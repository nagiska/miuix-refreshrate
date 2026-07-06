package com.refreshrate.control.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.painter.Painter
import com.refreshrate.control.model.AppInfo
import com.refreshrate.control.model.DisplayMode

expect class AppContext

val LocalAppContext: ProvidableCompositionLocal<AppContext> = compositionLocalOf {
    error("AppContext not provided")
}

@Composable
expect fun rememberAppContext(): AppContext

@Composable
expect fun refreshDisplayData(refreshKey: Int = 0): DisplayData?

expect fun applyDisplayMode(authMode: String, mode: DisplayMode, context: AppContext)

data class DisplayData(
    val currentMode: DisplayMode?,
    val authMode: String,
    val modeGroups: List<Pair<String, List<DisplayMode>>>
)

data class SettingsData(
    val authMode: String,
    val rootAvailable: Boolean,
    val customAppRefresh: Boolean
)

data class EnabledAppData(
    val pkg: String,
    val label: String,
    val summary: String,
    val hz: Int,
    val enabled: Boolean
)

data class AppInfoWithIcon(
    val name: String,
    val packageName: String,
    val systemApp: Boolean,
    val icon: Painter? = null
)

@Composable
expect fun loadSettingsData(): SettingsData?

expect fun saveAuthMode(context: AppContext, mode: String)

expect fun saveCustomAppRefresh(context: AppContext, enabled: Boolean)

@Composable
expect fun loadEnabledApps(): List<EnabledAppData>

@Composable
expect fun loadInstalledApps(): List<AppInfo>

@Composable
expect fun loadEnabledAppsWithIcons(): List<EnabledAppData>

@Composable
expect fun loadInstalledAppsWithIcons(): List<AppInfoWithIcon>

@Composable
expect fun rememberAppIcon(packageName: String): Painter?

expect fun saveAppConfig(context: AppContext, pkg: String, enabled: Boolean, res: String, hz: Int)

expect fun loadAppConfig(context: AppContext, pkg: String): Triple<Boolean, String, Int>

expect fun loadResolutions(context: AppContext): List<String>

expect fun loadHzList(context: AppContext, resolution: String): List<Int>

expect fun openAccessibilitySettings(context: AppContext)

@Composable
expect fun loadRuntimeLogs(refreshKey: Int = 0): List<String>

expect fun clearRuntimeLogs(context: AppContext)

@Composable
expect fun getEnabledAppCount(): Int

@Composable
expect fun isAccessibilityServiceEnabled(): Boolean

expect fun testRefreshRateSwitch(authMode: String): Boolean
