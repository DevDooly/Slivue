package com.devdooly.notificationedge.ui.settings

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.devdooly.notificationedge.service.NotificationListener

/** 동시 설치된 두 Slivue 중 다른 앱에 권한을 주지 않도록 해당 배포판의 상세 화면을 우선 연다. */
internal fun openNotificationAccessSettings(context: Context) {
    val fallback = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ComponentName(context, NotificationListener::class.java).flattenToString())
    } else fallback
    if (context !is Activity) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (error: ActivityNotFoundException) {
        if (intent.action == fallback.action) throw error
        context.startActivity(fallback)
    }
}
