// SPDX-License-Identifier: GPL-3.0-only
package dev.puitheme

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun PluginApplicationPicker(app: ModernMainActivity, title: String, selected: String, enabled: Boolean,
    onDismiss: () -> Unit, onChoose: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val entries by produceState<List<ApplicationChoice>?>(null, app) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val pm = app.packageManager
                pm.getInstalledApplications(0).mapNotNull { info ->
                    if (pm.getLaunchIntentForPackage(info.packageName) == null) null
                    else ApplicationChoice(info.packageName, runCatching { info.loadLabel(pm).toString() }.getOrDefault(info.packageName), false)
                }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
            }.getOrDefault(emptyList())
        }
    }
    val choices = remember(entries, query) {
        val text = query.trim()
        entries.orEmpty().filter { text.isEmpty() || it.label.contains(text, true) || it.packageName.contains(text, true) }
    }
    C17OverlayDialog(show = true, title = title, onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            C17TextField(value = query, onValueChange = { query = it }, label = "搜索应用名称或包名", singleLine = true,
                useLabelAsPlaceholder = true, modifier = Modifier.fillMaxWidth(), insideMargin = DpSize(12.dp, 10.dp))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                items(choices, key = { it.packageName }) { choice ->
                    C17ArrowPreference(title = (if (choice.packageName == selected) "✓ " else "") + choice.label,
                        summary = choice.packageName, enabled = enabled,
                        startAction = { Box(Modifier.padding(end = 12.dp)) { AppIcon(app, choice.packageName) } },
                        onClick = { onChoose(choice.packageName) })
                }
            }
            if (choices.isEmpty()) Text(if (entries == null) "正在读取手机应用…" else "没有找到可打开的应用", fontSize = 13.sp)
            C17TextButton("取消", onDismiss, Modifier.fillMaxWidth())
        }
    }
}
