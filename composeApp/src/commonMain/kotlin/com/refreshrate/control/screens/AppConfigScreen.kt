package com.refreshrate.control.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun AppConfigScreen(
    packageName: String,
    appLabel: String,
    onBack: () -> Unit
) {
    val appContext = LocalAppContext.current

    var enabled by remember { mutableStateOf(false) }
    var selectedResIndex by remember { mutableStateOf(0) }
    var selectedHzIndex by remember { mutableStateOf(0) }
    var resolutions by remember { mutableStateOf<List<String>>(emptyList()) }
    var hzList by remember { mutableStateOf<List<String>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(packageName) {
        val config = loadAppConfig(appContext, packageName)
        enabled = config.first
        val savedRes = config.second
        val savedHz = config.third

        resolutions = loadResolutions(appContext)

        if (resolutions.isNotEmpty()) {
            selectedResIndex = resolutions.indexOf(savedRes).coerceAtLeast(0)
            hzList = loadHzList(appContext, resolutions[selectedResIndex]).map { formatRefreshRateOption(it) }

            if (hzList.isNotEmpty()) {
                val savedHzStr = formatRefreshRateOption(savedHz)
                selectedHzIndex = hzList.indexOf(savedHzStr).coerceAtLeast(0)
            }
        }
        loaded = true
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = appLabel,
                color = Color.Transparent,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (!loaded) {
            Text("加载中...", modifier = Modifier.padding(24.dp))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .scrollEndHaptic()
                .overScrollVertical()
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
                    onCheckedChange = {
                        enabled = it
                        val res = resolutions.getOrNull(selectedResIndex) ?: ""
                        val hz = refreshRateOptionValue(hzList.getOrNull(selectedHzIndex))
                        saveAppConfig(appContext, packageName, it, res, hz)
                    }
                )
            }

            if (enabled && resolutions.isNotEmpty()) {
                SmallTitle("分辨率 (${resolutions.size})")

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
                        onSelectedIndexChange = {
                            selectedResIndex = it
                            hzList = loadHzList(appContext, resolutions[it]).map { hz -> formatRefreshRateOption(hz) }
                            selectedHzIndex = 0
                            val res = resolutions[it]
                            val hz = refreshRateOptionValue(hzList.getOrNull(0))
                            saveAppConfig(appContext, packageName, enabled, res, hz)
                        }
                    )
                }

                if (hzList.isNotEmpty()) {
                    SmallTitle("刷新率 (${hzList.size})")

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                    ) {
                        OverlayDropdownPreference(
                            title = "目标刷新率",
                            items = hzList,
                            selectedIndex = selectedHzIndex,
                            onSelectedIndexChange = {
                                selectedHzIndex = it
                                val res = resolutions.getOrNull(selectedResIndex) ?: ""
                                val hz = refreshRateOptionValue(hzList[it])
                                saveAppConfig(appContext, packageName, enabled, res, hz)
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp).navigationBarsPadding())
        }
    }
}

private fun formatRefreshRateOption(hz: Int): String = if (hz == 0) "自动最高" else "${hz}Hz"

private fun refreshRateOptionValue(option: String?): Int {
    return if (option == "自动最高") 0 else option?.removeSuffix("Hz")?.toIntOrNull() ?: -1
}
