package com.pmahz.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.InputField
import com.pmahz.model.AppInfo

@Composable
fun AppListScreen(
    onBack: () -> Unit,
    onAppClick: (String, String) -> Unit
) {
    var searchText by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val allApps = remember { mutableStateListOf<AppInfo>() }
    val filteredApps = remember(allApps, searchText) {
        if (searchText.isEmpty()) allApps
        else allApps.filter { it.name.contains(searchText, true) || it.packageName.contains(searchText, true) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = "选择应用",
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(MiuixIcons.Back, contentDescription = "返回")
                }
            }
        )

        SearchBar(
            inputField = {
                InputField(
                    query = searchText,
                    onQueryChange = { searchText = it },
                    onSearch = { expanded = false },
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    label = "搜索应用"
                )
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)
        ) {
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
