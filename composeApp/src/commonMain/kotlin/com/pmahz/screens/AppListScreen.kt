package com.pmahz.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pmahz.model.AppInfo
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AppListScreen(
    onBack: () -> Unit,
    onAppClick: (String, String) -> Unit
) {
    var searchText by remember { mutableStateOf("") }
    val allApps = loadInstalledApps()
    val filteredApps = remember(allApps, searchText) {
        if (searchText.isEmpty()) allApps
        else allApps.filter { it.name.contains(searchText, true) || it.packageName.contains(searchText, true) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SmallTopAppBar(title = "选择应用")

        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            TextField(
                value = searchText,
                onValueChange = { searchText = it },
                label = "搜索应用"
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)
        ) {
            if (filteredApps.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                    ) {
                        top.yukonga.miuix.kmp.basic.Text(
                            "未找到应用",
                            modifier = Modifier.padding(16.dp),
                            style = MiuixTheme.textStyles.body2
                        )
                    }
                }
            } else {
                items(filteredApps) { app ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                    ) {
                        BasicComponent(
                            title = app.name,
                            summary = app.packageName,
                            onClick = { onAppClick(app.packageName, app.name) }
                        )
                    }
                }
            }
        }
    }
}
