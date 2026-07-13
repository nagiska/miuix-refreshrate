package com.refreshrate.control.util

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.Log
import com.refreshrate.control.model.DisplayMode

object AutoOverclockManager {
    private const val TAG = "AutoOverclock"

    fun getSupportedModes(context: Context): List<DisplayMode> {
        val prefs = context.getSharedPreferences("s", Context.MODE_PRIVATE)
        val authMode = prefs.getString("auth_mode", "") ?: ""

        val modes = when (authMode.ifEmpty { "root" }) {
            "root" -> RootUtils.scanModesFromDumpsys()
            else -> {
                try {
                    val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                    val display = dm.getDisplay(0)
                    val displayModes = display.supportedModes ?: return emptyList()

                    val seen = mutableSetOf<String>()
                    displayModes.mapIndexed { i, m ->
                        val key = "${m.physicalWidth}x${m.physicalHeight}@${m.refreshRate.toInt()}"
                        if (seen.add(key)) {
                            val dm2 = DisplayMode(m.physicalWidth, m.physicalHeight, m.refreshRate, m.modeId)
                            dm2.sfIndex = m.modeId
                            dm2
                        } else null
                    }.filterNotNull()
                } catch (e: Exception) {
                    Log.e(TAG, "getSupportedModes fallback: ${e.message}")
                    emptyList()
                }
            }
        }

        if (modes.isEmpty()) {
            try {
                val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                val display = dm.getDisplay(0)
                val displayModes = display.supportedModes ?: return emptyList()

                val seen = mutableSetOf<String>()
                return displayModes.map { m ->
                    val key = "${m.physicalWidth}x${m.physicalHeight}@${m.refreshRate.toInt()}"
                    if (seen.add(key)) {
                        val dm2 = DisplayMode(m.physicalWidth, m.physicalHeight, m.refreshRate, m.modeId)
                        dm2.sfIndex = m.modeId
                        dm2
                    } else null
                }.filterNotNull().sortedByDescending { it.rateInt }
            } catch (e: Exception) {
                return emptyList()
            }
        }

        return modes
    }

    fun getCurrentMode(context: Context): DisplayMode? {
        try {
            val prefs = context.getSharedPreferences("s", Context.MODE_PRIVATE)
            val authMode = prefs.getString("auth_mode", "") ?: ""
            if (authMode.ifEmpty { "root" } == "root") {
                val state = RootUtils.readDisplayState()
                val actualHz = state.driverHz ?: state.physicalHz ?: state.activeHz
                if (actualHz != null) {
                    val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                    val androidMode = dm.getDisplay(0).mode
                    val modeId = state.activeModeId ?: androidMode.modeId
                    return DisplayMode(
                        state.activeWidth ?: androidMode.physicalWidth,
                        state.activeHeight ?: androidMode.physicalHeight,
                        actualHz.toFloat(),
                        modeId
                    ).apply {
                        sfIndex = modeId - 1
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getCurrentMode root state fallback: ${e.message}")
        }
        return try {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val display = dm.getDisplay(0)
            val m = display.mode
            DisplayMode(m.physicalWidth, m.physicalHeight, m.refreshRate, m.modeId)
        } catch (e: Exception) {
            null
        }
    }

    fun getCurrentRefreshRate(context: Context): Int {
        return try {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val display = dm.getDisplay(0)
            Math.round(display.refreshRate)
        } catch (e: Exception) {
            -1
        }
    }

    fun groupByResolution(modes: List<DisplayMode>): Map<String, List<DisplayMode>> {
        return modes.groupBy { it.resolutionLabel }
            .toSortedMap(compareByDescending<String> { it.split("x")[0].toIntOrNull() ?: 0 })
    }
}
