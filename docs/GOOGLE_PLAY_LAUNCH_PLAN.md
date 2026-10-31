# Slivue Google Play 최초 출시 계획

## 2026-10-31 구현 착수와 결정

최초 계획 아래의 2026-09-05 소스 진단은 당시 기록으로 보존한다. 현재 작업은 v1.3.27 후보에서 `direct`·`play` 분리, compile/target 36, Play 전용 `com.devdooly.slivue`, 자체 APK 설치/온라인 폰트/네트워크 권한/진단 내보내기 제외, 고지 동의·중지·철회 흐름을 구현·검증한다.

사용자는 아직 Play 앱을 만들지 않았으며 공개 개발자 이름·문의 이메일은 미정이다. 기존 GitHub판은 옛 패키지와 서명 계보를 유지하므로 Play판으로 덮어쓰는 업데이트·자동 데이터 이전은 하지 않는다. 정책은 공개/앱 전문이 같은 한·영 `draft`이며 호스팅·서명 등록·제출은 미실행이다. 현재 완료 증거와 남은 P0는 [제출 준비·심사 점검표](GOOGLE_PLAY_SUBMISSION_CHECKLIST.md)에서 확인한다.

## 1. 목표와 판단 기준

- 조사 기준일: **2026-09-05**.
- 소스 기준: **v1.3.19 / versionCode 149 / 커밋 `4837ea2`**.
- 1차 목표: 광고 없이 Google Play 심사를 통과하고 안정적으로 운영한다.
- 2차 목표: 실제 사용성과 운영 지표를 확인한 뒤 수익화 적합성을 판단한다.
- 이 문서는 **실행 전 계획**이다. 계정 생성·결제·약관 동의·Play 제출·서명키 등록·저장소 비공개 전환은 수행하지 않았다. 승인 또는 출시일을 보장하지 않는다.

판정 표기: **확인**은 소스·산출물·공식 문서에서 확인한 사실, **권고**는 이 앱에 적용한 설계 판단, **미확인**은 계정·실기기·최종 제출물에서 추가 확인할 사항이다. 정책은 제출 직전에 다시 확인한다.

함께 읽을 문서:

- [개인정보·권한·Data safety 준비서](GOOGLE_PLAY_PRIVACY_AND_DATA_SAFETY.md)
- [서명·공개 소스·수익화 전략](GOOGLE_PLAY_SIGNING_AND_MONETIZATION.md)
- [기존 개발 참조](DEVELOPMENT_REFERENCE.md), [One UI 실기기 체크리스트](ONE_UI_RELEASE_CHECKLIST.md)

### 요약 결론

현재 GitHub APK를 그대로 Play에 올리는 것은 권장하지 않는다. 우선순위는 **계정·서명 경로 확인 → Play 전용 산출물 분리 → API 36 대응 → 개인정보·권한 흐름 → 테스트·심사 자료 → 출시**다. 광고 SDK 추가와 GitHub 비공개 전환은 지금 하지 않는다.

특히 현재 신규 모바일 앱 제출 기준은 **Android 16 / API 36 이상**이다. 2026-08-31부터 적용되며, 현재 앱의 target 34는 부족하다. 유예 안내가 있지만 계정 적용·승인 여부가 미확인이므로 출시 계획을 유예에 의존시키지 않는다. [Play 대상 API 정책](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en)

## 2. 현재 소스와 출시 준비도

P0는 해결 또는 적격성 확인 전 제출하지 않는 항목, P1은 공개 출시 전 품질·운영 보완 항목이다. 정책 위반 확정과 심사 위험은 구분한다.

