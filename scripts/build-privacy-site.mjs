import assert from 'node:assert/strict';
import { lstat, mkdir, readFile, readdir, writeFile } from 'node:fs/promises';
import { dirname, isAbsolute, join, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { policyMeta, verifyFinalPolicy } from './verify-play-artifact.mjs';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
export const siteFiles = Object.freeze(['index.html', 'privacy/en.html', 'privacy/index.html', 'privacy/ko.html']);
const publicKeys = ['playDeveloperName', 'playSupportEmail', 'playPrivacyPolicyUrl'];

function escapeHtml(text) {
  return text.replace(/[&<>"']/g, value => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[value]);
}

export function parsePublicMetadata(text) {
  assert.ok(!text.startsWith('\uFEFF'), '공개 속성 파일에 BOM을 넣지 않습니다.');
  const values = {};
  for (const raw of text.split(/\r?\n/)) {
    const line = raw.trim();
    if (!line || line.startsWith('#') || line.startsWith('!')) continue;
    const separator = raw.indexOf('=');
    assert.ok(separator > 0, '공개 속성은 key=value 형식이어야 합니다.');
    const key = raw.slice(0, separator).trim();
    const value = raw.slice(separator + 1);
    assert.ok(publicKeys.includes(key), `공개 파일에 허용되지 않은 키가 있습니다: ${key}`);
    assert.ok(!Object.hasOwn(values, key), `중복된 공개 속성입니다: ${key}`);
    assert.ok(value && value === value.trim() && !/[\\\u0000-\u001f\u007f]/u.test(value), `공개 속성 값이 비어 있거나 올바르지 않습니다: ${key}`);
    values[key] = value;
  }
  assert.deepEqual(Object.keys(values).sort(), [...publicKeys].sort(), '공개 설정은 이름·문의 이메일·정책 URL 세 항목만 포함해야 합니다.');
  assert.match(values.playSupportEmail, /^[^\s@<>]+@[^\s@<>]+\.[^\s@<>]+$/u, '문의 이메일을 확인하세요.');
  const policyUrl = new URL(values.playPrivacyPolicyUrl);
  assert.ok(policyUrl.protocol === 'https:' && policyUrl.hostname && !policyUrl.username && !policyUrl.password && !policyUrl.search && !policyUrl.hash,
    '정책 주소는 인증정보·검색값·프래그먼트가 없는 HTTPS 주소여야 합니다.');
  assert.ok(policyUrl.pathname.endsWith('/privacy/ko.html'), '기본 정책 주소는 privacy/ko.html이어야 합니다.');
  return { developerName: values.playDeveloperName, supportEmail: values.playSupportEmail, privacyPolicyUrl: values.playPrivacyPolicyUrl };
}

async function assertPlainPath(path, expectedDirectory) {
  try {
    const entry = await lstat(path);
    assert.ok(!entry.isSymbolicLink(), `정책 사이트 경로에 심볼릭 링크를 사용할 수 없습니다: ${path}`);
    assert.ok(expectedDirectory ? entry.isDirectory() : entry.isFile(), `정책 사이트 경로 형식이 올바르지 않습니다: ${path}`);
  } catch (error) {
    if (error.code !== 'ENOENT') throw error;
  }
}

async function checkOutputTree(output, prefix = '') {
  await assertPlainPath(output, true);
  let entries;
  try { entries = await readdir(output, { withFileTypes: true }); }
  catch (error) { if (error.code === 'ENOENT') return []; throw error; }
  const files = [];
  for (const entry of entries) {
    const path = join(output, entry.name);
    const name = prefix + entry.name;
    assert.ok(!entry.isSymbolicLink(), `배포 산출물에 링크를 포함할 수 없습니다: ${name}`);
    if (entry.isDirectory()) {
      assert.equal(name, 'privacy', `허용하지 않은 배포 폴더입니다: ${name}`);
      files.push(...await checkOutputTree(path, name + '/'));
    } else {
      assert.ok(entry.isFile() && siteFiles.includes(name), `허용하지 않은 배포 파일입니다: ${name}`);
      assert.equal((await lstat(path)).nlink, 1, `배포 파일은 다른 파일의 하드 링크이면 안 됩니다: ${name}`);
      files.push(name);
    }
  }
  return files.sort();
}

export function renderPolicyIndex(metadata, status, withinPrivacy = false) {
  const linkPrefix = withinPrivacy ? '' : 'privacy/';
  const warning = status === 'draft'
    ? '<aside><strong>개인정보처리방침 검토용 준비본</strong><p>현재 실제 시행 중인 방침이 아니며 Google Play 제출용 최종본이 아닙니다.</p><p lang="en">This is a review draft, not an effective or submission-ready policy.</p></aside>'
    : '';
  return `<!doctype html>
<html lang="ko">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="robots" content="noindex, nofollow">
  <meta name="slivue-policy-status" content="${status}">
  <title>Slivue 개인정보 안내 · Privacy policy</title>
  <style>
    :root { color-scheme: light dark; font: 18px/1.7 system-ui, sans-serif; }
    body { max-width: 720px; margin: auto; padding: 32px 20px; overflow-wrap: anywhere; }
    h1 { line-height: 1.3; } a { color: #1762ba; text-underline-offset: 4px; }
    nav { display: flex; flex-wrap: wrap; gap: 16px; margin: 28px 0; }
    nav a { border: 1px solid currentColor; border-radius: 12px; padding: 12px 24px; min-height: 24px; }
    aside { border: 2px solid #b97000; border-radius: 12px; padding: 20px; }
    a:focus-visible { outline: 3px solid #7755df; outline-offset: 4px; }
    @media (prefers-color-scheme: dark) { a { color: #a9cfff; } }
  </style>
</head>
<body>
  <main>
    <h1>Slivue 개인정보 안내</h1>
    <p lang="en">Slivue privacy policy · Google Play distribution</p>
    <p>공개 개발자: ${escapeHtml(metadata.developerName)}<br>개인정보 문의: ${escapeHtml(metadata.supportEmail)}</p>
    ${warning}
    <nav aria-label="정책 언어 선택 / Policy language">
      <a href="${linkPrefix}ko.html" lang="ko" hreflang="ko">한국어</a>
      <a href="${linkPrefix}en.html" lang="en" hreflang="en">English</a>
    </nav>
    <p>Google Play판 <code>com.devdooly.slivue</code>의 정책입니다. GitHub 직접 배포판은 별도 앱입니다.</p>
    <p>이 페이지의 공개가 Google Play 출시나 심사 승인을 의미하지는 않습니다.</p>
  </main>
</body>
</html>
`;
}

export async function buildPrivacySite({ projectDir = root, outputDir } = {}) {
  const project = resolve(projectDir);
  const buildRoot = join(project, 'app', 'build');
  const output = resolve(outputDir || join(buildRoot, 'privacy-site'));
  const pathWithinBuild = relative(buildRoot, output);
  assert.ok(pathWithinBuild && !isAbsolute(pathWithinBuild) && pathWithinBuild !== '..' && !pathWithinBuild.startsWith('..' + sep),
    '정책 배포 폴더는 app/build 내부의 별도 하위 폴더여야 합니다.');
  // 결과 디렉터리까지의 기존 구성요소가 링크라면 알려지지 않은 위치에 쓰지 않는다.
  await assertPlainPath(join(project, 'app'), true);
  await assertPlainPath(buildRoot, true);
  let directory = buildRoot;
  for (const component of pathWithinBuild.split(sep)) {
    directory = join(directory, component);
    await assertPlainPath(directory, true);
  }
  const metadata = parsePublicMetadata(await readFile(join(project, 'config', 'play-public.properties'), 'utf8'));
  const policies = {};
  const statuses = [];
  const effectiveDates = [];
  for (const language of ['ko', 'en']) {
    const sourcePath = join(project, 'docs', 'privacy', language + '.html');
    const assetPath = join(project, 'app', 'src', 'play', 'assets', 'privacy', language + '.html');
    await assertPlainPath(sourcePath, false);
    await assertPlainPath(assetPath, false);
    const [source, asset] = await Promise.all([readFile(sourcePath), readFile(assetPath)]);
    assert.ok(source.equals(asset), `${language}: 공개 정책 원본과 앱 자산이 일치하지 않습니다.`);
    const html = source.toString('utf8');
    assert.match(html, new RegExp(`<html\\s+lang=["']${language}["']`, 'i'), `${language}: 정책 문서 언어가 올바르지 않습니다.`);
    for (const value of Object.values(metadata)) assert.ok(html.includes(value) || html.includes(escapeHtml(value)), `${language}: 공개 운영 정보와 정책 본문이 일치하지 않습니다.`);
    const status = policyMeta(html, 'slivue-policy-status');
    assert.ok(status === 'draft' || status === 'final', `${language}: 정책 상태는 draft 또는 final이어야 합니다.`);
    if (status === 'draft') {
      const robots = policyMeta(html, 'robots').toLowerCase().split(/[,\s]+/);
      assert.ok(robots.includes('noindex') && robots.includes('nofollow'), `${language}: 준비본은 noindex, nofollow를 유지해야 합니다.`);
    } else effectiveDates.push(verifyFinalPolicy(html, metadata, language).effectiveDate);
    policies[language] = source;
    statuses.push(status);
  }
  assert.equal(statuses[0], statuses[1], '한·영 정책의 공개 상태가 다릅니다.');
  if (effectiveDates.length) assert.equal(effectiveDates[0], effectiveDates[1], '한·영 확정 정책의 시행일이 다릅니다.');
  // 이전 결과에 예상 밖 파일이 있으면 보존한 채 실패한다. 재귀 삭제는 하지 않는다.
  await checkOutputTree(output);
  await mkdir(join(output, 'privacy'), { recursive: true });
  await Promise.all([
    writeFile(join(output, 'index.html'), renderPolicyIndex(metadata, statuses[0])),
    writeFile(join(output, 'privacy', 'index.html'), renderPolicyIndex(metadata, statuses[0], true)),
    ...Object.entries(policies).map(([language, bytes]) => writeFile(join(output, 'privacy', language + '.html'), bytes)),
  ]);
  assert.deepEqual(await checkOutputTree(output), [...siteFiles], '정책 배포 산출물은 허용된 네 개 파일만 포함해야 합니다.');
  return { outputDir: output, files: [...siteFiles], policyStatus: statuses[0] };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const result = await buildPrivacySite();
    console.log(JSON.stringify(result, null, 2));
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
