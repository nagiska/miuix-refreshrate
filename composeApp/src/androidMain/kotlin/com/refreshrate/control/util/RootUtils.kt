package com.refreshrate.control.util

import android.util.Log
import com.refreshrate.control.core.ModeSpec
import com.refreshrate.control.core.RefreshEvidence
import com.refreshrate.control.core.RefreshPlan
import com.refreshrate.control.core.SwitchDirection
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
        val driverHz: Int?,
        val renderedFps: Int?,
        val raw: String
    ) {
        fun hasRefreshEvidence(): Boolean {
            return driverHz != null || physicalHz != null || activeHz != null || preferredHz != null || userHz != null ||
                peakHz != null || minHz != null || miuiHz != null
        }

        /** rendered/measured fps 仅作诊断提示,不再是匹配硬否决条件。 */
        fun hasHighRateContradiction(targetHz: Int): Boolean {
            val tolerance = maxOf(6, targetHz / 20)
            return renderedFps != null && renderedFps > targetHz + tolerance
        }

        fun toEvidenceSnapshot(): RefreshEvidence.Snapshot {
            return RefreshEvidence.Snapshot(
                activeModeId = activeModeId,
                activeWidth = activeWidth,
                activeHeight = activeHeight,
                activeHz = activeHz,
                physicalHz = physicalHz,
                driverHz = driverHz,
                preferredHz = preferredHz,
                userHz = userHz,
                peakHz = peakHz,
                minHz = minHz,
                miuiHz = miuiHz,
                renderedFps = renderedFps,
                hasEvidence = hasRefreshEvidence()
            )
        }

        /** 按完整目标 mode(宽高+Hz)验证,renderedFps 仅诊断。 */
        fun matchesMode(width: Int, height: Int, hz: Int): RefreshEvidence.EvidenceResult {
            return RefreshEvidence.matchesMode(toEvidenceSnapshot(), width, height, hz)
        }

        fun matchesTarget(targetHz: Int): Boolean {
            return RefreshEvidence.matchesTarget(toEvidenceSnapshot(), targetHz)
        }

        fun summary(): String {
            val activeRes = if (activeWidth != null && activeHeight != null) {
                "${activeWidth}x${activeHeight}"
            } else {
                "?"
            }
            return "driver=${driverHz ?: "?"}Hz rendered=${renderedFps ?: "?"}fps " +
                "physical=${physicalHz ?: "?"}Hz active=${activeHz ?: "?"}Hz " +
                "res=$activeRes modeId=${activeModeId ?: "?"} " +
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
            // 保留原始浮点速率(59.94/60.0 不得合并);sfIndex 不猜测,仅已验证设备显式设置
            val fps = match.groupValues[4].toFloatOrNull() ?: continue
            if (fps in 30f..300f) {
                modes.add(DisplayMode(w, h, fps, id))
            }
        }
        return modes.sortedBy { it.rateInt }
    }

    fun setRate(mode: DisplayMode?, targetHz: Int): Boolean {
        val sfIndex = mode?.sfIndex?.takeIf { it >= 0 }
        Log.d(TAG, "setRate: modeId=${mode?.modeId}, hz=$targetHz, sfIndex=$sfIndex")
        val script = buildString {
            if (sfIndex != null) {
                appendLine("service call SurfaceFlinger 1035 i32 $sfIndex")
            }
            appendLine("settings put system peak_refresh_rate ${targetHz}.0")
            appendLine("settings put system min_refresh_rate ${targetHz}.0")
            appendLine("settings put system user_refresh_rate $targetHz")
            appendLine("settings put secure miui_refresh_rate $targetHz")
        }
        val result = execRootDetailed(script.trimEnd(), "setRate:${targetHz}Hz modeId=${mode?.modeId ?: "none"} sfIndex=${sfIndex ?: "none"}")
        Log.d(TAG, "setRate result: ${result.ok}")
        return result.ok
    }

    fun setRateDown(mode: DisplayMode?, targetHz: Int): Boolean {
        val sfIndex = mode?.sfIndex?.takeIf { it >= 0 }
        Log.d(TAG, "setRateDown: modeId=${mode?.modeId}, hz=$targetHz, sfIndex=$sfIndex")
        val script = buildString {
            // Reverse the working upshift order so min never remains above peak.
            appendLine("settings put secure miui_refresh_rate $targetHz")
            appendLine("settings put system user_refresh_rate $targetHz")
            appendLine("settings put system min_refresh_rate ${targetHz}.0")
            appendLine("settings put system peak_refresh_rate ${targetHz}.0")
            if (sfIndex != null) {
                appendLine("service call SurfaceFlinger 1035 i32 $sfIndex")
            }
        }
        val result = execRootDetailed(
            script.trimEnd(),
            "setRateDown:${targetHz}Hz modeId=${mode?.modeId ?: "none"} sfIndex=${sfIndex ?: "none"}"
        )
        Log.d(TAG, "setRateDown result: ${result.ok}")
        return result.ok
    }

    fun setPreferredMode(width: Int, height: Int, hz: Int): Boolean {
        if (width <= 0 || height <= 0 || hz <= 0) return false
        RuntimeLog.appendGlobal(TAG, "SWITCH setPreferredMode ${width}x$height@${hz}Hz")
        return execRootDetailed(
            "cmd display set-user-preferred-display-mode $width $height $hz",
            "setPreferred:${width}x$height@${hz}Hz"
        ).ok
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
            if (sfIndex >= 0) {
                appendLine("service call SurfaceFlinger 1035 i32 $sfIndex")
            }
        }
        return execRootDetailed(script.trimEnd(), "setDisplay:${width}x$height@${hz}Hz sfIndex=$sfIndex").ok
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

    /**
     * 统一切换流程:升档、降档、同档都按 RefreshPlan 生成步进,
     * 并在未取消时对目标 DisplayMode 做一次完整最终提交
     * (preferred mode + 必要 settings + 受控 SF fallback),记录目标 modeId。
     */
    fun switchRefreshRate(targetMode: DisplayMode, allModes: List<DisplayMode>, currentHz: Int, isCancelled: () -> Boolean = { false }): Boolean {
        val targetHz = targetMode.rateInt
        val plan = RefreshPlan.plan(
            target = targetMode.toModeSpec(),
            allModes = allModes.map { it.toModeSpec() },
            currentHz = currentHz
        )
        var ok = true
        Log.d(
            TAG,
            "switchRefreshRate: direction=${plan.direction} currentHz=$currentHz → targetHz=$targetHz, " +
                "res=${targetMode.width}x${targetMode.height}, steps=${plan.steps.map { it.rateInt }}"
        )
        RuntimeLog.appendGlobal(
            TAG,
            "STEP ${plan.direction} current=${currentHz}Hz target=${targetHz}Hz steps=${plan.steps.map { it.rateInt }}"
        )
        for (stepSpec in plan.steps) {
            if (isCancelled()) {
                Log.d(TAG, "switchRefreshRate cancelled at ${stepSpec.rateInt}Hz")
                RuntimeLog.appendGlobal(TAG, "STEP ${plan.direction} cancelled at=${stepSpec.rateInt}Hz target=${targetHz}Hz")
                return false
            }
            val stepMode = allModes.firstOrNull { it.modeId == stepSpec.modeId } ?: targetMode
            val stepOk = if (plan.direction == SwitchDirection.UP) {
                setRate(stepMode, stepMode.rateInt)
            } else {
                setRateDown(stepMode, stepMode.rateInt)
            }
            ok = stepOk && ok
            RuntimeLog.appendGlobal(TAG, "STEP ${plan.direction} set=${stepMode.rateInt}Hz modeId=${stepMode.modeId} ok=$stepOk")
            if (!RefreshSwitchCoordinator.sleepMillis(800) { isCancelled() }) {
                RuntimeLog.appendGlobal(TAG, "STEP ${plan.direction} cancelled at=${stepMode.rateInt}Hz target=${targetHz}Hz")
                return false
            }
        }
        // 最终提交:升档、降档、同档都在未取消时对目标做一次完整提交
        if (!isCancelled()) {
            val finalOk = commitTargetMode(targetMode, targetHz)
            ok = finalOk && ok
            RuntimeLog.appendGlobal(
                TAG,
                "STEP final target=${targetHz}Hz modeId=${targetMode.modeId} width=${targetMode.width} height=${targetMode.height} ok=$finalOk"
            )
        }
        Log.d(TAG, "switchRefreshRate complete: target=${targetHz}Hz ok=$ok")
        return ok
    }

    /**
     * 对目标 DisplayMode 做一次完整最终提交:
     * preferred mode + 必要 settings + 受控的 SF fallback(仅已验证设备,sfIndex >= 0)。
     */
    private fun commitTargetMode(targetMode: DisplayMode, targetHz: Int): Boolean {
        val sfIndex = targetMode.sfIndex.takeIf { it >= 0 }
        val script = buildString {
            appendLine("cmd display set-user-preferred-display-mode ${targetMode.width} ${targetMode.height} $targetHz")
            appendLine("settings put system peak_refresh_rate ${targetHz}.0")
            appendLine("settings put system min_refresh_rate ${targetHz}.0")
            appendLine("settings put system user_refresh_rate $targetHz")
            appendLine("settings put secure miui_refresh_rate $targetHz")
            if (sfIndex != null) {
                appendLine("service call SurfaceFlinger 1035 i32 $sfIndex")
            }
        }
        return execRootDetailed(
            script.trimEnd(),
            "commitTarget:${targetMode.width}x${targetMode.height}@${targetHz}Hz modeId=${targetMode.modeId} sfIndex=${sfIndex ?: "none"}"
        ).ok
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
            driverHz = parseNodeHz(output, "driverNode="),
            renderedFps = parseNodeHz(output, "renderedNode="),
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

    private fun parseNodeHz(output: String, prefix: String): Int? {
        val line = output.lineSequence().firstOrNull { it.startsWith(prefix) } ?: return null
        val values = NUMBER_PATTERN.findAll(line.substringAfter(":"))
            .mapNotNull { it.value.toFloatOrNull() }
            .toList()
        val direct = values.firstOrNull { it in 30f..300f }
        if (direct != null) return direct.roundToInt()
        val scaled = values.firstOrNull { it in 3000f..30000f }
        return scaled?.div(100f)?.roundToInt()
    }

    private fun displayStateScript(): String {
        return """
            echo peak=${'$'}(settings get system peak_refresh_rate 2>/dev/null)
            echo min=${'$'}(settings get system min_refresh_rate 2>/dev/null)
            echo user=${'$'}(settings get system user_refresh_rate 2>/dev/null)
            echo miui=${'$'}(settings get secure miui_refresh_rate 2>/dev/null)
            echo preferred=${'$'}(cmd display get-user-preferred-display-mode 2>/dev/null)
            echo sfPeriodNs=${'$'}(dumpsys SurfaceFlinger --latency 2>/dev/null | head -n 1)
            for f in /sys/class/drm/*/dynamic_fps /sys/class/drm/*/current_fps /sys/class/graphics/fb*/dynamic_fps /sys/class/graphics/fb*/current_fps; do
                if [ -r "${'$'}f" ]; then echo driverNode=${'$'}f:${'$'}(cat "${'$'}f" 2>/dev/null | head -n 1); fi
            done
            for f in /sys/class/drm/*/measured_fps /sys/class/graphics/fb*/measured_fps; do
                if [ -r "${'$'}f" ]; then echo renderedNode=${'$'}f:${'$'}(cat "${'$'}f" 2>/dev/null | head -n 1); fi
            done
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
