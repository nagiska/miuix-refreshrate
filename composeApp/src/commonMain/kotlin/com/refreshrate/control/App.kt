package com.refreshrate.control

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.VerticalSplit
import com.refreshrate.control.screens.HomeScreen
import com.refreshrate.control.screens.LocalAppContext
import com.refreshrate.control.screens.rememberAppContext
import com.refreshrate.control.screens.CustomAppScreen
import com.refreshrate.control.screens.SettingsScreen
import com.refreshrate.control.screens.AppListScreen
import com.refreshrate.control.screens.AppConfigScreen
import com.refreshrate.control.theme.RefreshRateTheme

sealed class SubScreen {
    data object AppList : SubScreen()
    data class AppConfig(val pkg: String, val label: String) : SubScreen()
}

@Composable
fun App() {
    RefreshRateTheme {
        val appContext = rememberAppContext()
        var currentTab by remember { mutableStateOf(0) }
        val navigationStack = remember { mutableStateListOf<SubScreen>() }

        CompositionLocalProvider(LocalAppContext provides appContext) {
            val currentSubScreen = navigationStack.lastOrNull()

            BackHandler(enabled = navigationStack.isNotEmpty() || currentTab != 0) {
                when {
                    navigationStack.isNotEmpty() -> navigationStack.removeAt(navigationStack.lastIndex)
                    currentTab != 0 -> currentTab = 0
                }
            }

            AnimatedContent(
                targetState = currentSubScreen,
                transitionSpec = {
                    if (targetState != null) {
                        slideInHorizontally { it } togetherWith slideOutHorizontally { -it / 3 }
                    } else {
                        slideInHorizontally { -it / 3 } togetherWith slideOutHorizontally { it }
                    }
                }
            ) { subScreen ->
                when (subScreen) {
                    null -> MainScaffold(
                        currentTab = currentTab,
                        onTabChange = { currentTab = it },
                        onNavigateToAppList = { navigationStack.add(SubScreen.AppList) },
                        onNavigateToAppConfig = { pkg, label -> navigationStack.add(SubScreen.AppConfig(pkg, label)) }
                    )
                    is SubScreen.AppList -> AppListScreen(
                        onBack = { navigationStack.removeAt(navigationStack.lastIndex) },
                        onAppClick = { pkg, label ->
                            navigationStack.removeAt(navigationStack.lastIndex)
                            navigationStack.add(SubScreen.AppConfig(pkg, label))
                        }
                    )
                    is SubScreen.AppConfig -> AppConfigScreen(
                        packageName = subScreen.pkg,
                        appLabel = subScreen.label,
                        onBack = { navigationStack.removeAt(navigationStack.lastIndex) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MainScaffold(
    currentTab: Int,
    onTabChange: (Int) -> Unit,
    onNavigateToAppList: () -> Unit,
    onNavigateToAppConfig: (String, String) -> Unit
) {
    val items = listOf(
        NavigationItem("首页", MiuixIcons.Refresh),
        NavigationItem("应用", MiuixIcons.VerticalSplit),
        NavigationItem("设置", MiuixIcons.Settings)
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = currentTab == index,
                        onClick = { onTabChange(index) },
                        icon = item.icon,
                        label = item.label
                    )
                }
            }
        }
    ) { paddingValues ->
        when (currentTab) {
            0 -> HomeScreen(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                onNavigateToTab = { onTabChange(1) }
            )
            1 -> CustomAppScreen(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                onNavigateToAppList = onNavigateToAppList,
                onNavigateToAppConfig = onNavigateToAppConfig
            )
            2 -> SettingsScreen(modifier = Modifier.fillMaxSize().padding(paddingValues))
        }
    }
}
