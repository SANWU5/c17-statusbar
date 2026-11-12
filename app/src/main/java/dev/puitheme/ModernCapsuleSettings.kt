// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 摄像头区域流体云的跑马灯：预设用一行摘要加弹窗里的色条网格选择，自定义颜色用一排圆点，不再占满一整页。 */

private fun presetIndex(app: ModernMainActivity): Int {
    val id = app.value(FluidCloudCapsule.MARQUEE_PRESET)?.toString()
    return FluidCloudCapsule.MARQUEE_PRESETS.indexOf(id).takeIf { it >= 0 } ?: 0
}

private fun customColors(app: ModernMainActivity): IntArray = IntArray(FluidCloudCapsule.MARQUEE_COLOR_KEYS.size) {
    (app.value(FluidCloudCapsule.MARQUEE_COLOR_KEYS[it]) as? Number)?.toInt() ?: FluidCloudCapsule.DEFAULT_MARQUEE_COLORS[it]
}

private fun customCount(app: ModernMainActivity): Int =
    ((app.value(FluidCloudCapsule.MARQUEE_COLOR_COUNT) as? Number)?.toFloat()?.let { Math.round(it) } ?: FluidCloudCapsule.DEFAULT_MARQUEE_COLOR_COUNT)
        .coerceIn(FluidCloudCapsule.MIN_MARQUEE_COLORS, FluidCloudCapsule.MAX_MARQUEE_COLORS)

/** One lap of colour as a pill-shaped strip. */
@Composable
private fun LapSwatch(colors: IntArray, modifier: Modifier) {
    val list = colors.map { Color(it) }
    val brush = Brush.horizontalGradient(if (list.size >= 2) list else listOf(list.first(), list.first()))
    Box(modifier.clip(RoundedCornerShape(50)).background(brush)
        .border(0.75.dp, MiuixTheme.colorScheme.dividerLine, RoundedCornerShape(50)))
}

/** 预设：一行，右侧是当前预设的色条，点开选择。 */
@Composable
fun MarqueePresetRow(app: ModernMainActivity, item: SettingsCatalog.Item, enabled: Boolean, note: String) {
    val index = presetIndex(app)
    val preset = FluidCloudCapsule.MARQUEE_PRESETS[index]
    val lap = FluidCloudCapsule.presetColors(preset, customColors(app), customCount(app))
    C17ArrowPreference(title = item.title, summary = note.ifEmpty { FluidCloudCapsule.MARQUEE_PRESET_LABELS[index] }, enabled = enabled,
        onClick = { app.marqueePickerOpen = true }, endActions = { LapSwatch(lap, Modifier.width(64.dp).height(18.dp)) })
}

/** 自定义颜色：一排圆点，点哪个改哪个；“自定义”预设用前 N 个，“单色流光”只用第一个，其余预设不使用。 */
@Composable
fun MarqueeColorsRow(app: ModernMainActivity, enabled: Boolean, note: String) {
    val preset = FluidCloudCapsule.MARQUEE_PRESETS[presetIndex(app)]
    val colors = customColors(app)
    val shown = if (preset == "comet") 1 else customCount(app)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr("自定义颜色"), fontSize = 16.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            for (i in 0 until shown) {
                val key = FluidCloudCapsule.MARQUEE_COLOR_KEYS[i]
                Box(Modifier.size(36.dp).clip(CircleShape).background(Color(colors[i] or (0xff shl 24)))
                    .border(0.75.dp, MiuixTheme.colorScheme.dividerLine, CircleShape)
                    .clickable(enabled = enabled) { SettingsCatalog.item(key)?.let { app.pickColor(it) } })
            }
        }
        val text = note.ifEmpty { if (preset == "comet") "单色流光只用第一个颜色" else "点圆点修改颜色；数量在上面的“自定义颜色数量”里调" }
        Text(text, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
fun MarqueePresetDialog(app: ModernMainActivity) {
    val item = SettingsCatalog.item(FluidCloudCapsule.MARQUEE_PRESET) ?: return
    val selected = presetIndex(app)
    val custom = customColors(app)
    val count = customCount(app)
    C17OverlayDialog(show = true, title = "跑马灯预设", summary = "点一个预设立即换上；“自定义”用你自己选的颜色。",
        onDismissRequest = { app.marqueePickerOpen = false }) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(FluidCloudCapsule.MARQUEE_PRESETS.indices.toList().chunked(3)) { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { index ->
                            val id = FluidCloudCapsule.MARQUEE_PRESETS[index]
                            val chosen = index == selected
                            Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                .border(if (chosen) 1.5.dp else 0.75.dp, if (chosen) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.dividerLine, RoundedCornerShape(12.dp))
                                .clickable(enabled = app.canEdit && !app.busy) { app.save(item.key, id); app.marqueePickerOpen = false }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                LapSwatch(FluidCloudCapsule.presetColors(id, custom, count), Modifier.fillMaxWidth().height(20.dp))
                                Text(FluidCloudCapsule.MARQUEE_PRESET_LABELS[index], fontSize = 12.sp, textAlign = TextAlign.Center,
                                    color = if (chosen) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface)
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            C17TextButton(text = "关闭", onClick = { app.marqueePickerOpen = false }, modifier = Modifier.fillMaxWidth())
        }
    }
}
