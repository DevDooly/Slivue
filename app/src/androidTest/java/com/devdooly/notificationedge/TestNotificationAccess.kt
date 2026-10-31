package com.devdooly.notificationedge

import android.content.ComponentName
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.test.platform.app.InstrumentationRegistry
import com.devdooly.notificationedge.service.NotificationListener
import com.devdooly.notificationedge.util.PrivacyConsent
import org.junit.Assert.assertEquals
import java.io.FileInputStream

/** 테스트 앱의 정확한 리스너 한 개만 변경한다. 실제 배포 코드에서는 사용하지 않는다. */
internal object TestNotificationAccess {
    private const val SETTING = "enabled_notification_listeners"

    fun setEnabled(enabled: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val component = ComponentName(context, NotificationListener::class.java)
        val resolver = context.contentResolver
        val before = entries(Settings.Secure.getString(resolver, SETTING))
        val otherComponents = before.filterNot { ComponentName.unflattenFromString(it) == component }
        val command = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O) {
            // API 26에는 notification shell 명령 구현이 없으므로 다른 항목을 그대로 보존한다.
            val target = if (enabled) {
                if (before.any { ComponentName.unflattenFromString(it) == component }) before
                else before + component.flattenToString()
            } else otherComponents
            val value = target.joinToString(":")
            // executeShellCommand는 인자를 직접 실행하므로 shell 따옴표를 넣지 않는다.
            require(value.none(Char::isWhitespace)) { "비정상적인 리스너 목록은 테스트에서 변경하지 않습니다." }
            if (value.isEmpty()) "settings delete secure $SETTING"
            else "settings put secure $SETTING $value"
        } else {
            val action = if (enabled) "allow_listener" else "disallow_listener"
            "cmd notification $action ${component.flattenToString()}"
        }
        val output = instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { it.bufferedReader().readText() }
        }
        val deadline = SystemClock.uptimeMillis() + 10_000L
        while (PrivacyConsent.hasNotificationAccess(context) != enabled && SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            SystemClock.sleep(25L)
        }
        assertEquals("정확한 테스트 리스너 권한 변경 실패: $output", enabled, PrivacyConsent.hasNotificationAccess(context))
        val afterOthers = entries(Settings.Secure.getString(resolver, SETTING))
            .filterNot { ComponentName.unflattenFromString(it) == component }
        assertEquals("다른 앱 리스너 설정은 변경하면 안 됩니다.", otherComponents, afterOthers)
    }

    private fun entries(raw: String?): List<String> = if (raw.isNullOrEmpty()) emptyList() else raw.split(':')
}
