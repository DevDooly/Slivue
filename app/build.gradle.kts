import java.io.FileInputStream
import java.util.Properties
import com.android.build.api.dsl.ManagedVirtualDevice
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI
import java.io.OutputStream
import java.security.KeyStore
import java.util.jar.JarFile
import java.security.MessageDigest
import java.time.Instant
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        FileInputStream(keystorePropertiesFile).use(::load)
    }
}

fun releaseSigningValue(propertyName: String, environmentName: String): String? =
    keystoreProperties.getProperty(propertyName)
        ?.takeIf { it.isNotBlank() }
        ?: providers.environmentVariable(environmentName).orNull?.takeIf { it.isNotBlank() }

val releaseStoreFile = releaseSigningValue("storeFile", "RELEASE_STORE_FILE")
val releaseStorePassword = releaseSigningValue("storePassword", "RELEASE_STORE_PASSWORD")
val releaseKeyAlias = releaseSigningValue("keyAlias", "RELEASE_KEY_ALIAS")
val releaseKeyPassword = releaseSigningValue("keyPassword", "RELEASE_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

val playUploadStoreFile = releaseSigningValue("playUploadStoreFile", "PLAY_UPLOAD_STORE_FILE")
val playUploadStorePassword = releaseSigningValue("playUploadStorePassword", "PLAY_UPLOAD_STORE_PASSWORD")
val playUploadKeyAlias = releaseSigningValue("playUploadKeyAlias", "PLAY_UPLOAD_KEY_ALIAS")
val playUploadKeyPassword = releaseSigningValue("playUploadKeyPassword", "PLAY_UPLOAD_KEY_PASSWORD")
val playUploadSigningValues = listOf(playUploadStoreFile, playUploadStorePassword, playUploadKeyAlias, playUploadKeyPassword)
val hasPlayUploadSigning = playUploadSigningValues.all { !it.isNullOrBlank() }
fun playMetadata(propertyName: String, environmentName: String): String =
    providers.gradleProperty(propertyName).orNull
        ?: providers.environmentVariable(environmentName).orNull.orEmpty()

val playPrivacyPolicyUrl = playMetadata("playPrivacyPolicyUrl", "PLAY_PRIVACY_POLICY_URL")
val playSupportEmail = playMetadata("playSupportEmail", "PLAY_SUPPORT_EMAIL")
val playDeveloperName = playMetadata("playDeveloperName", "PLAY_DEVELOPER_NAME")
val playPolicyApproved = playMetadata("playPolicyApproved", "PLAY_POLICY_APPROVED") == "true"

fun certificateSha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }

// 기존 공개 APK의 legacy/회전 direct 인증서다. 별도 Play 업로드 키로 재사용하지 않는다.
val prohibitedPlayUploadCertificates = setOf(
    "7799933e09ea9cc085b058639c6b27b61a45eba6e385e1e8c0ec0c4f8ef073e0",
    "3de001d769ca37e913d80d233d3a02da6eef90838daa8bb33191c5e7e5ac766c"
)

fun javaString(value: String): String = "\"" + value
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\r", "\\r")
    .replace("\n", "\\n") + "\""

val appProjectPath = project.path
val directReleaseArtifactTask = Regex("^(assemble|bundle|package|sign)Direct(?:Minified)?Release(?:Bundle|UniversalApk)?$")
val playReleaseArtifactTask = Regex("^(assemble|bundle|package|sign)Play(?:Minified)?Release(?:Bundle|UniversalApk)?$")
gradle.taskGraph.whenReady {
    val releaseArtifactRequested = allTasks.any { task ->
        task.project.path == appProjectPath &&
            directReleaseArtifactTask.matches(task.name)
    }
    if (releaseArtifactRequested && !hasReleaseSigning) {
        throw GradleException(
            "릴리스 서명 정보가 없습니다. keystore.properties 또는 RELEASE_* 환경변수를 설정하세요."
        )
    }
    val playArtifactRequested = allTasks.any { task ->
        task.project.path == appProjectPath &&
            playReleaseArtifactTask.matches(task.name)
    }
    if (playArtifactRequested && playUploadSigningValues.any { !it.isNullOrBlank() } && !hasPlayUploadSigning) {
        throw GradleException("Play 업로드 서명 정보가 일부만 설정되었습니다. PLAY_UPLOAD_* 네 항목을 모두 설정하거나 모두 비우세요.")
    }
    if (playArtifactRequested && !hasPlayUploadSigning) {
        logger.lifecycle("Play 업로드 키가 없어 미서명 검증 후보만 생성합니다. 이 AAB는 Play Console 업로드용 완성본이 아닙니다.")
    }
}

