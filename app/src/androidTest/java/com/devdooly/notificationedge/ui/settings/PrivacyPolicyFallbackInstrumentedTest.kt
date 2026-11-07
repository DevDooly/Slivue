package com.devdooly.notificationedge.ui.settings

import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 실제 제공자 상태나 시스템 설정을 바꾸지 않고 네이티브 대체 읽기를 검증한다. */
@RunWith(AndroidJUnit4::class)
class PrivacyPolicyFallbackInstrumentedTest {
    @Test
    fun nativeReader_showsBothLocalPoliciesWithoutCreatingWebView() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            var attemptedBrowserCreation = false
            val context = instrumentation.targetContext
            val reader = LocalPrivacyPolicyView(context, "ko", providerAvailable = { false }, createWebView = {
                attemptedBrowserCreation = true
                error("네이티브 대체 화면은 WebView를 생성하면 안 됩니다.")
            })
            try {
                assertTrue(reader.isUsingNativeFallback)
                assertFalse(attemptedBrowserCreation)
                val body = reader.findViewWithTag<TextView>(LocalPrivacyPolicyView.NATIVE_BODY_TAG)
                val korean = body.text.toString()
                assertTrue(korean.contains("Slivue"))
                assertTrue(korean.contains("알림"))
                reader.findViewWithTag<Button>(LocalPrivacyPolicyView.ENGLISH_BUTTON_TAG).performClick()
                assertEquals("en", reader.language)
                assertTrue(body.text.contains("notification", ignoreCase = true))
                reader.findViewWithTag<Button>(LocalPrivacyPolicyView.KOREAN_BUTTON_TAG).performClick()
                assertEquals(korean, body.text.toString())

                val density = context.resources.displayMetrics.density
                val width = (320 * density).toInt()
                val height = (480 * density).toInt()
                reader.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                reader.layout(0, 0, width, height)
                val scroll = reader.findViewWithTag<ScrollView>(LocalPrivacyPolicyView.NATIVE_SCROLL_TAG)
                scroll.scrollTo(0, Int.MAX_VALUE)
                assertTrue("네이티브 전문은 작은 화면에서도 스크롤할 수 있어야 합니다.", scroll.scrollY > 0)
            } finally { reader.release() }
        }
    }
}
