package com.refreshrate.control.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.refreshrate.control.model.AppInfo
import com.refreshrate.control.model.DisplayMode

object PrefsHelper {
    private const val PREFS_NAME = "s"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAuthMode(context: Context): String =
        prefs(context).getString("auth_mode", "") ?: ""

    fun setAuthMode(context: Context, mode: String) {
        prefs(context).edit().putString("auth_mode", mode).apply()
    }

    fun isCustomAppRefresh(context: Context): Boolean =
        prefs(context).getBoolean("custom_app_refresh", false)

    fun setCustomAppRefresh(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("custom_app_refresh", enabled).apply()
    }

    fun getEnabledApps(context: Context): List<Triple<String, String, String>> {
        val result = mutableListOf<Triple<String, String, String>>()
        val all = prefs(context).all
        val pm = context.packageManager
        for ((key, value) in all) {
            if (key.startsWith("app_refresh_enabled_") && value == true) {
                val pkg = key.removePrefix("app_refresh_enabled_")
                val res = prefs(context).getString("app_refresh_res_$pkg", "") ?: ""
                val hz = prefs(context).getInt("app_refresh_hz_$pkg", -1)
                val label = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                } catch (e: Exception) {
                    pkg
                }
                if (res.isNotEmpty() && hz >= 0) {
                    val rateLabel = if (hz == 0) "自动最高" else "${hz}Hz"
                    result.add(Triple(pkg, label, "$res @ $rateLabel"))
                }
            }
        }
        return result.sortedBy { it.second.lowercase() }
    }

    fun isAppEnabled(context: Context, pkg: String): Boolean =
        prefs(context).getBoolean("app_refresh_enabled_$pkg", false)

    fun getAppRes(context: Context, pkg: String): String =
        prefs(context).getString("app_refresh_res_$pkg", "") ?: ""

    fun getAppHz(context: Context, pkg: String): Int =
        prefs(context).getInt("app_refresh_hz_$pkg", -1)

    fun setAppConfig(context: Context, pkg: String, enabled: Boolean, res: String, hz: Int) {
        prefs(context).edit()
            .putBoolean("app_refresh_enabled_$pkg", enabled)
            .putString("app_refresh_res_$pkg", res)
            .putInt("app_refresh_hz_$pkg", hz)
            .apply()
    }

    fun removeAppConfig(context: Context, pkg: String) {
        prefs(context).edit()
            .remove("app_refresh_enabled_$pkg")
            .remove("app_refresh_res_$pkg")
            .remove("app_refresh_hz_$pkg")
            .apply()
    }

    fun getInstalledApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        return apps.map { ai ->
            AppInfo(
                name = pm.getApplicationLabel(ai).toString(),
                packageName = ai.packageName,
                systemApp = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0
            )
        }.filter { it.packageName != context.packageName }
            .sortedBy { it.name.lowercase() }
    }

    fun getDisplayModes(context: Context): List<DisplayMode> =
        AutoOverclockManager.getSupportedModes(context)

    fun getResolutionList(context: Context): List<String> {
        val modes = getDisplayModes(context)
        return modes.map { it.resolutionLabel }.distinct()
    }

    fun getHzList(context: Context, resolution: String): List<Int> {
        val modes = getDisplayModes(context)
        val rates = modes.filter { it.resolutionLabel == resolution }
            .map { it.rateInt }
            .distinct()
            .sortedDescending()
        return if (rates.isEmpty()) emptyList() else listOf(0) + rates
    }
}
