package com.devdooly.notificationedge.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.devdooly.notificationedge.util.userMessage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devdooly.notificationedge.R
import com.devdooly.notificationedge.ui.theme.*
import com.devdooly.notificationedge.util.CustomFontManager
@Composable
internal fun FontSettingsCard(
    selectedFontId: String,
    onFontSelected: (String) -> Unit
) {
    val context = LocalContext.current
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    var customFonts by remember { mutableStateOf(CustomFontManager.getCustomFonts(context)) }

    // 파일 선택 런처 (.ttf, .otf, .ttc)
    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val result = CustomFontManager.saveCustomFont(context, uri)
            result.onSuccess { fontInfo ->
                customFonts = CustomFontManager.getCustomFonts(context)
                onFontSelected(fontInfo.id)
                Toast.makeText(context, context.getString(R.string.font_added, fontInfo.displayName), Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                Toast.makeText(
                    context,
                    context.getString(R.string.font_import_failed, error.userMessage(context, R.string.custom_font_error_verify)),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val currentDisplayName = if (selectedFontId.startsWith("custom:")) {
        val fileName = selectedFontId.removePrefix("custom:")
        customFonts.find { it.fileName == fileName }?.displayName ?: fileName
    } else {
        stringResource(AppFont.fromId(selectedFontId).displayNameRes)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            val stateLabel = stringResource(if (isExpanded) R.string.settings_collapse else R.string.settings_expand)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .semantics { stateDescription = stateLabel }
                    .clickable(role = Role.Button, onClickLabel = stateLabel) { isExpanded = !isExpanded }
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(Icons.Default.TextFields, contentDescription = null, tint = EdgeCyan, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = stringResource(R.string.design_font),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.font_current, currentDisplayName),
                        color = EdgeCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = TextSecondary
                )
            }

            if (isExpanded) {
                // 한영 혼용 정렬 보정 안내 문구
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = DarkBackground,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, GlassBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.TextFields,
                            contentDescription = null,
                            tint = EdgeCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.font_alignment_description),
                            color = TextMuted,
                            fontSize = 13.sp,
                            lineHeight = 20.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 폰트 파일 업로드 버튼
                Button(
                    onClick = {
                        fontPickerLauncher.launch(
                            arrayOf(
                                "font/*",
                                "application/x-font-ttf",
                                "application/x-font-opentype",
                                "application/octet-stream",
                                "*/*"
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EdgeCyan.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = EdgeCyan)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.font_import),
                        color = EdgeCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                // 내가 추가한 폰트 목록
                if (customFonts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.font_custom_fonts, customFonts.size),
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        customFonts.forEach { customFont ->
                            val isSelected = customFont.id == selectedFontId
                            // 관리자의 수정 시각 기반 캐시를 사용해 같은 이름으로 다시 가져온 폰트도 반영한다.
                            val customFamily = CustomFontManager.loadFontFamily(context, customFont.id)
                                ?: androidx.compose.ui.text.font.FontFamily.Default

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) EdgeCyan.copy(alpha = 0.12f) else DarkBackground,
                                border = androidx.compose.foundation.BorderStroke(
                                    width = if (isSelected) 1.5.dp else 0.5.dp,
                                    color = if (isSelected) EdgeCyan else Color(0xFF333B4A)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onFontSelected(customFont.id) })
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = customFont.displayName,
                                            color = if (isSelected) EdgeCyan else Color.White,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 14.sp
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = customFont.fileName,
                                            color = TextMuted,
                                            fontSize = 11.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = stringResource(R.string.font_custom_preview),
                                            color = if (isSelected) CloudDancer else TextSecondary,
                                            fontSize = 12.sp,
                                            fontFamily = customFamily
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                CustomFontManager.deleteCustomFont(context, customFont.fileName)
                                                customFonts = CustomFontManager.getCustomFonts(context)
                                                if (selectedFontId == customFont.id) {
                                                    onFontSelected("default")
                                                }
                                                Toast.makeText(context, context.getString(R.string.font_deleted), Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = stringResource(R.string.font_delete),
                                                tint = TextMuted,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = null,
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = EdgeCyan,
                                                unselectedColor = TextMuted
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 기본 제공 폰트 프리셋 목록
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.font_presets),
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppFont.entries.forEach { fontOption ->
                        val isSelected = fontOption.id == selectedFontId
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) EdgeCyan.copy(alpha = 0.12f) else DarkBackground,
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isSelected) 1.5.dp else 0.5.dp,
                                color = if (isSelected) EdgeCyan else Color(0xFF333B4A)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onFontSelected(fontOption.id) })
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(fontOption.displayNameRes),
                                        color = if (isSelected) EdgeCyan else Color.White,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = stringResource(fontOption.descriptionRes),
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    // 폰트 실시간 적용 미리보기 샘플
                                    Text(
                                        text = stringResource(R.string.font_preset_preview),
                                        color = if (isSelected) CloudDancer else TextSecondary,
                                        fontSize = 12.sp,
                                        fontFamily = fontOption.toFontFamily()
                                    )
                                }
                                RadioButton(
                                    selected = isSelected,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = EdgeCyan,
                                        unselectedColor = TextMuted
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
