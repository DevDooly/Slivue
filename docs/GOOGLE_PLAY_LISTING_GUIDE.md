# Slivue 한·영 Google Play 소개 적용 안내

## 1. 준비한 자료와 범위

작성·공식 안내 확인 기준일: **2026-10-26**, 앱 소스 기준: **v1.3.26**.

Google Play에 입력할 한국어·영어 소개와 언어 전환 미리보기를 준비했다. 이 작업은 Play Console 등록·심사 제출·공개 출시, 신규 개인정보처리방침 확정이나 앱 기능 변경을 수행하지 않는다. 앱 버전은 올리지 않으며 APK 릴리스 태그도 새로 만들지 않는다.

사용자의 명시적 영문 README 요청에 따라 [README.en.md](../README.en.md)는 영문으로 작성했다. 나머지 운영·개발 문서는 기존 한글 규칙을 유지하고, 스토어의 영문 제출 원문은 `.txt`로 분리했다.

| 자료 | 한국어 | 영어 |
| --- | --- | --- |
| GitHub 소개 | [README.md](../README.md) | [README.en.md](../README.en.md) |
| 앱 이름 | [제출 원문](google-play/listings/ko-KR/title.txt) | [제출 원문](google-play/listings/en-US/title.txt) |
| 짧은 설명 | [제출 원문](google-play/listings/ko-KR/short_description.txt) | [제출 원문](google-play/listings/en-US/short_description.txt) |
| 상세 설명 | [제출 원문](google-play/listings/ko-KR/full_description.txt) | [제출 원문](google-play/listings/en-US/full_description.txt) |

앱 이름은 한국어 `Slivue - 엣지 알림 패널`, 영어 `Slivue - Edge Notifications`로 준비했다. 브랜드·패키지·데이터 키·외부 인텐트 식별자는 변경하지 않았다.

## 2. 한·영 미리보기 열기

[미리보기 파일](google-play/index.html)은 저장소를 내려받은 뒤 브라우저에서 열 수 있다. 예를 들어 탐색기에서 `docs/google-play/index.html`을 연다. 아이콘 경로가 유지되도록 HTML 하나만 옮기지 말고 저장소를 함께 받는다.

- 상단 **한국어 / English**로 문구와 화면 언어를 전환한다.
- `index.html#ko-KR`, `index.html#en-US`로 처음 열 언어를 지정한다.
- 언어를 지정하지 않으면 브라우저 언어가 한국어일 때 한국어, 그 외에는 영어로 연다.
- 앱 이름·짧은 설명·상세 설명마다 **복사**와 글자 수를 제공한다.
- 클립보드 API가 차단된 환경에서는 문구를 선택하고 수동 복사 안내를 표시한다.
- JavaScript가 꺼져 있어도 두 언어 원문과 원문 파일 링크를 읽을 수 있다.

이 HTML은 Google Play 화면을 복제한 것이 아니라 **제출 문구를 확인하는 자체 미리보기**다. GitHub는 저장소 내 HTML을 웹페이지로 실행하지 않는다. 이 작업에서 GitHub Pages나 별도 호스팅을 켜지 않았으므로 공개 웹사이트 주소는 아직 없다. 반면 GitHub README의 언어 전환은 저장소에서 바로 사용할 수 있다.

미리보기에는 기존 앱 아이콘만 사용하고, 과거 콘셉트 이미지나 구버전 화면을 새 스토어 스크린샷인 것처럼 넣지 않았다. 실제 한국어·영어 스크린샷과 기능 그래픽은 제출용 빌드가 준비된 뒤 별도로 촬영·검수한다.

## 3. Play Console에 한국어·영어 등록하기

1. Play Console에서 Slivue 앱을 선택한다. 최초 앱 생성이라면 기본 언어는 **한국어(대한민국), ko-KR**를 권장한다. 기존 앱의 기본 언어를 임의로 변경할 필요는 없다.
2. **스토어 등록정보 / 기본 스토어 등록정보** 화면에서 한국어 앱 이름·짧은 설명·상세 설명에 해당 `.txt` 내용을 붙여 넣는다. 파일명이나 마크다운 코드 블록은 붙이지 않는다.
3. **번역 관리 → 언어 선택**에서 **영어(미국), en-US**를 추가하고 적용한다. 계정과 Console 언어에 따라 메뉴 표기는 달라질 수 있다.
4. 영어 등록정보에서 영어 원문 세 항목을 넣고 저장한다.
5. 언어별 실제 스크린샷을 준비했다면 해당 언어의 그래픽 자산도 등록한다. 번역된 그래픽이 없으면 기본 언어 자산이 표시될 수 있다.
6. 앱 콘텐츠·개인정보처리방침·출시 빌드 등 나머지 준비가 끝난 뒤 검토 및 게시 절차를 진행한다. 문구를 저장했다는 사실만으로 앱이 출시되지는 않는다.

