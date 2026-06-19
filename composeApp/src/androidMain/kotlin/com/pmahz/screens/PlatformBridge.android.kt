package com.pmahz.screens

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.painter.BitmapPainter
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
            val ctx = context.context
            val currentHz = com.pmahz.util.AutoOverclockManager.getCurrentRefreshRate(ctx)
            val allModes = com.pmahz.util.AutoOverclockManager.getSupportedModes(ctx)
            when (authMode) {
                "root" -> com.pmahz.util.RootUtils.steppedSwitch(mode, allModes, currentHz)
                "shizuku" -> com.pmahz.util.ShizukuUtils.steppedSwitch(mode, allModes, currentHz)
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
                val parts = it.third.split(" @ ")
                val hzStr = parts.getOrNull(1)?.replace("Hz", "")?.toIntOrNull() ?: -1
                EnabledAppData(it.first, it.second, it.third, hzStr, true)
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
    // Auto-enable custom_app_refresh when any app is configured
    if (enabled) {
        PrefsHelper.setCustomAppRefresh(context.context, true)
    }
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

@Composable
actual fun loadEnabledAppsWithIcons(): List<EnabledAppData> {
    return loadEnabledApps()
}

@Composable
actual fun loadInstalledAppsWithIcons(): List<AppInfoWithIcon> {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppInfoWithIcon>>(emptyList()) }

    LaunchedEffect(Unit) {
        try {
            val pm = context.packageManager
            val installed = pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
            apps = installed.map { ai ->
                val icon = try { drawableToPainter(pm.getApplicationIcon(ai)) } catch (e: Exception) { null }
                AppInfoWithIcon(
                    name = pm.getApplicationLabel(ai).toString(),
                    packageName = ai.packageName,
                    systemApp = ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0,
                    icon = icon
                )
            }.filter { it.packageName != context.packageName }
                .sortedBy { it.name.lowercase() }
        } catch (e: Exception) {
        }
    }

    return apps
}

@Composable
actual fun rememberAppIcon(packageName: String): Painter? {
    val context = LocalContext.current
    var icon by remember { mutableStateOf<Painter?>(null) }

    LaunchedEffect(packageName) {
        try {
            val pm = context.packageManager
            val drawable = pm.getApplicationIcon(packageName)
            icon = drawableToPainter(drawable)
        } catch (e: Exception) {
        }
    }

    return icon
}

@Composable
actual fun getEnabledAppCount(): Int {
    val context = LocalContext.current
    var count by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        try {
            count = PrefsHelper.getEnabledApps(context).size
        } catch (e: Exception) {
        }
    }

    return count
}

private fun drawableToPainter(drawable: Drawable): Painter? {
    return try {
        val bitmap = android.graphics.Bitmap.createBitmap(
            drawable.intrinsicWidth.coerceAtLeast(1),
            drawable.intrinsicHeight.coerceAtLeast(1),
            android.graphics.Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        BitmapPainter(bitmap.asImageBitmap())
    } catch (e: Exception) {
        null
    }
}

@Composable
actual fun isAccessibilityServiceEnabled(): Boolean {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            enabled = com.pmahz.util.AccessibilityUtils.isKeepAliveServiceEnabled(context)
        } catch (e: Exception) {
        }
    }

    return enabled
}

actual fun testRefreshRateSwitch(authMode: String): Boolean {
    return try {
        val modes = com.pmahz.util.AutoOverclockManager.getSupportedModes(
            com.pmahz.MainActivity.instance
        )
        if (modes.isEmpty()) {
            android.util.Log.w("TestSwitch", "No modes available")
            return false
        }
        val mode = modes.first()
        android.util.Log.d("TestSwitch", "Testing: ${mode.width}x${mode.height} @ ${mode.rateInt}Hz, sfIndex=${mode.sfIndex}")
        when (authMode) {
            "root" -> com.pmahz.util.RootUtils.setDisplayMode(mode.width, mode.height, mode.rateInt, mode.sfIndex)
            "shizuku" -> com.pmahz.util.ShizukuUtils.setDisplayMode(mode.width, mode.height, mode.rateInt, mode.sfIndex)
            else -> {
                android.util.Log.w("TestSwitch", "Invalid authMode: $authMode")
                false
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("TestSwitch", "Test failed: ${e.message}")
        false
    }
}
