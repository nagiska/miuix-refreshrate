package com.refreshrate.control.core

import kotlin.math.roundToInt

/**
 * 解析 `dumpsys display` 的 DisplayModeRecord。兼容多种 ROM 格式:
 *  - 短:DisplayModeRecord{id=3, width=1200, height=2670, fps=90.0}
 *  - 长:DisplayModeRecord{mMode={id=1, parentModeId=-1, sfModeId=0, flags=,
 *        width=1200, height=2670, fps=120.00001, vsync=..., alternativeRefreshRates=[...]}}
 *
 * 关键:id 与 width 之间可能有 parentModeId / sfModeId / flags 等任意 key=value 字段,
 * 旧正则 `id=(\d+),\s*width=` 要求二者紧邻,在这类设备上会整体匹配失败(导致扫描回退 API、
 * active 模式解析不出、sfIndex 缺失)。这里容许中间任意 `key=value,` 段。
 *
 * 同时抽取 sfModeId:该字段是 SurfaceFlinger 事务 1035(setActiveDisplayMode)期望的权威模式索引,
 * 优先用它;缺失时才回退到调用方传入的路序序号。
 */
object DisplayRecordParser {
    private val PATTERN = Regex(
        """id=(\d+),\s*(?:[a-zA-Z_]+=[^,]*,\s*)*?width=(\d+),\s*height=(\d+),\s*fps=([\d.]+)"""
    )
    private val SF_MODE_ID = Regex("""sfModeId=(\d+)""")

    data class Record(
        val id: Int,
        val width: Int,
        val height: Int,
        val fps: Float,
        val sfModeId: Int?
    )

    fun parseLine(line: String): List<Record> =
        PATTERN.findAll(line).mapNotNull { m ->
            val id = m.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val w = m.groupValues[2].toIntOrNull() ?: return@mapNotNull null
            val h = m.groupValues[3].toIntOrNull() ?: return@mapNotNull null
            val fps = m.groupValues[4].toFloatOrNull() ?: return@mapNotNull null
            val sfModeId = SF_MODE_ID.find(m.value)?.groupValues?.get(1)?.toIntOrNull()
            Record(id, w, h, fps, sfModeId)
        }.toList()

    fun parse(text: String): List<Record> = text.lines().flatMap { parseLine(it) }
}
