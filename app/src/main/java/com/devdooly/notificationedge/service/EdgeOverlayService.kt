package com.devdooly.notificationedge.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import com.devdooly.notificationedge.MainActivity
import com.devdooly.notificationedge.R
import com.devdooly.notificationedge.data.model.AppSettings
import com.devdooly.notificationedge.data.model.EdgeSide
import com.devdooly.notificationedge.data.repository.NotificationRepository
import com.devdooly.notificationedge.data.repository.SettingsRepository
import com.devdooly.notificationedge.ui.overlay.EdgeLightingEffect
import com.devdooly.notificationedge.ui.overlay.EdgePanelActivity
import com.devdooly.notificationedge.ui.overlay.EdgePanelLauncher
import com.devdooly.notificationedge.util.AppLog
import com.devdooly.notificationedge.util.OverlayLifecycleOwner
import com.devdooly.notificationedge.util.PrivacyConsent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * 화면 측면 플로팅 핸들(Handle) 및 엣지 라이팅(Edge Lighting)을 백그라운드에서 관리하는 포그라운드 서비스
 */
class EdgeOverlayService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var windowManager: WindowManager
    private lateinit var settingsRepository: SettingsRepository

    private var handleView: View? = null
    private var lightingComposeView: ComposeView? = null
    private var lightingLifecycleOwner: OverlayLifecycleOwner? = null

    private val currentSettingsState = MutableStateFlow(AppSettings())
    private val currentSettings: AppSettings
        get() = currentSettingsState.value

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        settingsRepository = SettingsRepository.getInstance(applicationContext)

        startForegroundNotification()
        if (!PrivacyConsent.canProcessNotifications(this)) {
            stopSelf()
            return
        }
        observePrivacyConsent()
        observeSettings()
        observeNewNotifications()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            PrivacyConsent.getInstance(this).pauseProcessing()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!PrivacyConsent.canProcessNotifications(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_OPEN_PANEL -> {
                triggerHaptic()
                if (!EdgePanelActivity.isInstanceActive) {
                    openPanel()
                }
            }
            ACTION_CLOSE_PANEL -> {
                EdgePanelLauncher.close()
            }
            ACTION_TOGGLE_PANEL -> {
                triggerHaptic()
                togglePanel()
            }
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!PrivacyConsent.canProcessNotifications(this)) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        // 실행 중 언어 변경에도 알림과 기존 채널의 표시 이름을 갱신한다.
        startForegroundNotification()
    }

    private fun startForegroundNotification() {
        // 이전 MIN 중요도 채널은 생성 후 앱에서 올릴 수 없으므로 명시적인 LOW 채널로 이행한다.
        val channelId = "notification_edge_service_channel_v2"
        val channelName = getString(R.string.overlay_service_channel_name)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.overlay_service_channel_description)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_SETTINGS, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, EdgeOverlayService::class.java).setAction(ACTION_STOP_SERVICE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.overlay_service_title))
            .setContentText(getString(R.string.overlay_service_desc))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, getString(R.string.overlay_service_stop), stopIntent)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun observeSettings() {
        serviceScope.launch {
            settingsRepository.settingsFlow.collectLatest { settings ->
                if (!PrivacyConsent.canProcessNotifications(this@EdgeOverlayService)) {
                    removeHandleView()
                    stopSelf()
                    return@collectLatest
                }
                currentSettingsState.value = settings
                if (settings.isServiceEnabled) {
                    updateHandleView()
                } else {
                    removeHandleView()
                }

                // 엣지 핸들과 엣지 라이팅이 둘 다 꺼진 경우에만 서비스 자체를 종료
                if (!settings.isServiceEnabled && !settings.isEdgeLightingEnabled) {
                    stopSelf()
                }
            }
        }
    }

    private fun observeNewNotifications() {
        serviceScope.launch {
            NotificationRepository.newNotificationEvent.collect { notification ->
                if (!PrivacyConsent.canProcessNotifications(this@EdgeOverlayService)) return@collect
                // 엣지 핸들(마스터 스위치) 활성화 여부와 무관하게, 엣지 라이팅 설정이 켜져 있으면 라이팅 발동
                if (currentSettings.isEdgeLightingEnabled) {
                    if (!currentSettings.excludedPackages.contains(notification.packageName)) {
                        triggerHaptic()
                        showEdgeLighting()
                    }
                }
            }
        }
    }

    private fun observePrivacyConsent() {
        serviceScope.launch {
            PrivacyConsent.getInstance(this@EdgeOverlayService).state.collect { state ->
                if (!state.isProcessingEnabled) {
                    removeHandleView()
                    removeEdgeLighting()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun triggerHaptic() {
        if (!currentSettings.hapticFeedbackEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(40)
            }
        } catch (e: Exception) {
            AppLog.warning("EdgeOverlayService", "진동 실행 실패", e)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun updateHandleView() {
        val density = resources.displayMetrics.density
        val screenHeight = resources.displayMetrics.heightPixels

        val widthPx = (currentSettings.handleWidthDp * density).toInt()
        val heightPx = (currentSettings.handleHeightDp * density).toInt()
        val yPosPx = (screenHeight * currentSettings.handlePositionRatio - heightPx / 2f).toInt()

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val gravity = if (currentSettings.edgeSide == EdgeSide.RIGHT) {
            Gravity.TOP or Gravity.END
        } else {
            Gravity.TOP or Gravity.START
        }

        val params = WindowManager.LayoutParams(
            widthPx,
            heightPx,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity
            this.x = 0
            this.y = yPosPx
        }

        val effectiveAlpha = if (currentSettings.isHandleVisible) {
            (currentSettings.handleAlpha * 255).toInt()
        } else {
            0 // 완전히 투명하게 숨김 (터치는 가능)
        }

        if (handleView == null) {
            val view = View(this).apply {
                val drawable = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(currentSettings.handleColor.toInt())
                    alpha = effectiveAlpha
                    val radius = 12f * density
                    if (currentSettings.edgeSide == EdgeSide.RIGHT) {
                        cornerRadii = floatArrayOf(radius, radius, 0f, 0f, 0f, 0f, radius, radius)
                    } else {
                        cornerRadii = floatArrayOf(0f, 0f, radius, radius, radius, radius, 0f, 0f)
                    }
                }
                background = drawable

                var startX = 0f
                var startY = 0f
                setOnTouchListener { _, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            startX = event.rawX
                            startY = event.rawY
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            val diffX = event.rawX - startX
                            val isSwipe = if (currentSettings.edgeSide == EdgeSide.RIGHT) {
                                diffX < -20 // 왼쪽으로 스와이프
                            } else {
                                diffX > 20  // 오른쪽으로 스와이프
                            }
                            val isTap = kotlin.math.abs(diffX) < 15 && kotlin.math.abs(event.rawY - startY) < 15
                            if (isSwipe || isTap) {
                                triggerHaptic()
                                togglePanel()
                            }
                            true
                        }
                        else -> false
                    }
                }
            }
            handleView = view
            try {
                windowManager.addView(view, params)
            } catch (e: Exception) {
                AppLog.warning("EdgeOverlayService", "엣지 핸들 추가 실패", e)
            }
        } else {
            handleView?.let { view ->
                val drawable = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(currentSettings.handleColor.toInt())
                    alpha = effectiveAlpha
                    val radius = 12f * density
                    if (currentSettings.edgeSide == EdgeSide.RIGHT) {
                        cornerRadii = floatArrayOf(radius, radius, 0f, 0f, 0f, 0f, radius, radius)
                    } else {
                        cornerRadii = floatArrayOf(0f, 0f, radius, radius, radius, radius, 0f, 0f)
                    }
                }
                view.background = drawable
                try {
                    windowManager.updateViewLayout(view, params)
                } catch (e: Exception) {
                    AppLog.warning("EdgeOverlayService", "엣지 핸들 갱신 실패", e)
                }
            }
        }
    }

    private fun removeHandleView() {
        handleView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                AppLog.debug("EdgeOverlayService", "엣지 핸들 제거 실패", e)
            }
            handleView = null
        }
    }

    private fun togglePanel() {
        EdgePanelLauncher.toggle(this)
    }

    private fun openPanel() {
        EdgePanelLauncher.open(this)
    }

    private fun showEdgeLighting() {
        if (lightingComposeView != null) return // 이미 표시 중이면 무시

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            windowAnimations = 0
            format = PixelFormat.TRANSLUCENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        val lifecycleOwner = OverlayLifecycleOwner()
        lightingLifecycleOwner = lifecycleOwner

        val composeView = ComposeView(this).apply {
            setBackgroundColor(AndroidColor.TRANSPARENT)
            lifecycleOwner.attachToView(this)
            setContent {
                EdgeLightingEffect(
                    color = Color(currentSettings.edgeLightingColor),
                    cornerRadiusDp = currentSettings.edgeLightingCornerRadiusDp,
                    durationMs = currentSettings.edgeLightingDurationMs,
                    onFinish = { removeEdgeLighting() }
                )
            }
        }
        lightingComposeView = composeView
        lifecycleOwner.onCreate()

        try {
            windowManager.addView(composeView, params)
        } catch (e: Exception) {
            AppLog.warning("EdgeOverlayService", "엣지 라이팅 추가 실패", e)
        }
    }

    private fun removeEdgeLighting() {
        lightingComposeView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                AppLog.debug("EdgeOverlayService", "엣지 라이팅 제거 실패", e)
            }
            lightingComposeView = null
        }
        lightingLifecycleOwner?.onDestroy()
        lightingLifecycleOwner = null
    }

    override fun onDestroy() {
        super.onDestroy()
        removeHandleView()
        removeEdgeLighting()
        serviceScope.cancel()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        const val ACTION_OPEN_PANEL = "com.devdooly.notificationedge.ACTION_OPEN_PANEL"
        const val ACTION_CLOSE_PANEL = "com.devdooly.notificationedge.ACTION_CLOSE_PANEL"
        const val ACTION_TOGGLE_PANEL = "com.devdooly.notificationedge.ACTION_TOGGLE_PANEL"
        const val ACTION_STOP_SERVICE = "com.devdooly.notificationedge.ACTION_STOP_SERVICE"
    }
}
