package com.refreshrate.control.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun RefreshRateTestScreen(onBack: () -> Unit) {
    val displayData = refreshDisplayData()
    var measuredHz by remember { mutableDoubleStateOf(0.0) }
    var deliveredFps by remember { mutableDoubleStateOf(0.0) }
    var frameTimeMs by remember { mutableDoubleStateOf(0.0) }
    var jitterMs by remember { mutableDoubleStateOf(0.0) }
    var droppedFrames by remember { mutableIntStateOf(0) }
    var sampleCount by remember { mutableIntStateOf(0) }
    var phase by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        val intervals = ArrayDeque<Long>()
        var previousFrame = 0L
        var lastPublish = 0L
        while (currentCoroutineContext().isActive) {
            withFrameNanos { frameNanos ->
                phase = ((frameNanos / 1_000_000L) % 2400L) / 2400f
                if (previousFrame > 0L) {
                    val interval = frameNanos - previousFrame
                    if (interval in 1_000_000L..100_000_000L) {
                        intervals.addLast(interval)
                        if (intervals.size > 360) intervals.removeFirst()
                    }
                }
                previousFrame = frameNanos

                if (intervals.size >= 24 && frameNanos - lastPublish >= 250_000_000L) {
                    val sorted = intervals.sorted()
                    val median = sorted[sorted.size / 2].toDouble()
                    val mean = intervals.average()
                    val deviations = intervals.map { abs(it.toDouble() - median) }.sorted()
                    val medianDeviation = deviations[deviations.size / 2]

                    measuredHz = 1_000_000_000.0 / median
                    deliveredFps = 1_000_000_000.0 / mean
                    frameTimeMs = median / 1_000_000.0
                    jitterMs = medianDeviation / 1_000_000.0
                    droppedFrames = intervals.count { it > median * 1.5 }
                    sampleCount = intervals.size
                    lastPublish = frameNanos
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "实时刷新率检测",
                color = Color.Black,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (sampleCount < 24) "检测中" else "${formatOneDecimal(measuredHz)} Hz",
                            style = MiuixTheme.textStyles.title1,
                            color = Color(0xFF67F7D4)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (sampleCount < 24) "保持页面可见，正在收集帧时间" else "应用实际收到 ${formatOneDecimal(deliveredFps)} FPS",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onBackgroundVariant
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                MotionTrack(phase)

                SmallTitle("检测数据")
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        MetricRow("系统活动模式", displayData?.currentMode?.let { "${it.rateInt} Hz" } ?: "读取中")
                        MetricRow("中位帧时间", if (sampleCount > 0) "${formatOneDecimal(frameTimeMs)} ms" else "-")
                        MetricRow("帧时间抖动", if (sampleCount > 0) "${formatTwoDecimals(jitterMs)} ms" else "-")
                        MetricRow("长帧数量", if (sampleCount > 0) "$droppedFrames / $sampleCount" else "-")
                        MetricRow("检测稳定性", stabilityLabel(sampleCount, jitterMs))
                    }
                }

                Spacer(Modifier.height(12.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "该页面参考 TestUFO，通过连续帧回调估算应用实际获得的显示节奏。请保持页面前台并等待 2 秒；省电模式、温控和系统动画策略都可能影响结果。",
                        modifier = Modifier.padding(16.dp),
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onBackgroundVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MotionTrack(phase: Float) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .background(Color(0xFF080D12))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerY = size.height / 2f
                val travel = size.width + 120f
                val x = phase * travel - 60f

                for (line in 1..5) {
                    val y = size.height * line / 6f
                    drawLine(
                        color = Color.White.copy(alpha = 0.06f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f
                    )
                }
                for (trail in 6 downTo 1) {
                    drawCircle(
                        color = Color(0xFF7C5CFF).copy(alpha = 0.05f * trail),
                        radius = 15f + trail * 2f,
                        center = Offset(x - trail * 22f, centerY)
                    )
                }
                drawLine(
                    color = Color(0xFF67F7D4).copy(alpha = 0.45f),
                    start = Offset(x - 46f, centerY),
                    end = Offset(x + 46f, centerY),
                    strokeWidth = 7f,
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = Color(0xFF67F7D4),
                    radius = 23f,
                    center = Offset(x, centerY)
                )
                drawCircle(
                    color = Color.White,
                    radius = 8f,
                    center = Offset(x + 5f, centerY - 5f)
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(8.dp)
                    .background(Color(0xFF67F7D4), CircleShape)
            )
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onBackgroundVariant)
        Text(value, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onBackground)
    }
}

private fun stabilityLabel(samples: Int, jitterMs: Double): String {
    if (samples < 60) return "采样中"
    return when {
        jitterMs < 0.15 -> "稳定"
        jitterMs < 0.6 -> "轻微波动"
        else -> "波动较大"
    }
}

private fun formatOneDecimal(value: Double): String {
    val scaled = (value * 10.0).roundToInt()
    return "${scaled / 10}.${abs(scaled % 10)}"
}

private fun formatTwoDecimals(value: Double): String {
    val scaled = (value * 100.0).roundToInt()
    val decimals = abs(scaled % 100).toString().padStart(2, '0')
    return "${scaled / 100}.$decimals"
}
