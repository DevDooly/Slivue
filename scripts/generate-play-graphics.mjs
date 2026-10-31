import assert from 'node:assert/strict';
import { readFile, writeFile } from 'node:fs/promises';
import { createRequire } from 'node:module';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

// SVG 원본과 기존 아이콘으로 기능 그래픽을 기계 렌더링한다. 네트워크·패키지 설치는 수행하지 않는다.
// 사용: node scripts/generate-play-graphics.mjs [--sharp-module <Sharp 모듈 경로>] [--check]
// 모듈 경로를 주지 않으면 일반 require/NODE_PATH에서 설치된 sharp를 찾는다.
const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const require = createRequire(import.meta.url);
const args = process.argv.slice(2);
let sharpModule;
let checkOnly = false;
for (let index = 0; index < args.length; index++) {
  if (args[index] === '--sharp-module') {
    assert.ok(args[index + 1] && !args[index + 1].startsWith('--'), '--sharp-module 뒤에 모듈 경로를 지정하세요.');
    sharpModule = resolve(args[++index]);
  } else if (args[index] === '--check') checkOnly = true;
  else throw new Error(`지원하지 않는 인자: ${args[index]}`);
}

let sharp;
try {
  sharp = require(sharpModule || 'sharp');
} catch (cause) {
  throw new Error('설치된 Sharp를 찾지 못했습니다. --sharp-module 경로 또는 NODE_PATH를 지정하세요. 자동 설치하지 않습니다.', { cause });
}

const graphicsDir = resolve(root, 'docs/google-play/graphics');
const iconReference = '../../icons/play_store/notification_edge_sliding_panel_512.png';
const icon = await readFile(resolve(root, 'docs/icons/play_store/notification_edge_sliding_panel_512.png'));
const iconMeta = await sharp(icon).metadata();
assert.equal(iconMeta.width, 512, '아이콘 원본 너비가 바뀌었습니다.');
assert.equal(iconMeta.height, 512, '아이콘 원본 높이가 바뀌었습니다.');

for (const locale of ['ko', 'en']) {
  const svgPath = resolve(graphicsDir, `feature-${locale}.svg`);
  const pngPath = resolve(graphicsDir, `feature-${locale}.png`);
  const svg = await readFile(svgPath, 'utf8');
  assert.ok(svg.includes('width="1024" height="500"'), 'SVG 기능 그래픽 규격은 1024×500이어야 합니다.');
  assert.equal((svg.match(/<image /g) || []).length, 1, '기존 아이콘 하나만 포함해야 합니다.');
  assert.ok(svg.includes(`href="${iconReference}"`), '정해진 아이콘 원본을 참조해야 합니다.');
  assert.ok(!/<script|<foreignObject|(?:href|src)="(?:https?:|file:)/i.test(svg), '외부 리소스나 실행 코드를 포함할 수 없습니다.');

  // 원본 SVG는 상대 경로를 유지하고, PNG 렌더링에서만 아이콘을 내장해 환경별 외부 파일 접근 차이를 없앤다.
  const inlined = svg.replace(`href="${iconReference}"`, `href="data:image/png;base64,${icon.toString('base64')}"`);
  const expected = await sharp(Buffer.from(inlined))
    .flatten({ background: '#071022' })
    .removeAlpha()
    .toColourspace('srgb')
    .png({ compressionLevel: 9, palette: false })
    .toBuffer();
  if (checkOnly) {
    const actual = await readFile(pngPath);
    // PNG 인코더 메타데이터보다 실제 RGB 픽셀을 비교한다.
    const expectedPixels = await sharp(expected).raw().toBuffer();
    const actualPixels = await sharp(actual).raw().toBuffer();
    assert.ok(actualPixels.equals(expectedPixels), `${locale}: SVG/아이콘/현재 폰트의 렌더 결과와 PNG가 다릅니다.`);
  } else await writeFile(pngPath, expected);

  const metadata = await sharp(pngPath).metadata();
  assert.equal(metadata.format, 'png', 'PNG 파일이어야 합니다.');
  assert.equal(metadata.width, 1024, '기능 그래픽 너비가 다릅니다.');
  assert.equal(metadata.height, 500, '기능 그래픽 높이가 다릅니다.');
  assert.equal(metadata.channels, 3, '알파 없는 RGB 3채널이어야 합니다.');
  assert.equal(metadata.hasAlpha, false, '투명도는 허용하지 않습니다.');
  assert.equal(metadata.space, 'srgb', 'sRGB 색 공간이어야 합니다.');
  assert.equal(metadata.depth, 'uchar', '8비트 RGB여야 합니다.');
  console.log(`${locale}: ${metadata.width}×${metadata.height}, RGB, 8비트, 알파 없음${checkOnly ? ', 원본 렌더 일치' : ', 생성 완료'}`);
}

console.log('한국어 SVG는 Windows 맑은 고딕, 영문은 Segoe UI를 우선 사용합니다. 다른 OS/폰트에서는 줄 모양을 직접 재검수하세요.');
