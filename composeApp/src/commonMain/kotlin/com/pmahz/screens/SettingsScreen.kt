package com.pmahz.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    var authMode by remember { mutableStateOf("") }
    var autoOverclock by remember { mutableStateOf(false) }
    var selectedTargetIndex by remember { mutableStateOf(0) }
    val overclockTargets = remember { listOf("1080x2400 @ 120Hz", "1080x2400 @ 144Hz", "720x1600 @ 144Hz") }

    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        SmallTopAppBar(title = "设置")

        SmallTitle("授权管理")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
        ) {
            OverlayDropdownPreference(
                title = "授权方式",
                summary = "选择 Root 或 Shizuku",
                items = listOf("Root", "Shizuku"),
                selectedIndex = if (authMode == "shizuku") 1 else 0,
                onSelectedIndexChange = { authMode = if (it == 1) "shizuku" else "root" }
            )
        }

        SmallTitle("自动超频")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
        ) {
            SwitchPreference(
                title = "开机自动超频",
                summary = "开机后自动设置目标刷新率",
                checked = autoOverclock,
                onCheckedChange = { autoOverclock = it }
            )

            if (autoOverclock) {
                OverlayDropdownPreference(
                    title = "目标模式",
                    items = overclockTargets,
                    selectedIndex = selectedTargetIndex,
                    onSelectedIndexChange = { selectedTargetIndex = it }
                )
            }
        }

        SmallTitle("其他")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
        ) {
            ArrowPreference(
                title = "无障碍服务",
                summary = "点击前往无障碍设置页面",
                onClick = { }
            )
        }

        SmallTitle("关于")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
        ) {
            ArrowPreference(
                title = "关于应用",
                summary = "Miuix Refresh Rate v1.0.0",
                onClick = { }
            )
        }
    }
}
