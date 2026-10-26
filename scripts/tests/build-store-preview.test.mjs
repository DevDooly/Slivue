import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile, access } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { runInNewContext } from 'node:vm';
import { buildPreview, escapeHtml, fields, locales, renderLocale, validateText } from '../build-store-preview.mjs';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..');

test('소개 필드는 Google Play 글자 수 제한과 일치한다', () => {
  assert.deepEqual(fields.map(field => field.limit), [30, 80, 4000]);
  assert.deepEqual(Object.keys(locales), ['ko-KR', 'en-US']);
});

test('한도와 정확히 같은 글자 수를 허용하고 초과는 거절한다', () => {
  for (const field of fields) {
    assert.equal(validateText('가'.repeat(field.limit), field, 'ko-KR').length, field.limit);
    assert.throws(() => validateText('가'.repeat(field.limit + 1), field, 'ko-KR'), /한도를 초과/);
  }
});

test('빈 내용, 앞뒤 공백과 제어문자를 거절한다', () => {
  for (const text of ['', '\n', ' title', 'title ', 'title\n\n', 'title\t', 'title\u0000']) {
    assert.throws(() => validateText(text, fields[0], 'en-US'));
  }
});

test('CRLF와 파일 마지막 개행을 정규화한다', () => {
  assert.equal(validateText('첫째\r\n둘째\r\n', fields[2], 'ko-KR'), '첫째\n둘째');
  assert.equal(validateText('Slivue\n', fields[0], 'en-US'), 'Slivue');
});

test('제목과 짧은 설명에는 여러 줄을 허용하지 않는다', () => {
  for (const field of fields.slice(0, 2)) {
    assert.throws(() => validateText('first\nsecond', field, 'en-US'), /한 줄/);
  }
});

test('HTML 특수문자는 태그나 속성으로 해석되지 않는다', () => {
  assert.equal(escapeHtml('<script>"&\'</script>'), '&lt;script&gt;&quot;&amp;&#39;&lt;/script&gt;');
  const rendered = renderLocale('en-US', { title: '<img src=x>', short: 'A & B', full: '</pre><script>bad()</script>' });
  assert.ok(!rendered.includes('<script>'));
  assert.ok(rendered.includes('&lt;/pre&gt;&lt;script&gt;'));
  assert.ok(rendered.includes('A &amp; B'));
});

test('두 언어에서 모든 원문 링크·복사 대상·문서 언어를 제공한다', () => {
  for (const [locale, ui] of Object.entries(locales)) {
    const rendered = renderLocale(locale, { title: 'Slivue', short: 'Short', full: 'Full' });
    assert.ok(rendered.includes(`lang="${ui.lang}"`));
    for (const field of fields) {
      assert.ok(rendered.includes(`data-copy="${locale}-${field.key}"`));
      assert.ok(rendered.includes(`id="${locale}-${field.key}"`));
      assert.ok(rendered.includes(`href="listings/${locale}/${field.file}"`));
    }
  }
});

test('지원하지 않는 언어를 거절한다', () => {
  assert.throws(() => renderLocale('unsupported', {}), /지원하지 않는 언어/);
});

