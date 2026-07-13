package com.refreshrate.control.service

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.core.app.NotificationCompat
import com.refreshrate.control.BuildConfig
import com.refreshrate.control.MainActivity
import com.refreshrate.control.model.DisplayMode
import com.refreshrate.control.util.AutoOverclockManager
import com.refreshrate.control.util.RootUtils
import com.refreshrate.control.util.RuntimeLog

class KeepAliveAccessibilityService : AccessibilityService() {
    companion object {
        private const val TAG = "KeepAliveA11y"
        private const val CUSTOM_CHANNEL_ID = "custom_app_channel"
        private const val CUSTOM_NOTIF_ID = 1002
        private const val KEY_LAST_APPLIED_CONFIG = "last_applied_config"
        private const val KEY_RESTORE_WIDTH = "restore_width"
        private const val KEY_RESTORE_HEIGHT = "restore_height"
        private const val KEY_RESTORE_HZ = "restore_hz"
        private const val KEY_RESTORE_MODE_ID = "restore_mode_id"
        private const val VERIFY_TIMEOUT_MS = 3000L
        private const val SWITCH_RETRY_COUNT = 3
        private const val POLL_HEARTBEAT_MS = 30_000L
    }

    private var fgHandler: Handler? = null
    private var servicePrefs: SharedPreferences? = null
    private var currentFgPackage: String = ""
    private var pendingFgPackage: String = ""
    private var lastAppliedConfig: String = ""
    private var lastRealPkg: String = ""
    private var pendingApplyRunnable: Runnable? = null
    private var pollingRunnable: Runnable? = null
    private var cachedModes: List<DisplayMode> = emptyList()
    private var restoreMode: DisplayMode? = null
    private var restoreHz: Int = -1
    private var switchThread: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var foregroundStarted: Boolean = false
    private var lastPollHeartbeatAt: Long = 0L
    @Volatile private var switchGeneration: Long = 0L
    @Volatile private var restoreInProgress: Boolean = false
    private var restoringToPackage: String = ""
    private data class RefreshVerification(
        val targetHz: Int,
        val androidHz: Int,
        val rootState: RootUtils.DisplayState,
        val matched: Boolean,
        val source: String
    ) {
        fun summary(): String {
            return "target=${targetHz}Hz android=${androidHz}Hz source=$source matched=$matched root={${rootState.summary()}}"
        }
    }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
        if ("custom_app_refresh" == key) {
            runtimeLog("PREF custom_app_refresh=${sp.getBoolean("custom_app_refresh", false)}")
            if (sp.getBoolean("custom_app_refresh", false)) {
                if (lastRealPkg.isEmpty()) {
                    ensureForeground("等待应用切换", "后台自动升降档运行中")
                } else {
                    updatePersistentNotification(lastRealPkg)
                }
                startForegroundPolling()
            } else {
                stopForegroundPolling()
                cancelCustomNotification()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        fgHandler = Handler(Looper.getMainLooper())
        Log.d(TAG, "无障碍服务已连接")
        RuntimeLog.init(this)
        runtimeLog("SERVICE connected version=${BuildConfig.VERSION_NAME} revision=${BuildConfig.BUILD_REVISION}")
        createCustomChannel()
        servicePrefs = getSharedPreferences("s", Context.MODE_PRIVATE)
        runtimeLog(
            "SERVICE state custom=${servicePrefs?.getBoolean("custom_app_refresh", false)} " +
                "root=${RootUtils.isRootAvailable()} display=${RootUtils.readDisplayState().summary()}"
        )

        servicePrefs?.registerOnSharedPreferenceChangeListener(prefListener)
        restoreStateFromPrefs(servicePrefs)

        if (servicePrefs?.getBoolean("custom_app_refresh", false) == true) {
            ensureForeground("等待应用切换", "后台自动升降档运行中")
            startForegroundPolling()
        }

        Thread {
            try {
                cachedModes = AutoOverclockManager.getSupportedModes(this)
                Log.d(TAG, "预扫描模式完成: ${cachedModes.size} 个模式")
                runtimeLog("MODE cached=${cachedModes.size}")
            } catch (e: Exception) {
                Log.e(TAG, "预扫描模式失败: ${e.message}")
                runtimeLog("MODE cache failed=${e.message}")
            }
        }.start()

    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = getTopForegroundPackage(event) ?: return
        if (pkg.isEmpty()) return
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
        runtimeLog("SERVICE interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "无障碍服务销毁")
        runtimeLog("SERVICE destroyed")
        servicePrefs?.unregisterOnSharedPreferenceChangeListener(prefListener)
        stopForegroundPolling()
    }

    private fun applyForPackage(basePkg: String) {
        if (basePkg.isEmpty()) return
        val prefs = getSharedPreferences("s", Context.MODE_PRIVATE) ?: return
        if (!prefs.getBoolean("custom_app_refresh", false)) {
            prefs.edit().remove(KEY_LAST_APPLIED_CONFIG).apply()
            lastAppliedConfig = ""
            clearRestoreState(prefs)
            return
        }

        if (isTransientForeground(basePkg)) {
            Log.i(
                TAG,
                "忽略瞬态前台窗口 base=$basePkg lastApplied=$lastAppliedConfig " +
                    "restore=${restoreMode?.resolutionLabel}@${restoreHz}Hz"
            )
            return
        }

        val effectivePkg = resolveEffectivePkg(prefs, basePkg)
        val enabled = prefs.getBoolean("app_refresh_enabled_$effectivePkg", false)
        if (basePkg != currentFgPackage) {
            runtimeLog("FG base=$basePkg effective=$effectivePkg enabled=$enabled")
            currentFgPackage = basePkg
        }

        Log.i(
            TAG,
            "applyForPackage base=$basePkg effective=$effectivePkg enabled=$enabled " +
                "custom=${prefs.getBoolean("custom_app_refresh", false)} " +
                "lastApplied=$lastAppliedConfig restore=${restoreMode?.resolutionLabel}@${restoreHz}Hz gen=$switchGeneration"
        )
        runtimeLog("STATE last=$lastAppliedConfig restore=${restoreMode?.resolutionLabel}@${restoreHz}Hz gen=$switchGeneration")

        if (!enabled) {
            val savedConfig = prefs.getString(KEY_LAST_APPLIED_CONFIG, "") ?: ""
            if (savedConfig.isNotEmpty() || lastAppliedConfig.isNotEmpty() || restoreMode != null || restoreHz > 0) {
                if (restoreInProgress) {
                    Log.i(TAG, "恢复中，忽略重复未配置应用事件 base=$basePkg effective=$effectivePkg gen=$switchGeneration")
                    runtimeLog("恢复中，忽略重复未配置应用事件 base=$basePkg effective=$effectivePkg gen=$switchGeneration")
                    return
                }
                Log.i(TAG, "离开已配置应用 $effectivePkg，恢复进入前刷新率")
                runtimeLog("RESTORE start target=${restoreMode?.resolutionLabel}@${restoreHz}Hz")
                prefs.edit().remove(KEY_LAST_APPLIED_CONFIG).apply()
                lastAppliedConfig = ""
                val generation = nextSwitchGeneration()
                restoreInProgress = true
                restoringToPackage = effectivePkg
                switchThread = Thread {
                    withSwitchWakeLock("restore") {
                    try {
                        val targetHz = restoreHz.takeIf { it > 0 } ?: 120
                        val currentHz = AutoOverclockManager.getCurrentRefreshRate(this)
                        val allModes = cachedModes.ifEmpty { AutoOverclockManager.getSupportedModes(this) }
                        var restoreTargetHz = targetHz
                        var restoreTargetMode: DisplayMode? = null
                        var restored = false
                        var lastVerification: RefreshVerification? = null
                        runtimeLog(
                            "RESTORE prepare gen=$generation target=${restoreMode?.resolutionLabel}@${targetHz}Hz " +
                                "apiCurrent=${currentHz}Hz root=${RootUtils.readDisplayState().summary()} modes=${allModes.size}"
                        )

                        if (allModes.isEmpty()) {
                            Log.w(TAG, "模式列表为空，仅设置 settings 恢复到 ${targetHz}Hz")
                            runtimeLog("RESTORE noModes target=${targetHz}Hz")
                            for (attempt in 1..SWITCH_RETRY_COUNT) {
                                if (isSwitchCancelled(generation)) break
                                val ok = RootUtils.setRate(null, targetHz)
                                val verification = waitForRefreshRate(targetHz, VERIFY_TIMEOUT_MS, "restore#$attempt", generation)
                                lastVerification = verification
                                runtimeLog("VERIFY restore attempt=$attempt setRateOk=$ok ${verification.summary()}")
                                if (verification.matched) {
                                    restored = true
                                    break
                                }
                                sleepBeforeRetry(attempt)
                            }
                        } else {
                            val currentMode = AutoOverclockManager.getCurrentMode(this)
                            val target = restoreMode?.let { RootUtils.findBestTargetForMode(allModes, it) }
                                ?: RootUtils.findBestTargetForHz(allModes, currentMode, targetHz)
                            if (target != null) {
                                restoreTargetHz = target.rateInt
                                restoreTargetMode = target
                                Log.i(
                                    TAG,
                                    "restore target=${target.resolutionLabel}@${target.rateInt}Hz modeId=${target.modeId}, currentHz=$currentHz"
                                )
                                runtimeLog(
                                    "RESTORE target=${target.resolutionLabel}@${target.rateInt}Hz modeId=${target.modeId} " +
                                        "apiCurrent=${currentHz}Hz currentMode=${currentMode?.resolutionLabel}@${currentMode?.rateInt}Hz"
                                )
                                val steppedOk = RootUtils.switchRefreshRate(target, allModes, currentHz) {
                                    isSwitchCancelled(generation)
                                }
                                runtimeLog("RESTORE stepped ok=$steppedOk gen=$generation state=${RootUtils.readDisplayState().summary()}")
                                for (attempt in 1..SWITCH_RETRY_COUNT) {
                                    if (isSwitchCancelled(generation)) break
                                    val forceOk = RootUtils.forceDisplayMode(
                                        target.width,
                                        target.height,
                                        target.rateInt,
                                        target.modeId - 1,
                                        "restore#$attempt"
                                    )
                                    val verification = waitForRefreshRate(restoreTargetHz, VERIFY_TIMEOUT_MS, "restore#$attempt", generation)
                                    lastVerification = verification
                                    runtimeLog("VERIFY restore attempt=$attempt forceOk=$forceOk ${verification.summary()}")
                                    if (verification.matched) {
                                        restored = true
                                        break
                                    }
                                    sleepBeforeRetry(attempt)
                                }
                            } else {
                                Log.w(TAG, "未找到恢复模式，仅设置 settings 恢复到 ${targetHz}Hz")
                                runtimeLog("RESTORE noTarget settingsOnly target=${targetHz}Hz")
                                for (attempt in 1..SWITCH_RETRY_COUNT) {
                                    if (isSwitchCancelled(generation)) break
                                    val ok = RootUtils.setRate(null, targetHz)
                                    val verification = waitForRefreshRate(targetHz, VERIFY_TIMEOUT_MS, "restoreSettings#$attempt", generation)
                                    lastVerification = verification
                                    runtimeLog("VERIFY restoreSettings attempt=$attempt setRateOk=$ok ${verification.summary()}")
                                    if (verification.matched) {
                                        restored = true
                                        break
                                    }
                                    sleepBeforeRetry(attempt)
                                }
                            }
                        }
                        if (!isSwitchCancelled(generation)) {
                            runtimeLog("SNAPSHOT restore final ${RootUtils.readDisplaySnapshot()}")
                            if (restored) {
                                runtimeLog("RESTORE success target=${restoreTargetHz}Hz ${lastVerification?.summary().orEmpty()}")
                                clearRestoreState(prefs)
                                restoreTargetMode?.let { scheduleRestoreWatchdog(it, generation) }
                            } else {
                                runtimeLog(
                                    "RESTORE pending target=${restoreTargetHz}Hz last=${lastVerification?.summary() ?: "none"} " +
                                        "keep retry state"
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "恢复失败: ${e.message}")
                        runtimeLog("RESTORE failed=${e.message}")
                    } finally {
                        if (!isSwitchCancelled(generation)) {
                            restoreInProgress = false
                            restoringToPackage = ""
                        }
                    }
                    }
                }.apply { start() }
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

        if (restoreMode == null) {
            restoreMode = AutoOverclockManager.getCurrentMode(this)
            restoreHz = restoreMode?.rateInt ?: AutoOverclockManager.getCurrentRefreshRate(this)
            saveRestoreState(prefs, restoreMode, restoreHz)
            Log.i(TAG, "记录进入前刷新率: ${restoreMode?.resolutionLabel ?: "?"} @ ${restoreHz}Hz")
            runtimeLog("RESTORE save ${restoreMode?.resolutionLabel ?: "?"}@${restoreHz}Hz")
        }

        lastAppliedConfig = configKey
        prefs.edit().putString(KEY_LAST_APPLIED_CONFIG, configKey).apply()
        Log.i(TAG, "自定义刷新率切换: $effectivePkg → $res @ ${hz}Hz")
        runtimeLog("APPLY pkg=$effectivePkg target=$res@${hz}Hz")
        val generation = nextSwitchGeneration()
        restoreInProgress = false
        restoringToPackage = ""
        switchThread = Thread {
            withSwitchWakeLock("apply") {
                applyDisplayTarget(res, hz, configKey, generation)
            }
        }.apply { start() }
    }

    private fun nextSwitchGeneration(): Long {
        switchGeneration += 1
        switchThread?.interrupt()
        return switchGeneration
    }

    private fun isSwitchCancelled(generation: Long): Boolean = generation != switchGeneration

    private fun isTransientForeground(pkg: String): Boolean {
        return pkg == "android" || pkg == "com.android.systemui"
    }

    private fun startForegroundPolling() {
        if (pollingRunnable != null) return
        val runnable = object : Runnable {
            override fun run() {
                try {
                    val prefs = servicePrefs ?: getSharedPreferences("s", Context.MODE_PRIVATE)
                    if (prefs.getBoolean("custom_app_refresh", false)) {
                        val focusedPkg = getFocusedPackageFromWindows()
                        val rootPkg = if (focusedPkg == null) RootUtils.getTopPackageFromWindow() else null
                        val pkg = focusedPkg ?: rootPkg
                        if (!pkg.isNullOrEmpty()) {
                            scheduleForegroundApply(pkg)
                        }
                        logPollHeartbeat(pkg ?: "unknown", if (focusedPkg != null) "a11yWindows" else "rootWindow", prefs)
                        fgHandler?.postDelayed(this, 1000L)
                    } else {
                        pollingRunnable = null
                    }
                } catch (e: Exception) {
                    runtimeLog("POLL failed=${e.message}")
                    fgHandler?.postDelayed(this, 2000L)
                }
            }
        }
        pollingRunnable = runnable
        fgHandler?.post(runnable)
        runtimeLog("POLL started")
    }

    private fun stopForegroundPolling() {
        pollingRunnable?.let { fgHandler?.removeCallbacks(it) }
        pollingRunnable = null
        runtimeLog("POLL stopped")
    }

    private fun logPollHeartbeat(topPkg: String, source: String, prefs: SharedPreferences) {
        val now = System.currentTimeMillis()
        if (now - lastPollHeartbeatAt < POLL_HEARTBEAT_MS) return
        lastPollHeartbeatAt = now
        runtimeLog(
            "POLL heartbeat top=$topPkg source=$source custom=${prefs.getBoolean("custom_app_refresh", false)} " +
                "current=$currentFgPackage pending=$pendingFgPackage last=$lastAppliedConfig " +
                "restore=${restoreMode?.resolutionLabel}@${restoreHz}Hz inProgress=$restoreInProgress gen=$switchGeneration " +
                "root=${RootUtils.isRootAvailable()} display=${RootUtils.readDisplayState().summary()}"
        )
    }

    private fun getFocusedPackageFromWindows(): String? {
        return try {
            val focused = windows?.firstOrNull { it != null && it.isFocused && it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            val root = focused?.root ?: return null
            val pkg = root.packageName?.toString()
            root.recycle()
            pkg
        } catch (e: Exception) {
            null
        }
    }

    private fun saveRestoreState(prefs: SharedPreferences, mode: DisplayMode?, hz: Int) {
        val editor = prefs.edit().putInt(KEY_RESTORE_HZ, hz)
        if (mode != null) {
            editor
                .putInt(KEY_RESTORE_WIDTH, mode.width)
                .putInt(KEY_RESTORE_HEIGHT, mode.height)
                .putInt(KEY_RESTORE_MODE_ID, mode.modeId)
        }
        editor.apply()
    }

    private fun restoreStateFromPrefs(prefs: SharedPreferences?) {
        if (prefs == null) return
        val hz = prefs.getInt(KEY_RESTORE_HZ, -1)
        val width = prefs.getInt(KEY_RESTORE_WIDTH, -1)
        val height = prefs.getInt(KEY_RESTORE_HEIGHT, -1)
        val modeId = prefs.getInt(KEY_RESTORE_MODE_ID, -1)
        if (hz > 0) {
            restoreHz = hz
            if (width > 0 && height > 0 && modeId > 0) {
                restoreMode = DisplayMode(width, height, hz.toFloat(), modeId).apply {
                    sfIndex = modeId
                }
            }
            lastAppliedConfig = prefs.getString(KEY_LAST_APPLIED_CONFIG, "") ?: ""
            Log.i(TAG, "恢复持久化状态: lastApplied=$lastAppliedConfig restore=${restoreMode?.resolutionLabel}@${restoreHz}Hz")
            runtimeLog("RESTORE persisted last=$lastAppliedConfig target=${restoreMode?.resolutionLabel}@${restoreHz}Hz")
        }
    }

    private fun clearRestoreState(prefs: SharedPreferences? = servicePrefs) {
        restoreMode = null
        restoreHz = -1
        restoreInProgress = false
        restoringToPackage = ""
        prefs?.edit()
            ?.remove(KEY_RESTORE_WIDTH)
            ?.remove(KEY_RESTORE_HEIGHT)
            ?.remove(KEY_RESTORE_HZ)
            ?.remove(KEY_RESTORE_MODE_ID)
            ?.apply()
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

    private fun applyDisplayTarget(res: String, hz: Int, configKey: String, generation: Long) {
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
                runtimeLog("未找到匹配模式，仅 settings: $res @ ${hz}Hz")
                if (!isSwitchCancelled(generation)) RootUtils.setRate(null, hz)
                return
            }

            val currentHz = AutoOverclockManager.getCurrentRefreshRate(this)
            val allModes = modes
            Log.i(TAG, "开始切换: currentHz=$currentHz → targetHz=${target.rateInt}, modeId=${target.modeId}")
            runtimeLog(
                "SWITCH start current=${currentHz}Hz target=${target.rateInt}Hz modeId=${target.modeId} " +
                    "root=${RootUtils.readDisplayState().summary()}"
            )
            val steppedOk = RootUtils.switchRefreshRate(target, allModes, currentHz) {
                isSwitchCancelled(generation) || lastAppliedConfig != configKey
            }
            if (!isSwitchCancelled(generation) && lastAppliedConfig == configKey) {
                var applied = false
                var lastVerification: RefreshVerification? = null
                for (attempt in 1..SWITCH_RETRY_COUNT) {
                    if (isSwitchCancelled(generation) || lastAppliedConfig != configKey) break
                    val forceOk = RootUtils.forceDisplayMode(target.width, target.height, target.rateInt, target.modeId - 1, "apply#$attempt")
                    val verification = waitForRefreshRate(target.rateInt, VERIFY_TIMEOUT_MS, "apply#$attempt", generation)
                    lastVerification = verification
                    runtimeLog("VERIFY apply attempt=$attempt steppedOk=$steppedOk forceOk=$forceOk ${verification.summary()}")
                    if (verification.matched) {
                        applied = true
                        break
                    }
                    sleepBeforeRetry(attempt)
                }
                Log.d(TAG, "应用刷新率切换校验: $res @ ${hz}Hz (modeId=${target.modeId}) applied=$applied")
                runtimeLog("SNAPSHOT apply final ${RootUtils.readDisplaySnapshot()}")
                if (applied) {
                    runtimeLog("APPLY success target=${target.rateInt}Hz ${lastVerification?.summary().orEmpty()}")
                } else {
                    runtimeLog("APPLY pending target=${target.rateInt}Hz last=${lastVerification?.summary() ?: "none"}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "应用刷新率切换失败: ${e.message}")
            runtimeLog("SWITCH failed=${e.message}")
        }
    }

    private fun withSwitchWakeLock(reason: String, block: () -> Unit) {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val lock = wakeLock ?: pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:refresh-$reason").also {
            it.setReferenceCounted(false)
            wakeLock = it
        }
        try {
            if (!lock.isHeld) lock.acquire(30_000L)
            runtimeLog("WAKE acquire reason=$reason")
            block()
        } finally {
            if (lock.isHeld) lock.release()
            runtimeLog("WAKE release reason=$reason")
        }
    }

    private fun waitForRefreshRate(
        targetHz: Int,
        timeoutMs: Long,
        reason: String,
        generation: Long
    ): RefreshVerification {
        val deadline = System.currentTimeMillis() + timeoutMs
        var verification = readRefreshVerification(targetHz)
        while (!verification.matched && System.currentTimeMillis() < deadline && !isSwitchCancelled(generation)) {
            try { Thread.sleep(250) } catch (e: InterruptedException) { break }
            verification = readRefreshVerification(targetHz)
        }
        runtimeLog("VERIFY wait reason=$reason gen=$generation ${verification.summary()}")
        return verification
    }

    private fun readRefreshVerification(targetHz: Int): RefreshVerification {
        val androidHz = AutoOverclockManager.getCurrentRefreshRate(this)
        val rootState = RootUtils.readDisplayState()
        val source = if (rootState.hasRefreshEvidence()) "root" else "androidFallback"
        val matched = if (rootState.hasRefreshEvidence()) {
            rootState.matchesTarget(targetHz)
        } else {
            isRefreshRateMatched(androidHz, targetHz)
        }
        return RefreshVerification(targetHz, androidHz, rootState, matched, source)
    }

    private fun sleepBeforeRetry(attempt: Int) {
        if (attempt >= SWITCH_RETRY_COUNT) return
        try { Thread.sleep(600) } catch (e: InterruptedException) { }
    }

    private fun scheduleRestoreWatchdog(target: DisplayMode, generation: Long) {
        Thread {
            try { Thread.sleep(500) } catch (e: InterruptedException) { return@Thread }
            withSwitchWakeLock("restore-watchdog") {
                val delays = listOf(1500L, 3000L, 5000L)
                for ((index, delayMs) in delays.withIndex()) {
                    try { Thread.sleep(delayMs) } catch (e: InterruptedException) { return@withSwitchWakeLock }
                    if (isSwitchCancelled(generation)) {
                        runtimeLog("POST_RESTORE cancelled gen=$generation currentGen=$switchGeneration")
                        return@withSwitchWakeLock
                    }
                    val before = readRefreshVerification(target.rateInt)
                    runtimeLog("POST_RESTORE check=${index + 1} delay=${delayMs}ms ${before.summary()}")
                    if (!before.matched) {
                        val forceOk = RootUtils.forceDisplayMode(
                            target.width,
                            target.height,
                            target.rateInt,
                            target.modeId - 1,
                            "postRestore#${index + 1}"
                        )
                        val after = waitForRefreshRate(
                            target.rateInt,
                            VERIFY_TIMEOUT_MS,
                            "postRestore#${index + 1}",
                            generation
                        )
                        runtimeLog("POST_RESTORE reapplied=${index + 1} forceOk=$forceOk ${after.summary()}")
                    }
                }
            }
        }.start()
    }

    private fun isRefreshRateMatched(currentHz: Int, targetHz: Int): Boolean {
        return currentHz > 0 && kotlin.math.abs(currentHz - targetHz) <= 1
    }

    private fun runtimeLog(message: String) {
        RuntimeLog.append(this, TAG, message)
    }

    private fun updatePersistentNotification(basePkg: String) {
        try {
            val prefs = getSharedPreferences("s", Context.MODE_PRIVATE) ?: return
            if (!prefs.getBoolean("custom_app_refresh", false)) {
                cancelCustomNotification()
                return
            }
            if (basePkg.isEmpty() || basePkg == packageName || isTransientForeground(basePkg)) return

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

            startOrUpdateForeground(n)
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

            startOrUpdateForeground(n)
        } catch (e: Exception) {
        }
    }

    private fun ensureForeground(title: String, content: String) {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flag = if (Build.VERSION.SDK_INT >= 31) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else PendingIntent.FLAG_UPDATE_CURRENT
        val openPi = PendingIntent.getActivity(this, 3, openIntent, flag)
        val n = NotificationCompat.Builder(this, CUSTOM_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(openPi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        startOrUpdateForeground(n)
    }

    private fun startOrUpdateForeground(notification: android.app.Notification) {
        try {
            if (!foregroundStarted) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(CUSTOM_NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                } else {
                    startForeground(CUSTOM_NOTIF_ID, notification)
                }
                foregroundStarted = true
                runtimeLog("SERVICE foreground started")
            } else {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(CUSTOM_NOTIF_ID, notification)
            }
        } catch (e: Exception) {
            runtimeLog("SERVICE foreground failed=${e.message}")
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(CUSTOM_NOTIF_ID, notification)
        }
    }

    private fun cancelCustomNotification() {
        try {
            nextSwitchGeneration()
            lastAppliedConfig = ""
            servicePrefs?.edit()?.remove(KEY_LAST_APPLIED_CONFIG)?.apply()
            clearRestoreState(servicePrefs)
            if (foregroundStarted) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                foregroundStarted = false
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(CUSTOM_NOTIF_ID)
        } catch (e: Exception) {
        }
    }
}
