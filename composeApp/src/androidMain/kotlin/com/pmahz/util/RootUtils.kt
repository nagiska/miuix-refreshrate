package com.pmahz.util

import android.content.Context
import android.util.Log
import com.pmahz.model.DisplayMode
import java.io.BufferedReader
import java.io.InputStreamReader

object RootUtils {
    private const val TAG = "RootUtils"

    fun isRootAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "echo RootOK"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val result = reader.readLine()
            process.waitFor()
            "RootOK" == result
        } catch (e: Exception) {
            false
        }
    }

    fun setDisplayMode(width: Int, height: Int, hz: Int, sfIndex: Int) {
        val cmd = if (sfIndex >= 0) {
            "cmd display set-user-preferred-display-mode $sfIndex"
        } else {
            "cmd display set-user-preferred-display-mode $width $height $hz"
        }
        execRoot(cmd)
    }

    fun clearDisplayMode() {
        execRoot("cmd display clear-user-preferred-display-mode 2>/dev/null")
    }

    fun setRefreshRateSettings(hz: Int) {
        execRoot("settings put system min_refresh_rate ${hz}.0")
        execRoot("settings put system user_refresh_rate ${hz}.0")
        execRoot("settings put system peak_refresh_rate ${hz}.0 2>/dev/null")
    }

    fun getSupportedModes(context: Context): List<DisplayMode> {
        return AutoOverclockManager.getSupportedModes(context)
    }

    private fun execRoot(cmd: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            process.waitFor()
            process.exitValue() == 0
        } catch (e: Exception) {
            Log.e(TAG, "execRoot failed: ${e.message}")
            false
        }
    }
}
