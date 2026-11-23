import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const root = new URL('../../', import.meta.url);
const publicKeys = ['playDeveloperName', 'playSupportEmail', 'playPrivacyPolicyUrl'];
const inquiryRetention = {
  ko: [
    '운영자가 보관·관리하는 문의 정보',
    '문의 처리 목적이 달성되면 지체 없이 삭제합니다.',
    '법령상 보관 의무가 있는 경우',
    '해당 의무가 적용되는 정보만 해당 기간 동안 별도로 보관합니다.',
    'Gmail 서비스 자체의 보관·삭제 처리는 Google의 정책과 서비스 설정에 따릅니다.',
  ],
  en: [
    'Inquiry information retained and managed by the operator',
    'deleted without undue delay once the inquiry-handling purpose has been fulfilled.',
    'If retention is legally required',
    'only the information covered by that obligation is stored separately for the required period.',
    "Gmail's own service-side retention and deletion are governed separately by Google's policies and service settings.",
  ],
};

test('한글 업로드 helper는 Windows PowerShell 5.1이 읽을 수 있는 UTF-8 BOM을 유지한다', async () => {
  const bytes = await readFile(new URL('scripts/prepare-play-upload.ps1', root));
  assert.equal(bytes.subarray(0, 3).toString('hex'), 'efbbbf',
    'Windows PowerShell 5.1은 BOM 없는 한글 스크립트를 ANSI로 해석할 수 있습니다.');
  assert.notEqual(bytes.subarray(3, 6).toString('hex'), 'efbbbf', 'BOM을 중복해서 넣지 않습니다.');
  const script = new TextDecoder('utf-8', { fatal: true }).decode(bytes.subarray(3));
  assert.match(script, /^#requires -Version 5\.1\r?\n/);
});

// 공개 파일은 비밀 값이나 모호한 Java Properties 이스케이프가 없는 단순 UTF-8 형식으로 유지한다.
async function publicMetadata() {
  const text = await readFile(new URL('config/play-public.properties', root), 'utf8');
  assert.ok(!text.startsWith('\uFEFF'), '공개 속성 파일에 BOM을 넣지 않습니다.');
  const entries = [];
  for (const rawLine of text.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line || line.startsWith('#') || line.startsWith('!')) continue;
    const separator = line.indexOf('=');
    assert.ok(separator > 0, '공개 속성은 key=value 형식이어야 합니다.');
    const key = line.slice(0, separator).trim();
    const value = line.slice(separator + 1);
    assert.ok(publicKeys.includes(key), `공개 파일에 허용되지 않은 키가 있습니다: ${key}`);
    assert.ok(!value.includes('\\'), '공개 속성 값에는 이스케이프나 여러 줄 연결을 사용하지 않습니다.');
    assert.equal(value, value.trim(), '공개 값 앞뒤에 공백을 두지 않습니다.');
    assert.ok(!entries.some(([existing]) => existing === key), `중복 공개 속성: ${key}`);
    entries.push([key, value]);
  }
  assert.deepEqual(entries.map(([key]) => key).sort(), [...publicKeys].sort());
  return Object.fromEntries(entries);
}

test('공개 기본값은 사용자가 확정한 이름·문의 이메일이며 비밀·승인 값이 없다', async () => {
  const metadata = await publicMetadata();
  assert.equal(metadata.playDeveloperName, 'DevDooly');
  assert.equal(metadata.playSupportEmail, 'vmdooly@gmail.com');
  // 빈 URL은 미완료 상태로 남길 수 있다. 값이 생기면 공개 HTTPS 주소여야 한다.
  if (metadata.playPrivacyPolicyUrl) {
    const url = new URL(metadata.playPrivacyPolicyUrl);
    assert.equal(url.protocol, 'https:');
    assert.equal(url.username, '');
    assert.equal(url.password, '');
    assert.ok(url.hostname);
  }
});

for (const language of ['ko', 'en']) {
  test(`${language} 공개 정책과 앱 자산은 동일한 운영 정보·본문을 제공한다`, async () => {
    const metadata = await publicMetadata();
    const publicPolicy = await readFile(new URL(`docs/privacy/${language}.html`, root));
    const assetPolicy = await readFile(new URL(`app/src/play/assets/privacy/${language}.html`, root));
    assert.deepEqual(assetPolicy, publicPolicy, '공개 정책과 앱 내부 정책이 다릅니다.');
    const html = publicPolicy.toString('utf8');
    assert.ok(html.includes(metadata.playDeveloperName));
    assert.ok(html.includes(metadata.playSupportEmail));
    assert.match(html, /<meta\s+name="slivue-policy-status"\s+content="(?:draft|final)"\s*>/);
    assert.ok(html.includes('com.devdooly.slivue'));
    if (metadata.playPrivacyPolicyUrl) assert.ok(html.includes(metadata.playPrivacyPolicyUrl));
    for (const criterion of inquiryRetention[language]) {
      assert.ok(html.includes(criterion), `${language}: 확정한 문의 보관 기준 또는 Gmail 처리 범위가 누락되었습니다.`);
    }
    assert.doesNotMatch(html, /문의 정보의 구체적인 보관·삭제 기준은 정식 시행 전에 확정|Specific retention and deletion rules for inquiry information will be set|effective date and inquiry-retention rules are set/,
      '확정한 문의 보관 기준을 미정으로 표시하면 안 됩니다.');
  });
}
