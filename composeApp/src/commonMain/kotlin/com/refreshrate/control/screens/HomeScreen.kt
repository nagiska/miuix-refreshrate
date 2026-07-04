package com.refreshrate.control.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.refreshrate.control.model.DisplayMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onNavigateToTab: () -> Unit = {}
) {
    val appContext = LocalAppContext.current
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val displayData = refreshDisplayData(refreshTrigger)
    val enabledAppCount = getEnabledAppCount()

    val accessibilityEnabled = isAccessibilityServiceEnabled()
    val authMode = displayData?.authMode ?: ""

    Column(modifier = modifier) {
        SmallTopAppBar(title = "屏幕刷新率")

        // Status indicators row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Auth mode indicator
            Text(
                text = when (authMode) {
                    "root" -> "Root"
                    "shizuku" -> "Shizuku"
                    else -> "未授权"
                },
                style = MiuixTheme.textStyles.footnote2,
                color = if (authMode.isNotEmpty()) Color(0xFF34C759) else Color(0xFFFF4D4F)
            )
            Spacer(Modifier.width(8.dp))
            // Accessibility service indicator
            Text(
                text = if (accessibilityEnabled) "无障碍✓" else "无障碍✕",
                style = MiuixTheme.textStyles.footnote2,
                color = if (accessibilityEnabled) Color(0xFF34C759) else Color(0xFFFF4D4F)
            )
        }

        if (displayData == null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("加载中...")
            }
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 12.dp, end = 12.dp, top = 12.dp, bottom = 16.dp
            )
        ) {
            item {
                StatusCard(
                    currentHz = displayData.currentMode?.rateInt ?: -1,
                    resolution = displayData.currentMode?.resolutionLabel ?: "未知",
                    rateName = displayData.currentMode?.rateName ?: "",
                    authMode = displayData.authMode
                )
            }

            item {
                Spacer(Modifier.height(12.dp))
                CustomAppCountCard(
                    count = enabledAppCount,
                    onClick = onNavigateToTab
                )
            }

            displayData.modeGroups.forEach { (resolution, modes) ->
                item {
                    SmallTitle(text = resolution)
                }
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        modes.forEachIndexed { index, mode ->
                            RateCardItem(
                                mode = mode,
                                isCurrent = displayData.currentMode?.let {
                                    it.width == mode.width && it.height == mode.height && it.rateInt == mode.rateInt
                                } == true,
                                onClick = {
                                    scope.launch {
                                        val currentHz = displayData.currentMode?.rateInt ?: 0
                                        val targetHz = mode.rateInt
                                        applyDisplayMode(displayData.authMode, mode, appContext)
                                        // Calculate delay based on actual stepping steps
                                        val stepCount = displayData.modeGroups
                                            .flatMap { it.second }
                                            .count { it.rateInt > currentHz && it.rateInt <= targetHz }
                                        val stepDelay = if (targetHz > currentHz) {
                                            stepCount * 800L + 1000L
                                        } else 1000L
                                        delay(stepDelay)
                                        refreshTrigger++
                                    }
                                }
                            )
                            if (index < modes.size - 1) HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    currentHz: Int,
    resolution: String,
    rateName: String,
    authMode: String
) {
    val isDark = isSystemInDarkTheme()
    val cardBg = if (isDark) Color(0xFF1E3D2E) else Color(0xFFE8F5E9)
    val titleColor = if (isDark) Color.White else Color(0xFF1A1A1A)
    val subtitleColor = if (isDark) Color(0xFFD0D0D0) else Color(0xFF666666)
    val accentColor = Color(0xFF34C759)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        cornerRadius = 20.dp,
        colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(
            color = cardBg
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Text(
                    text = "工作中",
                    style = MiuixTheme.textStyles.title2,
                    color = titleColor
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = resolution,
                    style = MiuixTheme.textStyles.body2,
                    color = subtitleColor
                )

                Spacer(Modifier.height(20.dp))

                Text(
                    text = if (currentHz > 0) "${currentHz}Hz" else "未知",
                    style = MiuixTheme.textStyles.title1,
                    color = titleColor
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = when (authMode) {
                        "root" -> "授权方式: Root"
                        "shizuku" -> "授权方式: Shizuku"
                        else -> "未授权"
                    },
                    style = MiuixTheme.textStyles.footnote1,
                    color = subtitleColor
                )
            }

            // Large checkmark circle (bottom-right, partially overflow)
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 24.dp, y = 24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            width = 12.dp,
                            color = accentColor,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Custom thick checkmark with rounded corners
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier.size(96.dp)
                    ) {
                        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = size.minDimension * 0.12f,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                            join = androidx.compose.ui.graphics.StrokeJoin.Round
                        )
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(size.width * 0.2f, size.height * 0.5f)
                            lineTo(size.width * 0.42f, size.height * 0.72f)
                            lineTo(size.width * 0.8f, size.height * 0.28f)
                        }
                        drawPath(
                            path = path,
                            color = accentColor,
                            style = stroke
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomAppCountCard(
    count: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        cornerRadius = 20.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (count > 0) "$count" else "0",
                    style = MiuixTheme.textStyles.title1
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "自定义刷新率应用",
                    style = MiuixTheme.textStyles.body2
                )
            }
            Text(
                text = "→",
                style = MiuixTheme.textStyles.title2,
                color = MiuixTheme.colorScheme.onBackgroundVariant
            )
        }
    }
}

@Composable
private fun RateCardItem(
    mode: DisplayMode,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    BasicComponent(
        title = "${mode.rateInt}Hz · ${mode.rateName}",
        summary = mode.rateDesc,
        onClick = onClick,
        endActions = {
            if (isCurrent) {
                Text(
                    text = "当前",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.primary
                )
            }
        }
    )
}
