package com.refreshrate.control

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.VerticalSplit
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.refreshrate.control.components.IosGlassNavigationBar
import com.refreshrate.control.effect.Os3Background
import com.refreshrate.control.screens.HomeScreen
import com.refreshrate.control.screens.LocalAppContext
import com.refreshrate.control.screens.rememberAppContext
import com.refreshrate.control.screens.CustomAppScreen
import com.refreshrate.control.screens.SettingsScreen
import com.refreshrate.control.screens.AppListScreen
import com.refreshrate.control.screens.AppConfigScreen
import com.refreshrate.control.screens.RuntimeLogScreen
import com.refreshrate.control.screens.RefreshRateTestScreen
import com.refreshrate.control.screens.AboutScreen
import com.refreshrate.control.theme.RefreshRateTheme

sealed class SubScreen {
    data object AppList : SubScreen()
    data object RuntimeLog : SubScreen()
    data object RefreshRateTest : SubScreen()
    data object About : SubScreen()
    data class AppConfig(val pkg: String, val label: String) : SubScreen()
}

@Composable
fun App() {
    RefreshRateTheme {
        val appContext = rememberAppContext()
        var backgroundPlaying by remember { mutableStateOf(true) }
        var currentTab by remember { mutableStateOf(0) }
        val navigationStack = remember { mutableStateListOf<SubScreen>() }
        val backdrop = rememberLayerBackdrop()

        LifecycleResumeEffect(Unit) {
            backgroundPlaying = true
            onPauseOrDispose { backgroundPlaying = false }
        }

        Os3Background(
            playing = backgroundPlaying,
            modifier = Modifier.fillMaxSize(),
            backgroundModifier = Modifier.layerBackdrop(backdrop),
        ) {
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
                            (slideInHorizontally(
                                animationSpec = tween(320, easing = FastOutSlowInEasing),
                                initialOffsetX = { it / 6 },
                            ) + fadeIn(animationSpec = tween(220, delayMillis = 60))) togetherWith
                                (slideOutHorizontally(
                                    animationSpec = tween(280, easing = FastOutSlowInEasing),
                                    targetOffsetX = { -it / 16 },
                                ) + fadeOut(animationSpec = tween(180)))
                        } else {
                            (slideInHorizontally(
                                animationSpec = tween(300, easing = FastOutSlowInEasing),
                                initialOffsetX = { -it / 16 },
                            ) + fadeIn(animationSpec = tween(220, delayMillis = 40))) togetherWith
                                (slideOutHorizontally(
                                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                                    targetOffsetX = { it / 6 },
                                ) + fadeOut(animationSpec = tween(180)))
                        }
                    }
                ) { subScreen ->
                    when (subScreen) {
                        null -> MainScaffold(
                            currentTab = currentTab,
                            onTabChange = { currentTab = it },
                            onNavigateToAppList = { navigationStack.add(SubScreen.AppList) },
                            onNavigateToAppConfig = { pkg, label -> navigationStack.add(SubScreen.AppConfig(pkg, label)) },
                            onNavigateToLogs = { navigationStack.add(SubScreen.RuntimeLog) },
                            onNavigateToRefreshTest = { navigationStack.add(SubScreen.RefreshRateTest) },
                            onNavigateToAbout = { navigationStack.add(SubScreen.About) },
                            backdrop = backdrop,
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
                        SubScreen.RuntimeLog -> RuntimeLogScreen(
                            onBack = { navigationStack.removeAt(navigationStack.lastIndex) }
                        )
                        SubScreen.RefreshRateTest -> RefreshRateTestScreen(
                            onBack = { navigationStack.removeAt(navigationStack.lastIndex) }
                        )
                        SubScreen.About -> AboutScreen(
                            onBack = { navigationStack.removeAt(navigationStack.lastIndex) }
                        )
                    }
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
    onNavigateToAppConfig: (String, String) -> Unit,
    onNavigateToLogs: () -> Unit,
    onNavigateToRefreshTest: () -> Unit,
    onNavigateToAbout: () -> Unit,
    backdrop: top.yukonga.miuix.kmp.blur.LayerBackdrop,
) {
    val items = listOf(
        NavigationItem("首页", MiuixIcons.Refresh),
        NavigationItem("应用", MiuixIcons.VerticalSplit),
        NavigationItem("设置", MiuixIcons.Settings)
    )
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        when (currentTab) {
            0 -> HomeScreen(
                modifier = Modifier.fillMaxSize(),
                onNavigateToTab = { onTabChange(1) }
            )
            1 -> CustomAppScreen(
                modifier = Modifier.fillMaxSize(),
                onNavigateToAppList = onNavigateToAppList,
                onNavigateToAppConfig = onNavigateToAppConfig
            )
            2 -> SettingsScreen(
                modifier = Modifier.fillMaxSize(),
                onNavigateToLogs = onNavigateToLogs,
                onNavigateToRefreshTest = onNavigateToRefreshTest,
                onNavigateToAbout = onNavigateToAbout,
            )
            }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            IosGlassNavigationBar(
                items = items,
                selectedIndex = currentTab,
                onItemClick = onTabChange,
                backdrop = backdrop,
            )
        }
    }
}
