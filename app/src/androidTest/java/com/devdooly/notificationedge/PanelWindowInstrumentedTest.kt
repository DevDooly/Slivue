package com.devdooly.notificationedge

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Rect
import android.view.accessibility.AccessibilityWindowInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.devdooly.notificationedge.data.model.EdgeNotification
import com.devdooly.notificationedge.data.model.NotificationActionItem
import com.devdooly.notificationedge.data.repository.NotificationRepository
import com.devdooly.notificationedge.ui.overlay.EdgePanelActivity
import com.devdooly.notificationedge.util.PrivacyConsent
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream

@RunWith(AndroidJUnit4::class)
class PanelWindowInstrumentedTest {
    @get:Rule val composeRule = createEmptyComposeRule()
    private var scenario: ActivityScenario<EdgePanelActivity>? = null
    private val testKey = "slivue-window-ime-regression-${System.nanoTime()}"
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private var originalAccessibilityFlags = 0
    private var accessibilityConfigured = false
    private var listenerWasEnabled = false
    private var permissionSetupStarted = false

    @Before fun observeSystemWindows() {
        val context = instrumentation.targetContext
        listenerWasEnabled = PrivacyConsent.hasNotificationAccess(context)
        permissionSetupStarted = true
        if (!listenerWasEnabled) TestNotificationAccess.setEnabled(true)
        assertTrue("테스트 앱의 정확한 알림 리스너 접근 권한이 필요합니다.", PrivacyConsent.hasNotificationAccess(context))
        instrumentation.runOnMainSync {
            assertTrue("테스트 설치의 알림 고지 동의를 저장하지 못했습니다.", PrivacyConsent.getInstance(context).acceptDisclosure())
        }
        assertTrue("동의와 시스템 권한을 모두 허용한 테스트 상태여야 합니다.", PrivacyConsent.canProcessNotifications(context))

        val automation = instrumentation.uiAutomation
        val info = automation.serviceInfo
        originalAccessibilityFlags = info.flags
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = info
        accessibilityConfigured = true
    }

    @After fun tearDown() {
        try {
            scenario?.close()
        } finally {
            try {
                instrumentation.runOnMainSync {
                    // Slivue 메모리만 비운다. 다른 앱의 원본 알림 해제는 수행하지 않는다.
                    PrivacyConsent.getInstance(instrumentation.targetContext).revokeDisclosure()
                    NotificationRepository.clearMemoryOnly()
                }
            } finally {
                try {
                    if (permissionSetupStarted && !listenerWasEnabled) TestNotificationAccess.setEnabled(false)
                } finally {
                    if (accessibilityConfigured) {
                        val automation = instrumentation.uiAutomation
                        automation.serviceInfo = automation.serviceInfo.apply { flags = originalAccessibilityFlags }
                    }
                }
            }
        }
    }

    @Test fun panelFillsTheVisibleSafeAreaAndKeepsItsHeaderOnOneLine() {
        launchPanel()
        composeRule.waitForIdle()
        scenario!!.onActivity { activity ->
            val decor = activity.window.decorView
            val location = IntArray(2)
            decor.getLocationOnScreen(location)
            val visibleArea = Rect()
            decor.getWindowVisibleDisplayFrame(visibleArea)
            assertEquals(visibleArea.left, location[0])
            assertEquals(visibleArea.width(), decor.width)
            assertTrue(location[1] >= visibleArea.top)
            assertTrue(location[1] + decor.height <= visibleArea.bottom)
        }
        val context = instrumentation.targetContext
        val title = composeRule.onNodeWithText(context.getString(R.string.panel_title), useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val close = composeRule.onNodeWithContentDescription(context.getString(R.string.panel_close))
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(title.center.y, close.center.y, 1f)
    }

    @Test fun syntheticReplyCanOpenImeEnterTextAndHideImeWithoutClosingPanel() {
        instrumentation.runOnMainSync {
            // 전송 대상이 없는 합성 데이터만 사용한다. 실제 메시지 전송은 수행하지 않는다.
            NotificationRepository.addOrUpdateNotification(EdgeNotification(
                key = testKey, id = 99123, packageName = instrumentation.targetContext.packageName,
                appName = "Slivue regression", title = "Keyboard regression", text = "Synthetic only",
                actions = listOf(NotificationActionItem("Reply", null, isReply = true, remoteInputKey = "test"))
            ))
        }
        launchPanel()
        val context = instrumentation.targetContext
        composeRule.onNodeWithText(context.getString(R.string.panel_reply)).performScrollTo().performClick()
        composeRule.waitUntil(10_000) { imeVisible() }
        composeRule.onNode(hasSetTextAction()).assertIsDisplayed().performTextInput("Slivue keyboard test")
        composeRule.onNodeWithText("Slivue keyboard test").assertIsDisplayed()
        instrumentation.uiAutomation.executeShellCommand("input keyevent 4").use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
        }
        composeRule.waitUntil(10_000) { !imeVisible() }
        scenario!!.onActivity { assertFalse(it.isFinishing) }
    }

    private fun launchPanel() {
        scenario = ActivityScenario.launch(Intent(instrumentation.targetContext, EdgePanelActivity::class.java))
    }

    private fun imeVisible(): Boolean {
        // 구형 OS의 크기가 조절된 투명 창에서는 호환 Insets 추정값 대신 실제 키보드 창을 확인한다.
        return instrumentation.uiAutomation.windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
    }
}
