package com.devdooly.notificationedge.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import com.devdooly.notificationedge.data.repository.NotificationRepository
import com.devdooly.notificationedge.service.EdgeOverlayService
import com.devdooly.notificationedge.service.NotificationListener
import com.devdooly.notificationedge.ui.overlay.EdgePanelLauncher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Properties

data class PrivacyConsentState(
    val acceptedVersion: Int = 0,
    val acceptedAtEpochMs: Long = 0,
    val isPaused: Boolean = false,
    internal val observedSystemAccess: Boolean = false,
    internal val transitionGeneration: Long = 0
) {
    val hasCurrentConsent: Boolean
        get() = acceptedVersion == PrivacyConsent.CURRENT_DISCLOSURE_VERSION && acceptedAtEpochMs > 0

    /** 실제 OS 권한은 별도로 검사한다. 고지 동의를 권한 허용으로 표시하면 안 된다. */
    val isProcessingEnabled: Boolean
        get() = hasCurrentConsent && !isPaused
}

/**
 * 알림 고지 동의는 백업되지 않는 설치별 파일에 보관한다.
 * 설정·필터가 복원돼도 새 기기의 민감 알림 접근 동의를 대신하지 않는다.
 */
class PrivacyConsent internal constructor(private val context: Context) {
    private val storage = AtomicConsentStore(File(context.noBackupFilesDir, CONSENT_FILE))
    private val _state = MutableStateFlow(readState())
    val state: StateFlow<PrivacyConsentState> = _state.asStateFlow()

    @Volatile
    internal var accessGeneration: Long = 0L
        private set

    private var notificationAccessObserver: ContentObserver? = null

