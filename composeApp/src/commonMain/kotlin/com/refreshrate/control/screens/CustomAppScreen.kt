package com.refreshrate.control.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.refreshrate.control.model.AppInfo

@Composable
fun CustomAppScreen(
    modifier: Modifier = Modifier,
    onNavigateToAppList: () -> Unit,
    onNavigateToAppConfig: (String, String) -> Unit
) {
    var showSystemApps by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    val enabledApps = loadEnabledApps()
    val enabledPkgs = enabledApps.map { it.pkg }.toSet()

    // Load apps list (without icons for performance)
    val allApps = loadInstalledApps()
    val filteredApps = remember(allApps, showSystemApps, searchText) {
        allApps
            .filter { if (!showSystemApps) !it.systemApp else true }
            .filter {
                searchText.isEmpty() ||
                    it.name.contains(searchText, ignoreCase = true) ||
                    it.packageName.contains(searchText, ignoreCase = true)
            }
            .sortedWith(
                compareByDescending<AppInfo> { it.packageName in enabledPkgs }
                    .thenBy { it.name.lowercase() }
            )
    }

    Column(modifier = modifier) {
        SmallTopAppBar(
            title = "应用",
            actions = {
                // System apps toggle
                Text(
                    text = if (showSystemApps) "隐藏系统" else "显示系统",
                    modifier = Modifier
                        .clickable { showSystemApps = !showSystemApps }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.primary
                )
            }
        )

        // Search bar
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            TextField(
                value = searchText,
                onValueChange = { searchText = it },
                label = "搜索应用"
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 120.dp)
        ) {
            items(filteredApps, key = { it.packageName }) { app ->
                val isConfigured = app.packageName in enabledPkgs
                val hz = if (isConfigured) enabledApps.firstOrNull { it.pkg == app.packageName }?.hz ?: -1 else -1

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToAppConfig(app.packageName, app.name) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Lazy icon loading
                        val icon = rememberAppIcon(app.packageName)
                        if (icon != null) {
                            Image(
                                painter = icon,
                                contentDescription = app.name,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                            Spacer(Modifier.width(12.dp))
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = app.name,
                                style = MiuixTheme.textStyles.title4,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = app.packageName,
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onBackgroundVariant
                            )
                            if (isConfigured && hz > 0) {
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${hz}Hz",
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = MiuixTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "已配置",
                                        style = MiuixTheme.textStyles.footnote2,
                                        color = Color(0xFF34C759)
                                    )
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
        }
    }
}
