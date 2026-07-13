package com.refreshrate.control.screens

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
import com.refreshrate.control.model.AppInfo
import com.refreshrate.control.model.DisplayMode
import com.refreshrate.control.util.AutoOverclockManager
import com.refreshrate.control.util.PrefsHelper
import com.refreshrate.control.util.RootUtils
import com.refreshrate.control.util.RuntimeLog
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.Executors

private val manualSwitchGeneration = AtomicLong(0)
private val manualSwitchExecutor = Executors.newSingleThreadExecutor()

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
        if (refreshKey > 0) kotlinx.coroutines.delay(300)
        var modeGroups: List<Pair<String, List<DisplayMode>>> = emptyList()
        while (true) {
            try {
                val authMode = PrefsHelper.getAuthMode(context).ifEmpty { "root" }
                val current = AutoOverclockManager.getCurrentMode(context)
                if (modeGroups.isEmpty()) {
                    val modes = AutoOverclockManager.getSupportedModes(context)
                    modeGroups = AutoOverclockManager.groupByResolution(modes)
                        .entries.map { it.key to it.value }.toList()
                }
                data = DisplayData(current, authMode, modeGroups)
            } catch (e: Exception) {
            }
            kotlinx.coroutines.delay(1000)
        }
    }

    return data
}

actual fun applyDisplayMode(authMode: String, mode: DisplayMode, context: AppContext) {
    val generation = manualSwitchGeneration.incrementAndGet()
    manualSwitchExecutor.execute {
        try {
            if (generation != manualSwitchGeneration.get()) return@execute
            val ctx = context.context
            val currentHz = com.refreshrate.control.util.AutoOverclockManager.getCurrentRefreshRate(ctx)
            val allModes = com.refreshrate.control.util.AutoOverclockManager.getSupportedModes(ctx)
            val steppedOk = com.refreshrate.control.util.RootUtils.switchRefreshRate(mode, allModes, currentHz) {
                generation != manualSwitchGeneration.get()
            }
            if (generation != manualSwitchGeneration.get()) {
                RuntimeLog.append(ctx, "ManualSwitch", "CANCELLED gen=$generation target=${mode.rateInt}Hz")
                return@execute
            }
            com.refreshrate.control.util.RuntimeLog.append(
                ctx,
                "ManualSwitch",
                "START gen=$generation target=${mode.resolutionLabel}@${mode.rateInt}Hz current=${currentHz}Hz steppedOk=$steppedOk root=${com.refreshrate.control.util.RootUtils.readDisplayState().summary()}"
            )
            var matched = false
            var lastSummary = "none"
            for (attempt in 1..3) {
                if (generation != manualSwitchGeneration.get()) {
                    RuntimeLog.append(ctx, "ManualSwitch", "CANCELLED gen=$generation attempt=$attempt target=${mode.rateInt}Hz")
                    return@execute
                }
                val reapplyOk = if (attempt == 1) steppedOk else com.refreshrate.control.util.RootUtils.setDisplayMode(
                    mode.width,
                    mode.height,
                    mode.rateInt,
                    mode.modeId - 1
                )
                var androidHz = -1
                var rootState = com.refreshrate.control.util.RootUtils.readDisplayState()
                var consecutiveMatches = 0
                var samples = 0
                var contradicted = rootState.hasHighRateContradiction(mode.rateInt)
                while (samples < 8 && consecutiveMatches < 4) {
                    try { Thread.sleep(250) } catch (e: InterruptedException) { break }
                    if (generation != manualSwitchGeneration.get()) return@execute
                    androidHz = com.refreshrate.control.util.AutoOverclockManager.getCurrentRefreshRate(ctx)
                    rootState = com.refreshrate.control.util.RootUtils.readDisplayState()
                    contradicted = contradicted || rootState.hasHighRateContradiction(mode.rateInt)
                    val sampleMatched = if (rootState.hasRefreshEvidence()) {
                        rootState.matchesTarget(mode.rateInt)
                    } else {
                        kotlin.math.abs(androidHz - mode.rateInt) <= 1
                    }
                    consecutiveMatches = if (sampleMatched) consecutiveMatches + 1 else 0
                    samples += 1
                }
                matched = !contradicted && consecutiveMatches >= 4
                lastSummary = "target=${mode.rateInt}Hz android=${androidHz}Hz matched=$matched root={${rootState.summary()}}"
                com.refreshrate.control.util.RuntimeLog.append(
                    ctx,
                    "ManualSwitch",
                    "VERIFY manual attempt=$attempt reapplyOk=$reapplyOk " +
                        "samples=$samples consecutive=$consecutiveMatches contradicted=$contradicted $lastSummary"
                )
                if (matched) break
            }
            com.refreshrate.control.util.RuntimeLog.append(
                ctx,
                "ManualSwitch",
                "${if (matched) "SUCCESS" else "PENDING"} manual $lastSummary snapshot=${com.refreshrate.control.util.RootUtils.readDisplaySnapshot()}"
            )
        } catch (e: Exception) {
            android.util.Log.e("PlatformBridge", "applyDisplayMode failed: ${e.message}")
            com.refreshrate.control.util.RuntimeLog.append(context.context, "ManualSwitch", "failed=${e.message}")
        }
    }
}

@Composable
actual fun loadSettingsData(): SettingsData? {
    val context = LocalContext.current
    var data by remember { mutableStateOf<SettingsData?>(null) }

    LaunchedEffect(Unit) {
        try {
            val authMode = PrefsHelper.getAuthMode(context).ifEmpty { "root" }
            val rootAvailable = RootUtils.isRootAvailable()
            val customAppRefresh = PrefsHelper.isCustomAppRefresh(context)
            data = SettingsData(
                authMode,
                rootAvailable,
                customAppRefresh
            )
        } catch (e: Exception) {
        }
    }

    return data
}

actual fun saveAuthMode(context: AppContext, mode: String) {
    PrefsHelper.setAuthMode(context.context, "root")
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
actual fun loadRuntimeLogs(refreshKey: Int): List<String> {
    val context = LocalContext.current
    var logs by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(refreshKey) {
        logs = RuntimeLog.read(context)
    }

    return logs
}

actual fun clearRuntimeLogs(context: AppContext) {
    RuntimeLog.clear(context.context)
}

actual fun exportRuntimeLogs(context: AppContext) {
    val logs = RuntimeLog.read(context.context).asReversed().joinToString("\n")
    val text = logs.ifBlank { "暂无运行日志" }
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "刷新率运行日志")
        putExtra(Intent.EXTRA_TEXT, text)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    val chooser = Intent.createChooser(sendIntent, "导出运行日志").apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    context.context.startActivity(chooser)
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
            enabled = com.refreshrate.control.util.AccessibilityUtils.isKeepAliveServiceEnabled(context)
        } catch (e: Exception) {
        }
    }

    return enabled
}

actual fun testRefreshRateSwitch(authMode: String): Boolean {
    return try {
        val modes = com.refreshrate.control.util.AutoOverclockManager.getSupportedModes(
            com.refreshrate.control.MainActivity.instance
        )
        if (modes.isEmpty()) {
            android.util.Log.w("TestSwitch", "No modes available")
            return false
        }
        val mode = modes.first()
        android.util.Log.d("TestSwitch", "Testing: ${mode.width}x${mode.height} @ ${mode.rateInt}Hz, sfIndex=${mode.sfIndex}")
        com.refreshrate.control.util.RootUtils.setDisplayMode(mode.width, mode.height, mode.rateInt, mode.modeId - 1)
    } catch (e: Exception) {
        android.util.Log.e("TestSwitch", "Test failed: ${e.message}")
        false
    }
}
