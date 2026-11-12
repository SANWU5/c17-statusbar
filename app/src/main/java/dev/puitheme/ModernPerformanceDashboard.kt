// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Keep the everyday view small; records, instructions and module management stay in details. */
@Composable
internal fun PerformanceDashboard(app: ModernMainActivity, installation: String, result: String) {
    val module = app.performanceReport
    val present = module?.installed == true || module?.pending == true
    val reliable = module != null && module.root && !app.performanceError
    val editable = app.canEdit && !app.busy && !app.performanceBusy && reliable
    val hasRecord = module != null && module.lastState != "unknown"
    val fresh = hasRecord && reliable && module!!.currentBoot &&
        module.recordVersionCode == module.moduleVersionCode &&
        module.recordVersionCode >= RootPerformanceModule.VERSION_CODE
    val activeTask = present && fresh && module!!.lastState in listOf("waiting", "running")
    val palette = homeCardPalette(installation, LocalC17Dark.current)
    var expanded by rememberSaveable { mutableStateOf(false) }
    var diagnosticsExpanded by rememberSaveable { mutableStateOf(false) }
    val subtitle = when {
        app.performanceError -> "读取失败，请刷新"
        module == null || !module.root -> "需要 Root 授权"
        module.remove -> "重启后卸载"
        module.pending -> "重启后生效"
        module.outdated -> "有可用更新"
        !present -> "系统优化 · 常驻缓存"
        module.disabled -> "模块已停用"
        !module.lockAvailable -> "任务暂不可用，查看详情"
        !fresh && hasRecord -> "上次记录 · 当前未确认"
        module.lastState == "waiting" -> if (module.trigger == "run") "准备中" else "等待空闲"
        module.lastState == "running" -> "后台处理中"
        module.errorMessage().isNotEmpty() -> "处理未完成，查看详情"
        module.lastState == "current" -> "已复用有效结果"
        module.lastState == "ok" -> "本次核验完成"
        else -> "按需自动处理"
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        C17Card(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)), cornerRadius = 22.dp,
            colors = CardDefaults.defaultColors(color = palette.base)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(result, style = C17CardTypography.title.copy(fontSize = 22.sp, lineHeight = 28.sp), color = palette.foreground)
                Text(subtitle, style = C17CardTypography.supporting, color = palette.secondary)
            }
        }
        UiGroupCard {
            UiSwitchRow("系统UI防炸", "触发将清空 SystemUI 数据",
                checked = module?.rescueEnabled == true,
                enabled = editable && module?.rescueAvailable == true && module.installed && !module.pending && !module.remove,
                icon = Icons.Outlined.Restore, onChecked = app::setPerformanceRecovery)
            UiRow("常驻缓存", value = when {
                !reliable -> "未确认"
                !present -> "未安装"
                module!!.cacheRunning -> "运行中"
                module.remove || module.disabled -> "已停用"
                module.pending -> "待重启"
                else -> "未连接"
            }, icon = Icons.Outlined.Memory)
        }
        // Keep the existing action gates; compact labels never imply an unconfirmed result is current.
        val primary = when {
            !reliable -> "刷新状态"
            module!!.remove -> "待重启卸载"
            module.outdated -> "更新模块"
            !present -> "安装模块"
            !module.lockAvailable -> "暂不可用"
            module.disabled -> "启用模块"
            activeTask -> if (module.lastState == "running") "优化中" else "等待空闲"
            else -> "立即优化"
        }
        C17TextButton(text = primary, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
            enabled = if (!reliable) !app.performanceBusy && !app.busy else editable && module?.remove != true &&
                (!present || module?.outdated == true || module?.lockAvailable == true) &&
                (!activeTask || module?.outdated == true || module?.disabled == true), onClick = {
                when {
                    !reliable -> app.refreshPerformance()
                    module!!.outdated || !present -> app.installPerformance()
                    module.disabled -> app.enablePerformance()
                    else -> app.runPerformance()
                }
            })
        UiGroupCard {
            UiRow("详情", value = if (expanded) "收起" else "展开", icon = Icons.Outlined.Info) { expanded = !expanded }
            if (expanded) {
                if (module != null) {
                    UiRow("模块", "${module.manager} · ${if (module.moduleVersion.isBlank()) "版本未确认" else "v${module.moduleVersion}"}", installation)
                }
                UiRow("自动优化", "息屏或闲置 10 分钟 · 电量 > 15% · 电池 < 55℃")
                UiRow("防炸作用", "系统界面卡死、反复崩溃时，清空它的数据，恢复正常开机")
                UiRow("防炸用法", "开机动画期间，5 秒内连按 5 次音量上键")
                if (module != null) {
                    UiRow("防炸记录", if (!reliable) "当前未确认" else module.rescueMessage())
                    if (module.rescueAvailable && module.rescueTime > 0L)
                        UiRow("最近恢复", java.text.DateFormat.getDateTimeInstance().format(java.util.Date(module.rescueTime * 1000L)))
                    if (hasRecord) {
                        val time = if (module.time == 0L) "无时间记录" else java.text.DateFormat.getDateTimeInstance().format(java.util.Date(module.time * 1000L))
                        val origin = when (module.trigger) { "boot" -> "开机自动"; "auto" -> "自动"; "run" -> "手动"; "uninstall" -> "卸载恢复"; else -> "未记录" }
                        UiRow("最近优化", "$origin · $time", if (fresh) "本次开机" else "历史记录")
                        UiRow("核验结果", if (fresh) module.verificationMessage() else "当前未确认",
                            if (module.packages > 0L && module.recordVersionCode >= 3L) "${module.verified}/${module.packages}" else "—")
                    }
                    if (module.errorMessage().isNotEmpty()) UiRow("处理提示", module.errorMessage())
                    // Costs and estimates remain labelled; no unmeasured speed-up percentage is shown.
                    if (module.ledgerSince > 0L || module.runtimeSampled > 0L) {
                        val benefit = remember(module) { PerformanceBenefit.of(module) }
                        UiRow("累计编译", benefit.compilesNote, benefit.compiles)
                        UiRow("复用结果", benefit.savedNote, benefit.saved)
                        UiRow("系统界面", benefit.aotNote, benefit.aot)
                        UiRow("即时编译", benefit.jitNote, benefit.jit)
                    }
                }
                UiRow("诊断", value = if (diagnosticsExpanded) "收起" else "展开", icon = Icons.Outlined.Article) {
                    diagnosticsExpanded = !diagnosticsExpanded
                }
                if (diagnosticsExpanded) {
                    if (module != null) {
                        UiRow("缓存详情", module.cacheMessage())
                        UiRow("最近执行", module.executionLog.ifEmpty { "暂无记录" })
                        if (module.failureLog.isNotEmpty()) UiRow("历史失败（请核对时间）", module.failureLog)
                        if (module.packages > 0L) UiRow("处理统计", trf("编译 %1\$s · 复用 %2\$s · 延期 %3\$s", module.compiled, module.reused, module.deferred))
                        UiRow("任务优先级", when (module.priority) { "background" -> "系统后台"; "compatibility" -> "兼容方式"; else -> "未记录" })
                    }
                    Text(app.performanceDetail.ifEmpty { app.performanceMessage }, Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        style = C17CardTypography.supporting, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
                UiRow("刷新状态", icon = Icons.Outlined.Refresh, enabled = !app.performanceBusy && !app.busy) { app.refreshPerformance() }
                UiRow("WebUI", icon = Icons.Outlined.Dashboard) {
                    app.startActivity(android.content.Intent(app, PerformanceWebActivity::class.java))
                }
                if (present && module?.remove != true) {
                    if (module?.disabled != true) UiRow("停用模块", icon = Icons.Outlined.Pause,
                        enabled = editable) { app.disablePerformance() }
                    UiRow("卸载模块", icon = Icons.Outlined.DeleteOutline,
                        enabled = editable) { app.removePerformance() }
                }
            }
        }
    }
}
