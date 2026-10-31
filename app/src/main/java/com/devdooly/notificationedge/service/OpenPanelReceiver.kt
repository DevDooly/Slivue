package com.devdooly.notificationedge.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.devdooly.notificationedge.data.repository.SettingsRepository
import com.devdooly.notificationedge.util.PrivacyConsent

/**
 * Good Lock(One Hand Operation +), Tasker, 숏컷 등에서
 * 서비스의 공통 실행 경로를 통해 엣지 패널을 열기 위한 브로드캐스트 리시버.
 * 패널은 투명 Activity이며, 설정에 따라 실행 전 영상 일시정지를 요청한다.
 */
class OpenPanelReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!PrivacyConsent.canProcessNotifications(context)) return
        val repository = SettingsRepository.getInstance(context)
        if (!repository.isExternalControlEnabledSync()) return
        if (!Settings.canDrawOverlays(context)) return

        val serviceAction = when (intent.action) {
            ACTION_OPEN_PANEL -> EdgeOverlayService.ACTION_OPEN_PANEL
            ACTION_CLOSE_PANEL -> EdgeOverlayService.ACTION_CLOSE_PANEL
            ACTION_TOGGLE_PANEL -> EdgeOverlayService.ACTION_TOGGLE_PANEL
            else -> return
        }
        OverlayServiceStarter.start(context, serviceAction)
    }

    companion object {
        const val ACTION_OPEN_PANEL = "com.devdooly.notificationedge.OPEN_PANEL"
        const val ACTION_CLOSE_PANEL = "com.devdooly.notificationedge.CLOSE_PANEL"
        const val ACTION_TOGGLE_PANEL = "com.devdooly.notificationedge.TOGGLE_PANEL"
    }
}
