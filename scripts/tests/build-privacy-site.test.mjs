import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { isAbsolute, join, relative, resolve, sep } from 'node:path';
import { buildPrivacySite, parsePublicMetadata, siteFiles } from '../build-privacy-site.mjs';

const config = 'playDeveloperName=DevDooly\nplaySupportEmail=vmdooly@gmail.com\nplayPrivacyPolicyUrl=https://devdooly.github.io/Slivue/privacy/ko.html\n';

async function fixture(t, status = 'draft') {
  const project = await mkdtemp(join(tmpdir(), 'slivue-privacy-site-'));
  // 이 시험에서 만든 고유 임시 디렉터리만 확인 후 정리한다. 배포 산출물 폴더는 삭제하지 않는다.
  t.after(() => {
    const withinTemp = relative(resolve(tmpdir()), resolve(project));
    assert.ok(!isAbsolute(withinTemp) && withinTemp.startsWith('slivue-privacy-site-') && !withinTemp.includes(sep));
    return rm(project, { recursive: true, force: true });
  });
  await Promise.all(['config', 'docs/privacy', 'app/src/play/assets/privacy'].map(path => mkdir(join(project, path), { recursive: true })));
  await writeFile(join(project, 'config/play-public.properties'), config);
  for (const language of ['ko', 'en']) {
    const html = `<!doctype html><html lang="${language}"><head><meta name="robots" content="noindex, nofollow"><meta name="slivue-policy-status" content="${status}">${status === 'final' ? '<meta name="slivue-policy-effective-date" content="2026-12-01">' : ''}</head><body>Slivue ${status === 'draft' ? '검토용 준비본 · Review draft' : 'Privacy policy'} DevDooly vmdooly@gmail.com https://devdooly.github.io/Slivue/privacy/ko.html</body></html>`;
    await Promise.all(['docs/privacy', 'app/src/play/assets/privacy'].map(path => writeFile(join(project, path, language + '.html'), html)));
  }
  return project;
}

test('공개 정책 원문을 그대로 복사하고 언어별 링크·운영 정보·초안 경고를 제공한다', async t => {
  const project = await fixture(t);
  const result = await buildPrivacySite({ projectDir: project });
  assert.deepEqual(result.files, [...siteFiles]);
  assert.equal(result.policyStatus, 'draft');
  for (const language of ['ko', 'en']) {
    assert.deepEqual(await readFile(join(result.outputDir, 'privacy', language + '.html')), await readFile(join(project, 'docs/privacy', language + '.html')));
  }
  const home = await readFile(join(result.outputDir, 'index.html'), 'utf8');
  const privacy = await readFile(join(result.outputDir, 'privacy/index.html'), 'utf8');
  assert.ok(home.includes('href="privacy/ko.html"') && home.includes('href="privacy/en.html"'));
  assert.ok(privacy.includes('href="ko.html"') && privacy.includes('href="en.html"'));
  for (const html of [home, privacy]) {
    for (const value of ['DevDooly', 'vmdooly@gmail.com', 'noindex, nofollow', '검토용 준비본', 'not an effective or submission-ready policy']) assert.ok(html.includes(value));
    assert.ok(!/<script\b|<iframe\b/i.test(html));
  }
});

test('앱 정책과 공개 원본이 다르면 배포 폴더를 만들기 전에 거절한다', async t => {
  const project = await fixture(t);
  await writeFile(join(project, 'app/src/play/assets/privacy/en.html'), 'different policy');
  await assert.rejects(buildPrivacySite({ projectDir: project }), /원본과 앱 자산이 일치/);
  await assert.rejects(readFile(join(project, 'app/build/privacy-site/index.html')), { code: 'ENOENT' });
});

test('알려지지 않은 산출물은 삭제하거나 배포하지 않고 생성 자체를 멈춘다', async t => {
  const project = await fixture(t);
  const output = join(project, 'app/build/privacy-site');
  await mkdir(output, { recursive: true });
  await writeFile(join(output, 'private.secrets'), 'keep private');
  await writeFile(join(output, 'index.html'), 'keep existing index');
  await assert.rejects(buildPrivacySite({ projectDir: project }), /허용하지 않은 배포 파일/);
  assert.equal(await readFile(join(output, 'private.secrets'), 'utf8'), 'keep private');
  assert.equal(await readFile(join(output, 'index.html'), 'utf8'), 'keep existing index');
  await assert.rejects(buildPrivacySite({ projectDir: project, outputDir: join(project, 'docs') }), /app\/build/);
  await assert.rejects(buildPrivacySite({ projectDir: project, outputDir: join(project, 'app/build') }), /app\/build/);
});

test('공개 설정의 비밀 키·중복 키·인증정보가 있는 주소를 거절한다', () => {
  assert.throws(() => parsePublicMetadata(config + 'playUploadStorePassword=private\n'), /허용되지 않은 키/);
  assert.throws(() => parsePublicMetadata(config + 'playDeveloperName=Duplicate\n'), /중복된 공개 속성/);
  assert.throws(() => parsePublicMetadata(config.replace('https://devdooly.github.io/', 'https://user:password@devdooly.github.io/')), /인증정보/);
  assert.throws(() => parsePublicMetadata(config.replace('=DevDooly\n', '=DevDooly \n')), /올바르지 않습니다/);
});

test('확정 정책은 기존 최종 검사를 통과해야 하며 상태가 다른 문서는 배포하지 않는다', async t => {
  const project = await fixture(t, 'final');
  const result = await buildPrivacySite({ projectDir: project });
  assert.equal(result.policyStatus, 'final');
  const home = await readFile(join(result.outputDir, 'index.html'), 'utf8');
  assert.ok(!home.includes('검토용 준비본'));
  assert.ok(home.includes('Google Play 출시나 심사 승인을 의미하지는 않습니다'));
  const source = join(project, 'docs/privacy/en.html');
  const edited = (await readFile(source, 'utf8')).replace('content="final"', 'content="draft"');
  await Promise.all([writeFile(source, edited), writeFile(join(project, 'app/src/play/assets/privacy/en.html'), edited)]);
  await assert.rejects(buildPrivacySite({ projectDir: project }), /공개 상태가 다릅니다/);
  const incomplete = await fixture(t);
  for (const language of ['ko', 'en']) {
    const path = join(incomplete, 'docs/privacy', language + '.html');
    const html = (await readFile(path, 'utf8')).replace('content="draft"', 'content="final"');
    await Promise.all([writeFile(path, html), writeFile(join(incomplete, 'app/src/play/assets/privacy', language + '.html'), html)]);
  }
  await assert.rejects(buildPrivacySite({ projectDir: incomplete }), /시행일/);
});
