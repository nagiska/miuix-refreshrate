package com.pmahz.screens

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.pmahz.model.DisplayMode
import com.pmahz.util.AutoOverclockManager
import com.pmahz.util.RootUtils
import com.pmahz.util.ShizukuUtils

actual class AppContext(val context: Context)

@Composable
actual fun rememberAppContext(): AppContext {
    val ctx = LocalContext.current
    return remember(ctx) { AppContext(ctx) }
}

@Composable
actual fun refreshDisplayData(): DisplayData? {
    val context = LocalContext.current
    var data by remember { mutableStateOf<DisplayData?>(null) }

    LaunchedEffect(Unit) {
        try {
            val prefs = context.getSharedPreferences("s", Context.MODE_PRIVATE)
            val authMode = prefs.getString("auth_mode", "") ?: ""
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
    try {
        when (authMode) {
            "root" -> RootUtils.setDisplayMode(mode.width, mode.height, mode.rateInt, mode.sfIndex)
            "shizuku" -> ShizukuUtils.setDisplayMode(mode.width, mode.height, mode.rateInt, mode.sfIndex)
        }
    } catch (e: Exception) {
    }
}
