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
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun CustomAppScreen(
    onBack: () -> Unit,
    onNavigateToAppList: () -> Unit,
    onNavigateToAppConfig: (String, String) -> Unit
) {
    val appContext = LocalAppContext.current
    val settingsData = loadSettingsData()
    val enabledApps = loadEnabledApps()

    var masterEnabled by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(settingsData, refreshKey) {
        settingsData?.let { masterEnabled = it.customAppRefresh }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "自定义刷新率应用",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)
        ) {
            item {
                SmallTitle("总开关")
            }
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
                item {
                    SmallTitle("已配置应用 (${enabledApps.size})")
                }

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

                item {
                    SmallTitle("添加应用")
                }
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                    ) {
                        top.yukonga.miuix.kmp.basic.BasicComponent(
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
