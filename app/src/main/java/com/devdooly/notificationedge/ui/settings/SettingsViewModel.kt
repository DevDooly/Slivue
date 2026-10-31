package com.devdooly.notificationedge.ui.settings

import android.app.Application
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devdooly.notificationedge.R
import com.devdooly.notificationedge.data.model.AppSettings
import com.devdooly.notificationedge.data.model.EdgeNotification
import com.devdooly.notificationedge.data.model.EdgeSide
import com.devdooly.notificationedge.data.repository.NotificationRepository
import com.devdooly.notificationedge.data.repository.SettingsRepository
import com.devdooly.notificationedge.service.OverlayServiceStarter
import com.devdooly.notificationedge.util.PrivacyConsent
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 설정 화면의 영속 상태 변경과 앱 서비스 제어를 담당한다.
 *
 * Compose 화면은 상태를 표시하고 시스템 권한 화면을 여는 일만 맡는다.
 */
class SettingsViewModel internal constructor(
    application: Application,
    private val settingsRepository: SettingsRepository
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application,
        SettingsRepository.getInstance(application)
    )

    val settings: StateFlow<AppSettings> = settingsRepository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = AppSettings()
    )

    private val privacyConsent = PrivacyConsent.getInstance(application)
    val privacyState = privacyConsent.state

    fun acceptNotificationDisclosure(): Boolean = privacyConsent.acceptDisclosure()

    fun revokeNotificationDisclosure() = privacyConsent.revokeDisclosure()

    fun pauseNotificationProcessing() = privacyConsent.pauseProcessing()

    fun resumeNotificationProcessing(): Boolean {
        val resumed = privacyConsent.resumeProcessing()
        if (resumed) startOverlayServiceIfNeeded(settings.value.isServiceEnabled || settings.value.isEdgeLightingEnabled)
        return resumed
    }

    fun reconcileNotificationAccess() = privacyConsent.reconcileNotificationAccess()

    fun updateServiceEnabled(enabled: Boolean) = updateSettings {
        settingsRepository.updateServiceEnabled(enabled)
        startOverlayServiceIfNeeded(enabled || settings.value.isEdgeLightingEnabled)
    }

    fun updateEdgeSide(side: EdgeSide) = updateSettings {
        settingsRepository.updateEdgeSide(side)
    }

    fun updateHandlePositionRatio(ratio: Float) = updateSettings {
        settingsRepository.updateHandlePositionRatio(ratio)
    }

    fun updateHandleWidthDp(width: Int) = updateSettings {
        settingsRepository.updateHandleWidthDp(width)
    }

    fun updateHandleHeightDp(height: Int) = updateSettings {
        settingsRepository.updateHandleHeightDp(height)
    }

    fun updatePanelWidthDp(width: Int) = updateSettings {
        settingsRepository.updatePanelWidthDp(width)
    }

    fun updateAutoDismissOnOpen(enabled: Boolean) = updateSettings {
        settingsRepository.updateAutoDismissOnOpen(enabled)
    }

    fun updateHandleColor(color: Long) = updateSettings {
        settingsRepository.updateHandleColor(color)
    }

    fun updateHandleAlpha(alpha: Float) = updateSettings {
        settingsRepository.updateHandleAlpha(alpha)
    }

    fun updateHandleVisible(visible: Boolean) = updateSettings {
        settingsRepository.updateHandleVisible(visible)
    }

    fun updateLaunchDirectToPanel(enabled: Boolean) = updateSettings {
        settingsRepository.updateLaunchDirectToPanel(enabled)
    }

    fun updateExternalControlEnabled(enabled: Boolean) = updateSettings {
        settingsRepository.updateExternalControlEnabled(enabled)
    }

    fun updateEdgeLightingEnabled(enabled: Boolean) = updateSettings {
        settingsRepository.updateEdgeLightingEnabled(enabled)
        startOverlayServiceIfNeeded(enabled || settings.value.isServiceEnabled)
    }

    fun updateEdgeLightingColor(color: Long) = updateSettings {
        settingsRepository.updateEdgeLightingColor(color)
    }

    fun updateEdgeLightingCornerRadiusDp(radius: Int) = updateSettings {
        settingsRepository.updateEdgeLightingCornerRadiusDp(radius)
    }

    fun updateSelectedFont(fontId: String) = updateSettings {
        settingsRepository.updateSelectedFont(fontId)
    }

    fun updatePauseMediaOnOpen(enabled: Boolean) = updateSettings {
        settingsRepository.updatePauseMediaOnOpen(enabled)
    }

    fun updateHapticEnabled(enabled: Boolean) = updateSettings {
        settingsRepository.updateHapticEnabled(enabled)
    }

    fun setPackageExcluded(packageName: String, excluded: Boolean) = updateSettings {
        settingsRepository.setPackageExcluded(packageName, excluded)
    }

    fun clearDiscoveredPackages() = updateSettings {
        settingsRepository.clearDiscoveredPackages()
    }

    fun addBlockedKeyword(keyword: String) = updateSettings {
        settingsRepository.addBlockedKeyword(keyword)
    }

    fun removeBlockedKeyword(keyword: String) = updateSettings {
        settingsRepository.removeBlockedKeyword(keyword)
    }

    fun updateDiagnosticModeEnabled(enabled: Boolean) = updateSettings {
        settingsRepository.updateDiagnosticModeEnabled(enabled)
        if (!enabled) NotificationRepository.clearDiagnosticDumps()
    }

    fun emitTestNotification() {
        val application = getApplication<Application>()
        NotificationRepository.addOrUpdateNotification(
            EdgeNotification(
                key = "test_notification_${System.currentTimeMillis()}",
                id = 999,
                packageName = application.packageName,
                appName = application.getString(R.string.test_notification_app),
                title = application.getString(R.string.test_notification_sender),
                text = application.getString(R.string.test_notification_text),
                timestamp = System.currentTimeMillis()
            )
        )
    }

    private fun updateSettings(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private fun startOverlayServiceIfNeeded(needed: Boolean) {
        val application = getApplication<Application>()
        if (needed && PrivacyConsent.canProcessNotifications(application) && Settings.canDrawOverlays(application)) {
            OverlayServiceStarter.start(application)
        }
    }

}
