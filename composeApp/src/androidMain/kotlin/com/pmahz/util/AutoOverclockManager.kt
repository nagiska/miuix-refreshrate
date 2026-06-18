package com.pmahz.util

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.Display
import com.pmahz.model.DisplayMode

object AutoOverclockManager {
    private const val TAG = "AutoOverclock"

    fun getSupportedModes(context: Context): List<DisplayMode> {
        val modes = mutableListOf<DisplayMode>()
        try {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val display = dm.getDisplay(0)
            val displayModes = display.supportedModes ?: return emptyList()

            val seen = mutableSetOf<String>()
            for (i in displayModes.indices) {
                val m = displayModes[i]
                val key = "${m.physicalWidth}x${m.physicalHeight}@${m.refreshRate.toInt()}"
                if (seen.add(key)) {
                    val dm2 = DisplayMode(
                        m.physicalWidth,
                        m.physicalHeight,
                        m.refreshRate,
                        m.modeId
                    )
                    dm2.sfIndex = m.modeId
                    modes.add(dm2)
                }
            }
            modes.sortWith(compareByDescending<DisplayMode> { it.rateInt }.thenBy { it.width * it.height })
        } catch (e: Exception) {
            Log.e(TAG, "getSupportedModes: ${e.message}")
        }
        return modes
    }

    fun getCurrentMode(context: Context): DisplayMode? {
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
            .toSortedMap(compareByDescending<String> { it.split("x")[0].toInt() })
    }
}
