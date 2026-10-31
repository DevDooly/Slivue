import test from 'node:test';
import assert from 'node:assert/strict';
import { deflateRawSync } from 'node:zlib';
import { readZip, protoFields, readManifest, readXml, xmlResourceFiles, checkNativeAlignment, verifyPlayArtifact, policyMeta, verifyFinalPolicy } from '../verify-play-artifact.mjs';

const ANDROID_NS = 'http://schemas.android.com/apk/res/android';
const varint = number => {
  let value = BigInt(number);
  const bytes = [];
  do {
    let byte = Number(value & 0x7fn);
    value >>= 7n;
    if (value) byte |= 0x80;
    bytes.push(byte);
  } while (value);
  return Buffer.from(bytes);
};
const field = (number, value) => {
  if (typeof value === 'number') return Buffer.concat([varint(number << 3), varint(value)]);
  const bytes = Buffer.isBuffer(value) ? value : Buffer.from(value);
  return Buffer.concat([varint((number << 3) | 2), varint(bytes.length), bytes]);
};
const attr = (name, value, namespace = ANDROID_NS) => Buffer.concat([
  field(1, namespace), field(2, name),
  typeof value === 'number' ? field(6, field(7, field(6, value))) : field(3, value),
]);
const xml = (name, attributes = [], children = []) => field(1, Buffer.concat([
  field(3, name), ...attributes.map(value => field(4, value)), ...children.map(value => field(5, value)),
]));
const refAttr = (name, id) => Buffer.concat([field(1, ANDROID_NS), field(2, name), field(6, field(1, field(2, id)))]);
const backupDomains = ['root', 'file', 'database', 'sharedpref', 'external', 'device_root', 'device_file', 'device_database', 'device_sharedpref'];
const excludes = (domains = backupDomains, path = '.') => domains.map(domain => xml('exclude', [attr('domain', domain, ''), attr('path', path, '')]));
const legacyBackup = (domains = backupDomains) => xml('full-backup-content', [], excludes(domains));
const modernBackup = (cloud = backupDomains, device = backupDomains) => xml('data-extraction-rules', [], [
  xml('cloud-backup', [], excludes(cloud)), xml('device-transfer', [], excludes(device)),
]);
function resourceTable({ backupPaths = ['res/xml/backup_rules.xml'], extractionPaths = ['res/xml/data_extraction_rules.xml'] } = {}) {
  const entry = (id, name, paths) => field(3, Buffer.concat([
    field(1, field(1, id)), field(2, name),
    ...paths.map(path => field(6, field(2, field(4, field(5, field(1, path)))))),
  ]));
  return field(2, Buffer.concat([
    field(1, field(1, 0x7f)), field(2, 'com.devdooly.slivue'),
    field(3, Buffer.concat([field(1, field(1, 1)), field(2, 'xml'), entry(0, 'backup_rules', backupPaths), entry(1, 'data_extraction_rules', extractionPaths)])),
  ]));
}
function manifest({ packageName = 'com.devdooly.slivue', target = 36, permissions = [], allowBackup = 'false', providerName, fullBackupId = 0x7f010000, extractionId = 0x7f010001 } = {}) {
  return xml('manifest', [attr('package', packageName, ''), attr('versionCode', 157), attr('versionName', '1.3.27')], [
    xml('uses-sdk', [attr('minSdkVersion', 26), attr('targetSdkVersion', target)]),
    ...permissions.map(name => xml('uses-permission', [attr('name', name)])),
    xml('application', [
      ...(allowBackup === null ? [] : [attr('allowBackup', allowBackup)]),
      refAttr('fullBackupContent', fullBackupId), refAttr('dataExtractionRules', extractionId),
    ], providerName ? [xml('provider', [attr('name', providerName)])] : []),
  ]);
}
function zip(items, { compressed = false } = {}) {
  const local = [];
  const central = [];
  let offset = 0;
  for (const [name, raw] of Object.entries(items)) {
    const bytes = Buffer.isBuffer(raw) ? raw : Buffer.from(raw);
    const data = compressed ? deflateRawSync(bytes) : bytes;
    const filename = Buffer.from(name);
    const header = Buffer.alloc(30);
    header.writeUInt32LE(0x04034b50);
    header.writeUInt16LE(compressed ? 8 : 0, 8);
    header.writeUInt32LE(data.length, 18);
    header.writeUInt32LE(bytes.length, 22);
    header.writeUInt16LE(filename.length, 26);
    local.push(header, filename, data);
    const directory = Buffer.alloc(46);
    directory.writeUInt32LE(0x02014b50);
    directory.writeUInt16LE(compressed ? 8 : 0, 10);
    directory.writeUInt32LE(data.length, 20);
    directory.writeUInt32LE(bytes.length, 24);
    directory.writeUInt16LE(filename.length, 28);
    directory.writeUInt32LE(offset, 42);
    central.push(directory, filename);
    offset += header.length + filename.length + data.length;
  }
  const directory = Buffer.concat(central);
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50);
  end.writeUInt16LE(Object.keys(items).length, 8);
  end.writeUInt16LE(Object.keys(items).length, 10);
  end.writeUInt32LE(directory.length, 12);
  end.writeUInt32LE(offset, 16);
  return Buffer.concat([...local, directory, end]);
}
function artifact(overrides = {}, manifestOptions = {}) {
  const entries = {
    'BundleConfig.pb': Buffer.alloc(0),
    'base/manifest/AndroidManifest.xml': manifest(manifestOptions),
    'base/resources.pb': resourceTable(),
    'base/res/xml/backup_rules.xml': legacyBackup(),
    'base/res/xml/data_extraction_rules.xml': modernBackup(),
    'base/dex/classes.dex': 'dex\n035\0safe',
    'base/assets/privacy/ko.html': '<meta name="slivue-policy-status" content="draft">',
    'base/assets/privacy/en.html': '<meta name="slivue-policy-status" content="draft">',
    ...overrides,
  };
  return zip(Object.fromEntries(Object.entries(entries).filter(([, value]) => value !== null)));
}
function elf(alignment = 16384n) {
  const bytes = Buffer.alloc(120);
  Buffer.from([0x7f, 0x45, 0x4c, 0x46, 2, 1]).copy(bytes);
  bytes.writeBigUInt64LE(64n, 32);
  bytes.writeUInt16LE(56, 54);
  bytes.writeUInt16LE(1, 56);
  bytes.writeUInt32LE(1, 64);
  bytes.writeBigUInt64LE(alignment, 64 + 48);
  return bytes;
}

