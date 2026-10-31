package com.devdooly.notificationedge.data.repository

import android.annotation.SuppressLint
import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.devdooly.notificationedge.BuildConfig
import com.devdooly.notificationedge.data.model.AppSettings
import com.devdooly.notificationedge.data.model.EdgeSide
import com.devdooly.notificationedge.util.PrivacyConsent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.transformLatest
import java.io.IOException

private val Context.dataStore by preferencesDataStore(
    name = "notification_edge_settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

class SettingsRepository(private val context: Context) {

    companion object {
        private const val SYNC_PREFS_NAME = "notification_edge_sync_prefs"
        private const val KEY_LAUNCH_DIRECT = "launch_direct_to_panel"
        private const val KEY_EXTERNAL_CONTROL = "external_control_enabled"
        private const val KEY_DIAGNOSTIC_MODE = "diagnostic_mode_enabled"
        private const val KEY_DIAGNOSTIC_MODE_EXPIRES_AT = "diagnostic_mode_expires_at"
        internal const val DIAGNOSTIC_SESSION_DURATION_MS = 12L * 60L * 60L * 1000L

        // INSTANCE는 Activity가 아닌 applicationContext만 보관한다.
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var INSTANCE: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsRepository(context.applicationContext).also { INSTANCE = it }
            }
        }

        internal fun isDiagnosticSessionActive(
            enabled: Boolean,
            expiresAtEpochMs: Long,
            nowEpochMs: Long
        ): Boolean = enabled && expiresAtEpochMs > nowEpochMs
    }

    private object PreferencesKeys {
        val SERVICE_ENABLED = booleanPreferencesKey("service_enabled")
        val EDGE_SIDE = intPreferencesKey("edge_side")
        val HANDLE_POS_RATIO = floatPreferencesKey("handle_pos_ratio")
        val HANDLE_WIDTH_DP = intPreferencesKey("handle_width_dp")
        val HANDLE_HEIGHT_DP = intPreferencesKey("handle_height_dp")
        val HANDLE_COLOR = longPreferencesKey("handle_color")
        val HANDLE_ALPHA = floatPreferencesKey("handle_alpha")
        val HANDLE_VISIBLE = booleanPreferencesKey("handle_visible")
        val LAUNCH_DIRECT_TO_PANEL = booleanPreferencesKey("launch_direct_to_panel")
        val PANEL_WIDTH_DP = intPreferencesKey("panel_width_dp")
        val AUTO_DISMISS_ON_OPEN = booleanPreferencesKey("auto_dismiss_on_open")
        val EDGE_LIGHTING_ENABLED = booleanPreferencesKey("edge_lighting_enabled")
        val EDGE_LIGHTING_DURATION_MS = longPreferencesKey("edge_lighting_duration_ms")
        val EDGE_LIGHTING_COLOR = longPreferencesKey("edge_lighting_color")
        val EDGE_LIGHTING_CORNER_RADIUS_DP = intPreferencesKey("edge_lighting_corner_radius_dp")
        val SELECTED_FONT = stringPreferencesKey("selected_font")
        val HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
        val PAUSE_MEDIA_ON_OPEN = booleanPreferencesKey("pause_media_on_open")
        val DIAGNOSTIC_MODE_ENABLED = booleanPreferencesKey("diagnostic_mode_enabled")
        val DIAGNOSTIC_MODE_EXPIRES_AT = longPreferencesKey("diagnostic_mode_expires_at")
        val EXTERNAL_CONTROL_ENABLED = booleanPreferencesKey("external_control_enabled")
        val EXCLUDED_PACKAGES = stringSetPreferencesKey("excluded_packages")
        val DISCOVERED_APP_PACKAGES = stringSetPreferencesKey("discovered_app_packages")
        val BLOCKED_KEYWORDS = stringSetPreferencesKey("blocked_keywords")
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val settingsFlow: Flow<AppSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .transformLatest { prefs ->
            val defaults = AppSettings()
            val nowEpochMs = System.currentTimeMillis()
            val diagnosticExpiresAt = prefs[PreferencesKeys.DIAGNOSTIC_MODE_EXPIRES_AT] ?: 0L
            val diagnosticEnabled = !BuildConfig.IS_PLAY_DISTRIBUTION && isDiagnosticSessionActive(
                enabled = prefs[PreferencesKeys.DIAGNOSTIC_MODE_ENABLED] ?: defaults.diagnosticModeEnabled,
                expiresAtEpochMs = diagnosticExpiresAt,
                nowEpochMs = nowEpochMs
            )
            val settings = AppSettings(
                isServiceEnabled = prefs[PreferencesKeys.SERVICE_ENABLED] ?: defaults.isServiceEnabled,
                edgeSide = if ((prefs[PreferencesKeys.EDGE_SIDE] ?: 0) == 0) EdgeSide.LEFT else EdgeSide.RIGHT,
                handlePositionRatio = prefs[PreferencesKeys.HANDLE_POS_RATIO] ?: defaults.handlePositionRatio,
                handleWidthDp = prefs[PreferencesKeys.HANDLE_WIDTH_DP] ?: defaults.handleWidthDp,
                handleHeightDp = prefs[PreferencesKeys.HANDLE_HEIGHT_DP] ?: defaults.handleHeightDp,
                handleColor = prefs[PreferencesKeys.HANDLE_COLOR] ?: defaults.handleColor,
                handleAlpha = prefs[PreferencesKeys.HANDLE_ALPHA] ?: defaults.handleAlpha,
                isHandleVisible = prefs[PreferencesKeys.HANDLE_VISIBLE] ?: defaults.isHandleVisible,
                launchDirectToPanel = prefs[PreferencesKeys.LAUNCH_DIRECT_TO_PANEL] ?: defaults.launchDirectToPanel,
                panelWidthDp = prefs[PreferencesKeys.PANEL_WIDTH_DP] ?: defaults.panelWidthDp,
                autoDismissOnOpen = prefs[PreferencesKeys.AUTO_DISMISS_ON_OPEN] ?: defaults.autoDismissOnOpen,
                isEdgeLightingEnabled = prefs[PreferencesKeys.EDGE_LIGHTING_ENABLED] ?: defaults.isEdgeLightingEnabled,
                edgeLightingDurationMs = prefs[PreferencesKeys.EDGE_LIGHTING_DURATION_MS] ?: defaults.edgeLightingDurationMs,
                edgeLightingColor = prefs[PreferencesKeys.EDGE_LIGHTING_COLOR] ?: defaults.edgeLightingColor,
                edgeLightingCornerRadiusDp = prefs[PreferencesKeys.EDGE_LIGHTING_CORNER_RADIUS_DP]
                    ?: defaults.edgeLightingCornerRadiusDp,
                selectedFont = prefs[PreferencesKeys.SELECTED_FONT] ?: defaults.selectedFont,
                hapticFeedbackEnabled = prefs[PreferencesKeys.HAPTIC_ENABLED] ?: defaults.hapticFeedbackEnabled,
                pauseMediaOnOpen = prefs[PreferencesKeys.PAUSE_MEDIA_ON_OPEN] ?: defaults.pauseMediaOnOpen,
                diagnosticModeEnabled = diagnosticEnabled,
                externalControlEnabled = prefs[PreferencesKeys.EXTERNAL_CONTROL_ENABLED]
                    ?: defaults.externalControlEnabled,
                excludedPackages = prefs[PreferencesKeys.EXCLUDED_PACKAGES] ?: defaults.excludedPackages,
                discoveredAppPackages = prefs[PreferencesKeys.DISCOVERED_APP_PACKAGES]
                    ?: defaults.discoveredAppPackages,
                blockedKeywords = prefs[PreferencesKeys.BLOCKED_KEYWORDS] ?: defaults.blockedKeywords
            )
            emit(settings)
            if (diagnosticEnabled) {
                delay((diagnosticExpiresAt - nowEpochMs).coerceAtLeast(1L))
                emit(settings.copy(diagnosticModeEnabled = false))
            }
        }

    suspend fun updateServiceEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.SERVICE_ENABLED] = enabled }
    }

    suspend fun updateEdgeSide(side: EdgeSide) {
        context.dataStore.edit { it[PreferencesKeys.EDGE_SIDE] = if (side == EdgeSide.LEFT) 0 else 1 }
    }

    suspend fun updateHandlePositionRatio(ratio: Float) {
        context.dataStore.edit { it[PreferencesKeys.HANDLE_POS_RATIO] = ratio.coerceIn(0.1f, 0.9f) }
    }

    suspend fun updateHandleWidthDp(width: Int) {
        context.dataStore.edit { it[PreferencesKeys.HANDLE_WIDTH_DP] = width.coerceIn(4, 30) }
    }

    suspend fun updateHandleHeightDp(height: Int) {
        val snapped = (height / 5) * 5
        context.dataStore.edit { it[PreferencesKeys.HANDLE_HEIGHT_DP] = snapped.coerceIn(40, 250) }
    }

    suspend fun updateHandleColor(color: Long) {
        context.dataStore.edit { it[PreferencesKeys.HANDLE_COLOR] = color }
    }

    suspend fun updateHandleAlpha(alpha: Float) {
        context.dataStore.edit { it[PreferencesKeys.HANDLE_ALPHA] = alpha.coerceIn(0.0f, 1.0f) }
    }

    suspend fun updateHandleVisible(visible: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.HANDLE_VISIBLE] = visible }
    }

    suspend fun updateLaunchDirectToPanel(direct: Boolean) {
        context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_LAUNCH_DIRECT, direct)
            .apply()
        context.dataStore.edit { it[PreferencesKeys.LAUNCH_DIRECT_TO_PANEL] = direct }
    }

    fun isLaunchDirectToPanelSync(): Boolean {
        val sp = context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
        return sp.getBoolean(KEY_LAUNCH_DIRECT, true)
    }

    suspend fun updatePanelWidthDp(width: Int) {
        val snapped = (width / 5) * 5
        context.dataStore.edit { it[PreferencesKeys.PANEL_WIDTH_DP] = snapped.coerceIn(220, 360) }
    }

    suspend fun updateAutoDismissOnOpen(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.AUTO_DISMISS_ON_OPEN] = enabled }
    }

    suspend fun updateEdgeLightingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.EDGE_LIGHTING_ENABLED] = enabled }
    }

    suspend fun updateEdgeLightingColor(color: Long) {
        context.dataStore.edit { it[PreferencesKeys.EDGE_LIGHTING_COLOR] = color }
    }

    suspend fun updateEdgeLightingCornerRadiusDp(radius: Int) {
        context.dataStore.edit { it[PreferencesKeys.EDGE_LIGHTING_CORNER_RADIUS_DP] = radius.coerceIn(0, 60) }
    }

    suspend fun updateSelectedFont(fontId: String) {
        context.dataStore.edit { it[PreferencesKeys.SELECTED_FONT] = fontId }
    }

    suspend fun updateHapticEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.HAPTIC_ENABLED] = enabled }
    }

    suspend fun updatePauseMediaOnOpen(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.PAUSE_MEDIA_ON_OPEN] = enabled }
    }

    suspend fun updateDiagnosticModeEnabled(enabled: Boolean) {
        val allowed = enabled && !BuildConfig.IS_PLAY_DISTRIBUTION
        val expiresAtEpochMs = if (allowed) {
            System.currentTimeMillis() + DIAGNOSTIC_SESSION_DURATION_MS
        } else {
            0L
        }
        context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putBoolean(KEY_DIAGNOSTIC_MODE, allowed)
            if (allowed) {
                putLong(KEY_DIAGNOSTIC_MODE_EXPIRES_AT, expiresAtEpochMs)
            } else {
                remove(KEY_DIAGNOSTIC_MODE_EXPIRES_AT)
            }
        }.apply()
        context.dataStore.edit {
            it[PreferencesKeys.DIAGNOSTIC_MODE_ENABLED] = allowed
            if (allowed) {
                it[PreferencesKeys.DIAGNOSTIC_MODE_EXPIRES_AT] = expiresAtEpochMs
            } else {
                it.remove(PreferencesKeys.DIAGNOSTIC_MODE_EXPIRES_AT)
            }
        }
    }

    fun isDiagnosticModeEnabledSync(): Boolean {
        if (BuildConfig.IS_PLAY_DISTRIBUTION) return false
        val sp = context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
        val enabled = sp.getBoolean(KEY_DIAGNOSTIC_MODE, false)
        val expiresAtEpochMs = sp.getLong(KEY_DIAGNOSTIC_MODE_EXPIRES_AT, 0L)
        val active = isDiagnosticSessionActive(enabled, expiresAtEpochMs, System.currentTimeMillis())
        if (enabled && !active) {
            sp.edit()
                .putBoolean(KEY_DIAGNOSTIC_MODE, false)
                .remove(KEY_DIAGNOSTIC_MODE_EXPIRES_AT)
                .apply()
        }
        return active
    }

    suspend fun updateExternalControlEnabled(enabled: Boolean) {
        context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_EXTERNAL_CONTROL, enabled)
            .apply()
        context.dataStore.edit { it[PreferencesKeys.EXTERNAL_CONTROL_ENABLED] = enabled }
    }

    fun isExternalControlEnabledSync(): Boolean {
        val sp = context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
        return sp.getBoolean(KEY_EXTERNAL_CONTROL, false)
    }

    suspend fun setPackageExcluded(packageName: String, excluded: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[PreferencesKeys.EXCLUDED_PACKAGES] ?: emptySet()
            prefs[PreferencesKeys.EXCLUDED_PACKAGES] = if (excluded) {
                current + packageName
            } else {
                current - packageName
            }
        }
    }

    suspend fun addDiscoveredPackage(packageName: String, accessGeneration: Long? = null) {
        if (packageName.isBlank()) return
        context.dataStore.edit { prefs ->
            val update = {
                val current = prefs[PreferencesKeys.DISCOVERED_APP_PACKAGES] ?: emptySet()
                if (!current.contains(packageName)) {
                    prefs[PreferencesKeys.DISCOVERED_APP_PACKAGES] = current + packageName
                }
            }
            // DataStore 대기는 모니터 밖, 실제 메타데이터 변경만 권한 세대와 함께 검사한다.
            if (accessGeneration == null) update()
            else PrivacyConsent.getInstance(context).withCurrentAccess(accessGeneration, update)
        }
    }

    suspend fun clearDiscoveredPackages() {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.DISCOVERED_APP_PACKAGES] = emptySet()
        }
    }

    suspend fun addBlockedKeyword(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isBlank()) return
        context.dataStore.edit { prefs ->
            val current = prefs[PreferencesKeys.BLOCKED_KEYWORDS] ?: emptySet()
            prefs[PreferencesKeys.BLOCKED_KEYWORDS] = current + trimmed
        }
    }

    suspend fun removeBlockedKeyword(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isBlank()) return
        context.dataStore.edit { prefs ->
            val current = prefs[PreferencesKeys.BLOCKED_KEYWORDS] ?: emptySet()
            prefs[PreferencesKeys.BLOCKED_KEYWORDS] = current - trimmed
        }
    }
}