android {
    namespace = "com.devdooly.notificationedge"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.devdooly.notificationedge"
        minSdk = 26
        targetSdk = 36
        versionCode = 157
        versionName = "1.3.27"

        buildConfigField("String", "PRIVACY_POLICY_URL", javaString(playPrivacyPolicyUrl))
        buildConfigField("String", "SUPPORT_EMAIL", javaString(playSupportEmail))
        buildConfigField("String", "DEVELOPER_NAME", javaString(playDeveloperName))
        // UI 안내용이며 제출 게이트·실제 Play 심사 결과를 대신하지 않는다.
        buildConfigField("boolean", "IS_PLAY_PREPARATION_BUILD", (!playPolicyApproved || !hasPlayUploadSigning || playDeveloperName.isBlank() || playSupportEmail.isBlank() || playPrivacyPolicyUrl.isBlank()).toString())

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("directRelease") {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
        if (hasPlayUploadSigning) {
            create("playUpload") {
                storeFile = file(requireNotNull(playUploadStoreFile))
                storePassword = requireNotNull(playUploadStorePassword)
                keyAlias = requireNotNull(playUploadKeyAlias)
                keyPassword = requireNotNull(playUploadKeyPassword)
            }
        }
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("direct") {
            dimension = "distribution"
            applicationId = "com.devdooly.notificationedge"
            buildConfigField("boolean", "IS_PLAY_DISTRIBUTION", "false")
            // Play 전용 정책 URL을 직접 배포판 정책인 것처럼 표시하지 않는다.
            buildConfigField("String", "PRIVACY_POLICY_URL", javaString(""))
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("directRelease") else null
        }
        create("play") {
            dimension = "distribution"
            applicationId = "com.devdooly.slivue"
            buildConfigField("boolean", "IS_PLAY_DISTRIBUTION", "true")
            signingConfig = if (hasPlayUploadSigning) signingConfigs.getByName("playUpload") else null
        }
    }

    buildTypes {
        getByName("debug") {
            // 디버그는 배포 키·운영 메타데이터가 있어도 준비 빌드임을 명시한다.
            signingConfig = signingConfigs.getByName("debug")
            buildConfigField("boolean", "IS_PLAY_PREPARATION_BUILD", "true")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("minifiedRelease") {
            initWith(getByName("release"))
            isMinifyEnabled = true
            isShrinkResources = true
            matchingFallbacks += listOf("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    lint {
        checkReleaseBuilds = true
        abortOnError = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
        managedDevices {
            devices {
                listOf(31, 34, 35, 36).forEach { apiLevelValue ->
                    maybeCreate<ManagedVirtualDevice>("pixel2Api$apiLevelValue").apply {
                        device = "Pixel 2"
                        apiLevel = apiLevelValue
                        systemImageSource = "aosp"
                    }
                }
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// 기존 개발 지침의 명령을 유지하되, 두 배포판의 컴파일·단위 테스트를 모두 확인한다.
afterEvaluate {
    mapOf(
        "testDebugUnitTest" to listOf("testDirectDebugUnitTest", "testPlayDebugUnitTest"),
        "compileDebugKotlin" to listOf("compileDirectDebugKotlin", "compilePlayDebugKotlin"),
        "lintRelease" to listOf("lintDirectRelease", "lintPlayRelease"),
        "assembleRelease" to listOf("assembleDirectRelease", "assemblePlayRelease"),
        "assembleMinifiedRelease" to listOf("assembleDirectMinifiedRelease", "assemblePlayMinifiedRelease")
    ).forEach { (alias, targets) ->
        if (tasks.findByName(alias) == null) {
            tasks.register(alias) {
                group = "verification"
                description = "배포판별 검증 태스크를 함께 실행하는 호환 명령"
                dependsOn(targets)
            }
        }
    }
}

val beginPlaySubmissionAttempt by tasks.registering {
    group = "distribution"
    description = "이번 제출 후보 준비가 아직 끝나지 않았음을 기록해 과거 후보와 구분합니다."
    doLast {
        val destination = layout.buildDirectory.dir("play-submission").get().asFile
        destination.mkdirs()
        destination.resolve("latest-attempt.json").writeText(JsonOutput.prettyPrint(JsonOutput.toJson(mapOf(
            "status" to "not_ready",
            "attemptStartedAtUtc" to Instant.now().toString(),
            "packageName" to "com.devdooly.slivue",
            "versionCode" to android.defaultConfig.versionCode,
            "versionName" to android.defaultConfig.versionName,
            "reason" to "현재 시도의 준비 게이트·번들·서명·실물 검증이 완료되지 않았습니다. 과거 후보 파일이 있어도 이번 결과로 사용하지 마세요."
        ))), Charsets.UTF_8)
    }
}

val verifyPlaySubmissionReady by tasks.registering {
    group = "verification"
    description = "Play 업로드 키와 공개 개인정보 안내의 로컬 준비 조건을 확인합니다. Console 승인을 보장하지 않습니다."
    dependsOn(beginPlaySubmissionAttempt)
    doLast {
        val problems = mutableListOf<String>()
        val policyUri = runCatching { URI(playPrivacyPolicyUrl) }.getOrNull()
        val host = policyUri?.host?.lowercase().orEmpty()
        val blockedHost = !host.contains('.') || host.contains(':') || Regex("^[0-9.]+$").matches(host) ||
            host == "example.com" || host.endsWith(".example.com") || host == "example.org" || host.endsWith(".example.org") ||
            host == "example.net" || host.endsWith(".example.net") ||
            listOf(".invalid", ".test", ".local", ".localhost", ".example").any { host.endsWith(it) }
        if (policyUri?.scheme != "https" || policyUri?.userInfo != null || blockedHost) {
            problems += "PLAY_PRIVACY_POLICY_URL에 실제 공개 HTTPS 개인정보처리방침 주소가 필요합니다."
        }
        val emailPattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
        if (!emailPattern.matches(playSupportEmail) || playSupportEmail.substringAfter('@').lowercase() in setOf("example.com", "example.org", "example.net")) {
            problems += "PLAY_SUPPORT_EMAIL에 실제 관리할 공개 문의 이메일이 필요합니다."
        }
        if (playDeveloperName.isBlank() || playDeveloperName.lowercase() in setOf("todo", "tbd", "placeholder", "미정")) {
            problems += "PLAY_DEVELOPER_NAME에 확정한 개발자 표시 이름이 필요합니다."
        }
        if (!playPolicyApproved) problems += "운영자가 개인정보처리방침을 검토·확정한 뒤 PLAY_POLICY_APPROVED=true로 명시해야 합니다."
        listOf("ko", "en").forEach { language ->
            val bundledPolicy = file("src/play/assets/privacy/$language.html")
            val publicPolicy = rootProject.file("docs/privacy/$language.html")
            if (!bundledPolicy.isFile || !publicPolicy.isFile) {
                problems += "$language 개인정보처리방침의 앱 내 파일과 공개 웹 원본이 모두 필요합니다."
            } else {
                val text = bundledPolicy.readText(Charsets.UTF_8)
                val finalMarker = Regex("<meta\\s+name=[\"']slivue-policy-status[\"']\\s+content=[\"']final[\"']\\s*/?>", RegexOption.IGNORE_CASE)
                if (!finalMarker.containsMatchIn(text)) problems += "$language 개인정보처리방침은 아직 확정본이 아닙니다. 운영자 검토 후 meta 상태를 final로 바꿔야 합니다."
                if (!bundledPolicy.readBytes().contentEquals(publicPolicy.readBytes())) problems += "$language 앱 내 개인정보처리방침과 공개 웹 원본이 일치하지 않습니다."
            }
        }
        if (playDeveloperName.isNotBlank() && playSupportEmail.isNotBlank() && playPrivacyPolicyUrl.isNotBlank() &&
            listOf("ko", "en").all { file("src/play/assets/privacy/$it.html").isFile && rootProject.file("docs/privacy/$it.html").isFile }) {
            val policyCheck = providers.exec {
                workingDir(rootProject.projectDir)
                commandLine("node", rootProject.file("scripts/verify-play-artifact.mjs").absolutePath,
                    "--policy-sources-only", "--developer-name", playDeveloperName, "--support-email", playSupportEmail, "--policy-url", playPrivacyPolicyUrl)
                isIgnoreExitValue = true
            }
            if (policyCheck.result.get().exitValue != 0) {
                problems += "최종 정책 본문·운영 정보·ISO 시행일 검증 실패: " + policyCheck.standardError.asText.get().trim()
            }
        }
        if (!hasPlayUploadSigning) {
            problems += "PLAY_UPLOAD_STORE_FILE/STORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD의 별도 업로드 서명이 필요합니다."
        } else if (!file(requireNotNull(playUploadStoreFile)).isFile) {
            problems += "PLAY_UPLOAD_STORE_FILE이 실제 키스토어 파일을 가리키지 않습니다."
        } else {
            val uploadKeyValid = runCatching {
                val store = KeyStore.getInstance(file(requireNotNull(playUploadStoreFile)), requireNotNull(playUploadStorePassword).toCharArray())
                val keyValid = store.isKeyEntry(playUploadKeyAlias) && store.getKey(playUploadKeyAlias, requireNotNull(playUploadKeyPassword).toCharArray()) is java.security.PrivateKey
                val uploadCertificate = store.getCertificate(playUploadKeyAlias)
                val prohibited = prohibitedPlayUploadCertificates.toMutableSet()
                if (hasReleaseSigning && file(requireNotNull(releaseStoreFile)).isFile) {
                    runCatching {
                        val directStore = KeyStore.getInstance(file(requireNotNull(releaseStoreFile)), requireNotNull(releaseStorePassword).toCharArray())
                        directStore.getCertificate(releaseKeyAlias)?.let { prohibited += certificateSha256(it.encoded) }
                    }
                }
                if (uploadCertificate != null && certificateSha256(uploadCertificate.encoded) in prohibited) {
                    problems += "기존 legacy/direct 인증서를 Play 업로드 키로 재사용할 수 없습니다. 별도의 업로드 키가 필요합니다."
                }
                keyValid
            }.getOrDefault(false)
            if (!uploadKeyValid) problems += "Play 업로드 키스토어·별칭·비밀번호로 개인키를 확인하지 못했습니다."
        }
        if (problems.isNotEmpty()) throw GradleException("Play 제출 준비 조건이 미완료입니다:\n" + problems.joinToString("\n") { "- $it" })
        logger.lifecycle("로컬 업로드 준비 조건을 확인했습니다. 공개 정책 URL 접근성·계정 적격성·서명 등록·테스트·심사 항목은 Play Console에서 별도 확인해야 합니다.")
    }
}

tasks.configureEach {
    // 집계 태스크뿐 아니라 실제 APK/AAB를 만드는 package/sign 진입점도 보호한다.
    // verify는 파일·운영 정보만 검사하고 빌드 태스크에 의존하지 않아 순환하지 않는다.
    if (hasPlayUploadSigning && playReleaseArtifactTask.matches(name)) {
        dependsOn(verifyPlaySubmissionReady)
    }
    if (name == "bundlePlayRelease") mustRunAfter(verifyPlaySubmissionReady)
}

tasks.register("preparePlaySubmission") {
    group = "distribution"
    description = "로컬 준비 조건 확인 후 Play 업로드 키로 서명한 AAB를 제출 후보 폴더에 준비합니다. 자동 업로드는 하지 않습니다."
    dependsOn(verifyPlaySubmissionReady, "bundlePlayRelease")
    doLast {
        val bundle = layout.buildDirectory.file("outputs/bundle/playRelease/app-play-release.aab").get().asFile
        check(bundle.isFile) { "Play AAB 파일이 없습니다." }
        val uploadStore = KeyStore.getInstance(file(requireNotNull(playUploadStoreFile)), requireNotNull(playUploadStorePassword).toCharArray())
        val uploadCertificate = checkNotNull(uploadStore.getCertificate(playUploadKeyAlias)) { "Play 업로드 인증서가 없습니다." }.encoded
        // JAR 서명을 실제 읽어서 검증한다. 서명 파일의 존재만으로 유효한 서명이라고 판단하지 않는다.
        JarFile(bundle, true).use { jar ->
            var signedEntries = 0
            jar.entries().asSequence().filter { !it.isDirectory && !it.name.startsWith("META-INF/") }.forEach { entry ->
                jar.getInputStream(entry).use { it.copyTo(OutputStream.nullOutputStream()) }
                check(entry.certificates?.any { it.encoded.contentEquals(uploadCertificate) } == true) {
                    "Play AAB의 모든 파일이 설정된 업로드 키로 서명되지 않았습니다: ${entry.name}"
                }
                signedEntries++
            }
            check(signedEntries > 0) { "서명된 Play AAB 본문이 없습니다." }
        }
        val artifactCheck = providers.exec {
            workingDir(rootProject.projectDir)
            commandLine("node", rootProject.file("scripts/verify-play-artifact.mjs").absolutePath, bundle.absolutePath, "--require-upload-signature",
                "--developer-name", playDeveloperName, "--support-email", playSupportEmail, "--policy-url", playPrivacyPolicyUrl)
            isIgnoreExitValue = true
        }
        if (artifactCheck.result.get().exitValue != 0) {
            throw GradleException("Play AAB 실물 검증에 실패했습니다:\n" + artifactCheck.standardError.asText.get())
        }
        val artifactOutput = artifactCheck.standardOutput.asText.get()
        logger.lifecycle(artifactOutput)
        val destination = layout.buildDirectory.dir("play-submission").get().asFile
        destination.mkdirs()
        val generatedAt = Instant.now().toString()
        val bundleHash = certificateSha256(bundle.readBytes())
        val candidateDirectory = destination.resolve("candidate-${generatedAt.replace(":", "").replace(".", "")}-${bundleHash.take(16)}")
        check(candidateDirectory.mkdirs()) { "새 Play 후보 디렉터리를 만들지 못했습니다." }
        val candidate = candidateDirectory.resolve("Slivue-play-${android.defaultConfig.versionName}-upload-signed.aab")
        bundle.copyTo(candidate, overwrite = false)
        check(certificateSha256(candidate.readBytes()) == bundleHash) { "복사한 Play 후보의 SHA-256이 원본과 다릅니다." }
        val receipt = JsonOutput.prettyPrint(JsonOutput.toJson(mapOf(
            "status" to "candidate_ready_local_only",
            "generatedAtUtc" to generatedAt,
            "packageName" to "com.devdooly.slivue",
            "versionCode" to android.defaultConfig.versionCode,
            "versionName" to android.defaultConfig.versionName,
            "candidate" to candidate.relativeTo(destination).invariantSeparatorsPath,
            "sha256" to bundleHash,
            "bytes" to candidate.length(),
            "uploadCertificateSha256" to certificateSha256(uploadCertificate),
            "metadata" to mapOf("developerName" to playDeveloperName, "supportEmail" to playSupportEmail, "privacyPolicyUrl" to playPrivacyPolicyUrl, "policyApproved" to playPolicyApproved),
            "artifactVerification" to JsonSlurper().parseText(artifactOutput),
            "scope" to "이번 로컬 준비 시도의 서명·정책·기술 검증 결과입니다. 공개 URL 접근·계정·앱 서명키 등록·테스트·Play 심사는 별도이며 자동 업로드하지 않았습니다."
        )))
        candidateDirectory.resolve("verification.json").writeText(receipt, Charsets.UTF_8)
        destination.resolve("latest-attempt.json").writeText(receipt, Charsets.UTF_8)
        logger.lifecycle("새 서명 후보와 SHA-256·생성시각·메타데이터 확인서를 ${candidateDirectory.relativeTo(rootProject.projectDir).invariantSeparatorsPath}에 준비했습니다. latest-attempt.json의 상태와 해시를 확인하세요. 자동 업로드·심사 제출은 하지 않았습니다.")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    "directImplementation"(libs.androidx.ui.text.google.fonts)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)

    // Unit Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)

    // Instrumented Testing
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    // Debug Tooling & Test Manifest
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
