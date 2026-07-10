package com.refreshrate.control.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.refreshrate.control.MainActivity
import com.refreshrate.control.util.RootUtils
import com.refreshrate.control.util.RuntimeLog

class RefreshRateMonitorService : Service() {
    companion object {
        private const val TAG = "RefreshMonitor"
        private const val CHANNEL_ID = "refresh_monitor_channel"
        private const val NOTIF_ID = 1003
        private const val ACTION_START = "com.refreshrate.control.START_MONITOR"
        private const val ACTION_STOP = "com.refreshrate.control.STOP_MONITOR"

        fun start(context: Context) {
            val intent = Intent(context, RefreshRateMonitorService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, RefreshRateMonitorService::class.java).apply {
                action = ACTION_STOP
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var polling = false
    private var lastTopPackage = ""
    private var lastHeartbeatAt = 0L
    private val pollRunnable = object : Runnable {
        override fun run() {
            try {
                val prefs = getSharedPreferences("s", Context.MODE_PRIVATE)
                if (!prefs.getBoolean("custom_app_refresh", false)) {
                    runtimeLog("MONITOR disabled, stopping")
                    stopSelf()
                    return
                }

                val topPackage = RootUtils.getTopPackageFromWindow()
                val a11y = KeepAliveAccessibilityService.activeInstance
                if (!topPackage.isNullOrEmpty()) {
                    if (topPackage != lastTopPackage) {
                        runtimeLog("MONITOR top=$topPackage a11y=${a11y != null} state=${a11y?.monitorStateSummary() ?: "no-a11y"}")
                        lastTopPackage = topPackage
                    }
                    a11y?.handleMonitorForegroundPackage(topPackage)
                } else {
                    runtimeLog("MONITOR top=unknown a11y=${a11y != null} state=${a11y?.monitorStateSummary() ?: "no-a11y"}")
                }

                logHeartbeat(topPackage ?: "unknown", a11y)
                updateNotification(a11y?.monitorNotificationText(topPackage ?: "unknown") ?: "等待无障碍服务连接\n前台: ${topPackage ?: "unknown"}")
                handler.postDelayed(this, 1000L)
            } catch (e: Exception) {
                runtimeLog("MONITOR failed=${e.message}")
                handler.postDelayed(this, 2000L)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        RuntimeLog.init(this)
        createChannel()
        startAsForeground("监控启动中")
        runtimeLog("MONITOR service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            runtimeLog("MONITOR stop requested")
            stopSelf()
            return START_NOT_STICKY
        }
        startPolling()
        return START_STICKY
    }

    override fun onDestroy() {
        stopPolling()
        runtimeLog("MONITOR service destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startPolling() {
        if (polling) return
        polling = true
        handler.post(pollRunnable)
        runtimeLog("MONITOR poll started")
    }

    private fun stopPolling() {
        if (!polling) return
        polling = false
        handler.removeCallbacks(pollRunnable)
        runtimeLog("MONITOR poll stopped")
    }

    private fun logHeartbeat(topPackage: String, a11y: KeepAliveAccessibilityService?) {
        val now = System.currentTimeMillis()
        if (now - lastHeartbeatAt < 30_000L) return
        lastHeartbeatAt = now
        runtimeLog("MONITOR heartbeat top=$topPackage a11y=${a11y != null} display=${RootUtils.readDisplayState().summary()}")
    }

    private fun startAsForeground(content: String) {
        val notification = buildNotification(content)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun updateNotification(content: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(content))
    }

    private fun buildNotification(content: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= 31) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else PendingIntent.FLAG_UPDATE_CURRENT
        val openPi = PendingIntent.getActivity(this, 5, openIntent, flags)

        val stopIntent = Intent(this, CustomRefreshActionReceiver::class.java).apply {
            action = "STOP_CUSTOM_REFRESH"
        }
        val stopPi = PendingIntent.getBroadcast(this, 6, stopIntent, flags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("自动刷新率监控中")
            .setContentText(content.lineSequence().firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(openPi)
            .addAction(android.R.drawable.ic_media_pause, "停止", stopPi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "自动刷新率监控",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(ch)
        }
    }

    private fun runtimeLog(message: String) {
        RuntimeLog.append(this, TAG, message)
    }
}
