// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 分享、导出、导入配置之前先问一句：是否自定义项。第一步选“全部”或“自定义”，自定义则逐个页面勾选。 */

private fun verb(kind: String) = when (kind) { "share" -> "分享"; "import" -> "导入"; else -> "导出" }

/** The pages the choice is about: all of them for share and export, only those the file carries for an import. */
private fun choices(request: ConfigScopeRequest): List<Pair<ConfigScope.Section, Int>> =
    if (request.prepared != null) ConfigScope.present(request.prepared.keys()).map { it.key to it.value }
    else ConfigScope.sections().map { it to it.count() }

@Composable
fun ConfigScopeDialog(app: ModernMainActivity, request: ConfigScopeRequest) {
    if (request.customizing) ConfigScopeChecklist(app, request) else ConfigScopeAsk(app, request)
}

@Composable
private fun ConfigScopeAsk(app: ModernMainActivity, request: ConfigScopeRequest) {
    val action = verb(request.kind)
    val pages = choices(request)
    val total = pages.sumOf { it.second }
    val summary = if (request.prepared != null)
        trf("这个文件包含 %1\$s 个页面、共 %2\$s 项设置。是否自定义导入项？\n选择“全部导入”会导入文件里的所有设置；自定义可以只导入需要的页面。", pages.size, total)
    else
        trf("是否自定义%1\$s项？\n选择“全部%2\$s”会带上所有页面的设置；自定义可以只%3\$s需要的页面。", action, action, action)
    C17OverlayDialog(show = true, title = trf("%s配置", action), summary = summary, onDismissRequest = app::cancelConfigScope) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                C17TextButton(text = "自定义", onClick = app::customizeConfigScope, modifier = Modifier.weight(1f))
                C17TextButton(text = trf("全部%s", action), onClick = app::confirmConfigScopeAll, modifier = Modifier.weight(1f),
                    primary = true)
            }
            C17TextButton(text = "取消", onClick = app::cancelConfigScope, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ConfigScopeChecklist(app: ModernMainActivity, request: ConfigScopeRequest) {
    val action = verb(request.kind)
    val pages = choices(request)
    val chosen = pages.count { it.first.id in request.selected }
    val items = pages.filter { it.first.id in request.selected }.sumOf { it.second }
    C17OverlayDialog(show = true, title = trf("选择%s项", action), summary = trf("已选 %1\$s / %2\$s 个页面，共 %3\$s 项设置", chosen, pages.size, items),
        onDismissRequest = app::cancelConfigScope) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                C17TextButton(text = "全选", onClick = { app.selectAllConfigScope(true) }, modifier = Modifier.weight(1f))
                C17TextButton(text = "全不选", onClick = { app.selectAllConfigScope(false) }, modifier = Modifier.weight(1f))
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                pages.forEach { (section, count) ->
                    C17SwitchPreference(title = section.label(), summary = trf("%s 项", count), checked = section.id in request.selected,
                        onCheckedChange = { app.toggleConfigScope(section.id, it) })
                }
                if (request.prepared == null) Text(trf("字体、图标库等文件不会随配置%s，只带上它们的设置。", action), Modifier.padding(top = 8.dp),
                    fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                C17TextButton(text = "返回", onClick = app::backConfigScope, modifier = Modifier.weight(1f))
                C17TextButton(text = action, enabled = chosen > 0, onClick = app::confirmConfigScopeChoice, modifier = Modifier.weight(1f),
                    primary = true)
            }
        }
    }
}
