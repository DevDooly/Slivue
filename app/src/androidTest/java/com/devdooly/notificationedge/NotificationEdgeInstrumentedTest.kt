package com.devdooly.notificationedge

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationEdgeInstrumentedTest {

    @Test
    fun applicationId_isPreserved() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(if (BuildConfig.IS_PLAY_DISTRIBUTION) "com.devdooly.slivue" else "com.devdooly.notificationedge", appContext.packageName)
    }

    @Test
    fun sensitiveComponents_areNotExported() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageInfo = context.packageManager.getPackageInfoCompat(context.packageName)

        val edgePanelActivity = packageInfo.activities.orEmpty().single {
            it.name == "com.devdooly.notificationedge.ui.overlay.EdgePanelActivity"
        }
        val settingsActivity = packageInfo.activities.orEmpty().single {
            it.name == "com.devdooly.notificationedge.ui.settings.SettingsActivity"
        }
        val privacyActivity = packageInfo.activities.orEmpty().single { it.name.endsWith(".ui.settings.PrivacyPolicyActivity") }
        val overlayService = packageInfo.services.orEmpty().single {
            it.name == "com.devdooly.notificationedge.service.EdgeOverlayService"
        }
        val bootReceiver = packageInfo.receivers.orEmpty().single {
            it.name == "com.devdooly.notificationedge.service.BootReceiver"
        }

        assertFalse(edgePanelActivity.exported)
        assertFalse(settingsActivity.exported)
        assertFalse(privacyActivity.exported)
        assertFalse(overlayService.exported)
        assertFalse(bootReceiver.exported)
    }

    @Test
    fun distributionPermissions_andBackupMatchTheirBoundary() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getPackageInfoCompat(context.packageName)
        val permissions = info.requestedPermissions.orEmpty().toSet()
        assertTrue("android.permission.SYSTEM_ALERT_WINDOW" in permissions)
        assertTrue("android.permission.POST_NOTIFICATIONS" in permissions)
        if (BuildConfig.IS_PLAY_DISTRIBUTION) {
            assertFalse("android.permission.INTERNET" in permissions)
            assertFalse("android.permission.REQUEST_INSTALL_PACKAGES" in permissions)
            assertEquals(0, context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_ALLOW_BACKUP)
            assertFalse(info.providers.orEmpty().any { it.name == "androidx.core.content.FileProvider" })
        } else {
            assertTrue("android.permission.INTERNET" in permissions)
            assertTrue("android.permission.REQUEST_INSTALL_PACKAGES" in permissions)
        }
    }

    @Test
    fun bundledPrivacyText_hasBothLanguagesWithoutActiveContent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (language in listOf("ko", "en")) {
            val text = context.assets.open("privacy/$language.html").bufferedReader().use { it.readText() }
            assertTrue(text.contains("lang=\"$language\""))
            assertFalse(text.contains("<script", ignoreCase = true))
            assertFalse(Regex("<(?:img|iframe|link|script)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text))
        }
    }

    @Test
    fun notificationListener_isProtectedBySystemBindingPermission() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageInfo = context.packageManager.getPackageInfoCompat(context.packageName)
        val listener = packageInfo.services.orEmpty().single {
            it.name == "com.devdooly.notificationedge.service.NotificationListener"
        }

        assertEquals(
            "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
            listener.permission
        )
    }

    @Suppress("DEPRECATION")
    private fun PackageManager.getPackageInfoCompat(packageName: String): PackageInfo {
        val flags = PackageManager.GET_ACTIVITIES or
            PackageManager.GET_SERVICES or
            PackageManager.GET_RECEIVERS or
            PackageManager.GET_PROVIDERS or PackageManager.GET_PERMISSIONS
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            getPackageInfo(packageName, flags)
        }
    }
}
