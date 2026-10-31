package com.devdooly.notificationedge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko")
class BrandingTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `Slivue branding uses the selected distribution identity`() {
        assertEquals("Slivue", context.getString(R.string.app_name))
        assertEquals("Slivue", context.applicationInfo.loadLabel(context.packageManager).toString())
        assertEquals(if (BuildConfig.IS_PLAY_DISTRIBUTION) "com.devdooly.slivue" else "com.devdooly.notificationedge", context.packageName)
        assertEquals("화면은 그대로, 알림은 바로.", context.getString(R.string.app_tagline))
    }

    @Test
    fun `user facing service and shortcut labels use the new brand`() {
        listOf(
            R.string.notification_listener_service_name,
            R.string.open_panel_activity_label,
            R.string.overlay_service_title,
            R.string.overlay_service_channel_name,
            R.string.panel_permission_required,
            R.string.test_notification_text,
            R.string.good_lock_integration_description
        ).forEach { assertTrue(context.getString(it).contains("Slivue")) }
    }
}
