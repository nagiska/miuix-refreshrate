package com.refreshrate.control.core

import kotlin.math.roundToInt

/**
 * 显示模式完整身份(宽高 + 原始浮点 Hz + modeId)。
 * 手动 baseline 必须保存完整 identity,不能只存整数 Hz。
 */
data class ModeIdentity(
    val width: Int,
    val height: Int,
    val refreshRate: Float,
    val modeId: Int
) {
    val rateInt: Int get() = refreshRate.roundToInt()
    val resolutionLabel: String get() = "${width}x${height}"
}

enum class OwnershipState { IDLE, MANUAL_SELECTED, AUTO_PROFILE_ENTERED }

/**
 * 手动/自动刷新率所有权状态机(纯逻辑,可单测)。
 *
 * 语义:
 * - IDLE:无手动接管,auto profile 按"进入前实际模式"恢复。
 * - MANUAL_SELECTED:用户手动选择了刷新率(保存完整 manual baseline)。
 *   此后普通未配置应用/Launcher 的前台切换不得触发旧 restore;
 *   只有已启用的 per-app profile 进入前台时才临时接管。
 * - AUTO_PROFILE_ENTERED:auto profile 正在接管;恢复基线 =
 *   manual baseline(若有),否则为进入 profile 前的实际模式。
 */
data class RefreshOwnershipState(
    val state: OwnershipState = OwnershipState.IDLE,
    val manualBaseline: ModeIdentity? = null,
    val profileEnteredFrom: ModeIdentity? = null
) {

    /** 用户手动选择:保存 manual baseline,清除 auto 残留,进入 MANUAL_SELECTED。 */
    fun onManualSelected(mode: ModeIdentity): RefreshOwnershipState =
        RefreshOwnershipState(OwnershipState.MANUAL_SELECTED, manualBaseline = mode, profileEnteredFrom = null)

    /**
     * auto profile 进入前台:临时接管。
     * 恢复基线 = manual baseline(若有),否则为进入前的实际模式。
     */
    fun onAutoProfileEntered(actualMode: ModeIdentity): RefreshOwnershipState {
        val baseline = manualBaseline ?: actualMode
        return RefreshOwnershipState(
            OwnershipState.AUTO_PROFILE_ENTERED,
            manualBaseline = manualBaseline,
            profileEnteredFrom = baseline
        )
    }

    /**
     * auto profile 退出:恢复到恢复基线。
     * 若存在 manual baseline,回到 MANUAL_SELECTED(继续抑制普通前台 restore);
     * 否则回到 IDLE(无手动接管)。
     */
    fun onAutoProfileExited(): RefreshOwnershipState {
        return if (manualBaseline != null) {
            RefreshOwnershipState(OwnershipState.MANUAL_SELECTED, manualBaseline = manualBaseline, profileEnteredFrom = null)
        } else {
            RefreshOwnershipState()
        }
    }

    /** 显式清除手动接管(回到 IDLE)。 */
    fun onManualCleared(): RefreshOwnershipState = RefreshOwnershipState()

    /** 当前应恢复到的目标模式(仅 AUTO_PROFILE_ENTERED 需要主动恢复)。 */
    fun restoreTarget(): ModeIdentity? = when (state) {
        OwnershipState.AUTO_PROFILE_ENTERED -> profileEnteredFrom ?: manualBaseline
        else -> null
    }

    /** 手动模式生效期间,普通未配置前台切换应抑制旧 restore。 */
    fun shouldSuppressRestore(): Boolean =
        state == OwnershipState.MANUAL_SELECTED && manualBaseline != null
}
