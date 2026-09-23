package com.refreshrate.control.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import kotlin.math.abs

data class GlassNavItem(val label: String, val icon: ImageVector)

/** 橡皮筋映射:限位内线性,超过限位后按 dim 渐近阻尼(越拉越涩)。 */
private fun rubberBand(raw: Float, limit: Float, dim: Float): Float {
    val a = abs(raw)
    if (a <= limit) return raw
    val sign = if (raw >= 0f) 1f else -1f
    return sign * (limit + dim * (1f - 1f / (1f + (a - limit) / dim)))
}

/**
 * 底部导航 = MIUIX FloatingNavigationBar(选中/高亮全 MIUIX)+ Kyant0 液态玻璃。
 * 交互:按住可向四周拖拽,带限位的橡皮筋阻尼;按压缩形(transformOrigin 跟随按压点,
 * 抓不同位置形变/倾斜不同)、沿拖动方向拉伸、垂直方向轻微压缩;松手弹性回弹。
 */
@Composable
fun LiquidGlassNavBar(
    backdrop: Backdrop,
    items: List<GlassNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    var rawX by remember { mutableFloatStateOf(0f) }
    var rawY by remember { mutableFloatStateOf(0f) }
    var origin by remember { mutableStateOf(TransformOrigin.Center) }

    val density = LocalDensity.current
    val limit = with(density) { 18f.dp.toPx() }
    val dim = with(density) { 44f.dp.toPx() }
    val blurPx = with(density) { 16f.dp.toPx() }
    val lensW = with(density) { 22f.dp.toPx() }
    val lensH = with(density) { 26f.dp.toPx() }
    val isLight = !isSystemInDarkTheme()
    val containerColor =
        if (isLight) Color(0xFFFAFAFA).copy(alpha = 0.28f)
        else Color(0xFF101010).copy(alpha = 0.28f)
    val springBack = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)

    Box(
        modifier = modifier
            .graphicsLayer {
                val dx = offsetX.value
                val dy = offsetY.value
                translationX = dx
                translationY = dy
                transformOrigin = origin
                // 沿拖动方向拉伸、垂直方向轻微压缩(液态形变)
                val sx = abs(dx) / limit
                val sy = abs(dy) / limit
                scaleX = 1f + sx * 0.07f - sy * 0.02f
                scaleY = 1f + sy * 0.07f - sx * 0.02f
                // 抓取点偏离中心时轻微倾斜,强化"相对位置变化"
                rotationZ = (origin.pivotFractionX - 0.5f) * (sx + sy) * 4f
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { start ->
                        origin = TransformOrigin(
                            (start.x / size.width.toFloat()).coerceIn(0f, 1f),
                            (start.y / size.height.toFloat()).coerceIn(0f, 1f),
                        )
                    },
                    onDragEnd = {
                        rawX = 0f; rawY = 0f
                        scope.launch {
                            offsetX.animateTo(0f, springBack)
                            offsetY.animateTo(0f, springBack)
                        }
                    },
                    onDragCancel = {
                        rawX = 0f; rawY = 0f
                        scope.launch {
                            offsetX.animateTo(0f, springBack)
                            offsetY.animateTo(0f, springBack)
                        }
                    },
                ) { _, dragAmount ->
                    rawX += dragAmount.x
                    rawY += dragAmount.y
                    scope.launch {
                        offsetX.snapTo(rubberBand(rawX, limit, dim))
                        offsetY.snapTo(rubberBand(rawY, limit, dim))
                    }
                }
            }
    ) {
        FloatingNavigationBar(
            modifier = Modifier.drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(percent = 50) },
                effects = {
                    vibrancy()
                    blur(blurPx)
                    lens(lensW, lensH)
                },
                onDrawSurface = { drawRect(containerColor) },
            ),
            color = Color.Transparent,
        ) {
            items.forEachIndexed { index, item ->
                FloatingNavigationBarItem(
                    selected = selectedIndex == index,
                    onClick = { onSelect(index) },
                    icon = item.icon,
                    label = item.label,
                )
            }
        }
    }
}
