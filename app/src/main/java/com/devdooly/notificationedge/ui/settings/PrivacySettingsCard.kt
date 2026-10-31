package com.devdooly.notificationedge.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.devdooly.notificationedge.BuildConfig
import com.devdooly.notificationedge.R
import com.devdooly.notificationedge.ui.theme.*

@Composable
internal fun NotificationDisclosureDialog(onAccept: () -> Unit, onLater: () -> Unit, onReadPolicy: () -> Unit) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(stringResource(R.string.privacy_disclosure_title)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.privacy_disclosure_data))
                Text(stringResource(R.string.privacy_disclosure_purpose))
                Text(stringResource(if (BuildConfig.IS_PLAY_DISTRIBUTION) R.string.privacy_disclosure_play else R.string.privacy_disclosure_direct))
                Text(stringResource(R.string.privacy_disclosure_control))
                TextButton(onClick = onReadPolicy, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.privacy_policy_open)) }
            }
        },
        confirmButton = { TextButton(onClick = onAccept, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.privacy_accept)) } },
        dismissButton = { TextButton(onClick = onLater, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.privacy_later)) } },
    )
}

@Composable
internal fun PrivacySettingsCard(
    hasConsent: Boolean,
    isPaused: Boolean,
    onReviewAccess: () -> Unit,
    onStop: () -> Unit,
    onResume: () -> Unit,
    onRevoke: () -> Unit,
    onReadPolicy: () -> Unit,
    onClearHistory: () -> Unit,
    onManageData: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = DarkSurface), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.privacy_title), color = CloudDancer, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(when {
                !hasConsent -> R.string.privacy_processing_not_consented
                isPaused -> R.string.privacy_processing_paused
                else -> R.string.privacy_processing_active
            }), color = TextSecondary)
            if (!hasConsent) {
                Button(onClick = onReviewAccess, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.privacy_review)) }
            } else {
                OutlinedButton(onClick = if (isPaused) onResume else onStop, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(if (isPaused) R.string.privacy_resume else R.string.overlay_service_stop))
                }
                TextButton(onClick = onRevoke, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.privacy_revoke)) }
            }
            TextButton(onClick = onReadPolicy, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.privacy_policy_open)) }
            TextButton(onClick = onClearHistory, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.privacy_clear_history)) }
            Text(stringResource(R.string.privacy_delete_description), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onManageData, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.privacy_delete_data)) }
        }
    }
}

@Composable
internal fun PlayPreparationCard() {
    Card(colors = CardDefaults.cardColors(containerColor = Graphite900), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.play_preparation_title), color = EdgeCyan, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.play_preparation_description), color = TextSecondary)
        }
    }
}
