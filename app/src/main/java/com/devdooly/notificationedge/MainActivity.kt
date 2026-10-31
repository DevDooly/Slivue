package com.devdooly.notificationedge

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import com.devdooly.notificationedge.data.repository.SettingsRepository
import com.devdooly.notificationedge.ui.overlay.EdgePanelLauncher
import com.devdooly.notificationedge.ui.settings.SettingsActivity
import com.devdooly.notificationedge.ui.shortcuts.PanelShortcuts
import com.devdooly.notificationedge.util.PrivacyConsent

/**
 * 런처 아이콘 / Good Lock / 제스처 실행 시 엣지 패널을 즉시 토글하거나 설정창으로 분기하는 트램펄린 액티비티
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PanelShortcuts.publish(this)

        val settingsRepository = SettingsRepository.getInstance(applicationContext)
        val openSettings = intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false)

        if (!openSettings && PrivacyConsent.canProcessNotifications(this) &&
            Settings.canDrawOverlays(this) && settingsRepository.isLaunchDirectToPanelSync()) {
            EdgePanelLauncher.toggle(this)
        } else {
            val settingsIntent = Intent(this, SettingsActivity::class.java).apply {
                if (openSettings) putExtra(SettingsActivity.EXTRA_OPEN_SETTINGS, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            }
            startActivity(settingsIntent)
        }

        finish()
        com.devdooly.notificationedge.util.ActivityUtils.overridePendingTransitionNoAnim(this)
    }

    companion object {
        const val EXTRA_OPEN_SETTINGS = "extra_open_settings"
    }
}
