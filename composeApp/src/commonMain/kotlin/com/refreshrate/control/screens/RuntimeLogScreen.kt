package com.refreshrate.control.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.refreshrate.control.components.BlurredBar
import com.refreshrate.control.components.CardItem
import com.refreshrate.control.components.groupedCardItems
import com.refreshrate.control.components.rememberBlurBackdrop
import com.refreshrate.control.util.horizontalCutoutPadding
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun RuntimeLogScreen(onBack: () -> Unit) {
    val appContext = LocalAppContext.current
    var refreshKey by remember { mutableIntStateOf(0) }
    val logs = loadRuntimeLogs(refreshKey)
    val backdrop = rememberBlurBackdrop()
    val barColor = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    title = "运行日志",
                    color = barColor,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(MiuixIcons.Back, contentDescription = "返回")
                        }
                    },
                )
            }
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .horizontalCutoutPadding()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                .scrollEndHaptic()
                .overScrollVertical(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = paddingValues.calculateTopPadding(),
            ),
        ) {
            groupedCardItems(
                keyPrefix = "actions",
                outerBottomPadding = 12.dp,
                items = listOf(
                    CardItem("export") {
                        ArrowPreference(
                            title = "导出日志",
                            summary = "当前 ${logs.size} 条",
                            onClick = { exportRuntimeLogs(appContext) },
                        )
                    },
                    CardItem("clear") {
                        ArrowPreference(
                            title = "清空日志",
                            summary = "清空本地运行日志",
                            onClick = {
                                clearRuntimeLogs(appContext)
                                refreshKey++
                            },
                        )
                    },
                ),
            )

            if (logs.isEmpty()) {
                groupedCardItems(
                    keyPrefix = "empty",
                    outerBottomPadding = 12.dp,
                    items = listOf(
                        CardItem("none") {
                            Text(
                                text = "暂无日志",
                                modifier = Modifier.padding(16.dp),
                                style = MiuixTheme.textStyles.body2,
                            )
                        },
                    ),
                )
            } else {
                itemsIndexed(logs, key = { index, line -> "$index:$line" }) { _, line ->
                    com.refreshrate.control.components.CardSegment(
                        isFirst = true,
                        isLast = true,
                        outerBottomPadding = 12.dp,
                    ) {
                        Text(
                            text = line,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onBackground,
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp).navigationBarsPadding()) }
        }
    }
}
