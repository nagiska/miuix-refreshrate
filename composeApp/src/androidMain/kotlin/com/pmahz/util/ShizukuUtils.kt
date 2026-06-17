package com.pmahz.util

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.pmahz.model.DisplayMode
import rikka.shizuku.Shizuku

object ShizukuUtils {
    private const val TAG = "ShizukuUtils"

    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
    }

    fun hasPermission(): Boolean {
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    fun requestPermission() {
        try {
            Shizuku.requestPermission(0)
        } catch (e: Exception) {
            Log.e(TAG, "requestPermission: ${e.message}")
        }
    }

    fun setDisplayMode(width: Int, height: Int, hz: Int, sfIndex: Int) {
        val cmd = if (sfIndex >= 0) {
            "cmd display set-user-preferred-display-mode $sfIndex"
        } else {
            "cmd display set-user-preferred-display-mode $width $height $hz"
        }
        execShizuku(cmd)
    }

    fun clearDisplayMode() {
        execShizuku("cmd display clear-user-preferred-display-mode 2>/dev/null")
    }

    fun setRefreshRateSettings(hz: Int) {
        execShizuku("settings put system min_refresh_rate ${hz}.0")
        execShizuku("settings put system user_refresh_rate ${hz}.0")
        execShizuku("settings put system peak_refresh_rate ${hz}.0 2>/dev/null")
    }

    fun getSupportedModes(context: Context): List<DisplayMode> {
        return AutoOverclockManager.getSupportedModes(context)
    }

    private fun execShizuku(cmd: String): Boolean {
        return try {
            val binder = Shizuku.getBinder() ?: return false
            val process = binder.javaClass
                .getMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
                .invoke(binder, arrayOf("sh", "-c", cmd), null, null) as Process
            val exitCode = process.waitFor()
            process.destroy()
            exitCode == 0
        } catch (e: Exception) {
            try {
                val binder = Shizuku.getBinder() ?: return false
                val method = binder.javaClass.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
                method.isAccessible = true
                val process = method.invoke(binder, arrayOf("sh", "-c", cmd), null, null) as Process
                val exitCode = process.waitFor()
                process.destroy()
                exitCode == 0
            } catch (e2: Exception) {
                Log.e(TAG, "execShizuku failed: ${e2.message}")
                false
            }
        }
    }
}
