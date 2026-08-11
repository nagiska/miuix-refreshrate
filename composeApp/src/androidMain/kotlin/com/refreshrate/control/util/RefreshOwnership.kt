package com.refreshrate.control.util

import android.content.Context
import android.content.SharedPreferences
import com.refreshrate.control.core.ModeIdentity
import com.refreshrate.control.core.OwnershipState
import com.refreshrate.control.core.RefreshOwnershipState

/**
 * 刷新率所有权状态的持久化与通知层。
 *
 * - PlatformBridge(手动选择)写入完整 manual baseline + 递增 revision,并清理服务 restore keys;
 * - KeepAliveAccessibilityService 通过 revision 感知变化,并读取统一 ownership 状态决定 restore 行为,
 *   不再用互不关联的静态变量猜测。
 * 所有刷新率写入仍走 RefreshSwitchCoordinator,此处不做任何 root 操作。
 */
object RefreshOwnership {
    private const val TAG = "RefreshOwnership"

    private const val KEY_STATE = "ownership_state"
    private const val KEY_MANUAL_W = "ownership_manual_w"
    private const val KEY_MANUAL_H = "ownership_manual_h"
    private const val KEY_MANUAL_HZ = "ownership_manual_hz"
    private const val KEY_MANUAL_MODE_ID = "ownership_manual_mode_id"
    private const val KEY_AUTO_FROM_W = "ownership_auto_from_w"
    private const val KEY_AUTO_FROM_H = "ownership_auto_from_h"
    private const val KEY_AUTO_FROM_HZ = "ownership_auto_from_hz"
    private const val KEY_AUTO_FROM_MODE_ID = "ownership_auto_from_mode_id"
    private const val KEY_REVISION = "ownership_revision"

    private const val STATE_MANUAL = "manual"
    private const val STATE_AUTO = "auto"

    @Volatile
    private var cachedState: RefreshOwnershipState = RefreshOwnershipState()
    @Volatile
    private var cachedRevision: Long = 0L

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences("s", Context.MODE_PRIVATE)

    /** 当前内存态(服务侧高频读取)。 */
    fun currentState(): RefreshOwnershipState = cachedState

    /** 当前 revision。 */
    fun currentRevision(): Long = cachedRevision

    /** 手动选择:写完整 baseline + revision,清 auto 残留。 */
    @Synchronized
    fun recordManualSelected(context: Context, mode: ModeIdentity) {
        val previousState = cachedState
        val next = cachedState.onManualSelected(mode)
        cachedState = next
        cachedRevision += 1
        val prefs = prefs(context)
        prefs.edit()
            .putString(KEY_STATE, STATE_MANUAL)
            .putInt(KEY_MANUAL_W, mode.width)
            .putInt(KEY_MANUAL_H, mode.height)
            .putFloat(KEY_MANUAL_HZ, mode.refreshRate)
            .putInt(KEY_MANUAL_MODE_ID, mode.modeId)
            .remove(KEY_AUTO_FROM_W)
            .remove(KEY_AUTO_FROM_H)
            .remove(KEY_AUTO_FROM_HZ)
            .remove(KEY_AUTO_FROM_MODE_ID)
            .putLong(KEY_REVISION, cachedRevision)
            .apply()
        RuntimeLog.appendGlobal(
            TAG,
            "OWNERSHIP manualSelected ${mode.resolutionLabel}@${mode.refreshRate}Hz modeId=${mode.modeId} " +
                "revision=$cachedRevision old={${previousState.state}}"
        )
    }

    /** auto profile 进入前台:记录接管与恢复基线。 */
    @Synchronized
    fun recordAutoProfileEntered(context: Context, actualMode: ModeIdentity) {
        val next = cachedState.onAutoProfileEntered(actualMode)
        cachedState = next
        cachedRevision += 1
        val prefs = prefs(context)
        val baseline = next.profileEnteredFrom
        prefs.edit()
            .putString(KEY_STATE, STATE_AUTO)
            .apply {
                if (baseline != null) {
                    putInt(KEY_AUTO_FROM_W, baseline.width)
                    putInt(KEY_AUTO_FROM_H, baseline.height)
                    putFloat(KEY_AUTO_FROM_HZ, baseline.refreshRate)
                    putInt(KEY_AUTO_FROM_MODE_ID, baseline.modeId)
                } else {
                    remove(KEY_AUTO_FROM_W)
                    remove(KEY_AUTO_FROM_H)
                    remove(KEY_AUTO_FROM_HZ)
                    remove(KEY_AUTO_FROM_MODE_ID)
                }
            }
            .putLong(KEY_REVISION, cachedRevision)
            .apply()
        RuntimeLog.appendGlobal(
            TAG,
            "OWNERSHIP autoTakeover actual=${actualMode.resolutionLabel}@${actualMode.refreshRate}Hz " +
                "baseline=${baseline?.resolutionLabel}@${baseline?.refreshRate}Hz manual=${manualBaselineLabel(next)} revision=$cachedRevision"
        )
    }

