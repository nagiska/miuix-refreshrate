package com.pmahz.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import com.pmahz.model.DisplayMode

expect class AppContext

val LocalAppContext: ProvidableCompositionLocal<AppContext> = compositionLocalOf {
    error("AppContext not provided")
}

@Composable
expect fun rememberAppContext(): AppContext

@Composable
expect fun refreshDisplayData(): DisplayData?

expect fun applyDisplayMode(authMode: String, mode: DisplayMode, context: AppContext)

data class DisplayData(
    val currentMode: DisplayMode?,
    val authMode: String,
    val modeGroups: List<Pair<String, List<DisplayMode>>>
)
