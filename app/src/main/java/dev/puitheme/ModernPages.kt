// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.sp
import java.util.Locale
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.*

@Composable
fun C17Theme(content: @Composable () -> Unit) {
    val controller = remember { ThemeController(colorSchemeMode = ColorSchemeMode.System) }
    MiuixTheme(controller = controller, content = content)
}

/** The top native surface owns every app popup, above both the page and navigation. */
@Composable
fun C17OverlayLayer(app: ModernMainActivity) {
    val visible = app.confirmation != null || app.editing != null || app.colorEditing != null || app.fontCatalogOpen || app.downloadingFont != null
    SideEffect { app.syncModalLayer(visible) }
    Scaffold(containerColor = Color.Transparent) {
        C17Dialogs(app)
    }
}

@Composable
private fun C17Dialogs(app: ModernMainActivity) {
    if (app.fontCatalogOpen) FontCatalogDialog(app)
    app.downloadingFont?.let { entry ->
        OverlayDialog(show = true, title = "下载 ${entry.displayName}",
            summary = "${app.fontDownloadPercent}% · ${if (app.fontDownloadPercent == 100) "正在校验与加载" else "官方 GitHub 字体文件"}\n取消或离开应用会保留原字体。",
            onDismissRequest = app::cancelFontDownload) {
            TextButton(text = "取消下载", onClick = app::cancelFontDownload, modifier = Modifier.fillMaxWidth())
        }
    }
    app.confirmation?.let { prompt ->
        UiConfirmDialog(prompt.title, prompt.summary, prompt.button, delaySeconds = prompt.delaySeconds,
            expiresAt = prompt.expiresAt, onDismiss = app::dismissConfirmation) {
            app.confirmation = null; prompt.action()
        }
    }
    app.editing?.let { EditSettingDialog(app, it) }
    app.colorEditing?.let { item ->
        UiColorDialog(item.title, (app.value(item.key) as? Number)?.toInt() ?: 0,
            StatusBarSettings.customAlpha(app.values, item.key), item.description,
            onDismiss = { app.colorEditing = null }) { color, customAlpha ->
                app.saveColor(item, color, customAlpha); app.colorEditing = null
            }
    }
}

