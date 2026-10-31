package com.devdooly.notificationedge.util

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.devdooly.notificationedge.R
import com.devdooly.notificationedge.data.updater.AppUpdateManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34])
class LocalizedExceptionTest {
    private fun context(language: String): Context {
        val app = ApplicationProvider.getApplicationContext<Context>()
        return app.createConfigurationContext(Configuration(app.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        })
    }

    @Test
    fun `one failure is rendered in the current display language`() {
        val error = LocalizedException(R.string.update_error_response, 403)
        assertEquals("GitHub returned HTTP 403.", error.userMessage(context("en-US"), R.string.update_error_check))
        assertEquals("GitHub 응답 오류: 403", error.userMessage(context("ko-KR"), R.string.update_error_check))
    }

    @Test
    fun `platform errors use a translated fallback rather than raw exception text`() {
        val error = IOException("Internal server detail")
        for (language in listOf("en-US", "ko-KR")) {
            val context = context(language)
            assertEquals(context.getString(R.string.update_error_download), error.userMessage(context, R.string.update_error_download))
        }
    }

    @Test
    fun `missing APK keeps its verification failure and can be translated`() {
        val context = context("en-US")
        val error = AppUpdateManager.validateDownloadedApk(context, File(context.cacheDir, "not-present-localization.apk"))
            .exceptionOrNull()
        assertTrue(error is LocalizedException)
        assertEquals(R.string.update_error_invalid_file, (error as LocalizedException).messageRes)
        assertEquals("The APK is missing or has an invalid size.", error.userMessage(context, R.string.update_error_install))
    }

    @Test
    fun `missing release assets preserve a specific localized reason`() {
        val error = runCatching { AppUpdateManager.selectReleaseDownloads("v1.3.20", emptyList()) }.exceptionOrNull()
        assertTrue(error is LocalizedException)
        assertEquals(R.string.update_error_missing_apk, (error as LocalizedException).messageRes)
    }
}
