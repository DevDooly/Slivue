package com.devdooly.notificationedge

import android.app.Application
import android.content.res.Configuration
import com.devdooly.notificationedge.ui.shortcuts.PanelShortcuts
import com.devdooly.notificationedge.util.PrivacyConsent

class NotificationEdgeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PrivacyConsent.getInstance(this).observeNotificationAccessChanges()
        PanelShortcuts.publish(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        PanelShortcuts.publish(this)
    }
}
