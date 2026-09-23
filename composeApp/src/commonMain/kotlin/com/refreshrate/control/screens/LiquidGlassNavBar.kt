package com.refreshrate.control.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import kotlin.math.abs
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem

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
 * 交互(模仿参照视频):
 *  - 按下即在按压点产生局部"凹陷"形变(形变枢轴 transformOrigin 跟随按压点,抓哪儿瘪哪儿),
 *    垂直压扁 + 水平微张,抓偏中心带一点倾斜;
 *  - 拖动跟手,带限位橡皮筋阻尼;
 *  - 松手弹性回弹到原位,形变同步复原。
 */
@Composable
fun LiquidGlassNavBar(
    backdrop: Backdrop,
    items: List<GlassNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val pressScale = remember { Animatable(0f) }
    var origin by remember { mutableStateOf(TransformOrigin.Center) }

    val density = LocalDensity.current
    val limit = with(density) { 20f.dp.toPx() }
    val dim = with(density) { 46f.dp.toPx() }
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
                val ps = pressScale.value
                val sxDrag = abs(dx) / limit
                val syDrag = abs(dy) / limit
                // 按压处局部形变:以按压点为枢轴,垂直压扁、水平微张;拖动时沿方向再拉伸
                scaleX = 1f + ps * 0.06f + sxDrag * 0.05f
                scaleY = 1f - ps * 0.12f + syDrag * 0.05f
                // 抓取点偏离中心时轻微倾斜,强化"相对位置变化"
                rotationZ = (origin.pivotFractionX - 0.5f) * ps * 5f
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    val h = size.height.toFloat().coerceAtLeast(1f)
                    origin = TransformOrigin(
                        (down.position.x / w).coerceIn(0f, 1f),
                        (down.position.y / h).coerceIn(0f, 1f),
                    )
                    pressScale.animateTo(1f, tween(120))
                    var total = Offset.Zero
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        total += change.positionChange()
                        offsetX.snapTo(rubberBand(total.x, limit, dim))
                        offsetY.snapTo(rubberBand(total.y, limit, dim))
                    }
                    // 松手:弹性回弹到原位 + 形变复原
                    offsetX.animateTo(0f, springBack)
                    offsetY.animateTo(0f, springBack)
                    pressScale.animateTo(0f, tween(150))
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
