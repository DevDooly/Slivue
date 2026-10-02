package com.devdooly.notificationedge.util

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.devdooly.notificationedge.service.NotificationListener
import org.xmlpull.v1.XmlPullParser

/** 영상 앱의 세션 하나만 일시정지한다. 전역 미디어키와 자동 재생은 사용하지 않는다. */
object MediaControlHelper {
    internal enum class PauseResult { NOT_NEEDED, PAUSED, UNCONFIRMED, UNAVAILABLE }

    internal suspend fun pauseVideoBeforePanel(context: Context): PauseResult = withContext(Dispatchers.IO) {
        try {
            val manager = context.getSystemService(MediaSessionManager::class.java)
                ?: return@withContext PauseResult.UNAVAILABLE
            val sessions = manager.getActiveSessions(ComponentName(context, NotificationListener::class.java))
            // 플랫폼의 우선순위 순서를 따른다. 음악 앱과 Slivue 자체는 제외한다.
            val target = sessions.firstOrNull {
                isPlaybackActive(it.playbackState?.state) && isVideoApp(context, it.packageName)
            } ?: return@withContext PauseResult.NOT_NEEDED
            pauseAndAwait(AndroidPlaybackSession(target))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AppLog.warning("MediaControlHelper", "영상 일시정지 확인 불가; 패널 실행은 유지", error)
            PauseResult.UNAVAILABLE
        }
    }

    internal fun isVideoApp(context: Context, packageName: String): Boolean {
        if (packageName == context.packageName || packageName in MUSIC_PACKAGES) return false
        return try {
            val resources = context.packageManager.getResourcesForApplication(packageName)
            resources.assets.openXmlResourceParser("AndroidManifest.xml").use { parser ->
                declaresPictureInPicture(parser) {
                    val namespace = "http://schemas.android.com/apk/res/android"
                    val resourceId = parser.getAttributeResourceValue(namespace, "supportsPictureInPicture", 0)
                    if (resourceId != 0) resources.getBoolean(resourceId)
                    else parser.getAttributeBooleanValue(namespace, "supportsPictureInPicture", false)
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    /** 공개 SDK로 APK 매니페스트를 읽는다. 비공개 ActivityInfo 플래그나 리플렉션은 사용하지 않는다. */
    internal fun declaresPictureInPicture(parser: XmlPullParser, attributeEnabled: () -> Boolean): Boolean {
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "activity" && attributeEnabled()) {
                return true
            }
            parser.next()
        }
        return false
    }

    internal interface PlaybackSession {
        val state: Int?
        val actions: Long
        fun pause()
    }

    private class AndroidPlaybackSession(private val controller: MediaController) : PlaybackSession {
        override val state get() = controller.playbackState?.state
        override val actions get() = controller.playbackState?.actions ?: 0L
        override fun pause() = controller.transportControls.pause()
    }

    internal suspend fun pauseAndAwait(session: PlaybackSession): PauseResult {
        if (!isPlaybackActive(session.state)) return PauseResult.NOT_NEEDED
        if (session.actions and (PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE) == 0L) {
            return PauseResult.UNCONFIRMED
        }
        session.pause() // 토글은 이미 멈춘 영상을 재생할 수 있으므로 PAUSE만 사용한다.
        repeat(24) {
            if (session.state == PlaybackState.STATE_PAUSED || session.state == PlaybackState.STATE_STOPPED) {
                // 앱이 재생 상태와 PiP 파라미터를 함께 반영할 수 있도록 짧은 여유를 둔다.
                delay(80)
                return PauseResult.PAUSED
            }
            delay(25)
        }
        return PauseResult.UNCONFIRMED
    }

    internal fun isPlaybackActive(state: Int?) = state in ACTIVE_STATES

    private val ACTIVE_STATES = setOf(
        PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING,
        PlaybackState.STATE_FAST_FORWARDING, PlaybackState.STATE_REWINDING,
        PlaybackState.STATE_SKIPPING_TO_NEXT, PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
        PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM
    )

    private val MUSIC_PACKAGES = setOf(
        "com.google.android.apps.youtube.music", "com.spotify.music", "com.sec.android.app.music",
        "com.apple.android.music", "com.amazon.mp3",
        // 음악 앱에 뮤직비디오/PiP 화면이 있어도 음악 재생을 중단하지 않는다.
        "com.iloen.melon", "com.ktmusic.geniemusic"
    )
}