| 우선순위 | 확인한 현재 상태 | 필요한 조치·완료 기준 |
| --- | --- | --- |
| P0 | [빌드 설정](../app/build.gradle.kts): compile/target 34, min 26, AGP 8.6.0 | SDK 36과 호환 도구 조합으로 이행, API 26~36 회귀 검증 |
| P0 | [Manifest](../app/src/main/AndroidManifest.xml)에 `REQUEST_INSTALL_PACKAGES` 포함 | Play 최종 manifest에서 제거, 자체 설치 경로도 제외 |
| P0 | [업데이터](../app/src/main/java/com/devdooly/notificationedge/data/updater/AppUpdateManager.kt)가 공개 GitHub에서 APK를 내려받아 설치 | Play용은 Play 업데이트만 사용. 버튼만 숨기는 방식은 불충분 |
| P0 | [CI](../.github/workflows/release.yml)는 APK 빌드·회전 서명·GitHub 게시 경로 | 별도 Play AAB 생성·검증 및 내부 테스트 경로 마련 |
| P0 | [기존 서명 계보](SIGNING_KEY_ROTATION_RUNBOOK.md): 노출된 과거 키와 새 키가 API별로 사용됨 | Play 최초 등록에서 계보 수용 여부 확인 후 실제 Play 서명 APK로 교차 업데이트 시험 |
| P0 | [권한 화면](../app/src/main/java/com/devdooly/notificationedge/ui/settings/SettingsScreen.kt)은 시스템 설정으로 바로 이동 | 알림 접근 전 명확한 앱 내부 설명·동의·거절 경로, 개인정보처리방침 링크 추가 |
| P0 | 앱 내부 개인정보처리방침·공개 정책 페이지 및 Data safety 신고 자료 미구축 | 최종 Play 빌드의 실제 처리·전송·백업을 기준으로 작성·검증 |
| P0 | [서비스](../app/src/main/java/com/devdooly/notificationedge/service/EdgeOverlayService.kt)는 `specialUse` FGS, MIN 우선순위 알림 | 사용 목적·필요성·사용자 중지 방법 정비, FGS 신고와 시연자료 준비 |
| P1 | `POST_NOTIFICATIONS`는 선언되어 있으나 런타임 요청 경로를 찾지 못함 | 알림 접근 권한과 알림 보내기 권한을 구분해 안내·요청·거부 시험 |
| P1 | [진단 기능](../app/src/main/java/com/devdooly/notificationedge/ui/settings/GeneralSettingsCards.kt)은 릴리스에도 노출 | 첫 Play 버전에서는 원문 진단 내보내기 제외 권고. 마스킹을 완전 익명화로 설명하지 않기 |
| P1 | [백업 규칙](../app/src/main/res/xml/data_extraction_rules.xml)은 설정 파일 전체를 클라우드 백업하고 D2D 범위 미명시 | 발견 앱 목록·차단어·사용자 폰트·동의 상태의 저장/삭제/백업 정책 명시 |
| P1 | [Google Fonts 제공자](../app/src/main/java/com/devdooly/notificationedge/ui/theme/FontOption.kt)를 통한 선택 서체 제공 | 외부 통신·제3자 처리·라이선스 확인. 첫 버전에서 시스템/사용자 서체만 유지하는 간소화도 검토 |
| P1 | [기존 실기기 계획](ONE_UI_RELEASE_CHECKLIST.md)은 미완료 항목 존재 | One UI·Pixel, 권한 철회·재부팅·잠금·뒤로가기·배터리·접근성 확인 |
| P1 | 라이선스는 Apache-2.0, 별도 의존성/자산 권리 대장 미구축 | 라이선스·NOTICE·아이콘·스크린샷·서체 출처 정리 |

현재 유리한 점은 로그인·광고·분석 SDK 없이 시작할 수 있고, HTTPS 업데이트 검증·민감 로그 억제·외부 제어 기본 거부·85개 단위 테스트를 이미 갖춘 것이다. 다만 GitHub 릴리스 성공은 Play 적격성이나 실기기 권한 심사 통과를 뜻하지 않는다.

## 3. 먼저 확인할 개발자 계정

### 소유자가 확인할 항목

