package com.refreshrate.control.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
fun RefreshRateTheme(
    content: @Composable () -> Unit
) {
    val controller = remember { ThemeController(ColorSchemeMode.System) }
    val sourceColors = controller.currentColors()
    val colors = sourceColors.copy(
        surfaceContainer = sourceColors.surfaceContainer.copy(
            alpha = if (sourceColors.background.red < 0.5f) 0.56f else 0.82f,
        ),
    )
    MiuixTheme(
        colors = colors,
        content = content
    )
}
