package com.devdooly.notificationedge.data.repository

import android.app.ActivityOptions
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.devdooly.notificationedge.data.model.EdgeNotification
import com.devdooly.notificationedge.data.model.MessageItem
import com.devdooly.notificationedge.data.model.NotificationActionItem
import com.devdooly.notificationedge.util.AppLog
import com.devdooly.notificationedge.util.PrivacyConsent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object NotificationRepository {

    private const val MAX_HISTORY_COUNT = 150
    private const val MAX_MESSAGES_PER_NOTIFICATION = 50

    private val _notifications = MutableStateFlow<List<EdgeNotification>>(emptyList())
    val notifications: StateFlow<List<EdgeNotification>> = _notifications.asStateFlow()

    // 알림 수신 이벤트 (Edge Lighting 트리거용)
    private val _newNotificationEvent = MutableSharedFlow<EdgeNotification>(extraBufferCapacity = 5)
    val newNotificationEvent: SharedFlow<EdgeNotification> = _newNotificationEvent.asSharedFlow()

    // 리스너 서비스 인스턴스 콜백
    var cancelNotificationCallback: ((String) -> Unit)? = null
    var cancelAllNotificationsCallback: (() -> Unit)? = null

    fun addOrUpdateNotification(notification: EdgeNotification) {
        _notifications.update { list ->
            val existing = list.firstOrNull { isSameConversationOrNotification(it, notification) }

            val mergedMessages = if (existing != null) {
                val combined = existing.messages.toMutableList()
                if (notification.messages.isNotEmpty()) {
                    combined.addAll(notification.messages)
                } else if (notification.text.isNotBlank()) {
                    combined.add(
                        MessageItem(
                            sender = notification.title,
                            text = notification.text,
                            timestamp = notification.timestamp
                        )
                    )
                }
                combined.distinctBy { "${it.timestamp}_${it.sender}_${it.isFromUser}_${it.text}" }
                    .sortedBy { it.timestamp }
                    .takeLast(MAX_MESSAGES_PER_NOTIFICATION)
            } else {
                if (notification.messages.isNotEmpty()) {
                    notification.messages.takeLast(MAX_MESSAGES_PER_NOTIFICATION)
                } else if (notification.text.isNotBlank()) {
                    listOf(
                        MessageItem(
                            sender = notification.title,
                            text = notification.text,
                            timestamp = notification.timestamp
                        )
                    )
                } else {
                    emptyList()
                }
            }

            val updatedNotification = notification.copy(
                messages = mergedMessages,
                contentIntent = notification.contentIntent ?: existing?.contentIntent,
                isDismissed = false
            )

            val filteredList = list.filterNot { isSameConversationOrNotification(it, notification) }

            (listOf(updatedNotification) + filteredList).take(MAX_HISTORY_COUNT)
        }

        // 본인이 보낸 답장 메시지 알림(반사 노티)인 경우 새 알림 이벤트(라이팅/진동)를 방출하지 않음
        val isLatestFromUser = notification.messages.lastOrNull()?.isFromUser == true
        if (!isLatestFromUser) {
            _newNotificationEvent.tryEmit(notification)
        }
    }

    /**
     * 시스템 상태바에서 알림이 지워졌을 때 호출: 목록에서 삭제하지 않고 과거 히스토리로 보존
     */
    fun markAsDismissed(key: String) {
        _notifications.update { list ->
            list.map {
                if (it.key == key) it.copy(isDismissed = true) else it
            }
        }
    }

    /**
     * 사용자가 엣지 패널에서 명시적으로 삭제 버튼을 눌렀을 때
     */
    fun removeNotification(key: String) {
        cancelNotificationCallback?.invoke(key)
        _notifications.update { list ->
            list.filterNot { it.key == key }
        }
    }

    /**
     * 모두 지우기
     */
    fun clearAll() {
        _notifications.value = emptyList()
        cancelAllNotificationsCallback?.invoke()
    }

    /** 동의 철회·중지에서는 다른 앱의 원본 알림을 삭제하지 않고 Slivue 캐시만 비운다. */
    fun clearMemoryOnly() {
        cancelNotificationCallback = null
        cancelAllNotificationsCallback = null
        _notifications.value = emptyList()
    }

    /** 진단 모드를 끌 때 메모리에 남은 Extras 덤프를 즉시 제거한다. */
    fun clearDiagnosticDumps() {
        _notifications.update { list ->
            list.map { notification ->
                if (notification.debugExtrasDump == null) notification
                else notification.copy(debugExtrasDump = null)
            }
        }
    }

    fun dismissNotification(key: String) {
        removeNotification(key)
    }

    /**
     * 채팅방으로 이동하거나 앱 실행 (유튜브/유튜브 뮤직 등 미디어 재생 중단 방지)
     */
    fun openNotificationApp(
        context: Context,
        notification: EdgeNotification,
        autoDismiss: Boolean = true,
        onClose: () -> Unit
    ) {
        val consent = PrivacyConsent.getInstance(context)
        val generation = consent.accessGeneration
        if (!consent.canProcessNotifications()) {
            onClose()
            return
        }
        // 1. 오버레이 윈도우를 먼저 닫아 윈도우 포커스를 정상적으로 시스템/타깃 앱에 반환
        onClose()

        consent.withCurrentAccess(generation) {
            // 2. 옵션이 켜진 경우 해당 메시지 그룹을 자동 삭제한다.
            if (autoDismiss) removeNotification(notification.key)
            var launched = false

            // 3. 특정 대화방 PendingIntent를 우선 실행한다.
            if (notification.contentIntent != null) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        val options = ActivityOptions.makeBasic().apply {
                            pendingIntentBackgroundActivityStartMode = ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                        }
                        notification.contentIntent.send(context, 0, null, null, null, null, options.toBundle())
                    } else {
                        notification.contentIntent.send()
                    }
                    launched = true
                } catch (e: Exception) {
                    AppLog.warning("NotificationRepo", "알림 PendingIntent 실행 실패", e)
                }
            }

            // 4. 전용 Intent가 없거나 실패하면 기존 태스크를 유지한 런처 실행을 시도한다.
            if (!launched) {
                try {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(notification.packageName)?.apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    if (launchIntent != null) context.startActivity(launchIntent)
                } catch (e: Exception) {
                    AppLog.warning("NotificationRepo", "앱 실행 Intent 처리 실패", e)
                }
            }
        }
    }

    fun sendQuickReply(
        context: Context,
        notificationKey: String,
        action: NotificationActionItem,
        replyText: String
    ) {
        val consent = PrivacyConsent.getInstance(context)
        val generation = consent.accessGeneration
        if (!consent.canProcessNotifications()) return
        if (action.actionIntent == null || action.remoteInputKey == null) return

        try {
            val intent = Intent()
            val bundle = Bundle()
            bundle.putCharSequence(action.remoteInputKey, replyText)

            val remoteInput = RemoteInput.Builder(action.remoteInputKey).build()
            RemoteInput.addResultsToIntent(arrayOf(remoteInput), intent, bundle)

            consent.withCurrentAccess(generation) {
                action.actionIntent.send(context, 0, intent)
                appendSentReply(notificationKey, replyText)
            }
        } catch (e: Exception) {
            AppLog.warning("NotificationRepo", "빠른 답장 전송 실패", e)
        }
    }

    /** 전송과 같은 동의 모니터 안에서만 호출해 철회 뒤 답장 캐시를 갱신하지 않는다. */
    private fun appendSentReply(notificationKey: String, replyText: String) {
        _notifications.update { list ->
            list.map { notif ->
                if (notif.key != notificationKey) return@map notif
                val userMessage = MessageItem(
                    sender = "나", text = replyText,
                    timestamp = System.currentTimeMillis(), isFromUser = true
                )
                val existingMessages = when {
                    notif.messages.isNotEmpty() -> notif.messages
                    notif.text.isNotBlank() -> listOf(MessageItem(
                        sender = notif.title.ifBlank { notif.appName }, text = notif.text,
                        timestamp = notif.timestamp, isFromUser = false
                    ))
                    else -> emptyList()
                }
                notif.copy(
                    messages = (existingMessages + userMessage).takeLast(MAX_MESSAGES_PER_NOTIFICATION),
                    text = "나: $replyText", timestamp = System.currentTimeMillis()
                )
            }
        }
    }

    private fun isSameConversationOrNotification(a: EdgeNotification, b: EdgeNotification): Boolean {
        if (a.key == b.key) return true
        if (a.packageName != b.packageName) return false

        // 1. 단체방인 경우: 방 제목(title) 또는 subText가 일치하면 같은 단체방으로 병합
        if (a.isGroupChat && b.isGroupChat) {
            if (a.title.isNotBlank() && a.title == b.title) return true
            if (!a.subText.isNullOrBlank() && a.subText == b.subText) return true
            return false
        }

        // 2. 일반 알림(토스증권 종목 알림, 뉴스, 시스템 알림 등) 또는 1:1 개인 대화:
        // subText("토스증권" 등)가 우연히 같다고 해서 서로 다른 종목/제목의 알림을 병합하면 안 되며,
        // 제목(title)이 정확히 일치할 때만 동일 알림/동일 대화방으로 갱신
        return a.title.isNotBlank() && a.title == b.title
    }
}
