package com.devdooly.notificationedge.util

import android.app.Notification
import android.content.Context
import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34], manifest = Config.NONE)
class MessengerNotificationParserTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun createMockSbn(
        packageName: String = "com.kakao.talk",
        title: String? = null,
        text: String? = null,
        subText: String? = null,
        conversationTitle: String? = null,
        summaryText: String? = null,
        isGroupConversation: Boolean? = null,
        selfDisplayName: String? = null,
        messages: Array<Bundle>? = null
    ): StatusBarNotification {
        val sbn = mockk<StatusBarNotification>(relaxed = true)
        
        val builder = NotificationCompat.Builder(context, "test_channel")
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(subText)
            
        val notification = builder.build()
        if (conversationTitle != null) {
            notification.extras.putCharSequence(Notification.EXTRA_CONVERSATION_TITLE, conversationTitle)
        }
        if (summaryText != null) {
            notification.extras.putCharSequence(Notification.EXTRA_SUMMARY_TEXT, summaryText)
        }
        if (isGroupConversation != null) {
            notification.extras.putBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, isGroupConversation)
        }
        if (selfDisplayName != null) {
            notification.extras.putCharSequence(Notification.EXTRA_SELF_DISPLAY_NAME, selfDisplayName)
        }
        if (messages != null) {
            @Suppress("DEPRECATION")
            notification.extras.putParcelableArray(Notification.EXTRA_MESSAGES, messages)
        }

        every { sbn.packageName } returns packageName
        every { sbn.notification } returns notification
        every { sbn.postTime } returns 1000L

        return sbn
    }

    @Test
    fun `instagram 1-to-1 direct message dump should be parsed as direct chat with proper self reply identification`() {
        val msg0 = Bundle().apply {
            putCharSequence("sender", "병종")
            putCharSequence("text", "답장은 잘가나 단체방 뱃지는 뭐져")
            putLong("time", 1787717190698L)
        }
        val msg1 = Bundle().apply {
            putCharSequence("sender", "김선홍")
            putCharSequence("text", "안그래도 그거 수정중 ㅋㅋ")
            putLong("time", 1787717207929L)
        }
        val msg2 = Bundle().apply {
            putCharSequence("sender", "병종")
            putCharSequence("text", "ㅋㅋㅋㅋ")
            putLong("time", 1787717211688L)
        }
        val msg3 = Bundle().apply {
            putCharSequence("sender", "김선홍")
            putCharSequence("text", "글고 내가 보낸 내용도 남이 보낸것처럼 한번 더 보이네")
            putLong("time", 1787717258276L)
        }
        val msg4 = Bundle().apply {
            putCharSequence("sender", "병종")
            putCharSequence("text", "맞아여 그렇게 알림이 뜨더라구요")
            putLong("time", 1787717387797L)
        }

        val sbn = createMockSbn(
            packageName = "com.instagram.android",
            title = "sunhong2910: 병종",
            text = "맞아여 그렇게 알림이 뜨더라구요",
            isGroupConversation = false,
            selfDisplayName = "김선홍",
            messages = arrayOf(msg0, msg1, msg2, msg3, msg4)
        )

        val result = MessengerNotificationParser.parse(sbn)
        assertFalse(result.isGroupChat)
        assertEquals("병종", result.roomTitle)
        assertEquals(5, result.messages.size)
        assertFalse(result.messages[0].isFromUser)
        assertEquals("병종", result.messages[0].sender)
        assertTrue(result.messages[1].isFromUser)
        assertEquals("나", result.messages[1].sender)
        assertTrue(result.messages[3].isFromUser)
        assertEquals("나", result.messages[3].sender)
        assertFalse(result.messages[4].isFromUser)
        assertEquals("병종", result.messages[4].sender)
    }

    @Test
    fun `instagram direct dump with self latest reply should resolve room title as other party and tag self message`() {
        val msg0 = Bundle().apply {
            putCharSequence("sender", "김선홍")
            putCharSequence("text", "만든 앱으로 답장하긔")
            putLong("time", 1787834960679L)
        }
        val msg1 = Bundle().apply {
            putCharSequence("sender", "Feathers Mcgraw")
            putCharSequence("text", "헉 변태다")
            putLong("time", 1787834971362L)
        }
        val msg2 = Bundle().apply {
            putCharSequence("sender", "김선홍")
            putCharSequence("text", "아니 왜 시부래 답장하니까 두번 떠 버그발견")
            putLong("time", 1787834987765L)
        }

        val sbn = createMockSbn(
            packageName = "com.instagram.android",
            title = "sunhong2910: 김선홍",
            conversationTitle = "sunhong2910",
            text = "아니 왜 시부래 답장하니까 두번 떠 버그발견",
            isGroupConversation = false,
            selfDisplayName = "김선홍",
            messages = arrayOf(msg0, msg1, msg2)
        )

        val result = MessengerNotificationParser.parse(
            sbn = sbn,
            channelName = "메시지",
            shortcutLabel = "Feathers Mcgraw"
        )
        assertFalse(result.isGroupChat)
        assertEquals("sunhong2910", result.roomTitle)
        assertEquals(3, result.messages.size)
        assertTrue(result.messages[0].isFromUser)
        assertFalse(result.messages[1].isFromUser)
        assertEquals("Feathers Mcgraw", result.messages[1].sender)
        assertTrue(result.messages[2].isFromUser)
        assertEquals("나", result.messages[2].sender)
    }

    @Test
    fun `samsung messaging 1-to-1 message dump with self reply should remain direct chat and identify self messages`() {
        val msg0 = Bundle().apply {
            putCharSequence("sender", "전병종")
            putCharSequence("text", "테스트 해보기")
            putLong("time", 1787717176971L)
        }
        val msg1 = Bundle().apply {
            putCharSequence("text", "아아아아아아")
            putLong("time", 1787717281696L)
        }
        val msg2 = Bundle().apply {
            putCharSequence("text", "이건 뭔...")
            putLong("time", 1787717294823L)
        }

        val sbn = createMockSbn(
            packageName = "com.samsung.android.messaging",
            title = "전병종",
            subText = "전병종",
            text = "테스트 해보기",
            isGroupConversation = false,
            selfDisplayName = "나",
            messages = arrayOf(msg0, msg1, msg2)
        )

        val result = MessengerNotificationParser.parse(sbn)
        assertFalse(result.isGroupChat)
        assertEquals("전병종", result.roomTitle)
        assertEquals(3, result.messages.size)
        assertFalse(result.messages[0].isFromUser)
        assertEquals("전병종", result.messages[0].sender)
        assertTrue(result.messages[1].isFromUser)
        assertEquals("나", result.messages[1].sender)
        assertTrue(result.messages[2].isFromUser)
        assertEquals("나", result.messages[2].sender)
    }

    @Test
    fun `kakao talk group chat with channelName should extract group room title and sender properly`() {
        val msg0 = Bundle().apply {
            putCharSequence("sender", "김동관")
            putCharSequence("text", "속도 3, 4 정도로 걸음 유리는")
            putLong("time", 1787719594782L)
        }

        val sbn = createMockSbn(
            packageName = "com.kakao.talk",
            title = "김동관",
            text = "속도 3, 4 정도로 걸음 유리는",
            isGroupConversation = true,
            selfDisplayName = "김선홍",
            messages = arrayOf(msg0)
        )

        val result = MessengerNotificationParser.parse(
            sbn = sbn,
            channelName = "11단톡"
        )

        assertTrue(result.isGroupChat)
        assertEquals("11단톡", result.roomTitle)
        assertEquals("김동관", result.currentSender)
        assertEquals("속도 3, 4 정도로 걸음 유리는", result.cleanText)
        assertEquals(1, result.messages.size)
        assertEquals("김동관", result.messages[0].sender)
        assertFalse(result.messages[0].isFromUser)
    }

    @Test
    fun `kakao talk group chat with system channel like 알림 받지 않는 메시지 should be ignored and fallback to sender`() {
        val msg0 = Bundle().apply {
            putCharSequence("sender", "김동관")
            putCharSequence("text", "속도 3, 4 정도로 걸음 유리는")
            putLong("time", 1787719594782L)
        }

        val sbn = createMockSbn(
            packageName = "com.kakao.talk",
            title = "김동관",
            text = "속도 3, 4 정도로 걸음 유리는",
            isGroupConversation = true,
            selfDisplayName = "김선홍",
            messages = arrayOf(msg0)
        )

        val result = MessengerNotificationParser.parse(
            sbn = sbn,
            channelName = "알림 받지 않는 메시지"
        )

        assertTrue(result.isGroupChat)
        assertEquals("김동관", result.roomTitle) // 시스템 채널명 "알림 받지 않는 메시지"는 무시되어 발신자 이름 유지
        assertEquals("김동관", result.currentSender)
        assertEquals("속도 3, 4 정도로 걸음 유리는", result.cleanText)
    }

    @Test
    fun `kakao talk group chat with viewTexts from RemoteViews should extract 11단톡 properly even when channel is generic`() {
        val msg0 = Bundle().apply {
            putCharSequence("sender", "김동관")
            putCharSequence("text", "속도 3, 4 정도로 걸음 유리는")
            putLong("time", 1787719594782L)
        }

        val sbn = createMockSbn(
            packageName = "com.kakao.talk",
            title = "김동관",
            text = "속도 3, 4 정도로 걸음 유리는",
            isGroupConversation = true,
            selfDisplayName = "김선홍",
            messages = arrayOf(msg0)
        )

        val result = MessengerNotificationParser.parse(
            sbn = sbn,
            channelName = "알림 받지 않는 메시지",
            viewTexts = listOf("11단톡", "김동관", "속도 3, 4 정도로 걸음 유리는")
        )

        assertTrue(result.isGroupChat)
        assertEquals("11단톡", result.roomTitle) // RemoteViews 렌더링 텍스트에서 11단톡 완벽 추출
        assertEquals("김동관", result.currentSender)
        assertEquals("속도 3, 4 정도로 걸음 유리는", result.cleanText)
    }

    @Test
    fun `kakao talk group chat with shortcutLabel should prioritize shortcut label 11단톡`() {
        val msg0 = Bundle().apply {
            putCharSequence("sender", "정상현")
            putCharSequence("text", "근데 앞 수술이 저러면")
            putLong("time", 1787721555479L)
        }
        val msg1 = Bundle().apply {
            putCharSequence("sender", "정상현")
            putCharSequence("text", "더 긴장될것같은디")
            putLong("time", 1787721558722L)
        }
        val msg2 = Bundle().apply {
            putCharSequence("sender", "김동관")
            putCharSequence("text", "워...")
            putLong("time", 1787721644060L)
        }

        val sbn = createMockSbn(
            packageName = "com.kakao.talk",
            title = "김동관",
            text = "워...",
            isGroupConversation = true,
            selfDisplayName = "김선홍",
            messages = arrayOf(msg0, msg1, msg2)
        )

        val result = MessengerNotificationParser.parse(
            sbn = sbn,
            channelName = "알림 받지 않는 메시지",
            shortcutLabel = "11단톡"
        )

        assertTrue(result.isGroupChat)
        assertEquals("11단톡", result.roomTitle) // ShortcutLabel에서 11단톡 완벽 추출
        assertEquals("김동관", result.currentSender)
        assertEquals("워...", result.cleanText)
        assertEquals(3, result.messages.size)
        assertEquals("정상현", result.messages[0].sender)
        assertEquals("정상현", result.messages[1].sender)
        assertEquals("김동관", result.messages[2].sender)
    }

    @Test
    fun `kakao talk 1-to-1 chat dump with isGroupConversation false should remain direct chat`() {
        val msg0 = Bundle().apply {
            putCharSequence("sender", "용선정")
            putCharSequence("text", "뭐 시술 시간에 따라 다르것지 뭐")
            putLong("time", 1787722179037L)
        }
        val msg1 = Bundle().apply {
            putCharSequence("sender", "용선정")
            putCharSequence("text", "나름 수술이라 뭐 아무것도 못먹을 건디")
            putLong("time", 1787722186772L)
        }
        val msg2 = Bundle().apply {
            putCharSequence("sender", "용선정")
            putCharSequence("text", "걍 강제 금식이여??")
            putLong("time", 1787722191754L)
        }

        val sbn = createMockSbn(
            packageName = "com.kakao.talk",
            title = "용선정",
            text = "걍 강제 금식이여??",
            isGroupConversation = false, // 1:1 개인 대화방
            selfDisplayName = "김선홍",
            messages = arrayOf(msg0, msg1, msg2)
        )

        val result = MessengerNotificationParser.parse(
            sbn = sbn,
            channelName = "알림 받지 않는 메시지",
            shortcutLabel = "용선정"
        )

        assertFalse(result.isGroupChat) // 1:1 대화방이므로 false여야 함!
        assertEquals("용선정", result.roomTitle)
        assertEquals("용선정", result.currentSender)
        assertEquals("걍 강제 금식이여??", result.cleanText)
        assertEquals(3, result.messages.size)
    }

    @Test
    fun `kakao talk group chat single message dump with isGroupConversation true should remain group chat`() {
        val msg0 = Bundle().apply {
            putCharSequence("sender", "임현석")
            putCharSequence("text", "https://bbs.ruliweb.com/community/board/300143/read/76449446")
            putLong("time", 1787725624792L)
        }

        val sbn = createMockSbn(
            packageName = "com.kakao.talk",
            title = "임현석",
            text = "https://bbs.ruliweb.com/community/board/300143/read/76449446",
            isGroupConversation = true, // 단체방!
            selfDisplayName = "김선홍",
            messages = arrayOf(msg0)
        )

        val result = MessengerNotificationParser.parse(
            sbn = sbn,
            channelName = "알림 받지 않는 메시지"
        )

        assertTrue(result.isGroupChat) // 단체방 플래그 확실히 true!
        assertEquals("임현석", result.currentSender)
        assertEquals("https://bbs.ruliweb.com/community/board/300143/read/76449446", result.cleanText)
        assertEquals(1, result.messages.size)
    }

    @Test
    fun `kakao talk group chat with subText should extract room title and sender properly`() {
        val sbn = createMockSbn(
            packageName = "com.kakao.talk",
            title = "홍길동",
            text = "오늘 회의 몇시인가요?",
            subText = "개발팀 단톡방"
        )

        val result = MessengerNotificationParser.parse(sbn)
        assertEquals("개발팀 단톡방", result.roomTitle)
        assertEquals("개발팀 단톡방", result.groupRoomName)
        assertTrue(result.isGroupChat)
        assertEquals("홍길동", result.currentSender)
        assertEquals("오늘 회의 몇시인가요?", result.cleanText)
    }

    @Test
    fun `kakao talk group chat with title containing paren should extract room title`() {
        val sbn = createMockSbn(
            packageName = "com.kakao.talk",
            title = "홍길동 (가족모임)",
            text = "저녁 7시에 만나요"
        )

        val result = MessengerNotificationParser.parse(sbn)
        assertEquals("가족모임", result.roomTitle)
        assertEquals("가족모임", result.groupRoomName)
        assertTrue(result.isGroupChat)
        assertEquals("홍길동", result.currentSender)
    }

    @Test
    fun `kakao talk 1-to-1 direct message should remain direct chat`() {
        val sbn = createMockSbn(
            packageName = "com.kakao.talk",
            title = "이영희",
            text = "밥 먹었어?"
        )

        val result = MessengerNotificationParser.parse(sbn)
        assertEquals("이영희", result.roomTitle)
        assertNull(result.groupRoomName)
        assertEquals("이영희", result.currentSender)
        assertEquals("밥 먹었어?", result.cleanText)
    }

    @Test
    fun `kakao talk actual dump group chat should synthesize participants room title`() {
        val msg1 = Bundle().apply {
            putCharSequence("sender", "미리비트 윤창빈 책임")
            putCharSequence("text", "전 한 29분 정도입니다")
            putLong("time", 1001L)
        }
        val msg2 = Bundle().apply {
            putCharSequence("sender", "미리비트 정진우 책임")
            putCharSequence("text", "판교 출발")
            putLong("time", 1002L)
        }
        val msg3 = Bundle().apply {
            putCharSequence("sender", "김영남")
            putCharSequence("text", "@미리비트 정진우 책임 미리 주문점여")
            putLong("time", 1003L)
        }

        val sbn = createMockSbn(
            packageName = "com.kakao.talk",
            title = "김영남",
            text = "@미리비트 정진우 책임 미리 주문점여",
            isGroupConversation = true,
            messages = arrayOf(msg1, msg2, msg3)
        )

        val result = MessengerNotificationParser.parse(sbn)
        assertTrue(result.isGroupChat)
        assertEquals("미리비트 윤창빈 책임, 미리비트 정진우 책임, 김영남", result.roomTitle)
        assertEquals("김영남", result.currentSender)
        assertEquals("@미리비트 정진우 책임 미리 주문점여", result.cleanText)
        assertEquals(3, result.messages.size)
    }

    @Test
    fun `kakao talk group chat with 1 sender should keep sender name cleanly without awkward suffix`() {
        val msg = Bundle().apply {
            putCharSequence("sender", "김수환")
            putCharSequence("text", "브레이크가 고장나서 가장자리로 달리려던걸까")
            putLong("time", 1001L)
        }

        val sbn = createMockSbn(
            packageName = "com.kakao.talk",
            title = "김수환",
            text = "브레이크가 고장나서 가장자리로 달리려던걸까",
            isGroupConversation = true,
            messages = arrayOf(msg)
        )

        val result = MessengerNotificationParser.parse(sbn)
        assertTrue(result.isGroupChat)
        assertEquals("김수환", result.roomTitle)
        assertEquals("김수환", result.currentSender)
        assertEquals("브레이크가 고장나서 가장자리로 달리려던걸까", result.cleanText)
        assertEquals(1, result.messages.size)
    }

    @Test
    fun `toss securities non-chat notification should not be parsed as group chat and should have empty messages`() {
        val sbn = createMockSbn(
            packageName = "viva.republica.toss",
            title = "KODEX SK하이닉스단일종목레버리지 📈",
            text = "주식 가격이 5% 올랐어요(10,590원)",
            subText = "토스증권"
        )

        val result = MessengerNotificationParser.parse(sbn)
        assertFalse(result.isGroupChat)
        assertNull(result.groupRoomName)
        assertEquals("KODEX SK하이닉스단일종목레버리지 📈", result.roomTitle)
        assertEquals("주식 가격이 5% 올랐어요(10,590원)", result.cleanText)
        assertTrue(result.messages.isEmpty())
    }

    @Test
    fun `google gemini messaging style notification should not be group chat and have clean title and text`() {
        val msg = Bundle().apply {
            putCharSequence("sender", "Gemini Spark 설정")
            putCharSequence("text", "Spark 공동작업을 위한 요청 사항이 생성되었습니다.")
            putLong("time", 1787747901853L)
        }

        val sbn = createMockSbn(
            packageName = "com.google.android.googlequicksearchbox",
            title = "Gemini Spark 설정",
            text = "Spark 공동작업을 위한 요청 사항이 생성되었습니다.",
            subText = "Gemini",
            isGroupConversation = false,
            selfDisplayName = "Gemini Spark 설정",
            messages = arrayOf(msg)
        )

        val result = MessengerNotificationParser.parse(
            sbn = sbn,
            channelName = "Deep Research",
            shortcutLabel = "Gemini"
        )
        assertFalse(result.isGroupChat)
        assertEquals("Gemini Spark 설정", result.roomTitle)
        assertEquals("Spark 공동작업을 위한 요청 사항이 생성되었습니다.", result.cleanText)
        assertEquals(1, result.messages.size)
        assertEquals("Gemini Spark 설정", result.messages[0].sender)
    }

    @Test
    fun `samsung clock upcoming alarm notification should keep title and preserve full time text`() {
        val sbn = createMockSbn(
            packageName = "com.sec.android.app.clockpackage",
            title = "곧 울릴 알람을 끌까요?",
            text = "오전 7:10"
        )

        val result = MessengerNotificationParser.parse(
            sbn = sbn,
            channelName = "곧 울릴 알람",
            tickerText = "오전 7:10"
        )

        assertFalse(result.isGroupChat)
        assertEquals("곧 울릴 알람을 끌까요?", result.roomTitle)
        assertEquals("오전 7:10", result.cleanText)
        assertTrue(result.messages.isEmpty())
    }

    @Test
    fun `gmail email notification should extract sender as title and email body cleanly without group chat flag`() {
        val sbn = createMockSbn(
            packageName = "com.google.android.gm",
            title = "OpenAI",
            text = "New sign-in to your OpenAI account",
            subText = "sunhongyi@gmail.com"
        )

        val result = MessengerNotificationParser.parse(
            sbn = sbn,
            channelName = "메일",
            tickerText = "OpenAI"
        )

        assertFalse(result.isGroupChat)
        assertEquals("OpenAI", result.roomTitle)
        assertEquals("New sign-in to your OpenAI account", result.cleanText)
        assertTrue(result.messages.isEmpty())
    }
}
