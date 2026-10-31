package com.devdooly.notificationedge.ui.overlay

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devdooly.notificationedge.data.model.AppSettings
import com.devdooly.notificationedge.data.repository.SettingsRepository
import com.devdooly.notificationedge.ui.settings.SettingsActivity
import com.devdooly.notificationedge.ui.theme.NotificationEdgeTheme
import com.devdooly.notificationedge.util.PrivacyConsent

/**
 * 안드로이드 시스템 네비게이션 뒤로가기(하단 네비바 버튼, 양쪽 화면 제스처)를
 * 처리하며, 시스템 바를 제외한 안전 영역에서 아래 화면을 투명하게 보여 주는 호스트 액티비티.
 * 상태바와 내비게이션 바의 배경·아이콘 명암은 아래 화면의 표시를 유지한다.
 */
class EdgePanelActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!PrivacyConsent.canProcessNotifications(this)) {
            startActivity(Intent(this, SettingsActivity::class.java))
            closeAndFinish()
            return
        }
        configureTransparentPanelWindow(window)

        // OS 레벨의 뒤로가기 콜백 등록 (100% 보장)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                closeAndFinish()
            }
        })

        val settingsRepo = SettingsRepository.getInstance(applicationContext)

        setContent {
            val settings by settingsRepo.settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())

            NotificationEdgeTheme(
                fontId = settings.selectedFont,
                transparentStatusBar = true
            ) {
                EdgePanelContent(
                    edgeSide = settings.edgeSide,
                    panelWidthDp = settings.panelWidthDp,
                    autoDismissOnOpen = settings.autoDismissOnOpen,
                    onClose = { closeAndFinish() },
                    onOpenSettings = {
                        val intent = Intent(this@EdgePanelActivity, SettingsActivity::class.java).apply {
                            putExtra(SettingsActivity.EXTRA_OPEN_SETTINGS, true)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        startActivity(intent)
                        closeAndFinish()
                    },
                    onRequestFocus = { /* Activity 호스팅 환경에서는 OS가 포커스 자동 관리 */ }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!PrivacyConsent.canProcessNotifications(this)) {
            closeAndFinish()
            return
        }
        activeInstance = this
        isInstanceActive = true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 이미 열려 있는 상태에서 다시 호출되면 즉시 토글되어 닫힘
        closeAndFinish()
    }

    private fun closeAndFinish() {
        finish()
        com.devdooly.notificationedge.util.ActivityUtils.overridePendingTransitionNoAnim(this)
    }

    override fun onPause() {
        super.onPause()
        if (activeInstance == this) {
            isInstanceActive = false
        }
        if (!isFinishing) {
            closeAndFinish()
        } else {
            com.devdooly.notificationedge.util.ActivityUtils.overridePendingTransitionNoAnim(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (activeInstance == this) {
            activeInstance = null
            isInstanceActive = false
        }
    }

    companion object {
        @Volatile
        var isInstanceActive: Boolean = false
            private set

        private var activeInstance: EdgePanelActivity? = null

        /**
         * 현재 열려 있는 EdgePanelActivity 인스턴스가 있다면 즉시 닫고 true 반환
         */
        fun closeActiveInstance(): Boolean {
            val instance = activeInstance
            if (instance != null && !instance.isFinishing) {
                instance.closeAndFinish()
                return true
            }
            return false
        }
    }
}
