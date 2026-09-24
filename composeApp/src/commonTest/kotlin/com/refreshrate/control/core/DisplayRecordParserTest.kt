package com.refreshrate.control.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DisplayRecordParserTest {

    @Test
    fun `parses short DisplayModeRecord format`() {
        val line = "DisplayModeRecord{id=3, width=1200, height=2670, fps=90.0}"
        val recs = DisplayRecordParser.parseLine(line)
        assertEquals(1, recs.size)
        assertEquals(3, recs[0].id)
        assertEquals(1200, recs[0].width)
        assertEquals(2670, recs[0].height)
        assertEquals(90f, recs[0].fps, 0.001f)
        assertNull(recs[0].sfModeId, "短格式无 sfModeId")
    }

    @Test
    fun `parses mMode long format with intervening fields and sfModeId`() {
        // 来自真机日志的实际格式:id 与 width 之间有 parentModeId/sfModeId/flags
        val line = "DisplayModeRecord{mMode={id=1, parentModeId=-1, sfModeId=0, flags=, " +
            "width=1200, height=2670, fps=120.00001, vsync=120.00001, " +
            "alternativeRefreshRates=[24.000002, 30.000002, 40.0, 60.000004, 90.0], " +
            "supportedHdrTypes=[1, 2, 3, 4]}}"
        val recs = DisplayRecordParser.parseLine(line)
        assertEquals(1, recs.size)
        assertEquals(1, recs[0].id)
        assertEquals(1200, recs[0].width)
        assertEquals(2670, recs[0].height)
        assertEquals(120.00001f, recs[0].fps, 0.0001f)
        assertEquals(0, recs[0].sfModeId, "应解析出 sfModeId 作为 SF 1035 权威索引")
    }

    @Test
    fun `parses non-zero sfModeId`() {
        val line = "DisplayModeRecord{mMode={id=2, parentModeId=-1, sfModeId=6, flags=, width=1200, height=2670, fps=90.0}}"
        val recs = DisplayRecordParser.parseLine(line)
        assertEquals(2, recs[0].id)
        assertEquals(6, recs[0].sfModeId)
    }

    @Test
    fun `parses multiple records across lines`() {
        val text = """
            DisplayModeRecord{mMode={id=3, parentModeId=-1, sfModeId=7, flags=, width=1200, height=2670, fps=60.000004}}
            DisplayModeRecord{mMode={id=4, parentModeId=-1, sfModeId=8, flags=, width=1200, height=2670, fps=40.0}}
        """.trimIndent()
        val recs = DisplayRecordParser.parse(text)
        assertEquals(2, recs.size)
        assertTrue(recs.any { it.id == 3 && it.sfModeId == 7 })
        assertTrue(recs.any { it.id == 4 && it.sfModeId == 8 })
    }

    @Test
    fun `alternativeRefreshRates list does not break parsing`() {
        // alternativeRefreshRates 内含逗号,但位于 fps 之后,不得干扰匹配
        val line = "DisplayModeRecord{mMode={id=2, parentModeId=-1, sfModeId=6, flags=, " +
            "width=1200, height=2670, fps=90.0, alternativeRefreshRates=[24.0, 30.0, 60.0, 120.0]}}"
        val recs = DisplayRecordParser.parseLine(line)
        assertEquals(1, recs.size)
        assertEquals(90f, recs[0].fps, 0.001f)
    }
}
