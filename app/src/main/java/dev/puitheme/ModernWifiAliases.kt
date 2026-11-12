// SPDX-License-Identifier: GPL-3.0-only
package dev.puitheme

import androidx.compose.runtime.*

/** Uses the normal app overlay/editing state; only the display-name map is saved. */
@Composable
fun WifiAliasRulesDialog(app: ModernMainActivity) {
    val json = app.value(QsWifiAlias.RULES)?.toString() ?: "{}"
    val rules = remember(json) { runCatching { QsWifiAlias.parse(json) }.getOrDefault(emptyMap()) }
    val networks = remember(app, json) { (QsWifiAliasCatalog.read(app) + rules.keys).distinct().sorted() }
    val current = remember(app) { QsWifiAliasCatalog.current(app) }
    var selected by remember { mutableStateOf<String?>(null) }
    val network = selected
    if (network == null) {
        if (networks.isEmpty()) {
            UiConfirmDialog("尚未识别到 WiFi", "系统界面会提供系统允许读取的已保存网络和实际连接过的网络。请连接 WiFi 后重新打开此页。无法列出已删除且模块从未识别过的历史。", "知道了",
                onDismiss = { app.editing = null }) { app.editing = null }
        } else UiChoiceDialog("选择 WiFi", networks,
            networks.map { name -> name + if (name == current) " · 上次识别的连接" else "" }, current,
            description = "已保存或模块已识别的网络。同名 WiFi 的不同接入点共用一个别名；只改控制中心显示，不修改网络配置。",
            onDismiss = { if (selected == null) app.editing = null }, onChoice = { selected = it })
    } else UiInputDialog("WiFi 显示名称", rules[network] ?: "",
        trf("原名：%s\n最多 32 个字符；清空后恢复这个网络的系统原名。", network),
        onDismiss = { selected = null }) { alias ->
        val error = QsWifiAlias.aliasError(alias)
        if (error != null) error else {
            val updated = runCatching { QsWifiAlias.withAlias(json, network, alias) }
            if (updated.isFailure) updated.exceptionOrNull()?.message ?: "无法保存这个名称"
            else if (app.save(QsWifiAlias.RULES, updated.getOrThrow())) { app.editing = null; null }
            else "名称尚未保存，请重试"
        }
    }
}
