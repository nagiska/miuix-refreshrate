package com.pmahz.util

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.pmahz.model.DisplayMode
import rikka.shizuku.Shizuku

object ShizukuUtils {
    private const val TAG = "ShizukuUtils"
    private val RECORD_PATTERN = Regex("""id=(\d+),\s*width=(\d+),\s*height=(\d+),\s*fps=([\d.]+)""")

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

    private fun execShizuku(cmd: String): Boolean {
        return try {
            val clz = Class.forName("rikka.shizuku.Shizuku")
            val m = clz.getDeclaredMethod("newProcess", Array<String>::class.java)
            m.isAccessible = true
            val proc = m.invoke(null, arrayOf("sh", "-c", cmd)) as Any
            val pCls = proc.javaClass
            val ism = pCls.getDeclaredMethod("getInputStream")
            val esm = pCls.getDeclaredMethod("getErrorStream")
            val wfm = pCls.getDeclaredMethod("waitFor")
            ism.invoke(proc) as java.io.InputStream
            esm.invoke(proc) as java.io.InputStream
            val code = wfm.invoke(proc) as Int
            code == 0
        } catch (e: Exception) {
            Log.e(TAG, "execShizuku failed: ${e.message}")
            false
        }
    }

    private fun execShizukuForOutput(cmd: String): String {
        return try {
            val clz = Class.forName("rikka.shizuku.Shizuku")
            val m = clz.getDeclaredMethod("newProcess", Array<String>::class.java)
            m.isAccessible = true
            val proc = m.invoke(null, arrayOf("sh", "-c", cmd)) as Any
            val pCls = proc.javaClass
            val ism = pCls.getDeclaredMethod("getInputStream")
            val wfm = pCls.getDeclaredMethod("waitFor")
            val out = (ism.invoke(proc) as java.io.InputStream).bufferedReader().readText().trim()
            wfm.invoke(proc)
            out
        } catch (e: Exception) {
            Log.e(TAG, "execShizukuForOutput failed: ${e.message}")
            ""
        }
    }

    fun scanModesFromDumpsys(): List<DisplayMode> {
        val output = execShizukuForOutput("dumpsys display | grep 'DisplayModeRecord'")
        if (output.isBlank()) return emptyList()

        val modes = mutableListOf<DisplayMode>()
        for (line in output.lines()) {
            val match = RECORD_PATTERN.find(line) ?: continue
            val id = match.groupValues[1].toIntOrNull() ?: continue
            val w = match.groupValues[2].toIntOrNull() ?: continue
            val h = match.groupValues[3].toIntOrNull() ?: continue
            val fps = match.groupValues[4].toFloatOrNull()?.toInt() ?: continue
            if (fps in 30..300) {
                val dm = DisplayMode(w, h, fps.toFloat(), id)
                dm.sfIndex = id
                modes.add(dm)
            }
        }
        return modes.sortedBy { it.rateInt }
    }

    fun setRate(dumpsysModeId: Int?, targetHz: Int): Boolean {
        val cmd = buildString {
            if (dumpsysModeId != null && dumpsysModeId > 0) {
                append("service call SurfaceFlinger 1035 i32 ${dumpsysModeId - 1}; ")
            }
            append("settings put system peak_refresh_rate ${targetHz}.0; ")
            append("settings put system min_refresh_rate ${targetHz}.0; ")
            append("settings put system user_refresh_rate $targetHz; ")
            append("settings put secure miui_refresh_rate $targetHz; ")
            append("settings put system thermal_limit_refresh_rate $targetHz 2>/dev/null")
        }
        return execShizuku(cmd)
    }

    fun setDisplayMode(width: Int, height: Int, hz: Int, sfIndex: Int): Boolean {
        val cmd = buildString {
            if (width > 0 && height > 0 && hz > 0) {
                append("cmd display set-user-preferred-display-mode $width $height $hz 2>/dev/null; ")
            }
            append("settings put system peak_refresh_rate ${hz}.0; ")
            append("settings put system min_refresh_rate ${hz}.0; ")
            append("settings put system user_refresh_rate $hz; ")
            append("settings put secure miui_refresh_rate $hz; ")
            append("settings put system thermal_limit_refresh_rate $hz 2>/dev/null")
            if (sfIndex >= 0) {
                append("; service call SurfaceFlinger 1035 i32 $sfIndex")
            }
        }
        return execShizuku(cmd)
    }

    fun clearDisplayMode(): Boolean {
        return execShizuku("cmd display clear-user-preferred-display-mode 2>/dev/null")
    }

    fun restoreAdaptive(minHz: Int, maxHz: Int): Boolean {
        val cmd = buildString {
            append("cmd display clear-user-preferred-display-mode 2>/dev/null")
            if (minHz > 0) append("; settings put system min_refresh_rate ${minHz}.0")
            if (maxHz > 0) append("; settings put system peak_refresh_rate ${maxHz}.0")
        }
        return execShizuku(cmd)
    }

    fun setNativeRefreshOverlay(on: Boolean): Boolean {
        val valInt = if (on) 1 else 0
        return execShizuku("service call SurfaceFlinger 1034 i32 $valInt")
    }
}
