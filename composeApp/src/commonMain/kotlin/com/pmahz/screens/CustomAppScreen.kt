package com.pmahz.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
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
    val accessibilityEnabled = isAccessibilityServiceEnabled()

    var masterEnabled by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(settingsData, refreshKey) {
        settingsData?.let { masterEnabled = it.customAppRefresh }
    }

    Column(modifier = modifier) {
        SmallTopAppBar(title = "自定义刷新率应用")
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)
        ) {
            // 状态检查区域
            item { SmallTitle("状态检查") }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // 无障碍服务状态
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "无障碍服务: ",
                                style = MiuixTheme.textStyles.body1
                            )
                            Text(
                                text = if (accessibilityEnabled) "已开启" else "未开启",
                                style = MiuixTheme.textStyles.body1,
                                color = if (accessibilityEnabled) Color(0xFF34C759) else Color(0xFFFF4D4F)
                            )
                        }
                        if (!accessibilityEnabled) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "请先开启无障碍服务，否则无法自动切换刷新率",
                                style = MiuixTheme.textStyles.footnote1,
                                color = Color(0xFFFF4D4F)
                            )
                            Spacer(Modifier.height(8.dp))
                            ArrowPreference(
                                title = "开启无障碍服务",
                                onClick = { openAccessibilitySettings(appContext) }
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        // 授权状态
                        val authMode = settingsData?.authMode ?: ""
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "授权方式: ",
                                style = MiuixTheme.textStyles.body1
                            )
                            Text(
                                text = when (authMode) {
                                    "root" -> "Root"
                                    "shizuku" -> "Shizuku"
                                    else -> "未设置"
                                },
                                style = MiuixTheme.textStyles.body1,
                                color = if (authMode.isNotEmpty()) Color(0xFF34C759) else Color(0xFFFF4D4F)
                            )
                        }
                        if (authMode.isEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "请先在设置中选择 Root 或 Shizuku 授权方式",
                                style = MiuixTheme.textStyles.footnote1,
                                color = Color(0xFFFF4D4F)
                            )
                        }
                    }
                }
            }

            // 总开关
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

            // 已配置应用列表
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
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "暂无已配置的应用",
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onBackgroundVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "点击下方\"添加应用\"开始配置",
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onBackgroundVariant
                                )
                            }
                        }
                    }
                } else {
                    items(enabledApps) { app ->
                        val icon = rememberAppIcon(app.pkg)
                        AppListRow(
                            appName = app.label,
                            packageName = app.pkg,
                            hz = app.hz,
                            enabled = app.enabled,
                            icon = icon,
                            onClick = { onNavigateToAppConfig(app.pkg, app.label) }
                        )
                    }
                }

                // 测试按钮
                item {
                    val authMode = settingsData?.authMode ?: ""
                    if (authMode.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp)
                        ) {
                            ArrowPreference(
                                title = "测试刷新率切换",
                                summary = "测试当前授权方式是否可用，将切换到第一个可用模式",
                                onClick = {
                                    Thread {
                                        val success = testRefreshRateSwitch(authMode)
                                        android.util.Log.d("CustomAppScreen", "测试结果: $success")
                                    }.start()
                                }
                            )
                        }
                    }
                }

                // 添加应用入口
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

@Composable
private fun AppListRow(
    appName: String,
    packageName: String,
    hz: Int,
    enabled: Boolean,
    icon: androidx.compose.ui.graphics.painter.Painter?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Image(
                    painter = icon,
                    contentDescription = appName,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
                Spacer(Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appName,
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = packageName,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onBackgroundVariant
                )
                if (hz > 0) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${hz}Hz",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(6.dp))
                        if (enabled) {
                            Text(
                                text = "已启用",
                                style = MiuixTheme.textStyles.footnote2,
                                color = Color(0xFF34C759)
                            )
                        }
                    }
                }
            }

            Text(
                text = "→",
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onBackgroundVariant
            )
        }
    }
}
