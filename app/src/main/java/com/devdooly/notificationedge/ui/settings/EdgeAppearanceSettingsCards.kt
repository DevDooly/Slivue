package com.devdooly.notificationedge.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devdooly.notificationedge.R
import com.devdooly.notificationedge.data.model.AppSettings
import com.devdooly.notificationedge.data.model.EdgeSide
import com.devdooly.notificationedge.ui.theme.*
@Composable
internal fun EdgeHandleSettingsCard(
    settings: AppSettings,
    onSideChange: (EdgeSide) -> Unit,
    onPositionChange: (Float) -> Unit,
    onWidthChange: (Int) -> Unit,
    onHeightChange: (Int) -> Unit,
    onPanelWidthChange: (Int) -> Unit,
    onAutoDismissToggle: (Boolean) -> Unit,
    onColorChange: (Long) -> Unit,
    onAlphaChange: (Float) -> Unit,
    onVisibleToggle: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.appearance_handle_layout),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(14.dp))

            // 패널 가로 너비 (5dp 단위 조절)
            Text(
                stringResource(R.string.appearance_panel_width, settings.panelWidthDp),
                color = TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Slider(
                value = settings.panelWidthDp.toFloat(),
                onValueChange = { onPanelWidthChange(it.toInt()) },
                valueRange = 220f..360f,
                steps = 27,
                colors = SliderDefaults.colors(
                    thumbColor = EdgeCyan,
                    activeTrackColor = EdgeCyan
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 채팅방 이동 시 해당 알림 자동 삭제 (기본값 ON)
            SettingsToggleRow(
                title = stringResource(R.string.appearance_auto_dismiss),
                description = stringResource(R.string.appearance_auto_dismiss_description),
                checked = settings.autoDismissOnOpen,
                onCheckedChange = onAutoDismissToggle
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 핸들 보이기 / 숨기기 (제스처 전용)
            SettingsToggleRow(
                title = stringResource(R.string.appearance_handle_visible),
                description = stringResource(R.string.appearance_handle_visible_description),
                checked = settings.isHandleVisible,
                onCheckedChange = onVisibleToggle
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 좌 / 우 선택
            Text(stringResource(R.string.appearance_handle_side), color = TextSecondary, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onSideChange(EdgeSide.LEFT) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (settings.edgeSide == EdgeSide.LEFT) EdgeCyan else DarkSurfaceVariant
                    ),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        stringResource(R.string.appearance_side_left),
                        color = if (settings.edgeSide == EdgeSide.LEFT) Color.Black else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
                Button(
                    onClick = { onSideChange(EdgeSide.RIGHT) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (settings.edgeSide == EdgeSide.RIGHT) EdgeCyan else DarkSurfaceVariant
                    ),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        stringResource(R.string.appearance_side_right),
                        color = if (settings.edgeSide == EdgeSide.RIGHT) Color.Black else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 상하 위치 (Y 비율)
            Text(
                stringResource(R.string.appearance_vertical_position, (settings.handlePositionRatio * 100).toInt()),
                color = TextSecondary,
                fontSize = 13.sp
            )
            Slider(
                value = settings.handlePositionRatio,
                onValueChange = onPositionChange,
                valueRange = 0.1f..0.9f,
                colors = SliderDefaults.colors(
                    thumbColor = EdgeCyan,
                    activeTrackColor = EdgeCyan
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 핸들 크기 (높이, 5dp 단위 조절)
            Text(
                stringResource(R.string.appearance_handle_height, settings.handleHeightDp),
                color = TextSecondary,
                fontSize = 13.sp
            )
            Slider(
                value = settings.handleHeightDp.toFloat(),
                onValueChange = { onHeightChange(it.toInt()) },
                valueRange = 50f..200f,
                steps = 29,
                colors = SliderDefaults.colors(
                    thumbColor = EdgeCyan,
                    activeTrackColor = EdgeCyan
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 핸들 너비 (두께 조절)
            Text(
                stringResource(R.string.appearance_handle_width, settings.handleWidthDp),
                color = TextSecondary,
                fontSize = 13.sp
            )
            Slider(
                value = settings.handleWidthDp.toFloat(),
                onValueChange = { onWidthChange(it.toInt()) },
                valueRange = 4f..30f,
                steps = 25,
                colors = SliderDefaults.colors(
                    thumbColor = EdgeCyan,
                    activeTrackColor = EdgeCyan
                )
            )

            if (settings.isHandleVisible) {
                Spacer(modifier = Modifier.height(8.dp))

                // 투명도
                Text(
                    stringResource(R.string.appearance_handle_opacity, (settings.handleAlpha * 100).toInt()),
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                Slider(
                    value = settings.handleAlpha,
                    onValueChange = onAlphaChange,
                    valueRange = 0.0f..1.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = EdgeCyan,
                        activeTrackColor = EdgeCyan
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 색상 팔레트
                Text(stringResource(R.string.appearance_handle_color), color = TextSecondary, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))
                ColorPaletteRow(
                    selectedColor = settings.handleColor,
                    onSelectColor = onColorChange
                )
            }
        }
    }
}

@Composable
internal fun EdgeLightingSettingsCard(
    settings: AppSettings,
    onLightingToggle: (Boolean) -> Unit,
    onColorChange: (Long) -> Unit,
    onCornerRadiusChange: (Int) -> Unit,
    onTestTrigger: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            SettingsToggleRow(
                title = stringResource(R.string.appearance_lighting),
                description = stringResource(R.string.appearance_lighting_description),
                checked = settings.isEdgeLightingEnabled,
                onCheckedChange = onLightingToggle
            )

            if (settings.isEdgeLightingEnabled) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(stringResource(R.string.appearance_lighting_color), color = TextSecondary, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))
                ColorPaletteRow(
                    selectedColor = settings.edgeLightingColor,
                    onSelectColor = onColorChange
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 모서리 둥글기 (곡률) 조절 (0dp 직각 ~ 50dp 둥근 모서리)
                Text(
                    text = stringResource(R.string.appearance_corner_radius, settings.edgeLightingCornerRadiusDp),
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                Text(
                    text = stringResource(
                        if (settings.edgeLightingCornerRadiusDp == 0) R.string.appearance_square_corners
                        else R.string.appearance_adjust_corners
                    ),
                    color = TextMuted,
                    fontSize = 11.sp
                )
                Slider(
                    value = settings.edgeLightingCornerRadiusDp.toFloat(),
                    onValueChange = { onCornerRadiusChange(it.toInt()) },
                    valueRange = 0f..50f,
                    steps = 50,
                    colors = SliderDefaults.colors(
                        thumbColor = EdgeCyan,
                        activeTrackColor = EdgeCyan
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onTestTrigger,
                    colors = ButtonDefaults.buttonColors(containerColor = EdgeCyan.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = EdgeCyan)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.appearance_test_lighting), color = EdgeCyan, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPaletteRow(
    selectedColor: Long,
    onSelectColor: (Long) -> Unit
) {
    val colors = listOf(
        0xFF82D8D0 to R.string.design_color_aqua,
        0xFFA9A6EA to R.string.design_color_periwinkle,
        0xFF00E5FF to R.string.design_color_cyan,
        0xFF00E676 to R.string.design_color_emerald,
        0xFFFF4081 to R.string.design_color_pink,
        0xFFFFD600 to R.string.design_color_yellow,
        0xFFF0EEE9 to R.string.design_color_white
    )

    FlowRow(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        colors.forEach { (colorHex, nameRes) ->
            val isSelected = selectedColor == colorHex
            val name = stringResource(nameRes)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .semantics { contentDescription = name }
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelectColor(colorHex) }),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(36.dp).background(Color(colorHex), CircleShape).border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) CloudHighlight else Color.Transparent,
                    shape = CircleShape
                ))
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Graphite950,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
