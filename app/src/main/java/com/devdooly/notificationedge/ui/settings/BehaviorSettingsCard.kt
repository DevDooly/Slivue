package com.devdooly.notificationedge.ui.settings

import com.devdooly.notificationedge.ui.theme.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.devdooly.notificationedge.R
import com.devdooly.notificationedge.data.model.AppSettings

@Composable
internal fun BehaviorSettingsCard(
    settings: AppSettings,
    onPauseMediaOnOpenChange: (Boolean) -> Unit,
    onHapticFeedbackChange: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            SettingsToggleRow(
                title = stringResource(R.string.behavior_pause_youtube),
                description = stringResource(R.string.behavior_pause_youtube_description),
                checked = settings.pauseMediaOnOpen,
                onCheckedChange = onPauseMediaOnOpenChange
            )

            Spacer(modifier = Modifier.height(12.dp))

            SettingsToggleRow(
                title = stringResource(R.string.behavior_haptic_feedback),
                description = stringResource(R.string.behavior_haptic_feedback_description),
                checked = settings.hapticFeedbackEnabled,
                onCheckedChange = onHapticFeedbackChange
            )
        }
    }
}
