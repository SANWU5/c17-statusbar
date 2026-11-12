// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme

import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** The catalog and baseline are immutable; only this dialog's in-memory order is edited. */
data class CustomPluginSortDraft(
    val pluginId: String, val versionCode: Int, val key: String, val optionsAction: String,
    val title: String, val baseValue: String, val instanceId: String, val catalog: CustomPluginSort.Catalog,
)

@Composable
fun CustomPluginSortDialogs(model: CustomPluginController) {
    if (model.sortLoading) C17OverlayDialog(show = true, title = "读取排序项目", onDismissRequest = model::dismissSort) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(tr("正在读取插件当前可排序的项目…"), fontSize = 14.sp)
            C17TextButton("取消", model::dismissSort, Modifier.fillMaxWidth())
        }
    }
    model.sortDraft?.let { draft ->
        // Item names come from the plugin at run time; its own table translates the ones it ships.
        val text = remember(draft, C17Text.language()) { CustomPluginLocalization.forPlugin(model.plugins.firstOrNull { it.id == draft.pluginId }) }
        var order by remember(draft) { mutableStateOf(draft.catalog.items.map { it.value }) }
        var hidden by remember(draft) { mutableStateOf(draft.catalog.hidden.toSet()) }
        val entries = remember(draft) { draft.catalog.items.associateBy { it.value } }
        val grid = rememberLazyStaggeredGridState()
        val drag = rememberCustomPluginDragState(grid, order, !model.working) { order = it.toList() }
        fun move(id: String, delta: Int): Boolean {
            if (model.working || drag.isDragging) return false
            val position = order.indexOf(id); val target = position + delta
            if (position < 0 || target !in order.indices) return false
            order = order.toMutableList().apply { removeAt(position); add(target, id) }
            return true
        }
        C17OverlayDialog(show = true, title = text.tr(draft.title), onDismissRequest = { drag.cancel(); model.dismissSort() }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (draft.catalog.allowRemoval) "长按三条杠排序，移除后可恢复，保存后生效。" else "长按三条杠拖动调整顺序，完成后保存。", fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Box(Modifier.fillMaxWidth().height(360.dp).clipToBounds()) {
                    LazyVerticalStaggeredGrid(columns = StaggeredGridCells.Fixed(1), state = grid,
                        modifier = Modifier.fillMaxSize().customPluginDragContainer(drag), verticalItemSpacing = 8.dp,
                        contentPadding = PaddingValues(vertical = 4.dp)) {
                        items(order, key = { it }) { id ->
                            val outline = if (drag.hoverTargetId == id && drag.draggingId != id)
                                Modifier.border(2.dp, MiuixTheme.colorScheme.primary, RoundedCornerShape(18.dp)) else Modifier
                            SortItem(entries.getValue(id),
                                Modifier.customPluginDragItem(drag, id).then(outline),
                                Modifier.customPluginDragHandle(drag, id).semantics {
                                    contentDescription = trf("长按拖动 %s", text.tr(entries.getValue(id).title))
                                    if (!model.working) customActions = buildList {
                                        if (order.indexOf(id) > 0) add(CustomAccessibilityAction("向前移动") { move(id, -1) })
                                        if (order.indexOf(id) < order.lastIndex) add(CustomAccessibilityAction("向后移动") { move(id, 1) })
                                    }
                                }, removed = id in hidden, text = text, onToggle = if (draft.catalog.allowRemoval) ({
                                    if (!model.working && !drag.isDragging) hidden = if (id in hidden) hidden - id else hidden + id
                                }) else null)
                        }
                    }
                    CustomPluginDragOverlay(drag) { id -> SortItem(entries.getValue(id), text = text) }
                    if (order.isEmpty()) Text(tr("当前没有可排序项目"), Modifier.align(Alignment.Center), fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
                if (model.error.isNotBlank()) Text(model.error, fontSize = 13.sp, color = MiuixTheme.colorScheme.error)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    C17TextButton("取消", { drag.cancel(); model.dismissSort() }, Modifier.weight(1f), enabled = !model.sortSaving)
                    C17TextButton(if (model.sortSaving) "保存中" else "保存", { drag.cancel(); model.saveSort(draft, order, hidden) },
                        Modifier.weight(1f), enabled = !model.working && !drag.isDragging)
                }
            }
        }
    }
}

@Composable
private fun SortItem(item: CustomPluginSort.Item, modifier: Modifier = Modifier, handle: Modifier = Modifier,
    removed: Boolean = false, text: CustomPluginLocalization.Text = CustomPluginLocalization.Text { it }, onToggle: (() -> Unit)? = null) {
    val icon = remember(item.icon) { runCatching {
        if (item.icon.isEmpty()) null else {
            val bytes = android.util.Base64.decode(item.icon, android.util.Base64.NO_WRAP)
            val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            if (options.outWidth !in 1..96 || options.outHeight !in 1..96) null
            else android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }.getOrNull() }
    C17Card(modifier = modifier.fillMaxWidth(), cornerRadius = 18.dp, insideMargin = PaddingValues(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) Image(icon, null, Modifier.padding(end = 12.dp).size(28.dp), colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurface))
            Text(if (removed) text.tr(item.title) + tr("（已移除）") else text.tr(item.title), Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (onToggle != null) C17TextButton(if (removed) "恢复" else "移除", onToggle)
            Box(Modifier.size(44.dp).then(handle), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Menu, null, Modifier.size(20.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        }
    }
}
