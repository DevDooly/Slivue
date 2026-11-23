import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const root = new URL('../../', import.meta.url);
const publicKeys = ['playDeveloperName', 'playSupportEmail', 'playPrivacyPolicyUrl'];

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
  });
}
