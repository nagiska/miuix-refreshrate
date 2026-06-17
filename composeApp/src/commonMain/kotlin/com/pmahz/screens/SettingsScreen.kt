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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val appContext = LocalAppContext.current
    val settingsData = loadSettingsData()

    var authMode by remember { mutableStateOf("") }
    var autoOverclock by remember { mutableStateOf(false) }
    var rootAvailable by remember { mutableStateOf(false) }
    var shizukuAvailable by remember { mutableStateOf(false) }
    var shizukuHasPermission by remember { mutableStateOf(false) }
    var selectedTargetIndex by remember { mutableStateOf(0) }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(settingsData, refreshKey) {
        settingsData?.let {
            authMode = it.authMode
            autoOverclock = it.autoOverclock
            rootAvailable = it.rootAvailable
            shizukuAvailable = it.shizukuAvailable
            shizukuHasPermission = it.shizukuHasPermission
        }
    }

    val ocTargets = remember { listOf("1080x2400 @ 120Hz", "1080x2400 @ 144Hz", "720x1600 @ 144Hz") }

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
                summary = if (authMode.isEmpty()) "请选择授权方式" else "当前: ${if (authMode == "root") "Root" else "Shizuku"}",
                items = listOf("Root", "Shizuku"),
                selectedIndex = if (authMode == "shizuku") 1 else if (authMode == "root") 0 else -1,
                onSelectedIndexChange = { index ->
                    val mode = if (index == 1) "shizuku" else "root"
                    authMode = mode
                    saveAuthMode(appContext, mode)
                    refreshKey++
                }
            )
        }

        if (authMode == "root") {
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
                    if (!rootAvailable) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "请确保设备已 Root 并授予超级用户权限",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onBackgroundVariant
                        )
                    }
                }
            }
        }

        if (authMode == "shizuku") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = when {
                            shizukuAvailable && shizukuHasPermission -> "Shizuku: 已授权"
                            shizukuAvailable -> "Shizuku: 运行中，未授权"
                            else -> "Shizuku: 未运行"
                        },
                        style = MiuixTheme.textStyles.body1,
                        color = if (shizukuAvailable && shizukuHasPermission) MiuixTheme.colorScheme.primary else Color.Red
                    )
                    if (!shizukuAvailable || !shizukuHasPermission) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "请先启动 Shizuku 并在 Shizuku 管理器中授权",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onBackgroundVariant
                        )
                    }
                }
            }
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
                onCheckedChange = {
                    autoOverclock = it
                    val targetStr = ocTargets.getOrNull(selectedTargetIndex) ?: ""
                    val parts = targetStr.split(" @ ")
                    val res = parts.getOrNull(0) ?: ""
                    val hzStr = parts.getOrNull(1)?.replace("Hz", "") ?: ""
                    val hz = hzStr.toIntOrNull() ?: -1
                    saveAutoOverclock(appContext, it, res, hz)
                }
            )

            if (autoOverclock) {
                OverlayDropdownPreference(
                    title = "目标模式",
                    items = ocTargets,
                    selectedIndex = selectedTargetIndex,
                    onSelectedIndexChange = {
                        selectedTargetIndex = it
                        val targetStr = ocTargets[it]
                        val parts = targetStr.split(" @ ")
                        val res = parts.getOrNull(0) ?: ""
                        val hzStr = parts.getOrNull(1)?.replace("Hz", "") ?: ""
                        val hz = hzStr.toIntOrNull() ?: -1
                        saveAutoOverclock(appContext, true, res, hz)
                    }
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
                onClick = { }
            )
        }
    }
}
