package com.devdooly.notificationedge.data.updater

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {

    @Test
    fun `isNewerVersion should return true only when latest version is newer`() {
        assertTrue(AppUpdateManager.isNewerVersion("1.0.0", "1.0.1"))
        assertTrue(AppUpdateManager.isNewerVersion("1.0.0", "v1.0.1"))
        assertTrue(AppUpdateManager.isNewerVersion("v1.0.0", "1.0.1"))
        assertTrue(AppUpdateManager.isNewerVersion("1.3.13", "1.4.0"))
    }

    @Test
    fun `isNewerVersion should return false when same version or latest is empty`() {
        assertFalse(AppUpdateManager.isNewerVersion("1.0.1", "1.0.1"))
        assertFalse(AppUpdateManager.isNewerVersion("v1.0.1", "1.0.1"))
        assertFalse(AppUpdateManager.isNewerVersion("1.0.1", "v1.0.1"))
        assertFalse(AppUpdateManager.isNewerVersion("1.0.1", ""))
        assertFalse(AppUpdateManager.isNewerVersion("1.4.15", "1.0.1"))
        assertFalse(AppUpdateManager.isNewerVersion("1.0.1", "not-a-version"))
    }

    @Test
    fun `download URL should allow only trusted HTTPS hosts`() {
        assertTrue(AppUpdateManager.isAllowedDownloadUrl("https://github.com/DevDooly/message_edge/file.apk"))
        assertTrue(AppUpdateManager.isAllowedDownloadUrl("https://release-assets.githubusercontent.com/file.apk"))
        assertFalse(AppUpdateManager.isAllowedDownloadUrl("http://github.com/file.apk"))
        assertFalse(AppUpdateManager.isAllowedDownloadUrl("https://github.com.evil.example/file.apk"))
        assertFalse(AppUpdateManager.isAllowedDownloadUrl("https://evil.example/file.apk"))
    }

    @Test
    fun `renamed release selects versioned Slivue APK and its own checksum regardless of asset order`() {
        val assets = listOf(
            asset("NotificationEdge-v1.3.18.apk.sha256"),
            asset("NotificationEdge-v1.3.18.apk"),
            asset("Slivue.apk"),
            asset("Slivue-v1.3.18.apk.sha256"),
            asset("Slivue-v1.3.18.apk")
        )
        val selected = AppUpdateManager.selectReleaseDownloads("v1.3.18", assets)
        assertEquals(asset("Slivue-v1.3.18.apk").downloadUrl, selected.apkUrl)
        assertEquals(asset("Slivue-v1.3.18.apk.sha256").downloadUrl, selected.checksumUrl)
    }

    @Test
    fun `legacy release remains downloadable with matching checksum`() {
        val selected = AppUpdateManager.selectReleaseDownloads("v1.3.17", listOf(
            asset("NotificationEdge.apk"),
            asset("NotificationEdge-v1.3.17.apk"),
            asset("NotificationEdge-v1.3.17.apk.sha256")
        ))
        assertEquals(asset("NotificationEdge-v1.3.17.apk").downloadUrl, selected.apkUrl)
        assertEquals(asset("NotificationEdge-v1.3.17.apk.sha256").downloadUrl, selected.checksumUrl)
    }

    @Test
    fun `unrelated checksum is never attached to the selected APK`() {
        val selected = AppUpdateManager.selectReleaseDownloads("v1.3.18", listOf(
            asset("Slivue-v1.3.18.apk"),
            asset("different-build.apk.sha256")
        ))
        assertNull(selected.checksumUrl)
    }

    @Test
    fun `unversioned alias supports its matching checksum`() {
        val selected = AppUpdateManager.selectReleaseDownloads("v1.3.19", listOf(
            asset("Slivue.apk.sha256"), asset("Slivue.apk")
        ))
        assertEquals(asset("Slivue.apk.sha256").downloadUrl, selected.checksumUrl)
    }

    @Test
    fun `unknown future filename remains supported with matching checksum`() {
        val selected = AppUpdateManager.selectReleaseDownloads("v2.0.0", listOf(
            asset("future.apk.sha256"), asset("future.apk")
        ))
        assertEquals(asset("future.apk").downloadUrl, selected.apkUrl)
        assertEquals(asset("future.apk.sha256").downloadUrl, selected.checksumUrl)
    }

    @Test(expected = IllegalStateException::class)
    fun `missing APK fails instead of inventing a legacy download URL`() {
        AppUpdateManager.selectReleaseDownloads("v1.3.18", listOf(asset("source.zip")))
    }

    private fun asset(name: String) = ReleaseAsset(
        name, "https://github.com/DevDooly/message_edge/releases/download/test/$name"
    )
}
