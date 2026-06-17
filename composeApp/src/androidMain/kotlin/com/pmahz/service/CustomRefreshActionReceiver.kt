package com.pmahz.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CustomRefreshActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null || "STOP_CUSTOM_REFRESH" != intent.action) return
        val prefs = context.getSharedPreferences("s", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("custom_app_refresh", false).apply()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(1002)
    }
}
