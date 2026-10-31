package com.devdooly.notificationedge.service

import android.app.Application
import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.devdooly.notificationedge.data.repository.SettingsRepository
import com.devdooly.notificationedge.util.PrivacyConsent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OpenPanelReceiverTest {

    private lateinit var context: Application

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("notification_edge_sync_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        runBlocking {
            SettingsRepository.getInstance(context).updateExternalControlEnabled(false)
        }
        ShadowSettings.setCanDrawOverlays(true)
        Settings.Secure.putString(context.contentResolver, "enabled_notification_listeners",
            ComponentName(context, NotificationListener::class.java).flattenToString())
        PrivacyConsent.getInstance(context).acceptDisclosure()
        while (shadowOf(context).nextStartedService != null) {
            // 이전 테스트에서 기록된 서비스 시작 이력을 제거한다.
        }
    }

    @After
    fun tearDown() {
        PrivacyConsent.getInstance(context).revokeDisclosure()
    }

    @Test
    fun `외부 제어 기본 비활성 상태에서는 서비스를 시작하지 않는다`() {
        OpenPanelReceiver().onReceive(context, Intent(OpenPanelReceiver.ACTION_OPEN_PANEL))

        assertNull(shadowOf(context).nextStartedService)
    }

    @Test
    fun `외부 제어를 허용하면 알려진 명령만 서비스로 전달한다`() {
        runBlocking {
            SettingsRepository.getInstance(context).updateExternalControlEnabled(true)
        }

        OpenPanelReceiver().onReceive(context, Intent("com.example.UNKNOWN"))
        assertNull(shadowOf(context).nextStartedService)

        OpenPanelReceiver().onReceive(context, Intent(OpenPanelReceiver.ACTION_TOGGLE_PANEL))
        val serviceIntent = shadowOf(context).nextStartedService

        assertEquals(EdgeOverlayService::class.java.name, serviceIntent.component?.className)
        assertEquals(EdgeOverlayService.ACTION_TOGGLE_PANEL, serviceIntent.action)
    }

    @Test
    fun `오버레이 권한이 없으면 외부 명령을 허용해도 서비스를 시작하지 않는다`() {
        runBlocking {
            SettingsRepository.getInstance(context).updateExternalControlEnabled(true)
        }
        ShadowSettings.setCanDrawOverlays(false)

        OpenPanelReceiver().onReceive(context, Intent(OpenPanelReceiver.ACTION_OPEN_PANEL))

        assertNull(shadowOf(context).nextStartedService)
    }

    @Test
    fun `외부 제어를 허용해도 동의를 철회하거나 중지하면 서비스를 시작하지 않는다`() {
        runBlocking { SettingsRepository.getInstance(context).updateExternalControlEnabled(true) }
        PrivacyConsent.getInstance(context).pauseProcessing()
        OpenPanelReceiver().onReceive(context, Intent(OpenPanelReceiver.ACTION_OPEN_PANEL))
        assertNull(shadowOf(context).nextStartedService)
        PrivacyConsent.getInstance(context).revokeDisclosure()
        OpenPanelReceiver().onReceive(context, Intent(OpenPanelReceiver.ACTION_OPEN_PANEL))
        assertNull(shadowOf(context).nextStartedService)
    }
}