- [ ] Play Console 계정 보유 여부, 개인/조직 유형, 생성일, 계정 국가
- [ ] 실명·주소·연락처·결제 프로필 검증 상태
- [ ] 공개해도 되는 지원 이메일·개발자 전화번호·주소 범위
- [ ] 초기 배포 국가와 지원 언어
- [ ] 해당 시 실제 테스터 12명 이상 모집 가능 여부
- [ ] `com.devdooly.notificationedge`의 Play 등록·소유권 상태

가입은 18세 이상이며 등록비는 일회성 US$25로 안내된다. 실제 결제 통화·세금·카드 조건은 결제 화면에서 확인한다. 신분증·증빙·주소·비밀번호는 Git/Slack 문서에 보관하지 않는다. [개발자 계정 시작](https://support.google.com/googleplay/android-developer/answer/6112435?hl=en)

개인 계정으로도 수익화할 수 있다. 광고 계획 때문에 조직 계정을 선택하거나 테스트 규칙을 피하려 허위 조직 정보를 쓰지 않는다. 조직은 일반적으로 D‑U‑N‑S와 실재 조직 증빙이 필요하며 발급 대기 시간이 발생할 수 있다. [계정 유형](https://support.google.com/googleplay/android-developer/answer/10840893?hl=en), [계정 필수 정보](https://support.google.com/googleplay/android-developer/answer/13628312?hl=en)

**한국 계정이면 추가 확인:** 공개 개발자 전화번호와 한국 신원 인증 요건을 확인한다. 유료 앱/IAP의 사업자·통신판매 관련 Play 요구와 광고만 도입할 때의 국내 사업·세무 의무를 동일한 것으로 단정하지 않는다. 국내 법적·세무 판단은 실제 거주지·사업 형태에 맞춰 확인한다. [한국 연락처 요건](https://support.google.com/googleplay/android-developer/answer/6223646?hl=en-GB), [한국 신원 증빙](https://support.google.com/googleplay/android-developer/answer/15633622?co=GENIE.CountryCode%3DKR&hl=en)

### 신규 개인 계정인 경우

2023-11-13 이후 생성된 개인 계정은 최소 12명이 연속 14일 비공개 테스트에 참여한 상태로 프로덕션 액세스를 신청해야 한다. 실제 사용·피드백·수정 이력을 남기며, 숫자만 채우거나 14일 경과를 자동 승인으로 보지 않는다. 내부 테스트는 이 조건을 대신하지 않는다. 신청 검토는 통상 7일 이내 안내지만 더 오래 걸리거나 추가 테스트를 요구받을 수 있다. 이탈을 고려한 추가 테스터 모집은 자체 운영 권고다. [공식 테스트 조건](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en)

신규 개인 계정의 실기기 확인은 계정 소유자가 Play Console 모바일 앱과 Android 10 이상 비루팅 실물 기기로 수행한다. 에뮬레이터 회귀 테스트와 별개다. [실기기 확인](https://support.google.com/googleplay/android-developer/answer/14316361?hl=en)

### 2026년 Android 개발자 검증

2026-09-30부터 브라질·인도네시아·싱가포르·태국의 참여 앱스토어를 통한 Android 7 이상 인증 기기 설치에 개발자 검증이 적용되는 일정이 안내되어 있다. 2027년 이후 글로벌 확대 예정이지만 한국의 상세 시행일은 미확인이다. 한국의 동일 날짜 일괄 적용으로 해석하지 않는다. Play와 GitHub를 병행하면 패키지·각 배포 서명키 등록 상태를 함께 확인한다. Slivue가 이미 자동 등록되었다고 가정하지 않는다. [검증 개요](https://developer.android.com/developer-verification), [최신 시행 안내](https://support.google.com/android-developer-console/answer/16561738?hl=en)

## 4. 실행 순서와 작업 묶음

### 단계 0 — 계정·서명·출시 범위 확정

담당: 계정 소유자 + 개발 담당.

1. 위 계정 질문을 확인한다. 첫 공개판은 **무료·무광고·별도 계정 없음**을 권고한다.
2. [서명 결정 게이트](GOOGLE_PLAY_SIGNING_AND_MONETIZATION.md)를 먼저 수행한다.
3. 패키지명과 기존 데이터 유지가 가능한지 확인한다. 불가능하면 기존 사용자 영향과 새 패키지 대안을 소유자가 결정한다.
4. 지원 이메일·개인정보처리방침 호스팅·초기 국가·실제 테스트 참여자를 준비한다.

완료 조건: 계정별 요구사항과 서명 실험 경로가 기록되어 있고, 되돌리기 어려운 패키지/앱 서명키 결정을 추측으로 하지 않는다.

### 단계 1 — Play 전용 빌드 분리

주요 대상: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, 업데이터·업데이트 카드, CI.

권고 구조는 `play`와 `direct` 제품 변형이다. 이름은 구현 때 확정하며 아직 존재하는 명령으로 취급하지 않는다.

| 항목 | Play 산출물 | 직접 배포 산출물 |
| --- | --- | --- |
| 배포 형식 | AAB | APK |
| 업데이트 | Play 스토어 또는 공식 인앱 업데이트 | 기존 GitHub 경로 유지 여부 별도 운영 |
| APK 설치 권한/코드 | 포함하지 않음 | 기존 보안 검증 유지 |
| 서명 | 별도 업로드 키 → Play 앱 서명 | 검증된 직접 배포 서명/계보 |
| 공통 사항 | 확정된 패키지·데이터 정책, 버전 코드 충돌 방지 | 동일 |

Play 배포 앱은 Play 이외 방식으로 스스로 업데이트하면 안 된다. 앱 설치가 핵심 기능이 아닌 Slivue의 자체 업데이터를 설치 권한 예외로 정당화하지 않는다. 최종 AAB의 merged manifest·DEX·UI를 검사해 설치 권한·APK 설치 인텐트·GitHub 업데이트 경로가 제거됐는지 확인한다. 웹에 소스를 공개하는 것과 앱의 자기 업데이트는 별개다. [설치 권한 정책](https://support.google.com/googleplay/android-developer/answer/12085295?hl=en), [기기·네트워크 악용 정책](https://support.google.com/googleplay/android-developer/answer/16559646)

신규 Play 앱에는 AAB가 필요하다. 기존 `apksigner` APK 계보 적용 스크립트를 AAB 서명 절차로 재사용하지 않는다. 처음에는 자동 프로덕션 게시 대신 내부 테스트 후보 생성과 검증까지만 자동화한다. [AAB 개요](https://developer.android.com/guide/app-bundle)

완료 조건: 두 산출물의 차이를 자동 검사하고, Play 설치 권한이 0개이며 앱 버전 코드는 기존 149 및 이미 사용한 모든 코드보다 높다.

### 단계 2 — API 36·런타임 호환성

1. Android 16 지원 안정 버전의 AGP·Gradle·Kotlin·JDK 조합을 선정한다. 현재 AGP 8.6.0에서 숫자만 바꾸고 경고를 무시하지 않는다. [Android 16 SDK 설정](https://developer.android.com/about/versions/16/setup-sdk)
2. `compileSdk`·`targetSdk`를 36 이상으로 이행한다. `minSdk` 변경은 서명·기존 기기 영향 검토 후 별도 결정한다.
3. API 35+의 백그라운드 FGS 시작 예외를 재검증한다. 오버레이 권한 보유만으로 항상 허용되지 않으며, 해당 예외는 보이는 오버레이 창까지 요구한다. 부팅은 다른 예외가 적용될 수 있으므로 `specialUse` 부팅 복구를 일괄 금지/허용으로 단정하지 않는다. [Android 15 동작 변경](https://developer.android.com/about/versions/15/behavior-changes-15)
4. API 36의 edge-to-edge, 지원되는 뒤로가기 API, 큰 화면·회전·분할 화면을 확인한다. 설정·투명 패널·키보드의 인셋 및 종료를 각각 시험한다. [Android 16 동작 변경](https://developer.android.com/about/versions/16/behavior-changes-16)
5. Android 15+의 OTP 등 민감 알림 가림을 정상 보안 동작으로 처리한다. 이를 우회하거나 스토어에서 모든 알림 내용이 항상 보인다고 광고하지 않는다. [민감 알림 보호](https://developer.android.com/about/versions/15/behavior-changes-all?hl=en)

#### 16KB 확인 결과와 남은 검증

현재 로컬 v1.3.19 APK에 `libandroidx.graphics.path.so`, `libdatastore_shared_counter.so`가 각 4개 ABI로 포함돼 있다. 따라서 앱 코드가 Kotlin이라는 이유만으로 네이티브 검증을 생략할 수 없다. 읽기 전용 ELF 검사에서 arm64-v8a·x86_64의 모든 `PT_LOAD` 정렬값은 **16384**였다.

이는 ELF 일부 확인이지 Play AAB 전체 승인 증거는 아니다. SDK 36 이행 후 **AAB/APK ZIP 정렬, 모든 최종 네이티브 의존성, 16KB 환경 실행과 Play 업로드 진단**을 다시 확인한다. 최신 기술 안내에는 API 35+의 16KB 지원과 미지원 업데이트 차단일 2027-02-01이 적혀 있으나, 신규 앱의 무조건 유예로 해석하지 않는다. [16KB 가이드](https://developer.android.com/guide/practices/page-sizes), [Play 기술 품질 요건](https://support.google.com/googleplay/android-developer/answer/17492799?hl=en)

완료 조건: API 26·31·34·35·36 및 16KB 환경의 필수 시나리오 통과. 실제 One UI 결과 별도 기록.

### 단계 3 — 권한·개인정보·서비스 통제

세부 설계와 작성 초안은 [개인정보 준비서](GOOGLE_PLAY_PRIVACY_AND_DATA_SAFETY.md)를 따른다.

- 알림 접근 전에 처리하는 정보·목적·백그라운드 동작·외부 전송 여부를 설명하고 명시적 동의를 받는다.
- 거부·권한 철회 후 계속 수집하지 않고 재동의를 강요하는 반복 이동을 피한다.
- 알림 접근, 다른 앱 위 표시, 알림 보내기를 별도로 보여준다. 배터리 예외는 선택으로 유지한다.
- 핸들·라이팅뿐 아니라 **서비스 전체 중지**가 명확해야 한다. FGS 알림의 MIN 우선순위를 재검토하고 사용자가 실행 사실과 중지 방법을 알 수 있게 한다. [FGS 시작 안내](https://developer.android.com/develop/background-work/services/fgs/launch)
- `specialUse`가 왜 필요한지, 사용자가 언제 시작·중지하는지, 중단 시 어떤 기능이 동작하지 않는지 신고·영상으로 설명한다. 허용 여부는 심사 대상이며 자동 승인으로 보지 않는다.
- 개인정보처리방침 링크·Data safety·실제 코드·스토어 설명이 일치해야 한다. `데이터 수집 없음`은 최종 네트워크/SDK/백업 검증 전에 확정하지 않는다.

완료 조건: 동의 전·후·철회 후 동작, 서비스 중지, 데이터 삭제, 백업 복구, 민감 로그 부재를 검증하고 신고 초안에 미확인 항목이 없다.

### 단계 4 — 스토어 자료와 심사 재현성

| 준비물 | 계획 |
| --- | --- |
| 앱 이름/설명 | Slivue. 등록명 30자·짧은 설명 80자·상세 설명 4,000자 한도 안에서 실제 기능만 작성 |
| 아이콘 | 기존 [512px 아이콘](icons/play_store/notification_edge_sliding_panel_512.png)을 규격·권리·가독성 재검수 |
| 기능 그래픽 | 1024×500, JPEG 또는 알파 없는 24비트 PNG 새 제작 |
| 스크린샷 | 최소 2장 요건 확인. 권고는 실제 화면 4~6장: 패널·답장·맞춤 설정·권한 안내 |
| 개인정보처리방침 | 인증·지역 제한 없는 안정적인 공개 웹페이지, 인앱 링크 |
| 지원 | 실제 관리할 이메일, 지역별 개발자 공개 연락처 |
| App content | 광고 여부·대상 연령·콘텐츠 등급·Data safety·앱 접근·FGS/요청된 권한 선언 |
| 심사 메모 | 권한 허용 → 샘플 알림 생성 → 핸들 열기 → 지원 알림의 답장 → 전체 중지 절차 |

아이콘은 512×512의 32비트 PNG, 최대 1,024KB 기준을 확인한다. 스크린샷은 JPEG 또는 알파 없는 24비트 PNG, 각 변 320~3840px, 긴 변이 짧은 변의 2배 이하 조건을 확인한다. 권장 품질은 1080×1920 세로 또는 1920×1080 가로 이상의 실제 화면 4장 이상이며, 최소 제출 요건과 구분한다. 허구의 기능이나 실제 타인 대화를 사용하지 않고, README의 콘셉트 이미지를 실사용 화면인 것처럼 제출하지 않는다. [스토어 그래픽](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en), [등록 정보](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en), [심사 준비](https://support.google.com/googleplay/android-developer/answer/9859455?hl=en)

로그인은 없지만 권한·알림이 없으면 비어 보이므로 심사자가 기능을 직접 재현할 수 있어야 한다. 샘플 알림·테스트 모드는 명확히 표시하고 실사용 정보가 필요 없는 절차를 제공한다. 대상 연령과 IARC 등급을 추측으로 확정하지 않으며 어린이 대상 여부는 실제 제품 설계·홍보에 맞게 답한다.

### 단계 5 — 테스트·출시·초기 운영

1. 업로드 키로 서명한 AAB를 내부 테스트 트랙에 올린다.
2. Play가 서명한 실제 설치물로 신규 설치·직접 배포판에서의 업데이트를 확인한다. **Internal App Sharing의 별도 테스트 서명은 교차 업데이트 검증 대체재가 아니다.**
3. 적용되는 경우 12명/14일 비공개 테스트와 실제 피드백 반영을 수행한다.
4. Pre-launch report, App content, 패키지·서명·versionCode·16KB 경고를 해결한다.
5. 프로덕션 액세스 신청·심사 결과를 확인하고 실제 제공되는 콘솔 출시 제어를 사용한다. 신규 첫 출시에서 업데이트용 비율 롤아웃이 항상 제공된다고 가정하지 않는다.
6. 출시 후 Android vitals·충돌·ANR·기기별 불만·권한 이탈·배터리 문제를 관찰한다. 첫 버전부터 알림 원문을 분석 서버에 보내 지표를 만들지 않는다.
7. 심각한 문제는 배포 중단/공지/더 높은 versionCode의 수정본으로 대응한다. 버전 코드 다운그레이드를 복구 전략으로 삼지 않는다.

## 5. 반드시 남길 테스트 증거

| 시나리오 | 확인할 결과 |
| --- | --- |
| 첫 실행·권한 허용/거부 | 앱으로 복귀, 설명과 실제 상태 일치, 무한 권한 유도 없음 |
| 권한 철회·재동의 | 알림 처리 중단·캐시 정리·재개 정책 준수 |
| 알림 보내기 거부 | FGS/시스템 작업 관리자 동작과 안내가 정확함 |
| 전체 중지·강제 중지 | 백그라운드 기능이 사용자 의사에 반해 재시작하지 않음 |
| 홈·잠금·재부팅·절전 | 허용된 방식의 복구, 잠금 화면 민감 내용 노출 방지 |
| 오버레이·키보드·뒤로가기 | 다른 앱 조작 방해 없음, 새 OS에서도 정상 종료 |
| 권한/결제 등 민감 시스템 화면 | 화면 위를 가리거나 클릭을 유도하지 않음 |
| 메신저·OTP·가려진 알림 | 지원 범위 명확, OS 보호 우회 없음 |
| 16KB·큰 화면·회전 | 충돌·깨짐·터치 위치 오류 없음 |
| API별 Play 교차 업데이트 | 서명·UID·설정·권한 보존 여부를 각각 기록 |
| 네트워크·백업·진단 | Data safety·처리방침과 일치, 원문/토큰 로그 부재 |
| TalkBack·글자 확대·대비 | 핵심 권한·중지·패널 버튼 조작 가능 |

테스터 기록은 기기/API·빌드·단계·기대/실제·재현율·수정 버전으로 남긴다. 실제 메시지·계정·증빙은 공개 이슈에 첨부하지 않는다.

## 6. 일정·비용·완료 조건

다음은 **작업량 추정**이며 심사 약속이 아니다. 서명 호환성 문제나 실제 기기 결함이 나오면 재산정한다.

| 구간 | 예상 범위 | 선행조건 |
| --- | --- | --- |
| 계정/서명/범위 결정 | 수일~수주 | 신원·D‑U‑N‑S·Play 지원 답변 등 외부 대기 가능 |
| 빌드·SDK·권한·문서 보완 | 개발 1~2주 초안 | 서명 실험과 병행 가능하나 프로덕션 결정 전 완료 |
| 내부·실기기 시험 | 수일~1주 | 실제 Play 후보와 One UI 기기 |
| 조건부 비공개 시험 | 최소 연속 14일 + 수정/신청 기간 | 해당 개인 계정, 실제 참여 유지 |
| 심사·게시 | 콘솔 결과에 따름 | 미완료 신고/테스트 없음 |

직접 비용은 Play 등록비, 필요 시 호스팅·도메인·실기기·테스터 운영·법률/세무 검토다. 서버·광고·분석 서비스 비용을 처음부터 추가할 필요는 없다. 외부 테스터 구매를 통과 수단으로 계획하지 않는다.

### 최초 공개 승인 체크리스트

- [ ] 계정 검증과 적용되는 테스트 자격 완료
- [ ] 서명·패키지 결정 및 실제 Play 설치물의 업데이트 시험 완료
- [ ] API 36 대응과 최종 AAB 검증 완료
- [ ] 자체 APK 설치/업데이트 경로 Play 산출물에서 제거
- [ ] 개인정보처리방침·명시 동의·Data safety 확정
- [ ] FGS 필요성 신고·명확한 실행 알림·중지 동작 확인
- [ ] 실제 기기·16KB·Pre-launch report의 주요 문제 해결
- [ ] 스토어 자산·제3자 권리·지원 연락처·심사 절차 준비
- [ ] 광고 없이 제출, 심사 결과 확인 후 게시

## 7. 다음 결정

가장 먼저 필요한 답변은 **Play Console 계정 보유 여부, 개인/조직 유형, 생성 시기, 계정 국가**다. 그다음 공개 지원 연락처·초기 국가·기존 설치본 유지 조건을 확정한다. 광고와 저장소 공개 여부는 첫 출시를 막는 선행 결정으로 만들지 않는다.

정책 재확인 시점은 개발 착수, 첫 AAB 업로드, 프로덕션 제출, 광고 SDK 도입 직전이다. 최신 문서의 2027년 기술 품질 요건도 후속 일정에 반영하되 아직 하지 않은 최적화나 테스트를 완료로 표시하지 않는다.
