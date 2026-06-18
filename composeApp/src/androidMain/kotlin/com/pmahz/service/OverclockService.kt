package com.pmahz.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.pmahz.util.RootUtils
import com.pmahz.util.ShizukuUtils

class OverclockService : Service() {
    companion object {
        private const val TAG = "OverclockService"
        private const val CHANNEL_ID = "overclock_channel"
        private const val NOTIF_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        if (intent != null) {
            val authMode = intent.getStringExtra("auth_mode") ?: ""
            val targetW = intent.getIntExtra("targetW", 0)
            val targetH = intent.getIntExtra("targetH", 0)
            val targetHz = intent.getIntExtra("targetHz", 0)
            if (authMode.isNotEmpty() && targetW > 0 && targetH > 0 && targetHz > 0) {
                applyOverclock(authMode, targetW, targetH, targetHz)
            }
        }
        return START_STICKY
    }

    private fun applyOverclock(authMode: String, w: Int, h: Int, hz: Int) {
        try {
            val modes = com.pmahz.util.AutoOverclockManager.getSupportedModes(this)
            var target = modes.find { it.width == w && it.height == h && it.rateInt == hz }
            if (target == null) {
                target = modes.filter { it.width == w && it.height == h }
                    .minByOrNull { Math.abs(it.rateInt - hz) }
            }
            if (target != null) {
                val dumpsysModeId = target.sfIndex
                when (authMode) {
                    "root" -> RootUtils.setRate(dumpsysModeId, target.rateInt)
                    "shizuku" -> ShizukuUtils.setRate(dumpsysModeId, target.rateInt)
                }
                Log.d(TAG, "超频已应用: ${w}x${h} @ ${hz}Hz")
            }
        } catch (e: Exception) {
            Log.e(TAG, "applyOverclock: ${e.message}")
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL_ID, "自动超频", NotificationManager.IMPORTANCE_LOW)
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("自动超频运行中")
            .setContentText("正在维持目标刷新率")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
