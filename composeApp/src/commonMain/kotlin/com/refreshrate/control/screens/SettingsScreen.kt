package com.refreshrate.control.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val appContext = LocalAppContext.current
    val settingsData = loadSettingsData()

    var rootAvailable by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        saveAuthMode(appContext, "root")
    }

    LaunchedEffect(settingsData, refreshKey) {
        settingsData?.let {
            rootAvailable = it.rootAvailable
        }
    }

    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        SmallTopAppBar(
            title = "设置",
            color = MiuixTheme.colorScheme.background
        )

        SmallTitle("授权管理")

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (rootAvailable) "Root 权限: 已获取" else "Root 权限: 未获取",
                    style = MiuixTheme.textStyles.body1,
                    color = if (rootAvailable) MiuixTheme.colorScheme.primary else Color.Red
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (rootAvailable) "当前仅使用 Root 方式切换刷新率" else "请确保设备已 Root 并授予超级用户权限",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onBackgroundVariant
                )
            }
        }

        SmallTitle("无障碍服务")

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
        ) {
            ArrowPreference(
                title = "开启无障碍服务",
                summary = "逐应用刷新率需要无障碍服务支持，点击前往设置",
                onClick = { openAccessibilitySettings(appContext) }
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
                onClick = { refreshKey++ }
            )
        }

        Spacer(Modifier.height(120.dp))
    }
}
