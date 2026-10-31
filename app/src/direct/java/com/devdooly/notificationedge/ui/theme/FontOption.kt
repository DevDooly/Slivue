package com.devdooly.notificationedge.ui.theme

// Google Fonts 제공자는 직접 배포판에만 포함한다.

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.devdooly.notificationedge.R
import com.devdooly.notificationedge.util.CustomFontManager

private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

enum class AppFont(
    val id: String,
    @StringRes val displayNameRes: Int,
    @StringRes val descriptionRes: Int
) {
    SYSTEM_DEFAULT(
        id = "default",
        displayNameRes = R.string.font_system_default,
        descriptionRes = R.string.font_system_default_description
    ),
    NOTO_SANS_KR(
        id = "noto_sans_kr",
        displayNameRes = R.string.font_noto_sans_kr,
        descriptionRes = R.string.font_noto_sans_kr_description
    ),
    IBM_PLEX_SANS_KR(
        id = "ibm_plex_sans_kr",
        displayNameRes = R.string.font_ibm_plex_sans_kr,
        descriptionRes = R.string.font_ibm_plex_sans_kr_description
    ),
    NANUM_GOTHIC(
        id = "nanum_gothic",
        displayNameRes = R.string.font_nanum_gothic,
        descriptionRes = R.string.font_nanum_gothic_description
    ),
    GOWUN_DODUM(
        id = "gowun_dodum",
        displayNameRes = R.string.font_gowun_dodum,
        descriptionRes = R.string.font_gowun_dodum_description
    ),
    SERIF(
        id = "serif",
        displayNameRes = R.string.font_serif,
        descriptionRes = R.string.font_serif_description
    ),
    MONOSPACE(
        id = "monospace",
        displayNameRes = R.string.font_monospace,
        descriptionRes = R.string.font_monospace_description
    );

    fun toFontFamily(): FontFamily {
        return when (this) {
            SYSTEM_DEFAULT -> FontFamily.Default
            NOTO_SANS_KR -> createGoogleFontFamily("Noto Sans KR")
            IBM_PLEX_SANS_KR -> createGoogleFontFamily("IBM Plex Sans KR")
            NANUM_GOTHIC -> createGoogleFontFamily("Nanum Gothic")
            GOWUN_DODUM -> createGoogleFontFamily("Gowun Dodum")
            SERIF -> FontFamily.Serif
            MONOSPACE -> FontFamily.Monospace
        }
    }

    companion object {
        fun fromId(id: String): AppFont {
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: SYSTEM_DEFAULT
        }
    }
}

fun resolveFontFamily(context: Context, fontId: String): FontFamily {
    if (fontId.startsWith("custom:")) {
        val customFamily = CustomFontManager.loadFontFamily(context, fontId)
        if (customFamily != null) {
            return customFamily
        }
    }
    return AppFont.fromId(fontId).toFontFamily()
}

private fun createGoogleFontFamily(fontName: String): FontFamily {
    return FontFamily(
        Font(
            googleFont = GoogleFont(fontName),
            fontProvider = provider
        )
    )
}
