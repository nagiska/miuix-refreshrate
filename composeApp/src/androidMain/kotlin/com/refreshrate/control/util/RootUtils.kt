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
    private val NUMBER_PATTERN = Regex("""-?\d+(?:\.\d+)?""")

    data class RootCommandResult(
        val ok: Boolean,
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
        val label: String
    )

    data class DisplayState(
        val peakHz: Int?,
        val minHz: Int?,
        val userHz: Int?,
        val miuiHz: Int?,
        val preferredHz: Int?,
        val activeModeId: Int?,
        val activeWidth: Int?,
        val activeHeight: Int?,
        val activeHz: Int?,
        val physicalHz: Int?,
        val raw: String
    ) {
        fun hasRefreshEvidence(): Boolean {
            return physicalHz != null || activeHz != null || preferredHz != null || userHz != null ||
                peakHz != null || minHz != null || miuiHz != null
        }

        fun matchesTarget(targetHz: Int): Boolean {
            if (physicalHz != null) return hzMatches(physicalHz, targetHz)
            if (activeHz != null) return hzMatches(activeHz, targetHz)
            if (preferredHz != null) return hzMatches(preferredHz, targetHz)
            val settings = listOfNotNull(userHz, peakHz, minHz, miuiHz)
            return settings.isNotEmpty() && settings.all { hzMatches(it, targetHz) }
        }

        fun summary(): String {
            val activeRes = if (activeWidth != null && activeHeight != null) {
                "${activeWidth}x${activeHeight}"
            } else {
                "?"
            }
            return "physical=${physicalHz ?: "?"}Hz active=${activeHz ?: "?"}Hz res=$activeRes modeId=${activeModeId ?: "?"} " +
                "preferred=${preferredHz ?: "?"} peak=${peakHz ?: "?"} min=${minHz ?: "?"} " +
                "user=${userHz ?: "?"} miui=${miuiHz ?: "?"}"
        }
    }

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
        return execRootDetailed(script).ok
    }

    fun execRootDetailed(script: String, label: String = firstCommand(script)): RootCommandResult {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val stdin = DataOutputStream(process.outputStream)
            stdin.writeBytes("$script\nexit\n")
            stdin.flush()
            val exitCode = process.waitFor()
            val out = BufferedReader(InputStreamReader(process.inputStream)).readText().trim()
            val err = BufferedReader(InputStreamReader(process.errorStream)).readText().trim()
            val result = RootCommandResult(exitCode == 0, exitCode, out, err, label)
            logRootResult(result)
            result
        } catch (e: Exception) {
            Log.e(TAG, "execRoot failed: ${e.message}")
            RuntimeLog.appendGlobal(TAG, "ROOT exception label=$label error=${e.message}")
            RootCommandResult(false, -1, "", e.message ?: "unknown", label)
        }
    }

    fun execRootForOutput(script: String, log: Boolean = false, label: String = firstCommand(script)): String {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val stdin = DataOutputStream(process.outputStream)
            stdin.writeBytes("$script\nexit\n")
            stdin.flush()
            val exitCode = process.waitFor()
            val out = BufferedReader(InputStreamReader(process.inputStream)).readText().trim()
            val err = BufferedReader(InputStreamReader(process.errorStream)).readText().trim()
            if (log || exitCode != 0 || err.isNotBlank()) {
                logRootResult(RootCommandResult(exitCode == 0, exitCode, out, err, label))
            }
            out
        } catch (e: Exception) {
            Log.e(TAG, "execRootForOutput failed: ${e.message}")
            RuntimeLog.appendGlobal(TAG, "ROOT output exception label=$label error=${e.message}")
            ""
        }
    }

    fun scanModesFromDumpsys(): List<DisplayMode> {
        val output = execRootForOutput("dumpsys display | grep 'DisplayModeRecord'", label = "scanModes")
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
        val result = execRootDetailed(script.trimEnd(), "setRate:${targetHz}Hz modeId=${dumpsysModeId ?: "none"} sfIndex=${sfIndex ?: "none"}")
        Log.d(TAG, "setRate result: ${result.ok}")
        return result.ok
    }

    fun setDisplayMode(width: Int, height: Int, hz: Int, sfIndex: Int): Boolean {
        RuntimeLog.appendGlobal(TAG, "SWITCH setDisplayMode ${width}x$height@${hz}Hz sfIndex=$sfIndex")
        val script = buildString {
            if (width > 0 && height > 0 && hz > 0) {
                appendLine("cmd display set-user-preferred-display-mode $width $height $hz")
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
        return execRootDetailed(script.trimEnd(), "setDisplay:${width}x$height@${hz}Hz sfIndex=$sfIndex").ok
    }

    fun forceDisplayMode(width: Int, height: Int, hz: Int, sfIndex: Int, reason: String): Boolean {
        RuntimeLog.appendGlobal(TAG, "FORCE start reason=$reason ${width}x$height@${hz}Hz sfIndex=$sfIndex pre=${readDisplayState().summary()}")
        var ok = setDisplayMode(width, height, hz, sfIndex)
        try { Thread.sleep(350) } catch (e: InterruptedException) { return ok }
        val script = buildString {
            if (sfIndex >= 0) {
                appendLine("service call SurfaceFlinger 1035 i32 $sfIndex")
            }
            appendLine("cmd display clear-user-preferred-display-mode")
            appendLine("cmd display set-user-preferred-display-mode $width $height $hz")
            appendLine("settings put system peak_refresh_rate ${hz}.0")
            appendLine("settings put system min_refresh_rate ${hz}.0")
            appendLine("settings put system user_refresh_rate $hz")
            appendLine("settings put secure miui_refresh_rate $hz")
            appendLine("settings put system thermal_limit_refresh_rate $hz 2>/dev/null")
            if (sfIndex >= 0) {
                appendLine("service call SurfaceFlinger 1035 i32 $sfIndex")
            }
        }
        val result = execRootDetailed(script.trimEnd(), "force:$reason ${width}x$height@${hz}Hz sfIndex=$sfIndex")
        ok = result.ok && ok
        RuntimeLog.appendGlobal(TAG, "FORCE done reason=$reason ok=$ok state=${readDisplayState().summary()} snapshot=${readDisplaySnapshot()}")
        return ok
    }

    fun readDisplayState(): DisplayState {
        val output = execRootForOutput(displayStateScript(), label = "displayState")
        return parseDisplayState(output)
    }

    fun readDisplaySnapshot(): String {
        return execRootForOutput(displayStateScript(), label = "displaySnapshot").lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" | ")
            .take(1800)
    }

    fun getTopPackageFromWindow(): String? {
        val output = execRootForOutput(
            "dumpsys window 2>/dev/null | grep -E 'mCurrentFocus|mFocusedApp' | head -n 3",
            label = "topPackage"
        )
        if (output.isBlank()) return null
        val packagePattern = Regex("([a-zA-Z0-9_]+\\.)+[a-zA-Z0-9_]+")
        return packagePattern.findAll(output)
            .map { it.value }
            .firstOrNull { pkg ->
                pkg != "com.android.systemui" && pkg != "android" && !pkg.startsWith("com.android.server")
            }
    }

    fun clearDisplayMode(): Boolean {
        return execRootDetailed("cmd display clear-user-preferred-display-mode", "clearDisplayMode").ok
    }

    fun restoreAdaptive(minHz: Int, maxHz: Int): Boolean {
        val script = buildString {
            appendLine("cmd display clear-user-preferred-display-mode")
            if (minHz > 0) appendLine("settings put system min_refresh_rate ${minHz}.0")
            if (maxHz > 0) appendLine("settings put system peak_refresh_rate ${maxHz}.0")
        }
        return execRootDetailed(script.trimEnd(), "restoreAdaptive min=$minHz max=$maxHz").ok
    }

    fun setNativeRefreshOverlay(on: Boolean): Boolean {
        val valInt = if (on) 1 else 0
        return execRootDetailed("service call SurfaceFlinger 1034 i32 $valInt", "nativeRefreshOverlay=$on").ok
    }

    fun steppedSwitch(targetMode: DisplayMode, allModes: List<DisplayMode>, currentHz: Int, isCancelled: () -> Boolean = { false }): Boolean {
        return switchRefreshRate(targetMode, allModes, currentHz, isCancelled)
    }

    fun switchRefreshRate(targetMode: DisplayMode, allModes: List<DisplayMode>, currentHz: Int, isCancelled: () -> Boolean = { false }): Boolean {
        val targetHz = targetMode.rateInt
        var ok = true
        if (currentHz < targetHz) {
            val steps = allModes
                .filter { it.width == targetMode.width && it.height == targetMode.height }
                .filter { it.rateInt > currentHz && it.rateInt <= targetHz }
                .sortedBy { it.rateInt }
            Log.d(TAG, "steppedSwitch: currentHz=$currentHz → targetHz=$targetHz, res=${targetMode.width}x${targetMode.height}, steps=${steps.map { it.rateInt }}")
            RuntimeLog.appendGlobal(TAG, "STEP up current=${currentHz}Hz target=${targetHz}Hz steps=${steps.map { it.rateInt }}")
            for (step in steps) {
                if (isCancelled()) {
                    Log.d(TAG, "steppedSwitch cancelled at ${step.rateInt}Hz")
                    RuntimeLog.appendGlobal(TAG, "STEP up cancelled at=${step.rateInt}Hz target=${targetHz}Hz")
                    return false
                }
                Log.d(TAG, "stepping to ${step.rateInt}Hz (modeId=${step.modeId})")
                val stepOk = setRate(step.modeId, step.rateInt)
                ok = stepOk && ok
                RuntimeLog.appendGlobal(TAG, "STEP up set=${step.rateInt}Hz modeId=${step.modeId} ok=$stepOk")
                try { Thread.sleep(800) } catch (e: InterruptedException) { break }
            }
        } else if (currentHz > targetHz) {
            ok = steppedDecrease(allModes, currentHz, targetHz, isCancelled, targetMode) && ok
        } else {
            Log.d(TAG, "direct switch to ${targetHz}Hz (modeId=${targetMode.modeId})")
            RuntimeLog.appendGlobal(TAG, "STEP direct target=${targetHz}Hz modeId=${targetMode.modeId}")
        }
        if (!isCancelled()) {
            val finalOk = setRate(targetMode.modeId, targetHz)
            ok = finalOk && ok
            RuntimeLog.appendGlobal(TAG, "STEP final target=${targetHz}Hz modeId=${targetMode.modeId} ok=$finalOk")
        }
        Log.d(TAG, "switchRefreshRate complete: target=${targetHz}Hz ok=$ok")
        return ok
    }

    fun steppedDecrease(allModes: List<DisplayMode>, currentHz: Int, targetHz: Int, isCancelled: () -> Boolean = { false }, targetMode: DisplayMode? = null): Boolean {
        if (currentHz <= targetHz) return true
        var ok = true
        val resFilter = if (targetMode != null) {
            allModes.filter { it.width == targetMode.width && it.height == targetMode.height }
        } else {
            allModes
        }
        val steps = resFilter
            .filter { it.rateInt in targetHz until currentHz }
            .sortedByDescending { it.rateInt }
        Log.d(TAG, "steppedDecrease: currentHz=$currentHz → targetHz=$targetHz, steps=${steps.map { it.rateInt }}")
        RuntimeLog.appendGlobal(TAG, "STEP down current=${currentHz}Hz target=${targetHz}Hz steps=${steps.map { it.rateInt }}")
        for (step in steps) {
            if (isCancelled()) {
                RuntimeLog.appendGlobal(TAG, "STEP down cancelled at=${step.rateInt}Hz target=${targetHz}Hz")
                return false
            }
            Log.d(TAG, "stepping down to ${step.rateInt}Hz (modeId=${step.modeId})")
            val stepOk = setDisplayMode(step.width, step.height, step.rateInt, step.modeId - 1)
            ok = stepOk && ok
            RuntimeLog.appendGlobal(TAG, "STEP down set=${step.rateInt}Hz modeId=${step.modeId} ok=$stepOk state=${readDisplayState().summary()}")
            try { Thread.sleep(800) } catch (e: InterruptedException) { break }
        }
        return ok
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

    fun findBestTargetForMode(allModes: List<DisplayMode>, mode: DisplayMode): DisplayMode? {
        if (allModes.isEmpty()) return null
        val sameResolution = allModes.filter { it.width == mode.width && it.height == mode.height }
        return sameResolution.firstOrNull { it.rateInt == mode.rateInt }
            ?: sameResolution.minByOrNull { abs(it.rateInt - mode.rateInt) }
            ?: allModes.minByOrNull { abs(it.rateInt - mode.rateInt) }
    }

    private fun parseDisplayState(output: String): DisplayState {
        val activeModeId = Regex("""mActiveModeId=(\d+)""").find(output)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("""activeModeId=(\d+)""").find(output)?.groupValues?.get(1)?.toIntOrNull()
        val records = RECORD_PATTERN.findAll(output).associate { match ->
            val id = match.groupValues[1].toInt()
            id to ModeRecord(
                match.groupValues[2].toInt(),
                match.groupValues[3].toInt(),
                match.groupValues[4].toFloat().roundToInt()
            )
        }
        val activeRecord = activeModeId?.let { records[it] }
        val activeHz = activeRecord?.hz
            ?: Regex("""mRefreshRate=([\d.]+)""").find(output)?.groupValues?.get(1)?.toFloatOrNull()?.roundToInt()
            ?: Regex("""refreshRate=([\d.]+)""").find(output)?.groupValues?.get(1)?.toFloatOrNull()?.roundToInt()

        return DisplayState(
            peakHz = parseSimpleHz(output, "peak"),
            minHz = parseSimpleHz(output, "min"),
            userHz = parseSimpleHz(output, "user"),
            miuiHz = parseSimpleHz(output, "miui"),
            preferredHz = parsePreferredHz(output),
            activeModeId = activeModeId,
            activeWidth = activeRecord?.width,
            activeHeight = activeRecord?.height,
            activeHz = activeHz,
            physicalHz = parsePhysicalHz(output),
            raw = output
        )
    }

    private fun parseSimpleHz(output: String, key: String): Int? {
        val value = output.lineSequence()
            .firstOrNull { it.startsWith("$key=") }
            ?.substringAfter("=")
            ?.trim()
            ?: return null
        return NUMBER_PATTERN.find(value)?.value?.toFloatOrNull()?.roundToInt()
    }

    private fun parsePreferredHz(output: String): Int? {
        val line = output.lineSequence().firstOrNull { it.startsWith("preferred=") } ?: return null
        Regex("""User preferred display mode:\s+\d+\s+\d+\s+([\d.]+)""")
            .find(line)
            ?.groupValues
            ?.get(1)
            ?.toFloatOrNull()
            ?.roundToInt()
            ?.let { return it }
        return Regex("""([\d.]+)\s*Hz""").find(line)?.groupValues?.get(1)?.toFloatOrNull()?.roundToInt()
    }

    private fun parsePhysicalHz(output: String): Int? {
        val periodNs = output.lineSequence()
            .firstOrNull { it.startsWith("sfPeriodNs=") }
            ?.substringAfter("=")
            ?.trim()
            ?.toLongOrNull()
            ?: return null
        if (periodNs !in 3_000_000L..50_000_000L) return null
        return (1_000_000_000.0 / periodNs.toDouble()).roundToInt()
    }

    private fun displayStateScript(): String {
        return """
            echo peak=${'$'}(settings get system peak_refresh_rate 2>/dev/null)
            echo min=${'$'}(settings get system min_refresh_rate 2>/dev/null)
            echo user=${'$'}(settings get system user_refresh_rate 2>/dev/null)
            echo miui=${'$'}(settings get secure miui_refresh_rate 2>/dev/null)
            echo preferred=${'$'}(cmd display get-user-preferred-display-mode 2>/dev/null)
            echo sfPeriodNs=${'$'}(dumpsys SurfaceFlinger --latency 2>/dev/null | head -n 1)
            dumpsys display 2>/dev/null | grep -E 'mActiveMode|activeMode|DisplayModeRecord|mModeId|mRefreshRate|refreshRate' | head -n 80
            dumpsys SurfaceFlinger 2>/dev/null | grep -iE 'refresh.?rate|vsync.*period|active.*config|active.*mode' | head -n 30
        """.trimIndent()
    }

    private fun logRootResult(result: RootCommandResult) {
        val out = shorten(result.stdout)
        val err = shorten(result.stderr)
        if (!result.ok) {
            Log.e(TAG, "execRoot FAILED: exitCode=${result.exitCode}, err=$err, label=${result.label}")
        }
        val detail = buildString {
            append("ROOT label=${result.label} ok=${result.ok} exit=${result.exitCode}")
            if (out.isNotBlank()) append(" out=$out")
            if (err.isNotBlank()) append(" err=$err")
        }
        RuntimeLog.appendGlobal(TAG, detail)
    }

    private fun firstCommand(script: String): String {
        return script.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    }

    private fun shorten(value: String, limit: Int = 220): String {
        val compact = value.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" | ")
        return if (compact.length <= limit) compact else compact.take(limit) + "..."
    }

    private data class ModeRecord(val width: Int, val height: Int, val hz: Int)
}

private fun hzMatches(currentHz: Int, targetHz: Int): Boolean {
    return currentHz > 0 && abs(currentHz - targetHz) <= 1
}
