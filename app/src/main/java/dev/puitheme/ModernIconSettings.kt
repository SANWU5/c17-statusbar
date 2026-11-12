// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ApplicationChoice(val packageName: String, val label: String, val system: Boolean = false)

/** Launcher icons of the app pickers, drawn once at their shown size and kept for the session (bounded). */
private val appIcons = java.util.Collections.synchronizedMap(object : LinkedHashMap<String, androidx.compose.ui.graphics.ImageBitmap?>(64, .75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, androidx.compose.ui.graphics.ImageBitmap?>?) = size > 200
})

/** One application's icon, loaded off the main thread; a soft square stands in while it loads or when it has none. */
@Composable
internal fun AppIcon(app: ModernMainActivity, packageName: String, size: androidx.compose.ui.unit.Dp = 32.dp) {
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { size.roundToPx() }.coerceAtLeast(1)
    val key = "$packageName@$px"
    val bitmap by produceState(appIcons[key], key) {
        if (value == null && !appIcons.containsKey(key)) {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    val source = app.packageManager.getApplicationIcon(packageName)
                    val drawable = source.constantState?.newDrawable()?.mutate() ?: source
                    val bitmap = android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888)
                    drawable.setBounds(0, 0, px, px); drawable.draw(android.graphics.Canvas(bitmap))
                    bitmap.asImageBitmap()
                }.getOrNull()
            }
            appIcons[key] = loaded
            value = loaded
        }
    }
    val shown = bitmap
    if (shown != null) Image(shown, null, Modifier.size(size))
    else Box(Modifier.size(size).clip(RoundedCornerShape(size / 4)).background(MiuixTheme.colorScheme.primary.copy(alpha = .12f)))
}

