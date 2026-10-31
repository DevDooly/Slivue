package com.devdooly.notificationedge

import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.devdooly.notificationedge.data.repository.NotificationRepository
import com.devdooly.notificationedge.data.model.EdgeNotification
import com.devdooly.notificationedge.ui.settings.PrivacyPolicyActivity
import com.devdooly.notificationedge.util.PrivacyConsent
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** 실제 Android 파일 시스템에서 동의의 원자 교체와 새 인스턴스 읽기를 검증한다. */
@RunWith(AndroidJUnit4::class)
class InstrumentedConsentStorageTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Before
    fun startWithoutConsent() {
        instrumentation.runOnMainSync { PrivacyConsent.getInstance(context).revokeDisclosure() }
    }

    @After
    fun clearTestConsentAndMemory() {
        instrumentation.runOnMainSync {
            PrivacyConsent.getInstance(context).revokeDisclosure()
            NotificationRepository.clearMemoryOnly()
        }
    }

    @Test
    fun actualNoBackupFile_reloadsAcceptancePauseAndRevocation() {
        val consent = PrivacyConsent.getInstance(context)
        assertFalse(PrivacyConsent(context).state.value.hasCurrentConsent)

        instrumentation.runOnMainSync {
            assertTrue("실제 Android 파일 시스템에 동의를 저장해야 합니다.", consent.acceptDisclosure())
        }
        val accepted = PrivacyConsent(context).state.value
        assertTrue("새 인스턴스가 저장된 동의를 읽어야 합니다.", accepted.hasCurrentConsent)
        assertTrue(accepted.acceptedAtEpochMs > 0)
        val file = File(context.noBackupFilesDir, PrivacyConsent.CONSENT_FILE)
        assertTrue(file.isFile)
        assertFalse(File(file.path + ".new").exists())

        instrumentation.runOnMainSync { consent.pauseProcessing() }
        val paused = PrivacyConsent(context).state.value
        assertTrue("기존 동의 파일이 원자 교체돼야 합니다.", paused.hasCurrentConsent)
        assertTrue("새 인스턴스에서도 중지가 유지돼야 합니다.", paused.isPaused)
        assertFalse(paused.isProcessingEnabled)
        assertFalse(File(file.path + ".new").exists())

        instrumentation.runOnMainSync { consent.revokeDisclosure() }
        assertFalse("철회된 동의가 새 인스턴스에서 복구되면 안 됩니다.", PrivacyConsent(context).state.value.hasCurrentConsent)
        assertFalse(File(file.path + ".new").exists())
    }

    @Test
    fun revokingSystemNotificationAccess_clearsConsentWithoutReturningToSettings() {
        val consent = PrivacyConsent.getInstance(context)
        val originallyAllowed = PrivacyConsent.hasNotificationAccess(context)
        var policy: ActivityScenario<PrivacyPolicyActivity>? = null
        try {
            if (!originallyAllowed) TestNotificationAccess.setEnabled(true)
            assertTrue(PrivacyConsent.hasNotificationAccess(context))
            instrumentation.runOnMainSync {
                assertTrue(consent.observeNotificationAccessChanges())
                assertTrue(consent.acceptDisclosure())
                NotificationRepository.addOrUpdateNotification(EdgeNotification(
                    key = "slivue-consent-observer-${System.nanoTime()}", id = 99124,
                    packageName = context.packageName, appName = "Slivue regression",
                    title = "Consent observer regression", text = "Synthetic only"
                ))
            }
            policy = ActivityScenario.launch(Intent(context, PrivacyPolicyActivity::class.java))
            policy.onActivity { assertFalse(it.isFinishing) }
            assertTrue(consent.state.value.hasCurrentConsent)

            TestNotificationAccess.setEnabled(false)
            val deadline = SystemClock.uptimeMillis() + 10_000L
            while ((consent.state.value.hasCurrentConsent || NotificationRepository.notifications.value.isNotEmpty()) &&
                SystemClock.uptimeMillis() < deadline) {
                instrumentation.waitForIdleSync()
                SystemClock.sleep(25L)
            }
            // 설정 화면 복귀나 명시적인 reconcile 호출 없이 Application 관찰자가 처리해야 한다.
            assertFalse("정책 화면에 머물러도 시스템 권한 철회를 반영해야 합니다.", consent.state.value.hasCurrentConsent)
            assertTrue("철회 즉시 Slivue 메모리 이력도 지워야 합니다.", NotificationRepository.notifications.value.isEmpty())
            assertFalse(PrivacyConsent(context).state.value.hasCurrentConsent)
            policy.onActivity { assertFalse("검증 전에 정책 화면을 닫으면 안 됩니다.", it.isFinishing) }
        } finally {
            try {
                instrumentation.runOnMainSync {
                    consent.revokeDisclosure()
                    NotificationRepository.clearMemoryOnly()
                }
                if (PrivacyConsent.hasNotificationAccess(context) != originallyAllowed) {
                    TestNotificationAccess.setEnabled(originallyAllowed)
                }
            } finally {
                policy?.close()
            }
        }
    }

}
