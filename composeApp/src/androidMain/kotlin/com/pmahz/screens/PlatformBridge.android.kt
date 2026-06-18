package com.pmahz.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.pmahz.model.AppInfo
import com.pmahz.model.DisplayMode
import com.pmahz.util.AutoOverclockManager
import com.pmahz.util.PrefsHelper
import com.pmahz.util.RootUtils
import com.pmahz.util.ShizukuUtils

actual class AppContext(val context: Context)

@Composable
actual fun rememberAppContext(): AppContext {
    val ctx = LocalContext.current
    return remember(ctx) { AppContext(ctx) }
}

@Composable
actual fun refreshDisplayData(refreshKey: Int): DisplayData? {
    val context = LocalContext.current
    var data by remember { mutableStateOf<DisplayData?>(null) }

    LaunchedEffect(refreshKey) {
        try {
            val authMode = PrefsHelper.getAuthMode(context)
            val current = AutoOverclockManager.getCurrentMode(context)
            val modes = AutoOverclockManager.getSupportedModes(context)
            val grouped = AutoOverclockManager.groupByResolution(modes)
                .entries.map { it.key to it.value }.toList()
            data = DisplayData(current, authMode, grouped)
        } catch (e: Exception) {
        }
    }

    return data
}

actual fun applyDisplayMode(authMode: String, mode: DisplayMode, context: AppContext) {
    Thread {
        try {
            when (authMode) {
                "root" -> {
                    RootUtils.setDisplayMode(mode.width, mode.height, mode.rateInt, mode.sfIndex)
                    RootUtils.setRefreshRateSettings(mode.rateInt)
                }
                "shizuku" -> {
                    ShizukuUtils.setDisplayMode(mode.width, mode.height, mode.rateInt, mode.sfIndex)
                    ShizukuUtils.setRefreshRateSettings(mode.rateInt)
                }
            }
        } catch (e: Exception) {
        }
    }.start()
}

@Composable
actual fun loadSettingsData(): SettingsData? {
    val context = LocalContext.current
    var data by remember { mutableStateOf<SettingsData?>(null) }

    LaunchedEffect(Unit) {
        try {
            val authMode = PrefsHelper.getAuthMode(context)
            val autoOverclock = PrefsHelper.isAutoOverclock(context)
            val ocRes = PrefsHelper.getOcTargetRes(context)
            val ocHz = PrefsHelper.getOcTargetHz(context)
            val rootAvailable = RootUtils.isRootAvailable()
            val shizukuAvailable = ShizukuUtils.isShizukuAvailable()
            val shizukuHasPermission = ShizukuUtils.hasPermission()
            val customAppRefresh = PrefsHelper.isCustomAppRefresh(context)
            data = SettingsData(
                authMode, autoOverclock, ocRes, ocHz,
                rootAvailable, shizukuAvailable, shizukuHasPermission,
                customAppRefresh
            )
        } catch (e: Exception) {
        }
    }

    return data
}

actual fun saveAuthMode(context: AppContext, mode: String) {
    PrefsHelper.setAuthMode(context.context, mode)
}

actual fun saveAutoOverclock(context: AppContext, enabled: Boolean, res: String, hz: Int) {
    PrefsHelper.setAutoOverclock(context.context, enabled)
    if (enabled && res.isNotEmpty() && hz > 0) {
        PrefsHelper.setOcTarget(context.context, res, hz)
    }
}

actual fun saveCustomAppRefresh(context: AppContext, enabled: Boolean) {
    PrefsHelper.setCustomAppRefresh(context.context, enabled)
}

@Composable
actual fun loadEnabledApps(): List<EnabledAppData> {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<EnabledAppData>>(emptyList()) }

    LaunchedEffect(Unit) {
        try {
            apps = PrefsHelper.getEnabledApps(context).map {
                EnabledAppData(it.first, it.second, it.third)
            }
        } catch (e: Exception) {
        }
    }

    return apps
}

@Composable
actual fun loadInstalledApps(): List<AppInfo> {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }

    LaunchedEffect(Unit) {
        try {
            apps = PrefsHelper.getInstalledApps(context)
        } catch (e: Exception) {
        }
    }

    return apps
}

actual fun saveAppConfig(context: AppContext, pkg: String, enabled: Boolean, res: String, hz: Int) {
    PrefsHelper.setAppConfig(context.context, pkg, enabled, res, hz)
}

actual fun loadAppConfig(context: AppContext, pkg: String): Triple<Boolean, String, Int> {
    val enabled = PrefsHelper.isAppEnabled(context.context, pkg)
    val res = PrefsHelper.getAppRes(context.context, pkg)
    val hz = PrefsHelper.getAppHz(context.context, pkg)
    return Triple(enabled, res, hz)
}

actual fun loadResolutions(context: AppContext): List<String> {
    return PrefsHelper.getResolutionList(context.context)
}

actual fun loadHzList(context: AppContext, resolution: String): List<Int> {
    return PrefsHelper.getHzList(context.context, resolution)
}

actual fun openAccessibilitySettings(context: AppContext) {
    try {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.context.startActivity(intent)
    } catch (e: Exception) {
    }
}
