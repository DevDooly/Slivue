package com.devdooly.notificationedge.ui.theme

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.text.font.FontFamily
import com.devdooly.notificationedge.R
import com.devdooly.notificationedge.util.CustomFontManager

/** Play판은 네트워크 제공자 없이 시스템·사용자 선택 파일만 사용한다. */
enum class AppFont(val id: String, @StringRes val displayNameRes: Int, @StringRes val descriptionRes: Int) {
    SYSTEM_DEFAULT("default", R.string.font_system_default, R.string.font_system_default_description),
    SERIF("serif", R.string.font_serif, R.string.font_serif_description),
    MONOSPACE("monospace", R.string.font_monospace, R.string.font_monospace_description);

    fun toFontFamily(): FontFamily = when (this) {
        SYSTEM_DEFAULT -> FontFamily.Default
        SERIF -> FontFamily.Serif
        MONOSPACE -> FontFamily.Monospace
    }

    companion object {
        fun fromId(id: String): AppFont = entries.find { it.id.equals(id, ignoreCase = true) } ?: SYSTEM_DEFAULT
    }
}

fun resolveFontFamily(context: Context, fontId: String): FontFamily {
    if (fontId.startsWith("custom:")) CustomFontManager.loadFontFamily(context, fontId)?.let { return it }
    return AppFont.fromId(fontId).toFontFamily()
}
