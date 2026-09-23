package com.refreshrate.control.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class GlassNavItem(val label: String, val icon: ImageVector)

/**
 * 液态玻璃底部导航栏(基于 Kyant0 Backdrop):
 * 采样背后(动态背景 + 页面内容)并做 blur + lens 折射 + vibrancy,呈胶囊形玻璃 pill。
 */
@Composable
fun LiquidGlassNavBar(
    backdrop: Backdrop,
    items: List<GlassNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLight = !isSystemInDarkTheme()
    val accent = if (isLight) Color(0xFF0088FF) else Color(0xFF0091FF)
    val containerColor =
        if (isLight) Color(0xFFFAFAFA).copy(alpha = 0.35f)
        else Color(0xFF101010).copy(alpha = 0.35f)
    val unselected = MiuixTheme.colorScheme.onBackgroundVariant

    val density = LocalDensity.current
    val blurRadius = with(density) { 14f.dp.toPx() }
    val lensW = with(density) { 22f.dp.toPx() }
    val lensH = with(density) { 26f.dp.toPx() }

    Row(
        modifier = modifier
            .padding(horizontal = 28.dp, vertical = 20.dp)
            .fillMaxWidth()
            .height(62.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(blurRadius)
                    lens(lensW, lensH)
                },
                onDrawSurface = { drawRect(containerColor) },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            val tint = if (selected) accent else unselected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(index) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = tint,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = item.label,
                    color = tint,
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}
