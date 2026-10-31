package com.devdooly.notificationedge.ui.settings

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.devdooly.notificationedge.BuildConfig
import com.devdooly.notificationedge.service.NotificationListener
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34])
class NotificationAccessNavigationTest {
    @Test
    fun `현재 배포판 리스너의 상세 권한 화면을 우선 연다`() {
        val context = mockk<Context>()
        every { context.packageName } returns BuildConfig.APPLICATION_ID
        var opened: Intent? = null
        every { context.startActivity(any()) } answers { opened = firstArg() }
        openNotificationAccessSettings(context)
        val intent = requireNotNull(opened)
        if (Build.VERSION.SDK_INT >= 30) {
            assertEquals(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS, intent.action)
            assertEquals(ComponentName(context, NotificationListener::class.java).flattenToString(), intent.getStringExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME))
        } else assertEquals(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, intent.action)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun `제조사가 상세 화면을 제공하지 않으면 전체 권한 목록으로 복귀한다`() {
        val context = mockk<Context>()
        every { context.packageName } returns BuildConfig.APPLICATION_ID
        val attempted = mutableListOf<String?>()
        every { context.startActivity(any()) } answers {
            val intent = firstArg<Intent>()
            attempted += intent.action
            if (intent.action == Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS) throw ActivityNotFoundException()
        }
        openNotificationAccessSettings(context)
        assertEquals(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, attempted.last())
        assertEquals(if (Build.VERSION.SDK_INT >= 30) 2 else 1, attempted.size)
    }
}
