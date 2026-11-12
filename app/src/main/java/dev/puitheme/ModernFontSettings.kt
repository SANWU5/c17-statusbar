// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** A font choice without any persisted effect. Scope checkboxes live only in the dialog draft. */
data class FontScopeSelection(val source: String, val title: String)

@Composable
internal fun FontSettingsPanel(app: ModernMainActivity) {
    val assignments = remember(app.values) {
        runCatching { FontScopes.parse(app.value(FontScopes.KEY)?.toString() ?: "{}") }.getOrDefault(emptyMap())
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        UiGroupCard {
            UiRow("选择字体", "先选字体，再选择作用范围，统一应用后生效", app.currentFontTitle(), Icons.Outlined.TextFields,
                enabled = !app.busy) { app.chooseOpenFont() }
        }
        if (assignments.isNotEmpty()) UiGroupCard {
            FontScopes.SCOPES.filter { assignments.containsKey(it) }.forEach { scope ->
                val source = assignments.getValue(scope)
                UiRow(FontScopes.title(scope), app.fontSourceTitle(source), "更改", enabled = app.canEdit && !app.busy) {
                    app.chooseOpenFont()
                }
            }
        }
        Text(tr("建议统一字体，混用可能造成文字宽度和对齐不一致。作用范围只覆盖模块已支持的文字；对应功能未开启时保留原状。"),
            fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        if (assignments.size < FontScopes.SCOPES.size) Text(tr("尚未分配的范围继续沿用原有字体选择。各功能原有的独立设置仍保留。"),
            fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        FontLicenseFooter(app)
    }
}

@Composable
internal fun FontCatalogDialog(app: ModernMainActivity) {
    val ready = app.fontLibraryEntries.associateBy { it.revision }
    val assignments = remember(app.values) {
        runCatching { FontScopes.parse(app.value(FontScopes.KEY)?.toString() ?: "{}") }.getOrDefault(emptyMap())
    }
    val allowed = app.canEdit && !app.busy
    C17OverlayDialog(show = true, title = "选择字体",
        summary = "选中后选择作用范围，点击应用才改变字体。字号与粗细仍在各功能中调节。",
        onDismissRequest = { if (!app.busy) app.fontCatalogOpen = false }) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { UiGroupCard {
                    FontSourceRow(app, "跟随系统", trf("保留系统字体家族\n%s", app.fontUsage("system")),
                        assignments.containsValue("system")) { app.selectFontScopes("system", "跟随系统") }
                    FontSourceRow(app, "苹方", trf("内置可变字体，粗细100–900；缺字由系统补齐\n%s", app.fontUsage("pingfang")),
                        assignments.containsValue("pingfang")) { app.selectFontScopes("pingfang", "苹方") }
                    UiRow("导入字体", "TTF / OTF / TTC；入库后选择作用范围", "导入", Icons.Outlined.FileUpload,
                        enabled = allowed) { app.chooseFont() }
                } }
                items(app.fontLibraryEntries.filter { it.catalog == null }, key = { "import-${it.revision}" }) { entry ->
                    val source = FontScopes.source("custom", entry.revision)
                    UiGroupCard {
                        FontSourceRow(app, entry.name, trf("已导入 · 字重支持取决于文件\n%s", app.fontUsage(source)),
                            assignments.containsValue(source), entry, canRename = true) { app.selectLibraryFont(entry) }
                    }
                }
                items(FontCatalog.entries(), key = { it.id }) { entry ->
                    val stored = ready[entry.sha256]
                    val source = FontScopes.source("custom", entry.sha256)
                    UiGroupCard {
                        FontSourceRow(app, stored?.name ?: entry.displayName,
                            trf("%1\$s\n粗细%2\$s–%3\$s · %%.1f MB · %4\$s\n%5\$s", entry.coverage, entry.minWeight, entry.maxWeight, if (stored == null) tr("需下载") else tr("已下载"), app.fontUsage(source))
                                .format(Locale.ROOT, entry.bytes / 1048576.0),
                            assignments.containsValue(source), stored) {
                            if (stored == null) app.downloadFont(entry) else app.selectLibraryFont(stored)
                        }
                    }
                }
                item { FontLicenseFooter(app) }
            }
            C17TextButton(text = "关闭", enabled = !app.busy,
                onClick = { app.fontCatalogOpen = false }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun FontSourceRow(app: ModernMainActivity, title: String, summary: String, selected: Boolean,
    stored: FontLibrary.Entry? = null, canRename: Boolean = false, onClick: () -> Unit) {
    C17ArrowPreference(title = title, summary = summary, enabled = app.canEdit && !app.busy,
        onClick = onClick, endActions = {
            if (selected) Icon(Icons.Outlined.Check, "已分配作用范围", tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            if (stored != null) {
                if (canRename) C17IconButton(enabled = app.canEdit && !app.busy, onClick = { app.fontCatalogOpen = false; app.fontRenameTarget = stored }) {
                    Icon(Icons.Outlined.Edit, trf("重命名 %s", title), modifier = Modifier.size(20.dp))
                }
                C17IconButton(enabled = app.canEdit && !app.busy, onClick = { app.fontCatalogOpen = false; app.deleteLibraryFont(stored) }) {
                    Icon(Icons.Outlined.DeleteOutline, trf("删除 %s", title), modifier = Modifier.size(20.dp))
                }
            }
        })
}

@Composable
internal fun FontScopeDialog(app: ModernMainActivity, selection: FontScopeSelection) {
    val scopes = FontScopes.SCOPES.toList()
    var selected by remember(selection) { mutableStateOf(scopes.toSet()) }
    val allowed = app.canEdit && !app.busy
    C17OverlayDialog(show = true, title = "选择作用范围", summary = selection.title,
        onDismissRequest = app::cancelFontScopeSelection) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { UiGroupCard {
                    UiSwitchRow("统一应用", "默认选择全部已支持范围；不改变各功能开关", selected.size == scopes.size, allowed) {
                        selected = if (it) scopes.toSet() else emptySet()
                    }
                } }
                item { UiGroupCard {
                    scopes.forEach { scope ->
                        UiSwitchRow(FontScopes.title(scope), fontScopeDescription(scope), scope in selected, allowed) { checked ->
                            selected = if (checked) selected + scope else selected - scope
                        }
                    }
                } }
                item {
                    Text(if (selected.size < scopes.size) "建议统一字体。混用字体可能造成文字宽度和对齐不一致；其余范围沿用原生或各功能原有选择。所选范围在对应功能开启时生效。"
                        else "统一字体可减少字宽和对齐差异。仅覆盖模块已支持的文字，所选范围在对应功能开启时生效。",
                        fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
                if (!app.bool(FeatureOptions.masterKey("font"))) item {
                    Text(tr("字体功能关闭时，应用后只启用本次勾选范围。"), fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
                if (selected.isEmpty()) item { Text(tr("至少选择一个作用范围。"), fontSize = 13.sp, color = MiuixTheme.colorScheme.primary) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                C17TextButton(text = "取消", enabled = !app.busy, modifier = Modifier.weight(1f), onClick = app::cancelFontScopeSelection)
                C17TextButton(text = if (app.busy) "正在应用…" else trf("应用到 %s 个范围", selected.size), enabled = allowed && selected.isNotEmpty(),
                    modifier = Modifier.weight(1f), onClick = { app.applyFontScopes(selection, selected) })
            }
        }
    }
}

private fun fontScopeDescription(scope: String): String = when (scope) {
    "statusbar_clock" -> "状态栏时间文字，保留现有格式、字号与位置"
    "shade_clock" -> "通知中心、控制中心及经典模式的下拉时钟"
    "network_type" -> "模块网络制式文字及已支持的原生网络角标"
    "network_speed" -> "模块网速数值与单位"
    "shade_text" -> "下拉页由模块替换的运营商、自定义文字"
    "lockscreen_text" -> "锁屏由模块替换的文字，不包含系统锁屏主时钟"
    "notification_clock" -> "通知页竖屏、横屏大时钟及其日期、附文、左右组件，不包含普通通知正文"
    else -> ""
}

@Composable
internal fun FontLicenseFooter(app: ModernMainActivity) {
    var expanded by remember { mutableStateOf(false) }
    UiGroupCard {
        UiRow("字体来源与许可", "内置字体与开源字体的来源、许可和导入说明", if (expanded) "收起" else "查看", Icons.Outlined.Article) {
            expanded = !expanded
        }
        if (expanded) {
            UiRow("内置苹方", "Apple 原始设计 · ACT-02 可变字体重建；应用保留来源说明", "来源", Icons.Outlined.Code) {
                app.openUrl("https://github.com/ACT-02/PingFangUI-VF")
            }
            FontCatalog.entries().forEach { entry ->
                C17ArrowPreference(title = entry.displayName, summary = entry.licenseName,
                    onClick = { app.openUrl(entry.sourceUrl) }, endActions = {
                        C17IconButton(onClick = { app.openUrl(entry.licenseUrl) }) { Icon(Icons.Outlined.Article, trf("查看 %s 许可", entry.displayName), modifier = Modifier.size(20.dp)) }
                    })
            }
            UiRow("导入字体", "字体文件保存在本机；请确认拥有使用权限。可变粗细由文件支持范围决定。")
        }
    }
}
