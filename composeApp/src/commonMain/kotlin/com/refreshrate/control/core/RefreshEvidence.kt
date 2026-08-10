package com.refreshrate.control.core

import kotlin.math.abs

/**
 * 刷新率验证证据模型(纯逻辑,可单测)。
 *
 * 证据优先级:
 * 1. active mode(宽高 + activeHz)——最可靠的"面板当前状态"
 * 2. physical(vsync 周期推导)
 * 3. driver 节点(/sys 下 driver/current_fps)
 * 4. preferred 模式
 * 5. settings(peak/min/user/miui)
 *
 * rendered/measured fps 仅用于诊断,绝不作为匹配与否的硬否决条件:
 * 实测帧率可能高于已成功切换的 panel mode(例如 90Hz 面板渲染 144fps)。
 */
object RefreshEvidence {

    data class Snapshot(
        val activeModeId: Int? = null,
        val activeWidth: Int? = null,
        val activeHeight: Int? = null,
        val activeHz: Int? = null,
        val physicalHz: Int? = null,
        val driverHz: Int? = null,
        val preferredHz: Int? = null,
        val userHz: Int? = null,
        val peakHz: Int? = null,
        val minHz: Int? = null,
        val miuiHz: Int? = null,
        val renderedFps: Int? = null,
        val hasEvidence: Boolean = false
    )

    sealed interface EvidenceResult {
        data class Match(val source: String) : EvidenceResult
        data class Mismatch(val reason: String) : EvidenceResult
    }

    fun hzClose(a: Int, b: Int): Boolean = a > 0 && abs(a - b) <= 1

    /**
     * 按完整目标 mode(宽高 + Hz)验证。
     * 只要最高优先级证据明确匹配即成功;若 active mode 可解析且宽高不一致,视为明确失败
     * (切到了别的分辨率),不再降级到低级证据,避免"settings 写对了但面板没切"被误判成功。
     */
    fun matchesMode(s: Snapshot, width: Int, height: Int, hz: Int): EvidenceResult {
        val activeResolvable = s.activeModeId != null && s.activeWidth != null &&
            s.activeHeight != null && s.activeHz != null
        if (activeResolvable) {
            val resMatch = s.activeWidth == width && s.activeHeight == height
            val hzMatch = hzClose(s.activeHz!!, hz)
            if (resMatch && hzMatch) return EvidenceResult.Match("activeMode")
            if (!resMatch) {
                return EvidenceResult.Mismatch(
                    "activeMode:${s.activeWidth}x${s.activeHeight}@${s.activeHz}Hz != target:${width}x${height}@${hz}Hz"
                )
            }
            // 分辨率一致但 Hz 不一致:继续看次级证据(物理周期可能更接近真实)
        }
        if (s.physicalHz != null && hzClose(s.physicalHz, hz)) return EvidenceResult.Match("physical")
        if (s.driverHz != null && hzClose(s.driverHz, hz)) return EvidenceResult.Match("driver")
        if (s.preferredHz != null && hzClose(s.preferredHz, hz)) return EvidenceResult.Match("preferred")
        val settings = listOfNotNull(s.userHz, s.peakHz, s.minHz, s.miuiHz)
        if (settings.isNotEmpty() && settings.all { hzClose(it, hz) }) return EvidenceResult.Match("settings")
        return EvidenceResult.Mismatch("no evidence matches target:${width}x${height}@${hz}Hz")
    }

    /** 兼容旧式整数 Hz 验证(仅 androidFallback 使用)。 */
    fun matchesTarget(s: Snapshot, hz: Int): Boolean {
        val activeResolvable = s.activeModeId != null && s.activeHz != null
        if (activeResolvable) {
            if (hzClose(s.activeHz!!, hz)) return true
        }
        if (s.physicalHz != null && hzClose(s.physicalHz, hz)) return true
        if (s.driverHz != null && hzClose(s.driverHz, hz)) return true
        if (s.preferredHz != null && hzClose(s.preferredHz, hz)) return true
        val settings = listOfNotNull(s.userHz, s.peakHz, s.minHz, s.miuiHz)
        return settings.isNotEmpty() && settings.all { hzClose(it, hz) }
    }
}
