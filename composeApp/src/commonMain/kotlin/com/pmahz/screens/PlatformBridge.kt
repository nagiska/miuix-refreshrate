package com.pmahz.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import com.pmahz.model.AppInfo
import com.pmahz.model.DisplayMode

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
    val autoOverclock: Boolean,
    val ocTargetRes: String,
    val ocTargetHz: Int,
    val rootAvailable: Boolean,
    val shizukuAvailable: Boolean,
    val shizukuHasPermission: Boolean,
    val customAppRefresh: Boolean
)

data class EnabledAppData(
    val pkg: String,
    val label: String,
    val summary: String
)

@Composable
expect fun loadSettingsData(): SettingsData?

expect fun saveAuthMode(context: AppContext, mode: String)

expect fun saveAutoOverclock(context: AppContext, enabled: Boolean, res: String, hz: Int)

expect fun saveCustomAppRefresh(context: AppContext, enabled: Boolean)

@Composable
expect fun loadEnabledApps(): List<EnabledAppData>

@Composable
expect fun loadInstalledApps(): List<AppInfo>

expect fun saveAppConfig(context: AppContext, pkg: String, enabled: Boolean, res: String, hz: Int)

expect fun loadAppConfig(context: AppContext, pkg: String): Triple<Boolean, String, Int>

expect fun loadResolutions(context: AppContext): List<String>

expect fun loadHzList(context: AppContext, resolution: String): List<Int>

expect fun openAccessibilitySettings(context: AppContext)