Google Play는 추가한 번역과 사용자의 언어 환경이 맞으면 해당 언어 등록정보를 보여준다. **앱 안의 언어 설정**, **GitHub README의 언어 링크**, **스토어 소개 번역**은 서로 별도다. 앱의 영어 UI만 지원한다고 스토어 설명이 자동으로 이 원문으로 바뀌지는 않는다. [공식 번역 안내](https://support.google.com/googleplay/android-developer/answer/9844778?hl=en)

## 4. 원문 수정·검증

실제 원문은 언어별 `.txt` 6개다. 생성된 `index.html`을 직접 편집하면 원문과 어긋나므로 아래 명령으로 다시 만든다. 화면 틀과 복사·언어 전환 동작은 `preview.template.html`, 표시 라벨과 생성 규칙은 `scripts/build-store-preview.mjs`에서 관리한다.

```powershell
node scripts/build-store-preview.mjs
node --test scripts/tests/build-store-preview.test.mjs
node scripts/build-store-preview.mjs --check
```

- 앱 이름 **30자**, 짧은 설명 **80자**, 상세 설명 **4,000자** 이내로 검사한다. UTF-16 길이를 사용해 보수적으로 제한하며, 현재 원문에는 이모지 등 서로게이트 문자가 없다. [공식 등록정보 한도](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en)
- 빈 원문·앞뒤 공백·제어문자·제목과 짧은 설명의 여러 줄을 거절한다. CRLF는 LF로 통일하고 파일 마지막 개행 한 개는 문구에 포함하지 않는다.
- HTML 특수문자를 이스케이프하고, 한·영 복사 대상·파일 링크·글자 수를 자동 생성한다.
- 테스트에서 두 언어 원문과 생성된 HTML의 일치를 검사한다. 관련 파일 변경 시 GitHub Actions에서도 같은 검사를 실행한다.

### 이번 작업의 검증 범위

- 문구 제한·HTML 이스케이프·생성물 일치·문서/이미지 링크 검사 통과.
- 최소 DOM 대역을 사용한 언어 자동 선택·양방향 전환·복사/수동 복사 안내를 포함해 Node 테스트 **15개 통과**. 실제 브라우저 테스트와 구분한다.
- 기존 앱의 `testDebugUnitTest compileDebugKotlin lintRelease assembleRelease` 검증 통과. 앱 소스가 같아 일부 작업은 Gradle의 최신 상태 캐시를 재사용했다.
- 브라우저 도구의 로컬 파일 URL 정책으로 `file://` 미리보기를 열 수 없어 실제 화면 크기별 시각 검증은 수행하지 않았다. 차단된 경로를 다른 UI 경로로 우회하지 않았다.

## 5. 제출 전에 남은 사항

소개 문구는 현재 소스에서 확인한 기능만 설명한다. 빠른 답장은 Android `RemoteInput` 지원 알림에 한정하고, 모든 메신저·전체 화면·PiP 환경의 동작을 보장하지 않는다. 빠른 답장이 원래 메시지 앱으로 전달된다는 점과 선택 글꼴의 다운로드·설정 백업 가능성을 구분했다. 이 문구는 Data safety 최종 판정이나 완성된 개인정보처리방침을 대신하지 않는다.

- **Play용 빌드**: 현재 `targetSdk 34`다. 확인일 기준 신규 모바일 앱은 API 36 이상 대상이 필요하므로 SDK·도구·호환성 이행이 남아 있다. 유예 승인 여부를 가정하지 않는다. [공식 대상 API 안내](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en)
- **업데이트 경로**: 현재 GitHub APK 자체 설치와 `REQUEST_INSTALL_PACKAGES`가 남아 있다. Play용 AAB에서는 설치 권한·자체 업데이트 경로 정리가 필요하다.
- **개인정보와 권한**: 실제 운영 주체·지원 연락처·공개 개인정보처리방침 URL·권한 고지/동의·최종 빌드 기준 Data safety를 확정해야 한다. 지원 이메일을 추측해 만들지 않는다.
- **스토어 자산**: 실제 한·영 스크린샷, 기능 그래픽, 아이콘 규격·권리 점검이 남아 있다.
- **계정·서명·심사**: 개발자 계정 적격성, Play App Signing, 적용되는 테스트 요건과 심사 재현 자료를 준비해야 한다.

상세 순서는 [최초 출시 계획](GOOGLE_PLAY_LAUNCH_PLAN.md), [개인정보·권한·Data safety 준비서](GOOGLE_PLAY_PRIVACY_AND_DATA_SAFETY.md), [서명·수익화 전략](GOOGLE_PLAY_SIGNING_AND_MONETIZATION.md)을 따른다. 이 문서들의 과거 조사 내용은 최종 제출 시 다시 확인한다.
