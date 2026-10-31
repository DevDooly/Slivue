import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { inflateRawSync } from 'node:zlib';

const ANDROID_NS = 'http://schemas.android.com/apk/res/android';
const MAX_ENTRY_BYTES = 64 * 1024 * 1024;

export function policyMeta(html, name) {
  const matches = [];
  for (const tag of html.match(/<meta\b[^>]*>/gi) || []) {
    const attributes = Object.fromEntries([...tag.matchAll(/([\w:-]+)\s*=\s*(["'])(.*?)\2/g)].map(match => [match[1].toLowerCase(), match[3]]));
    if (attributes.name === name) matches.push(attributes.content || '');
  }
  assert.ok(matches.length <= 1, `개인정보처리방침의 ${name} 메타가 중복됩니다.`);
  return matches[0] || '';
}

function htmlText(html) {
  return html.replace(/<!--[\s\S]*?-->/g, '').replace(/<(?:style|script)\b[^>]*>[\s\S]*?<\/(?:style|script)>/gi, '')
    .replace(/<[^>]*>/g, ' ')
    .replace(/&(?:#(\d+)|#x([\da-f]+)|(amp|quot|apos|lt|gt|nbsp));/gi, (_, decimal, hex, named) => {
      if (decimal || hex) {
        const code = Number.parseInt(decimal || hex, decimal ? 10 : 16);
        return code >= 0 && code <= 0x10ffff ? String.fromCodePoint(code) : '';
      }
      return ({ amp: '&', quot: '"', apos: "'", lt: '<', gt: '>', nbsp: ' ' })[named.toLowerCase()];
    }).replace(/\s+/g, ' ').trim();
}

export function verifyFinalPolicy(html, metadata, language) {
  assert.equal(policyMeta(html, 'slivue-policy-status'), 'final', `${language}: 초안 개인정보처리방침이 포함되어 있어 제출할 수 없습니다.`);
  const effectiveDate = policyMeta(html, 'slivue-policy-effective-date');
  assert.match(effectiveDate, /^\d{4}-\d{2}-\d{2}$/, `${language}: 확정된 ISO 시행일 메타(slivue-policy-effective-date)가 필요합니다.`);
  const parsedDate = new Date(effectiveDate + 'T00:00:00Z');
  assert.ok(Number.isFinite(parsedDate.valueOf()) && parsedDate.toISOString().slice(0, 10) === effectiveDate, `${language}: 실제 달력 날짜가 아닌 시행일입니다.`);
  const visible = htmlText(html);
  assert.ok(!/미정|초안|준비본|확정\s*필요|not\s+yet\s+(?:decided|set)|\bdraft\b|\bTODO\b|\bTBD\b|\bplaceholder\b|\bexample\.(?:com|org|net)\b/i.test(visible), `${language}: 정책 본문에 초안·미정·임시 문구가 남아 있습니다.`);
  for (const [key, label] of [['developerName', '개발자 표시 이름'], ['supportEmail', '문의 이메일'], ['privacyPolicyUrl', '공개 정책 주소']]) {
    const expected = metadata?.[key]?.trim();
    assert.ok(expected, `${language}: 비교할 실제 ${label} 메타데이터가 필요합니다.`);
    const normalized = expected.replace(/\s+/g, ' ');
    const escaped = expected.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    const present = visible.includes(normalized) || (key === 'privacyPolicyUrl' && (html.includes(expected) || html.includes(escaped)));
    assert.ok(present, `${language}: 정책과 빌드의 ${label}가 일치하지 않습니다.`);
  }
  return { effectiveDate, sha256: createHash('sha256').update(html).digest('hex') };
}

export async function verifyPolicySources(metadata) {
  const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
  const policies = {};
  for (const language of ['ko', 'en']) {
    const bundled = await readFile(resolve(root, 'app/src/play/assets/privacy', language + '.html'));
    const publicPage = await readFile(resolve(root, 'docs/privacy', language + '.html'));
    assert.ok(bundled.equals(publicPage), `${language}: 앱 내·공개 정책 원본이 일치하지 않습니다.`);
    policies[language] = verifyFinalPolicy(bundled.toString('utf8'), metadata, language);
  }
  assert.equal(policies.ko.effectiveDate, policies.en.effectiveDate, '한·영 정책의 시행일이 서로 다릅니다.');
  return policies;
}

// 이 검사는 AAB 자체를 읽는다. APK 생성 후의 ZIP 정렬·실행·Play 심사는 별도 검증이다.
export function readZip(buffer) {
  assert.ok(buffer.length >= 22, '유효한 ZIP/AAB가 아닙니다.');
  let end = -1;
  for (let index = buffer.length - 22; index >= Math.max(0, buffer.length - 65557); index--) {
    if (buffer.readUInt32LE(index) === 0x06054b50 && index + 22 + buffer.readUInt16LE(index + 20) === buffer.length) {
      end = index;
      break;
    }
  }
  assert.ok(end >= 0, 'ZIP 끝 레코드를 찾지 못했습니다.');
  assert.equal(buffer.readUInt16LE(end + 4), 0, '분할 ZIP은 지원하지 않습니다.');
  assert.equal(buffer.readUInt16LE(end + 6), 0, '분할 ZIP은 지원하지 않습니다.');
  const count = buffer.readUInt16LE(end + 10);
  assert.notEqual(count, 0xffff, 'ZIP64 AAB는 별도 도구로 확인하세요.');
  let cursor = buffer.readUInt32LE(end + 16);
  const entries = new Map();
  for (let index = 0; index < count; index++) {
    assert.ok(cursor + 46 <= end, 'ZIP 중앙 디렉터리가 잘렸습니다.');
    assert.equal(buffer.readUInt32LE(cursor), 0x02014b50, 'ZIP 중앙 디렉터리가 올바르지 않습니다.');
    const flags = buffer.readUInt16LE(cursor + 8);
    const method = buffer.readUInt16LE(cursor + 10);
    const compressedSize = buffer.readUInt32LE(cursor + 20);
    const size = buffer.readUInt32LE(cursor + 24);
    const nameLength = buffer.readUInt16LE(cursor + 28);
    const extraLength = buffer.readUInt16LE(cursor + 30);
    const commentLength = buffer.readUInt16LE(cursor + 32);
    const offset = buffer.readUInt32LE(cursor + 42);
    const next = cursor + 46 + nameLength + extraLength + commentLength;
    assert.ok(next <= end, 'ZIP 파일명이 잘렸습니다.');
    const name = buffer.subarray(cursor + 46, cursor + 46 + nameLength).toString('utf8');
    assert.ok(!entries.has(name), `ZIP 항목이 중복됩니다: ${name}`);
    assert.equal(flags & 1, 0, `암호화된 ZIP 항목입니다: ${name}`);
    assert.ok(method === 0 || method === 8, `지원하지 않는 ZIP 압축입니다: ${name}`);
    assert.ok(size <= MAX_ENTRY_BYTES, `검사 한도를 넘는 ZIP 항목입니다: ${name}`);
    assert.ok(offset + 30 <= cursor && buffer.readUInt32LE(offset) === 0x04034b50, `ZIP 로컬 헤더가 올바르지 않습니다: ${name}`);
    const start = offset + 30 + buffer.readUInt16LE(offset + 26) + buffer.readUInt16LE(offset + 28);
    assert.ok(start + compressedSize <= cursor, `ZIP 데이터가 잘렸습니다: ${name}`);
    entries.set(name, {
      name, size,
      read() {
        const compressed = buffer.subarray(start, start + compressedSize);
        const content = method === 0 ? compressed : inflateRawSync(compressed, { maxOutputLength: MAX_ENTRY_BYTES });
        assert.equal(content.length, size, `ZIP 원본 크기가 다릅니다: ${name}`);
        return content;
      },
    });
    cursor = next;
  }
  return entries;
}

// AAPT2 Resources.proto의 XmlNode/XmlElement/XmlAttribute/Item/Primitive에 필요한 필드만 읽는다.
// 공식 스키마: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/tools/aapt2/Resources.proto
export function protoFields(buffer) {
  const fields = [];
  let cursor = 0;
  function varint() {
    let value = 0n;
    for (let shift = 0n; shift < 70n; shift += 7n) {
      assert.ok(cursor < buffer.length, 'Protobuf varint가 잘렸습니다.');
      const byte = buffer[cursor++];
      value |= BigInt(byte & 0x7f) << shift;
      if ((byte & 0x80) === 0) return value;
    }
    assert.fail('Protobuf varint가 너무 깁니다.');
  }
  while (cursor < buffer.length) {
    const key = Number(varint());
    const number = key >>> 3;
    const wire = key & 7;
    assert.ok(number > 0, 'Protobuf 필드 번호가 올바르지 않습니다.');
    if (wire === 0) fields.push({ number, wire, value: varint() });
    else if (wire === 2) {
      const length = Number(varint());
      assert.ok(Number.isSafeInteger(length) && length >= 0 && cursor + length <= buffer.length, 'Protobuf 메시지가 잘렸습니다.');
      fields.push({ number, wire, value: buffer.subarray(cursor, cursor + length) });
      cursor += length;
    } else if (wire === 1 || wire === 5) {
      const length = wire === 1 ? 8 : 4;
      assert.ok(cursor + length <= buffer.length, 'Protobuf 고정 필드가 잘렸습니다.');
      fields.push({ number, wire, value: buffer.subarray(cursor, cursor + length) });
      cursor += length;
    } else assert.fail(`지원하지 않는 Protobuf wire type: ${wire}`);
  }
  return fields;
}

const valueOf = (fields, number) => fields.find(field => field.number === number)?.value;
const stringOf = (fields, number) => {
  const value = valueOf(fields, number);
  return Buffer.isBuffer(value) ? value.toString('utf8') : '';
};

export function readXml(buffer) {
  function attribute(bytes) {
    const fields = protoFields(bytes);
    let value = stringOf(fields, 3);
    const compiled = valueOf(fields, 6);
    let referenceId;
    if (Buffer.isBuffer(compiled)) {
      const reference = valueOf(protoFields(compiled), 1);
      if (Buffer.isBuffer(reference)) {
        const id = valueOf(protoFields(reference), 2);
        if (typeof id === 'bigint') referenceId = Number(id);
      }
    }
    if (!value && Buffer.isBuffer(compiled)) {
      const item = protoFields(compiled);
      const primitive = valueOf(item, 7);
      const string = valueOf(item, 2);
      if (Buffer.isBuffer(primitive)) {
        const prim = protoFields(primitive).find(field => [6, 7, 8].includes(field.number));
        if (prim) value = String(prim.value);
      } else if (Buffer.isBuffer(string)) value = stringOf(protoFields(string), 1);
    }
    return { name: stringOf(fields, 2), namespace: stringOf(fields, 1), value, ...(referenceId === undefined ? {} : { referenceId }) };
  }
  function node(bytes, depth = 0) {
    assert.ok(depth <= 128, 'XML 중첩 한도를 초과했습니다.');
    const element = valueOf(protoFields(bytes), 1);
    if (!Buffer.isBuffer(element)) return null;
    const fields = protoFields(element);
    return {
      name: stringOf(fields, 3),
      attributes: fields.filter(field => field.number === 4).map(field => attribute(field.value)),
      children: fields.filter(field => field.number === 5).map(field => node(field.value, depth + 1)).filter(Boolean),
    };
  }
  const root = node(buffer);
  assert.ok(root?.name, 'AAPT2 Protobuf XML을 읽지 못했습니다.');
  return root;
}

export function readManifest(buffer) {
  const manifest = readXml(buffer);
  assert.equal(manifest?.name, 'manifest', 'AAB base manifest를 읽지 못했습니다.');
  return manifest;
}

function attributeOf(node, name, namespace = ANDROID_NS) {
  return node.attributes.find(attribute => attribute.name === name && attribute.namespace === namespace)?.value;
}

export function xmlResourceFiles(buffer) {
  const resources = [];
  for (const packageField of protoFields(buffer).filter(field => field.number === 2)) {
    const packageFields = protoFields(packageField.value);
    const packageId = Number(valueOf(protoFields(valueOf(packageFields, 1) || Buffer.alloc(0)), 1) || 0n);
    for (const typeField of packageFields.filter(field => field.number === 3)) {
      const typeFields = protoFields(typeField.value);
      if (stringOf(typeFields, 2) !== 'xml') continue;
      const typeId = Number(valueOf(protoFields(valueOf(typeFields, 1) || Buffer.alloc(0)), 1) || 0n);
      for (const entryField of typeFields.filter(field => field.number === 3)) {
        const entryFields = protoFields(entryField.value);
        const entryId = Number(valueOf(protoFields(valueOf(entryFields, 1) || Buffer.alloc(0)), 1) || 0n);
        const paths = [];
        for (const configValue of entryFields.filter(field => field.number === 6)) {
          const value = valueOf(protoFields(configValue.value), 2);
          const item = value && valueOf(protoFields(value), 4);
          const file = item && valueOf(protoFields(item), 5);
          if (Buffer.isBuffer(file)) paths.push(stringOf(protoFields(file), 1));
        }
        resources.push({ id: ((packageId << 24) | (typeId << 16) | entryId) >>> 0, name: stringOf(entryFields, 2), paths });
      }
    }
  }
  return resources;
}

const backupDomains = Object.freeze(['root', 'file', 'database', 'sharedpref', 'external', 'device_root', 'device_file', 'device_database', 'device_sharedpref']);

function verifyExcludes(node, label) {
  assert.ok(node.children.every(child => child.name === 'exclude'), `${label}: exclude 이외의 백업 규칙이 남아 있습니다.`);
  for (const domain of backupDomains) {
    assert.ok(node.children.some(child => attributeOf(child, 'domain', '') === domain && attributeOf(child, 'path', '') === '.'), `${label}: ${domain} 전체 제외 규칙이 없습니다.`);
  }
  return [...backupDomains];
}

export function verifyBackupConfiguration(entries, application) {
  assert.ok(application, 'Play application 요소가 없습니다.');
  assert.ok(['false', '0'].includes(attributeOf(application, 'allowBackup')), 'Play application의 allowBackup이 명시적으로 false가 아닙니다.');
  const table = entries.get('base/resources.pb');
  assert.ok(table, 'AAB의 base/resources.pb가 없습니다.');
  const resources = xmlResourceFiles(table.read());
  const report = { allowBackup: false, registeredFileProviders: [], legacy: [], cloud: [], deviceTransfer: [] };
  for (const [attributeName, resourceName] of [['fullBackupContent', 'backup_rules'], ['dataExtractionRules', 'data_extraction_rules']]) {
    const attribute = application.attributes.find(item => item.name === attributeName && item.namespace === ANDROID_NS);
    assert.ok(attribute, `Manifest의 ${attributeName} 연결이 없습니다.`);
    const resource = resources.find(item => item.id === attribute.referenceId && item.name === resourceName);
    assert.ok(resource?.paths.length > 0, `Manifest의 ${attributeName}가 실제 ${resourceName} XML 리소스를 참조하지 않습니다.`);
    for (const path of new Set(resource.paths)) {
      const entry = entries.get('base/' + path);
      assert.ok(entry, `최종 AAB 백업 XML이 없습니다: ${path}`);
      const root = readXml(entry.read());
      if (resourceName === 'backup_rules') {
        assert.equal(root.name, 'full-backup-content', `${path}: 구형 백업 XML 형식이 아닙니다.`);
        report.legacy.push({ path, excludedDomains: verifyExcludes(root, path) });
      } else {
        assert.equal(root.name, 'data-extraction-rules', `${path}: 데이터 추출 XML 형식이 아닙니다.`);
        for (const [section, reportKey] of [['cloud-backup', 'cloud'], ['device-transfer', 'deviceTransfer']]) {
          const sections = root.children.filter(child => child.name === section);
          assert.equal(sections.length, 1, `${path}: ${section} 규칙이 한 개 있어야 합니다.`);
          report[reportKey].push({ path, excludedDomains: verifyExcludes(sections[0], `${path}/${section}`) });
        }
      }
    }
  }
  return report;
}

function assertNoFileProvider(manifest, name) {
  for (const application of manifest.children.filter(child => child.name === 'application')) {
    for (const provider of application.children.filter(child => child.name === 'provider')) {
      const providerName = attributeOf(provider, 'name') || '';
      assert.ok(!providerName.endsWith('FileProvider'), `${name}: Play에 FileProvider가 남아 있습니다: ${providerName}`);
    }
  }
}

export function checkNativeAlignment(buffer, name) {
  assert.ok(buffer.length >= 64 && buffer.subarray(0, 4).equals(Buffer.from([0x7f, 0x45, 0x4c, 0x46])), `ELF 헤더가 올바르지 않습니다: ${name}`);
  assert.equal(buffer[4], 2, `64비트 ELF가 아닙니다: ${name}`);
  assert.equal(buffer[5], 1, `little-endian ELF가 아닙니다: ${name}`);
  const offset = Number(buffer.readBigUInt64LE(32));
  const entrySize = buffer.readUInt16LE(54);
  const entryCount = buffer.readUInt16LE(56);
  assert.ok(Number.isSafeInteger(offset) && entrySize >= 56 && entryCount > 0 && offset + entrySize * entryCount <= buffer.length, `ELF program header가 올바르지 않습니다: ${name}`);
  const alignments = [];
  for (let index = 0; index < entryCount; index++) {
    const cursor = offset + index * entrySize;
    if (buffer.readUInt32LE(cursor) !== 1) continue;
    const alignment = buffer.readBigUInt64LE(cursor + 48);
    assert.ok(alignment >= 16384n && (alignment & (alignment - 1n)) === 0n, `16KB PT_LOAD 정렬을 지원하지 않습니다: ${name} (${alignment})`);
    alignments.push(Number(alignment));
  }
  assert.ok(alignments.length > 0, `ELF PT_LOAD를 찾지 못했습니다: ${name}`);
  return { name, alignments };
}

export function verifyPlayArtifact(buffer, { requireUploadSignature = false, metadata } = {}) {
  const entries = readZip(buffer);
  assert.ok(entries.has('BundleConfig.pb'), 'Android App Bundle이 아닙니다.');
  const manifestEntry = entries.get('base/manifest/AndroidManifest.xml');
  assert.ok(manifestEntry, 'base AndroidManifest.xml이 없습니다.');
  const manifest = readManifest(manifestEntry.read());
  const packageName = attributeOf(manifest, 'package', '');
  assert.equal(packageName, 'com.devdooly.slivue', 'Play 전용 applicationId가 아닙니다.');
  const sdk = manifest.children.find(child => child.name === 'uses-sdk');
  assert.ok(sdk, 'uses-sdk가 없습니다.');
  const minSdk = Number(attributeOf(sdk, 'minSdkVersion'));
  const targetSdk = Number(attributeOf(sdk, 'targetSdkVersion'));
  assert.equal(minSdk, 26, '기존 최소 지원 버전이 달라졌습니다.');
  assert.ok(targetSdk >= 36, 'Play 대상 SDK 36 이상이 아닙니다.');
  const permissions = manifest.children.filter(child => child.name.startsWith('uses-permission')).map(child => attributeOf(child, 'name'));
  assertNoFileProvider(manifest, 'base');
  const backupConfiguration = verifyBackupConfiguration(entries, manifest.children.find(child => child.name === 'application'));
  for (const permission of ['android.permission.REQUEST_INSTALL_PACKAGES', 'android.permission.INTERNET']) {
    assert.ok(!permissions.includes(permission), `Play에 금지한 권한이 포함되었습니다: ${permission}`);
  }
  const forbidden = [
    'Lcom/devdooly/notificationedge/data/updater/AppUpdateManager;',
    'https://api.github.com/repos/DevDooly/message_edge/releases/latest',
    'application/vnd.android.package-archive',
    'android.settings.MANAGE_UNKNOWN_APP_SOURCES',
    'Landroidx/compose/ui/text/googlefonts/',
    'com.google.android.gms.fonts',
  ];
  let dexCount = 0;
  const nativeAlignment = [];
  for (const [name, entry] of entries) {
    if (/\/dex\/classes\d*\.dex$/.test(name)) {
      dexCount++;
      const bytes = entry.read();
      for (const term of forbidden) assert.ok(!bytes.includes(Buffer.from(term)), `Play DEX에 직접 배포/온라인 글꼴 코드가 남았습니다: ${name}: ${term}`);
    }
    if (/\/lib\/(?:arm64-v8a|x86_64)\/[^/]+\.so$/.test(name)) nativeAlignment.push(checkNativeAlignment(entry.read(), name));
    if (/\/manifest\/AndroidManifest\.xml$/.test(name)) {
      const bytes = entry.read();
      assertNoFileProvider(readManifest(bytes), name);
      for (const permission of ['android.permission.REQUEST_INSTALL_PACKAGES', 'android.permission.INTERNET']) {
        assert.ok(!bytes.includes(Buffer.from(permission)), `추가 모듈에 금지한 권한이 있습니다: ${name}: ${permission}`);
      }
    }
  }
  assert.ok(dexCount > 0, 'AAB에 classes.dex가 없습니다.');
  const signaturePresent = [...entries.keys()].some(name => /^META-INF\/[^/]+\.(?:RSA|DSA|EC)$/i.test(name));
  if (requireUploadSignature) assert.ok(signaturePresent, '미서명 AAB는 Play 업로드 제출 후보가 될 수 없습니다.');
  const policyStatus = {};
  const finalPolicies = {};
  for (const language of ['ko', 'en']) {
    const entry = entries.get(`base/assets/privacy/${language}.html`);
    assert.ok(entry, `${language} 앱 내 개인정보처리방침이 없습니다.`);
    const text = entry.read().toString('utf8');
    policyStatus[language] = policyMeta(text, 'slivue-policy-status') || 'unknown';
    if (requireUploadSignature) finalPolicies[language] = verifyFinalPolicy(text, metadata, language);
  }
  if (requireUploadSignature) {
    assert.equal(finalPolicies.ko.effectiveDate, finalPolicies.en.effectiveDate, '한·영 정책 시행일이 서로 다릅니다.');
  }
  return {
    packageName, minSdk, targetSdk,
    versionCode: attributeOf(manifest, 'versionCode'), versionName: attributeOf(manifest, 'versionName'),
    bytes: buffer.length, sha256: createHash('sha256').update(buffer).digest('hex'),
    signaturePresent, policyStatus, ...(requireUploadSignature ? { finalPolicies } : {}), submissionReadiness: '별도 verifyPlaySubmissionReady·preparePlaySubmission 및 Play Console 확인 필요', permissions, backupConfiguration, dexCount, nativeAlignment,
    scope: 'AAB 패키지·권한·Manifest/백업 XML·DEX·64비트 ELF 정적 검증입니다. 서명 유효성·생성 APK ZIP 정렬·실행·제조사 백업 동작·Play 심사는 별도입니다.',
  };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const args = process.argv.slice(2);
  const sourcesOnly = args[0] === '--policy-sources-only';
  const artifactPath = sourcesOnly ? null : args[0];
  const options = {};
  let requireUploadSignature = false;
  for (let index = 1; index < args.length; index++) {
    if (args[index] === '--require-upload-signature') requireUploadSignature = true;
    else {
      const key = ({ '--developer-name': 'developerName', '--support-email': 'supportEmail', '--policy-url': 'privacyPolicyUrl' })[args[index]];
      assert.ok(key && index + 1 < args.length && !options[key], '지원하지 않거나 중복된 검증 인자입니다.');
      options[key] = args[++index];
    }
  }
  assert.ok(args.length > 0, 'AAB 경로 또는 --policy-sources-only를 지정하세요.');
  try {
    const result = sourcesOnly ? await verifyPolicySources(options) : verifyPlayArtifact(await readFile(resolve(artifactPath)), { requireUploadSignature, metadata: options });
    console.log(JSON.stringify(result, null, 2));
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
