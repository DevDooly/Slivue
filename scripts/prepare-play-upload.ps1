#requires -Version 5.1
[CmdletBinding()]
param([ValidateSet('Inspect', 'CreateKey', 'Verify', 'Prepare')][string]$Action = 'Inspect')

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repositoryRoot = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$privateDirectory = Join-Path $repositoryRoot 'app\keystore'
$keyStorePath = Join-Path $privateDirectory 'slivue-play-upload.jks'
$secretPath = Join-Path $privateDirectory 'slivue-play-upload.secrets'
$certificateDirectory = Join-Path $repositoryRoot 'app\build\play-upload-key'
$certificatePath = Join-Path $certificateDirectory 'slivue-play-upload-cert.pem'
$keyAlias = 'slivue-play-upload'
$passwordNames = @('SLIVUE_PLAY_STORE_PASSWORD', 'SLIVUE_PLAY_KEY_PASSWORD')
$playNames = @('PLAY_UPLOAD_STORE_FILE', 'PLAY_UPLOAD_STORE_PASSWORD', 'PLAY_UPLOAD_KEY_ALIAS', 'PLAY_UPLOAD_KEY_PASSWORD')
$originalEnvironment = @{}
foreach ($name in ($passwordNames + $playNames)) {
    $originalEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
$exitCode = 0
$stage = '환경 확인'
$secret = $null
$protectedSecret = $null
$plainJson = $null
$password = $null

function Protect-NewPrivateFile([string]$PrivateFilePath) {
    # 새로 생성한 두 개인 파일만 다룬다. 파일 소유자나 시스템 계정의 소유권은 바꾸지 않는다.
    $acl = Get-Acl -LiteralPath $PrivateFilePath
    $acl.SetAccessRuleProtection($true, $false)
    $user = [Security.Principal.WindowsIdentity]::GetCurrent().User
    $system = [Security.Principal.SecurityIdentifier]::new('S-1-5-18')
    foreach ($identity in @($user, $system)) {
        $acl.SetAccessRule([Security.AccessControl.FileSystemAccessRule]::new(
            $identity, [Security.AccessControl.FileSystemRights]::FullControl,
            [Security.AccessControl.AccessControlType]::Allow))
    }
    Set-Acl -LiteralPath $PrivateFilePath -AclObject $acl
}

function Invoke-KeyTool([string[]]$Arguments) {
    $preference = $ErrorActionPreference
    try {
        # keytool의 정상 진행 문구도 stderr로 나온다. 반환 코드로만 성공 여부를 판단한다.
        $ErrorActionPreference = 'Continue'
        $output = & $script:keyToolPath '-J-Duser.language=en' '-J-Duser.country=US' @Arguments 2>&1
        $result = $LASTEXITCODE
    } finally { $ErrorActionPreference = $preference }
    if ($result -ne 0) { throw "keytool 작업 실패: exit $result" }
    return @($output | ForEach-Object { $_.ToString() })
}

try {
    if ($env:OS -ne 'Windows_NT') { throw 'Windows DPAPI가 필요합니다.' }
    $keyToolPath = $null
    if ($env:JAVA_HOME) {
        $javaKeyTool = Join-Path $env:JAVA_HOME.Trim('"') 'bin\keytool.exe'
        if (Test-Path -LiteralPath $javaKeyTool -PathType Leaf) { $keyToolPath = $javaKeyTool }
    }
    if (-not $keyToolPath) {
        $command = Get-Command keytool.exe -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($command) { $keyToolPath = $command.Source }
    }
    if (-not $keyToolPath) { throw 'JDK keytool을 찾지 못했습니다.' }
    $hasKey = Test-Path -LiteralPath $keyStorePath
    $hasSecret = Test-Path -LiteralPath $secretPath

    if ($Action -eq 'CreateKey') {
        $stage = '새 업로드 키 생성'
        if ($hasKey -or $hasSecret) { throw '기존 키나 비밀 파일은 덮어쓰지 않습니다.' }
        [IO.Directory]::CreateDirectory($privateDirectory) | Out-Null
        $random = [Security.Cryptography.RandomNumberGenerator]::Create()
        try {
            $bytes = [byte[]]::new(36)
            $random.GetBytes($bytes)
            $password = [Convert]::ToBase64String($bytes)
        } finally { $random.Dispose() }
        $pendingPath = Join-Path $privateDirectory ('slivue-play-upload-' + [Guid]::NewGuid().ToString('N') + '.jks')
        $plainJson = [ordered]@{
            version = 1; keyAlias = $keyAlias; storePassword = $password; keyPassword = $password
            createdAtUtc = [DateTime]::UtcNow.ToString('o'); pendingKeyStore = $pendingPath
        } | ConvertTo-Json -Compress
        $protectedSecret = ConvertTo-SecureString -String $plainJson -AsPlainText -Force
        $encrypted = ConvertFrom-SecureString -SecureString $protectedSecret
        $encoded = [Text.UTF8Encoding]::new($false).GetBytes($encrypted)
        $stream = [IO.File]::Open($secretPath, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
        try { $stream.Write($encoded, 0, $encoded.Length); $stream.Flush($true) } finally { $stream.Dispose() }
        Protect-NewPrivateFile $secretPath
        [Environment]::SetEnvironmentVariable($passwordNames[0], $password, 'Process')
        [Environment]::SetEnvironmentVariable($passwordNames[1], $password, 'Process')
        $null = Invoke-KeyTool @('-genkeypair', '-alias', $keyAlias, '-keyalg', 'RSA', '-keysize', '3072',
            '-sigalg', 'SHA256withRSA', '-storetype', 'PKCS12', '-validity', '10000',
            '-dname', 'CN=DevDooly,OU=Slivue Play Upload', '-keystore', $pendingPath,
            '-storepass:env', $passwordNames[0], '-keypass:env', $passwordNames[1])
        Protect-NewPrivateFile $pendingPath
        [IO.File]::Move($pendingPath, $keyStorePath) # 대상이 이미 있으면 실패하며 덮어쓰지 않는다.
        $hasKey = $true
        $hasSecret = $true
    }

    if ($Action -eq 'Inspect' -and -not $hasKey -and -not $hasSecret) {
        Write-Output 'Play 업로드 키는 아직 없습니다. CreateKey를 명시적으로 실행해야 생성합니다.'
    } else {
        $stage = '업로드 개인키 검증'
        if (-not $hasKey -or -not $hasSecret) { throw '키·비밀 파일 중 하나가 없습니다. 자동 복구나 덮어쓰기는 하지 않습니다.' }
        if ($null -ne $protectedSecret) { $protectedSecret.Dispose(); $protectedSecret = $null }
        $protectedSecret = Get-Content -LiteralPath $secretPath -Raw -Encoding UTF8 | ConvertTo-SecureString
        $plainJson = [Net.NetworkCredential]::new('', $protectedSecret).Password
        $secret = $plainJson | ConvertFrom-Json
        if ($secret.version -ne 1 -or $secret.keyAlias -ne $keyAlias -or
            [string]::IsNullOrWhiteSpace($secret.storePassword) -or
            $secret.storePassword -cne $secret.keyPassword) { throw '비밀 파일 형식이 올바르지 않습니다.' }
        [Environment]::SetEnvironmentVariable($passwordNames[0], $secret.storePassword, 'Process')
        [Environment]::SetEnvironmentVariable($passwordNames[1], $secret.keyPassword, 'Process')
        $entry = Invoke-KeyTool @('-list', '-v', '-alias', $keyAlias, '-keystore', $keyStorePath,
            '-storepass:env', $passwordNames[0])
        if (-not ($entry -match '^\s*Entry type:\s*PrivateKeyEntry\s*$')) { throw '개인키 항목이 아닙니다.' }
        $null = Invoke-KeyTool @('-certreq', '-alias', $keyAlias, '-keystore', $keyStorePath,
            '-storepass:env', $passwordNames[0], '-keypass:env', $passwordNames[1])
        $certificateOutput = Invoke-KeyTool @('-exportcert', '-rfc', '-alias', $keyAlias, '-keystore', $keyStorePath,
            '-storepass:env', $passwordNames[0])
        $pemMatch = [regex]::Match(($certificateOutput -join "`n"),
            '(?s)-----BEGIN CERTIFICATE-----\s*([A-Za-z0-9+/=\r\n]+?)\s*-----END CERTIFICATE-----')
        if (-not $pemMatch.Success) { throw '공개 인증서를 읽지 못했습니다.' }
        $der = [Convert]::FromBase64String(($pemMatch.Groups[1].Value -replace '\s', ''))
        $sha = [Security.Cryptography.SHA256]::Create()
        try { $digest = [BitConverter]::ToString($sha.ComputeHash($der)).Replace('-', '').ToLowerInvariant() }
        finally { $sha.Dispose() }
        if ($digest -in @('7799933e09ea9cc085b058639c6b27b61a45eba6e385e1e8c0ec0c4f8ef073e0',
            '3de001d769ca37e913d80d233d3a02da6eef90838daa8bb33191c5e7e5ac766c')) {
            throw '직접 배포 인증서를 Play 업로드 키로 재사용할 수 없습니다.'
        }
        [IO.Directory]::CreateDirectory($certificateDirectory) | Out-Null
        [IO.File]::WriteAllText($certificatePath, $pemMatch.Value.Trim() + [Environment]::NewLine, [Text.Encoding]::ASCII)
        Write-Output "공개 업로드 인증서: $certificatePath"
        Write-Output "인증서 SHA-256: $digest"

        if ($Action -in @('Verify', 'Prepare')) {
            $stage = 'Play 최종 제출 준비 검사'
            # Gradle의 기존 로컬 속성 우선순위가 이번에 검증한 키의 환경 값을 가리지 않게 한다.
            $localSigningFile = Join-Path $repositoryRoot 'keystore.properties'
            if (Test-Path -LiteralPath $localSigningFile -PathType Leaf) {
                foreach ($line in [IO.File]::ReadLines($localSigningFile)) {
                    if ($line -match '^\s*playUpload(?:StoreFile|StorePassword|KeyAlias|KeyPassword)\s*[:=]') {
                        throw '로컬 Play 서명 속성이 환경 값을 가릴 수 있습니다.'
                    }
                }
            }
            $values = @($keyStorePath, $secret.storePassword, $keyAlias, $secret.keyPassword)
            for ($index = 0; $index -lt $playNames.Length; $index++) {
                [Environment]::SetEnvironmentVariable($playNames[$index], $values[$index], 'Process')
            }
            $task = if ($Action -eq 'Verify') { 'verifyPlaySubmissionReady' } else { 'preparePlaySubmission' }
            Push-Location $repositoryRoot
            $gradlePreference = $ErrorActionPreference
            try {
                $ErrorActionPreference = 'Continue'
                & (Join-Path $repositoryRoot 'gradlew.bat') $task '--no-daemon' '--console=plain'
                $exitCode = $LASTEXITCODE
            } finally { $ErrorActionPreference = $gradlePreference; Pop-Location }
        }
    }
} catch {
    # 복호 JSON·암호·DPAPI 원문이 예외 메시지에 섞일 수 있어 메시지와 객체를 출력하지 않는다.
    Write-Error -Message "Play 업로드 준비 실패 — 단계: $stage. 기존 생성 파일은 보존했습니다. 공개 안내서를 확인하세요." -ErrorAction Continue
    $exitCode = 1
} finally {
    foreach ($name in ($passwordNames + $playNames)) {
        [Environment]::SetEnvironmentVariable($name, $originalEnvironment[$name], 'Process')
    }
    if ($null -ne $protectedSecret) { $protectedSecret.Dispose() }
    $secret = $null; $plainJson = $null; $password = $null; $encrypted = $null; $encoded = $null
}
exit $exitCode
