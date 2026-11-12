// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon

/**
 * Inside of the Gallery tab capsule, measured from Gallery 17.10.7: 56 dp high, 12 dp side padding, tabs 8 dp apart
 * and 48 dp high, the 24 dp icon 4 dp below the tab top, 2 dp to a 10 sp label. The capsule is 192 dp wide and grows
 * only when larger text needs it. The selected tab shows the solid icon and a medium label; the others the outlined
 * icon in a lighter ink. A press lights a 40 dp spotlight under the finger, like COUI's spotlight drawable, and a
 * horizontal drag reports a damped offset so the native capsule can rebound.
 */
@Composable
internal fun C17CouiTabContent(
    page: Int,
    onPage: (Int) -> Unit,
    onDrag: (Float) -> Unit,
    onRelease: () -> Unit,
) {
    val dark = LocalC17Dark.current
    val density = LocalDensity.current
    val labels = remember { listOf(tr("主页"), tr("配置"), tr("关于")) }
    val filled = remember { listOf(C17ColorOsIcons.HomeFilled, C17ColorOsIcons.TuneFilled, C17ColorOsIcons.InfoFilled) }
    val outlined = remember { listOf(C17ColorOsIcons.HomeOutline, C17ColorOsIcons.TuneOutline, C17ColorOsIcons.InfoOutline) }
    val selectedInk = if (dark) Color.White.copy(alpha = .9f) else Color(0xE6000000)
    val idleInk = if (dark) Color.White.copy(alpha = .68f) else Color.Black.copy(alpha = .68f)
    val labelStyle = TextStyle(fontSize = 10.sp, lineHeight = 13.5.sp)
    val measurer = rememberTextMeasurer()
    val labelWidth = remember(labelStyle, density, measurer) {
        with(density) { labels.maxOf { measurer.measure(it, style = labelStyle.copy(fontWeight = FontWeight.Medium)).size.width }.toDp() }
    }
    val cell = maxOf((192.dp - 24.dp - 16.dp) / 3, labelWidth + 16.dp)
    val scope = rememberCoroutineScope()
    val light = remember { Animatable(0f) }
    var spot by remember { mutableStateOf(Offset.Zero) }
    val spotRadius = with(density) { 40.dp.toPx() }
    Box(Modifier
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                spot = down.position
                scope.launch { light.animateTo(1f, tween(90)) }
                do {
                    val event = awaitPointerEvent()
                    event.changes.firstOrNull()?.let { spot = it.position }
                } while (event.changes.any { it.pressed })
                scope.launch { light.animateTo(0f, tween(260)) }
            }
        }
        .pointerInput(Unit) {
            var travelled = 0f
            detectHorizontalDragGestures(
                onDragStart = { travelled = 0f },
                onDragEnd = onRelease, onDragCancel = onRelease,
            ) { _, delta -> travelled += delta; onDrag(travelled) }
        }
        .drawWithContent {
            drawContent()
            val alpha = light.value
            if (alpha > 0f) drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = (if (dark) .18f else .55f) * alpha),
                Color.Transparent), center = spot, radius = spotRadius), radius = spotRadius, center = spot)
        }) {
        Row(Modifier.height(56.dp).padding(horizontal = 12.dp, vertical = 4.dp).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEachIndexed { index, label ->
                val on = page == index
                val ink by animateColorAsState(if (on) selectedInk else idleInk, tween(180), label = "c17-coui-tab-$index")
                Column(Modifier.width(cell).height(48.dp)
                    .selectable(on, role = Role.Tab, interactionSource = remember { MutableInteractionSource() },
                        indication = null, onClick = { if (!on) onPage(index) }),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(4.dp))
                    Icon(if (on) filled[index] else outlined[index], contentDescription = null,
                        modifier = Modifier.size(24.dp), tint = ink)
                    Spacer(Modifier.height(2.dp))
                    Text(label, style = labelStyle, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                        color = ink, softWrap = false)
                }
            }
        }
    }
}
