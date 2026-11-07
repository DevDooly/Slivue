package com.devdooly.notificationedge.ui.settings

import android.content.Context
import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.URLSpan
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.text.HtmlCompat
import com.devdooly.notificationedge.R
import com.devdooly.notificationedge.util.AppLog
import java.io.IOException
import java.util.Locale

/** WebView가 없는 순정 이미지에서도 같은 로컬 정책을 읽을 수 있는 설치별 뷰다. */
internal class LocalPrivacyPolicyView(
    context: Context,
    initialLanguage: String,
    providerAvailable: () -> Boolean = { WebView.getCurrentWebViewPackage() != null },
    createWebView: (Context) -> WebView = { WebView(it) },
) : FrameLayout(context) {
    var language: String = policyLanguage(initialLanguage)
        private set

    var isUsingNativeFallback: Boolean = false
        private set

    private var browser: WebView? = null
    private var nativeBody: TextView? = null
    private var nativeScroll: ScrollView? = null
    private var koreanButton: Button? = null
    private var englishButton: Button? = null

    init {
        try {
            if (providerAvailable()) {
                val webView = createWebView(context)
                browser = webView
                configureBrowser(webView)
                addView(webView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            } else {
                showNativeFallback()
            }
        } catch (error: RuntimeException) {
            useNativeAfterInitializationFailure(error)
        } catch (error: LinkageError) {
            useNativeAfterInitializationFailure(error)
        }
        selectLanguage(language)
    }

    private fun configureBrowser(webView: WebView) {
        with(webView.settings) {
            javaScriptEnabled = false
            domStorageEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            blockNetworkLoads = true
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (uri.scheme == "https" && uri.host == "slivue.invalid" &&
                    uri.path in setOf("/privacy/ko.html", "/privacy/en.html")) {
                    selectLanguage(if (uri.lastPathSegment == "ko.html") "ko" else "en")
                }
                // 로컬 언어 전환 외 모든 탐색은 차단한다. 외부 링크는 Activity의 명시적 버튼만 사용한다.
                return true
            }
        }
    }

    fun selectLanguage(value: String) {
        language = policyLanguage(value)
        val html = readPolicyHtml(context, language)
        val webView = browser
        if (webView != null) {
            try {
                webView.loadDataWithBaseURL("https://slivue.invalid/privacy/", html, "text/html", "UTF-8", null)
                return
            } catch (error: RuntimeException) {
                useNativeAfterInitializationFailure(error)
            } catch (error: LinkageError) {
                useNativeAfterInitializationFailure(error)
            }
        }
        nativeBody?.apply {
            textLocale = Locale.forLanguageTag(language)
            text = policyHtmlAsNativeText(html)
        }
        koreanButton?.isEnabled = language != "ko"
        englishButton?.isEnabled = language != "en"
        nativeScroll?.scrollTo(0, 0)
    }

    private fun useNativeAfterInitializationFailure(error: Throwable) {
        AppLog.warning("PrivacyPolicy", "WebView를 사용할 수 없어 로컬 정책을 기본 읽기 화면으로 표시합니다.", error)
        releaseBrowser()
        showNativeFallback()
    }

    private fun showNativeFallback() {
        isUsingNativeFallback = true
        removeAllViews()
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(11, 24, 48))
        }
        column.addView(TextView(context).apply {
            setText(R.string.privacy_native_reader)
            setTextColor(Color.rgb(178, 192, 212))
            textSize = 14f
            setPadding(dp(20), dp(12), dp(20), dp(4))
        }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val languages = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), 0, dp(12), dp(4))
        }
        koreanButton = languageButton("ko", R.string.privacy_language_korean)
        englishButton = languageButton("en", R.string.privacy_language_english)
        languages.addView(koreanButton, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        languages.addView(englishButton, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        column.addView(languages, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        nativeBody = TextView(context).apply {
            tag = NATIVE_BODY_TAG
            textSize = 18f
            setTextColor(Color.rgb(231, 238, 249))
            setTextIsSelectable(true)
            setLineSpacing(dp(4).toFloat(), 1.1f)
            setPadding(dp(20), dp(8), dp(20), dp(48))
            // HTML 링크는 일반 텍스트로만 읽는다. 자동 링크나 네트워크 로드는 하지 않는다.
            autoLinkMask = 0
            linksClickable = false
        }
        nativeScroll = ScrollView(context).apply {
            tag = NATIVE_SCROLL_TAG
            addView(nativeBody, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        column.addView(nativeScroll, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private fun languageButton(value: String, label: Int): Button = Button(context).apply {
        tag = if (value == "ko") KOREAN_BUTTON_TAG else ENGLISH_BUTTON_TAG
        setText(label)
        minimumHeight = dp(48)
        setOnClickListener { selectLanguage(value) }
    }

    fun release() {
        releaseBrowser()
        removeAllViews()
    }

    private fun releaseBrowser() {
        val webView = browser ?: return
        browser = null
        removeView(webView)
        try {
            webView.stopLoading()
            webView.destroy()
        } catch (_: RuntimeException) {
            // 초기화가 끝나지 않은 제공자의 해제 실패가 정책 화면을 종료시키지 않게 한다.
        } catch (_: LinkageError) {
            // 잘못된 시스템 제공자에서도 네이티브 대체 경로는 유지한다.
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        internal const val NATIVE_BODY_TAG = "slivue_privacy_native_body"
        internal const val NATIVE_SCROLL_TAG = "slivue_privacy_native_scroll"
        internal const val KOREAN_BUTTON_TAG = "slivue_privacy_language_ko"
        internal const val ENGLISH_BUTTON_TAG = "slivue_privacy_language_en"
    }
}

internal fun policyLanguage(value: String): String = if (value == "ko") "ko" else "en"

private fun readPolicyHtml(context: Context, language: String): String = try {
    context.assets.open("privacy/${policyLanguage(language)}.html").bufferedReader(Charsets.UTF_8).use { it.readText() }
} catch (_: IOException) {
    "<html><body><p>${TextUtils.htmlEncode(context.getString(R.string.privacy_asset_error))}</p></body></html>"
}

/** CSS·문서 메타·탐색 링크 대신 정책 본문 전체를 네이티브 텍스트로 표시한다. */
internal fun policyHtmlAsNativeText(html: String): Spanned {
    val options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    val body = Regex("<body\\b[^>]*>(.*?)</body\\s*>", options).find(html)?.groupValues?.get(1) ?: html
    val content = body
        .replace(Regex("<(head|style|script|nav)\\b[^>]*>.*?</\\1\\s*>", options), "")
        .replace(Regex("</?(?:main|aside|section|footer|dl|dt|dd)\\b[^>]*>", RegexOption.IGNORE_CASE)) {
            if (it.value.startsWith("</")) "</div>" else "<div>"
        }
    return SpannableStringBuilder(HtmlCompat.fromHtml(content, HtmlCompat.FROM_HTML_MODE_LEGACY)).apply {
        getSpans(0, length, URLSpan::class.java).forEach(::removeSpan)
    }
}
