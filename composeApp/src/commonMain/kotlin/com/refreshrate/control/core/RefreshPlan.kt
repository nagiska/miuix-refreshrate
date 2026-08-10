package com.refreshrate.control.core

import kotlin.math.roundToInt

/**
 * 显示模式规格(纯数据,跨平台)。
 * 保留原始 refreshRate 浮点值;rateInt 仅用于 UI 展示与粗略比较。
 */
data class ModeSpec(
    val width: Int,
    val height: Int,
    val refreshRate: Float,
    val modeId: Int,
    val sfIndex: Int = -1
) {
    val rateInt: Int get() = refreshRate.roundToInt()
    val resolutionLabel: String get() = "${width}x${height}"
}

enum class SwitchDirection { UP, DOWN, SAME }

/**
 * 刷新率切换计划(纯逻辑,可单测)。
 *
 * 生成升档/降档/同档的步进序列与最终提交要求:
 * - UP:   从 currentHz 逐级升到 target(包含 target 本身)
 * - DOWN: 从 currentHz 逐级降到 target(步进不含 target,最终提交统一在 finalize 完成)
 * - SAME: 无步进
 *
 * 注意:down 的过滤条件 `in targetHz until currentHz` 在 Kotlin 中包含 lower bound,
 * 与 `..` 语义等价,这里保持 until 写法。
 */
data class RefreshPlan(
    val target: ModeSpec,
    val direction: SwitchDirection,
    val steps: List<ModeSpec>,
    val currentHz: Int
) {
    companion object {
        private fun sameResolutionModes(allModes: List<ModeSpec>, target: ModeSpec): List<ModeSpec> {
            return allModes.filter { it.width == target.width && it.height == target.height }
        }

        fun plan(target: ModeSpec, allModes: List<ModeSpec>, currentHz: Int): RefreshPlan {
            val targetHz = target.rateInt
            val direction = when {
                currentHz < targetHz -> SwitchDirection.UP
                currentHz > targetHz -> SwitchDirection.DOWN
                else -> SwitchDirection.SAME
            }
            val res = sameResolutionModes(allModes, target)
            val steps = when (direction) {
                SwitchDirection.UP -> res
                    .filter { it.rateInt > currentHz && it.rateInt <= targetHz }
                    .sortedBy { it.rateInt }
                SwitchDirection.DOWN -> (res + target)
                    .distinctBy { Triple(it.width, it.height, it.refreshRate) }
                    // until 包含 lower bound(即 targetHz 本身);target 由最终 commitTargetMode 提交,步进排除
                    .filter { it.modeId != target.modeId && it.rateInt in targetHz until currentHz }
                    .sortedByDescending { it.rateInt }
                SwitchDirection.SAME -> emptyList()
            }
            return RefreshPlan(target, direction, steps, currentHz)
        }
    }
}

/**
 * 切换取消代号(纯逻辑,可单测)。
 * 新请求递增代号以淘汰旧请求;stale request 在提交/写入前检查代号即可丢弃。
 */
class SwitchGeneration {
    private var current: Long = 0L

    /** 递增并返回新代号。调用方需保证并发安全(实际使用在协调器内同步)。 */
    fun next(): Long {
        current += 1
        return current
    }

    fun isCurrent(generation: Long): Boolean = generation == current

    fun currentValue(): Long = current
}
