// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Good = Color(0xFF28AE83)
private val Caution = Color(0xFFCE9846)
private val Info = Color(0xFF5487E6)

private fun tone(severity: HookHealth.Severity, error: Color): Color = when (severity) {
    HookHealth.Severity.ERROR -> error
    HookHealth.Severity.WARN -> Caution
    HookHealth.Severity.INFO -> Info
    HookHealth.Severity.OK -> Good
}

private fun bytes(value: Long): String = when {
    value < 1024L -> "$value B"
    value < 1024L * 1024L -> String.format(Locale.ROOT, "%.1f KB", value / 1024.0)
    else -> String.format(Locale.ROOT, "%.2f MB", value / 1024.0 / 1024.0)
}

private fun span(first: Long, last: Long): String {
    val minutes = ((last - first) / 60000L).coerceAtLeast(0L)
    return when {
        minutes < 1L -> "不到 1 分钟"
        minutes < 60L -> trf("共 %s 分钟", minutes)
        minutes < 24L * 60L -> trf("共 %1\$s 小时 %2\$s 分", minutes / 60, minutes % 60)
        else -> trf("共 %1\$s 天 %2\$s 小时", minutes / (24 * 60), minutes % (24 * 60) / 60)
    }
}

/** 关于 → 问题诊断. Two sections the reader chooses between: what the log holds and what to do with it, and the log itself. */
@Composable
fun DiagnosticsPage(app: ModernMainActivity) {
    val result = app.diagnostics
    val stats = app.diagnosticsStats
    val hooks = app.diagnosticsHooks
    val errorColor = MiuixTheme.colorScheme.error
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var level by rememberSaveable { mutableIntStateOf(0) }
    var hooksOpen by rememberSaveable { mutableStateOf(false) }
    var expanded by remember { mutableStateOf<Int?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val scroll = rememberC17ScrollBehavior(rememberC17TopAppBarState(initialHeightOffsetLimit = 0f))
    val list = rememberLazyListState()
    val clock = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.ROOT) }
    val short = remember { SimpleDateFormat("MM-dd HH:mm", Locale.ROOT) }
    val filtered = remember(result, level, query) {
        (result?.entries ?: emptyList()).asReversed().filter { entry ->
            (level == 0 || (level == 1 && entry.level != "info") || (level == 2 && entry.level == "error")) &&
                (query.isBlank() || entry.message.contains(query, true) || entry.sourceLabel.contains(query, true) || entry.detail.contains(query, true))
        }.take(150)
    }
    C17Scaffold(containerColor = MiuixTheme.colorScheme.surface, topBar = {
        C17SmallTopAppBar(title = "问题诊断", scrollBehavior = scroll, navigationIcon = {
            C17IconButton(onClick = app::closeDiagnostics) { Icon(Icons.Outlined.ArrowBack, "返回") }
        })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()).nestedScroll(scroll.nestedScrollConnection),
            state = list, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = padding.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "summary") { Summary(result, app.diagnosticsLoading, errorColor) }
            item(key = "tabs") { UiContentTabs(listOf("日志导出", "日志记录"), tab, { tab = it }) }
            if (tab == 0) {
                item(key = "overview") {
                    UiGroupCard {
                        UiRow("时间范围", if (stats == null || stats.first == 0L) "还没有记录" else span(stats.first, stats.last),
                            if (stats == null || stats.first == 0L) "—" else "${short.format(Date(stats.first))} → ${short.format(Date(stats.last))}")
                        UiRow("内容", "事件按条、日志文件按行", if (stats == null) "—" else trf("%1\$s 条 · %2\$s 行", stats.events, stats.lines))
                        UiRow("文件大小", if (stats == null) "" else trf("导出的报告约 %s", bytes(stats.exportBytes)), if (stats == null) "—" else bytes(stats.fileBytes))
                        val worry = result?.findings?.filter { it.severity == HookHealth.Severity.ERROR || it.severity == HookHealth.Severity.WARN } ?: emptyList()
                        UiRow("可能的问题", if (result == null) "正在分析…" else if (worry.isEmpty()) "没有发现明显问题" else worry.take(3).joinToString("、") { it.title },
                            if (result == null) "—" else trf("%s 项", worry.size))
                    }
                }
                if (result != null) {
                    item { SectionLabel("诊断结论") }
                    items(result.findings, key = { it.id }) { finding -> Finding(finding, errorColor) }
                }
                item { SectionLabel("挂钩状态") }
                item(key = "hooks") {
                    UiGroupCard {
                        val rows = HookHealth.table(hooks)
                        val bad = rows.count { it[2] == "未就绪" || it[2] == "失败" }
                        UiRow("系统界面里的挂钩", if (hooks == null) "还没有拿到系统界面的回应，点下方刷新" else trf("共 %1\$s 组，%2\$s 组未就绪", rows.size, bad),
                            if (hooksOpen) "收起" else "展开") { hooksOpen = !hooksOpen }
                        if (hooksOpen) rows.forEach { row ->
                            val color = when (row[2]) { "未就绪", "失败" -> errorColor; "按需", "未启用" -> MiuixTheme.colorScheme.onSurfaceVariantSummary; else -> Good }
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                                Text(row[1], Modifier.weight(1f).padding(start = 10.dp), fontSize = 14.sp)
                                Text(row[3], fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                Text(row[2], Modifier.padding(start = 10.dp), fontSize = 13.sp, color = color)
                            }
                        }
                    }
                }
                item { SectionLabel("操作") }
                item(key = "actions") {
                    UiGroupCard {
                        UiSwitchRow("诊断日志", "复现问题前先开启；挂钩状态不依赖日志", app.bool(StatusBarSettings.DIAGNOSTICS_ENABLED), app.canEdit) {
                            app.save(StatusBarSettings.DIAGNOSTICS_ENABLED, it)
                        }
                        UiRow("刷新", "重新读取日志并询问系统界面", icon = Icons.Outlined.Refresh) { app.openDiagnostics() }
                        UiRow("导出报告", "保存为可读的文本，可先看一眼", icon = Icons.Outlined.FileUpload) { app.chooseLog() }
                        UiRow("分享报告", icon = Icons.Outlined.Share) { app.shareLog() }
                        UiRow("清空日志", "只删除日志，不影响配置", icon = Icons.Outlined.DeleteOutline, enabled = !app.busy) { app.clearLogHistory() }
                    }
                }
            } else {
                item(key = "log-controls") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        UiContentTabs(listOf("全部", "警告及以上", "仅错误"), level, { level = it })
                        C17TextField(value = query, onValueChange = { query = it }, label = "搜索消息、来源或异常", singleLine = true,
                            useLabelAsPlaceholder = true, modifier = Modifier.fillMaxWidth())
                    }
                }
                if (result == null || filtered.isEmpty()) item {
                    Text(if (result == null) "正在读取…" else if (query.isNotBlank() || level != 0) "没有符合条件的记录" else "还没有日志。开启诊断日志并复现问题后再来看。",
                        Modifier.padding(horizontal = 4.dp, vertical = 8.dp), fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
                items(filtered.size, key = { index -> "e${filtered[index].time}-$index" }) { index ->
                    val entry = filtered[index]
                    val color = when (entry.level) { "error" -> errorColor; "warn" -> Caution; else -> MiuixTheme.colorScheme.onSurfaceVariantSummary }
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MiuixTheme.colorScheme.onSurface.copy(alpha = .045f))
                        .clickable(enabled = entry.detail.isNotEmpty()) { expanded = if (expanded == index) null else index }
                        .padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(entry.levelLabel(), fontSize = 11.sp, fontWeight = FontWeight.Medium, color = color)
                            Text(entry.sourceLabel, fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            Text(clock.format(Date(entry.time)), Modifier.weight(1f), fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                        Text(entry.message.ifEmpty { "（无消息）" }, fontSize = 14.sp, maxLines = if (expanded == index) 12 else 2, overflow = TextOverflow.Ellipsis)
                        if (expanded == index && entry.detail.isNotEmpty())
                            Text(entry.detail, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                }
                if (result != null && result.entries.size >= DiagnosticCenter.MAX_ENTRIES) item {
                    Text(trf("只显示最近 %s 条；完整记录在导出的报告里。", filtered.size), Modifier.padding(horizontal = 4.dp), fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(title, Modifier.padding(start = 4.dp, top = 6.dp), fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
}

@Composable
private fun Summary(result: DiagnosticCenter.Result?, loading: Boolean, errorColor: Color) {
    val worst = result?.findings?.firstOrNull { it.severity != HookHealth.Severity.OK && it.severity != HookHealth.Severity.INFO }
    val color = when {
        result == null -> Info
        worst != null -> tone(worst.severity, errorColor)
        else -> Good
    }
    val title = when {
        result == null -> if (loading) "正在检查…" else "尚未检查"
        worst == null -> "没有发现问题"
        else -> worst.title
    }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(color.copy(alpha = .14f)).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(14.dp).clip(CircleShape).background(color))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(if (result == null) "读取日志并询问系统界面" else trf("%1\$s 条错误 · %2\$s 条警告 · %3\$s 条记录", result.errors, result.warnings, result.entries.size),
                fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}

@Composable
private fun Finding(finding: HookHealth.Finding, errorColor: Color) {
    val color = tone(finding.severity, errorColor)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MiuixTheme.colorScheme.onSurface.copy(alpha = .045f)).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.padding(top = 5.dp).size(9.dp).clip(CircleShape).background(color))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(finding.title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (finding.detail.isNotEmpty()) Text(finding.detail, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}
