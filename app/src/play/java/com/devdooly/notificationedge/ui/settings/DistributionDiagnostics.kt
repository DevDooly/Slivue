package com.devdooly.notificationedge.ui.settings

import androidx.compose.runtime.Composable

/** Play판에는 알림 원문 진단 내보내기 UI를 컴파일하지 않는다. */
@Composable
internal fun DistributionDiagnosticsSection(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) = Unit
