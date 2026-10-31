package com.devdooly.notificationedge.util

import android.app.Application
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Intent
import android.os.Looper
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.devdooly.notificationedge.data.model.EdgeNotification
import com.devdooly.notificationedge.data.model.NotificationActionItem
import com.devdooly.notificationedge.data.repository.NotificationRepository
import com.devdooly.notificationedge.service.NotificationListener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34])
class PrivacyConsentTest {
    private lateinit var application: Application
    private lateinit var consent: PrivacyConsent
    private lateinit var consentFile: File

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        consentFile = File(application.noBackupFilesDir, PrivacyConsent.CONSENT_FILE)
        consentFile.delete()
        File(consentFile.path + ".bak").delete()
        File(consentFile.path + ".new").delete()
        setNotificationAccess(false)
        consent = PrivacyConsent.getInstance(application)
        consent.revokeDisclosure()
        NotificationRepository.clearMemoryOnly()
    }

    @After
    fun tearDown() {
        setNotificationAccess(false)
        consent.revokeDisclosure()
        NotificationRepository.clearMemoryOnly()
        consentFile.delete()
        File(consentFile.path + ".bak").delete()
        File(consentFile.path + ".new").delete()
    }

    @Test
    fun `시스템 알림 접근이 남아 있어도 새 설치는 명시적 동의가 필요하다`() {
        setNotificationAccess(true)
        assertFalse(consent.state.value.hasCurrentConsent)
        assertFalse(consent.canProcessNotifications())
    }

    @Test
    fun `동의 후 시스템 설정을 열기 전에는 동의를 자동 철회하지 않는다`() {
        assertTrue(consent.acceptDisclosure())
        consent.reconcileNotificationAccess()
        assertTrue(consent.state.value.hasCurrentConsent)
        assertFalse(consent.canProcessNotifications())

        setNotificationAccess(true)
        consent.reconcileNotificationAccess()
        assertTrue(consent.canProcessNotifications())
    }

    @Test
    fun `시스템 접근은 정확한 리스너 컴포넌트가 허용된 경우만 인정한다`() {
        consent.acceptDisclosure()
        Settings.Secure.putString(
            application.contentResolver,
            "enabled_notification_listeners",
            ComponentName(application.packageName, application.packageName + ".OtherListener").flattenToString()
        )
        assertFalse(PrivacyConsent.hasNotificationAccess(application))
        assertFalse(consent.canProcessNotifications())

        setNotificationAccess(true)
        assertTrue(PrivacyConsent.hasNotificationAccess(application))
        assertTrue(consent.canProcessNotifications())
    }

    @Test
    fun `허용했던 시스템 권한을 철회하면 동의와 Slivue 메모리만 지운다`() {
        setNotificationAccess(true)
        consent.acceptDisclosure()
        var originalNotificationCancelled = false
        NotificationRepository.addOrUpdateNotification(sampleNotification())
        NotificationRepository.cancelAllNotificationsCallback = { originalNotificationCancelled = true }

        setNotificationAccess(false)
        consent.reconcileNotificationAccess()

        assertFalse(consent.state.value.hasCurrentConsent)
        assertFalse(consent.canProcessNotifications())
        assertTrue(NotificationRepository.notifications.value.isEmpty())
        assertFalse(originalNotificationCancelled)
        assertEquals(null, NotificationRepository.cancelAllNotificationsCallback)
        assertFalse(PrivacyConsent(application).state.value.hasCurrentConsent)
    }

    @Test
    fun `전체 중지는 동의를 유지하지만 이력을 지우고 재시작 뒤에도 중지한다`() {
        setNotificationAccess(true)
        consent.acceptDisclosure()
        NotificationRepository.addOrUpdateNotification(sampleNotification())
        val oldGeneration = consent.accessGeneration

        consent.pauseProcessing()

        assertTrue(consent.state.value.hasCurrentConsent)
        assertTrue(consent.state.value.isPaused)
        assertFalse(consent.canProcessNotifications())
        assertTrue(NotificationRepository.notifications.value.isEmpty())
        assertTrue(consent.accessGeneration > oldGeneration)
        val reloaded = PrivacyConsent(application)
        assertTrue("저장=${consentFile.readText()} / 재로드=${reloaded.state.value}", reloaded.state.value.isPaused)
        assertFalse(reloaded.canProcessNotifications())
        assertTrue(reloaded.resumeProcessing())
        assertTrue(reloaded.canProcessNotifications())
    }

    @Test
    fun `동의 철회 이후 재개만으로 다시 알림을 처리하지 않는다`() {
        setNotificationAccess(true)
        consent.acceptDisclosure()
        consent.revokeDisclosure()

        assertFalse(consent.resumeProcessing())
        assertFalse(consent.canProcessNotifications())
        assertFalse(PrivacyConsent(application).state.value.hasCurrentConsent)
    }

    @Test
    fun `동의는 noBackup 영역에만 저장하며 파일이 복원되지 않으면 다시 동의한다`() {
        consent.acceptDisclosure()
        assertTrue(consentFile.isFile)
        assertFalse(File(application.filesDir, PrivacyConsent.CONSENT_FILE).exists())
        assertTrue("저장=${consentFile.readText()}", PrivacyConsent(application).state.value.hasCurrentConsent)

        consentFile.delete()
        assertFalse(PrivacyConsent(application).state.value.hasCurrentConsent)
    }

    @Test
    fun `손상되거나 구버전인 동의 파일은 허용으로 간주하지 않는다`() {
        consentFile.writeText("version=oops\naccepted_at=100\npaused=false\nobserved_system_access=false")
        assertFalse(PrivacyConsent(application).state.value.hasCurrentConsent)
        consentFile.writeText("version=0\naccepted_at=100\npaused=false\nobserved_system_access=false")
        assertFalse(PrivacyConsent(application).state.value.hasCurrentConsent)
        consentFile.writeText("version=1\naccepted_at=100\npaused=broken\nobserved_system_access=false")
        assertFalse(PrivacyConsent(application).state.value.hasCurrentConsent)
    }

    @Test
    fun `동의 전과 중지 후 빠른 답장은 원래 앱에 전달하지 않는다`() {
        val pendingIntent = PendingIntent.getBroadcast(
            application, 71, Intent("example.PRIVACY_REPLY").setPackage(application.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val action = NotificationActionItem("답장", pendingIntent, true, "reply")
        val beforeCount = shadowOf(application).broadcastIntents.size
        setNotificationAccess(true)

        NotificationRepository.sendQuickReply(application, "privacy-test", action, "동의 전 답장")
        assertEquals(beforeCount, shadowOf(application).broadcastIntents.size)

        consent.acceptDisclosure()
        NotificationRepository.sendQuickReply(application, "privacy-test", action, "허용한 답장")
        assertEquals(beforeCount + 1, shadowOf(application).broadcastIntents.size)
        val forwarded = shadowOf(application).broadcastIntents.last()
        assertEquals("허용한 답장", RemoteInput.getResultsFromIntent(forwarded).getCharSequence("reply"))

        consent.pauseProcessing()
        NotificationRepository.sendQuickReply(application, "privacy-test", action, "중지 후 답장")
        assertEquals(beforeCount + 1, shadowOf(application).broadcastIntents.size)
    }

    @Test
    fun `중지 후 재개해도 이전 세대의 작업은 알림을 다시 주입하지 못한다`() {
        setNotificationAccess(true)
        consent.acceptDisclosure()
        val oldGeneration = consent.accessGeneration
        val previousState = consent.state.value
        consent.pauseProcessing()
        assertTrue(consent.resumeProcessing())
        consent.withCurrentAccess(oldGeneration) { NotificationRepository.addOrUpdateNotification(sampleNotification()) }
        assertTrue(NotificationRepository.notifications.value.isEmpty())
        assertTrue(previousState != consent.state.value)
        consent.withCurrentAccess(consent.accessGeneration) { NotificationRepository.addOrUpdateNotification(sampleNotification()) }
        assertEquals(1, NotificationRepository.notifications.value.size)
    }

    @Test
    fun `최종 동작과 중지는 동일 모니터로 순서를 보장한다`() {
        setNotificationAccess(true)
        consent.acceptDisclosure()
        val generation = consent.accessGeneration
        val effectStarted = CountDownLatch(1)
        val releaseEffect = CountDownLatch(1)
        val stopStarted = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        val effect = thread {
            consent.withCurrentAccess(generation) {
                effectStarted.countDown()
                check(releaseEffect.await(5, TimeUnit.SECONDS))
            }
        }
        assertTrue(effectStarted.await(5, TimeUnit.SECONDS))
        val stop = thread {
            stopStarted.countDown()
            consent.pauseProcessing()
            stopped.countDown()
        }
        try {
            assertTrue(stopStarted.await(5, TimeUnit.SECONDS))
            assertFalse(stopped.await(100, TimeUnit.MILLISECONDS))
        } finally {
            releaseEffect.countDown()
            effect.join(5000)
            stop.join(5000)
        }
        assertEquals(0L, stopped.count)
        var lateEffect = false
        consent.withCurrentAccess(generation) { lateEffect = true }
        assertFalse(lateEffect)
    }

    @Test
    fun `시스템 권한이 없으면 재개 성공으로 처리하지 않는다`() {
        consent.acceptDisclosure()
        consent.pauseProcessing()
        assertFalse(consent.resumeProcessing())
        assertTrue(consent.state.value.isPaused)
        setNotificationAccess(true)
        assertTrue(consent.resumeProcessing())
        setNotificationAccess(false)
        assertFalse(consent.resumeProcessing())
        assertFalse(consent.state.value.hasCurrentConsent)
    }

    @Test
    fun `늦게 도착한 중지 관찰은 재개한 리스너를 해제하지 않는다`() {
        setNotificationAccess(true)
        consent.acceptDisclosure()
        consent.pauseProcessing()
        assertTrue(consent.resumeProcessing())
        var unbound = false
        consent.whenProcessingInactive { unbound = true }
        assertFalse(unbound)
    }

    @Test
    fun `권한 변경 관찰은 화면 복귀 없이 동의와 메모리를 즉시 철회한다`() {
        assertTrue(consent.observeNotificationAccessChanges())
        assertTrue(consent.observeNotificationAccessChanges())
        setNotificationAccess(true)
        assertTrue(consent.acceptDisclosure())
        NotificationRepository.addOrUpdateNotification(sampleNotification())
        var originalNotificationCancelled = false
        NotificationRepository.cancelAllNotificationsCallback = { originalNotificationCancelled = true }
        assertTrue(consent.state.value.hasCurrentConsent)

        setNotificationAccess(false)
        application.contentResolver.notifyChange(Settings.Secure.getUriFor("enabled_notification_listeners"), null)
        shadowOf(Looper.getMainLooper()).idle()

        // reconcileNotificationAccess나 Activity.onResume을 직접 호출하지 않는다.
        assertFalse(consent.state.value.hasCurrentConsent)
        assertFalse(consent.canProcessNotifications())
        assertTrue(NotificationRepository.notifications.value.isEmpty())
        assertFalse(originalNotificationCancelled)
        assertFalse(PrivacyConsent(application).state.value.hasCurrentConsent)
    }

    private fun setNotificationAccess(enabled: Boolean) {
        Settings.Secure.putString(
            application.contentResolver,
            "enabled_notification_listeners",
            if (enabled) ComponentName(application, NotificationListener::class.java).flattenToString() else ""
        )
    }

    private fun sampleNotification() = EdgeNotification(
        key = "privacy-test", id = 1, packageName = "example.chat", appName = "Example",
        title = "테스트", text = "실제 사용자 정보가 아닌 테스트 메시지", timestamp = 1L
    )
}
