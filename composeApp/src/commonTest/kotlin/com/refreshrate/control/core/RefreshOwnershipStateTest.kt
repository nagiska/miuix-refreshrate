package com.refreshrate.control.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RefreshOwnershipStateTest {

    private val manual30 = ModeIdentity(1080, 2400, 30f, 1)
    private val profile90 = ModeIdentity(1080, 2400, 90f, 3)
    private val actual120 = ModeIdentity(1080, 2400, 120f, 4)

    @Test
    fun `manual 30 then launcher suppresses restore`() {
        val state = RefreshOwnershipState().onManualSelected(manual30)
        assertEquals(OwnershipState.MANUAL_SELECTED, state.state)
        assertEquals(manual30, state.manualBaseline)
        assertTrue(state.shouldSuppressRestore(), "手动模式期间普通前台切换必须抑制旧 restore")
        assertNull(state.restoreTarget(), "手动模式不应触发主动 restore")
    }

    @Test
    fun `manual 30 then return to control app keeps suppress`() {
        val state = RefreshOwnershipState().onManualSelected(manual30)
        // 返回控制应用/任意未配置应用:状态不变,继续抑制
        assertEquals(OwnershipState.MANUAL_SELECTED, state.state)
        assertTrue(state.shouldSuppressRestore())
    }

    @Test
    fun `manual 30 then profile 90 then exit restores manual 30`() {
        val state = RefreshOwnershipState()
            .onManualSelected(manual30)
            .onAutoProfileEntered(actual120) // 进入 profile 时当前实际模式可能是 120(切到 90 前)
        assertEquals(OwnershipState.AUTO_PROFILE_ENTERED, state.state)
        assertEquals(manual30, state.restoreTarget(), "恢复基线必须是 manual 30 而非进入前实际模式")
        assertFalse(state.shouldSuppressRestore(), "AUTO 接管期间不抑制")

        val exited = state.onAutoProfileExited()
        assertEquals(OwnershipState.MANUAL_SELECTED, exited.state)
        assertEquals(manual30, exited.manualBaseline, "退出 profile 后回到手动 30")
        assertTrue(exited.shouldSuppressRestore())
    }

    @Test
    fun `no manual baseline profile 90 then exit restores entered mode`() {
        val state = RefreshOwnershipState().onAutoProfileEntered(actual120)
        assertEquals(OwnershipState.AUTO_PROFILE_ENTERED, state.state)
        assertEquals(actual120, state.restoreTarget(), "无 manual baseline 时恢复进入前实际模式")
        assertNull(state.manualBaseline)

        val exited = state.onAutoProfileExited()
        assertEquals(OwnershipState.IDLE, exited.state, "无 manual baseline 时退出后回 IDLE")
        assertNull(exited.manualBaseline)
        assertFalse(exited.shouldSuppressRestore())
    }

    @Test
    fun `manual selection during auto restore overrides old state`() {
        // 模拟:auto restore 运行中用户手动选择 30Hz —— 新 manual 状态直接覆盖旧 AUTO 状态
        val stale = RefreshOwnershipState().onAutoProfileEntered(actual120)
        val overridden = stale.onManualSelected(manual30)
        assertEquals(OwnershipState.MANUAL_SELECTED, overridden.state)
        assertEquals(manual30, overridden.manualBaseline)
        assertNull(overridden.profileEnteredFrom, "旧 AUTO 残留必须清除")
        assertTrue(overridden.shouldSuppressRestore())
    }

    @Test
    fun `manual cleared returns to idle`() {
        val state = RefreshOwnershipState().onManualSelected(manual30).onManualCleared()
        assertEquals(OwnershipState.IDLE, state.state)
        assertNull(state.manualBaseline)
        assertFalse(state.shouldSuppressRestore())
    }
}