test('저장형과 deflate ZIP 항목을 정확히 읽는다', () => {
  for (const compressed of [false, true]) {
    const entries = readZip(zip({ 'one.txt': '한글', 'two.txt': 'English' }, { compressed }));
    assert.equal(entries.get('one.txt').read().toString('utf8'), '한글');
    assert.equal(entries.get('two.txt').read().toString('utf8'), 'English');
  }
});

test('잘린 ZIP과 Protobuf를 거절한다', () => {
  assert.throws(() => readZip(Buffer.from('invalid')));
  const bytes = zip({ 'one.txt': 'content' });
  assert.throws(() => readZip(bytes.subarray(0, bytes.length - 2)));
  assert.throws(() => protoFields(Buffer.from([0x0a, 0x7f, 0x01])));
  assert.throws(() => protoFields(Buffer.from([0x08, 0x80])));
});

test('AAPT2 manifest의 문자열과 컴파일된 SDK 정수를 읽는다', () => {
  const parsed = readManifest(manifest());
  assert.equal(parsed.name, 'manifest');
  assert.equal(parsed.attributes.find(value => value.name === 'package').value, 'com.devdooly.slivue');
  assert.equal(parsed.children[0].attributes.find(value => value.name === 'targetSdkVersion').value, '36');
});

test('Play 패키지·SDK·파일 크기·초안/미서명 상태를 보고한다', () => {
  const bytes = artifact();
  const report = verifyPlayArtifact(bytes);
  assert.equal(report.packageName, 'com.devdooly.slivue');
  assert.equal(report.targetSdk, 36);
  assert.equal(report.versionCode, '157');
  assert.equal(report.versionName, '1.3.27');
  assert.equal(report.bytes, bytes.length);
  assert.equal(report.signaturePresent, false);
  assert.deepEqual(report.policyStatus, { ko: 'draft', en: 'draft' });
  assert.match(report.sha256, /^[a-f0-9]{64}$/);
});

test('기존 직접배포 패키지와 낮은 대상 SDK를 거절한다', () => {
  assert.throws(() => verifyPlayArtifact(artifact({}, { packageName: 'com.devdooly.notificationedge' })), /applicationId/);
  assert.throws(() => verifyPlayArtifact(artifact({}, { target: 34 })), /SDK 36/);
});