test('저장된 미리보기는 실제 한·영 소개 원문과 일치한다', async () => {
  const html = await buildPreview({ check: true });
  assert.ok(html.startsWith('<!doctype html>'));
  assert.equal((html.match(/data-locale="/g) || []).length, 2);
  assert.ok(!html.includes('<!-- LISTINGS -->'));
});

// 실제 브라우저가 아닌 최소 DOM 대역으로 언어·복사 제어 코드만 단위 검증한다.
async function controllerFixture({ language = 'ko-KR', hash = '', clipboard, secure = true, selectionAvailable = true } = {}) {
  const html = await readFile(resolve(root, 'docs/google-play/index.html'), 'utf8');
  const script = html.match(/<script>([\s\S]*?)<\/script>/)[1];
  const sections = Object.entries(locales).map(([id, ui]) => ({
    id, lang: ui.lang, hidden: false,
    dataset: { copied: ui.copied, fallback: ui.fallback, failed: ui.failed },
    querySelector: () => ({ textContent: ui.intro }),
  }));
  const links = sections.map(section => ({
    hash: '#' + section.id, attributes: {},
    setAttribute(name, value) { this.attributes[name] = value; },
    removeAttribute(name) { delete this.attributes[name]; },
  }));
  const buttons = sections.flatMap(section => fields.map(field => ({
    dataset: { copy: `${section.id}-${field.key}` },
    closest: () => section,
    addEventListener(type, callback) { this[type] = callback; },
  })));
  const targets = Object.fromEntries(buttons.map(button => [button.dataset.copy, { textContent: `copy:${button.dataset.copy}` }]));
  const status = { textContent: '' };
  const selection = {
    removeAllRanges() {},
    addRange(range) { this.range = range; },
  };
  const listeners = {};
  const document = {
    documentElement: {}, title: '',
    querySelectorAll(selector) {
      return ({ '[data-locale]': sections, '.language-nav a': links, '[data-copy]': buttons })[selector];
    },
    querySelector: () => status,
    getElementById: id => targets[id],
    createRange: () => ({ selectNodeContents(node) { this.selected = node; } }),
  };
  const location = { hash };
  runInNewContext(script, {
    document, location, navigator: { language, clipboard },
    window: { isSecureContext: secure, getSelection: () => selectionAvailable ? selection : null },
    addEventListener: (type, callback) => { listeners[type] = callback; },
    setTimeout: () => 1, clearTimeout() {},
  });
  return { document, location, sections, links, buttons, targets, status, selection, listeners };
}

test('한국어 브라우저는 한국어, 미지원 언어는 영어로 시작한다', async () => {
  for (const [language, expected] of [['ko-KR', 'ko-KR'], ['ko', 'ko-KR'], ['en-GB', 'en-US'], ['fr-FR', 'en-US']]) {
    const fixture = await controllerFixture({ language });
    assert.deepEqual(fixture.sections.filter(section => !section.hidden).map(section => section.id), [expected]);
    assert.equal(fixture.document.documentElement.lang, locales[expected].lang);
    assert.equal(fixture.links.filter(link => link.attributes['aria-current'] === 'true').length, 1);
  }
});

test('직접 언어 링크·양방향 전환·본문 링크에서 선택 언어가 유지된다', async () => {
  const fixture = await controllerFixture({ language: 'ko-KR', hash: '#en-US' });
  assert.equal(fixture.sections.find(section => !section.hidden).id, 'en-US');
  for (const expected of ['ko-KR', 'en-US', 'ko-KR']) {
    fixture.location.hash = '#' + expected;
    fixture.listeners.hashchange();
    assert.equal(fixture.sections.find(section => !section.hidden).id, expected);
    assert.equal(fixture.document.documentElement.lang, locales[expected].lang);
    assert.equal(fixture.document.title, 'Slivue · ' + locales[expected].intro);
  }
  fixture.location.hash = '#content';
  fixture.listeners.hashchange();
  assert.equal(fixture.sections.find(section => !section.hidden).id, 'ko-KR');
});

test('복사 버튼은 선택 항목의 문구만 복사하고 해당 언어로 알린다', async () => {
  let copied;
  const fixture = await controllerFixture({ clipboard: { writeText: async text => { copied = text; } } });
  const button = fixture.buttons.find(candidate => candidate.dataset.copy === 'en-US-short');
  await button.click();
  assert.equal(copied, fixture.targets['en-US-short'].textContent);
  assert.equal(fixture.status.textContent, locales['en-US'].copied);
});

test('클립보드 미지원·비보안·거부 시 수동 복사 안내를 제공한다', async () => {
  for (const options of [
    {},
    { clipboard: { writeText: async () => assert.fail('호출되면 안 됨') }, secure: false },
    { clipboard: { writeText: async () => { throw new Error('거부됨'); } } },
  ]) {
    const fixture = await controllerFixture(options);
    const button = fixture.buttons.find(candidate => candidate.dataset.copy === 'ko-KR-full');
    await button.click();
    assert.equal(fixture.selection.range.selected, fixture.targets['ko-KR-full']);
    assert.equal(fixture.status.textContent, locales['ko-KR'].fallback);
  }
});

test('텍스트 선택 API도 없는 환경에서 수동 복사 안내를 유지한다', async () => {
  const fixture = await controllerFixture({ selectionAvailable: false });
  await fixture.buttons[0].click();
  assert.equal(fixture.status.textContent, locales['ko-KR'].failed);
});

test('양언어 README·적용 안내·미리보기의 로컬 링크와 이미지가 존재한다', async () => {
  for (const file of ['README.md', 'README.en.md', 'docs/GOOGLE_PLAY_LISTING_GUIDE.md', 'docs/google-play/index.html']) {
    const absolute = resolve(root, file);
    const text = await readFile(absolute, 'utf8');
    const targets = file.endsWith('.html')
      ? [...text.matchAll(/(?:href|src)="([^"]+)"/g)].map(match => match[1])
      : [...text.matchAll(/\]\(([^\s)]+)\)/g)].map(match => match[1]).concat([...text.matchAll(/src="([^"]+)"/g)].map(match => match[1]));
    for (const target of targets) {
      if (/^(?:https?:|mailto:|#)/.test(target)) continue;
      await assert.doesNotReject(access(resolve(dirname(absolute), decodeURIComponent(target.split('#')[0]))), `${file}: ${target}`);
    }
  }
});
