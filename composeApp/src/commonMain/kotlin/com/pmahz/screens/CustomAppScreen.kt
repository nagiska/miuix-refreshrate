package com.pmahz.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun CustomAppScreen(
    modifier: Modifier = Modifier,
    onNavigateToAppList: () -> Unit,
    onNavigateToAppConfig: (String, String) -> Unit
) {
    val appContext = LocalAppContext.current
    val settingsData = loadSettingsData()
    val enabledApps = loadEnabledApps()

    var masterEnabled by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }

    androidx.compose.runtime.LaunchedEffect(settingsData, refreshKey) {
        settingsData?.let { masterEnabled = it.customAppRefresh }
    }

    Column(modifier = modifier) {
        SmallTopAppBar(title = "逐应用刷新率")

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)
        ) {
            item { SmallTitle("总开关") }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    SwitchPreference(
                        title = "逐应用自定义刷新率",
                        summary = "开启后根据前台应用自动切换刷新率",
                        checked = masterEnabled,
                        onCheckedChange = {
                            masterEnabled = it
                            saveCustomAppRefresh(appContext, it)
                            refreshKey++
                        }
                    )
                }
            }

            if (masterEnabled) {
                item { SmallTitle("已配置应用 (${enabledApps.size})") }

                if (enabledApps.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp)
                        ) {
                            Text(
                                "暂无已配置的应用",
                                modifier = Modifier.padding(16.dp),
                                style = MiuixTheme.textStyles.body2
                            )
                        }
                    }
                } else {
                    items(enabledApps) { app ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp)
                        ) {
                            ArrowPreference(
                                title = app.label,
                                summary = app.summary,
                                onClick = { onNavigateToAppConfig(app.pkg, app.label) }
                            )
                        }
                    }
                }

                item { SmallTitle("添加应用") }

                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                    ) {
                        ArrowPreference(
                            title = "选择应用",
                            summary = "从应用列表中选择应用并配置刷新率",
                            onClick = onNavigateToAppList
                        )
                    }
                }
            }
        }
    }
}
