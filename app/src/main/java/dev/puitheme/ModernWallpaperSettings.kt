// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** A picture on its way to a panel: chosen from the album (or the panel's own current one) and about to be turned and cut. */
class WallpaperEdit(val scene: String, val uri: Uri)

/** The shape of the screen in this orientation, width over height: what a panel's picture is cut to. */
private fun panelAspect(app: ModernMainActivity, scene: String): Float {
    if (QsCardSettings.wallpaperScene(scene)) return 2f
    val metrics = app.resources.displayMetrics
    val shortEdge = minOf(metrics.widthPixels, metrics.heightPixels).toFloat()
    val longEdge = maxOf(metrics.widthPixels, metrics.heightPixels).toFloat()
    return if (ShadeWallpaperSettings.landscape(scene)) longEdge / shortEdge else shortEdge / longEdge
}

private fun panelName(scene: String) = when {
    QsCardSettings.wallpaperScene(scene) -> "音乐卡片"
    scene.startsWith("control_") -> "控制中心"
    scene.startsWith("classic_") -> "经典下拉"
    else -> "通知中心"
}

/**
 * One panel's picture on its tab: how it looks now, and what can be done with it. Choosing and cutting open the full-screen editor; the
 * brightness, the blur and the clock zone are ordinary settings below it.
 */
