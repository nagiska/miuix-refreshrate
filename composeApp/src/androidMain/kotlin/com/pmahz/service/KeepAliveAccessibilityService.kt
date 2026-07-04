package com.pmahz.service

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.core.app.NotificationCompat
import com.pmahz.MainActivity
import com.pmahz.model.DisplayMode
import com.pmahz.util.AutoOverclockManager
import com.pmahz.util.RootUtils
import com.pmahz.util.ShizukuUtils

class KeepAliveAccessibilityService : AccessibilityService() {
    companion object {
        private const val TAG = "KeepAliveA11y"
        private const val CUSTOM_CHANNEL_ID = "custom_app_channel"
        private const val CUSTOM_NOTIF_ID = 1002
    }

    private var fgHandler: Handler? = null
    private var servicePrefs: SharedPreferences? = null
    private var currentFgPackage: String = ""
    private var pendingFgPackage: String = ""
    private var lastAppliedConfig: String = ""
    private var lastRealPkg: String = ""
    private var pendingApplyRunnable: Runnable? = null
    private var cachedModes: List<DisplayMode> = emptyList()

    override fun onServiceConnected() {
        super.onServiceConnected()
        fgHandler = Handler(Looper.getMainLooper())
        Log.d(TAG, "无障碍服务已连接")
        createCustomChannel()
        servicePrefs = getSharedPreferences("s", Context.MODE_PRIVATE)

        servicePrefs?.registerOnSharedPreferenceChangeListener { sp, key ->
            if ("custom_app_refresh" == key) {
                if (sp.getBoolean("custom_app_refresh", false)) {
                    if (lastRealPkg.isEmpty()) {
                        postWaitingNotification()
                    } else {
                        updatePersistentNotification(lastRealPkg)
                    }
                } else {
                    cancelCustomNotification()
                }
            }
        }

        if (servicePrefs?.getBoolean("custom_app_refresh", false) == true) {
            postWaitingNotification()
        }

        Thread {
            try {
                cachedModes = AutoOverclockManager.getSupportedModes(this)
                Log.d(TAG, "预扫描模式完成: ${cachedModes.size} 个模式")
            } catch (e: Exception) {
                Log.e(TAG, "预扫描模式失败: ${e.message}")
            }
        }.start()

        checkAndRestartService()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = getTopForegroundPackage(event) ?: return
        if (pkg.isEmpty() || pkg == packageName || pkg == "android") return
        scheduleForegroundApply(pkg)
    }

    private fun getTopForegroundPackage(event: AccessibilityEvent): String? {
        try {
            val windows = windows
            if (windows != null) {
                for (w in windows) {
                    if (w != null && w.isFocused && w.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                        val root = w.root
                        if (root != null) {
                            val p = root.packageName
                            root.recycle()
                            if (p != null && p.isNotEmpty()) return p.toString()
                        }
                    }
                }
            }
        } catch (e: Exception) {
        }
        val p2 = event.packageName
        return p2?.toString()
    }

    private fun scheduleForegroundApply(pkg: String) {
        pendingFgPackage = pkg
        pendingApplyRunnable?.let { fgHandler?.removeCallbacks(it) }
        val runnable = Runnable {
            if (pkg == pendingFgPackage) {
                applyForPackage(pkg)
                updatePersistentNotification(pkg)
            }
        }
        pendingApplyRunnable = runnable
        fgHandler?.postDelayed(runnable, 200)
    }

