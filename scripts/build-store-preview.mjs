import assert from 'node:assert/strict';
import { readFile, writeFile } from 'node:fs/promises';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const storeDir = join(root, 'docs', 'google-play');
export const fields = Object.freeze([
  { file: 'title.txt', key: 'title', limit: 30 },
  { file: 'short_description.txt', key: 'short', limit: 80 },
  { file: 'full_description.txt', key: 'full', limit: 4000 },
]);
export const locales = Object.freeze({
  'ko-KR': {
    lang: 'ko', name: '한국어', heading: '화면은 그대로, 알림은 바로.',
    intro: 'Google Play 등록정보 미리보기', copy: '복사', copied: '복사했습니다.',
    fallback: '텍스트를 선택했습니다. Ctrl+C 또는 기기의 복사 메뉴를 사용하세요.',
    failed: '복사할 내용을 길게 누르거나 직접 선택해 복사하세요.',
    description: '화면 가장자리에서 만나는 나만의 알림 패널.',
    note: '소개 문구 준비본입니다. Google Play에 게시된 화면이 아니며, 실제 표시는 스토어 디자인에 따라 달라집니다.',
    labels: ['앱 이름', '짧은 설명', '상세 설명'],
    download: '원문 파일 보기', requirements: 'Android 8.0 이상 · 한국어 / 영어',
    footer: '스토어용 스크린샷·개인정보처리방침·출시용 빌드는 별도로 준비해야 합니다.',
  },
  'en-US': {
    lang: 'en', name: 'English', heading: 'Your screen. Your notifications. In reach.',
    intro: 'Google Play listing preview', copy: 'Copy', copied: 'Copied.',
    fallback: 'Text selected. Press Ctrl+C or use your device’s Copy menu.',
    failed: 'Long-press or select the text to copy it manually.',
    description: 'A notification panel, right at the edge of your screen.',
    note: 'Prepared listing copy, not a published Google Play page. The actual store layout may differ.',
    labels: ['App name', 'Short description', 'Full description'],
    download: 'View source text', requirements: 'Android 8.0+ · Korean / English',
    footer: 'Store screenshots, a privacy policy and a Play-ready build must be prepared separately.',
  },
});

export function escapeHtml(value) {
  return value.replace(/[&<>"']/g, character => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
  })[character]);
}

export function validateText(raw, field, locale) {
  // CRLF를 LF로 통일하고 파일 끝의 한 개 개행만 제외한다. 공백은 조용히 삭제하지 않는다.
  const text = raw.replace(/\r\n/g, '\n').replace(/\n$/, '');
  const label = `${locale}/${field.file}`;
  assert.ok(text.length > 0, `${label}: 내용이 비어 있습니다.`);
  assert.equal(text, text.trim(), `${label}: 앞뒤 공백 또는 중복 끝 개행을 제거하세요.`);
  assert.ok(!/[\u0000-\u0009\u000b-\u001f\u007f]/u.test(text), `${label}: 허용하지 않는 제어문자입니다.`);
  if (field.key !== 'full') assert.ok(!text.includes('\n'), `${label}: 한 줄만 입력하세요.`);
  // UTF-16 기준으로 보수적으로 검사한다. 현재 문구에는 서로게이트 문자가 없다.
  assert.ok(text.length <= field.limit, `${label}: ${text.length}/${field.limit}자로 한도를 초과했습니다.`);
  return text;
}

export function renderLocale(locale, texts) {
  const ui = locales[locale];
  assert.ok(ui, '지원하지 않는 언어입니다.');
  const cards = fields.map((field, index) => {
    const id = `${locale}-${field.key}`;
    return `      <article class="field-card" aria-labelledby="${id}-label">
        <div class="field-header">
          <h2 id="${id}-label">${ui.labels[index]}</h2>
          <div class="field-actions"><span class="count">${texts[field.key].length} / ${field.limit}</span><button type="button" data-copy="${id}" aria-label="${ui.labels[index]} ${ui.copy}">${ui.copy}</button></div>
        </div>
        <pre id="${id}" class="field-text${field.key === 'full' ? ' full-text' : ''}">${escapeHtml(texts[field.key])}</pre>
        <a class="source-link" href="listings/${locale}/${field.file}">${ui.download}<span aria-hidden="true"> ↗</span></a>
      </article>`;
  }).join('\n');
  return `    <section id="${locale}" lang="${ui.lang}" data-locale="${locale}" data-copied="${escapeHtml(ui.copied)}" data-fallback="${escapeHtml(ui.fallback)}" data-failed="${escapeHtml(ui.failed)}" aria-labelledby="${locale}-heading">
      <div class="hero">
        <img src="../icons/play_store/notification_edge_sliding_panel_512.png" alt="Slivue" width="112" height="112">
        <div><p class="eyebrow">${ui.intro}</p><h1 id="${locale}-heading">${ui.heading}</h1><p class="tagline">${ui.description}</p><p class="requirements">${ui.requirements}</p></div>
      </div>
      <p class="notice">${ui.note}</p>
${cards}
      <p class="footer-note">${ui.footer}</p>
    </section>`;
}

export async function buildPreview({ check = false } = {}) {
  const sections = [];
  for (const locale of Object.keys(locales)) {
    const texts = {};
    for (const field of fields) {
      texts[field.key] = validateText(await readFile(join(storeDir, 'listings', locale, field.file), 'utf8'), field, locale);
      console.log(`${locale}/${field.file}: ${texts[field.key].length}/${field.limit}`);
    }
    sections.push(renderLocale(locale, texts));
  }
  const template = (await readFile(join(storeDir, 'preview.template.html'), 'utf8')).replace(/\r\n/g, '\n');
  assert.equal(template.split('<!-- LISTINGS -->').length, 2, '미리보기 삽입 위치는 한 개여야 합니다.');
  const output = template.replace('<!-- LISTINGS -->', sections.join('\n'));
  const outputPath = join(storeDir, 'index.html');
  if (check) {
    assert.equal((await readFile(outputPath, 'utf8')).replace(/\r\n/g, '\n'), output, '소개 원문과 미리보기가 다릅니다. node scripts/build-store-preview.mjs를 실행하세요.');
    console.log('한·영 소개 원문과 미리보기 일치 확인 완료');
  } else {
    await writeFile(outputPath, output, 'utf8');
    console.log('docs/google-play/index.html 생성 완료');
  }
  return output;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const args = process.argv.slice(2);
  assert.ok(args.length === 0 || (args.length === 1 && args[0] === '--check'), '허용 인자: --check');
  await buildPreview({ check: args.includes('--check') });
}
