package com.refreshrate.control.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

data class GlassNavItem(val label: String, val icon: ImageVector)

/**
 * 底部导航 = MIUIX 官方 FloatingNavigationBar(选中/高亮全用 MIUIX)+ Kyant0 液态玻璃背景。
 * - 玻璃:color=Transparent 关掉 MIUIX 自身面板,用 drawBackdrop(vibrancy+blur+lens)采样背后 backdrop。
 * - 小玩意:按住底栏可向四周小幅度拖拽(±16dp),松手弹性回弹。
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
    val density = LocalDensity.current
    val maxOffset = with(density) { 16f.dp.toPx() }
    val blurPx = with(density) { 16f.dp.toPx() }
    val lensW = with(density) { 22f.dp.toPx() }
    val lensH = with(density) { 26f.dp.toPx() }
    val isLight = !isSystemInDarkTheme()
    val containerColor =
        if (isLight) Color(0xFFFAFAFA).copy(alpha = 0.28f)
        else Color(0xFF101010).copy(alpha = 0.28f)
    val springBack = spring<Float>(dampingRatio = 0.55f, stiffness = 380f)

    Box(
        modifier = modifier
            .graphicsLayer {
                translationX = offsetX.value
                translationY = offsetY.value
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        scope.launch {
                            offsetX.animateTo(0f, springBack)
                            offsetY.animateTo(0f, springBack)
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            offsetX.animateTo(0f, springBack)
                            offsetY.animateTo(0f, springBack)
                        }
                    },
                ) { _, dragAmount ->
                    scope.launch {
                        offsetX.snapTo((offsetX.value + dragAmount.x).coerceIn(-maxOffset, maxOffset))
                        offsetY.snapTo((offsetY.value + dragAmount.y).coerceIn(-maxOffset, maxOffset))
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
