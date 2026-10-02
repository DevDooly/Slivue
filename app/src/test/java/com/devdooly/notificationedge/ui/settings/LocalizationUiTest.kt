package com.devdooly.notificationedge.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.devdooly.notificationedge.BuildConfig
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
class LocalizationUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun englishPermissionCard_displaysEnglishAndInvokesGrantAction() {
        var overlayRequests = 0
        showCard {
            PermissionStatusCard(
                hasOverlay = false,
                hasNotification = true,
                hasBatteryOpt = true,
                onGrantOverlay = { overlayRequests++ },
                onGrantNotification = {},
                onGrantBattery = {}
            )
        }

        composeRule.onNodeWithText("Required permissions").assertIsDisplayed()
        composeRule.onNodeWithText("Permissions needed").assertIsDisplayed()
        composeRule.onNodeWithText("Display over other apps").assertIsDisplayed()
        composeRule.onNodeWithText("알림 접근 권한").assertDoesNotExist()
        composeRule.onNodeWithText("Grant").performClick()
        assertEquals(1, overlayRequests)
    }

    @Test
    @Config(qualifiers = "ko-rKR-w360dp-h800dp")
    fun koreanPermissionCard_preservesKoreanAndExpandsOnClick() {
        showCard {
            PermissionStatusCard(
                hasOverlay = true,
                hasNotification = true,
                hasBatteryOpt = true,
                onGrantOverlay = {},
                onGrantNotification = {},
                onGrantBattery = {}
            )
        }

        composeRule.onNodeWithText("필수 권한 설정").assertIsDisplayed()
        composeRule.onNodeWithText("모두 허용됨").assertIsDisplayed()
        composeRule.onNodeWithText("알림 접근 권한").assertDoesNotExist()
        composeRule.onNodeWithText("필수 권한 설정").performClick()
        composeRule.onNodeWithText("알림 접근 권한").assertIsDisplayed()
        composeRule.onNodeWithText("Notification access").assertDoesNotExist()
    }

    @Test
    fun englishMasterSwitch_displaysEnglishAndRemainsInteractive() {
        var selectedValue: Boolean? = null
        showCard {
            MasterSwitchCard(enabled = true, onCheckedChange = { selectedValue = it })
        }

        composeRule.onNodeWithText("Screen edge handle").assertIsDisplayed()
        composeRule.onNodeWithText("Swipe from the screen edge to open the panel").assertIsDisplayed()
        composeRule.onNodeWithText("화면 가장자리 엣지 핸들").assertDoesNotExist()
        composeRule.onNode(isToggleable()).performClick()
        assertEquals(false, selectedValue)
    }

    @Test
    @Config(qualifiers = "ko-rKR-w360dp-h800dp")
    fun koreanMasterSwitch_preservesKoreanDisabledExplanation() {
        showCard { MasterSwitchCard(enabled = false, onCheckedChange = {}) }

        composeRule.onNodeWithText("화면 가장자리 엣지 핸들").assertIsDisplayed()
        composeRule.onNodeWithText("핸들 비활성화됨 (엣지 라이팅은 독립 작동)").assertIsDisplayed()
        composeRule.onNodeWithText("Screen edge handle").assertDoesNotExist()
    }

    @Test
    fun englishAppInfo_displaysEnglishAndPreservesApplicationId() {
        showCard { AppInfoCard() }

        composeRule.onNodeWithText("Slivue").assertIsDisplayed()
        composeRule.onNodeWithText("Stay on screen. Stay notified.").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Version ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE}) | " +
                "Target SDK ${composeRule.activity.applicationInfo.targetSdkVersion}"
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Package: com.devdooly.notificationedge").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "ko-rKR-w360dp-h800dp")
    fun koreanAppInfo_preservesKoreanAndApplicationId() {
        showCard { AppInfoCard() }

        composeRule.onNodeWithText("Slivue").assertIsDisplayed()
        composeRule.onNodeWithText("화면은 그대로, 알림은 바로.").assertIsDisplayed()
        composeRule.onNodeWithText(
            "버전 ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE}) | " +
                "Target SDK ${composeRule.activity.applicationInfo.targetSdkVersion}"
        ).assertIsDisplayed()
        composeRule.onNodeWithText("패키지: com.devdooly.notificationedge").assertIsDisplayed()
    }

    @Test
    fun englishFilterCard_expandsAndSubmitsKeyword() {
        var addedKeyword: String? = null
        showCard { EmptyFilterCard(onAddKeyword = { addedKeyword = it }) }

        composeRule.onNodeWithText("Notification filters").assertIsDisplayed()
        composeRule.onNodeWithText("Apps: 0 · Blocked keywords: 0").assertIsDisplayed()
        composeRule.onNodeWithText("Blocked keywords (0)").assertDoesNotExist()
        composeRule.onNodeWithText("Notification filters").performClick()
        composeRule.onNodeWithText("Blocked keywords (0)").assertIsDisplayed()
        composeRule.onNodeWithText("No blocked keywords added.").assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).performTextInput("spam")
        composeRule.onNodeWithText("Add").performClick()
        assertEquals("spam", addedKeyword)
    }

    @Test
    @Config(qualifiers = "ko-rKR-w360dp-h800dp")
    fun koreanFilterCard_expandsAndPreservesKoreanLabels() {
        showCard { EmptyFilterCard() }

        composeRule.onNodeWithText("알림 필터").assertIsDisplayed()
        composeRule.onNodeWithText("수신 앱 0개 · 차단 키워드 0개").assertIsDisplayed()
        composeRule.onNodeWithText("알림 필터").performClick()
        composeRule.onNodeWithText("차단 키워드 (0개)").assertIsDisplayed()
        composeRule.onNodeWithText("등록된 차단 키워드가 없습니다.").assertIsDisplayed()
        composeRule.onNodeWithText("추가").assertIsDisplayed()
        composeRule.onNodeWithText("Add").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h1000dp")
    fun englishNarrowCards_withLargeFont_keepFullTextLayout() {
        showCard {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.5f)) {
                Box(modifier = Modifier.width(320.dp)) {
                    Column {
                        MasterSwitchCard(enabled = false, onCheckedChange = {})
                        PermissionStatusCard(
                            hasOverlay = true,
                            hasNotification = true,
                            hasBatteryOpt = false,
                            onGrantOverlay = {},
                            onGrantNotification = {},
                            onGrantBattery = {}
                        )
                    }
                }
            }
        }

        assertTextHasNoVisualOverflow("Screen edge handle")
        assertTextHasNoVisualOverflow("Handle disabled (edge lighting works independently)")
        assertTextHasNoVisualOverflow("Required permissions")
        assertTextHasNoVisualOverflow("Required permissions granted")
    }

    private fun showCard(content: @Composable () -> Unit) {
        composeRule.setContent { MaterialTheme(content = content) }
    }

    @Composable
    private fun EmptyFilterCard(onAddKeyword: (String) -> Unit = {}) {
        NotificationFilterSettingsCard(
            discoveredPackages = emptySet(),
            excludedPackages = emptySet(),
            blockedKeywords = emptySet(),
            onToggleExcludedPackage = { _, _ -> },
            onClearDiscoveredPackages = {},
            onAddBlockedKeyword = onAddKeyword,
            onRemoveBlockedKeyword = {}
        )
    }

    private fun assertTextHasNoVisualOverflow(text: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(text, useUnmergedTree = true)
            .assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action -> action(layouts) }
        assertTrue("텍스트 배치 결과가 있어야 합니다: $text", layouts.isNotEmpty())
        layouts.forEach { layout ->
            assertFalse(
                "텍스트가 잘리지 않아야 합니다: $text; size=${layout.size}, " +
                    "paragraph=${layout.multiParagraph.width}x${layout.multiParagraph.height}, " +
                    "widthOverflow=${layout.didOverflowWidth}, heightOverflow=${layout.didOverflowHeight}, " +
                    "lines=${layout.lineCount}, constraints=${layout.layoutInput.constraints}",
                layout.hasVisualOverflow
            )
        }
    }
}
