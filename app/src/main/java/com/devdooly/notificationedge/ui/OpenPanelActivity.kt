package com.devdooly.notificationedge.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.devdooly.notificationedge.ui.shortcuts.PanelShortcuts
import com.devdooly.notificationedge.R
import com.devdooly.notificationedge.ui.overlay.EdgePanelLauncher
import com.devdooly.notificationedge.ui.settings.SettingsActivity
import com.devdooly.notificationedge.util.PrivacyConsent

/**
 * Good Lock (One Hand Operation +), 엣지 패널 바로가기, 런처 숏컷 등에서
 * 설정창 없이 알림 엣지 패널을 즉시 열거나 토글하기 위한 투명 액티비티
 */
class OpenPanelActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PanelShortcuts.publish(this)

        if (intent.action == Intent.ACTION_CREATE_SHORTCUT) {
            // 등록 요청은 패널을 열지 않고 호출 앱에 실행 인텐트를 돌려준다.
            setResult(RESULT_OK, createShortcutResult(this))
            finish()
            return
        }

        if (Settings.canDrawOverlays(this) && PrivacyConsent.canProcessNotifications(this)) {
            EdgePanelLauncher.toggle(this)
        } else {
            Toast.makeText(this, getString(R.string.panel_permission_required), Toast.LENGTH_SHORT).show()
            val settingsIntent = Intent(this, SettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            }
            startActivity(settingsIntent)
        }

        finish()
        com.devdooly.notificationedge.util.ActivityUtils.overridePendingTransitionNoAnim(this)
    }

    companion object {
        internal fun createShortcutResult(context: Context): Intent {
            val shortcut = ShortcutInfoCompat.Builder(context, PanelShortcuts.OPEN_PANEL_ID)
                .setShortLabel(context.getString(R.string.open_panel_activity_label))
                .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                .setIntent(PanelShortcuts.createLaunchIntent(context))
                .build()
            return ShortcutManagerCompat.createShortcutResultIntent(context, shortcut)
        }
    }
}
