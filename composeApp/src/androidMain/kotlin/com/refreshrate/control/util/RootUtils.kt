package com.refreshrate.control.util

import android.util.Log
import com.refreshrate.control.model.DisplayMode
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import kotlin.math.abs
import kotlin.math.roundToInt

object RootUtils {
    private const val TAG = "RootUtils"
    private val RECORD_PATTERN = Regex("""id=(\d+),\s*width=(\d+),\s*height=(\d+),\s*fps=([\d.]+)""")

    fun isRootAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val stdin = DataOutputStream(process.outputStream)
            stdin.writeBytes("echo RootOK\nexit\n")
            stdin.flush()
            process.waitFor()
            val out = BufferedReader(InputStreamReader(process.inputStream)).readText().trim()
            "RootOK" == out
        } catch (e: Exception) {
            false
        }
    }

    fun execRoot(script: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val stdin = DataOutputStream(process.outputStream)
            stdin.writeBytes("$script\nexit\n")
            stdin.flush()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                val err = BufferedReader(InputStreamReader(process.errorStream)).readText().trim()
                Log.e(TAG, "execRoot FAILED: exitCode=$exitCode, err=$err, script=${script.take(200)}")
            }
            exitCode == 0
        } catch (e: Exception) {
            Log.e(TAG, "execRoot failed: ${e.message}")
            false
        }
    }

    fun execRootForOutput(script: String): String {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val stdin = DataOutputStream(process.outputStream)
            stdin.writeBytes("$script\nexit\n")
            stdin.flush()
            process.waitFor()
            BufferedReader(InputStreamReader(process.inputStream)).readText().trim()
        } catch (e: Exception) {
            Log.e(TAG, "execRootForOutput failed: ${e.message}")
            ""
        }
    }

    fun scanModesFromDumpsys(): List<DisplayMode> {
        val output = execRootForOutput("dumpsys display | grep 'DisplayModeRecord'")
        if (output.isBlank()) return emptyList()

        val modes = mutableListOf<DisplayMode>()
        for (line in output.lines()) {
            val match = RECORD_PATTERN.find(line) ?: continue
            val id = match.groupValues[1].toIntOrNull() ?: continue
            val w = match.groupValues[2].toIntOrNull() ?: continue
            val h = match.groupValues[3].toIntOrNull() ?: continue
            val fps = match.groupValues[4].toFloatOrNull()?.roundToInt() ?: continue
            if (fps in 30..300) {
                val dm = DisplayMode(w, h, fps.toFloat(), id)
                dm.sfIndex = id
                modes.add(dm)
            }
        }
        return modes.sortedBy { it.rateInt }
    }

    fun setRate(dumpsysModeId: Int?, targetHz: Int): Boolean {
        val sfIndex = if (dumpsysModeId != null && dumpsysModeId > 0) dumpsysModeId - 1 else null
        Log.d(TAG, "setRate: modeId=$dumpsysModeId, hz=$targetHz, sfIndex=$sfIndex")
        val script = buildString {
            if (sfIndex != null) {
                appendLine("service call SurfaceFlinger 1035 i32 $sfIndex")
            }
            appendLine("settings put system peak_refresh_rate ${targetHz}.0")
            appendLine("settings put system min_refresh_rate ${targetHz}.0")
            appendLine("settings put system user_refresh_rate $targetHz")
            appendLine("settings put secure miui_refresh_rate $targetHz")
            appendLine("settings put system thermal_limit_refresh_rate $targetHz 2>/dev/null")
        }
        val result = execRoot(script.trimEnd())
        Log.d(TAG, "setRate result: $result")
        return result
    }

    fun setDisplayMode(width: Int, height: Int, hz: Int, sfIndex: Int): Boolean {
        val script = buildString {
            if (width > 0 && height > 0 && hz > 0) {
                appendLine("cmd display set-user-preferred-display-mode $width $height $hz 2>/dev/null")
            }
            appendLine("settings put system peak_refresh_rate ${hz}.0")
            appendLine("settings put system min_refresh_rate ${hz}.0")
            appendLine("settings put system user_refresh_rate $hz")
            appendLine("settings put secure miui_refresh_rate $hz")
            appendLine("settings put system thermal_limit_refresh_rate $hz 2>/dev/null")
            if (sfIndex >= 0) {
                appendLine("service call SurfaceFlinger 1035 i32 $sfIndex")
            }
        }
        return execRoot(script.trimEnd())
    }

    fun clearDisplayMode(): Boolean {
        return execRoot("cmd display clear-user-preferred-display-mode 2>/dev/null")
    }

    fun restoreAdaptive(minHz: Int, maxHz: Int): Boolean {
        val script = buildString {
            appendLine("cmd display clear-user-preferred-display-mode 2>/dev/null")
            if (minHz > 0) appendLine("settings put system min_refresh_rate ${minHz}.0")
            if (maxHz > 0) appendLine("settings put system peak_refresh_rate ${maxHz}.0")
        }
        return execRoot(script.trimEnd())
    }

    fun setNativeRefreshOverlay(on: Boolean): Boolean {
        val valInt = if (on) 1 else 0
        return execRoot("service call SurfaceFlinger 1034 i32 $valInt")
    }

    fun steppedSwitch(targetMode: DisplayMode, allModes: List<DisplayMode>, currentHz: Int, isCancelled: () -> Boolean = { false }) {
        switchRefreshRate(targetMode, allModes, currentHz, isCancelled)
    }

    fun switchRefreshRate(targetMode: DisplayMode, allModes: List<DisplayMode>, currentHz: Int, isCancelled: () -> Boolean = { false }) {
        val targetHz = targetMode.rateInt
        if (currentHz < targetHz) {
            val steps = allModes
                .filter { it.width == targetMode.width && it.height == targetMode.height }
                .filter { it.rateInt > currentHz && it.rateInt <= targetHz }
                .sortedBy { it.rateInt }
            Log.d(TAG, "steppedSwitch: currentHz=$currentHz → targetHz=$targetHz, res=${targetMode.width}x${targetMode.height}, steps=${steps.map { it.rateInt }}")
            for (step in steps) {
                if (isCancelled()) {
                    Log.d(TAG, "steppedSwitch cancelled at ${step.rateInt}Hz")
                    return
                }
                Log.d(TAG, "stepping to ${step.rateInt}Hz (modeId=${step.modeId})")
                setRate(step.modeId, step.rateInt)
                try { Thread.sleep(800) } catch (e: InterruptedException) { break }
            }
        } else if (currentHz > targetHz) {
            steppedDecrease(allModes, currentHz, targetHz, isCancelled, targetMode)
        } else {
            Log.d(TAG, "direct switch to ${targetHz}Hz (modeId=${targetMode.modeId})")
        }
        if (!isCancelled()) {
            setRate(targetMode.modeId, targetHz)
        }
        Log.d(TAG, "switchRefreshRate complete: target=${targetHz}Hz")
    }

    fun steppedDecrease(allModes: List<DisplayMode>, currentHz: Int, targetHz: Int, isCancelled: () -> Boolean = { false }, targetMode: DisplayMode? = null) {
        if (currentHz <= targetHz) return
        val resFilter = if (targetMode != null) {
            allModes.filter { it.width == targetMode.width && it.height == targetMode.height }
        } else {
            allModes
        }
        val steps = resFilter
            .filter { it.rateInt in targetHz until currentHz }
            .sortedByDescending { it.rateInt }
        Log.d(TAG, "steppedDecrease: currentHz=$currentHz → targetHz=$targetHz, steps=${steps.map { it.rateInt }}")
        for (step in steps) {
            if (isCancelled()) return
            Log.d(TAG, "stepping down to ${step.rateInt}Hz (modeId=${step.modeId})")
            setRate(step.modeId, step.rateInt)
            try { Thread.sleep(500) } catch (e: InterruptedException) { break }
        }
    }

    fun findBestTargetForHz(allModes: List<DisplayMode>, currentMode: DisplayMode?, targetHz: Int): DisplayMode? {
        if (allModes.isEmpty()) return null
        val sameResolution = if (currentMode != null) {
            allModes.filter { it.width == currentMode.width && it.height == currentMode.height }
        } else {
            emptyList()
        }
        val candidates = sameResolution.ifEmpty { allModes }
        return candidates
            .filter { it.rateInt <= targetHz }
            .maxByOrNull { it.rateInt }
            ?: candidates.minByOrNull { abs(it.rateInt - targetHz) }
    }
}
