package com.devdooly.notificationedge.ui.overlay

import android.content.Context
import android.content.Intent
import com.devdooly.notificationedge.data.repository.SettingsRepository
import com.devdooly.notificationedge.util.MediaControlHelper
import com.devdooly.notificationedge.util.AppLog
import com.devdooly.notificationedge.util.PrivacyConsent
import com.devdooly.notificationedge.ui.settings.SettingsActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first

/** 패널 Activity의 열기·닫기·토글 정책을 모든 진입점에서 공유한다. */
object EdgePanelLauncher {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val gate = PanelLaunchGate(scope)

    fun open(context: Context) {
        if (!PrivacyConsent.canProcessNotifications(context)) {
            context.startActivity(Intent(context, SettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            })
            return
        }
        if (EdgePanelActivity.isInstanceActive) return
        // 진입용 무화면 Activity는 바로 종료하므로 Application만 보관한다.
        val appContext = context.applicationContext
        gate.open(
            prepare = {
                try {
                    val settings = SettingsRepository.getInstance(appContext).settingsFlow.first()
                    if (settings.pauseMediaOnOpen) MediaControlHelper.pauseVideoBeforePanel(appContext)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    AppLog.warning("EdgePanelLauncher", "패널 실행 준비 실패", error)
                }
            },
            launch = {
                if (PrivacyConsent.canProcessNotifications(appContext) && !EdgePanelActivity.isInstanceActive) {
                    try {
                        appContext.startActivity(createIntent(appContext))
                    } catch (error: Exception) {
                        AppLog.warning("EdgePanelLauncher", "패널 실행 실패", error)
                    }
                }
            }
        )
    }

    internal fun createIntent(context: Context) = Intent(context, EdgePanelActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
    }

    fun close() {
        gate.cancel()
        EdgePanelActivity.closeActiveInstance()
    }

    fun toggle(context: Context) {
        if (EdgePanelActivity.isInstanceActive || gate.isPending) close() else open(context)
    }
}
