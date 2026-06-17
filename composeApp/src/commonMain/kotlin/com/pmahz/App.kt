package com.pmahz

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.VerticalSplit
import top.yukonga.miuix.kmp.icon.extended.Home
import com.pmahz.screens.HomeScreen
import com.pmahz.screens.LocalAppContext
import com.pmahz.screens.rememberAppContext
import com.pmahz.screens.CustomAppScreen
import com.pmahz.screens.SettingsScreen
import com.pmahz.screens.AppListScreen
import com.pmahz.screens.AppConfigScreen
import com.pmahz.theme.RefreshRateTheme

@Composable
fun App() {
    RefreshRateTheme {
        val appContext = rememberAppContext()
        var currentTab by remember { mutableStateOf(0) }
        var currentScreen by remember { mutableStateOf<Screen>(Screen.Main) }

        CompositionLocalProvider(LocalAppContext provides appContext) {
            AnimatedContent(targetState = currentScreen) { screen ->
            when (screen) {
                Screen.Main -> MainScaffold(
                    currentTab = currentTab,
                    onTabChange = { currentTab = it },
                    onNavigateToAppList = { currentScreen = Screen.AppList },
                    onNavigateToAppConfig = { pkg, label -> currentScreen = Screen.AppConfig(pkg, label) }
                )
                Screen.AppList -> AppListScreen(
                    onBack = { currentScreen = Screen.Main },
                    onAppClick = { pkg, label -> currentScreen = Screen.AppConfig(pkg, label) }
                )
                is Screen.AppConfig -> AppConfigScreen(
                    packageName = screen.pkg,
                    appLabel = screen.label,
                    onBack = { currentScreen = Screen.Main }
                )
            }
            }
        }
    }
}

sealed class Screen {
    data object Main : Screen()
    data object AppList : Screen()
    data class AppConfig(val pkg: String, val label: String) : Screen()
}

@Composable
private fun MainScaffold(
    currentTab: Int,
    onTabChange: (Int) -> Unit,
    onNavigateToAppList: () -> Unit,
    onNavigateToAppConfig: (String, String) -> Unit
) {
    val items = listOf(
        NavigationItem("首页", MiuixIcons.Home),
        NavigationItem("应用", MiuixIcons.VerticalSplit),
        NavigationItem("设置", MiuixIcons.Settings)
    )

    Scaffold(
        bottomBar = {
            NavigationBar(
                items = items,
                selected = currentTab,
                onClick = { onTabChange(it) }
            )
        }
    ) { paddingValues ->
        when (currentTab) {
            0 -> HomeScreen(modifier = Modifier.fillMaxSize().padding(paddingValues))
            1 -> CustomAppScreen(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                onNavigateToAppList = onNavigateToAppList,
                onNavigateToAppConfig = onNavigateToAppConfig
            )
            2 -> SettingsScreen(modifier = Modifier.fillMaxSize().padding(paddingValues))
        }
    }
}
