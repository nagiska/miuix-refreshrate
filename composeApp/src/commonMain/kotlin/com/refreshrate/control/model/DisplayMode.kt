package com.refreshrate.control.model

class DisplayMode(
    val width: Int,
    val height: Int,
    val refreshRate: Float,
    val modeId: Int
) {
    var sfIndex: Int = -1

    val resolutionLabel: String
        get() = "${width}x${height}"

    val rateInt: Int
        get() = Math.round(refreshRate)

    val rateName: String
        get() {
            val r = rateInt
            return when {
                r >= 185 -> "极致"
                r >= 165 -> "限速"
                r >= 144 -> "高刷"
                r >= 120 -> "超刷"
                r >= 90 -> "优刷"
                r >= 60 -> "流畅"
                else -> "标准"
            }
        }

    val rateDesc: String
        get() {
            val r = rateInt
            return when {
                r >= 185 -> "极致流畅，适合游戏"
                r >= 165 -> "高帧率，流畅体验"
                r >= 144 -> "高刷新率，丝滑操作"
                r >= 120 -> "超清刷新率，游戏首选"
                r >= 90 -> "优秀刷新率，日常流畅"
                r >= 60 -> "标准流畅，日常使用"
                else -> "省电模式，续航优先"
            }
        }
}
