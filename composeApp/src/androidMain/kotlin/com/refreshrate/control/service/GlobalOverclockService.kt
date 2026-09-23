package com.refreshrate.control.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.refreshrate.control.MainActivity
import com.refreshrate.control.core.OwnershipState
import com.refreshrate.control.model.DisplayMode
import com.refreshrate.control.util.AutoOverclockManager
import com.refreshrate.control.util.RefreshOwnership
import com.refreshrate.control.util.RefreshSwitchCoordinator
import com.refreshrate.control.util.RootUtils
import com.refreshrate.control.util.RuntimeLog
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 全局刷新率锁定(方案 B):前台常驻服务,周期性把系统显示模式压回目标分辨率+刷新率,
 * 覆盖桌面/触控 boost 等系统的瞬态提频(设置型 peak/min 在这类设备上并非硬上限)。
 *
 * 目标由开启时写入 prefs(global_oc_res / global_oc_hz);关闭时 restoreAdaptive 交还系统。
 * 常驻通知带"停止";任务被滑掉用 AlarmManager 1s 重启;开机由 BootCompletedReceiver 恢复。
 */
class GlobalOverclockService : Service() {
    companion object {
        private const val CHANNEL_ID = "global_overclock_channel"
        private const val NOTIFICATION_ID = 1003
        private const val POLL_MS = 1500L
        const val ACTION_START = "com.refreshrate.control.START_GLOBAL_OC"
        const val ACTION_STOP = "com.refreshrate.control.STOP_GLOBAL_OC"

        fun isEnabled(context: Context): Boolean =
            context.getSharedPreferences("s", Context.MODE_PRIVATE).getBoolean("global_overclock", false)

        fun start(context: Context, res: String, hz: Int) {
            context.getSharedPreferences("s", Context.MODE_PRIVATE).edit()
                .putBoolean("global_overclock", true)
                .putString("global_oc_res", res)
                .putInt("global_oc_hz", hz)
                .apply()
            val intent = Intent(context, GlobalOverclockService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.getSharedPreferences("s", Context.MODE_PRIVATE).edit()
                .putBoolean("global_overclock", false).apply()
            context.stopService(Intent(context, GlobalOverclockService::class.java))
        }

        private fun immutableFlag(): Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
    }

    @Volatile
    private var running = false
    private var worker: Thread? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        startWorker()
        return START_STICKY
    }

    private fun startWorker() {
        if (running) return
        running = true
        val t = Thread {
            val prefs = getSharedPreferences("s", Context.MODE_PRIVATE)
            while (running) {
                try {
                    // per-app 配置生效中时让位,避免与分应用切换互抢
                    RefreshOwnership.syncFromPrefs(this)
                    if (RefreshOwnership.currentState().state == OwnershipState.AUTO_PROFILE_ENTERED) {
                        Thread.sleep(POLL_MS)
                        continue
                    }
                    val res = prefs.getString("global_oc_res", "") ?: ""
                    val hz = prefs.getInt("global_oc_hz", 0)
                    val wh = if (res.isEmpty()) emptyList() else res.split("x")
                    val tw = wh.getOrNull(0)?.toIntOrNull()
                    val th = wh.getOrNull(1)?.toIntOrNull()
                    if (hz <= 0 || tw == null || th == null) {
                        Thread.sleep(POLL_MS)
                        continue
                    }
                    val allModes = AutoOverclockManager.getSupportedModes(this)
                    val current = AutoOverclockManager.getCurrentMode(this)
                    val matched = current != null && current.width == tw && current.height == th &&
                        kotlin.math.abs(current.rateInt - hz) <= 1
                    if (!matched && allModes.isNotEmpty()) {
                        val target: DisplayMode? = allModes.filter { it.width == tw && it.height == th }
                            .minByOrNull { kotlin.math.abs(it.rateInt - hz) }
                            ?: allModes.minByOrNull { kotlin.math.abs(it.rateInt - hz) }
                        if (target != null) {
                            val currentHz = AutoOverclockManager.getCurrentRefreshRate(this)
                            RuntimeLog.appendGlobal(
                                "GlobalOC",
                                "ENFORCE current=${current?.rateInt ?: -1}Hz target=${target.rateInt}Hz sfIndex=${target.sfIndex}"
                            )
                            val latch = CountDownLatch(1)
                            RefreshSwitchCoordinator.submitWithoutBump("global-oc") { _, _ ->
                                try {
                                    RootUtils.switchRefreshRate(target, allModes, currentHz, useSfFallback = true) { false }
                                } finally {
                                    latch.countDown()
                                }
                            }
                            latch.await(4000, TimeUnit.MILLISECONDS)
                        }
                    }
                    updateNotification()
                    Thread.sleep(POLL_MS)
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    RuntimeLog.appendGlobal("GlobalOC", "error=${e.message}")
                    try {
                        Thread.sleep(POLL_MS)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }
        }
        t.isDaemon = true
        worker = t
        t.start()
        RuntimeLog.appendGlobal("GlobalOC", "SERVICE started target=${targetLabel()}")
    }

    override fun onDestroy() {
        running = false
        worker?.interrupt()
        worker = null
        // 仅用户主动停止(开关置 false)时才交还系统自适应;被系统回收重启时不解锁,避免闪烁
        val prefs = getSharedPreferences("s", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("global_overclock", false)) {
            try {
                RootUtils.restoreAdaptive(0, 0)
            } catch (_: Exception) { }
        }
        super.onDestroy()
        RuntimeLog.appendGlobal("GlobalOC", "SERVICE stopped")
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        try {
            val restart = Intent(applicationContext, GlobalOverclockService::class.java).setAction(ACTION_START)
            val pi = PendingIntent.getService(
                applicationContext, 3, restart,
                PendingIntent.FLAG_ONE_SHOT or immutableFlag()
            )
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 1000, pi)
        } catch (_: Exception) { }
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "全局刷新率锁定", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    private fun targetLabel(): String {
        val prefs = getSharedPreferences("s", Context.MODE_PRIVATE)
        return "${prefs.getString("global_oc_res", "?")} @ ${prefs.getInt("global_oc_hz", 0)}Hz"
    }

    private fun buildNotification(): Notification {
        val stop = Intent(this, GlobalOverclockService::class.java).setAction(ACTION_STOP)
        val stopPi = PendingIntent.getService(this, 1, stop, PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag())
        val open = Intent(this, MainActivity::class.java)
        val openPi = PendingIntent.getActivity(this, 2, open, PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag())
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("全局刷新率锁定")
            .setContentText("锁定 ${targetLabel()}")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(openPi)
            .addAction(android.R.drawable.ic_media_pause, "停止", stopPi)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification())
    }
}
