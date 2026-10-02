package com.devdooly.notificationedge.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.devdooly.notificationedge.R
import com.devdooly.notificationedge.ui.theme.*

/** 앱별 알림 수신과 차단 키워드를 한 번의 펼침으로 관리한다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NotificationFilterSettingsCard(
    discoveredPackages: Set<String>,
    excludedPackages: Set<String>,
    blockedKeywords: Set<String>,
    onToggleExcludedPackage: (String, Boolean) -> Unit,
    onClearDiscoveredPackages: () -> Unit,
    onAddBlockedKeyword: (String) -> Unit,
    onRemoveBlockedKeyword: (String) -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var newKeywordText by rememberSaveable { mutableStateOf("") }
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    val expandLabel = stringResource(if (isExpanded) R.string.settings_collapse else R.string.settings_expand)

    // 앱 정보는 수신 목록이 변경될 때만 읽고, 앱별 키로 아이콘 상태를 유지한다.
    val pm = remember(context) { context.packageManager }
    val discoveredAppList = remember(discoveredPackages, pm) {
        discoveredPackages.map { pkg ->
            val appName = try {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (e: Exception) {
                pkg
            }
            pkg to appName
        }.sortedWith(compareBy<Pair<String, String>> { it.second.lowercase() }.thenBy { it.first })
    }
    val sortedKeywords = remember(blockedKeywords) { blockedKeywords.sorted() }
    val submitKeyword = {
        val keyword = newKeywordText.trim()
        if (keyword.isNotEmpty()) {
            onAddBlockedKeyword(keyword)
            newKeywordText = ""
            keyboardController?.hide()
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, GlassBorder)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { stateDescription = expandLabel }
                .clickable(role = Role.Button, onClickLabel = expandLabel) { isExpanded = !isExpanded }
                .heightIn(min = 72.dp)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Icon(Icons.Default.FilterList, null, tint = EdgeCyan, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.design_filters),
                    modifier = Modifier.fillMaxWidth(),
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_filter_summary, discoveredAppList.size, blockedKeywords.size),
                    modifier = Modifier.fillMaxWidth(),
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = TextSecondary
            )
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_filter_description),
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                HorizontalDivider(color = GlassBorder)

                // 앱 목록이 길어져도 키워드 입력을 찾기 쉽도록 먼저 배치한다.
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.settings_blocked_keywords, blockedKeywords.size),
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = stringResource(R.string.settings_blocked_keywords_description),
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                OutlinedTextField(
                    value = newKeywordText,
                    onValueChange = { newKeywordText = it },
                    label = { Text(stringResource(R.string.settings_keyword_label)) },
                    placeholder = { Text(stringResource(R.string.settings_keyword_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 14.sp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EdgeCyan,
                        unfocusedBorderColor = TextMuted,
                        focusedLabelColor = EdgeCyan,
                        unfocusedLabelColor = TextSecondary,
                        focusedPlaceholderColor = TextMuted,
                        unfocusedPlaceholderColor = TextMuted,
                        cursorColor = EdgeCyan
                    ),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submitKeyword() })
                )
                Button(
                    onClick = submitKeyword,
                    enabled = newKeywordText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = EdgeCyan, contentColor = Graphite950),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.settings_add), fontWeight = FontWeight.SemiBold)
                }

                if (blockedKeywords.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_blocked_keywords_empty),
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        sortedKeywords.forEach { keyword ->
                            key(keyword) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Graphite900,
                                    border = BorderStroke(1.dp, GlassBorder)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(start = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = keyword,
                                            color = EdgeCyan,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            // 남는 폭 안에서 긴 키워드를 줄바꿈하고 삭제 버튼 폭을 확보한다.
                                            modifier = Modifier.weight(1f, fill = false).padding(vertical = 8.dp)
                                        )
                                        IconButton(
                                            onClick = { onRemoveBlockedKeyword(keyword) },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = stringResource(R.string.settings_remove_keyword, keyword),
                                                tint = EdgeCyan,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = GlassBorder)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_received_apps, discoveredAppList.size),
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (discoveredAppList.isNotEmpty()) {
                        TextButton(
                            onClick = onClearDiscoveredPackages,
                            modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)
                        ) {
                            Text(stringResource(R.string.settings_clear_history), color = EdgeCyan, fontSize = 12.sp)
                        }
                    }
                }

                if (discoveredAppList.isEmpty()) {
                    Surface(
                        color = Graphite900,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.settings_received_apps_empty),
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        discoveredAppList.forEach { (pkg, appName) ->
                            key(pkg) {
                                val isExcluded = excludedPackages.contains(pkg)
                                val appState = stringResource(
                                    if (isExcluded) R.string.settings_app_excluded else R.string.settings_app_receiving
                                )
                                val iconBitmap = remember(pkg, pm) {
                                    try {
                                        pm.getApplicationIcon(pkg).toBitmap(72, 72).asImageBitmap()
                                    } catch (e: Exception) {
                                        null
                                    }
                                }

                                Surface(
                                    color = Graphite900,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, GlassBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .semantics {
                                                contentDescription = "$appName, $pkg"
                                                stateDescription = appState
                                            }
                                            .toggleable(
                                                value = !isExcluded,
                                                role = Role.Switch,
                                                onValueChange = { isReceiving -> onToggleExcludedPackage(pkg, !isReceiving) }
                                            )
                                            .heightIn(min = 64.dp)
                                            .padding(horizontal = 12.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (iconBitmap != null) {
                                            Image(
                                                bitmap = iconBitmap,
                                                contentDescription = null,
                                                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                                            )
                                            Spacer(Modifier.width(12.dp))
                                        }
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text(
                                                text = appName,
                                                color = TextPrimary,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 14.sp,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(text = appState, color = if (isExcluded) TextSecondary else EdgeCyan, fontSize = 12.sp)
                                            Text(
                                                text = pkg,
                                                color = TextMuted,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Switch(
                                            checked = !isExcluded,
                                            // 행 전체가 단일 접근성 스위치이며, 스위치 자체는 시각 표시만 담당한다.
                                            onCheckedChange = null,
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = EdgeCyan,
                                                checkedTrackColor = EdgeCyan.copy(alpha = 0.3f),
                                                uncheckedThumbColor = TextMuted,
                                                uncheckedTrackColor = GlassBorder
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
