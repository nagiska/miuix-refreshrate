package com.refreshrate.control.util

import android.content.Context
import com.refreshrate.control.model.DisplayMode
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 手动切换成功后的"有界保持"看门狗(方案 A):
 * 在 durationMs 内周期性检查 active 是否漂离目标(例如切回桌面时系统/桌面瞬提到 120),
 * 漂移则经 coordinator 用 switchRefreshRate(带 SF 1035 回退)压回目标。
 *
 * 结束条件(任一):
 *  - 达到 durationMs;
 *  - generation 被更新的请求顶掉(用户又手动切/触发 apply);
 *  - 全局锁定(GlobalOverclockService)开启(交由全局服务负责,避免双写)。
 *
 * reapply 走 submitWithoutBump,不递增代号,避免把自己顶掉。
 */
object RefreshHoldManager {
    private const val TAG = "RefreshHold"
    private const val POLL_MS = 1200L

    @Volatile
    private var holdGeneration: Long = -1L

    @Volatile
    private var thread: Thread? = null

    fun cancel() {
        holdGeneration = -1L
        thread?.interrupt()
        thread = null
    }

    fun start(context: Context, target: DisplayMode, durationMs: Long = 18_000L) {
        cancel()
        val appContext = context.applicationContext
        val capturedGen = RefreshSwitchCoordinator.currentGeneration()
        holdGeneration = capturedGen
        val t = Thread {
            val deadline = System.currentTimeMillis() + durationMs
            try {
                while (System.currentTimeMillis() < deadline && holdGeneration == capturedGen) {
                    Thread.sleep(POLL_MS)
                    if (holdGeneration != capturedGen) break
                    if (RefreshSwitchCoordinator.currentGeneration() != capturedGen) {
                        RuntimeLog.appendGlobal(
                            TAG,
                            "HOLD stop superseded gen=$capturedGen current=${RefreshSwitchCoordinator.currentGeneration()}"
                        )
                        break
                    }
                    val prefs = appContext.getSharedPreferences("s", Context.MODE_PRIVATE)
                    if (prefs.getBoolean("global_overclock", false)) {
                        RuntimeLog.appendGlobal(TAG, "HOLD stop globalLock active")
                        break
                    }
                    val allModes = AutoOverclockManager.getSupportedModes(appContext)
                    if (allModes.isEmpty()) continue
                    val current = AutoOverclockManager.getCurrentMode(appContext)
                    val matched = current != null &&
                        current.width == target.width && current.height == target.height &&
                        Math.abs(current.rateInt - target.rateInt) <= 1
                    if (matched) continue
                    val currentHz = AutoOverclockManager.getCurrentRefreshRate(appContext)
                    RuntimeLog.appendGlobal(
                        TAG,
                        "HOLD drift current=${current?.rateInt ?: -1}Hz target=${target.rateInt}Hz reapplied"
                    )
                    val latch = CountDownLatch(1)
                    RefreshSwitchCoordinator.submitWithoutBump("hold-reapply") { _, _ ->
                        try {
                            RootUtils.switchRefreshRate(target, allModes, currentHz, useSfFallback = true) { false }
                        } finally {
                            latch.countDown()
                        }
                    }
                    latch.await(4000, TimeUnit.MILLISECONDS)
                }
            } catch (e: InterruptedException) {
                RuntimeLog.appendGlobal(TAG, "HOLD interrupted gen=$capturedGen")
            } catch (e: Exception) {
                RuntimeLog.appendGlobal(TAG, "HOLD error=${e.message}")
            } finally {
                if (holdGeneration == capturedGen) {
                    RuntimeLog.appendGlobal(TAG, "HOLD done target=${target.rateInt}Hz gen=$capturedGen")
                }
            }
        }
        t.isDaemon = true
        thread = t
        t.start()
        RuntimeLog.appendGlobal(
            TAG,
            "HOLD start target=${target.resolutionLabel}@${target.rateInt}Hz duration=${durationMs}ms gen=$capturedGen"
        )
    }
}