@Composable
fun ShadeWallpaperCard(app: ModernMainActivity, scene: String) {
    val revision = app.value(ShadeWallpaperSettings.assetRevisionKey(scene))?.toString().orEmpty()
    val hasImage = ShadeWallpaperSettings.validRevision(revision)
    val aspect = panelAspect(app, scene)
    val landscape = ShadeWallpaperSettings.landscape(scene) || QsCardSettings.wallpaperScene(scene)
    val allowed = app.canEdit && !app.busy
    val preview by produceState<ImageBitmap?>(null, scene, revision) {
        value = null
        if (hasImage) value = withContext(Dispatchers.IO) {
            runCatching {
                app.contentResolver.openInputStream(ShadeWallpaperRepository.uri(scene, revision)).use { input ->
                    if (input == null) null else BitmapFactory.decodeStream(input, null,
                        BitmapFactory.Options().apply { inSampleSize = 2; inScaled = false })?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    var deleting by remember(scene) { mutableStateOf(false) }
    val other = when {
        scene.startsWith("notification_") -> scene.replace("notification_", "control_")
        scene.startsWith("control_") -> scene.replace("control_", "notification_")
        else -> null
    }
    UiGroupCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxWidth(if (landscape) 1f else .46f).aspectRatio(aspect).clip(RoundedCornerShape(16.dp))
                    .background(MiuixTheme.colorScheme.surfaceContainer), contentAlignment = Alignment.Center) {
                    val image = preview
                    if (image != null) Image(bitmap = image, contentDescription = trf("%s壁纸预览", panelName(scene)),
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Text(if (hasImage) "读取中…" else "还没有图片", fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
            if (deleting) {
                Text(if (QsCardSettings.wallpaperScene(scene)) "删除音乐卡片的图片？插件将恢复原生背景，插件配置保持已保存的选择。" else trf("删除%s的图片，并关闭它的壁纸、恢复原生背景？", panelName(scene)), fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    C17TextButton(text = "取消", modifier = Modifier.weight(1f), onClick = { deleting = false })
                    C17TextButton(text = "删除", enabled = allowed, modifier = Modifier.weight(1f), onClick = {
                        app.deleteShadeWallpaper(scene); deleting = false
                    })
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    C17TextButton(text = if (hasImage) "从相册更换" else "从相册选择", enabled = allowed, modifier = Modifier.weight(1f),
                        onClick = { app.pickShadeWallpaper(scene) })
                    C17TextButton(text = "重新裁剪", enabled = allowed && hasImage, modifier = Modifier.weight(1f),
                        onClick = { app.editShadeWallpaper(scene) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (other != null) C17TextButton(text = trf("用于%s", panelName(other)), enabled = allowed && hasImage, modifier = Modifier.weight(1f),
                        onClick = { app.copyShadeWallpaper(scene, other) })
                    C17TextButton(text = "删除图片", enabled = allowed && hasImage, modifier = Modifier.weight(1f), onClick = { deleting = true })
                }
                Text(if (QsCardSettings.wallpaperScene(scene)) "为插件选择音乐卡片图片，可拖动、双指缩放和旋转，按 4×2 比例裁剪；保存后，在上方插件配置中启用壁纸并调整不透明度。" else "图片只用于解锁后的下拉面板，锁屏保持原生背景。选图后可以拖动、双指缩放、旋转，按屏幕的形状裁剪；应用后会自动启用这个面板和本页。",
                    fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        }
    }
}

private fun turn(source: Bitmap, degrees: Int): Bitmap {
    if (degrees % 360 == 0) return source
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}

/** Where the cut-out window sits on the screen of the editor: the panel's shape, as large as fits, in the middle. */
private fun cropWindow(box: IntSize, aspect: Float, margin: Float): Rect {
    val availableWidth = box.width - 2 * margin
    val availableHeight = box.height - 2 * margin
    var width = availableWidth
    var height = width / aspect
    if (height > availableHeight) { height = availableHeight; width = height * aspect }
    val left = (box.width - width) / 2f
    val top = (box.height - height) / 2f
    return Rect(left, top, left + width, top + height)
}

/** The picture is never smaller than the window and the window never leaves the picture. */
private fun clampPan(pan: Offset, window: Rect, imageWidth: Int, imageHeight: Int, zoom: Float): Offset {
    val scale = maxOf(window.width / imageWidth, window.height / imageHeight) * zoom
    val maxX = maxOf(0f, (imageWidth * scale - window.width) / 2f)
    val maxY = maxOf(0f, (imageHeight * scale - window.height) / 2f)
    return Offset(pan.x.coerceIn(-maxX, maxX), pan.y.coerceIn(-maxY, maxY))
}

/**
 * The full-screen editor: the picture under a window of the panel's shape. Drag to move it, pinch to zoom, turn it by quarter turns,
 * pick another one from the album; Apply cuts exactly what the window shows and makes it the panel's picture.
 */
@Composable
fun ShadeWallpaperCropDialog(app: ModernMainActivity, edit: WallpaperEdit) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val aspect = remember(edit.scene) { panelAspect(app, edit.scene) }
    val decoded by produceState<Result<Bitmap>?>(null, edit) {
        value = null
        value = withContext(Dispatchers.IO) { runCatching { ShadeWallpaperRepository.decode(app, edit.uri, 1800, 3_000_000L) } }
    }
    var rotation by remember(edit) { mutableIntStateOf(0) }
    var zoom by remember(edit) { mutableFloatStateOf(1f) }
    var pan by remember(edit) { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val source = decoded?.getOrNull()
    val turned = remember(source, rotation) { source?.let { turn(it, rotation) } }
    val image = remember(turned) { turned?.asImageBitmap() }
    val window = remember(box, aspect) { if (box == IntSize.Zero) Rect.Zero else cropWindow(box, aspect, 20f * density) }
    val busy = app.busy
    Dialog(onDismissRequest = { if (!busy) app.wallpaperEdit = null },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnClickOutside = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xFF101010))) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("裁剪壁纸"), fontSize = 18.sp, color = Color.White)
                        Text("${if (ShadeWallpaperSettings.landscape(edit.scene)) "横屏" else "竖屏"} · ${panelName(edit.scene)}",
                            fontSize = 13.sp, color = Color(0xFFB8B8B8))
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth().onSizeChanged { box = it }.pointerInput(window, turned) {
                    val shown = turned ?: return@pointerInput
                    detectTransformGestures { centroid, panChange, zoomChange, _ ->
                        val nextZoom = (zoom * zoomChange).coerceIn(1f, 8f)
                        val fromCenter = centroid - window.center
                        // the point under the fingers stays under them while zooming
                        val moved = (pan - fromCenter) * (nextZoom / zoom) + fromCenter + panChange
                        zoom = nextZoom
                        pan = clampPan(moved, window, shown.width, shown.height, nextZoom)
                    }
                }) {
                    val shown = image
                    when {
                        decoded == null -> Text(tr("读取图片中…"), modifier = Modifier.align(Alignment.Center), color = Color(0xFFB8B8B8), fontSize = 14.sp)
                        shown == null -> Text(trf("图片无法读取：%s", decoded?.exceptionOrNull()?.message ?: tr("格式不支持")), modifier = Modifier.align(Alignment.Center),
                            color = Color(0xFFFF8A80), fontSize = 14.sp)
                        window != Rect.Zero -> Canvas(Modifier.fillMaxSize()) {
                            val fit = maxOf(window.width / shown.width, window.height / shown.height) * zoom
                            val left = window.center.x + pan.x - shown.width * fit / 2f
                            val top = window.center.y + pan.y - shown.height * fit / 2f
                            withTransform({ translate(left, top); scale(fit, fit, Offset.Zero) }) {
                                drawImage(shown, filterQuality = FilterQuality.Medium)
                            }
                            val radius = CornerRadius(22f * density)
                            val dim = Path().apply {
                                fillType = PathFillType.EvenOdd
                                addRect(Rect(0f, 0f, size.width, size.height))
                                addRoundRect(RoundRect(window, radius))
                            }
                            drawPath(dim, Color(0xB3000000))
                            drawRoundRect(Color.White, window.topLeft, Size(window.width, window.height), radius, style = Stroke(2f * density))
                            // thirds, to compose against
                            for (i in 1..2) {
                                val x = window.left + window.width * i / 3f
                                val y = window.top + window.height * i / 3f
                                drawLine(Color(0x66FFFFFF), Offset(x, window.top), Offset(x, window.bottom), strokeWidth = density)
                                drawLine(Color(0x66FFFFFF), Offset(window.left, y), Offset(window.right, y), strokeWidth = density)
                            }
                        }
                    }
                }
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (QsCardSettings.wallpaperScene(edit.scene)) "拖动移动，双指缩放；窗口对应音乐卡片的 4×2 比例。" else "拖动移动，双指缩放；窗口的形状就是下拉面板的形状。", fontSize = 12.sp, color = Color(0xFFB8B8B8),
                        modifier = Modifier.padding(horizontal = 4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        C17TextButton(text = "换一张", enabled = !busy, modifier = Modifier.weight(1f),
                            onClick = { app.pickShadeWallpaper(edit.scene) })
                        C17TextButton(text = "向右转", enabled = !busy && turned != null, modifier = Modifier.weight(1f), onClick = {
                            rotation = (rotation + 90) % 360; zoom = 1f; pan = Offset.Zero
                        })
                        C17TextButton(text = "重置", enabled = !busy && turned != null, modifier = Modifier.weight(1f), onClick = {
                            zoom = 1f; pan = Offset.Zero
                        })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        C17TextButton(text = "取消", enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            onClick = { app.wallpaperEdit = null })
                        C17TextButton(text = if (busy) "正在处理…" else "应用", enabled = !busy && turned != null && window != Rect.Zero,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp), onClick = {
                                val shown = turned ?: return@C17TextButton
                                val fit = maxOf(window.width / shown.width, window.height / shown.height) * zoom
                                val imageLeft = window.center.x + pan.x - shown.width * fit / 2f
                                val imageTop = window.center.y + pan.y - shown.height * fit / 2f
                                app.applyShadeWallpaper(edit.scene, edit.uri, ShadeWallpaperRepository.Crop(rotation,
                                    (window.left - imageLeft) / (shown.width * fit), (window.top - imageTop) / (shown.height * fit),
                                    (window.right - imageLeft) / (shown.width * fit), (window.bottom - imageTop) / (shown.height * fit)))
                            },
                            primary = true)
                    }
                }
            }
        }
    }
}
