package com.devdooly.notificationedge.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.devdooly.notificationedge.BuildConfig
import com.devdooly.notificationedge.R
import com.devdooly.notificationedge.ui.theme.DarkBackground
import com.devdooly.notificationedge.data.model.EdgeSide
import com.devdooly.notificationedge.util.AppLog

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasNotificationPermission by remember { mutableStateOf(isNotificationServiceEnabled(context)) }
    var isIgnoringBatteryOptimizations by remember { mutableStateOf(isBatteryOptimized(context)) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = Settings.canDrawOverlays(context)
                hasNotificationPermission = isNotificationServiceEnabled(context)
                isIgnoringBatteryOptimizations = isBatteryOptimized(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            // 고정 높이의 툴바 대신 내용 높이를 사용해 큰 시스템 글씨에서도 브랜드가 잘리지 않는다.
            Surface(color = DarkBackground) {
                Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 12.dp)) {
                    SlivueSettingsBrand()
                }
            }
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "master-switch") {
                MasterSwitchCard(
                    enabled = settings.isServiceEnabled,
                    onCheckedChange = viewModel::updateServiceEnabled
                )
            }

            item(key = "permissions") {
                PermissionStatusCard(
                    hasOverlay = hasOverlayPermission,
                    hasNotification = hasNotificationPermission,
                    hasBatteryOpt = isIgnoringBatteryOptimizations,
                    onGrantOverlay = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    },
                    onGrantNotification = {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        context.startActivity(intent)
                    },
                    onGrantBattery = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            try {
                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                AppLog.warning("SettingsScreen", "배터리 최적화 설정 화면 열기 실패", e)
                            }
                        }
                    }
                )
            }

            item(key = "notifications-heading") {
                SettingsGroupHeading(stringResource(R.string.design_notifications_group))
            }

            // 카드 자체의 펼침만 사용한다. 필터 조작까지 추가 메뉴를 거치지 않는다.
            item(key = "notification-filter") {
                NotificationFilterSettingsCard(
                    discoveredPackages = settings.discoveredAppPackages,
                    excludedPackages = settings.excludedPackages,
                    blockedKeywords = settings.blockedKeywords,
                    onToggleExcludedPackage = viewModel::setPackageExcluded,
                    onClearDiscoveredPackages = viewModel::clearDiscoveredPackages,
                    onAddBlockedKeyword = viewModel::addBlockedKeyword,
                    onRemoveBlockedKeyword = viewModel::removeBlockedKeyword
                )
            }

            item(key = "appearance-heading") {
                SettingsGroupHeading(stringResource(R.string.design_appearance_group))
            }

            item(key = "handle-preview") {
                HandlePreviewCard(settings) {
                    context.startActivity(Intent(context, com.devdooly.notificationedge.ui.OpenPanelActivity::class.java))
                }
            }

            item(key = "edge-handle") {
                SettingsSection(
                    stringResource(R.string.design_handle_style),
                    Icons.Default.Tune,
                    summary = stringResource(
                        R.string.design_handle_summary,
                        stringResource(if (settings.edgeSide == EdgeSide.LEFT) R.string.design_side_left else R.string.design_side_right),
                        settings.handleWidthDp, settings.handleHeightDp, settings.panelWidthDp
                    )
                ) {
                    EdgeHandleSettingsCard(
                        settings = settings,
                        onSideChange = viewModel::updateEdgeSide,
                        onPositionChange = viewModel::updateHandlePositionRatio,
                        onWidthChange = viewModel::updateHandleWidthDp,
                        onHeightChange = viewModel::updateHandleHeightDp,
                        onPanelWidthChange = viewModel::updatePanelWidthDp,
                        onAutoDismissToggle = viewModel::updateAutoDismissOnOpen,
                        onColorChange = viewModel::updateHandleColor,
                        onAlphaChange = viewModel::updateHandleAlpha,
                        onVisibleToggle = viewModel::updateHandleVisible
                    )
                }
            }

            item(key = "edge-lighting") {
                SettingsSection(
                    stringResource(R.string.design_lighting),
                    Icons.Default.Lightbulb,
                    summary = stringResource(if (settings.isEdgeLightingEnabled) R.string.design_lighting_on else R.string.design_lighting_off)
                ) {
                    EdgeLightingSettingsCard(
                        settings = settings,
                        onLightingToggle = viewModel::updateEdgeLightingEnabled,
                        onColorChange = viewModel::updateEdgeLightingColor,
                        onCornerRadiusChange = viewModel::updateEdgeLightingCornerRadiusDp,
                        onTestTrigger = viewModel::emitTestNotification
                    )
                }
            }

            item(key = "font") {
                FontSettingsCard(
                    selectedFontId = settings.selectedFont,
                    onFontSelected = viewModel::updateSelectedFont
                )
            }

            item(key = "behavior-heading") {
                SettingsGroupHeading(stringResource(R.string.design_behavior_group))
            }

            item(key = "panel-behavior") {
                SettingsSection(
                    stringResource(R.string.design_behavior), Icons.Default.TouchApp,
                    summary = stringResource(R.string.design_behavior_summary)
                ) {
                    BehaviorSettingsCard(
                        settings = settings,
                        onPauseMediaOnOpenChange = viewModel::updatePauseMediaOnOpen,
                        onHapticFeedbackChange = viewModel::updateHapticEnabled
                    )
                }
            }

            item(key = "integrations") {
                SettingsSection(
                    stringResource(R.string.design_integrations), Icons.Default.Link,
                    summary = stringResource(R.string.design_integrations_summary)
                ) {
                    GoodLockIntegrationCard(
                        launchDirectToPanel = settings.launchDirectToPanel,
                        onToggleLaunchDirect = viewModel::updateLaunchDirectToPanel,
                        externalControlEnabled = settings.externalControlEnabled,
                        onToggleExternalControl = viewModel::updateExternalControlEnabled,
                        onTestOpenPanel = {
                            val intent = Intent(context, com.devdooly.notificationedge.ui.OpenPanelActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }
                    )
                }
            }

            item(key = "about-heading") {
                SettingsGroupHeading(stringResource(R.string.design_about_group))
            }

            item(key = "diagnostics") {
                SettingsSection(
                    stringResource(R.string.design_diagnostics), Icons.Default.BugReport,
                    summary = stringResource(if (settings.diagnosticModeEnabled) R.string.design_diagnostics_on else R.string.design_diagnostics_off)
                ) {
                    NotificationDebugDumpCard(
                        enabled = settings.diagnosticModeEnabled,
                        onEnabledChange = viewModel::updateDiagnosticModeEnabled
                    )
                }
            }

            // 다운로드 중 메뉴를 접더라도 업데이트 작업의 Composition을 제거하지 않는다.
            item(key = "app-update") {
                AppUpdateCard(currentVersionName = BuildConfig.VERSION_NAME)
            }
            item(key = "app-info") { AppInfoCard() }
        }
    }
}

private fun isNotificationServiceEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return flat != null && flat.contains(context.packageName)
}

private fun isBatteryOptimized(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }
    return true
}
