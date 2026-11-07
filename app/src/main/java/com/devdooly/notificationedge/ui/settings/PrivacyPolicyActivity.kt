package com.devdooly.notificationedge.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.devdooly.notificationedge.BuildConfig
import com.devdooly.notificationedge.R
import com.devdooly.notificationedge.ui.theme.DarkBackground
import com.devdooly.notificationedge.ui.theme.NotificationEdgeTheme

/** 로컬 정책 HTML만 렌더링하며 JavaScript·네트워크·파일/콘텐츠 접근을 허용하지 않는다. */
class PrivacyPolicyActivity : ComponentActivity() {
    private var policyView: LocalPrivacyPolicyView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initialLanguage = savedInstanceState?.getString(POLICY_LANGUAGE_STATE)
            ?: if (resources.configuration.locales[0].language == "ko") "ko" else "en"
        setContent {
            NotificationEdgeTheme {
                Scaffold(
                    topBar = {
                        Surface(color = DarkBackground) {
                            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { finish() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.privacy_back)) }
                                Text(stringResource(if (BuildConfig.IS_PLAY_DISTRIBUTION) R.string.privacy_policy_title else R.string.privacy_title), modifier = Modifier.weight(1f).padding(vertical = 8.dp), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }, containerColor = DarkBackground,
                ) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        if (isPublicPolicyUrl(BuildConfig.PRIVACY_POLICY_URL)) {
                            TextButton(onClick = { openExternalLink(this@PrivacyPolicyActivity, BuildConfig.PRIVACY_POLICY_URL) }, modifier = Modifier.padding(horizontal = 12.dp)) {
                                Text(stringResource(R.string.privacy_policy_public))
                            }
                        }
                        AndroidView(
                            factory = { context ->
                                LocalPrivacyPolicyView(context, initialLanguage).also { policyView = it }
                            },
                            modifier = Modifier.fillMaxSize(),
                            onReset = null,
                            onRelease = { it.release(); if (policyView === it) policyView = null },
                        )
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        policyView?.let { outState.putString(POLICY_LANGUAGE_STATE, it.language) }
        super.onSaveInstanceState(outState)
    }

    private companion object {
        const val POLICY_LANGUAGE_STATE = "privacy_policy_language"
    }
}

internal fun isPublicPolicyUrl(value: String): Boolean = runCatching {
    val uri = Uri.parse(value)
    uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null
}.getOrDefault(false)

internal fun openExternalLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.privacy_open_failed, Toast.LENGTH_SHORT).show()
    }
}