    /** auto profile 退出:恢复基线并回到 MANUAL_SELECTED(有 manual baseline)或 IDLE。 */
    @Synchronized
    fun recordAutoProfileExited(context: Context): ModeIdentity? {
        val target = cachedState.restoreTarget()
        val next = cachedState.onAutoProfileExited()
        cachedState = next
        cachedRevision += 1
        val prefs = prefs(context)
        prefs.edit()
            .putString(KEY_STATE, if (next.state == OwnershipState.MANUAL_SELECTED) STATE_MANUAL else "")
            .apply {
                if (next.manualBaseline != null) {
                    putInt(KEY_MANUAL_W, next.manualBaseline.width)
                    putInt(KEY_MANUAL_H, next.manualBaseline.height)
                    putFloat(KEY_MANUAL_HZ, next.manualBaseline.refreshRate)
                    putInt(KEY_MANUAL_MODE_ID, next.manualBaseline.modeId)
                }
                remove(KEY_AUTO_FROM_W)
                remove(KEY_AUTO_FROM_H)
                remove(KEY_AUTO_FROM_HZ)
                remove(KEY_AUTO_FROM_MODE_ID)
            }
            .putLong(KEY_REVISION, cachedRevision)
            .apply()
        RuntimeLog.appendGlobal(
            TAG,
            "OWNERSHIP autoRestoreManual target=${target?.resolutionLabel}@${target?.refreshRate}Hz " +
                "next=${next.state} revision=$cachedRevision"
        )
        return target
    }

    /** 服务侧:revision 变化时从 prefs 重建状态并返回是否变化。 */
    @Synchronized
    fun syncFromPrefs(context: Context): Boolean {
        val prefs = prefs(context)
        val revision = prefs.getLong(KEY_REVISION, 0L)
        if (revision == cachedRevision && revision != 0L) return false
        if (revision == 0L) {
            cachedState = RefreshOwnershipState()
            cachedRevision = 0L
            return cachedState.state != OwnershipState.IDLE
        }
        val stateStr = prefs.getString(KEY_STATE, "") ?: ""
        val manual = readMode(
            prefs,
            KEY_MANUAL_W, KEY_MANUAL_H, KEY_MANUAL_HZ, KEY_MANUAL_MODE_ID
        )
        val autoFrom = readMode(
            prefs,
            KEY_AUTO_FROM_W, KEY_AUTO_FROM_H, KEY_AUTO_FROM_HZ, KEY_AUTO_FROM_MODE_ID
        )
        cachedState = when (stateStr) {
            STATE_MANUAL -> RefreshOwnershipState(
                OwnershipState.MANUAL_SELECTED,
                manualBaseline = manual,
                profileEnteredFrom = null
            )
            STATE_AUTO -> RefreshOwnershipState(
                OwnershipState.AUTO_PROFILE_ENTERED,
                manualBaseline = manual,
                profileEnteredFrom = autoFrom ?: manual
            )
            else -> RefreshOwnershipState()
        }
        cachedRevision = revision
        return true
    }

    /** 手动选择后清理服务 restore keys,避免残留 120Hz 恢复状态。 */
    fun clearServiceRestoreState(context: Context) {
        val prefs = prefs(context)
        prefs.edit()
            .remove("last_applied_config")
            .remove("restore_width")
            .remove("restore_height")
            .remove("restore_hz")
            .remove("restore_mode_id")
            .remove("restore_source_hz")
            .apply()
        RuntimeLog.appendGlobal(TAG, "OWNERSHIP manualCleared service restore keys")
    }

    private fun readMode(
        prefs: SharedPreferences,
        keyW: String, keyH: String, keyHz: String, keyId: String
    ): ModeIdentity? {
        val w = prefs.getInt(keyW, -1)
        val h = prefs.getInt(keyH, -1)
        val hz = prefs.getFloat(keyHz, -1f)
        val id = prefs.getInt(keyId, -1)
        if (w <= 0 || h <= 0 || hz <= 0f || id <= 0) return null
        return ModeIdentity(w, h, hz, id)
    }

    private fun manualBaselineLabel(state: RefreshOwnershipState): String {
        val b = state.manualBaseline ?: return "none"
        return "${b.resolutionLabel}@${b.refreshRate}Hz"
    }
}
