package com.devdooly.notificationedge.service

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.devdooly.notificationedge.BuildConfig
import com.devdooly.notificationedge.data.model.AppSettings
import com.devdooly.notificationedge.data.model.EdgeNotification
import com.devdooly.notificationedge.data.model.NotificationActionItem
import com.devdooly.notificationedge.data.repository.NotificationRepository
import com.devdooly.notificationedge.data.repository.SettingsRepository
import com.devdooly.notificationedge.util.AppLog
import com.devdooly.notificationedge.util.RemoteViewsTextExtractor
import com.devdooly.notificationedge.util.PrivacyConsent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel

class NotificationListener : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val notificationEvents = Channel<NotificationEvent>(
        capacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var privacyConsent: PrivacyConsent
    @Volatile
    private var listenerConnected = false
    private val settingsReady = MutableStateFlow(false)
    private val currentSettingsState = MutableStateFlow(AppSettings())
    private val currentSettings: AppSettings
        get() = currentSettingsState.value

    private val cancelOneCallback: (String) -> Unit = { key ->
        privacyConsent.withCurrentAccess(privacyConsent.accessGeneration) {
            runCatching { cancelNotification(key) }
                .onFailure { AppLog.warning("NotificationListener", "개별 알림 해제 실패", it) }
        }
    }
    private val cancelAllCallback: () -> Unit = {
        privacyConsent.withCurrentAccess(privacyConsent.accessGeneration) {
            runCatching { cancelAllNotifications() }
                .onFailure { AppLog.warning("NotificationListener", "전체 알림 해제 실패", it) }
        }
    }

    private sealed interface NotificationEvent {
        data class Posted(val notification: StatusBarNotification, val generation: Long) : NotificationEvent
        data class Removed(val key: String, val generation: Long) : NotificationEvent
    }

    override fun onCreate() {
        super.onCreate()
        settingsRepo = SettingsRepository.getInstance(applicationContext)
        privacyConsent = PrivacyConsent.getInstance(applicationContext)
        serviceScope.launch {
            settingsRepo.settingsFlow.collect {
                currentSettingsState.value = it
                settingsReady.value = true
            }
        }
        serviceScope.launch {
            // 최초 수신에서도 저장된 제외 앱·차단어를 읽기 전에 기본값으로 처리하지 않는다.
            settingsReady.first { it }
            for (event in notificationEvents) {
                when (event) {
                    is NotificationEvent.Posted -> {
                        if (isAccessCurrent(event.generation)) parseAndAddNotification(event.notification, event.generation)
                    }
                    is NotificationEvent.Removed -> {
                        privacyConsent.withCurrentAccess(event.generation) {
                            NotificationRepository.markAsDismissed(event.key)
                        }
                    }
                }
            }
        }
        serviceScope.launch {
            privacyConsent.state.collect { state ->
                if (state.isProcessingEnabled) refreshAuthorizedConnection(state.transitionGeneration)
                else unbindIfInactive()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        listenerConnected = false
        notificationEvents.cancel()
        clearRepositoryCallbacks()
        // 권한 철회로 바인딩이 파괴되는 경로에서는 disconnected 콜백이 생략될 수 있다.
        if (::privacyConsent.isInitialized) {
            privacyConsent.reconcileNotificationAccess()
            privacyConsent.whenProcessingInactive { NotificationRepository.clearMemoryOnly() }
        }
        serviceScope.cancel()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        listenerConnected = true
        privacyConsent.reconcileNotificationAccess()
        if (!privacyConsent.canProcessNotifications()) {
            unbindIfInactive()
            return
        }
        refreshAuthorizedConnection(privacyConsent.accessGeneration)
    }

    private fun refreshAuthorizedConnection(generation: Long) {
        if (!listenerConnected) return
        privacyConsent.withCurrentAccess(generation) {
            if (!listenerConnected) return@withCurrentAccess
            NotificationRepository.cancelNotificationCallback = cancelOneCallback
            NotificationRepository.cancelAllNotificationsCallback = cancelAllCallback
            // 연결·재개 시에만 현재 활성 알림을 가져오며, 조회와 큐 삽입도 철회와 선형화한다.
            try {
                activeNotifications?.forEach { sbn ->
                    notificationEvents.trySend(NotificationEvent.Posted(sbn, generation))
                }
            } catch (e: Exception) {
                AppLog.warning("NotificationListener", "활성 알림 초기 로드 실패", e)
            }
        }
    }

    private fun unbindIfInactive() {
        // 이 모니터 뒤에 resume/requestRebind가 실행되므로 늦은 unbind가 재개를 덮지 않는다.
        privacyConsent.whenProcessingInactive {
            clearRepositoryCallbacks()
            NotificationRepository.clearMemoryOnly()
            while (notificationEvents.tryReceive().isSuccess) { /* 철회 전 대기 중인 원문 참조 폐기 */ }
            if (listenerConnected) {
                listenerConnected = false
                runCatching { requestUnbind() }
            }
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        listenerConnected = false
        clearRepositoryCallbacks()
        privacyConsent.reconcileNotificationAccess()
        privacyConsent.whenProcessingInactive { NotificationRepository.clearMemoryOnly() }
    }

    private fun clearRepositoryCallbacks() {
        if (NotificationRepository.cancelNotificationCallback === cancelOneCallback) {
            NotificationRepository.cancelNotificationCallback = null
        }
        if (NotificationRepository.cancelAllNotificationsCallback === cancelAllCallback) {
            NotificationRepository.cancelAllNotificationsCallback = null
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null || !listenerConnected || !privacyConsent.canProcessNotifications()) return
        val generation = privacyConsent.accessGeneration
        privacyConsent.withCurrentAccess(generation) {
            notificationEvents.trySend(NotificationEvent.Posted(sbn, generation))
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn == null || !listenerConnected || !privacyConsent.canProcessNotifications()) return
        val generation = privacyConsent.accessGeneration
        privacyConsent.withCurrentAccess(generation) {
            notificationEvents.trySend(NotificationEvent.Removed(sbn.key, generation))
        }
    }

    private fun isAccessCurrent(generation: Long): Boolean =
        generation == privacyConsent.accessGeneration && privacyConsent.canProcessNotifications()

    private fun parseAndAddNotification(sbn: StatusBarNotification, generation: Long) {
        if (!isAccessCurrent(generation)) return
        val packageName = sbn.packageName
        // 본인 앱의 포그라운드 서비스 알림 등은 제외
        if (packageName == applicationContext.packageName) return

        // 0. 수신된 앱 목록(발견된 앱)에 자동 누적
        serviceScope.launch {
            try {
                if (isAccessCurrent(generation)) settingsRepo.addDiscoveredPackage(packageName, generation)
            } catch (e: Exception) {
                AppLog.warning("NotificationListener", "발견 앱 저장 실패", e)
            }
        }

        // 0-1. 사용자가 알림 제외(차단)로 지정한 앱 필터링
        if (currentSettings.excludedPackages.contains(packageName)) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        // 1. 지속적 알림(Ongoing) 및 고정 알림 필터링
        val isOngoing = (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0 ||
                (notification.flags and Notification.FLAG_NO_CLEAR) != 0
        if (isOngoing) return

        // 2. 그룹 요약(Group Summary) 알림 필터링 (Gmail, 카카오톡 등에서 개별 알림과 함께 발생하는 묶음 서머리 중복 방지)
        val isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        if (isGroupSummary) return

        // 3. 미디어 재생 제어 알림 필터링 (YouTube, YouTube Music, Spotify 등의 MediaSession / Transport)
        val isMediaTransport = notification.category == Notification.CATEGORY_TRANSPORT ||
                notification.category == Notification.CATEGORY_SERVICE ||
                extras.containsKey(Notification.EXTRA_MEDIA_SESSION) ||
                extras.containsKey("android.mediaSession")
        if (isMediaTransport) return

        // 4. 인스타그램 등 메신저의 본인 답장 완료 반사 알림(reply_notification) 필터링
        val igPushCategory = extras.getCharSequence("com.instagram.android.igns.logging.push_category")?.toString()
        if (igPushCategory == "reply_notification") return

        // NotificationChannel 및 Ranking 정보 추출 (카카오톡 단체방 제목 / ShortcutInfo 등 조회용)
        var channelObj: android.app.NotificationChannel? = null
        var rankingShortcutInfo: android.content.pm.ShortcutInfo? = null
        var rankingIsConversation: Boolean? = null
        val channelName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ranking = Ranking()
            if (currentRanking?.getRanking(sbn.key, ranking) == true) {
                channelObj = ranking.channel
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    rankingShortcutInfo = ranking.conversationShortcutInfo
                    rankingIsConversation = ranking.isConversation
                }
                ranking.channel?.name?.toString()?.trim()
            } else null
        } else null
        val tickerText = notification.tickerText?.toString()?.trim()

        // 1. Ranking 객체에서 제공하는 OS 공인 ShortcutInfo 라벨 조회 (API 31+)
        val rankingShortcutLabel = rankingShortcutInfo?.shortLabel?.toString()?.trim()
            ?: rankingShortcutInfo?.longLabel?.toString()?.trim()

        // 2. ShortcutId 기반 LauncherApps 단체방 라벨 조회 (안드로이드 One UI Conversations 보조 탐색)
        val shortcutId = notification.shortcutId
        val (launcherShortcutLabel, launcherDebugInfo) = getShortcutInfoWithDebug(packageName, shortcutId)
        val effectiveShortcutLabel = if (!rankingShortcutLabel.isNullOrBlank()) rankingShortcutLabel else launcherShortcutLabel

        // RemoteViews (One UI 상태창에 실제 렌더링된 텍스트 계층 리플렉션 분석)
        val cvTexts = RemoteViewsTextExtractor.extract(notification.contentView)
        val bigCvTexts = RemoteViewsTextExtractor.extract(notification.bigContentView)
        val headsUpCvTexts = RemoteViewsTextExtractor.extract(notification.headsUpContentView)
        val allViewTexts = (cvTexts + bigCvTexts + headsUpCvTexts).distinct()

        val parsed = com.devdooly.notificationedge.util.MessengerNotificationParser.parse(
            sbn = sbn,
            channelName = channelName,
            tickerText = tickerText,
            viewTexts = allViewTexts,
            shortcutLabel = effectiveShortcutLabel
        )

        // 내용이 없는 빈 알림은 무시
        if (parsed.roomTitle.isBlank() && parsed.cleanText.isBlank() && parsed.messages.isEmpty()) return

        val formattedTitle = if (parsed.isGroupChat || parsed.roomTitle.contains(",")) {
            com.devdooly.notificationedge.util.NotificationTextCleaner.formatGroupTitle(parsed.roomTitle)
        } else {
            parsed.roomTitle
        }

        // 5. 특정 차단 키워드 필터링 (제목, 본문, 대화 메시지에 차단 키워드가 포함된 경우 알림 제외)
        val blockedKeywords = currentSettings.blockedKeywords
        if (blockedKeywords.isNotEmpty()) {
            val contentToInspect = buildString {
                append(formattedTitle).append(" ")
                append(parsed.cleanText).append(" ")
                parsed.messages.forEach { append(it.sender).append(" ").append(it.text).append(" ") }
            }
            val isBlocked = blockedKeywords.any { kw ->
                kw.isNotBlank() && contentToInspect.contains(kw.trim(), ignoreCase = true)
            }
            if (isBlocked) return
        }

        val pm = applicationContext.packageManager
        val appName = try {
            val appInfo = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }

        val appIcon = try {
            pm.getApplicationIcon(packageName)
        } catch (e: Exception) {
            null
        }

        val contentIntent = notification.contentIntent

        // Actions 및 Quick Reply 파싱
        val actionsList = mutableListOf<NotificationActionItem>()
        notification.actions?.forEach { action ->
            var isReply = false
            var remoteInputKey: String? = null

            action.remoteInputs?.forEach { remoteInput ->
                if (remoteInput.allowFreeFormInput) {
                    isReply = true
                    remoteInputKey = remoteInput.resultKey
                }
            }

            actionsList.add(
                NotificationActionItem(
                    title = action.title ?: "",
                    actionIntent = action.actionIntent,
                    isReply = isReply,
                    remoteInputKey = remoteInputKey
                )
            )
        }

        val finalTitle = if (formattedTitle.isNotBlank()) formattedTitle else appName

        val extrasDump = if (!BuildConfig.IS_PLAY_DISTRIBUTION && settingsRepo.isDiagnosticModeEnabledSync()) {
            dumpExtras(
                extras = extras,
                sbn = sbn,
                channel = channelObj,
                viewTexts = allViewTexts,
                rankingShortcutInfo = rankingShortcutInfo,
                rankingIsConversation = rankingIsConversation,
                launcherShortcutLabel = launcherShortcutLabel,
                launcherDebugInfo = launcherDebugInfo,
                effectiveShortcutLabel = effectiveShortcutLabel
            )
        } else {
            null
        }

        val edgeNotification = EdgeNotification(
            key = sbn.key,
            id = sbn.id,
            packageName = packageName,
            appName = appName,
            appIcon = appIcon,
            title = finalTitle,
            text = parsed.cleanText,
            subText = parsed.groupRoomName ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
            messages = parsed.messages,
            timestamp = sbn.postTime,
            contentIntent = contentIntent,
            actions = actionsList,
            isClearable = sbn.isClearable,
            isGroupChat = parsed.isGroupChat,
            debugExtrasDump = extrasDump,
            titleLabel = parsed.roomTitleLabel,
            subTextLabel = parsed.groupRoomNameLabel
        )

        privacyConsent.withCurrentAccess(generation) {
            NotificationRepository.addOrUpdateNotification(edgeNotification)
        }
    }

    /**
     * LauncherApps를 통해 안드로이드 시스템에 등록된 바로가기(Shortcut / Conversation) 라벨 및 디버그 정보 조회
     */
    private fun getShortcutInfoWithDebug(packageName: String, shortcutId: String?): Pair<String?, String> {
        if (shortcutId.isNullOrBlank()) return Pair(null, "shortcutId is null/blank")
        return try {
            val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as? android.content.pm.LauncherApps
            if (launcherApps == null) {
                Pair(null, "LauncherApps service unavailable")
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                val query = android.content.pm.LauncherApps.ShortcutQuery().apply {
                    setPackage(packageName)
                    setShortcutIds(listOf(shortcutId))
                    setQueryFlags(
                        android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                                android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or
                                android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                                android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_CACHED
                    )
                }
                val shortcuts = launcherApps.getShortcuts(query, android.os.Process.myUserHandle())
                val shortcut = shortcuts?.firstOrNull()
                val label = shortcut?.shortLabel?.toString()?.trim()
                    ?: shortcut?.longLabel?.toString()?.trim()
                val count = shortcuts?.size ?: 0
                Pair(if (!label.isNullOrBlank()) label else null, "shortcutsFound=$count, label=$label, id=${shortcut?.id}")
            } else {
                Pair(null, "SDK < 25 (N_MR1)")
            }
        } catch (e: Exception) {
            Pair(null, "Exception: ${e.javaClass.simpleName}")
        }
    }

    private fun dumpExtras(
        extras: Bundle,
        sbn: StatusBarNotification,
        channel: android.app.NotificationChannel?,
        viewTexts: List<String>,
        rankingShortcutInfo: android.content.pm.ShortcutInfo?,
        rankingIsConversation: Boolean?,
        launcherShortcutLabel: String?,
        launcherDebugInfo: String,
        effectiveShortcutLabel: String?
    ): String {
        val sb = StringBuilder()
        sb.append("=== [Notification Full Debug Dump] ===\n")
        sb.append("Package: ").append(sbn.packageName).append("\n")
        sb.append("Key: ").append(sbn.key).append("\n")
        sb.append("Id: ").append(sbn.id).append("\n")
        sb.append("PostTime: ").append(sbn.postTime).append("\n")
        sb.append("Tag: ").append(sbn.tag).append("\n")
        sb.append("GroupKey: ").append(sbn.groupKey).append("\n")
        sb.append("Flags: ").append(sbn.notification.flags).append("\n")
        if (sbn.notification.shortcutId != null) {
            sb.append("ShortcutId: \"").append(sbn.notification.shortcutId).append("\"\n")
        }
        if (rankingIsConversation != null) {
            sb.append("RankingIsConversation: ").append(rankingIsConversation).append("\n")
        }
        if (rankingShortcutInfo != null) {
            sb.append("RankingShortcut.id: \"").append(rankingShortcutInfo.id).append("\"\n")
            sb.append("RankingShortcut.shortLabel: \"").append(rankingShortcutInfo.shortLabel).append("\"\n")
            sb.append("RankingShortcut.longLabel: \"").append(rankingShortcutInfo.longLabel).append("\"\n")
        } else {
            sb.append("RankingShortcutInfo: null\n")
        }
        sb.append("LauncherApps.Debug: ").append(launcherDebugInfo).append("\n")
        if (effectiveShortcutLabel != null) {
            sb.append("EffectiveShortcutLabel: \"").append(effectiveShortcutLabel).append("\"\n")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            sb.append("ChannelId: ").append(sbn.notification.channelId).append("\n")
            if (channel != null) {
                sb.append("ChannelName: \"").append(channel.name).append("\"\n")
                sb.append("ChannelDesc: \"").append(channel.description).append("\"\n")
                sb.append("ChannelGroup: \"").append(channel.group).append("\"\n")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    sb.append("ChannelParent: \"").append(channel.parentChannelId).append("\"\n")
                    sb.append("ChannelConversationId: \"").append(channel.conversationId).append("\"\n")
                }
            }
        }
        if (sbn.notification.tickerText != null) {
            sb.append("TickerText: \"").append(sbn.notification.tickerText).append("\"\n")
        }

        if (viewTexts.isNotEmpty()) {
            sb.append("--- RemoteViews Rendered Texts ---\n")
            viewTexts.forEachIndexed { i, txt ->
                sb.append("• ViewText[").append(i).append("]: \"").append(txt).append("\"\n")
            }
        }

        sb.append("--- Extras Keys & Values ---\n")
        for (key in extras.keySet()) {
            val value = extras.get(key)
            when (value) {
                is CharSequence -> sb.append("• ").append(key).append(" (CharSequence): \"").append(value).append("\"\n")
                is Array<*> -> {
                    sb.append("• ").append(key).append(" (Array[").append(value.size).append("]):\n")
                    value.forEachIndexed { i, item ->
                        if (item is Bundle) {
                            sb.append("    [").append(i).append("] (Bundle): { ")
                            for (bKey in item.keySet()) {
                                val bVal = item.get(bKey)
                                if (bVal is Bundle) {
                                    sb.append(bKey).append("={ ")
                                    for (innerKey in bVal.keySet()) {
                                        sb.append(innerKey).append("=\"").append(bVal.get(innerKey)).append("\", ")
                                    }
                                    sb.append("}, ")
                                } else {
                                    sb.append(bKey).append("=\"").append(bVal).append("\", ")
                                }
                            }
                            sb.append("}\n")
                        } else {
                            sb.append("    [").append(i).append("]: \"").append(item).append("\"\n")
                        }
                    }
                }
                is Bundle -> {
                    sb.append("• ").append(key).append(" (Bundle): { ")
                    for (bKey in value.keySet()) {
                        sb.append(bKey).append("=\"").append(value.get(bKey)).append("\", ")
                    }
                    sb.append("}\n")
                }
                else -> sb.append("• ").append(key).append(" (").append(value?.javaClass?.simpleName ?: "null").append("): \"").append(value).append("\"\n")
            }
        }
        sb.append("=========================================")
        return com.devdooly.notificationedge.util.NotificationDumpSanitizer.sanitize(sb.toString())
    }
}
