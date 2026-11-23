# Slivue Play 등록 준비 진행 기록 — 2026-11-23

## 사용자가 확정한 정보

- 공개 개발자 표시 이름: **DevDooly**
- 지원·개인정보 문의 이메일: **vmdooly@gmail.com**
- 이전 계정은 정책 위반 해지가 아닌 **휴면·장기 미사용 종료**였다고 확인했다.
- 새 Play Console 계정 생성과 본인인증 완료는 **사용자 확인**이다. 이 작업에서 비밀번호·신분증·개인 주소를 수집하지 않았다.
- GitHub Pages의 한·영 정책 페이지 공개를 승인했다. 정책 내용의 최종 승인·시행일·대상 연령·국가·등급·Console 신고는 별개다.
- 문의 정보는 **문의 처리 목적 달성 후 지체 없이 삭제하고, 법령상 보관 의무가 있는 경우 해당 기간만 별도 보관**하는 운영 기준을 사용자가 추가 확정했다. 메일의 실제 삭제는 운영자가 수행한다.
- 한국을 배포 국가로 지정했고, 추가 요건에 문제가 없으면 여러 나라에도 배포하는 방향을 허용했다. 한국·미국·캐나다·영국·호주·뉴질랜드를 초기 검토안으로 준비하며, 해외 개별 국가의 실제 Console 선택은 미실행이다.
- 어린이용 앱이 아니며 **13~15세·16~17세·18세 이상인 청소년과 성인**을 실제 대상으로 사용자가 선택했다. IARC 콘텐츠 등급·배포 국가별 아동 요건 검토는 별도다.

## 반영한 내용

1. `config/play-public.properties`에 이름·이메일·정책 URL만 기록했다. CLI 속성 > 환경변수 > 공개 기본값 순서를 유지하며, 비밀키·암호·정책 승인 상태는 공개 파일에 넣지 않는다.
2. 한·영 공개 정책 원본과 Play 앱 자산에 같은 운영 정보를 반영했다. GitHub Pages 접속정보와 Gmail 문의는 앱의 알림 로컬 처리와 구분해 설명했다.
3. 정책은 **검토용 `draft`**다. 실제 시행 중인 방침이나 Play 제출 완료로 표시하지 않는다. 공개 페이지는 원문 HTML 두 개와 언어 선택 페이지 두 개만 배포한다.
4. 승인받은 저장소의 Pages를 Actions 배포 방식으로 활성화하고 루트·한영 정책의 실제 HTTPS 접근과 원문 일치를 확인했다. 문의 삭제 기준의 후속 갱신에도 같은 검증을 적용한다.
5. 별도의 로컬 업로드 키를 생성했다. 기존 직접 배포 서명과 다르며, Google의 앱 서명키나 Console에 등록한 키를 뜻하지 않는다.
6. 공개 정보·정책 일치·사이트 화이트리스트 회귀 검사와 표준 빌드 CI를 연결했다. 일반 CI는 Play 개인키를 갖지 않으며 미서명 기술 후보만 생성한다.
7. [배포 국가·언어·연령 준비](GOOGLE_PLAY_DISTRIBUTION_AND_LOCALIZATION.md)에 언어와 국가 설정의 차이, 초기 해외 후보 및 사용자 선택 연령을 기록했다. 기본 영어 소개와 한국어 번역으로 등록하는 계획이며 아직 Console 설정을 변경하지 않았다.

## 정책 주소

- 한국어: <https://devdooly.github.io/Slivue/privacy/ko.html>
- 영어: <https://devdooly.github.io/Slivue/privacy/en.html>

주소와 공개는 승인됐고 최초 공개본(`12bd530`)의 두 정책 및 루트 페이지에서 실제 HTTP 200 접근과 커밋 원본·앱 자산의 바이트 일치를 확인했다. 이번 문의 삭제 기준의 후속 갱신은 재배포 후 다시 확인한다. 최종 정책 내용 승인은 아직 받지 않았으므로 검토용 경고를 유지한다.

## 로컬 업로드 키 준비 결과

- 개인키: `app/keystore/slivue-play-upload.jks`
- Windows DPAPI 암호 파일: `app/keystore/slivue-play-upload.secrets`
- 공개 인증서: `app/build/play-upload-key/slivue-play-upload-cert.pem`
- 공개 인증서 SHA-256: `0c18975a335f473742ec82dac3b09c17f1715a3deb99a0acacf06f23769707da`

개인키와 암호 파일은 Git 추적에서 제외했고, 새 개인 파일의 접근은 생성 사용자와 SYSTEM으로 제한했다. 실제 개인키 항목·CSR 서명과 공개 인증서를 검사했다. 재실행 시 기존 파일을 덮어쓰지 않는지와 프로세스 비밀 환경 복원도 확인했다. 암호나 복호한 비밀 JSON은 채팅·문서에 출력하지 않았다.

**DPAPI 파일만 복사한다고 다른 Windows 계정·PC에서 암호를 복구할 수 있는 것은 아니다.** 별도 안전 백업은 소유자가 준비해야 한다. 개인키·암호를 Git·Slack·이슈나 이 문서에 붙이지 않는다. 사용법은 [업로드 키 안내서](PLAY_UPLOAD_KEY_GUIDE.md)를 따른다.

