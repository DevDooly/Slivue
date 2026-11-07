package com.devdooly.notificationedge.ui.settings

import android.app.Application
import android.content.res.Configuration
import android.text.style.URLSpan
import android.view.View
import android.webkit.WebView
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34])
class LocalPrivacyPolicyViewTest {
    private val application get() = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `제공자가 없으면 WebView를 생성하지 않고 같은 한국어 정책을 읽는다`() {
        var attemptedBrowserCreation = false
        val view = LocalPrivacyPolicyView(application, "ko", providerAvailable = { false }, createWebView = {
            attemptedBrowserCreation = true
            error("없는 제공자를 생성하면 안 됩니다.")
        })
        try {
            assertTrue(view.isUsingNativeFallback)
            assertFalse(attemptedBrowserCreation)
            assertEquals("ko", view.language)
            assertTrue(body(view).text.contains("Slivue"))
            assertTrue(body(view).text.contains("알림"))
            assertNotNull(view.findViewWithTag<ScrollView>(LocalPrivacyPolicyView.NATIVE_SCROLL_TAG))
            assertFalse(view.findViewWithTag<Button>(LocalPrivacyPolicyView.KOREAN_BUTTON_TAG).isEnabled)
            assertTrue(view.findViewWithTag<Button>(LocalPrivacyPolicyView.ENGLISH_BUTTON_TAG).isEnabled)
        } finally { view.release() }
    }

    @Test
    fun `제공자 생성 예외도 영문 네이티브 화면으로 대체한다`() {
        val view = LocalPrivacyPolicyView(application, "en", providerAvailable = { true }, createWebView = {
            throw android.util.AndroidRuntimeException("No WebView installed")
        })
        try {
            assertTrue(view.isUsingNativeFallback)
            assertTrue(body(view).text.contains("Slivue"))
            assertTrue(body(view).text.contains("notification", ignoreCase = true))
        } finally { view.release() }
    }

    @Test
    fun `잘못된 제공자 클래스 조회도 앱을 종료하지 않는다`() {
        val view = LocalPrivacyPolicyView(application, "ko", providerAvailable = {
            throw NoClassDefFoundError("Broken WebView provider")
        })
        try {
            assertTrue(view.isUsingNativeFallback)
            assertTrue(body(view).text.contains("개인정보"))
        } finally { view.release() }
    }

    @Test
    fun `네이티브 언어 버튼으로 양쪽 로컬 전문을 전환한다`() {
        val view = LocalPrivacyPolicyView(application, "ko", providerAvailable = { false })
        try {
            val koreanText = body(view).text.toString()
            view.findViewWithTag<Button>(LocalPrivacyPolicyView.ENGLISH_BUTTON_TAG).performClick()
            assertEquals("en", view.language)
            assertTrue(body(view).text.contains("notification", ignoreCase = true))
            assertFalse(view.findViewWithTag<Button>(LocalPrivacyPolicyView.ENGLISH_BUTTON_TAG).isEnabled)
            view.findViewWithTag<Button>(LocalPrivacyPolicyView.KOREAN_BUTTON_TAG).performClick()
            assertEquals("ko", view.language)
            assertEquals(koreanText, body(view).text.toString())
        } finally { view.release() }
    }

    @Test
    fun `본문 변환은 정책과 말미를 보존하고 CSS와 실행 링크는 남기지 않는다`() {
        val converted = policyHtmlAsNativeText("""
            <html><head><title>Hidden title</title><style>body { color: red; }</style></head><body>
            <nav><a href="en.html">English navigation</a></nav>
            <main><h1>Policy heading</h1><p>First &amp; second</p>
            <dl><dt>Operator</dt><dd>Pending</dd></dl>
            <p><a href="https://example.invalid/policy">Visible link label</a></p></main>
            <footer>Policy end</footer></body></html>
        """.trimIndent())
        val plainText = converted.toString()
        for (text in listOf("Policy heading", "First & second", "Operator", "Pending", "Visible link label", "Policy end")) {
            assertTrue("본문이 누락되었습니다: $text", plainText.contains(text))
        }
        assertFalse(plainText.contains("Hidden title"))
        assertFalse(plainText.contains("color: red"))
        assertFalse(plainText.contains("English navigation"))
        assertEquals(0, converted.getSpans(0, converted.length, URLSpan::class.java).size)
    }

    @Test
    fun `사용 가능한 WebView는 기존 네트워크와 파일 차단을 유지한다`() {
        val browser = WebView(application)
        val view = LocalPrivacyPolicyView(application, "ko", providerAvailable = { true }, createWebView = { browser })
        try {
            assertFalse(view.isUsingNativeFallback)
            assertSame(view, browser.parent)
            with(browser.settings) {
                assertFalse(javaScriptEnabled)
                assertFalse(domStorageEnabled)
                assertFalse(allowFileAccess)
                assertFalse(allowContentAccess)
                assertTrue(blockNetworkLoads)
            }
            view.selectLanguage("en")
            assertEquals("en", view.language)
        } finally { view.release() }
        assertEquals(0, view.childCount)
    }

    @Test
    fun `큰 글씨와 좁은 화면에서도 전문을 스크롤할 수 있다`() {
        val config = Configuration(application.resources.configuration).apply { fontScale = 2f }
        val context = application.createConfigurationContext(config)
        val view = LocalPrivacyPolicyView(context, "ko", providerAvailable = { false })
        try {
            val density = context.resources.displayMetrics.density
            val width = (320 * density).toInt()
            val height = (480 * density).toInt()
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, width, height)
            val scroll = view.findViewWithTag<ScrollView>(LocalPrivacyPolicyView.NATIVE_SCROLL_TAG)
            assertTrue(scroll.height > 0)
            scroll.scrollTo(0, Int.MAX_VALUE)
            assertTrue("좁은 큰 글씨 화면에서도 전문의 끝으로 이동할 수 있어야 합니다.", scroll.scrollY > 0)
            assertTrue(view.findViewWithTag<Button>(LocalPrivacyPolicyView.ENGLISH_BUTTON_TAG).height > 0)
        } finally { view.release() }
    }

    private fun body(view: LocalPrivacyPolicyView): TextView =
        view.findViewWithTag(LocalPrivacyPolicyView.NATIVE_BODY_TAG)
}