    override fun onInterrupt() {
        Log.d(TAG, "无障碍服务中断")
        checkAndRestartService()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "无障碍服务销毁")
        checkAndRestartService()
    }

    private fun applyForPackage(basePkg: String) {
        if (basePkg.isEmpty() || basePkg == "android" || basePkg == packageName) return
        val prefs = getSharedPreferences("s", Context.MODE_PRIVATE) ?: return

        val authMode = prefs.getString("auth_mode", "") ?: ""
        if (authMode.isEmpty()) {
            Log.d(TAG, "auth_mode 为空，跳过")
            return
        }

        val effectivePkg = resolveEffectivePkg(prefs, basePkg)
        val enabled = prefs.getBoolean("app_refresh_enabled_$effectivePkg", false)

        if (!enabled) {
            // Not a configured app - check if we need to restore to 120Hz
            val savedConfig = prefs.getString("last_applied_config", "") ?: ""
            if (savedConfig.isNotEmpty()) {
                Log.i(TAG, "离开已配置应用 $effectivePkg，逐级下降到 120Hz")
                prefs.edit().remove("last_applied_config").apply()
                lastAppliedConfig = ""
                Thread {
                    try {
                        val currentHz = AutoOverclockManager.getCurrentRefreshRate(this)
                        val allModes = AutoOverclockManager.getSupportedModes(this)
                        if (allModes.isEmpty()) {
                            Log.w(TAG, "模式列表为空，仅设置 settings")
                            when (authMode) {
                                "root" -> RootUtils.setRate(null, 120)
                                "shizuku" -> ShizukuUtils.setRate(null, 120)
                            }
                        } else {
                            Log.i(TAG, "开始下降: currentHz=$currentHz → 120Hz")
                            when (authMode) {
                                "root" -> RootUtils.steppedDecrease(allModes, currentHz, 120, isCancelled = {
                                    prefs.getString("last_applied_config", "")?.isNotEmpty() == true
                                })
                                "shizuku" -> ShizukuUtils.steppedDecrease(allModes, currentHz, 120, isCancelled = {
                                    prefs.getString("last_applied_config", "")?.isNotEmpty() == true
                                })
                            }
                        }
                        Log.i(TAG, "已下降到 120Hz")
                    } catch (e: Exception) {
                        Log.e(TAG, "下降失败: ${e.message}")
                    }
                }.start()
            }
            return
        }

        val res = prefs.getString("app_refresh_res_$effectivePkg", "") ?: ""
        val hz = prefs.getInt("app_refresh_hz_$effectivePkg", -1)
        if (res.isEmpty() || hz <= 0) {
            Log.w(TAG, "应用 $effectivePkg 配置不完整: res=$res, hz=$hz")
            return
        }

        val configKey = "$effectivePkg@$res@$hz"
        if (configKey == lastAppliedConfig) {
            return
        }

        currentFgPackage = basePkg
        lastAppliedConfig = configKey
        prefs.edit().putString("last_applied_config", configKey).apply()
        Log.i(TAG, "自定义刷新率切换: $effectivePkg → $res @ ${hz}Hz (auth=$authMode)")
        applyDisplayTarget(authMode, res, hz, configKey)
    }

    private fun resolveEffectivePkg(prefs: SharedPreferences, basePkg: String): String {
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val procs = am.runningAppProcesses
            if (procs != null) {
                for (proc in procs) {
                    if (proc.importance == 100 && proc.pkgList != null) {
                        if (basePkg in proc.pkgList) {
                            val userId = proc.uid / 100000
                            if (userId > 0) {
                                val cloneKey = "$basePkg:u$userId"
                                if (prefs.getBoolean("app_refresh_enabled_$cloneKey", false)) {
                                    return cloneKey
                                }
                            }
                            return basePkg
                        }
                    }
                }
            }
        } catch (e: Exception) {
        }
        return basePkg
    }

    private fun applyDisplayTarget(authMode: String, res: String, hz: Int, configKey: String) {
        try {
            val wh = res.split("x")
            if (wh.size != 2) return
            val targetW = wh[0].toInt()
            val targetH = wh[1].toInt()

            val modes = cachedModes.ifEmpty { AutoOverclockManager.getSupportedModes(this) }

            var target: DisplayMode? = null
            for (m in modes) {
                if (m.width == targetW && m.height == targetH && m.rateInt == hz) {
                    target = m
                    break
                }
            }
            if (target == null) {
                for (m in modes) {
                    if (m.width == targetW && m.height == targetH) {
                        if (target == null || Math.abs(m.rateInt - hz) < Math.abs(target.rateInt - hz)) {
                            target = m
                        }
                    }
                }
            }

            if (target == null) {
                Log.w(TAG, "未找到匹配模式，仅设置 settings: $res @ ${hz}Hz")
                when (authMode) {
                    "root" -> RootUtils.setRate(null, hz)
                    "shizuku" -> ShizukuUtils.setRate(null, hz)
                }
                return
            }

            val currentHz = AutoOverclockManager.getCurrentRefreshRate(this)
            val allModes = modes
            Log.i(TAG, "开始切换: currentHz=$currentHz → targetHz=${target.rateInt}, modeId=${target.modeId}")
            when (authMode) {
                "root" -> RootUtils.steppedSwitch(target, allModes, currentHz) { lastAppliedConfig != configKey }
                "shizuku" -> ShizukuUtils.steppedSwitch(target, allModes, currentHz) { lastAppliedConfig != configKey }
            }
            Log.d(TAG, "应用刷新率已切换: $res @ ${hz}Hz (modeId=${target.modeId})")
        } catch (e: Exception) {
            Log.e(TAG, "应用刷新率切换失败: ${e.message}")
        }
    }

    private fun updatePersistentNotification(basePkg: String) {
        try {
            val prefs = getSharedPreferences("s", Context.MODE_PRIVATE) ?: return
            if (!prefs.getBoolean("custom_app_refresh", false)) {
                cancelCustomNotification()
                return
            }
            if (basePkg.isEmpty() || basePkg == "android" || basePkg == packageName) return
            if (basePkg == "com.android.systemui") return

            lastRealPkg = basePkg
            val effectivePkg = resolveEffectivePkg(prefs, basePkg)
            val enabled = prefs.getBoolean("app_refresh_enabled_$effectivePkg", false)
            val setRes = prefs.getString("app_refresh_res_$effectivePkg", "") ?: ""
            val setHz = prefs.getInt("app_refresh_hz_$effectivePkg", -1)

            val cur = getCurrentWHRate()
            val curRes = if (cur[0] > 0) "${cur[0]}×${cur[1]}" else "?"
            val curHz = cur[2]
            postCustomNotification(basePkg, enabled, setRes, setHz, curRes, curHz)
        } catch (e: Exception) {
            Log.e(TAG, "updatePersistentNotification: ${e.message}")
        }
    }

    private fun getCurrentWHRate(): IntArray {
        try {
            val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val d = dm.getDisplay(0)
            val m = d.mode
            return intArrayOf(m.physicalWidth, m.physicalHeight, Math.round(d.refreshRate))
        } catch (e: Exception) {
            return intArrayOf(0, 0, 0)
        }
    }

    private fun createCustomChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                val ch = NotificationChannel(
                    CUSTOM_CHANNEL_ID,
                    "自定义应用刷新率",
                    NotificationManager.IMPORTANCE_LOW
                )
                val nm = getSystemService(NotificationManager::class.java)
                nm?.createNotificationChannel(ch)
            } catch (e: Exception) {
            }
        }
    }

    private fun postCustomNotification(
        pkg: String, enabled: Boolean, setRes: String, setHz: Int,
        curRes: String, curHz: Int
    ) {
        try {
            var appLabel = pkg
            try {
                val pm = packageManager
                appLabel = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (e: Exception) {
            }

            val openIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val flag = if (Build.VERSION.SDK_INT >= 31) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else PendingIntent.FLAG_UPDATE_CURRENT
            val openPi = PendingIntent.getActivity(this, 3, openIntent, flag)

            val stopIntent = Intent(this, CustomRefreshActionReceiver::class.java).apply {
                action = "STOP_CUSTOM_REFRESH"
            }
            val stopPi = PendingIntent.getBroadcast(this, 4, stopIntent, flag)

            val statusLine = if (enabled) "已启用" else "未启用"
            val curLine = "当前: $curRes @ ${curHz}Hz"
            val title = "$appLabel  $statusLine"

            val big = StringBuilder()
            if (enabled && setRes.isNotEmpty() && setHz > 0) {
                big.append("设置: $setRes @ ${setHz}Hz\n")
            }
            big.append(curLine)

            val n = NotificationCompat.Builder(this, CUSTOM_CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(curLine)
                .setStyle(NotificationCompat.BigTextStyle().bigText(big.toString()).setBigContentTitle(title))
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(openPi)
                .addAction(android.R.drawable.ic_media_pause, "停止", stopPi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()

            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(CUSTOM_NOTIF_ID, n)
        } catch (e: Exception) {
            Log.e(TAG, "postCustomNotification: ${e.message}")
        }
    }

    private fun postWaitingNotification() {
        try {
            val openIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val flag = if (Build.VERSION.SDK_INT >= 31) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else PendingIntent.FLAG_UPDATE_CURRENT
            val openPi = PendingIntent.getActivity(this, 3, openIntent, flag)

            val stopIntent = Intent(this, CustomRefreshActionReceiver::class.java).apply {
                action = "STOP_CUSTOM_REFRESH"
            }
            val stopPi = PendingIntent.getBroadcast(this, 4, stopIntent, flag)

            val n = NotificationCompat.Builder(this, CUSTOM_CHANNEL_ID)
                .setContentTitle("等待应用切换")
                .setContentText("切换到已配置的应用时将自动调整刷新率")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(openPi)
                .addAction(android.R.drawable.ic_media_pause, "停止", stopPi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()

            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(CUSTOM_NOTIF_ID, n)
        } catch (e: Exception) {
        }
    }

    private fun cancelCustomNotification() {
        try {
            lastAppliedConfig = ""
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(CUSTOM_NOTIF_ID)
        } catch (e: Exception) {
        }
    }

    private fun checkAndRestartService() {
        val prefs = getSharedPreferences("s", Context.MODE_PRIVATE) ?: return
        if (!prefs.getBoolean("auto_overclock", false)) return
        val authMode = prefs.getString("auth_mode", "") ?: ""
        val ocRes = prefs.getString("oc_target_res", "") ?: ""
        val ocHz = prefs.getInt("oc_target_hz", -1)
        if (authMode.isEmpty() || ocRes.isEmpty() || ocHz < 0) return

        val wh = ocRes.split("x")
        if (wh.size < 2) return
        try {
            val tw = wh[0].toInt()
            val th = wh[1].toInt()
            val si = Intent(this, OverclockService::class.java).apply {
                putExtra("auth_mode", authMode)
                putExtra("targetW", tw)
                putExtra("targetH", th)
                putExtra("targetHz", ocHz)
            }
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(si) else startService(si)
        } catch (e: Exception) {
            Log.e(TAG, "重启服务失败: ${e.message}")
        }
    }
}
