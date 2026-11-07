package com.devdooly.notificationedge

import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import java.io.FileInputStream

/** 하드웨어 키보드가 있는 가상 기기에서도 실제 소프트키보드를 시험하고 원래 설정을 복원한다. */
internal class TestImeEnvironment {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private var originalShowWithHardwareKeyboard: String? = null
    private var settingCaptured = false

    fun prepare() {
        val manager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val selected = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        val selectedComponent = selected?.let(ComponentName::unflattenFromString)
        check(selectedComponent != null && manager.enabledInputMethodList.any {
            ComponentName.unflattenFromString(it.id) == selectedComponent
        }) {
            "실제 키보드 시험에 사용할 기본 입력기가 없습니다. sdk=${android.os.Build.VERSION.SDK_INT}, " +
                "selected=$selected, enabledCount=${manager.enabledInputMethodList.size}"
        }

        originalShowWithHardwareKeyboard = Settings.Secure.getString(context.contentResolver, SHOW_WITH_HARDWARE_KEYBOARD)
        check(originalShowWithHardwareKeyboard in setOf(null, "0", "1")) {
            "예상하지 못한 소프트키보드 설정값은 테스트에서 변경하지 않습니다."
        }
        // 명령 실행 뒤 검증이 실패해도 @After에서 원래 값으로 복원한다.
        settingCaptured = true
        writeShowWithHardwareKeyboard("1")
        // 일부 Android 16 입력기는 이 설정을 사용하지 않는다. 실제 IME 창 검증은 별도로 유지한다.
    }

    fun restore() {
        if (!settingCaptured) return
        writeShowWithHardwareKeyboard(originalShowWithHardwareKeyboard)
        settingCaptured = false
    }

    fun diagnosticSummary(): String {
        val configuration = context.resources.configuration
        val manager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        return "sdk=${android.os.Build.VERSION.SDK_INT}, " +
            "selectedIme=${Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)}, " +
            "enabledImeCount=${manager.enabledInputMethodList.size}, " +
            "inputMethodActive=${manager.isActive}, acceptingText=${manager.isAcceptingText}, " +
            "showWithHardware=${Settings.Secure.getString(context.contentResolver, SHOW_WITH_HARDWARE_KEYBOARD)}, " +
            "keyboard=${configuration.keyboard}, keyboardHidden=${configuration.keyboardHidden}, " +
            "hardKeyboardHidden=${configuration.hardKeyboardHidden}"
    }

    private fun writeShowWithHardwareKeyboard(value: String?) {
        val command = if (value == null) "settings delete secure $SHOW_WITH_HARDWARE_KEYBOARD"
        else "settings put secure $SHOW_WITH_HARDWARE_KEYBOARD $value"
        // UiAutomation는 인자를 직접 실행하므로 shell 따옴표를 넣지 않는다.
        val output = instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { it.bufferedReader().readText() }
        }
        val deadline = SystemClock.uptimeMillis() + 10_000L
        while (Settings.Secure.getString(context.contentResolver, SHOW_WITH_HARDWARE_KEYBOARD) != value &&
            SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            SystemClock.sleep(25L)
        }
        assertEquals("테스트 소프트키보드 설정 적용·복원 실패: $output", value,
            Settings.Secure.getString(context.contentResolver, SHOW_WITH_HARDWARE_KEYBOARD))
    }

    companion object {
        private const val SHOW_WITH_HARDWARE_KEYBOARD = "show_ime_with_hard_keyboard"
    }
}