@Composable
fun C17Pages(app: ModernMainActivity) {
    val modalVisible = app.confirmation != null || app.editing != null || app.colorEditing != null || app.fontCatalogOpen || app.downloadingFont != null
    SideEffect { app.syncModalLayer(modalVisible) }
    val group = app.selectedGroup?.let { SettingsCatalog.group(it) }
    var requestedSetting by remember { mutableStateOf<String?>(null) }
    val detailPages = remember(group?.id, app.category) {
        SettingsCatalog.detailPages(group).filter { group?.id != "carrier" || it.id == carrierScene(app.category) }
    }
    var detailPageId by rememberSaveable(group?.id, app.category) {
        mutableStateOf(detailPages.firstOrNull { page -> page.items.any { it.key == requestedSetting } }?.id
            ?: detailPages.firstOrNull()?.id ?: "all")
    }
    val activeDetail = detailPages.firstOrNull { it.id == detailPageId } ?: detailPages.firstOrNull()
    val detailMaster = if (group?.id == "carrier") carrierPrefix(app.category) + "_enabled" else group?.masterKey
    val revealedSetting = remember(group?.id, app.category) { requestedSetting }
    val detailSections = activeDetail?.items?.filterNot { it.key == detailMaster }
        ?.filter { item -> group?.id != "notification_icons" || when (item.key) {
            NotificationIconArea.TEXT -> app.value(NotificationIconArea.MODE) == "text" || item.key == revealedSetting
            NotificationIconArea.IMAGE_NAME -> app.value(NotificationIconArea.MODE) == "image" || item.key == revealedSetting
            else -> true
        } }?.groupBy { it.section } ?: emptyMap()
    val homeTopBar = rememberTopAppBarState()
    val configTopBar = key(app.category, app.query.isNotBlank()) { rememberTopAppBarState() }
    val aboutTopBar = rememberTopAppBarState()
    val detailTopBar = key(group?.id, activeDetail?.id) { rememberTopAppBarState(initialHeightOffsetLimit = 0f) }
    val topBarState = if (group != null) detailTopBar else listOf(homeTopBar, configTopBar, aboutTopBar)[app.page]
    val scroll = MiuixScrollBehavior(state = topBarState)
    val title = group?.let { destinationTitle(it, app.category) } ?: listOf("主页", "配置", "关于")[app.page]
    val homeScroll = rememberLazyListState()
    val configScroll = key(app.category, app.query.isNotBlank()) { rememberLazyListState() }
    val aboutScroll = rememberLazyListState()
    val detailScroll = key(group?.id, activeDetail?.id) { rememberLazyListState() }
    val currentScroll = if (group != null) detailScroll else listOf(homeScroll, configScroll, aboutScroll)[app.page]
    val searching = group == null && app.page == 1 && app.query.isNotBlank()
    LaunchedEffect(group?.id, activeDetail?.id, requestedSetting) {
        val requested = requestedSetting ?: return@LaunchedEffect
        if (group == null) return@LaunchedEffect
        if (requested == detailMaster) {
            detailScroll.scrollToItem(0); requestedSetting = null; return@LaunchedEffect
        }
        val sectionIndex = detailSections.values.indexOfFirst { items -> items.any { it.key == requested } }
        if (sectionIndex >= 0) {
            detailScroll.scrollToItem(1 + sectionIndex)
            requestedSetting = null
        }
    }
    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            if (group == null) TopAppBar(title = title, scrollBehavior = scroll)
            else SmallTopAppBar(title = title, scrollBehavior = scroll, navigationIcon = {
                IconButton(onClick = app::closeGroup) { Icon(MiuixIcons.Back, "返回") }
            }, actions = {
                detailMaster?.let { key ->
                    Switch(checked = app.bool(key), onCheckedChange = { app.save(key, it) }, enabled = app.canEdit && !app.busy)
                }
            })
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
        if (group != null && detailPages.size > 1) {
            TabRow(tabs = detailPages.map { it.title }, selectedTabIndex = detailPages.indexOf(activeDetail),
                onTabSelected = { requestedSetting = null; detailPageId = detailPages[it].id },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                minWidth = 64.dp, itemSpacing = 6.dp)
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth()
                .nestedScroll(scroll.nestedScrollConnection),
            state = currentScroll,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = if (searching) 8.dp else 12.dp,
                bottom = padding.calculateBottomPadding() + if (group == null) 80.dp else 32.dp),
            verticalArrangement = Arrangement.spacedBy(if (searching) 8.dp else 12.dp),
        ) {
            if (group != null) {
                item { DetailIntro(app, group) }
                detailSections.forEach { (section, items) ->
                    item(key = "${group.id}-$section") {
                        DetailSettingsSection(app, group, section, items, revealedSetting)
                    }
                }
                val resetItems = if (group.id == "carrier" || detailPages.size > 1) activeDetail?.items ?: emptyList() else group.items
                val resetTitle = title + if (detailPages.size > 1) " · ${activeDetail?.title}" else ""
                item { UiGroupCard { UiRow("恢复本页默认设置", "只重置$resetTitle", icon = MiuixIcons.Reset, enabled = app.canEdit && !app.busy) {
                    app.resetGroup(group, items = resetItems, title = resetTitle)
                } } }
            } else when (app.page) {
                0 -> {
                    item { ActivationHero(app) }
                    item { OverviewGroups(app) }
                    item { SectionTitle("运行状态") }
                    item { UiGroupCard {
                        UiRow("LSPosed", if (app.lspEnabled) "${app.framework.frameworkName} ${app.framework.frameworkVersion}" else app.framework.message.take(42),
                            if (app.lspEnabled) "已启用" else "未连接", Icons.Outlined.Extension) {
                            app.confirmation = UiConfirmation("LSPosed 状态", app.framework.message + if (app.lspEnabled) "\nAPI ${app.framework.apiVersion}" else "", "知道了") {}
                        }
                        UiRow("当前版本", if (app.runtimeActive) "已在系统界面生效" else if (app.checkingRuntime) "正在验证系统界面" else "重新加载系统界面后应用当前版本",
                            if (app.runtimeActive) "已生效" else if (app.checkingRuntime) "检测中" else "待重载", Icons.Outlined.CheckCircle) { app.checkActivation() }
                        UiRow("Root 权限", if (app.rootGranted) "允许未激活时独立保存配置" else app.rootMessage.take(36), if (app.rootGranted) "已授权" else if (app.checkingRoot) "检测中" else "授权", Icons.Outlined.VerifiedUser, enabled = !app.checkingRoot) { app.requestRoot() }
                        UiRow("系统界面", "应用当前模块版本", if (app.busy) "处理中" else "重启", Icons.Outlined.RestartAlt, enabled = !app.busy) { app.restartSystemUi() }
                    } }
                    item { SectionTitle("模块控制") }
                    item { UiGroupCard {
                        UiSwitchRow("安全模式", "暂停所有修改，保留现有配置", app.bool(StatusBarSettings.SAFE_MODE), app.canEdit && !app.busy) { app.save(StatusBarSettings.SAFE_MODE, it) }
                    } }
                    item { Text(if (app.canEdit) "设置会实时保存。系统界面激活后即可应用修改。" else "在 LSPosed 中启用模块并勾选系统界面，或授予 Root 后独立保存配置。",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary) }
                }
                1 -> {
                    item { ConfigurationHeader(app) }
                    if (!app.canEdit) item { UiGroupCard { UiRow("尚未获得配置权限", "前往主页激活模块或授予 Root", "前往", MiuixIcons.Unlock) { app.selectPage(0) } } }
                    if (app.query.isBlank()) {
                        SettingsCatalog.navigationSections(app.category).forEach { section ->
                            item(key = "category-${app.category}-${section.title}") {
                                Column {
                                    SectionTitle(section.title)
                                    UiGroupCard {
                                        section.groups.forEach { destination ->
                                            UiRow(destinationTitle(destination, app.category), concise(destination.description),
                                                if (destinationEnabled(app, destination, app.category)) "已开启" else "已关闭",
                                                groupIcon(destination.id)) { app.openGroup(destination.id) }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        val term = app.query.trim()
                        val matches = SettingsCatalog.groups().mapNotNull { destination ->
                            val matchingItems = destination.items.filterNot { it.master }.filter {
                                (it.title + it.description + it.section + it.labels.joinToString(" ")).contains(term, true)
                            }
                            if ((destination.title + destination.description).contains(term, true) || matchingItems.isNotEmpty()) destination to matchingItems else null
                        }
                        matches.forEach { (destination, matchingItems) ->
                            item(key = "search-${destination.id}") {
                                Column {
                                    Text(destination.title, Modifier.padding(start = 16.dp, top = 2.dp, bottom = 6.dp),
                                        fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                    UiGroupCard {
                                        if (matchingItems.isEmpty()) SearchResultRow(destination.title, destination.description, term,
                                            groupIcon(destination.id)) { app.openGroup(destination.id) }
                                        matchingItems.forEach { setting ->
                                            val summary = if (setting.title.contains(term, true) || setting.section.contains(term, true)) setting.section
                                                else setting.labels.firstOrNull { it.contains(term, true) }
                                                    ?: setting.description.takeIf { it.contains(term, true) } ?: setting.section
                                            SearchResultRow(setting.title, summary, term, groupIcon(destination.id)) {
                                                if (destination.id == "carrier") app.category = when {
                                                    setting.key.startsWith(CarrierPanels.LOCKSCREEN + "_") -> SettingsCatalog.LOCK_SCREEN
                                                    setting.key.startsWith(CarrierPanels.CONTROL + "_") -> SettingsCatalog.CONTROL_CENTER
                                                    else -> SettingsCatalog.NOTIFICATION
                                                }
                                                requestedSetting = setting.key; app.openGroup(destination.id)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (matches.isEmpty()) item { Text("没有找到相关配置", Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary) }
                    }
                }
                2 -> {
                    item { AboutHero(app) }
                    item { SectionTitle("配置文件") }
                    item { UiGroupCard {
                        UiRow("导出配置", "保存当前设置为 JSON 文件", icon = Icons.Outlined.FileUpload, enabled = app.canEdit && !app.busy) { app.chooseExport() }
                        UiRow("导入配置", "预览并确认后应用设置", icon = Icons.Outlined.FileDownload, enabled = app.canEdit && !app.busy) { app.chooseImport() }
                    } }
                    item { SectionTitle("项目与开源") }
                    item { UiGroupCard {
                        UiRow("检查更新", app.updateMessage, if (app.checkingUpdates) "检查中" else "检查",
                            icon = Icons.Outlined.SystemUpdate, enabled = !app.checkingUpdates && !app.busy) { app.checkUpdates() }
                        UiRow("正式发布", "查看最新正式版与安装包", icon = Icons.Outlined.OpenInNew) { app.openLatestRelease() }
                        UiRow("项目源码", "GitHub · SANWU5/c17-statusbar", icon = Icons.Outlined.Code) { app.openUrl("https://github.com/SANWU5/c17-statusbar") }
                        UiRow("Miuix", "界面组件 · Apache 2.0", "0.9.4", Icons.Outlined.Palette) { app.openUrl("https://github.com/compose-miuix-ui/miuix") }
                        UiRow("开源许可", "GPL 3.0 · Apache 2.0 · OFL 1.1", icon = Icons.Outlined.Article) {
                            app.confirmation = UiConfirmation("开源许可", "C17 Statusbar：GPL-3.0-only\nMiuix：Apache-2.0\nGitHub 字体目录：SIL Open Font License 1.1，各字体完整许可随应用保留。\n磁贴圆角技术参考：MCGA\nZhuangzhi Meng (Gustate XiaoMeng)\nGPL-3.0-or-later\n完整许可与版权声明已随应用保留。", "知道了") {}
                        }
                    } }
                    item { SectionTitle("诊断") }
                    item { UiGroupCard {
                        UiSwitchRow("诊断日志", "需要排查问题时开启", app.bool(StatusBarSettings.DIAGNOSTICS_ENABLED), app.canEdit) { app.save(StatusBarSettings.DIAGNOSTICS_ENABLED, it) }
                        UiRow("导出诊断日志", "保存用于排查的运行记录", icon = Icons.Outlined.Description) { app.chooseLog() }
                    } }
                    item { Text("Copyright © 2026 aiingjie", modifier = Modifier.fillMaxWidth().padding(12.dp), fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary) }
                }
            }
        }
        }
    }
}

@Composable
private fun ActivationHero(app: ModernMainActivity) {
    val safe = app.bool(StatusBarSettings.SAFE_MODE)
    val dark = isSystemInDarkTheme()
    val background = when {
        safe -> if (dark) Color(0xff3b2d16) else Color(0xfffff2d9)
        app.runtimeActive || app.lspEnabled -> if (dark) Color(0xff173820) else Color(0xffdffbe4)
        app.rootGranted -> if (dark) Color(0xff182d49) else Color(0xffe8f1ff)
        else -> if (dark) Color(0xff282b31) else Color(0xffeeeff3)
    }
    val accent = when {
        safe -> Color(0xffd79e25)
        app.runtimeActive || app.lspEnabled -> if (dark) Color(0xff46d970) else Color(0xff2fce60)
        app.rootGranted -> MiuixTheme.colorScheme.primary
        else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
    val version = remember(app) {
        runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "1.6.0" }
            .getOrDefault("1.6.0")
    }
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.defaultColors(color = background), onClick = app::checkActivation) {
        Box(Modifier.fillMaxWidth().height(128.dp).clip(RoundedCornerShape(16.dp))) {
            Box(
                Modifier.size(112.dp).align(Alignment.BottomEnd).offset(x = 22.dp, y = 28.dp)
                    .border(9.dp, accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (safe) MiuixIcons.Pause else if (app.runtimeActive || app.lspEnabled) MiuixIcons.Ok else if (app.rootGranted) MiuixIcons.Unlock else MiuixIcons.Layers,
                    null, Modifier.size(74.dp), tint = accent)
            }
            Column(Modifier.align(Alignment.TopStart).padding(start = 20.dp, top = 14.dp)) {
                Text(if (safe) "安全模式" else if (app.runtimeActive) "模块已激活" else if (app.lspEnabled) "模块已启用" else if (app.rootGranted) "Root 已授权" else "模块待激活",
                    fontSize = 23.sp, fontWeight = FontWeight.Normal, color = MiuixTheme.colorScheme.onSurface)
                Spacer(Modifier.height(2.dp))
                Text(version, fontSize = 16.sp, fontWeight = FontWeight.Normal, color = MiuixTheme.colorScheme.onSurface)
            }
            Text(if (safe) "修改已暂停，配置完整保留" else if (app.runtimeActive) "当前版本已生效 · 配置可实时保存" else if (app.lspEnabled) "重新加载系统界面以应用当前版本" else if (app.rootGranted) "可独立编辑并保存配置" else "启用模块，开始定制状态栏",
                Modifier.align(Alignment.BottomStart).padding(start = 20.dp, end = 92.dp, bottom = 14.dp),
                fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun OverviewGroups(app: ModernMainActivity) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf("statusbar" to "状态栏", "notification" to "通知中心").forEach { (category, title) ->
            val groups = if (category == SettingsCatalog.STATUSBAR) SettingsCatalog.groups(category)
                else SettingsCatalog.groups().filter { it.category != SettingsCatalog.STATUSBAR }
            Card(modifier = Modifier.weight(1f), onClick = { app.category = category; app.selectPage(1) }) {
                Column(Modifier.padding(20.dp)) {
                    Icon(if (category == "statusbar") C17SectionIcons.StatusBar else C17SectionIcons.NotificationCenter, null, Modifier.size(27.dp), tint = MiuixTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(12.dp))
                    Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text("${groups.count { destinationEnabled(app, it) }} / ${groups.size} 项开启", fontSize = 13.sp, color = MiuixTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun ConfigurationHeader(app: ModernMainActivity) {
    val categories = listOf(SettingsCatalog.STATUSBAR, SettingsCatalog.NOTIFICATION, SettingsCatalog.CONTROL_CENTER, SettingsCatalog.LOCK_SCREEN)
    val labels = listOf("状态栏", "通知栏", "控制中心", "锁屏")
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val textStyle = MiuixTheme.textStyles.body2
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val itemWidth = (maxWidth - 10.dp - 12.dp) / 4
            val available = with(density) { (itemWidth - 8.dp).toPx() }.coerceAtLeast(1f)
            val fontSize = remember(available, density.fontScale, textStyle, measurer) {
                var size = 14f
                repeat(5) {
                    val width = labels.maxOf { label -> measurer.measure(label,
                        style = textStyle.copy(fontSize = size.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1, softWrap = false).size.width.toFloat() }
                    if (width > available) size = (size * available / width - .15f).coerceAtLeast(1f)
                }
                size.sp
            }
            MiuixTheme(textStyles = MiuixTheme.textStyles.copy(body2 = textStyle.copy(fontSize = fontSize))) {
                TabRowWithContour(tabs = labels, selectedTabIndex = categories.indexOf(app.category).coerceAtLeast(0),
                    onTabSelected = { app.category = categories[it] }, modifier = Modifier.fillMaxWidth(),
                    minWidth = itemWidth, maxWidth = itemWidth, itemSpacing = 4.dp, height = 45.dp)
            }
        }
        TextField(value = app.query, onValueChange = { app.query = it }, label = "搜索所有配置", singleLine = true,
            useLabelAsPlaceholder = true, insideMargin = DpSize(16.dp, 12.dp),
            leadingIcon = { Icon(MiuixIcons.Search, null, Modifier.padding(start = 16.dp, end = 12.dp).size(20.dp)) },
            modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SearchResultRow(title: String, summary: String, term: String, icon: ImageVector, onClick: () -> Unit) {
    val accent = MiuixTheme.colorScheme.primary
    fun highlighted(value: String) = buildAnnotatedString {
        append(value)
        var start = value.indexOf(term, ignoreCase = true)
        while (term.isNotEmpty() && start >= 0) {
            addStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold), start, start + term.length)
            start = value.indexOf(term, start + term.length, ignoreCase = true)
        }
    }
    val shownTitle = remember(title, term, accent) { highlighted(title) }
    val shownSummary = remember(summary, term, accent) {
        val match = summary.indexOf(term, ignoreCase = true)
        val visibleSummary = if (match > 36) "…" + summary.substring(match - 18) else summary
        highlighted(visibleSummary)
    }
    BasicComponent(insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp), onClick = onClick,
        startAction = { Icon(icon, null, Modifier.padding(end = 12.dp).size(20.dp), tint = MiuixTheme.colorScheme.onSurface) },
        endActions = { Icon(MiuixIcons.Basic.ArrowRight, null, Modifier.padding(start = 8.dp).size(width = 10.dp, height = 16.dp),
            tint = MiuixTheme.colorScheme.onSurfaceVariantActions) }) {
        Text(shownTitle, fontSize = MiuixTheme.textStyles.headline1.fontSize, fontWeight = FontWeight.Medium,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (summary.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(shownSummary, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DetailIntro(app: ModernMainActivity, group: SettingsCatalog.Group) {
    val description = if (group.id == "carrier") "只作用于${when (app.category) {
        SettingsCatalog.LOCK_SCREEN -> "锁屏"
        SettingsCatalog.CONTROL_CENTER -> "控制中心"
        else -> "通知栏"
    }}，可显示运营商、时间或自定义文本。" else if (group.id == "notification_icons" &&
        app.value(NotificationIconArea.MODE) == "image" && (app.value(NotificationIconArea.IMAGE_REVISION) as? String).isNullOrBlank())
        group.description + "\n尚未导入图片，当前会保留原生通知图标。" else group.description
    val conflicts = SettingsCatalog.conflictDescription(group.id, app.category)
    Text(description + if (conflicts.isNotBlank()) "\n$conflicts" else "",
        Modifier.padding(horizontal = 12.dp, vertical = 4.dp), fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    if (!app.canEdit) UiGroupCard { UiRow("激活模块或授予 Root 后即可修改", "点击前往主页", icon = MiuixIcons.Unlock) { app.closeGroup(); app.selectPage(0) } }
}

@Composable
private fun DetailSettingsSection(app: ModernMainActivity, group: SettingsCatalog.Group, section: String,
    items: List<SettingsCatalog.Item>, requested: String?) {
    val advanced = group.id == "notification_big_clock" && section in listOf("收起样式", "收起位置")
    var expanded by rememberSaveable(group.id, section) { mutableStateOf(!advanced || items.any { it.key == requested }) }
    LaunchedEffect(requested) { if (items.any { it.key == requested }) expanded = true }
    Column {
        if (!advanced) SectionTitle(section.substringAfter(" · "))
        UiGroupCard {
            if (advanced) UiRow(section, "上滑收起后的细节", if (expanded) "收起" else "展开") { expanded = !expanded }
            if (expanded) items.forEach { item ->
                val paired = item.type == "color" && item.pairedKey.isNotEmpty() && items.any { it.key == item.pairedKey }
                if (paired && item.scene == "light") {
                    val dark = SettingsCatalog.item(item.pairedKey)
                    UiColorPair(item.title.substringBefore(" ·"),
                        (app.value(item.key) as? Number)?.toInt() ?: 0,
                        (app.value(dark.key) as? Number)?.toInt() ?: 0,
                        app.canEdit && !app.busy, onLight = { app.pickColor(item) }, onDark = { app.pickColor(dark) })
                } else if (!paired || item.scene != "dark") SettingsItem(app, item)
            }
        }
    }
}

@Composable
private fun SettingsItem(app: ModernMainActivity, item: SettingsCatalog.Item) {
    val value = if (item.type == "numeric") SettingEditor.storedValue(item, app.values) else app.value(item.key)
    val unavailable = SettingsCatalog.unavailableReason(item.key)
    val enabled = app.canEdit && !app.busy && unavailable.isEmpty()
    when (item.type) {
        "boolean" -> UiSwitchRow(item.title, unavailable.ifEmpty { item.description }, value == true, enabled) { app.save(item.key, it) }
        "numeric" -> {
            var current by remember(item.key, value) { mutableStateOf<Number>((value as? Number) ?: 0f) }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(item.title, Modifier.weight(1f).clickable(enabled = enabled) { app.editing = item }, fontSize = 16.sp)
                    Text(SettingEditor.formatted(item, current), modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = enabled) { app.editing = item }.padding(horizontal = 8.dp, vertical = 6.dp), fontSize = 14.sp, color = MiuixTheme.colorScheme.primary)
                }
                Slider(value = current.toFloat().coerceIn(item.min, item.max), onValueChange = {
                    current = if (item.key == "qs_tile_corner_radius") kotlin.math.round(it * 2f) / 2f else roundToStep(it, item.step)
                }, valueRange = item.min..item.max, enabled = enabled, onValueChangeFinished = {
                    app.save(item.key, current)
                })
                Text(unavailable.ifEmpty { item.description.ifEmpty { "点击数值手动编辑，滑块仅提供建议范围" } },
                    fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        }
        "color" -> ArrowPreference(title = item.title.substringBefore(" ·"), summary = "颜色与透明度", enabled = enabled,
            onClick = { app.pickColor(item) }, endActions = {
                Box(Modifier.size(30.dp).clip(CircleShape).background(Color((value as? Number)?.toInt() ?: 0))
                    .border(0.75.dp, MiuixTheme.colorScheme.dividerLine, CircleShape))
            })
        "font" -> {
            UiRow("GitHub 开源字体", "10 款可变字体，支持连续粗细调节", "选择", enabled = enabled) { app.chooseOpenFont() }
            UiRow(item.title, item.description, value?.toString()?.takeIf { it.isNotBlank() } ?: "选择字体", enabled = enabled) { app.chooseFont() }
        }
        "image" -> UiRow(item.title, item.description, value?.toString()?.takeIf { it.isNotBlank() }
            ?: if ((app.value(NotificationIconArea.IMAGE_REVISION) as? String).isNullOrBlank()) "选择图片" else "已导入图片",
            icon = Icons.Outlined.Image, enabled = enabled) { app.chooseNotificationIcon() }
        else -> UiRow(item.title, item.description, displayValue(item, value), enabled = enabled) { app.editing = item }
    }
}

@Composable
private fun FontCatalogDialog(app: ModernMainActivity) {
    OverlayDialog(show = true, title = "GitHub 开源可变字体",
        summary = "精选 10 款成熟字体；点击后下载，字号和粗细仍在各功能里调节。中文优先选择 Noto Sans SC 或 Noto Serif SC。",
        onDismissRequest = { app.fontCatalogOpen = false }) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(FontCatalog.entries(), key = { it.id }) { entry ->
                    UiGroupCard {
                        UiRow(entry.displayName, "${entry.coverage}\n粗细 ${entry.minWeight}–${entry.maxWeight} · %.1f MB".format(Locale.ROOT, entry.bytes / 1048576.0),
                            "下载", enabled = app.canEdit && !app.busy) { app.downloadFont(entry) }
                        UiRow("字体来源", "官方 GitHub 仓库", "GitHub", icon = Icons.Outlined.Code) { app.openUrl(entry.sourceUrl) }
                        UiRow("开源许可", entry.licenseName, "查看", icon = Icons.Outlined.Article) { app.openUrl(entry.licenseUrl) }
                    }
                }
            }
            TextButton(text = "关闭", onClick = { app.fontCatalogOpen = false }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun EditSettingDialog(app: ModernMainActivity, item: SettingsCatalog.Item) {
    val editor = remember(item.key, app.values) { SettingEditor.forItem(item, app.values) }
    if (item.type == "options") {
        UiChoiceDialog(item.title, item.values.toList(), item.labels.toList(), app.value(item.key)?.toString() ?: "",
            description = editor.context + if (item.description.isBlank()) "" else "\n${item.description}",
            onDismiss = { app.editing = null }) {
            if (it == "custom" && (item.key == StatusBarSettings.FONT_MODE || item.key == NotificationBigClockSettings.FONT)
                && !ConfigTransfer.customFontAvailable(app)) {
                app.toast("请先在文字字体页面导入自选字体")
            } else app.save(item.key, it)
            app.editing = null
        }
    } else UiInputDialog(item.title, editor.initial, editor.description, editor.numeric,
        label = editor.label, context = editor.context, valueSummary = editor.valueSummary,
        integer = editor.integer, allowNegative = !editor.integer && item.key != QsTileCorners.RADIUS,
        pattern = editor.pattern, onDismiss = { app.editing = null }) { input ->
        val result: Any = if (item.type == "numeric") {
            try { SettingsCatalog.customNumber(item, input) } catch (error: IllegalArgumentException) { return@UiInputDialog error.message ?: "请输入有效的有限数值" }
        } else input
        val error = SettingsCatalog.validationError(item, result)
        if (error != null) error else {
            if (result is Float) app.tryNumber(item, result)
            else { app.save(item.key, result); app.editing = null }
            null
        }
    }
}

@Composable
private fun AboutHero(app: ModernMainActivity) {
    val version = remember(app) { app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "" }
    val versionLabel = remember(version) {
        val suffix = version.substringBefore('+').substringAfter('-', "").lowercase(Locale.ROOT)
        if (suffix.split('.', '-').any { it.startsWith("alpha") || it.startsWith("beta") || it.startsWith("rc") }) "测试版" else "正式版"
    }
    UiGroupCard {
        Column(Modifier.fillMaxWidth().padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AndroidView(factory = { context -> android.widget.ImageView(context).apply {
                setImageDrawable(context.packageManager.getApplicationIcon(context.packageName))
                contentDescription = context.getString(dev.puitheme.iosstatusbar.R.string.app_name)
            } }, modifier = Modifier.size(72.dp))
            Spacer(Modifier.height(14.dp))
            Text(app.getString(dev.puitheme.iosstatusbar.R.string.app_name), fontSize = 22.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text("$version · $versionLabel", fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Spacer(Modifier.height(6.dp))
            Text("作者 aiingjie", fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurface)
            Spacer(Modifier.height(12.dp))
            Text("状态栏与通知中心，由你定制。", fontSize = 14.sp)
        }
    }
}

@Composable private fun SectionTitle(title: String) { Text(title, Modifier.padding(start = 12.dp, top = 8.dp, bottom = 2.dp), fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary) }
private fun concise(description: String) = description.substringBefore("。").take(45)
private fun carrierScene(category: String) = when (category) {
    SettingsCatalog.LOCK_SCREEN -> "lockscreen"
    SettingsCatalog.CONTROL_CENTER -> "control"
    else -> "notification"
}
private fun carrierPrefix(category: String) = when (category) {
    SettingsCatalog.LOCK_SCREEN -> CarrierPanels.LOCKSCREEN
    SettingsCatalog.CONTROL_CENTER -> CarrierPanels.CONTROL
    else -> CarrierPanels.NOTIFICATION
}
private fun destinationTitle(group: SettingsCatalog.Group, category: String): String =
    if (group.id != "carrier") group.title else when (category) {
        SettingsCatalog.LOCK_SCREEN -> "锁屏运营商文字自定义"
        SettingsCatalog.CONTROL_CENTER -> "控制中心运营商文字自定义"
        else -> "通知栏运营商文字自定义"
    }
private fun destinationEnabled(app: ModernMainActivity, group: SettingsCatalog.Group, category: String? = null): Boolean =
    if (group.id != "carrier") app.bool(group.masterKey)
    else if (category != null) app.bool(carrierPrefix(category) + "_enabled")
    else CarrierPanels.GROUPS.any { app.bool(it + "_enabled") }
private fun formatNumber(value: Float): String = if (kotlin.math.abs(value) >= 1e7f || value != 0f && kotlin.math.abs(value) < .001f) value.toString()
    else java.math.BigDecimal(value.toString()).stripTrailingZeros().toPlainString()
private fun roundToStep(value: Float, step: Float): Float = NumericInput.parse(value.toString(), if (step >= 1f) 0 else 2)
private fun displayValue(item: SettingsCatalog.Item, value: Any?): String {
    val index = item.values.indexOf(value?.toString())
    return if (index >= 0) item.labels[index] else value?.toString()?.take(24) ?: "默认"
}
private fun groupIcon(id: String): ImageVector = when (id) {
    "speed" -> Icons.Outlined.Speed
    "data" -> Icons.Outlined.SignalCellularAlt
    "wifi" -> Icons.Outlined.Wifi
    "label" -> Icons.Outlined.NetworkCell
    "clock", "shade_clock", "notification_big_clock" -> Icons.Outlined.Schedule
    "carrier" -> Icons.Outlined.CellTower
    "font" -> Icons.Outlined.TextFields
    "battery" -> Icons.Outlined.BatteryFull
    "tiles" -> Icons.Outlined.Swipe
    "qs_appearance" -> Icons.Outlined.Palette
    "tile_corners" -> Icons.Outlined.RoundedCorner
    "tile_icon_size" -> Icons.Outlined.GridView
    "shade_status_icons" -> Icons.Outlined.SwapHoriz
    "notification_clear" -> Icons.Outlined.DeleteOutline
    "notification_icons" -> C17SectionIcons.NotificationCenter
    "qs_media" -> Icons.Outlined.MusicNote
    else -> Icons.Outlined.Tune
}
