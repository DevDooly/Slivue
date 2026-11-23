# Play 업로드 키 준비·보관 안내

개발자 표시 이름은 **DevDooly**, 공개 문의 이메일은 **vmdooly@gmail.com**이다. 이 안내와 helper는 `com.devdooly.slivue`의 **업로드 키**만 준비한다. Google Play 앱 서명키 등록, 계정 약관 동의, Play 업로드·제출, 공개 정책 최종 승인을 자동으로 실행하지 않는다.

## 사용 명령

저장소에서 Windows PowerShell 5.1 이상과 JDK의 `keytool`을 사용한다. `JAVA_HOME/bin/keytool.exe`를 먼저 찾고, 없으면 `PATH`에서 찾는다.

이 스크립트는 한국어를 포함하므로 **UTF-8 BOM 인코딩을 유지한다.** Windows PowerShell 5.1은 BOM 없는 파일을 시스템 ANSI 코드 페이지로 읽어 구문 오류를 일으킬 수 있다. PowerShell 7에서도 같은 파일을 사용할 수 있으며, 편집·포맷 과정에서 BOM을 제거하지 않는다.

```powershell
# 기본 동작: 존재 여부 확인. 키가 있으면 개인키 항목과 복호 가능성을 검증하고 공개 인증서만 출력
.\scripts\prepare-play-upload.ps1

# 별도 Play 업로드 키를 처음 생성. 이미 키나 비밀 파일 하나라도 있으면 중단
.\scripts\prepare-play-upload.ps1 -Action CreateKey

# 실제 업로드 키를 이번 프로세스에만 적용하여 제출 조건 확인
.\scripts\prepare-play-upload.ps1 -Action Verify

# 모든 승인·정책 조건이 충족된 뒤에만 최종 서명 후보 생성
.\scripts\prepare-play-upload.ps1 -Action Prepare
```

`Verify`와 `Prepare`는 Gradle 실패 코드를 그대로 반환한다. 정책이 여전히 초안이거나 시행일·최종 승인 등 조건이 남아 있다면 **실패가 정상**이다. 실패를 없애기 위해 `PLAY_POLICY_APPROVED=true`를 임의로 설정하거나 정책 메타만 `final`로 바꾸지 않는다.

실행 전후의 `PLAY_UPLOAD_*`와 helper용 암호 환경변수는 원래 값으로 복원한다. `keystore.properties`, 사용자 환경변수, 원격 저장소 비밀 설정을 수정하지 않는다. 일반 `bundlePlayRelease` 명령의 미서명 기술 후보 동작과 최종 준비 명령을 구분한다.

기존 `keystore.properties`에 `playUploadStoreFile` 등 Play 업로드 서명 항목이 있으면 이번 프로세스의 환경 값을 가릴 수 있어 `Verify`·`Prepare`가 중단한다. 파일을 자동으로 고치지 않으며, 실제 사용할 설정을 소유자가 확인한 뒤 한 방식으로 정리한다. 기본 직접 배포 서명 항목은 이 이유로 차단하지 않는다.

## 생성·확인하는 파일

| 경로 | 내용·취급 |
| --- | --- |
| `app/keystore/slivue-play-upload.jks` | RSA 3072·PKCS12 업로드 개인키. Git 추적 제외 |
| `app/keystore/slivue-play-upload.secrets` | 암호·별칭 등의 JSON을 Windows DPAPI로 암호화한 파일. Git 추적 제외 |
| `app/build/play-upload-key/slivue-play-upload-cert.pem` | 공개 인증서. `Inspect`가 다시 내보낼 수 있는 공개 정보 |

생성 별칭은 `slivue-play-upload`, 인증서 표시 정보는 `CN=DevDooly,OU=Slivue Play Upload`이며 유효 기간은 10,000일이다. 36바이트 난수로 만든 강한 암호를 사용하고 스토어·개인키 암호를 동일하게 둔다. 암호는 명령행 인자로 직접 전달하지 않고 비밀 환경변수로 `keytool`에 전달한다.

비밀 파일은 독점 생성 모드로 먼저 저장한다. 키는 추적 제외 임시 `.jks`에 생성한 뒤 기존 파일을 덮어쓰지 않는 이동으로 확정한다. 새 개인 파일은 현재 Windows 사용자와 SYSTEM만 접근하도록 제한하며 파일 소유권을 임의 변경하지 않는다. 생성 중 실패해도 비밀 파일과 생성 자료를 자동 삭제하지 않는다. 이 경우 `CreateKey`를 반복해 덮어쓰지 말고 `app/keystore/slivue-play-upload-*.jks` 등 남은 자료의 상태를 안전하게 확인한다.

`Inspect`는 실제 `PrivateKeyEntry` 및 개인키를 이용한 CSR 생성 가능성을 확인하고 공개 인증서의 SHA-256을 출력한다. 기존 GitHub 직접 배포 legacy·회전 인증서 지문은 Play 업로드 키로 재사용하지 못하도록 차단한다. CSR·암호·복호 JSON·DPAPI 원문은 출력하지 않는다.

## 백업과 복구 경계

- **DPAPI 비밀 파일은 생성한 Windows 사용자·PC 환경에 종속된다.** `.jks`와 `.secrets` 파일만 다른 PC에 복사해도 암호를 복호할 수 있다고 가정하지 않는다.
- PC 초기화·Windows 계정 삭제·프로필 손실 전에, 복구 가능한 암호와 개인키의 별도 안전 백업 절차를 완료해야 한다. 암호 관리자나 암호화된 오프라인 보관소 등 실제 관리 가능한 방법을 정한다.
- 이 helper는 비밀번호를 표시하는 기능이나 다른 PC로 백업을 이전하는 기능이 없다. 별도 백업이 완료되기 전 `.secrets`가 영구적인 복구 수단인 것처럼 취급하지 않는다.
- 암호, 개인키, 복호 JSON, DPAPI 파일을 채팅·이메일·Git·GitHub Actions 로그에 올리지 않는다. 문의 시에는 필요한 **공개 인증서 지문**만 공유한다.

## 업로드 키와 앱 서명키의 차이

업로드 키는 개발자가 Play에 보내는 AAB를 서명·인증하는 용도다. 사용자 기기에 설치하는 Play 앱의 서명은 **Play App Signing의 앱 서명키**와 관련된다. 둘을 같은 키라고 단정하거나 이번 로컬 생성이 Google의 앱 서명 등록을 완료한 것으로 해석하지 않는다. Play Console의 실제 선택과 약관 확인은 소유자가 수행한다. [Android 앱 서명 공식 안내](https://developer.android.com/studio/publish/app-signing)

서명된 최종 AAB, 공개 정책의 실제 접근성, Data safety, 권한·FGS 설명, Play가 서명한 설치물과 업데이트 시험은 [제출 점검표](GOOGLE_PLAY_SUBMISSION_CHECKLIST.md)에 따라 별도로 확인한다.
