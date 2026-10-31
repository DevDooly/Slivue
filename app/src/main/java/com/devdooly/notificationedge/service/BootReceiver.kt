package com.devdooly.notificationedge.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.devdooly.notificationedge.data.repository.SettingsRepository
import com.devdooly.notificationedge.util.AppLog
import com.devdooly.notificationedge.util.PrivacyConsent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver {
    // Android Manifest용 기본 생성자
    constructor() : super()

    override fun onReceive(context: Context, intent: Intent) {
        if (!PrivacyConsent.canProcessNotifications(context)) return
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // 오버레이 권한이 허용되어 있는지 확인
            if (Settings.canDrawOverlays(context)) {
                val pendingResult = goAsync()
                val repository = SettingsRepository.getInstance(context)
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val settings = repository.settingsFlow.first()
                        if (settings.isServiceEnabled || settings.isEdgeLightingEnabled) {
                            OverlayServiceStarter.start(context)
                        }
                    } catch (e: Exception) {
                        AppLog.warning("BootReceiver", "부팅 후 서비스 복구 실패", e)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
