package com.pmahz.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pmahz.model.DisplayMode
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val displayData = refreshDisplayData()
    val appContext = LocalAppContext.current
    var refreshTrigger by remember { mutableStateOf(0) }

    Column(modifier = modifier) {
        SmallTopAppBar(title = "屏幕刷新率")

        if (displayData == null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                Text("加载中...")
            }
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(top = 12.dp, bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("当前刷新率", style = MiuixTheme.textStyles.body2)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = displayData.currentMode?.let {
                                "${it.resolutionLabel} @ ${it.rateInt}Hz"
                            } ?: "未知",
                            style = MiuixTheme.textStyles.title2
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(displayData.currentMode?.rateName ?: "", style = MiuixTheme.textStyles.body2)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = when (displayData.authMode) {
                                "root" -> "授权: Root"
                                "shizuku" -> "授权: Shizuku"
                                else -> "未授权，请在设置中配置"
                            },
                            style = MiuixTheme.textStyles.footnote1
                        )
                    }
                }
            }

            displayData.modeGroups.forEach { (resolution, modes) ->
                item { SmallTitle(text = resolution) }
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                    ) {
                        modes.forEachIndexed { index, mode ->
                            RateCardItem(
                                mode = mode,
                                isCurrent = displayData.currentMode?.let {
                                    it.width == mode.width && it.height == mode.height && it.rateInt == mode.rateInt
                                } == true,
                                onClick = {
                                    applyDisplayMode(displayData.authMode, mode, appContext)
                                    refreshTrigger++
                                }
                            )
                            if (index < modes.size - 1) HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RateCardItem(
    mode: DisplayMode,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    BasicComponent(
        title = "${mode.rateInt}Hz · ${mode.rateName}",
        summary = mode.rateDesc,
        onClick = onClick,
        endActions = {
            if (isCurrent) {
                Text(
                    text = "当前",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.primary
                )
            }
        }
    )
}
