package com.pmahz.util

import android.util.Log
import com.pmahz.model.DisplayMode
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

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
            process.waitFor()
            process.exitValue() == 0
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
            val fps = match.groupValues[4].toFloatOrNull()?.toInt() ?: continue
            if (fps in 30..300) {
                val dm = DisplayMode(w, h, fps.toFloat(), id)
                dm.sfIndex = id - 1
                modes.add(dm)
            }
        }
        return modes.sortedBy { it.rateInt }
    }

    fun setRate(dumpsysModeId: Int?, targetHz: Int): Boolean {
        val script = buildString {
            if (dumpsysModeId != null && dumpsysModeId > 0) {
                appendLine("service call SurfaceFlinger 1035 i32 ${dumpsysModeId - 1}")
            }
            appendLine("settings put system peak_refresh_rate ${targetHz}.0")
            appendLine("settings put system min_refresh_rate ${targetHz}.0")
            appendLine("settings put system user_refresh_rate $targetHz")
            appendLine("settings put secure miui_refresh_rate $targetHz")
            appendLine("settings put system thermal_limit_refresh_rate $targetHz 2>/dev/null")
        }
        return execRoot(script.trimEnd())
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

    fun steppedSwitch(targetMode: DisplayMode, allModes: List<DisplayMode>, currentHz: Int) {
        val targetHz = targetMode.rateInt
        if (currentHz < targetHz) {
            val steps = allModes
                .filter { it.rateInt in (currentHz + 1)..targetHz }
                .sortedBy { it.rateInt }
            for (step in steps) {
                setDisplayMode(step.width, step.height, step.rateInt, step.sfIndex)
                try { Thread.sleep(800) } catch (e: InterruptedException) { break }
            }
        } else {
            setDisplayMode(targetMode.width, targetMode.height, targetMode.rateInt, targetMode.sfIndex)
        }
    }
}
