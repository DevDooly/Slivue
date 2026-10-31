package com.devdooly.notificationedge.ui.settings

import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.devdooly.notificationedge.R
import com.devdooly.notificationedge.data.model.AppSettings
import com.devdooly.notificationedge.data.repository.SettingsRepository
import com.devdooly.notificationedge.service.OverlayServiceStarter
import com.devdooly.notificationedge.util.PrivacyConsent
import com.devdooly.notificationedge.ui.theme.NotificationEdgeTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Slivue 설정 화면 전용 액티비티.
 * 시스템 권한 화면이나 파일 선택기로 이동할 때는 종료하지 않고 복귀할 화면을 유지한다.
 * 일시적으로 가려지는 것과 사용자의 명시적인 뒤로가기 종료를 구분한다.
 */
class SettingsActivity : ComponentActivity() {

    private lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsRepository = SettingsRepository.getInstance(applicationContext)

        setTheme(R.style.Theme_NotificationEdge)
        enableEdgeToEdge()

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
                com.devdooly.notificationedge.util.ActivityUtils.overridePendingTransitionNoAnim(this@SettingsActivity)
            }
        })

        setContent {
            val liveSettings by settingsRepository.settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())
            NotificationEdgeTheme(
                fontId = liveSettings.selectedFont
            ) {
                SettingsScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        PrivacyConsent.getInstance(this).reconcileNotificationAccess()
        checkAndStartService()
    }

    private fun checkAndStartService() {
        if (Settings.canDrawOverlays(this) && PrivacyConsent.canProcessNotifications(this)) {
            lifecycleScope.launch {
                val settings = settingsRepository.settingsFlow.first()
                if (settings.isServiceEnabled || settings.isEdgeLightingEnabled) {
                    OverlayServiceStarter.start(this@SettingsActivity)
                }
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_SETTINGS = "extra_open_settings"
    }
}