@Composable
internal fun InstalledApplicationChoices(app: ModernMainActivity, allowed: Boolean,
    entries: List<ApplicationChoice> = app.applicationChoices, launchableOnly: Boolean = false,
    loading: Boolean = false, onChoose: (ApplicationChoice) -> Unit) {
    var query by remember { mutableStateOf("") }
    var systemOnly by remember { mutableStateOf(false) }
    val choices = remember(entries, query, systemOnly) {
        entries.filter { it.system == systemOnly &&
            (query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true)) }
    }
    var page by remember(entries, query, systemOnly) { mutableIntStateOf(0) }
    val lastPage = ((choices.size - 1).coerceAtLeast(0)) / 16
    C17TextField(value = query, onValueChange = { query = it }, label = "搜索全部已安装应用", singleLine = true,
        useLabelAsPlaceholder = true, modifier = Modifier.fillMaxWidth(), insideMargin = DpSize(12.dp, 10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        C17TextButton(text = if (!systemOnly) "✓ 非系统应用" else "非系统应用", modifier = Modifier.weight(1f), onClick = { systemOnly = false })
        C17TextButton(text = if (systemOnly) "✓ 系统应用" else "系统应用", modifier = Modifier.weight(1f), onClick = { systemOnly = true })
    }
    choices.drop(page * 16).take(16).forEach { choice -> key(choice.packageName) {
        C17ArrowPreference(title = choice.label, summary = choice.packageName, enabled = allowed,
            startAction = { Box(Modifier.padding(end = 12.dp)) { AppIcon(app, choice.packageName) } },
            onClick = { onChoose(choice) })
    } }
    if (choices.isEmpty()) Text(if (loading) "正在读取可启动应用…" else if (!launchableOnly && entries.isEmpty()) "正在读取应用列表，可先填写包名。" else "没有符合条件的应用。",
        fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    else {
        Text(trf("共 %1\$s 个应用 · 第 %2\$s / %3\$s 页", choices.size, page + 1, lastPage + 1) +
            if (launchableOnly) "，只显示可启动应用。" else "，包含没有桌面入口的应用。", fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        if (lastPage > 0) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            C17TextButton(text = "上一页", enabled = page > 0, modifier = Modifier.weight(1f), onClick = { page-- })
            C17TextButton(text = "下一页", enabled = page < lastPage, modifier = Modifier.weight(1f), onClick = { page++ })
        }
    }
}

@Composable
fun NotificationOverridesDialog(app: ModernMainActivity) {
    val json = app.value(NotificationIconOverrides.RULES)?.toString() ?: "[]"
    val rules = remember(json) { runCatching { NotificationIconOverrides.rules(json) }.getOrDefault(emptyList()) }
    var editingPackage by remember { mutableStateOf<String?>(null) }
    var selectedPackage by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var replacementType by remember { mutableStateOf(NotificationIconOverrides.TYPE_TEXT) }
    var sourcePackage by remember { mutableStateOf("") }
    var selectingTarget by remember { mutableStateOf(false) }
    var selectingSource by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val allowed = app.canEdit && !app.busy
    fun edit(rule: NotificationIconOverrides.Rule?) {
        editingPackage = rule?.packageName ?: ""
        selectedPackage = rule?.packageName ?: ""
        replacement = rule?.text ?: ""
        replacementType = rule?.type ?: NotificationIconOverrides.TYPE_TEXT
        sourcePackage = rule?.sourcePackage ?: ""
        selectingTarget = rule == null; selectingSource = false; error = ""
    }
    C17OverlayDialog(show = true, title = if (editingPackage == null) "指定应用通知图标" else "设置替换图标",
        onDismissRequest = { app.notificationOverridesOpen = false }) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (editingPackage == null) {
                Text(tr("选择文字、表情或另一个应用的彩色图标。只替换状态栏小图标，通知内容和分组不变。"), fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    if (rules.isEmpty()) UiRow("尚未添加应用", "点击下方添加，其他应用保留原生图标")
                    rules.forEach { rule -> key(rule.packageName) {
                        UiGroupCard {
                            UiSwitchRow(app.applicationLabel(rule.packageName), if (rule.isAppIcon()) trf("图标来自 %s", app.applicationLabel(rule.sourcePackage)) else trf("替换为 %s", rule.text), rule.enabled, allowed) {
                                if (rule.isAppIcon()) {
                                    val message = app.saveNotificationAppIconOverride(rule.packageName, rule.sourcePackage, it, rule.packageName)
                                    if (message.isNotEmpty()) app.toast(message)
                                }
                                else app.saveNotificationOverride(rule.packageName, rule.text, it, rule.packageName)
                            }
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                C17TextButton(text = "编辑", enabled = allowed, modifier = Modifier.weight(1f), onClick = { edit(rule) })
                                C17TextButton(text = "删除", enabled = allowed, modifier = Modifier.weight(1f), onClick = { app.removeNotificationOverride(rule.packageName) })
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    } }
                }
                C17TextButton(text = "添加应用", enabled = allowed && rules.size < 128,
                    modifier = Modifier.fillMaxWidth(), onClick = { edit(null) })
                C17TextButton(text = "完成", modifier = Modifier.fillMaxWidth(), onClick = { app.notificationOverridesOpen = false })
            } else {
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    C17TextField(value = selectedPackage, onValueChange = { selectedPackage = it; error = "" }, label = "应用包名",
                        singleLine = true, useLabelAsPlaceholder = true, modifier = Modifier.fillMaxWidth(), insideMargin = DpSize(12.dp, 10.dp))
                    C17TextButton(text = if (selectingTarget) "收起通知应用列表" else "选择通知所属应用", enabled = allowed,
                        modifier = Modifier.fillMaxWidth(), onClick = { selectingTarget = !selectingTarget })
                    if (selectingTarget) {
                        InstalledApplicationChoices(app, allowed) { selectedPackage = it.packageName; selectingTarget = false; error = "" }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        C17TextButton(text = if (replacementType == NotificationIconOverrides.TYPE_TEXT) "✓ 文字与表情" else "文字与表情",
                            enabled = allowed, modifier = Modifier.weight(1f), onClick = { replacementType = NotificationIconOverrides.TYPE_TEXT; error = "" })
                        C17TextButton(text = if (replacementType == NotificationIconOverrides.TYPE_APP_ICON) "✓ 应用图标" else "应用图标",
                            enabled = allowed, modifier = Modifier.weight(1f), onClick = { replacementType = NotificationIconOverrides.TYPE_APP_ICON; selectingSource = sourcePackage.isBlank(); error = "" })
                    }
                    if (replacementType == NotificationIconOverrides.TYPE_APP_ICON) {
                        C17TextField(value = sourcePackage, onValueChange = { sourcePackage = it; error = "" }, label = "图标来源应用包名",
                            singleLine = true, useLabelAsPlaceholder = true, modifier = Modifier.fillMaxWidth(), insideMargin = DpSize(12.dp, 10.dp))
                        C17TextButton(text = if (selectingSource) "收起图标来源应用列表" else "选择提供图标的应用", enabled = allowed,
                            modifier = Modifier.fillMaxWidth(), onClick = { selectingSource = !selectingSource })
                        if (selectingSource) InstalledApplicationChoices(app, allowed) {
                            sourcePackage = it.packageName; selectingSource = false; error = ""
                        }
                        Text(tr("保留来源应用图标的颜色。来源应用卸载或图标读取失败时，恢复原生通知图标。"), fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    } else {
                        C17TextField(value = replacement, onValueChange = { replacement = it; error = "" }, label = "替换文字或表情",
                            singleLine = true, useLabelAsPlaceholder = true, modifier = Modifier.fillMaxWidth(), insideMargin = DpSize(12.dp, 10.dp))
                        Text(tr("最多 8 个字符，可使用 ❤️、✨ 等表情。尺寸跟随原生图标或通知图标区域的大小设置。"), fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                    if (error.isNotEmpty()) Text(error, fontSize = 13.sp, color = MiuixTheme.colorScheme.primary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    C17TextButton(text = "返回", modifier = Modifier.weight(1f), onClick = { editingPackage = null })
                    C17TextButton(text = "确定", enabled = allowed, modifier = Modifier.weight(1f), onClick = {
                        val old = rules.firstOrNull { it.packageName == editingPackage }
                        error = if (replacementType == NotificationIconOverrides.TYPE_APP_ICON)
                            app.saveNotificationAppIconOverride(selectedPackage.trim(), sourcePackage.trim(), old?.enabled ?: true, editingPackage)
                        else app.saveNotificationOverride(selectedPackage.trim(), replacement, old?.enabled ?: true, editingPackage)
                        if (error.isEmpty()) editingPackage = null
                    })
                }
            }
        }
    }
}
