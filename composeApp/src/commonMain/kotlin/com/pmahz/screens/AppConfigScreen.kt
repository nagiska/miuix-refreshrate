package com.pmahz.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.basic.Text

@Composable
fun AppConfigScreen(
    packageName: String,
    appLabel: String,
    onBack: () -> Unit
) {
    var enabled by remember { mutableStateOf(false) }
    var selectedResIndex by remember { mutableStateOf(0) }
    var selectedHzIndex by remember { mutableStateOf(0) }

    val resolutions = remember { listOf("1080x2400", "720x1600", "1440x3200") }
    val refreshRates = remember { listOf("60", "90", "120", "144") }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = appLabel,
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(MiuixIcons.Back, contentDescription = "返回")
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            SmallTitle("刷新率配置")

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
            ) {
                SwitchPreference(
                    title = "启用自定义刷新率",
                    summary = "切换到此应用时自动应用",
                    checked = enabled,
                    onCheckedChange = { enabled = it }
                )
            }

            if (enabled) {
                SmallTitle("分辨率")

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    OverlayDropdownPreference(
                        title = "目标分辨率",
                        items = resolutions,
                        selectedIndex = selectedResIndex,
                        onSelectedIndexChange = { selectedResIndex = it }
                    )
                }

                SmallTitle("刷新率")

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    OverlayDropdownPreference(
                        title = "目标刷新率",
                        items = refreshRates,
                        selectedIndex = selectedHzIndex,
                        onSelectedIndexChange = { selectedHzIndex = it }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
