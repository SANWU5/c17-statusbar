// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.round
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ShadeWallpaperDialog(app: ModernMainActivity) {
    var panel by remember { mutableStateOf("classic") }
    var landscape by remember { mutableStateOf(false) }
    val scene = ShadeWallpaperSettings.scene(if (panel == "classic") "classic" else "separate", panel == "control", landscape)!!
    var deleting by remember { mutableStateOf(false) }
    val allowed = app.canEdit && !app.busy
    val revision = app.value(ShadeWallpaperSettings.revisionKey(scene))?.toString().orEmpty()
    val hasImage = ShadeWallpaperSettings.validRevision(revision)
    val storedBrightness = ShadeWallpaperSettings.brightness(app.value(ShadeWallpaperSettings.brightnessKey(scene)))
    var brightness by remember(scene, storedBrightness) { mutableFloatStateOf(storedBrightness) }
    val preview by produceState<ImageBitmap?>(null, scene, revision) {
        value = null
        if (ShadeWallpaperSettings.validRevision(revision)) value = withContext(Dispatchers.IO) {
            runCatching {
                app.contentResolver.openInputStream(ShadeWallpaperRepository.uri(scene, revision)).use { input ->
                    if (input == null) null else BitmapFactory.decodeStream(input, null,
                        BitmapFactory.Options().apply { inSampleSize = 4; inScaled = false })?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    val previewFilter = remember(brightness) {
        val multiplier = if (brightness <= 100f) brightness / 100f else 2f - brightness / 100f
        val offset = if (brightness > 100f) (brightness - 100f) * 2.55f else 0f
        ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
            multiplier, 0f, 0f, 0f, offset, 0f, multiplier, 0f, 0f, offset,
            0f, 0f, multiplier, 0f, offset, 0f, 0f, 0f, 1f, 0f)))
    }
    OverlayDialog(show = true, title = if (deleting) "删除这张壁纸" else "下拉面板壁纸",
        onDismissRequest = { if (!app.busy) app.shadeWallpaperOpen = false }) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (deleting) {
                Text("删除${ShadeWallpaperSettings.title(scene)}的图片并恢复原生背景。", fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(text = "取消", modifier = Modifier.weight(1f), onClick = { deleting = false })
                    TextButton(text = "删除", enabled = allowed, modifier = Modifier.weight(1f), onClick = {
                        app.deleteShadeWallpaper(scene); deleting = false
                    })
                }
            } else {
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("经典与分离式分别保存，横屏与竖屏分别设置。分离式左右切换时，壁纸跟随原生手势平滑过渡。", fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("classic" to "经典", "notification" to "分离通知栏", "control" to "分离控制中心").forEach { (id, title) ->
                            TextButton(text = if (panel == id) "✓ $title" else title, enabled = !app.busy,
                                modifier = Modifier.weight(1f), onClick = { panel = id })
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(text = if (!landscape) "✓ 竖屏" else "竖屏", enabled = !app.busy,
                            modifier = Modifier.weight(1f), onClick = { landscape = false })
                        TextButton(text = if (landscape) "✓ 横屏" else "横屏", enabled = !app.busy,
                            modifier = Modifier.weight(1f), onClick = { landscape = true })
                    }
                    Text(ShadeWallpaperSettings.title(scene), fontSize = 16.sp, color = MiuixTheme.colorScheme.primary)
                    preview?.let { image ->
                        Image(bitmap = image, contentDescription = "${ShadeWallpaperSettings.title(scene)}壁纸预览",
                            modifier = Modifier.fillMaxWidth().height(128.dp), contentScale = ContentScale.Crop, colorFilter = previewFilter)
                    }
                    UiGroupCard {
                        UiSwitchRow("启用${ShadeWallpaperSettings.title(scene)}", "关闭后使用原生背景，保留已导入图片",
                            app.bool(ShadeWallpaperSettings.enabledKey(scene)), allowed && hasImage) {
                            app.save(ShadeWallpaperSettings.enabledKey(scene), it)
                        }
                        UiRow("选择图片", "PNG、JPEG 或 WebP，最多 24 MB", if (hasImage) "更换" else "导入", enabled = allowed) {
                            app.openShadeWallpaperImport(scene)
                        }
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("图片明暗", fontSize = 16.sp)
                                Text("${round(brightness).toInt()}%", fontSize = 14.sp, color = MiuixTheme.colorScheme.primary)
                            }
                            Slider(value = brightness, onValueChange = { brightness = round(it) }, valueRange = 0f..200f,
                                enabled = allowed && hasImage, onValueChangeFinished = {
                                    app.save(ShadeWallpaperSettings.brightnessKey(scene), brightness)
                                })
                            Text("100% 保留原图，小于 100% 变暗，大于 100% 变亮。图片居中裁剪填满下拉面板。", fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                        UiRow("删除当前场景图片", "恢复这一场景的原生背景", "删除", enabled = allowed && hasImage) { deleting = true }
                    }
                    Text(if (!app.bool(ShadeWallpaperSettings.MASTER)) "壁纸总开关当前关闭，可在下拉面板壁纸页面启用。图片只用于解锁后的系统下拉面板，锁屏保持原生背景。"
                        else "图片只用于解锁后的系统下拉面板，锁屏保持原生背景。未启用或图片不可读取时自动使用原生背景。", fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
                TextButton(text = "完成", enabled = !app.busy, modifier = Modifier.fillMaxWidth(), onClick = { app.shadeWallpaperOpen = false })
            }
        }
    }
}
