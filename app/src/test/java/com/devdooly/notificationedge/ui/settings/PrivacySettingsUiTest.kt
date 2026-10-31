package com.devdooly.notificationedge.ui.settings

import android.app.Application
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.devdooly.notificationedge.BuildConfig
import com.devdooly.notificationedge.R
import com.devdooly.notificationedge.data.model.AppSettings
import com.devdooly.notificationedge.data.repository.SettingsRepository
import com.devdooly.notificationedge.util.PrivacyConsent
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS-w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class PrivacySettingsUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val viewModelStore = ViewModelStore()
    private lateinit var application: Application

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        Settings.Secure.putString(application.contentResolver, "enabled_notification_listeners", "")
        PrivacyConsent.getInstance(application).revokeDisclosure()
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        Settings.Secure.putString(application.contentResolver, "enabled_notification_listeners", "")
        PrivacyConsent.getInstance(application).revokeDisclosure()
    }

    @Test
    fun later_invokesOnlyDeferralAndNeverAcceptsConsent() {
        val calls = mutableListOf<String>()
        showDisclosure(calls)

        assertEquals(emptyList<String>(), calls)
        composeRule.onNodeWithText("Later").assertIsDisplayed().performClick()

        assertEquals(listOf("later"), calls)
        assertFalse(PrivacyConsent.getInstance(application).state.value.hasCurrentConsent)
    }

    @Test
    fun readingPrivacyInformation_doesNotAcceptOrDismissTheDisclosure() {
        val calls = mutableListOf<String>()
        showDisclosure(calls)

        composeRule.onNodeWithText("Read privacy information").performScrollTo().performClick()

        assertEquals(listOf("policy"), calls)
        composeRule.onNodeWithText("Before allowing notification access").assertIsDisplayed()
        composeRule.onNodeWithText("Agree and open settings").assertIsDisplayed()
        assertFalse(PrivacyConsent.getInstance(application).state.value.hasCurrentConsent)
    }

    @Test
    fun consentCallback_isInvokedOnlyByTheExplicitAgreeButton() {
        val calls = mutableListOf<String>()
        showDisclosure(calls)
        val disclosureBody = application.getString(
            if (BuildConfig.IS_PLAY_DISTRIBUTION) R.string.privacy_disclosure_play
            else R.string.privacy_disclosure_direct
        )
        composeRule.onNodeWithText(disclosureBody).assertExists()
        assertEquals(emptyList<String>(), calls)

        composeRule.onNodeWithText("Agree and open settings").performClick()

        assertEquals(listOf("accept"), calls)
        // UI 콜백 검사이며, 실제 동의 저장·OS 권한 검사는 PrivacyConsentTest가 담당한다.
        assertFalse(PrivacyConsent.getInstance(application).state.value.hasCurrentConsent)
    }

    @Test
    fun activePrivacyCard_routesStopRevokePolicyHistoryAndDataToTheirOwnCallbacks() {
        val calls = mutableListOf<String>()
        showPrivacyCard(hasConsent = true, isPaused = false, calls = calls)

        composeRule.onNodeWithText("Resume Slivue").assertDoesNotExist()
        composeRule.onNodeWithText("Review notification access").assertDoesNotExist()
        for ((label, action) in listOf(
            "Stop Slivue" to "stop",
            "Withdraw consent and stop" to "revoke",
            "Read privacy information" to "policy",
            "Clear Slivue notification history" to "history",
            "Manage or delete app data" to "data"
        )) {
            composeRule.onNodeWithText(label).performScrollTo().performClick()
            assertEquals(action, calls.last())
        }

        assertEquals(listOf("stop", "revoke", "policy", "history", "data"), calls)
    }

    @Test
    fun stoppedPrivacyCard_offersResumeInsteadOfStopOrImplicitConsent() {
        val calls = mutableListOf<String>()
        showPrivacyCard(hasConsent = true, isPaused = true, calls = calls)

        composeRule.onNodeWithText("Slivue is stopped. Notification processing and overlays are paused.").assertIsDisplayed()
        composeRule.onNodeWithText("Stop Slivue").assertDoesNotExist()
        composeRule.onNodeWithText("Review notification access").assertDoesNotExist()
        assertEquals(emptyList<String>(), calls)
        composeRule.onNodeWithText("Resume Slivue").performClick()

        assertEquals(listOf("resume"), calls)
    }

    @Test
    fun withoutConsent_reviewIsExplicitAndPreparationWarningIsNotSubmissionApproval() {
        val calls = mutableListOf<String>()
        showPrivacyCard(hasConsent = false, isPaused = false, calls = calls, showPreparation = true)

        composeRule.onNodeWithText("Stop Slivue").assertDoesNotExist()
        composeRule.onNodeWithText("Resume Slivue").assertDoesNotExist()
        composeRule.onNodeWithText("Withdraw consent and stop").assertDoesNotExist()
        assertEquals(emptyList<String>(), calls)
        composeRule.onNodeWithText("Review notification access").performClick()
        assertEquals(listOf("review"), calls)
        composeRule.onNodeWithText("Play preparation build — not ready for submission").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(application.getString(R.string.play_preparation_description))
            .performScrollTo().assertIsDisplayed()
        assertFalse(PrivacyConsent.getInstance(application).state.value.hasCurrentConsent)
    }

    @Test
    @Config(qualifiers = "ko-rKR-w360dp-h800dp")
    fun koreanDisclosure_hasKoreanChoicesAndLaterDoesNotAccept() {
        val calls = mutableListOf<String>()
        showDisclosure(calls)

        composeRule.onNodeWithText("알림 접근 허용 전에 확인해 주세요").assertIsDisplayed()
        composeRule.onNodeWithText("동의하고 설정 열기").assertIsDisplayed()
        composeRule.onNodeWithText("Agree and open settings").assertDoesNotExist()
        composeRule.onNodeWithText("나중에").performClick()

        assertEquals(listOf("later"), calls)
    }

    @Test
    @Config(qualifiers = "ko-rKR-w360dp-h800dp")
    fun koreanPrivacyCard_andPreparationWarningRemainLocalized() {
        val calls = mutableListOf<String>()
        showPrivacyCard(hasConsent = true, isPaused = true, calls = calls, showPreparation = true)

        composeRule.onNodeWithText("개인정보와 알림 접근").assertIsDisplayed()
        composeRule.onNodeWithText("Slivue 다시 시작").performClick()
        assertEquals(listOf("resume"), calls)
        composeRule.onNodeWithText("Privacy and notification access").assertDoesNotExist()
        composeRule.onNodeWithText("Play 등록 준비 빌드 — 제출 불가").performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h1000dp")
    fun narrowPrivacyCard_atDoubleFontKeepsButtonsReadableAndTouchTargetsLargeEnough() {
        val calls = mutableListOf<String>()
        composeRule.setContent {
            MaterialTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                    Box(Modifier.width(320.dp)) {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            TestPrivacyCard(hasConsent = true, isPaused = false, calls = calls)
                        }
                    }
                }
            }
        }

        for (label in listOf(
            "Stop Slivue", "Withdraw consent and stop", "Read privacy information",
            "Clear Slivue notification history", "Manage or delete app data"
        )) {
            val button = composeRule.onNode(hasText(label) and hasClickAction()).performScrollTo().assertIsDisplayed()
            val bounds = button.fetchSemanticsNode().boundsInRoot
            assertTrue("큰 글씨에서도 최소 48dp 터치 영역이어야 합니다: $label", bounds.height >= 48f * composeRule.density.density - 1f)
            assertTextFitsMeasuredBounds(label)
        }
        composeRule.onNodeWithText("Manage or delete app data").performClick()
        assertEquals(listOf("data"), calls)
    }

    @Test
    fun settingsScreen_doesNotOpenSystemAccessSettingsBeforeExplicitConsent() {
        val repository = mockk<SettingsRepository>()
        every { repository.settingsFlow } returns MutableStateFlow(AppSettings())
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SettingsViewModel(application, repository) as T
        }
        val viewModel = ViewModelProvider(viewModelStore, factory)[SettingsViewModel::class.java]
        composeRule.setContent { MaterialTheme { SettingsScreen(viewModel) } }

        composeRule.onNodeWithText("Before allowing notification access").assertDoesNotExist()
        assertFalse(PrivacyConsent.getInstance(application).state.value.hasCurrentConsent)
        assertNull(shadowOf(composeRule.activity).nextStartedActivity)
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Review notification access"))
        composeRule.onNodeWithText("Review notification access").assertIsDisplayed().performClick()

        composeRule.onNodeWithText("Before allowing notification access").assertIsDisplayed()
        assertNull(shadowOf(composeRule.activity).nextStartedActivity)
        composeRule.onNodeWithText("Later").performClick()

        composeRule.onNodeWithText("Before allowing notification access").assertDoesNotExist()
        assertFalse(PrivacyConsent.getInstance(application).state.value.hasCurrentConsent)
        assertNull(shadowOf(composeRule.activity).nextStartedActivity)
    }

    private fun showDisclosure(calls: MutableList<String>) {
        composeRule.setContent {
            MaterialTheme {
                NotificationDisclosureDialog(
                    onAccept = { calls += "accept" },
                    onLater = { calls += "later" },
                    onReadPolicy = { calls += "policy" }
                )
            }
        }
    }

    private fun showPrivacyCard(
        hasConsent: Boolean,
        isPaused: Boolean,
        calls: MutableList<String>,
        showPreparation: Boolean = false
    ) {
        composeRule.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TestPrivacyCard(hasConsent, isPaused, calls)
                    if (showPreparation) PlayPreparationCard()
                }
            }
        }
    }

    @Composable
    private fun TestPrivacyCard(hasConsent: Boolean, isPaused: Boolean, calls: MutableList<String>) {
        PrivacySettingsCard(
            hasConsent = hasConsent,
            isPaused = isPaused,
            onReviewAccess = { calls += "review" },
            onStop = { calls += "stop" },
            onResume = { calls += "resume" },
            onRevoke = { calls += "revoke" },
            onReadPolicy = { calls += "policy" },
            onClearHistory = { calls += "history" },
            onManageData = { calls += "data" }
        )
    }

    private fun assertTextFitsMeasuredBounds(text: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action -> action(layouts) }
        assertTrue("텍스트 배치 결과가 있어야 합니다: $text", layouts.isNotEmpty())
        layouts.forEach { layout ->
            val diagnostic = "큰 글씨에서도 버튼 글자가 잘리지 않아야 합니다: $text, size=${layout.size}"
            assertFalse(diagnostic, layout.multiParagraph.didExceedMaxLines)
            for (line in 0 until layout.lineCount) {
                assertFalse(diagnostic, layout.isLineEllipsized(line))
                assertTrue(diagnostic, layout.getLineLeft(line) >= -1f)
                assertTrue(diagnostic, layout.getLineRight(line) <= layout.size.width + 1f)
                assertTrue(diagnostic, layout.getLineTop(line) >= -1f)
                assertTrue(diagnostic, layout.getLineBottom(line) <= layout.size.height + 1f)
            }
        }
    }
}
