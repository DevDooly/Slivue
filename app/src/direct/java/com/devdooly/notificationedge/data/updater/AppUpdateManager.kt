package com.devdooly.notificationedge.data.updater

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.devdooly.notificationedge.R
import com.devdooly.notificationedge.util.LocalizedException
import com.devdooly.notificationedge.util.requireLocalized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class ReleaseInfo(
    val tagName: String,
    val title: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val sha256: String?,
    val hasUpdate: Boolean,
    val publishedAt: String
)

internal data class ReleaseAsset(val name: String, val downloadUrl: String)

internal data class ReleaseDownloads(val apkUrl: String, val checksumUrl: String?)

/** 직접 배포판 전용 업데이터. Play 소스 세트에는 포함하지 않는다. */
object AppUpdateManager {

    private const val GITHUB_REPO = "DevDooly/message_edge"
    private const val API_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
    private const val MAX_REDIRECTS = 5
    private const val MAX_APK_BYTES = 200L * 1024L * 1024L
    private const val MAX_CHECKSUM_BYTES = 4096

    private val semanticVersionPattern = Regex("""^(\d+)\.(\d+)\.(\d+)(?:[-+]([0-9A-Za-z.-]+))?$""")
    private val sha256Pattern = Regex("""(?i)\b[0-9a-f]{64}\b""")

