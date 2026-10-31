package com.devdooly.notificationedge.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.devdooly.notificationedge.R
import com.devdooly.notificationedge.ui.theme.*

/** Play 스토어를 여는 것 외에 APK 확인·다운로드·설치 코드는 없다. */
@Composable
internal fun AppUpdateCard(currentVersionName: String) {
    val context = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = DarkSurface), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.play_updates_title), color = CloudDancer, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.play_updates_description), color = TextSecondary)
            Text(stringResource(R.string.play_current_version, currentVersionName), color = EdgeCyan)
            OutlinedButton(onClick = {
                openExternalLink(context, "https://play.google.com/store/apps/details?id=${context.packageName}")
            }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.play_open_store)) }
        }
    }
}
