// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.items as staggeredItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import kotlinx.coroutines.flow.first
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val pluginSortLabels = linkedMapOf(
    "custom" to "自定义顺序", "name" to "名称", "installed" to "导入时间",
    "updated" to "更新时间", "version" to "版本", "enabled" to "启用状态",
)

@Composable
fun CustomPluginsPage(app: ModernMainActivity) {
    val model = app.customPlugins
    val plugin = model.selected
    val scroll = rememberC17ScrollBehavior(rememberC17TopAppBarState(initialHeightOffsetLimit = 0f))
    C17Scaffold(containerColor = MiuixTheme.colorScheme.surface, topBar = {
        C17SmallTopAppBar(title = model.settingsPath.lastOrNull() ?: plugin?.name ?: "扩展插件", scrollBehavior = scroll, navigationIcon = {
            C17IconButton(onClick = { if (!model.back()) app.closeCustomPlugins() }) { Icon(Icons.Outlined.ArrowBack, "返回") }
        }, actions = {
            C17IconButton(onClick = { if (model.tab == 1 && plugin == null) model.refreshCatalog() else model.refreshPage() }, enabled = !model.working) {
                Icon(Icons.Outlined.Refresh, if (model.tab == 1 && plugin == null) "刷新插件仓库" else "刷新已安装插件")
            }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()).nestedScroll(scroll.nestedScrollConnection)) {
            if (plugin == null) UiContentTabs(
                tabs = listOf("插件", "导入"), selectedTabIndex = model.tab, onTabSelected = model::selectTab,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            when {
                plugin != null -> PluginDetail(app, plugin, padding.calculateBottomPadding())
                model.tab == 0 -> InstalledPlugins(app, padding.calculateBottomPadding())
                else -> ImportPlugins(app, padding.calculateBottomPadding())
            }
        }
    }
}

@Composable
private fun PluginNotices(app: ModernMainActivity) {
    val model = app.customPlugins
    if (!app.runtimeActive) UiGroupCard {
        UiRow("等待当前版本生效", "在主页重启系统界面后，扩展插件才能应用系统修改。", "重启", Icons.Outlined.RestartAlt,
            enabled = !app.busy && !model.working) { app.restartSystemUi() }
    }
    if (model.error.isNotBlank()) UiGroupCard {
        UiRow("操作未完成", model.error, "收起", Icons.Outlined.ErrorOutline) { model.dismissError() }
    }
}

@Composable
private fun InstalledPlugins(app: ModernMainActivity, bottom: Dp) {
    val model = app.customPlugins
    val entries = model.sortedPlugins
    val grid = rememberLazyStaggeredGridState()
    val drag = rememberCustomPluginDragState(grid, entries.map { it.id }, !model.working && !model.hasDialog, model::commitPluginOrder)
    Box(Modifier.fillMaxSize().clipToBounds()) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(150.dp), state = grid,
            modifier = Modifier.fillMaxSize().customPluginDragContainer(drag),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottom + 24.dp),
            verticalItemSpacing = 10.dp, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!app.runtimeActive || model.error.isNotBlank()) item(key = "notices", span = StaggeredGridItemSpan.FullLine) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { PluginNotices(app) }
            }
            item(key = "sorting", span = StaggeredGridItemSpan.FullLine) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(start = 4.dp)) {
                        Text(trf("%1\$s 个插件 · %2\$s 个启用", entries.size, entries.count { it.enabled }), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(if (drag.isDragging) "拖到目标后松手" else trf("%s · 长按三条杠排序", pluginSortLabels[model.sortMode]),
                            fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                    C17IconButton(onClick = { drag.cancel(); model.sortOpen = true }, enabled = !model.working) {
                        Icon(Icons.Outlined.Sort, "选择排序方式")
                    }
                    C17TextButton(if (model.sortAscending) "升序" else "降序", { drag.cancel(); model.toggleSortDirection() }, enabled = !model.working)
                }
            }
            if (entries.isEmpty()) item(key = "empty", span = StaggeredGridItemSpan.FullLine) {
                UiGroupCard { UiRow("还没有扩展插件", "从文件或仓库导入后，在这里启用与设置。", "去导入", Icons.Outlined.Extension) { model.selectTab(1) } }
            }
            staggeredItems(entries, key = { it.id }) { entry ->
                val target = drag.hoverTargetId == entry.id && drag.draggingId != entry.id
                val outline = if (target) Modifier.border(2.dp, MiuixTheme.colorScheme.primary, RoundedCornerShape(18.dp)) else Modifier
                InstalledPluginCard(app, entry,
                    modifier = Modifier.customPluginDragItem(drag, entry.id).then(outline),
                    handleModifier = Modifier.customPluginDragHandle(drag, entry.id), canOpen = { !drag.isDragging })
            }
            if (entries.any { it.enabled }) item(key = "disable-all", span = StaggeredGridItemSpan.FullLine) {
                C17TextButton("停用全部插件", { drag.cancel(); model.disableAll() }, Modifier.fillMaxWidth(), enabled = !model.working)
            }
        }
        CustomPluginDragOverlay(drag) { id ->
            entries.firstOrNull { it.id == id }?.let { InstalledPluginCard(app, it, ghost = true) }
        }
    }
}

