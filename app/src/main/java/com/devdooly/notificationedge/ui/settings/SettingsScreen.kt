package com.devdooly.notificationedge.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
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
import com.devdooly.notificationedge.util.PrivacyConsent
import com.devdooly.notificationedge.data.repository.NotificationRepository

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val privacyState by viewModel.privacyState.collectAsStateWithLifecycle()
    var showDisclosure by rememberSaveable { mutableStateOf(false) }
    var pendingHandleEnable by rememberSaveable { mutableStateOf(false) }

    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasNotificationPermission by remember { mutableStateOf(PrivacyConsent.hasNotificationAccess(context)) }
    var isIgnoringBatteryOptimizations by remember { mutableStateOf(isBatteryOptimized(context)) }
    var hasPostPermission by remember { mutableStateOf(hasPostNotificationPermission(context)) }
    val postPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPostPermission = hasPostNotificationPermission(context)
    }
    val openNotificationAccess = { openNotificationAccessSettings(context) }
    val readPolicy = { context.startActivity(Intent(context, PrivacyPolicyActivity::class.java)); Unit }

    if (showDisclosure) {
        NotificationDisclosureDialog(
            onAccept = {
                if (viewModel.acceptNotificationDisclosure()) {
                    if (pendingHandleEnable) viewModel.updateServiceEnabled(true)
                    pendingHandleEnable = false
                    showDisclosure = false
                    openNotificationAccess()
                } else Toast.makeText(context, R.string.privacy_save_failed, Toast.LENGTH_LONG).show()
            },
            onLater = { showDisclosure = false; pendingHandleEnable = false },
            onReadPolicy = readPolicy,
        )
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = Settings.canDrawOverlays(context)
                viewModel.reconcileNotificationAccess()
                hasNotificationPermission = PrivacyConsent.hasNotificationAccess(context)
                isIgnoringBatteryOptimizations = isBatteryOptimized(context)
                hasPostPermission = hasPostNotificationPermission(context)
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
            if (BuildConfig.IS_PLAY_DISTRIBUTION && BuildConfig.IS_PLAY_PREPARATION_BUILD) {
                item(key = "play-preparation") { PlayPreparationCard() }
            }
            item(key = "master-switch") {
                MasterSwitchCard(
                    enabled = settings.isServiceEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled && !privacyState.hasCurrentConsent) { pendingHandleEnable = true; showDisclosure = true }
                        else viewModel.updateServiceEnabled(enabled)
                    }
                )
            }

            item(key = "permissions") {
                PermissionStatusCard(
                    hasOverlay = hasOverlayPermission,
                    hasNotification = hasNotificationPermission,
                    hasBatteryOpt = isIgnoringBatteryOptimizations,
                    hasPostNotification = hasPostPermission,
                    onGrantPostNotification = {
                        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            postPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                        }
                    },
                    onGrantOverlay = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    },
                    onGrantNotification = {
                        if (privacyState.hasCurrentConsent) openNotificationAccess() else showDisclosure = true
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

            item(key = "privacy") {
                PrivacySettingsCard(
                    hasConsent = privacyState.hasCurrentConsent,
                    isPaused = privacyState.isPaused,
                    onReviewAccess = { showDisclosure = true },
                    onStop = viewModel::pauseNotificationProcessing,
                    onResume = {
                        if (!hasNotificationPermission) openNotificationAccess() else viewModel.resumeNotificationProcessing()
                    },
                    onRevoke = viewModel::revokeNotificationDisclosure,
                    onReadPolicy = readPolicy,
                    onClearHistory = {
                        NotificationRepository.clearMemoryOnly()
                        Toast.makeText(context, R.string.privacy_clear_history_done, Toast.LENGTH_SHORT).show()
                    },
                    onManageData = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) },
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
                DistributionDiagnosticsSection(settings.diagnosticModeEnabled, viewModel::updateDiagnosticModeEnabled)
            }

            // 다운로드 중 메뉴를 접더라도 업데이트 작업의 Composition을 제거하지 않는다.
            item(key = "app-update") {
                AppUpdateCard(currentVersionName = BuildConfig.VERSION_NAME)
            }
            item(key = "app-info") { AppInfoCard() }
        }
    }
}

private fun hasPostNotificationPermission(context: Context): Boolean =
    (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
        NotificationManagerCompat.from(context).areNotificationsEnabled()

private fun isBatteryOptimized(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }
    return true
}
