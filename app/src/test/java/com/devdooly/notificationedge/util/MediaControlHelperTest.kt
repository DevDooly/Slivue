package com.devdooly.notificationedge.util

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.media.session.PlaybackState
import android.util.Xml
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34])
@OptIn(ExperimentalCoroutinesApi::class)
class MediaControlHelperTest {
    private class Session(
        override var state: Int? = PlaybackState.STATE_PLAYING,
        override var actions: Long = PlaybackState.ACTION_PAUSE
    ) : MediaControlHelper.PlaybackSession {
        var pauseCount = 0
        override fun pause() { pauseCount++ }
    }

    @Test fun pauseIsConfirmedBeforeReturningAndNeverTogglesPlayback() = runTest {
        val session = Session(actions = PlaybackState.ACTION_PLAY_PAUSE)
        launch { delay(100); session.state = PlaybackState.STATE_PAUSED }
        assertEquals(MediaControlHelper.PauseResult.PAUSED, MediaControlHelper.pauseAndAwait(session))
        assertEquals(1, session.pauseCount)
        assertEquals(180, testScheduler.currentTime)
    }

    @Test fun alreadyPausedStoppedAndMissingStateAreNotTouched() = runTest {
        listOf(PlaybackState.STATE_PAUSED, PlaybackState.STATE_STOPPED, null).forEach { state ->
            val session = Session(state)
            assertEquals(MediaControlHelper.PauseResult.NOT_NEEDED, MediaControlHelper.pauseAndAwait(session))
            assertEquals(0, session.pauseCount)
        }
    }

    @Test fun unresponsivePlayerDoesNotBlockPanelIndefinitely() = runTest {
        val session = Session()
        assertEquals(MediaControlHelper.PauseResult.UNCONFIRMED, MediaControlHelper.pauseAndAwait(session))
        assertEquals(600, testScheduler.currentTime)
        assertEquals(1, session.pauseCount)
    }

    @Test fun unsupportedPauseDoesNotSendUnknownOrToggleCommands() = runTest {
        val session = Session(actions = PlaybackState.ACTION_PLAY)
        assertEquals(MediaControlHelper.PauseResult.UNCONFIRMED, MediaControlHelper.pauseAndAwait(session))
        assertEquals(0, session.pauseCount)
    }

    @Test fun cancellingWaitDoesNotReplayOrPauseAgain() = runTest {
        val session = Session()
        val task = launch { MediaControlHelper.pauseAndAwait(session) }
        testScheduler.runCurrent()
        task.cancel()
        testScheduler.advanceUntilIdle()
        assertTrue(task.isCancelled)
        assertEquals(1, session.pauseCount)
    }

    @Test fun detectsActivityPipDeclarationNotPackageNameOrOtherElements() {
        fun declares(elements: String): Boolean {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"><application>$elements</application></manifest>"))
            return MediaControlHelper.declaresPictureInPicture(parser) {
                parser.getAttributeValue("http://schemas.android.com/apk/res/android", "supportsPictureInPicture") == "true"
            }
        }
        assertTrue(declares("<activity android:supportsPictureInPicture=\"true\"/>"))
        assertFalse(declares("<activity android:supportsPictureInPicture=\"false\"/>"))
        assertFalse(declares("<service android:supportsPictureInPicture=\"true\"/>"))
        assertFalse(declares("<activity/>"))
    }

    @Test fun musicSelfAndUnknownAppsAreNotTargeted() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertFalse(MediaControlHelper.isVideoApp(context, context.packageName))
        assertFalse(MediaControlHelper.isVideoApp(context, "example.fake.revanced.android.youtube"))
        assertFalse(MediaControlHelper.isVideoApp(context, "com.google.android.apps.youtube.music"))
        assertFalse(MediaControlHelper.isVideoApp(context, "example.missing"))
    }

    @Test fun melonIsExcludedEvenWhenItsManifestSupportsPip() {
        assertMusicIsExcludedBeforeReadingManifest("com.iloen.melon")
    }

    @Test fun genieIsExcludedEvenWhenItsManifestSupportsPip() {
        assertMusicIsExcludedBeforeReadingManifest("com.ktmusic.geniemusic")
    }

    @Test fun existingMusicExclusionsStillTakePrecedenceOverPipSupport() {
        val environment = PipCapableEnvironment()
        val musicPackages = listOf(
            "com.google.android.apps.youtube.music", "com.spotify.music",
            "com.sec.android.app.music", "com.apple.android.music", "com.amazon.mp3"
        )
        musicPackages.forEach { packageName ->
            assertFalse(packageName, MediaControlHelper.isVideoApp(environment.context, packageName))
        }
        verify(exactly = 0) { environment.packageManager.getResourcesForApplication(any<String>()) }
    }

    @Test fun nonMusicPipAppsRemainVideoTargets() {
        val environment = PipCapableEnvironment()
        assertTrue(MediaControlHelper.isVideoApp(environment.context, "com.google.android.youtube"))
        verify(exactly = 1) { environment.packageManager.getResourcesForApplication("com.google.android.youtube") }
    }

    private fun assertMusicIsExcludedBeforeReadingManifest(packageName: String) {
        val environment = PipCapableEnvironment()
        assertFalse(packageName, MediaControlHelper.isVideoApp(environment.context, packageName))
        verify(exactly = 0) { environment.packageManager.getResourcesForApplication(any<String>()) }
        // 설치되지 않은 앱이라서 우연히 제외되는 검사가 되지 않도록 PiP 판별도 확인한다.
        assertTrue(MediaControlHelper.isVideoApp(environment.context, "com.google.android.youtube"))
    }

    /** 모든 앱이 PiP를 선언한 상황에서도 음악 앱 제외 규칙이 우선하는지 검증한다. */
    private class PipCapableEnvironment {
        val context = mockk<Context>()
        val packageManager = mockk<PackageManager>()

        init {
            val resources = mockk<Resources>()
            val assets = mockk<AssetManager>()
            val parser = mockk<XmlResourceParser>()
            every { context.packageName } returns "com.devdooly.notificationedge"
            every { context.packageManager } returns packageManager
            every { packageManager.getResourcesForApplication(any<String>()) } returns resources
            every { resources.assets } returns assets
            every { assets.openXmlResourceParser("AndroidManifest.xml") } returns parser
            every { parser.eventType } returns XmlPullParser.START_TAG
            every { parser.name } returns "activity"
            every { parser.getAttributeResourceValue(any<String>(), "supportsPictureInPicture", 0) } returns 0
            every { parser.getAttributeBooleanValue(any<String>(), "supportsPictureInPicture", false) } returns true
            every { parser.close() } returns Unit
        }
    }
}