test('설치 권한과 인터넷 권한이 남으면 실패한다', () => {
  for (const permission of ['android.permission.INTERNET', 'android.permission.REQUEST_INSTALL_PACKAGES']) {
    assert.throws(() => verifyPlayArtifact(artifact({}, { permissions: [permission] })), /권한/);
  }
});

test('DEX의 직접 업데이트·APK 설치·온라인 글꼴 흔적을 거절한다', () => {
  for (const term of [
    'Lcom/devdooly/notificationedge/data/updater/AppUpdateManager;',
    'application/vnd.android.package-archive',
    'android.settings.MANAGE_UNKNOWN_APP_SOURCES',
    'com.google.android.gms.fonts',
  ]) assert.throws(() => verifyPlayArtifact(artifact({ 'base/dex/classes.dex': term })), /코드가 남았습니다/);
});

test('64비트 ELF PT_LOAD 16KB 이상 정렬을 확인한다', () => {
  assert.deepEqual(checkNativeAlignment(elf(), 'test.so').alignments, [16384]);
  assert.deepEqual(checkNativeAlignment(elf(65536n), 'test.so').alignments, [65536]);
  assert.throws(() => checkNativeAlignment(elf(4096n), 'test.so'), /16KB/);
  assert.throws(() => checkNativeAlignment(elf(20000n), 'test.so'), /16KB/);
});

test('미서명·초안 정책을 제출 가능한 후보로 오인하지 않는다', () => {
  assert.throws(() => verifyPlayArtifact(artifact(), { requireUploadSignature: true }), /미서명/);
  assert.throws(() => verifyPlayArtifact(artifact({ 'META-INF/UPLOAD.RSA': 'presence-only' }), { requireUploadSignature: true }), /초안/);
});

const metadata = {
  developerName: 'Dev & Dooly', supportEmail: 'privacy@devdooly.com',
  privacyPolicyUrl: 'https://devdooly.com/slivue/privacy?hl=en&v=1',
};
const finalPolicy = `<!doctype html><html><head>
  <meta name="slivue-policy-status" content="final">
  <meta content="2026-10-31" name="slivue-policy-effective-date">
  <style>.draft { color: orange; }</style>
  </head><body><h1>Slivue Privacy Policy</h1><p>Dev &amp; Dooly</p>
  <p>privacy@devdooly.com</p><a href="https://devdooly.com/slivue/privacy?hl=en&amp;v=1">Policy</a></body></html>`;

test('확정 정책의 실제 운영 정보·HTML 엔티티·ISO 시행일을 확인한다', () => {
  const result = verifyFinalPolicy(finalPolicy, metadata, 'en');
  assert.equal(result.effectiveDate, '2026-10-31');
  assert.match(result.sha256, /^[a-f0-9]{64}$/);
  assert.equal(policyMeta(finalPolicy, 'slivue-policy-effective-date'), '2026-10-31');
});

test('final 메타만 바꾸고 남겨둔 초안 본문을 거절한다', () => {
  for (const placeholder of ['초안', '미정', '준비본', '확정 필요', 'draft', 'not yet decided', 'not yet set', 'TODO', 'TBD', 'placeholder']) {
    assert.throws(() => verifyFinalPolicy(finalPolicy.replace('</body>', `<p>${placeholder}</p></body>`), metadata, 'en'), /임시 문구/);
  }
});

test('시행일 메타 누락·잘못된 달력 날짜를 거절한다', () => {
  assert.throws(() => verifyFinalPolicy(finalPolicy.replace('slivue-policy-effective-date', 'other-meta'), metadata, 'en'), /ISO 시행일/);
  assert.throws(() => verifyFinalPolicy(finalPolicy.replace('2026-10-31', '2026-02-30'), metadata, 'en'), /달력 날짜/);
});

test('정책과 빌드의 이름·이메일·공개 URL 불일치를 거절한다', () => {
  for (const [key, value] of [['developerName', 'Another operator'], ['supportEmail', 'other@devdooly.com'], ['privacyPolicyUrl', 'https://devdooly.com/wrong']]) {
    assert.throws(() => verifyFinalPolicy(finalPolicy, { ...metadata, [key]: value }, 'en'), /일치하지 않습니다/);
  }
  assert.throws(() => verifyFinalPolicy(finalPolicy, undefined, 'en'), /실제.*메타데이터/);
});

test('중복된 상태 메타를 거절한다', () => {
  assert.throws(() => policyMeta(finalPolicy + '<meta name="slivue-policy-status" content="draft">', 'slivue-policy-status'), /중복/);
});

