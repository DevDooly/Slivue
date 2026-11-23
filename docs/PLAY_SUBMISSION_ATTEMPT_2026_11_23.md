# Slivue Play 실제 제출 요청 점검 — 2026-11-23

## 결과

사용자가 **실제 제출을 지시**했다. 제출 권한이 없어서 멈춘 것이 아니라, 아래 실행 환경과 제출 산출물 조건이 충족되지 않아 **실제 Console 접속·앱 등록·AAB 업로드·심사 제출을 수행하지 못했다.** 사용자 요청을 다시 포괄적으로 승인받는 단계로 되돌리지 않는다.

## 확인한 내용

| 항목 | 실제 결과 |
| --- | --- |
| 브라우저 연결 | 도구 초기화가 `windows sandbox failed: CreateProcessWithLogonW failed: 1920`으로 종료. 세션 초기화 후 재시도도 동일 |
| Windows UI 대체 경로 | 공식 Windows 제어 런타임도 같은 시작 오류. 실제 Console 화면·로그인·프로덕션 접근 상태를 읽지 못함 |
| 대체 연동 도구 | 플러그인 검색 결과에서 Play Console 제출 기능을 제공하는 도구를 확인하지 못함. 검색에 나온 다른 Google 서비스 도구로 권한을 대신하지 않음 |
| 실제 제출 준비 검사 | `scripts/prepare-play-upload.ps1 -Action Verify` 실행. 업로드 키의 공개 인증서 확인 후 `verifyPlaySubmissionReady`가 정책 초안·미설정 시행일·미완료 최종 정책 확인을 이유로 종료 코드 1 반환 |
| 업로드 키 | 기존 로컬 독립 키 유지. 새 키 생성·외부 업로드 없음 |
| 로컬 SDK | `local.properties`가 지정한 `C:/Users/sunho/AppData/Local/Android/Sdk` 없음. 표준 대체 위치와 캐시에서도 SDK 복구본을 찾지 못함 |
| 빌드 도구 캐시 | JDK 21·Gradle 8.14·AGP 8.13.2는 있음. 이 캐시만으로 Android SDK를 대체할 수 없음 |
| 로컬 AAB | 이전 v1.3.28 파일은 현재 v1.3.29(159)의 제출물로 사용하지 않음. 현재 소스의 최종 제출용 서명 AAB는 미생성 |
| 최종 정책 | 공개 원본·Play 자산은 한·영 각각 일치하지만 아직 `draft`. 최종 시행일이 없는 상태를 검사 코드만 변경해 통과시키지 않음 |

앞선 `c5aa195`의 Android 빌드·Play 미서명 후보·CodeQL·정책 배포 성공은 유효한 기술 검증이다. 이를 실제 Console 제출이나 현재 로컬 서명 AAB 생성 성공으로 해석하지 않는다.

## 계속 진행할 순서

1. **브라우저 도구 연결 복구:** Codex 앱을 완전히 종료한 뒤 다시 실행하고 재연결을 확인한다. 재시작이 해결을 보장하는 것은 아니며, 오류가 지속되면 같은 실행 오류를 기준으로 지원을 요청한다. 비밀번호·OTP를 채팅에 보내지 않는다.
2. **정책 최종 문안 확인:** 이미 결정한 개발자 이름·이메일·문의 삭제 기준·대상 연령을 반복 질문하지 않는다. 현재 구현을 반영한 한·영 최종 문안과 시행일을 구체적으로 확인하고 공개 원본·앱 자산을 함께 갱신한다. 실제 전송·OEM 이전·Play 설치 시험을 하지 않고 완료했다고 적지 않는다.
3. **최소 SDK 복구:** 공식 도구로 `platforms;android-36`, AGP에 필요한 `build-tools;35.0.0`, `platform-tools`를 준비하고 실제 SDK 경로를 로컬 설정에 연결한다. AAB 생성만을 위해 IDE·에뮬레이터·시스템 이미지·NDK를 일괄 재설치하지 않는다. SDK 라이선스 확인과 다른 PC의 라이선스 파일 복사를 구분한다. [SDK 도구 공식 안내](https://developer.android.com/tools/sdkmanager), [AGP 8.13 호환성](https://developer.android.com/build/releases/agp-8-13-0-release-notes?hl=en)
4. **현 소스의 제출물 생성:** `preparePlaySubmission`으로 새 AAB를 생성하고 구조·정책·모든 본문 항목의 암호학적 서명·예상 업로드 인증서 일치·해시와 `verification.json`을 확인한다. 이전 후보를 재서명해 현재 빌드인 것처럼 제출하지 않는다.
5. **실제 Console 등록:** 현재 계정의 앱·계약·앱 서명·콘텐츠 선언·테스트 트랙 상태를 확인하고, 준비된 값으로 등록을 계속한다. 없는 Console 상태나 테스트 참여자를 만들어 완료 처리하지 않는다.

새 개인 계정에 관련 테스트 요건이 적용된다면 프로덕션 접근 전에 **최소 12명의 직전 연속 14일 비공개 테스트** 등 실제 Console 요구를 충족해야 한다. 아직 계정 화면을 읽지 못했으므로 적용 여부·완료 상태는 미확인이다. 내부 테스트는 별도 초기 배포 단계이며 프로덕션 심사 완료와 구분한다. [공식 테스트 요건](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en)

입력 원문과 국가·연령 계획은 [Console 입력 가이드](PLAY_CONSOLE_ENTRY_GUIDE.md), [배포 국가·언어·연령 준비](GOOGLE_PLAY_DISTRIBUTION_AND_LOCALIZATION.md)를 따른다.