    /** Application의 설치별 싱글턴에서 한 번만 등록한다. 파일 재읽기용 인스턴스는 등록하지 않는다. */
    @Synchronized
    fun observeNotificationAccessChanges(): Boolean {
        if (notificationAccessObserver != null) return true
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                runCatching { reconcileNotificationAccess() }
                    .onFailure { AppLog.warning("PrivacyConsent", "알림 접근 권한 변경 반영 실패", it) }
            }
        }
        val registered = runCatching {
            context.contentResolver.registerContentObserver(
                Settings.Secure.getUriFor(NOTIFICATION_LISTENERS_SETTING), false, observer
            )
        }.onFailure { AppLog.warning("PrivacyConsent", "알림 접근 권한 감시 등록 실패; 화면·리스너 복귀 검사 유지", it) }
        if (registered.isFailure) return false
        notificationAccessObserver = observer
        runCatching { reconcileNotificationAccess() }
            .onFailure { AppLog.warning("PrivacyConsent", "시작 시 알림 접근 권한 확인 실패", it) }
        return true
    }

    @Synchronized
    fun acceptDisclosure(): Boolean {
        val next = PrivacyConsentState(
            acceptedVersion = CURRENT_DISCLOSURE_VERSION,
            acceptedAtEpochMs = System.currentTimeMillis(),
            observedSystemAccess = hasNotificationAccess(context)
        )
        if (!persist(next)) return false
        publish(next)
        requestListenerRebind()
        return true
    }

    @Synchronized
    fun revokeDisclosure() {
        // 저장 실패에도 현재 프로세스의 접근은 즉시 차단한다.
        val next = PrivacyConsentState()
        val saved = persist(next)
        publish(next)
        if (!saved) runCatching { storage.delete() }
        clearRuntimeDataAndStop()
    }

    @Synchronized
    fun pauseProcessing() {
        val next = _state.value.copy(isPaused = true)
        if (!persist(next)) {
            // 중지가 디스크에 남지 않으면 동의 자체를 제거해 재시작도 차단한다.
            publish(PrivacyConsentState())
            runCatching { storage.delete() }
        } else publish(next)
        clearRuntimeDataAndStop()
    }

    @Synchronized
    fun resumeProcessing(): Boolean {
        reconcileNotificationAccess()
        val current = _state.value
        if (!current.hasCurrentConsent || !hasNotificationAccess(context)) return false
        val next = current.copy(isPaused = false)
        if (!persist(next)) return false
        publish(next)
        requestListenerRebind()
        return true
    }

    /** 설정 복귀·리스너 연결/해제 시 호출해 시스템 권한 철회를 반영한다. */
    @Synchronized
    fun reconcileNotificationAccess() {
        val current = _state.value
        if (!current.hasCurrentConsent) return
        if (hasNotificationAccess(context)) {
            if (!current.observedSystemAccess) {
                val next = current.copy(observedSystemAccess = true)
                if (persist(next)) publish(next)
                else revokeDisclosure()
            }
        } else if (current.observedSystemAccess) {
            revokeDisclosure()
        }
    }

    fun canProcessNotifications(): Boolean =
        _state.value.isProcessingEnabled && hasNotificationAccess(context)

    /**
     * 검사와 실제 알림 저장·전송을 철회/중지와 동일한 모니터로 선형화한다.
     * suspend 작업이나 다른 저장소 락을 기다리는 코드는 이 블록에 넣지 않는다.
     */
    @Synchronized
    internal fun <T> withCurrentAccess(expectedGeneration: Long, action: () -> T): T? {
        if (expectedGeneration != accessGeneration || !canProcessNotifications()) return null
        return action()
    }

    /** 늦게 도착한 중지 관찰자가 재개 이후에 리스너를 다시 끊지 못하게 한다. */
    @Synchronized
    internal fun whenProcessingInactive(action: () -> Unit) {
        if (!canProcessNotifications()) action()
    }

    private fun requestListenerRebind() {
        if (!canProcessNotifications()) return
        runCatching {
            NotificationListenerService.requestRebind(ComponentName(context, NotificationListener::class.java))
        }.onFailure { AppLog.warning("PrivacyConsent", "알림 리스너 재연결 실패", it) }
    }

    private fun publish(next: PrivacyConsentState) {
        accessGeneration += 1
        // 빠른 중지→재개가 StateFlow에서 같은 값으로 합쳐져 재개 갱신을 놓치지 않는다.
        _state.value = next.copy(transitionGeneration = accessGeneration)
    }

    private fun clearRuntimeDataAndStop() {
        NotificationRepository.clearMemoryOnly()
        context.stopService(Intent(context, EdgeOverlayService::class.java))
        if (Looper.myLooper() == Looper.getMainLooper()) EdgePanelLauncher.close()
        else Handler(Looper.getMainLooper()).post { EdgePanelLauncher.close() }
    }

    private fun readState(): PrivacyConsentState = try {
        storage.openRead().use { input ->
            val properties = Properties().apply { load(input) }
            PrivacyConsentState(
                acceptedVersion = requireNotNull(properties.getProperty("version")).toInt(),
                acceptedAtEpochMs = requireNotNull(properties.getProperty("accepted_at")).toLong(),
                isPaused = requireNotNull(properties.getProperty("paused")).toBooleanStrict(),
                observedSystemAccess = requireNotNull(properties.getProperty("observed_system_access")).toBooleanStrict()
            )
        }
    } catch (_: Exception) {
        PrivacyConsentState()
    }

    private fun persist(next: PrivacyConsentState): Boolean {
        return try {
            val properties = Properties().apply {
                setProperty("version", next.acceptedVersion.toString())
                setProperty("accepted_at", next.acceptedAtEpochMs.toString())
                setProperty("paused", next.isPaused.toString())
                setProperty("observed_system_access", next.observedSystemAccess.toString())
            }
            storage.write(properties)
            true
        } catch (error: Exception) {
            AppLog.warning("PrivacyConsent", "알림 고지 동의 저장 실패", error)
            false
        }
    }

    companion object {
        const val CURRENT_DISCLOSURE_VERSION = 1
        internal const val CONSENT_FILE = "notification_privacy_consent.properties"
        private const val NOTIFICATION_LISTENERS_SETTING = "enabled_notification_listeners"

        @Volatile
        private var instance: PrivacyConsent? = null

        fun getInstance(context: Context): PrivacyConsent {
            val application = context.applicationContext
            return instance?.takeIf { it.context === application } ?: synchronized(this) {
                instance?.takeIf { it.context === application }
                    ?: PrivacyConsent(application).also { instance = it }
            }
        }

        fun hasNotificationAccess(context: Context): Boolean = runCatching {
            Settings.Secure.getString(context.contentResolver, NOTIFICATION_LISTENERS_SETTING)
                ?.split(':')
                ?.mapNotNull(ComponentName::unflattenFromString)
                ?.contains(ComponentName(context, NotificationListener::class.java)) == true
        }.getOrDefault(false)

        fun canProcessNotifications(context: Context): Boolean =
            getInstance(context).canProcessNotifications()
    }
}