    suspend fun checkForUpdate(currentVersionName: String): Result<ReleaseInfo> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = openFollowingRedirects(API_URL, 10_000, 10_000)
            requireSuccessfulResponse(connection)

            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseBody)
            val tagName = json.optString("tag_name", "")
            val title = json.optString("name", tagName)
            val body = json.optString("body", "")
            val publishedAt = json.optString("published_at", "")

            val releaseAssets = mutableListOf<ReleaseAsset>()
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (index in 0 until assets.length()) {
                    val asset = assets.getJSONObject(index)
                    releaseAssets += ReleaseAsset(
                        name = asset.optString("name", ""),
                        downloadUrl = asset.optString("browser_download_url", "")
                    )
                }
            }

            val downloads = selectReleaseDownloads(tagName, releaseAssets)
            val downloadUrl = downloads.apkUrl
            requireLocalized(isAllowedDownloadUrl(downloadUrl), R.string.update_error_url)

            val checksum = downloads.checksumUrl?.let(::fetchSha256)
            Result.success(
                ReleaseInfo(
                    tagName = tagName,
                    title = title,
                    releaseNotes = body,
                    downloadUrl = downloadUrl,
                    sha256 = checksum,
                    hasUpdate = isNewerVersion(currentVersionName, tagName),
                    publishedAt = publishedAt
                )
            )
        } catch (error: Exception) {
            Result.failure(error)
        } finally {
            connection?.disconnect()
        }
    }

    /** 새 브랜드 자산을 우선하되 이전 릴리스 자산과 해당 파일의 체크섬도 지원한다. */
    internal fun selectReleaseDownloads(tagName: String, assets: List<ReleaseAsset>): ReleaseDownloads {
        val available = assets.filter { it.downloadUrl.isNotBlank() }
        val preferredNames = listOf(
            "Slivue-$tagName.apk", "Slivue.apk",
            "NotificationEdge-$tagName.apk", "NotificationEdge.apk"
        )
        val apk = preferredNames.firstNotNullOfOrNull { preferred ->
            available.firstOrNull { it.name.equals(preferred, ignoreCase = true) }
        } ?: available.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
            ?: throw LocalizedException(R.string.update_error_missing_apk)
        val checksum = available.firstOrNull {
            it.name.equals("${apk.name}.sha256", ignoreCase = true)
        }
        return ReleaseDownloads(apk.downloadUrl, checksum?.downloadUrl)
    }

    /** 현재 버전보다 최신 SemVer인 경우에만 true를 반환한다. */
    internal fun isNewerVersion(current: String, latest: String): Boolean {
        val currentParts = parseSemanticVersion(current) ?: return false
        val latestParts = parseSemanticVersion(latest) ?: return false

        for (index in 0..2) {
            val comparison = latestParts[index].compareTo(currentParts[index])
            if (comparison != 0) return comparison > 0
        }
        return false
    }

    private fun parseSemanticVersion(value: String): List<Long>? {
        val match = semanticVersionPattern.matchEntire(value.removePrefix("v").trim()) ?: return null
        return (1..3).map { groupIndex -> match.groupValues[groupIndex].toLongOrNull() ?: return null }
    }

    internal fun isAllowedDownloadUrl(rawUrl: String): Boolean {
        return runCatching {
            val url = URL(rawUrl)
            val host = url.host.lowercase()
            url.protocol.equals("https", ignoreCase = true) &&
                    (host == "github.com" ||
                            host == "api.github.com" ||
                            host.endsWith(".githubusercontent.com"))
        }.getOrDefault(false)
    }

    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        expectedSha256: String?,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        val targetFile = File(context.cacheDir, "Slivue_update.apk")
        val partialFile = File(context.cacheDir, "Slivue_update.apk.part")
        try {
            val normalizedChecksum = expectedSha256?.trim()?.lowercase()
            requireLocalized(
                normalizedChecksum != null && sha256Pattern.matches(normalizedChecksum),
                R.string.update_error_missing_checksum
            )
            requireLocalized(isAllowedDownloadUrl(downloadUrl), R.string.update_error_url)

            connection = openFollowingRedirects(downloadUrl, 15_000, 30_000)
            requireSuccessfulResponse(connection)

            val contentLength = connection.contentLengthLong
            requireLocalized(contentLength <= 0 || contentLength <= MAX_APK_BYTES, R.string.update_error_size)

            partialFile.delete()
            val digest = MessageDigest.getInstance("SHA-256")
            var totalDownloaded = 0L
            connection.inputStream.use { input ->
                FileOutputStream(partialFile).use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        totalDownloaded += count
                        requireLocalized(totalDownloaded <= MAX_APK_BYTES, R.string.update_error_size)
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        if (contentLength > 0) {
                            withContext(Dispatchers.Main) {
                                onProgress((totalDownloaded.toFloat() / contentLength).coerceIn(0f, 1f))
                            }
                        }
                    }
                    output.fd.sync()
                }
            }

            val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
            requireLocalized(actualSha256 == normalizedChecksum, R.string.update_error_checksum_mismatch)

            targetFile.delete()
            requireLocalized(partialFile.renameTo(targetFile), R.string.update_error_save)
            validateDownloadedApk(context, targetFile).getOrThrow()

            withContext(Dispatchers.Main) { onProgress(1f) }
            Result.success(targetFile)
        } catch (error: Exception) {
            partialFile.delete()
            targetFile.delete()
            Result.failure(error)
        } finally {
            connection?.disconnect()
        }
    }

    internal fun validateDownloadedApk(context: Context, apkFile: File): Result<Unit> = runCatching {
        requireLocalized(apkFile.exists() && apkFile.length() in 1..MAX_APK_BYTES, R.string.update_error_invalid_file)

        val packageManager = context.packageManager
        val archiveInfo = getPackageInfo(packageManager, apkFile.absolutePath)
            ?: throw LocalizedException(R.string.update_error_archive_info)
        val installedInfo = getPackageInfo(packageManager, context.packageName)
            ?: throw LocalizedException(R.string.update_error_installed_info)

        requireLocalized(archiveInfo.packageName == context.packageName, R.string.update_error_package)
        requireLocalized(
            packageVersionCode(archiveInfo) >= packageVersionCode(installedInfo),
            R.string.update_error_downgrade
        )

        val archiveSigners = signerDigests(archiveInfo)
        val installedSigners = signerDigests(installedInfo)
        requireLocalized(
            archiveSigners.isNotEmpty() && archiveSigners.any(installedSigners::contains),
            R.string.update_error_signature
        )
    }

    fun installApk(context: Context, apkFile: File): Result<Unit> = runCatching {
        validateDownloadedApk(context, apkFile).getOrThrow()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val permissionIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(permissionIntent)
            return@runCatching
        }

        val authority = "${context.packageName}.fileprovider"
        val apkUri = FileProvider.getUriForFile(context, authority, apkFile)
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(installIntent)
    }

    private fun fetchSha256(checksumUrl: String): String? {
        requireLocalized(isAllowedDownloadUrl(checksumUrl), R.string.update_error_checksum_url)
        var connection: HttpURLConnection? = null
        return try {
            connection = openFollowingRedirects(checksumUrl, 10_000, 10_000)
            requireSuccessfulResponse(connection)
            val bytes = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(512)
                var totalBytes = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    totalBytes += count
                    requireLocalized(totalBytes <= MAX_CHECKSUM_BYTES, R.string.update_error_checksum_size)
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            sha256Pattern.find(bytes.toString(Charsets.UTF_8))?.value?.lowercase()
        } finally {
            connection?.disconnect()
        }
    }

    private fun openFollowingRedirects(
        rawUrl: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int
    ): HttpURLConnection {
        var currentUrl = URL(rawUrl)
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            requireLocalized(isAllowedDownloadUrl(currentUrl.toString()), R.string.update_error_host)
            val connection = (currentUrl.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json, application/octet-stream")
                setRequestProperty("User-Agent", "Slivue-Android")
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
            }
            val status = connection.responseCode
            if (status in listOf(301, 302, 303, 307, 308)) {
                val location = connection.getHeaderField("Location")
                    ?: throw LocalizedException(R.string.update_error_redirect_missing)
                connection.disconnect()
                requireLocalized(redirectCount < MAX_REDIRECTS, R.string.update_error_redirect_limit)
                currentUrl = URL(currentUrl, location)
            } else {
                return connection
            }
        }
        throw LocalizedException(R.string.update_error_redirect_limit)
    }

    private fun requireSuccessfulResponse(connection: HttpURLConnection) {
        requireLocalized(connection.responseCode in 200..299, R.string.update_error_response, connection.responseCode)
    }

    @Suppress("DEPRECATION")
    private fun getPackageInfo(
        packageManager: PackageManager,
        packageNameOrArchivePath: String
    ): android.content.pm.PackageInfo? {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        return if (packageNameOrArchivePath.endsWith(".apk", ignoreCase = true)) {
            packageManager.getPackageArchiveInfo(packageNameOrArchivePath, flags)
        } else {
            try {
                packageManager.getPackageInfo(packageNameOrArchivePath, flags)
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun signerDigests(packageInfo: android.content.pm.PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.signingCertificateHistory.orEmpty()
        } else {
            packageInfo.signatures.orEmpty()
        }
        return signatures.mapTo(mutableSetOf()) { signature ->
            MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }
    }

    @Suppress("DEPRECATION")
    private fun packageVersionCode(packageInfo: android.content.pm.PackageInfo): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            packageInfo.versionCode.toLong()
        }
    }
}