test('AAB의 실제 리소스 ID·파일과 구형/cloud/D2D 제외 XML을 확인한다', () => {
  const resources = xmlResourceFiles(resourceTable());
  assert.equal(resources[0].id, 0x7f010000);
  assert.equal(resources[1].id, 0x7f010001);
  assert.deepEqual(resources[0].paths, ['res/xml/backup_rules.xml']);
  assert.equal(readXml(legacyBackup()).name, 'full-backup-content');
  const report = verifyPlayArtifact(artifact()).backupConfiguration;
  assert.equal(report.allowBackup, false);
  for (const section of ['legacy', 'cloud', 'deviceTransfer']) {
    assert.deepEqual(report[section][0].excludedDomains, backupDomains);
  }
});

test('명시적인 allowBackup=false가 아니면 거절한다', () => {
  assert.throws(() => verifyPlayArtifact(artifact({}, { allowBackup: 'true' })), /allowBackup/);
  assert.throws(() => verifyPlayArtifact(artifact({}, { allowBackup: null })), /allowBackup/);
  assert.equal(verifyPlayArtifact(artifact({}, { allowBackup: 0 })).backupConfiguration.allowBackup, false);
});

test('base 및 추가 모듈의 FileProvider를 거절한다', () => {
  assert.throws(() => verifyPlayArtifact(artifact({}, { providerName: 'androidx.core.content.FileProvider' })), /FileProvider/);
  assert.throws(() => verifyPlayArtifact(artifact({ 'feature/manifest/AndroidManifest.xml': manifest({ providerName: 'androidx.core.content.FileProvider' }) })), /FileProvider/);
});

test('구형 백업의 모든 domain 전체 제외가 실제로 필요하다', () => {
  for (const missing of backupDomains) {
    assert.throws(() => verifyPlayArtifact(artifact({ 'base/res/xml/backup_rules.xml': legacyBackup(backupDomains.filter(domain => domain !== missing)) })), /전체 제외 규칙/);
  }
  assert.throws(() => verifyPlayArtifact(artifact({ 'base/res/xml/backup_rules.xml': xml('full-backup-content', [], excludes(backupDomains, 'datastore/')) })), /전체 제외 규칙/);
});

test('cloud와 D2D 각각에서 모든 domain 전체 제외를 요구한다', () => {
  for (const missing of backupDomains) {
    const partial = backupDomains.filter(domain => domain !== missing);
    assert.throws(() => verifyPlayArtifact(artifact({ 'base/res/xml/data_extraction_rules.xml': modernBackup(partial, backupDomains) })), /cloud-backup.*전체 제외 규칙/);
    assert.throws(() => verifyPlayArtifact(artifact({ 'base/res/xml/data_extraction_rules.xml': modernBackup(backupDomains, partial) })), /device-transfer.*전체 제외 규칙/);
  }
});

test('직접 배포판 include 규칙이 들어온 경우 실패한다', () => {
  const include = xml('include', [attr('domain', 'sharedpref', ''), attr('path', '.', '')]);
  assert.throws(() => verifyPlayArtifact(artifact({ 'base/res/xml/backup_rules.xml': xml('full-backup-content', [], [include, ...excludes()]) })), /exclude 이외/);
  assert.throws(() => verifyPlayArtifact(artifact({ 'base/res/xml/data_extraction_rules.xml': xml('data-extraction-rules', [], [xml('cloud-backup', [], [include]), xml('device-transfer', [], excludes())]) })), /exclude 이외/);
});

test('리소스 파일만 두고 Manifest 연결이 틀리거나 XML이 없으면 실패한다', () => {
  assert.throws(() => verifyPlayArtifact(artifact({}, { fullBackupId: 0x7f010002 })), /실제 backup_rules XML 리소스/);
  assert.throws(() => verifyPlayArtifact(artifact({ 'base/resources.pb': null })), /resources.pb/);
  assert.throws(() => verifyPlayArtifact(artifact({ 'base/res/xml/backup_rules.xml': null })), /최종 AAB 백업 XML/);
});

test('기기별 다른 XML 설정 하나라도 백업을 허용하면 실패한다', () => {
  const resources = resourceTable({ backupPaths: ['res/xml/backup_rules.xml', 'res/xml-v31/backup_rules.xml'] });
  assert.throws(() => verifyPlayArtifact(artifact({
    'base/resources.pb': resources,
    'base/res/xml-v31/backup_rules.xml': legacyBackup(['file']),
  })), /전체 제외 규칙/);
});
