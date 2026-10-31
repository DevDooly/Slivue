package com.devdooly.notificationedge.ui.settings

import android.app.Application
import android.content.Intent
import android.content.ComponentName
import android.net.Uri
import android.os.Looper
import android.provider.Settings
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.devdooly.notificationedge.data.model.AppSettings
import com.devdooly.notificationedge.data.repository.SettingsRepository
import com.devdooly.notificationedge.service.EdgeOverlayService
import com.devdooly.notificationedge.service.NotificationListener
import com.devdooly.notificationedge.util.PrivacyConsent
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34])
class SettingsActivityTest {

    private lateinit var application: Application
    private lateinit var savedSettings: MutableStateFlow<AppSettings>
    private lateinit var controller: ActivityController<SettingsActivity>

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        savedSettings = MutableStateFlow(AppSettings())
        val repository = mockk<SettingsRepository>()
        every { repository.settingsFlow } returns savedSettings
        mockkObject(SettingsRepository.Companion)
        every { SettingsRepository.getInstance(any()) } returns repository
        ShadowSettings.setCanDrawOverlays(false)
        controller = Robolectric.buildActivity(SettingsActivity::class.java)
            .create().start().resume()
        drainMainLooper()
    }

    @After
    fun tearDown() {
        if (::controller.isInitialized) {
            when (controller.get().lifecycle.currentState) {
                Lifecycle.State.RESUMED -> controller.pause().stop().destroy()
                Lifecycle.State.STARTED -> controller.stop().destroy()
                Lifecycle.State.CREATED -> controller.destroy()
                else -> Unit
            }
        }
        unmockkObject(SettingsRepository.Companion)
    }

    @Test
    fun `오버레이 권한 화면으로 이동해도 설정 화면을 종료하지 않는다`() {
        val activity = controller.get()
        val permissionIntent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${activity.packageName}")
        )

        activity.startActivity(permissionIntent)
        controller.pause().stop()

        assertEquals(permissionIntent, shadowOf(activity).nextStartedActivity)
        assertFalse(activity.isFinishing)
        assertFalse(activity.isDestroyed)
        assertEquals(Lifecycle.State.CREATED, activity.lifecycle.currentState)
    }

    @Test
    fun `오버레이 허용 후 돌아오면 같은 설정 화면에서 서비스를 시작한다`() {
        val activity = controller.get()
        assertNull(shadowOf(application).nextStartedService)
        controller.pause().stop()
        ShadowSettings.setCanDrawOverlays(true)
        Settings.Secure.putString(application.contentResolver, "enabled_notification_listeners", ComponentName(application, NotificationListener::class.java).flattenToString())
        assertTrue(PrivacyConsent.getInstance(application).acceptDisclosure())

        controller.restart().start().resume()
        drainMainLooper()

        assertSame(activity, controller.get())
        assertFalse(activity.isFinishing)
        assertEquals(Lifecycle.State.RESUMED, activity.lifecycle.currentState)
        assertEquals(
            EdgeOverlayService::class.java.name,
            shadowOf(application).nextStartedService?.component?.className
        )
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `권한을 거부하고 돌아와도 설정 화면에서 다시 요청할 수 있다`() {
        val activity = controller.get()
        controller.pause().stop()

        controller.restart().start().resume()
        drainMainLooper()

        assertFalse(activity.isFinishing)
        assertEquals(Lifecycle.State.RESUMED, activity.lifecycle.currentState)
        assertFalse(Settings.canDrawOverlays(activity))
        assertNull(shadowOf(application).nextStartedService)
        assertNull(shadowOf(activity).nextStartedActivity)

        activity.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        assertEquals(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            shadowOf(activity).nextStartedActivity.action
        )
    }

    @Test
    fun `알림 접근 권한과 배터리 설정에서 연속 복귀해도 설정 화면이 유지된다`() {
        val activity = controller.get()
        listOf(
            Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS,
            Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
        ).forEach { action ->
            activity.startActivity(Intent(action))
            controller.pause().stop()
            assertFalse(activity.isFinishing)
            assertEquals(action, shadowOf(activity).nextStartedActivity.action)

            controller.restart().start().resume()
            drainMainLooper()

            assertSame(activity, controller.get())
            assertEquals(Lifecycle.State.RESUMED, activity.lifecycle.currentState)
            assertNull(shadowOf(activity).nextStartedActivity)
        }
    }

    @Test
    fun `서비스와 엣지 라이팅을 모두 끄면 권한 허용 후에도 서비스를 시작하지 않는다`() {
        savedSettings.value = AppSettings(isServiceEnabled = false, isEdgeLightingEnabled = false)
        controller.pause().stop()
        ShadowSettings.setCanDrawOverlays(true)
        Settings.Secure.putString(application.contentResolver, "enabled_notification_listeners", ComponentName(application, NotificationListener::class.java).flattenToString())
        assertTrue(PrivacyConsent.getInstance(application).acceptDisclosure())

        controller.restart().start().resume()
        drainMainLooper()

        assertFalse(controller.get().isFinishing)
        assertNull(shadowOf(application).nextStartedService)
    }

    @Test
    fun `화면 재생성 뒤에도 권한 설정 복귀가 가능하다`() {
        val original = controller.get()

        controller.recreate()
        drainMainLooper()
        val recreated = controller.get()
        assertNotSame(original, recreated)
        assertFalse(recreated.isFinishing)
        assertEquals(Lifecycle.State.RESUMED, recreated.lifecycle.currentState)

        controller.pause().stop().restart().start().resume()
        drainMainLooper()

        assertFalse(recreated.isFinishing)
        assertEquals(Lifecycle.State.RESUMED, recreated.lifecycle.currentState)
    }

    @Test
    fun `명시적으로 뒤로가기를 누르면 설정 화면을 종료한다`() {
        val activity = controller.get()

        activity.onBackPressedDispatcher.onBackPressed()

        assertTrue(activity.isFinishing)
    }

    private fun drainMainLooper() {
        shadowOf(Looper.getMainLooper()).idle()
    }
}