@Composable
private fun InstalledPluginCard(
    app: ModernMainActivity, entry: CustomPluginRepository.Plugin,
    modifier: Modifier = Modifier, handleModifier: Modifier = Modifier, ghost: Boolean = false,
    canOpen: () -> Boolean = { true },
) {
    val model = app.customPlugins
    val text = remember(entry.runtimeBaseDir, C17Text.language()) { CustomPluginLocalization.forPlugin(entry) }
    val name = text.tr(entry.name)
    val status = when {
        entry.error.isNotBlank() -> "已隔离"
        app.bool(StatusBarSettings.SAFE_MODE) && entry.enabled -> "安全模式暂停"
        entry.enabled -> "已启用"
        else -> "已停用"
    }
    C17Card(modifier = modifier.fillMaxWidth(), cornerRadius = 18.dp, insideMargin = PaddingValues(12.dp),
        onClick = if (ghost) null else ({ if (canOpen()) model.selectedId = entry.id })) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(name, Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Box(Modifier.size(40.dp).then(if (ghost) Modifier else handleModifier).semantics {
                contentDescription = trf("长按拖动 %s", name)
                if (!ghost && !model.working) customActions = listOf(
                    CustomAccessibilityAction("向前移动") { model.movePlugin(entry.id, -1); true },
                    CustomAccessibilityAction("向后移动") { model.movePlugin(entry.id, 1); true },
                )
            }, contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Menu, null, Modifier.size(20.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        }
        if (entry.description.isNotBlank()) Text(text.tr(entry.description), Modifier.padding(top = 2.dp), fontSize = 12.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr(status) + " · " + entry.version, Modifier.weight(1f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (entry.error.isNotBlank()) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurfaceVariantSummary)
            C17Switch(entry.enabled, { model.setEnabled(entry, it) },
                Modifier.semantics { contentDescription = trf("启用 %s", name); stateDescription = tr(status) },
                enabled = !ghost && !model.working && (entry.enabled || app.canEdit))
        }
    }
}

@Composable
private fun ImportPlugins(app: ModernMainActivity, bottom: Dp) {
    val model = app.customPlugins
    val selectedSource = model.sources.firstOrNull { it.id == model.selectedSourceId }
    val catalog = model.catalog
    LazyColumn(Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottom + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!app.runtimeActive || model.error.isNotBlank()) item(key = "notices") { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { PluginNotices(app) } }
        item(key = "local-import") {
            C17Card(insideMargin = PaddingValues(20.dp)) {
                Icon(Icons.Outlined.FileUpload, null, Modifier.size(30.dp), tint = MiuixTheme.colorScheme.primary)
                Text(tr("从文件导入"), Modifier.padding(top = 12.dp), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(tr("选择 .js 文件或 ZIP 插件包。查看内容和来源后，再确认导入。"), Modifier.padding(top = 8.dp), fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                C17TextButton(if (model.working) "处理中" else "选择文件", model::chooseFile,
                    Modifier.fillMaxWidth().padding(top = 16.dp), enabled = !model.working)
                Text(tr("导入后默认停用，可在“插件”页检查并启用。"), Modifier.padding(top = 8.dp), fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        }
        item(key = "sources") {
            C17Card(insideMargin = PaddingValues(16.dp)) {
                Text(tr("插件仓库"), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(tr("选择来源，浏览并获取作者发布的插件。"), Modifier.padding(top = 6.dp, bottom = 8.dp), fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                model.sources.forEach { source ->
                    val selected = source.id == model.selectedSourceId
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable(enabled = !model.working) { model.selectSource(source.id) }
                            .padding(vertical = 12.dp).semantics { stateDescription = if (selected) "已选择" else "未选择" }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null,
                                    Modifier.padding(end = 8.dp).size(20.dp), tint = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                Text(source.name, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, fontSize = 15.sp)
                            }
                            Text("${source.owner}/${source.repo} · ${source.branch}", Modifier.padding(start = 28.dp, top = 4.dp),
                                fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            if (model.isDefaultSource(source)) Text(tr("项目仓库"), Modifier.padding(start = 28.dp, top = 2.dp), fontSize = 11.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                        if (!model.isDefaultSource(source)) C17IconButton(onClick = { model.removeSource(source.id) }, enabled = !model.working) {
                            Icon(Icons.Outlined.DeleteOutline, trf("移除仓库 %s", source.name))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    C17TextButton("添加仓库", { model.repositoryInputOpen = true }, Modifier.weight(1f), enabled = !model.working)
                    C17TextButton("打开仓库", model::openRepositoryHome, Modifier.weight(1f), enabled = !model.working && selectedSource != null)
                }
            }
        }
        item(key = "catalog-heading") {
            Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(catalog?.name?.ifBlank { "仓库插件" } ?: "仓库插件", Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    C17IconButton(onClick = model::refreshCatalog, enabled = !model.working && !model.catalogLoading && selectedSource != null) {
                        Icon(Icons.Outlined.Refresh, "刷新当前仓库")
                    }
                }
                if (catalog != null) {
                    if (catalog.description.isNotBlank()) Text(catalog.description, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Text(trf("目录更新：%s", catalog.updatedAt.ifBlank { tr("未提供") }), fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        C17TextButton("仓库说明", model::openRepositoryReadme, Modifier.weight(1f))
                        C17TextButton("更新记录", model::openRepositoryChanges, Modifier.weight(1f))
                    }
                }
                Text(tr("仓库内容由发布者提供，收录与校验不代表安全认证。"), fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        }
        if (model.catalogError.isNotBlank()) item(key = "catalog-error") { UiGroupCard {
            UiRow("暂时无法读取仓库", model.catalogError, "重试", Icons.Outlined.CloudOff, enabled = !model.working) { model.refreshCatalog() }
        } }
        if (model.catalogEntries.isEmpty()) item(key = "catalog-empty") { UiGroupCard {
            UiRow(if (model.catalogLoading) "正在读取仓库" else if (catalog == null) "尚未读取目录" else "这个仓库暂无插件",
                if (model.catalogLoading) "可继续从本地导入文件，仓库在后台读取。" else if (catalog == null) "刷新仓库，或先从本地文件导入。" else "发布者更新目录后，可在这里刷新查看。",
                icon = Icons.Outlined.FolderOpen)
        } }
        items(model.catalogEntries, key = { "catalog-${it.id}" }) { entry -> CatalogPluginCard(app, entry) }
        item(key = "documentation") { UiGroupCard {
            UiRow("开发规范与文档", "了解插件能做什么，以及如何发布自己的插件", "查看", Icons.Outlined.Code,
                enabled = !model.working) { model.helpOpen = true }
            UiRow("使用规范与免责声明", if (model.policyAccepted) "已阅读并同意，可随时重新查看" else "首次导入前需阅读并同意", "查看", Icons.Outlined.Info,
                enabled = !model.working) { model.policyOpen = true }
        } }
    }
}

@Composable
private fun CatalogPluginCard(app: ModernMainActivity, entry: CustomPluginCatalog.Entry) {
    val model = app.customPlugins
    val installed = model.plugins.firstOrNull { it.id == entry.id }
    val update = installed != null && entry.versionCode > installed.versionCode
    val action = when { update -> "可更新"; installed != null -> "已安装"; else -> "获取" }
    C17Card(insideMargin = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(entry.name, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(entry.version, Modifier.padding(start = 10.dp).widthIn(max = 100.dp), fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        if (entry.description.isNotBlank()) Text(entry.description, Modifier.padding(top = 8.dp), fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(listOf(entry.author.ifBlank { "作者未提供" }, pluginSize(entry.size)).joinToString(" · "),
                Modifier.weight(1f).padding(end = 8.dp), fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            C17TextButton(action, { model.download(entry) }, enabled = !model.working && (installed == null || update))
        }
        if (update) Text(trf("当前 %1\$s → %2\$s", installed!!.version, entry.version), Modifier.padding(top = 4.dp), fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

/** The open plugin's own translations (its i18n/<language>.json); the identity elsewhere. See CustomPluginLocalization. */
private val LocalPluginText = androidx.compose.runtime.staticCompositionLocalOf { CustomPluginLocalization.Text { it } }

/** A plugin's display string in the app language: its own table first, then the shared Text and row lookups. */
@Composable
private fun pt(text: String): String = LocalPluginText.current.tr(text)

/** Lists joined for reading: the Chinese enumeration comma only where the language uses it. */
private fun listSeparator(): String = if (C17Text.source() || C17Text.language() == "zh-Hant" || C17Text.language() == "ja") "、" else ", "

@Composable
private fun PluginDetail(app: ModernMainActivity, plugin: CustomPluginRepository.Plugin, bottom: Dp) {
    val model = app.customPlugins
    val controls = plugin.descriptor.optJSONArray("ui")
    val editableCount = CustomPluginSchema.configurationCount(plugin.descriptor)
    val editable = !model.working && (!plugin.enabled || app.canEdit)
    LaunchedEffect(plugin.id, plugin.versionCode, plugin.enabled, app.bool(StatusBarSettings.SAFE_MODE)) {
        snapshotFlow { model.working }.first { !it }
        model.refreshFormStatus(plugin)
    }
    var scopeOpen by remember(plugin.id) { mutableStateOf(false) }
    val declaredScopes = CustomPluginSupport.scopes(plugin.descriptor)
    val executionProblem = CustomPluginSupport.executionProblem(plugin.descriptor)
    val settings = plugin.descriptor.optJSONObject("settings") ?: JSONObject()
    val affected = settings.keys().asSequence().toList()
    val all = (0 until (controls?.length() ?: 0)).mapNotNull { controls?.optJSONObject(it) }
    val relatedKeys = (affected + all.mapNotNull { it.optString("key").ifBlank { null } }).distinct()
    val relatedGroups = relatedKeys.mapNotNull { SettingsCatalog.item(it)?.groupId }.distinct().mapNotNull { SettingsCatalog.group(it) }
    val features = groupedControls(all.filter { it.optString("section", "features") != "management" })
    val management = groupedControls(all.filter { it.optString("section", "features") == "management" })
    val scopeMissing = model.frameworkScopes.known &&
        (0 until declaredScopes.length()).any { !model.frameworkScopes.packages.contains(declaredScopes.getString(it)) }
    val blocked = executionProblem.isNotBlank() || plugin.error.isNotBlank() || scopeMissing
    // The three documented sections keep their order; only one is shown at a time.
    var section by remember(plugin.id) { mutableIntStateOf(if (blocked) 0 else 1) }
    val pluginText = remember(plugin.runtimeBaseDir, C17Text.language()) { CustomPluginLocalization.forPlugin(plugin) }
    androidx.compose.runtime.CompositionLocalProvider(LocalPluginText provides pluginText) { Column(Modifier.fillMaxSize()) {
        UiContentTabs(tabs = listOf("说明", "设置", "管理"), selectedTabIndex = section, onTabSelected = { model.resetSettingsPath(); section = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
        key(plugin.id, section, model.settingsPath) { LazyColumn(Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = bottom + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!app.runtimeActive || model.error.isNotBlank()) item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { PluginNotices(app) }
            }
            when (section) {
                0 -> pluginInformation(this, model, plugin, declaredScopes, executionProblem, affected, settings, scopeOpen) { scopeOpen = !scopeOpen }
                1 -> pluginSettings(this, app, plugin, features, editableCount, editable)
                else -> pluginManagement(this, app, plugin, management, relatedGroups, editableCount, editable)
            }
        } }
    } }
}

private fun pluginInformation(list: LazyListScope, model: CustomPluginController, plugin: CustomPluginRepository.Plugin,
    declaredScopes: org.json.JSONArray, executionProblem: String, affected: List<String>, settings: JSONObject,
    scopeOpen: Boolean, toggleScope: () -> Unit) {
    list.item { UiGroupCard {
        UiRow("使用说明", pt(plugin.descriptor.optString("usage")).ifBlank { pt(plugin.description).ifBlank { "作者未提供使用说明" } }, icon = Icons.Outlined.Info)
        if (plugin.descriptor.has("minimumHost")) UiRow("宿主版本", value = trf("code%1\$s · 最低 %2\$s", CustomPluginSupport.versionCode(), plugin.descriptor.optInt("minimumHost")))
        if (declaredScopes.length() == 0) UiRow("作用域", value = "无需注入")
        if (executionProblem.isNotBlank()) UiRow("当前引擎不支持", executionProblem, icon = Icons.Outlined.ErrorOutline)
        if (plugin.error.isNotBlank()) UiRow("已隔离", plugin.error, icon = Icons.Outlined.ErrorOutline)
    } }
    if (declaredScopes.length() > 0) list.item { PluginScopeCard(model, plugin) }
    list.item { UiGroupCard {
        UiRow("作者", value = plugin.author.ifBlank { "未填写" })
        UiRow("版本", value = "${plugin.version} (${plugin.versionCode})")
        UiRow("更新时间", value = pluginDate(plugin.updatedAt))
        UiRow("更多资料", trf("%1\$s 项设置 · %2\$s 项系统修改", affected.size, plugin.descriptor.optJSONArray("hooks")?.length() ?: 0),
            if (scopeOpen) "收起" else "展开", Icons.Outlined.Extension, onClick = toggleScope)
        if (scopeOpen) {
            if (plugin.description.isNotBlank()) UiRow("简介", pt(plugin.description))
            UiRow("来源", plugin.origin)
            UiRow("插件标识", plugin.id)
            UiRow("导入时间", value = pluginDate(plugin.installedAt))
            if (plugin.descriptor.has("runtime")) UiRow("完整运行扩展", "可调用 Java/Android 与 Hook，实际修改由插件源码决定。")
            for (field in affected) {
                val value = settings.opt(field)
                UiRow(SettingsCatalog.item(field)?.title ?: field, value = when (value) { true -> "开启"; false -> "关闭"; else -> value?.toString().orEmpty() })
            }
            Text(capabilities(plugin.descriptor), Modifier.padding(horizontal = 16.dp, vertical = 10.dp), fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    } }
}

private fun pluginSettings(list: LazyListScope, app: ModernMainActivity, plugin: CustomPluginRepository.Plugin,
    groups: List<Pair<String, List<JSONObject>>>, editableCount: Int, editable: Boolean) {
    val model = app.customPlugins
    val executionProblem = CustomPluginSupport.executionProblem(plugin.descriptor)
    val controls = groups.flatMap { it.second }
    val hierarchical = controls.any { it.has("groupPath") }
    val path = if (hierarchical) model.settingsPath else emptyList()
    if (path.isEmpty()) list.item { UiGroupCard {
        UiSwitchRow("启用插件", if (app.bool(StatusBarSettings.SAFE_MODE) && plugin.enabled) "安全模式暂停中" else "启用后在开机或作用域重启时自动加载，无需打开主 App。",
            plugin.enabled, enabled = !model.working && (plugin.enabled || app.canEdit && executionProblem.isBlank())) { model.setEnabled(plugin, it) }
        if (plugin.descriptor.has("nativeSettings")) UiRow("更多设置", if (app.bool(StatusBarSettings.SAFE_MODE)) "安全模式下暂停" else "",
            "打开", Icons.Outlined.Tune, enabled = editable && !app.bool(StatusBarSettings.SAFE_MODE)) { model.openNativeSettings(plugin) }
    } }
    if (editableCount == 0 && !plugin.descriptor.has("nativeSettings"))
        list.item { UiGroupCard { UiRow("没有可调设置", "仍可停用或删除此插件", icon = Icons.Outlined.Info) } }
    if (hierarchical) {
        pluginSettingsMenu(list, app, plugin, controls, path, editable)
        return
    }
    pluginFlatGroups(list, app, plugin, groups, editable, true)
}

/** Legacy groups and explicit menus share the same controls in Settings and Management. */
private fun pluginFlatGroups(list: LazyListScope, app: ModernMainActivity, plugin: CustomPluginRepository.Plugin,
    groups: List<Pair<String, List<JSONObject>>>, editable: Boolean, wallpaper: Boolean = false) {
    val model = app.customPlugins
    for ((title, controls) in groups) {
        val collapsible = title.isNotBlank() && plugin.descriptor.optJSONArray("features")?.let { features ->
            (0 until features.length()).any { features.optString(it) == "ui.collapsible-groups" }
        } == true
        if (collapsible) list.item { PluginFoldedGroup(model, plugin, title, controls, editable) }
        else {
            if (title.isNotBlank()) list.item { PluginGroupTitle(title) }
            list.item { UiGroupCard {
                for (control in controls) key(control.optString("key"), control.optString("type")) { PluginControl(model, plugin, control, editable) }
            } }
        }
        if (wallpaper && controls.any { it.optString("key") == QsCardSettings.WALLPAPER_ENABLED }) list.item { ShadeWallpaperCard(app, QsCardSettings.WALLPAPER_SCENE) }
    }
}

/**
 * Paths are real menu levels; each level renders only its own controls and direct children. A level's own
 * controls (its switches) come first, its sub-menus below them.
 */
private fun pluginSettingsMenu(list: LazyListScope, app: ModernMainActivity, plugin: CustomPluginRepository.Plugin,
    controls: List<JSONObject>, path: List<String>, editable: Boolean, wallpaper: Boolean = true) {
    val model = app.customPlugins
    val entries = controls.map { it to CustomPluginSchema.groupPath(it) }
    val direct = entries.filter { it.second == path }.map { it.first }
    val children = entries.mapNotNull { (_, target) ->
        target.getOrNull(path.size)?.takeIf { target.take(path.size) == path }
    }.distinct()
    if (direct.isNotEmpty()) list.item(key = "controls:${path.joinToString("/")}") { UiGroupCard {
        for (control in direct) key(control.optString("key"), control.optString("type")) {
            PluginControl(model, plugin, control, editable)
        }
    } }
    if (children.isNotEmpty()) list.item(key = "menu:${path.joinToString("/")}") { UiGroupCard {
        for (child in children) {
            val next = path + child
            val summary = entries.mapNotNull { (_, target) ->
                target.getOrNull(next.size)?.takeIf { target.take(next.size) == next }
            }.distinct().take(3).map { pt(it) }.joinToString(listSeparator())
            UiRow(pt(child), summary, icon = Icons.Outlined.Tune, enabled = !model.working) { model.openSettingsGroup(next) }
        }
    } }
    if (wallpaper && direct.any { it.optString("key") == QsCardSettings.WALLPAPER_ENABLED })
        list.item { ShadeWallpaperCard(app, QsCardSettings.WALLPAPER_SCENE) }
}

@Composable
private fun PluginScopeCard(model: CustomPluginController, plugin: CustomPluginRepository.Plugin) {
    val scopes = CustomPluginSupport.scopes(plugin.descriptor)
    val request = model.scopeRequestStatus.takeIf { model.scopeRequestPluginId == plugin.id }
    var expanded by rememberSaveable(plugin.id) { mutableStateOf(false) }
    val granted = (0 until scopes.length()).count { model.frameworkScopes.packages.contains(scopes.optString(it)) }
    val requestingElsewhere = model.scopeRequestRunning && model.scopeRequestPluginId != plugin.id
    UiGroupCard {
        UiRow("一键申请作用域", if (requestingElsewhere) "另一插件正在等待框架确认；取消等待不会撤销已有授权。"
            else request?.message ?: "保留已勾选的应用，只申请缺少的作用域。",
            if (model.scopeRequestRunning) "取消等待" else "申请", Icons.Outlined.Security,
            enabled = !model.working) { if (model.scopeRequestRunning) model.cancelScopeRequest() else model.requestPluginScopes(plugin) }
        UiRow("一键重启作用域",
            if (model.scopeRestartPluginId == plugin.id) model.scopeRestartMessage
            else "需要 Root；重启已授权的相关应用，系统界面和桌面会短暂重新加载。",
            "重启", Icons.Outlined.RestartAlt,
            enabled = !model.working && !model.scopeRequestRunning) { model.restartPluginScopes(plugin) }
        UiRow("作用域详情", value = if (expanded) "收起" else if (model.frameworkScopes.known) "$granted/${scopes.length()}" else "查看",
            onClick = { expanded = !expanded })
        if (expanded) for (index in 0 until scopes.length()) {
            val target = scopes.getString(index)
            val name = when (target) {
                ModuleRuntimeStatus.SYSTEM_UI -> "系统界面"
                "com.android.launcher" -> "系统桌面"
                "com.coloros.smartsidebar" -> "智能侧边栏"
                "com.coloros.assistantscreen" -> "负一屏"
                "com.oplus.blur" -> "系统模糊服务"
                else -> target
            }
            val state = when {
                request?.notInstalled?.contains(target) == true -> "未安装"
                !model.frameworkScopes.known -> "待确认"
                model.frameworkScopes.packages.contains(target) -> "已勾选"
                else -> "未勾选"
            }
            UiRow(name, value = state)
        }
    }
}

private fun pluginManagement(list: LazyListScope, app: ModernMainActivity, plugin: CustomPluginRepository.Plugin,
    groups: List<Pair<String, List<JSONObject>>>, related: List<SettingsCatalog.Group>, editableCount: Int, editable: Boolean) {
    val model = app.customPlugins
    val controls = groups.flatMap { it.second }
    if (controls.any { it.has("groupPath") }) {
        pluginSettingsMenu(list, app, plugin, controls, model.settingsPath, editable, false)
        // Host maintenance belongs to the Management root, not every plugin sub-page.
        if (model.settingsPath.isNotEmpty()) return
    } else {
        pluginFlatGroups(list, app, plugin, groups, editable)
    }
    if (related.isNotEmpty()) {
        list.item { PluginGroupTitle("关联设置") }
        list.item { UiGroupCard {
            for (group in related) UiRow(group.title, value = "打开", icon = Icons.Outlined.Tune, enabled = !model.working) { app.openCustomPluginSettings(group.id) }
        } }
    }
    list.item { PluginGroupTitle("维护") }
    list.item { UiGroupCard {
        if (editableCount > 0) {
            UiRow("导出配置", "保存此插件的独立设置", "导出", Icons.Outlined.FileUpload, enabled = !model.working) { model.exportConfiguration(plugin) }
            UiRow("导入配置", "从 JSON 文件恢复此插件设置，保留启用状态", "导入", Icons.Outlined.FileDownload, enabled = editable) { model.chooseConfiguration(plugin) }
        }
        if (editableCount > 0) UiRow("恢复默认配置", "保留启用状态", "恢复", Icons.Outlined.Restore, enabled = editable) { model.resetConfiguration(plugin) }
        UiRow("从文件更新", "", "选择文件", Icons.Outlined.SystemUpdate, enabled = !model.working) { model.chooseFile() }
        UiRow("删除插件", "撤销修改并移除设置", "删除", Icons.Outlined.DeleteOutline, enabled = !model.working) { model.delete(plugin) }
    } }
}

@Composable
private fun PluginGroupTitle(title: String) {
    Text(pt(title), Modifier.padding(horizontal = 6.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
}

/** Opt-in groups keep large extension forms compact without changing any stored setting. */
@Composable
private fun PluginFoldedGroup(model: CustomPluginController, plugin: CustomPluginRepository.Plugin,
    title: String, controls: List<JSONObject>, editable: Boolean) {
    var expanded by rememberSaveable(plugin.id, title) { mutableStateOf(false) }
    UiGroupCard {
        UiRow(pt(title), value = if (expanded) "收起" else "展开", icon = Icons.Outlined.Tune) { expanded = !expanded }
        if (expanded) for (control in controls) key(control.optString("key"), control.optString("type")) {
            PluginControl(model, plugin, control, editable)
        }
    }
}

/** Consecutive controls sharing a group form one card; a later repeat starts a new card. */
private fun groupedControls(controls: List<JSONObject>): List<Pair<String, List<JSONObject>>> {
    val result = mutableListOf<Pair<String, MutableList<JSONObject>>>()
    for (control in controls) {
        val group = control.optString("group")
        if (result.isEmpty() || result.last().first != group) result.add(group to mutableListOf())
        result.last().second.add(control)
    }
    return result
}

@Composable
private fun PluginControl(model: CustomPluginController, plugin: CustomPluginRepository.Plugin, control: JSONObject, enabled: Boolean) {
    val type = control.getString("type")
    val title = pt(control.getString("title"))
    val enablers = control.optJSONArray("enableOnEdit")
    val controls = plugin.descriptor.optJSONArray("ui")
    val missingGates = (0 until (enablers?.length() ?: 0)).mapNotNull { index ->
        val gate = enablers!!.optString(index)
        if (plugin.state.optBoolean(gate)) null else (0 until (controls?.length() ?: 0))
            .mapNotNull { controls?.optJSONObject(it) }.firstOrNull { it.optString("key") == gate }?.optString("title")?.let { pt(it) }
    }
    val activation = if (missingGates.isEmpty()) "" else
        tr(if (type == "switch") "开启时同时启用：" else "调整时自动启用：") + missingGates.joinToString(listSeparator())
    val summary = listOf(pt(control.optString("description")), activation).filter { it.isNotBlank() }.joinToString("\n")
    val key = control.optString("key")
    val value = plugin.state.opt(key) ?: control.opt("default")
    when (type) {
        "text" -> UiRow(title, summary, icon = Icons.Outlined.Info)
        "status" -> UiRow(title, pt(model.statusText(plugin.id, key)).ifBlank { summary.ifBlank { "等待插件提供状态" } }, icon = Icons.Outlined.Info)
        "action" -> UiRow(title, summary, "执行", Icons.Outlined.PlayArrow,
            enabled && plugin.enabled && model.actionReady(plugin.id, key)) { model.runAction(plugin, key) }
        "preset" -> UiRow(title, summary, "应用", Icons.Outlined.Palette, enabled) { model.applyPreset(plugin, control) }
        "switch" -> UiSwitchRow(title, summary, value as? Boolean ?: false, enabled) { model.update(key, it) }
        "slider" -> {
            val min = control.getDouble("min").toFloat()
            val max = control.getDouble("max").toFloat()
            val stored = (value as? Number)?.toDouble()
            val initial = (stored?.toFloat() ?: min).coerceIn(min, max)
            var draft by remember(plugin.id, key, initial) { mutableFloatStateOf(initial) }
            var dragging by remember(plugin.id, key, stored) { mutableStateOf(false) }
            LaunchedEffect(model.working, initial) { if (!model.working) { draft = initial; dragging = false } }
            // A typed value outside an unbounded slider's range parks the thumb at the edge but is shown as saved.
            val shown = if (!dragging && stored != null && (CustomPluginSchema.unboundedInput(control) || stored in control.getDouble("min")..control.getDouble("max"))) stored
                else draft.toString().toDouble().coerceIn(control.getDouble("min"), control.getDouble("max"))
            Column {
                UiRow(title, summary, CustomPluginControlValues.formatSlider(control, shown) + pt(control.optString("unit")),
                    enabled = enabled) { model.editing = control }
                C17Slider(value = draft, valueRange = min..max, enabled = enabled,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    onValueChangeFinished = { model.update(key, CustomPluginControlValues.snapSlider(control, draft.toDouble())) }, onValueChange = {
                        dragging = true
                        draft = CustomPluginControlValues.snapSlider(control, it.toDouble()).toFloat()
                    })
            }
        }
        "color" -> {
            val raw = (value as? String).orEmpty()
            val color = runCatching { CustomPluginControlValues.parseColor(raw) }.getOrDefault(0)
            C17ArrowPreference(title = title, summary = summary.takeIf { it.isNotBlank() }, enabled = enabled,
                onClick = { model.editing = control }, endActions = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(24.dp).clip(CircleShape).background(Color(color))
                            .border(0.75.dp, MiuixTheme.colorScheme.dividerLine, CircleShape))
                        Text(raw, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                })
        }
        "input" -> UiRow(title, summary, (value as? String).orEmpty().ifBlank { "编辑" }.take(24), Icons.Outlined.Edit, enabled) { model.editing = control }
        "app" -> UiRow(title, summary, (value as? String).orEmpty().ifBlank { "选择应用" }.take(24), Icons.Outlined.Apps, enabled) { model.editing = control }
        "sort" -> UiRow(title, summary, "排序", Icons.Outlined.Sort, enabled && plugin.enabled) { model.openSort(plugin, control) }
        "select" -> {
            val options = control.getJSONArray("options")
            val current = value?.toString() ?: options.getJSONObject(0).getString("value")
            val label = (0 until options.length()).map { options.getJSONObject(it) }.firstOrNull { it.getString("value") == current }?.getString("label") ?: current
            UiRow(title, summary, pt(label), Icons.Outlined.Tune, enabled) { model.editing = control }
        }
        "button" -> UiRow(title, summary, "执行", Icons.Outlined.PlayArrow, enabled) { model.update(key, control.opt("value") ?: true) }
    }
}

@Composable
fun CustomPluginDialogs(app: ModernMainActivity) {
    val model = app.customPlugins
    CustomPluginSortDialogs(model)
    if (model.policyOpen) {
        C17OverlayDialog(show = true, title = CustomPluginPolicy.TITLE, onDismissRequest = model::dismissPolicy) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) { Text(CustomPluginPolicy.TEXT, fontSize = 14.sp) }
                if (model.error.isNotBlank()) Text(model.error, fontSize = 13.sp, color = MiuixTheme.colorScheme.error)
                if (model.policyAccepted) C17TextButton("知道了", model::dismissPolicy, Modifier.fillMaxWidth(), enabled = !model.working)
                else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    C17TextButton("暂不同意", model::dismissPolicy, Modifier.weight(1f), enabled = !model.working)
                    C17TextButton("同意并继续", model::acceptPolicy, Modifier.weight(1f), enabled = !model.working)
                }
            }
        }
        return
    }
    if (model.sortOpen) UiChoiceDialog(
        title = "插件排序", values = pluginSortLabels.keys.toList(), labels = pluginSortLabels.values.toList(), selected = model.sortMode,
        description = "长按卡片右上角的三条杠后拖动，松手保存自定义顺序；也可按名称、时间等升降序排列。", onDismiss = { model.sortOpen = false }, onChoice = model::setSortMode,
    )
    if (model.repositoryInputOpen) UiInputDialog(
        title = "添加插件仓库", initial = model.repositoryInput,
        description = "输入完整 GitHub 仓库链接或 HTTPS 插件目录地址。仓库需提供兼容目录；添加来源不代表其内容经过安全认证。",
        label = "仓库或目录地址", confirmLabelForValue = { "添加" }, onDismiss = { model.repositoryInputOpen = false },
    ) { input ->
        val value = input.trim()
        if (value.isEmpty()) "请输入仓库地址" else { model.repositoryInput = value; model.addSource(value); null }
    }
    model.preview?.let { preview ->
        val descriptor = preview.descriptor
        C17OverlayDialog(show = true, title = if (preview.upgrade) trf("更新 %s", descriptor.getString("name")) else trf("导入 %s", descriptor.getString("name")), onDismissRequest = model::dismissPreview) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(descriptor.optString("description"), fontSize = 14.sp)
                    Text(trf("版本：%1\$s\n作者：%2\$s\n来源：%3\$s\n%4\$s", descriptor.getString("version"), descriptor.optString("author").ifBlank { tr("未提供") }, model.previewOrigin, capabilities(descriptor)),
                        fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    val settings = descriptor.optJSONObject("settings")
                    if (settings != null && settings.length() > 0) Text(tr("将调整：") + settings.keys().asSequence().joinToString("、") { SettingsCatalog.item(it)?.title ?: it }, fontSize = 13.sp)
                    val hooks = descriptor.optJSONArray("hooks")
                    if (hooks != null && hooks.length() > 0) Text(trf("包含 %s 项系统界面修补，能否生效取决于目标系统版本。", hooks.length()), fontSize = 13.sp)
                    if (descriptor.has("nativeSettings")) Text(tr("此插件提供原生设置页面。主动打开该入口时，会在C17应用内运行已导入的插件设置代码，插件停用时也可配置。"), fontSize = 13.sp)
                    Text(if (preview.upgrade) trf("从 %s 更新。保留独立设置，更新后需重新启用。", preview.previousVersion) else "导入后默认停用，可在插件详情中检查并启用。", fontSize = 13.sp)
                    Text(trf("插件标识：%s", descriptor.getString("id")), fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    if (!model.policyAccepted) C17TextButton("阅读使用规范与免责声明", { model.policyOpen = true }, Modifier.fillMaxWidth())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    C17TextButton("取消", model::dismissPreview, Modifier.weight(1f), enabled = !model.working)
                    C17TextButton(if (model.working) "处理中" else if (preview.upgrade) "确认更新" else "确认导入", model::install,
                        Modifier.weight(1f), enabled = !model.working && model.policyAccepted)
                }
            }
        }
    }
    model.editing?.let { control ->
        val plugin = model.selected ?: return@let
        val key = control.getString("key")
        val text = remember(plugin.runtimeBaseDir, C17Text.language()) { CustomPluginLocalization.forPlugin(plugin) }
        val title = text.tr(control.getString("title"))
        val description = text.tr(control.optString("description"))
        if (control.getString("type") == "slider") {
            val initial = (plugin.state.opt(key) ?: control.opt("default")) as? Number ?: control.getDouble("min")
            UiInputDialog(title, CustomPluginControlValues.formatSlider(control, initial),
                description = description, numeric = true,
                context = CustomPluginControlValues.sliderRange(control),
                allowNegative = CustomPluginSchema.unboundedInput(control) || control.getDouble("min") < 0,
                onDismiss = { model.editing = null }) { text ->
                try { model.saveEditing(control, CustomPluginControlValues.parseSlider(control, text)) }
                catch (error: IllegalArgumentException) { error.message ?: "请输入有效数值" }
            }
        } else if (control.getString("type") == "color") {
            val raw = plugin.state.optString(key, control.optString("default")).trim()
            UiColorDialog(title, CustomPluginControlValues.parseColor(raw), raw.length == 9,
                description = description,
                alphaDescription = "关闭后使用不透明颜色；开启后可设置颜色自身的透明度。",
                onDismiss = { model.editing = null }) { color, alpha ->
                model.saveEditing(control, CustomPluginControlValues.formatColor(color, alpha))?.let(app::toast)
            }
        } else if (control.getString("type") == "app") {
            PluginApplicationPicker(app, title, plugin.state.optString(key), !model.working,
                onDismiss = { model.editing = null }) { model.saveEditing(control, it)?.let(app::toast); model.editing = null }
        } else if (control.getString("type") == "select") {
            val options = control.getJSONArray("options")
            val choices = (0 until options.length()).map { options.getJSONObject(it) }
            UiChoiceDialog(title, choices.map { it.getString("value") }, choices.map { text.tr(it.getString("label")) },
                plugin.state.optString(key, control.optString("default", choices.first().getString("value"))),
                description = description, onDismiss = { model.editing = null }) { model.saveEditing(control, it)?.let(app::toast); model.editing = null }
        } else UiInputDialog(title, plugin.state.optString(key, control.optString("default")),
            description, onDismiss = { model.editing = null }) {
            if (it.length > 4096) "最多 4096 个字符" else model.saveEditing(control, it)
        }
    }
    if (model.helpOpen) C17OverlayDialog(show = true, title = "插件开发与文档", onDismissRequest = { model.helpOpen = false }) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr("扩展插件可以增加独立功能设置、调整现有界面，或修正特定系统版本的显示行为。停用插件后会撤销其修改。"), fontSize = 14.sp)
                Text(tr("开始开发"), fontWeight = FontWeight.SemiBold)
                Text(tr("用一个 .js 文件声明插件；需要说明文件时可打包为 ZIP。配套 SDK 提供接口说明、格式校验和打包工具。"), fontSize = 14.sp)
                Text(tr("发布前需注明适用的系统版本、改动范围、来源与许可，并实际验证启用、停用和更新。新的底层能力需要宿主支持。"), fontSize = 14.sp)
                Text(tr("API 1 提供声明式设置与系统规则；API 2 在系统界面运行 Java/Android、DEX 与 Hook 扩展，并可提供独立原生设置界面。插件可检测宿主能力，普通业务功能由插件自身更新。新增宿主接口或其他应用的注入引擎仍需对应宿主支持。"), fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Text(tr("仓库目录仅帮助发现和获取插件，不代表对第三方内容的安全认证。使用前请阅读适用范围与风险说明。"), fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            C17TextButton("打开仓库文档", model::openRepositoryHome, Modifier.fillMaxWidth())
            C17TextButton("知道了", { model.helpOpen = false }, Modifier.fillMaxWidth())
        }
    }
}

private fun pluginDate(epoch: Long): String = if (epoch <= 0) "未提供" else
    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA).format(java.util.Date(epoch))

private fun pluginSize(size: Int): String = if (size <= 0) "大小未提供" else if (size < 1024) "$size B" else "${(size + 1023L) / 1024} KB"

private fun capabilities(descriptor: JSONObject): String {
    val values = descriptor.optJSONArray("capabilities") ?: return "能力：独立设置界面"
    val titles = (0 until values.length()).map { when (values.getString(it)) { "settings" -> "调整模块设置"; "systemui" -> "系统界面修补"; "runtime" -> "完整 Java/Android 运行扩展"; else -> "独立设置界面" } }.distinct()
    return "能力：" + titles.ifEmpty { listOf("独立设置界面") }.joinToString("、")
}