## 검증 결과와 한계

- Node 회귀 **46개 통과**: 소개 15개, 기존 AAB 검사 22개, 공개 운영 정보·Windows 스크립트 인코딩 4개, 정책 사이트 5개.
- 업로드 준비 스크립트의 UTF-8 BOM 누락을 보완하고 Windows PowerShell 5.1·PowerShell 7에서 실제 구문 분석과 키 `Inspect` 성공을 확인했다. 기존 개인키·비밀 파일은 변경하지 않았다.
- 허용한 HTML 4개만 생성했고 공개 원문·앱 자산이 일치함을 확인했다. 예상 밖 파일·비밀 설정·링크·출력 경로 이탈은 배포하지 않는다.
- 생성한 키를 사용하는 실제 `verifyPlaySubmissionReady`는 **초안·최종 승인·시행일 미완료로 정상 실패**했다. 키·이름·이메일·URL을 입력한 것만으로 제출 준비가 완료되지 않는다.
- 이 PC의 기존 `local.properties`에 지정된 Android SDK 디렉터리가 없어 로컬 앱 전체 빌드는 진행하지 못했다. 저장소의 표준 명령 `testDebugUnitTest compileDebugKotlin lintRelease assembleRelease`를 원격 빌드에서 함께 실행하도록 구성했다. 로컬 실패를 앱 소스 성공이나 로컬 검증 통과로 기록하지 않는다.
- 준비 구현 커밋 `12bd530`의 [표준 빌드](https://github.com/DevDooly/Slivue/actions/runs/45127345886), [Play 미서명 후보 검증](https://github.com/DevDooly/Slivue/actions/runs/45127345786), [정책 사이트 배포](https://github.com/DevDooly/Slivue/actions/runs/45127345831), [CodeQL](https://github.com/DevDooly/Slivue/actions/runs/45127345795)이 통과했다. 표준 빌드는 두 배포 변형의 단위 테스트·컴파일·린트·릴리스를 실제 실행했다.
- 문의 삭제 기준 후속 커밋 `a0463d7`의 [표준 빌드](https://github.com/DevDooly/Slivue/actions/runs/45129891360), [Play 미서명 후보 검증](https://github.com/DevDooly/Slivue/actions/runs/45129891460), [정책 사이트 배포](https://github.com/DevDooly/Slivue/actions/runs/45129891534), [CodeQL](https://github.com/DevDooly/Slivue/actions/runs/45129891505)도 통과했다. 이후 실제 공개 페이지의 새 삭제 문구·HTTP 200·원문 및 앱 자산 일치를 다시 확인했다. Windows 기본 PowerShell에서 실행한 제출 준비 검사도 초안·최종 승인·시행일 미완료로 정상 실패했다.
- [직접 배포판 v1.3.29](https://github.com/DevDooly/Slivue/releases/tag/v1.3.29)를 공개했다. 내려받은 APK의 SHA-256은 `bb73e0b10925470c664d700fd98cd869dfa4337e4b57a892e054040ec6e23295`이며, 공개 체크섬·GitHub 자산 해시가 일치하고 공식 `apksig` 라이브러리로 서명을 확인했다. 이는 Play판 출시나 최종 정책 승인 결과가 아니다.
- 실제 Console의 앱 생성, Play App Signing 약관·키 등록, AAB 업로드, Data safety/FGS 신고, 비공개 테스트·심사·게시를 수행한 것은 아니다.

## 다음 소유자 확인

1. 공개된 한·영 정책의 실제 처리 설명을 읽고 최종 시행일·내용을 승인한다. 문의 삭제 기준과 대상 연령 세 구간은 위와 같이 확정했다. 한국 포함 해외 개별 국가 요건을 확인한 뒤 실제 배포 목록과 IARC 설문을 입력한다.
2. 최종 정책과 앱 자산을 동일하게 반영하고 운영자가 명시 승인한 후에만 준비 게이트를 통과시킨다. 단순히 `draft` 경고나 검사 코드를 지워서 통과시키지 않는다.
3. 최종 서명 AAB와 검증 JSON·해시를 보관하고 소유자가 Console에서 내부 테스트에 올린다. 서명 등록·계정 약관 동의는 소유자가 직접 확인한다.
4. 실제 Play 설치·업데이트, Pre-launch report, 16KB 런타임·기기별 필수 흐름을 확인하고 최종 Data safety 답안을 결정한다.
5. 해당하는 신규 개인 계정의 비공개 테스트·프로덕션 액세스 요건을 진행한다. 기존 GitHub 릴리스와 에뮬레이터 CI를 Play 심사 승인이나 실제 참여자 테스트로 대체하지 않는다.

세부 입력 순서는 [Console 입력 가이드](PLAY_CONSOLE_ENTRY_GUIDE.md), 미완료 항목은 [제출 점검표](GOOGLE_PLAY_SUBMISSION_CHECKLIST.md)를 참고한다.
