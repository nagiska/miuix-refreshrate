package com.refreshrate.control.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.MiuixTheme

object StatusColors {
    val healthy: Color
        @Composable get() = Color(0xFF34C759)

    val danger: Color
        @Composable get() = MiuixTheme.colorScheme.error

    val accent: Color
        @Composable get() = Color(0xFF34C759)

    val measure: Color
        @Composable get() = MiuixTheme.colorScheme.primary
}
