package com.devdooly.notificationedge.ui.settings

import android.app.Application
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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.devdooly.notificationedge.data.model.AppSettings
import com.devdooly.notificationedge.data.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS-w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val viewModelStore = ViewModelStore()

    @After
    fun tearDown() {
        viewModelStore.clear()
    }

    @Test
    fun settingsScreen_filterOpensInOneTapAndKeywordChangesReachTheRepository() {
        val savedSettings = MutableStateFlow(AppSettings())
        val repository = mockk<SettingsRepository>()
        every { repository.settingsFlow } returns savedSettings
        coEvery { repository.addBlockedKeyword(any()) } coAnswers {
            savedSettings.value = savedSettings.value.copy(
                blockedKeywords = savedSettings.value.blockedKeywords + firstArg<String>()
            )
        }
        coEvery { repository.removeBlockedKeyword(any()) } coAnswers {
            savedSettings.value = savedSettings.value.copy(
                blockedKeywords = savedSettings.value.blockedKeywords - firstArg<String>()
            )
        }
        val viewModel = createViewModel(repository)
        composeRule.setContent { MaterialTheme { SettingsScreen(viewModel) } }

        val settingsList = composeRule.onNode(hasScrollToIndexAction())
        settingsList.performScrollToNode(hasText("Notification filters"))
        composeRule.onNodeWithText("Notification filters").performClick()

        // 두 번째 접기/펼치기 헤더 없이 한 번의 선택으로 실제 입력 항목에 접근한다.
        settingsList.performScrollToNode(hasSetTextAction())
        composeRule.onNode(hasSetTextAction()).assertIsDisplayed().performTextInput("  spam  ")
        composeRule.onNodeWithText("Add").performClick()
        composeRule.onNodeWithContentDescription("Remove keyword: spam").performScrollTo().performClick()
        composeRule.waitForIdle()

        coVerify(exactly = 1) { repository.addBlockedKeyword("spam") }
        coVerify(exactly = 1) { repository.removeBlockedKeyword("spam") }
        assertEquals(emptySet<String>(), savedSettings.value.blockedKeywords)
    }

    @Test
    fun filterKeywordInput_disablesBlankSubmissionAndTrimsTheAddedKeyword() {
        val addedKeywords = mutableListOf<String>()
        showFilter(onAddKeyword = { addedKeywords += it })
        composeRule.onNodeWithText("Notification filters").performClick()

        val input = composeRule.onNode(hasSetTextAction()).performScrollTo()
        composeRule.onNodeWithText("Add").assertIsNotEnabled()
        input.performTextInput("   ")
        composeRule.onNodeWithText("Add").assertIsNotEnabled()
        assertEquals(emptyList<String>(), addedKeywords)

        input.performTextReplacement("  sale  ")
        composeRule.onNodeWithText("Add").performClick()

        assertEquals(listOf("sale"), addedKeywords)
        input.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        composeRule.onNodeWithText("Add").assertIsNotEnabled()
    }

    @Test
    fun filterAppRow_changesOnlyThatAppsExcludedState() {
        val changes = mutableListOf<Pair<String, Boolean>>()
        showFilter(
            discoveredPackages = setOf("com.example.chat", "com.example.mail"),
            excludedPackages = setOf("com.example.mail"),
            onToggleExcludedPackage = { packageName, excluded -> changes += packageName to excluded }
        )
        composeRule.onNodeWithText("Notification filters").performClick()

        composeRule.onNodeWithText("com.example.chat").performScrollTo().performClick()
        composeRule.onNodeWithText("com.example.mail").performScrollTo().performClick()

        assertEquals(
            listOf("com.example.chat" to true, "com.example.mail" to false),
            changes
        )
    }

    @Test
    fun filterExpansionAndUnsubmittedKeyword_surviveSavedInstanceStateRestore() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    FilterCard()
                }
            }
        }
        composeRule.onNodeWithText("Notification filters").performClick()
        composeRule.onNode(hasSetTextAction()).performScrollTo().performTextInput("draft keyword")

        restoration.emulateSavedInstanceStateRestore()

        composeRule.onNode(hasSetTextAction()).performScrollTo().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("draft keyword"))
        )
        composeRule.onNodeWithText("Notification filters").performScrollTo().performClick()
        composeRule.onNode(hasSetTextAction()).assertDoesNotExist()
        composeRule.onNodeWithText("Notification filters").performClick()
        composeRule.onNode(hasSetTextAction()).performScrollTo().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("draft keyword"))
        )
    }

    @Test
    fun settingsScreen_fontPresetsAreSelectableAfterOneExpansion() {
        val savedSettings = MutableStateFlow(AppSettings())
        val repository = mockk<SettingsRepository>()
        every { repository.settingsFlow } returns savedSettings
        coEvery { repository.updateSelectedFont(any()) } coAnswers {
            savedSettings.value = savedSettings.value.copy(selectedFont = firstArg())
        }
        val viewModel = createViewModel(repository)
        composeRule.setContent { MaterialTheme { SettingsScreen(viewModel) } }

        val settingsList = composeRule.onNode(hasScrollToIndexAction())
        settingsList.performScrollToNode(hasText("Font"))
        composeRule.onNodeWithText("Font").performClick()
        settingsList.performScrollToNode(hasText("Serif"))
        composeRule.onNodeWithText("Serif").performScrollTo().performClick()
        settingsList.performScrollToNode(hasText("Current: Serif"))
        composeRule.onNodeWithText("Current: Serif").performScrollTo().assertIsDisplayed()

        coVerify(exactly = 1) { repository.updateSelectedFont("serif") }
        assertEquals("serif", savedSettings.value.selectedFont)
    }

    @Test
    fun behaviorSettingTitles_toggleOnlyTheirAssociatedSetting() {
        val pauses = mutableListOf<Boolean>()
        val haptics = mutableListOf<Boolean>()
        composeRule.setContent {
            MaterialTheme {
                BehaviorSettingsCard(
                    settings = AppSettings(pauseMediaOnOpen = true, hapticFeedbackEnabled = false),
                    onPauseMediaOnOpenChange = { pauses += it },
                    onHapticFeedbackChange = { haptics += it }
                )
            }
        }

        composeRule.onNodeWithText("Pause video before opening the panel").performClick()
        assertEquals(listOf(false), pauses)
        assertEquals(emptyList<Boolean>(), haptics)
        composeRule.onNodeWithText("Haptic feedback").performClick()
        assertEquals(listOf(false), pauses)
        assertEquals(listOf(true), haptics)
    }

    @Test
    fun narrowFilterAtLargeFont_keepsLabelsReadableAndLongKeywordDeleteAccessible() {
        val keyword = "Long notification keyword that must wrap without covering its remove button"
        val removedKeywords = mutableListOf<String>()
        composeRule.setContent {
            MaterialTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.5f)) {
                    Box(Modifier.width(320.dp)) {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            NotificationFilterSettingsCard(
                                discoveredPackages = emptySet(),
                                excludedPackages = emptySet(),
                                blockedKeywords = setOf(keyword),
                                onToggleExcludedPackage = { _, _ -> },
                                onClearDiscoveredPackages = {},
                                onAddBlockedKeyword = {},
                                onRemoveBlockedKeyword = { removedKeywords += it }
                            )
                        }
                    }
                }
            }
        }

        assertTextFitsMeasuredBounds("Notification filters")
        assertTextFitsMeasuredBounds("Apps: 0 · Blocked keywords: 1")
        composeRule.onNodeWithText("Notification filters").performClick()
        composeRule.onNode(hasSetTextAction()).performScrollTo()
        assertTextFitsMeasuredBounds("Keyword to block")

        val keywordNode = composeRule.onNodeWithText(keyword, useUnmergedTree = true).performScrollTo()
        assertTextFitsMeasuredBounds(keyword)
        val removeButton = composeRule.onNodeWithContentDescription("Remove keyword: $keyword")
            .assertIsDisplayed()
        assertFalse(
            "긴 키워드 본문과 삭제 버튼이 겹치지 않아야 합니다.",
            keywordNode.fetchSemanticsNode().boundsInRoot.overlaps(removeButton.fetchSemanticsNode().boundsInRoot)
        )
        removeButton.performClick()
        assertEquals(listOf(keyword), removedKeywords)
    }

    private fun assertTextFitsMeasuredBounds(text: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(text, useUnmergedTree = true)
            .assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action -> action(layouts) }
        assertTrue("텍스트 배치 결과가 있어야 합니다: $text", layouts.isNotEmpty())
        layouts.forEach { layout ->
            val diagnostic =
                "큰 글꼴에서도 텍스트가 잘리지 않아야 합니다: $text; size=${layout.size}, " +
                    "paragraph=${layout.multiParagraph.width}x${layout.multiParagraph.height}, " +
                    "widthOverflow=${layout.didOverflowWidth}, heightOverflow=${layout.didOverflowHeight}, " +
                    "lines=${layout.lineCount}, constraints=${layout.layoutInput.constraints}, " +
                    "lineBounds=${(0 until layout.lineCount).map { line -> "${layout.getLineLeft(line)},${layout.getLineTop(line)}..${layout.getLineRight(line)},${layout.getLineBottom(line)}" }}"
            // Native Robolectric은 문단의 빈 영역까지 maxWidth로 보고할 수 있다.
            // wrap-content 라벨의 실제 내용은 줄 경계와 말줄임 여부로 검증한다.
            assertFalse(diagnostic, layout.multiParagraph.didExceedMaxLines)
            for (line in 0 until layout.lineCount) {
                assertFalse(diagnostic, layout.isLineEllipsized(line))
                assertTrue(diagnostic, layout.getLineLeft(line) >= 0f)
                assertTrue(diagnostic, layout.getLineRight(line) <= layout.size.width.toFloat())
                assertTrue(diagnostic, layout.getLineTop(line) >= 0f)
                assertTrue(diagnostic, layout.getLineBottom(line) <= layout.size.height.toFloat())
            }
        }
    }

    private fun createViewModel(repository: SettingsRepository): SettingsViewModel {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SettingsViewModel(application, repository) as T
        }
        return ViewModelProvider(viewModelStore, factory)[SettingsViewModel::class.java]
    }

    private fun showFilter(
        discoveredPackages: Set<String> = emptySet(),
        excludedPackages: Set<String> = emptySet(),
        onAddKeyword: (String) -> Unit = {},
        onToggleExcludedPackage: (String, Boolean) -> Unit = { _, _ -> }
    ) {
        composeRule.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    FilterCard(discoveredPackages, excludedPackages, onAddKeyword, onToggleExcludedPackage)
                }
            }
        }
    }

    @Composable
    private fun FilterCard(
        discoveredPackages: Set<String> = emptySet(),
        excludedPackages: Set<String> = emptySet(),
        onAddKeyword: (String) -> Unit = {},
        onToggleExcludedPackage: (String, Boolean) -> Unit = { _, _ -> }
    ) {
        NotificationFilterSettingsCard(
            discoveredPackages = discoveredPackages,
            excludedPackages = excludedPackages,
            blockedKeywords = emptySet(),
            onToggleExcludedPackage = onToggleExcludedPackage,
            onClearDiscoveredPackages = {},
            onAddBlockedKeyword = onAddKeyword,
            onRemoveBlockedKeyword = {}
        )
    }
}
