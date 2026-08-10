package com.refreshrate.control.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RefreshPlanTest {

    private fun mode(w: Int, h: Int, hz: Float, id: Int, sf: Int = -1) = ModeSpec(w, h, hz, id, sf)

    private val modes1080 = listOf(
        mode(1080, 2400, 60f, 1),
        mode(1080, 2400, 90f, 2),
        mode(1080, 2400, 120f, 3),
        mode(1080, 2400, 144f, 4),
        mode(1080, 2400, 165f, 5),
    )

    @Test
    fun `165 to 90 down steps include intermediate modes and exclude target`() {
        val plan = RefreshPlan.plan(mode(1080, 2400, 90f, 2), modes1080, currentHz = 165)
        assertEquals(SwitchDirection.DOWN, plan.direction)
        assertEquals(listOf(144, 120), plan.steps.map { it.rateInt })
        assertTrue(plan.steps.none { it.modeId == 2 }, "target 90 不应出现在步进中(由 finalize 提交)")
    }

    @Test
    fun `165 to 60 down steps include all intermediates`() {
        val plan = RefreshPlan.plan(mode(1080, 2400, 60f, 1), modes1080, currentHz = 165)
        assertEquals(SwitchDirection.DOWN, plan.direction)
        assertEquals(listOf(144, 120, 90), plan.steps.map { it.rateInt })
    }

    @Test
    fun `90 to high refresh up steps include target itself`() {
        val plan = RefreshPlan.plan(mode(1080, 2400, 144f, 4), modes1080, currentHz = 90)
        assertEquals(SwitchDirection.UP, plan.direction)
        assertEquals(listOf(120, 144), plan.steps.map { it.rateInt })
        assertTrue(plan.steps.any { it.modeId == 4 }, "升档步进应包含 target 模式")
    }

    @Test
    fun `60 to high refresh up steps`() {
        val plan = RefreshPlan.plan(mode(1080, 2400, 165f, 5), modes1080, currentHz = 60)
        assertEquals(SwitchDirection.UP, plan.direction)
        assertEquals(listOf(90, 120, 144, 165), plan.steps.map { it.rateInt })
    }

    @Test
    fun `same rate switch has no steps`() {
        val plan = RefreshPlan.plan(mode(1080, 2400, 120f, 3), modes1080, currentHz = 120)
        assertEquals(SwitchDirection.SAME, plan.direction)
        assertTrue(plan.steps.isEmpty())
    }

    @Test
    fun `59_94 and 60_0 are not merged as distinct modes`() {
        val modes = listOf(
            mode(1080, 2400, 59.94f, 1),
            mode(1080, 2400, 60.0f, 2),
            mode(1080, 2400, 90f, 3),
        )
        val plan = RefreshPlan.plan(mode(1080, 2400, 60.0f, 2), modes, currentHz = 90)
        assertEquals(SwitchDirection.DOWN, plan.direction)
        // 59.94 与 60.0 都是 rateInt=60,但 modeId 不同,不应被 distinct 合并掉
        assertEquals(1, plan.steps.size)
        assertEquals(1, plan.steps[0].modeId)
        assertEquals(59.94f, plan.steps[0].refreshRate)
    }
}

class SwitchGenerationTest {

    @Test
    fun `new generation invalidates stale requests`() {
        val gen = SwitchGeneration()
        val g1 = gen.next()
        assertTrue(gen.isCurrent(g1))
        val g2 = gen.next() // 快速 165->90->60 的后续请求
        assertTrue(gen.isCurrent(g2))
        assertFalse(gen.isCurrent(g1), "旧请求必须被淘汰")
        val g3 = gen.next()
        assertTrue(gen.isCurrent(g3))
        assertFalse(gen.isCurrent(g2))
        assertFalse(gen.isCurrent(g1))
    }
}

class RefreshEvidenceTest {

    private val snapshot = RefreshEvidence.Snapshot(
        activeModeId = 2,
        activeWidth = 1080,
        activeHeight = 2400,
        activeHz = 90,
        physicalHz = 90,
        driverHz = 90,
        preferredHz = 90,
        userHz = 90,
        peakHz = 90,
        minHz = 90,
        miuiHz = 90,
        renderedFps = 144, // 渲染帧率高于面板,不得否决匹配
        hasEvidence = true
    )

    @Test
    fun `active 90 rendered 144 matches target 90`() {
        val result = RefreshEvidence.matchesMode(snapshot, 1080, 2400, 90)
        assertTrue(result is RefreshEvidence.EvidenceResult.Match, "renderedFps 高于 target 不应否决匹配")
    }

    @Test
    fun `active resolution mismatch is explicit failure`() {
        val result = RefreshEvidence.matchesMode(snapshot, 1080, 2340, 90)
        assertTrue(result is RefreshEvidence.EvidenceResult.Mismatch)
    }

    @Test
    fun `active hz mismatch falls back to secondary evidence`() {
        // activeHz=120 但 physical=90:physical 是次级证据
        val s = snapshot.copy(activeHz = 120, physicalHz = 90)
        val result = RefreshEvidence.matchesMode(s, 1080, 2400, 90)
        assertTrue(result is RefreshEvidence.EvidenceResult.Match)
    }

    @Test
    fun `no evidence never matches`() {
        val s = RefreshEvidence.Snapshot(hasEvidence = false)
        val result = RefreshEvidence.matchesMode(s, 1080, 2400, 90)
        assertTrue(result is RefreshEvidence.EvidenceResult.Mismatch)
    }
}
