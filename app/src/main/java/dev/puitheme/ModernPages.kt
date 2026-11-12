// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.viewinterop.AndroidView
import dev.puitheme.iosstatusbar.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import java.util.Locale
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.preference.RadioButtonLocation
import top.yukonga.miuix.kmp.theme.*

/** The top native surface owns every app popup, above both the page and navigation. */
@Composable
fun C17OverlayLayer(app: ModernMainActivity) {
    val visible = app.customPlugins.hasDialog || app.welcomeOpen || app.freeNoticeRequired || app.thanksOpen || app.performanceOpen || app.donationImage != null || app.confirmation != null || app.editing != null || app.statusBarOrderChoice != null || app.colorEditing != null || app.fontCatalogOpen || app.fontScopeSelection != null || app.marqueePickerOpen || app.configScope != null || app.fontRenameTarget != null || app.downloadingFont != null || app.notificationOverridesOpen || (app.wallpaperEdit != null) || app.clockLaunchEditing != null
    SideEffect { app.syncModalLayer(visible) }
    C17Scaffold(containerColor = Color.Transparent) {
        C17Dialogs(app)
    }
}

@Composable
private fun C17Dialogs(app: ModernMainActivity) {
    if (app.welcomeOpen) { WelcomeFlow(app); return }
    if (app.freeNoticeRequired) {
        FreeNoticeDialog(app)
        // The thanks wall can be opened from the notice; it lies above it.
        if (app.thanksOpen) ThanksWallDialog(app)
        return
    }
    if (app.thanksOpen) { ThanksWallDialog(app); return }
    if (app.customPlugins.hasDialog) CustomPluginDialogs(app)
    if (app.performanceOpen && app.confirmation == null) PerformanceSheet(app)
    app.donationImage?.let { DonationDialog(app, it) }
    if (app.notificationOverridesOpen) NotificationOverridesDialog(app)
    app.clockLaunchEditing?.let { ClockLaunchAppDialog(app, it) }
    app.wallpaperEdit?.let { ShadeWallpaperCropDialog(app, it) }
    if (app.fontCatalogOpen) FontCatalogDialog(app)
    app.fontScopeSelection?.let { FontScopeDialog(app, it) }
    if (app.marqueePickerOpen) MarqueePresetDialog(app)
    app.configScope?.let { ConfigScopeDialog(app, it) }
    app.fontRenameTarget?.let { entry ->
        UiInputDialog("重命名字体", entry.name, "只修改字体列表中的名称，不改变字形、粗细或其他已导入字体。",
            onDismiss = { app.fontRenameTarget = null; app.fontCatalogOpen = true }) { app.renameLibraryFont(entry, it) }
    }
    app.downloadingFont?.let { entry ->
        C17OverlayDialog(show = true, title = trf("下载 %s", entry.displayName),
            summary = trf("%1\$s%% · %2\$s\n下载后选择作用范围，统一应用前保留原字体。", app.fontDownloadPercent, if (app.fontDownloadPercent == 100) tr("正在校验与入库") else tr("官方 GitHub 字体文件")),
            onDismissRequest = app::cancelFontDownload) {
            C17TextButton(text = "取消下载", onClick = app::cancelFontDownload, modifier = Modifier.fillMaxWidth())
        }
    }
    app.confirmation?.let { prompt ->
        UiConfirmDialog(prompt.title, prompt.summary, prompt.button, delaySeconds = prompt.delaySeconds,
            expiresAt = prompt.expiresAt, linkLabel = prompt.linkLabel,
            onLink = prompt.linkUrl.takeIf { it.isNotEmpty() }?.let { url -> { app.openUrl(url) } },
            onDismiss = app::dismissConfirmation) {
            app.confirmation = null; prompt.action()
        }
    }
    app.statusBarOrderChoice?.let { choice ->
        UiChoiceDialog(choice.title, choice.values, choice.labels, choice.selected,
            description = choice.description, onDismiss = { app.statusBarOrderChoice = null }, onChoice = choice.onChoice)
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
private fun ClockLaunchAppDialog(app: ModernMainActivity, key: String) {
    val calendar = key == NotificationBigClockSettings.DATE_LAUNCH_APP
    val component = BigClockWidgetsSettings.launchAppKey(key)
    val allowed = app.canEdit && !app.busy && app.clockLaunchEnabled(key)
    val selected = app.value(key)?.toString().orEmpty()
    val selectedLabel = remember(key, selected, app.clockLaunchChoices) { app.clockLaunchLabel(key) }
    var query by remember(key) { mutableStateOf("") }
    var category by remember(key) { mutableIntStateOf(0) }
    var error by remember(key) { mutableStateOf("") }
    val choices = remember(app.clockLaunchChoices, selected, query, category) {
        app.clockLaunchChoices.filter { choice ->
            (category == 0 || choice.system == (category == 2)) &&
                (query.isBlank() || choice.label.contains(query, true) || choice.packageName.contains(query, true))
        }.sortedBy { if (it.packageName == selected) 0 else 1 }
    }
    val window = LocalWindowInfo.current.containerDpSize
    // Only a short window moves the filters into the list. The keyboard opening must not: the search field would be rebuilt there,
    // lose its focus and close the keyboard again, and the dialog jumped between the two layouts.
    val compact = window.height < 500.dp
    val listState = key(key, query, category) { rememberLazyListState() }
    val defaultRow: @Composable () -> Unit = {
        C17RadioButtonPreference(title = if (component) "不打开应用" else "系统默认", selected = selected.isEmpty(), enabled = allowed,
            summary = if (component) "保留文字显示，不占用点击区域" else app.clockLaunchDefault?.let { trf("打开 %s", it.label) }
                ?: if (app.clockLaunchLoading) "正在读取系统默认程序…" else trf("沿用系统原生%s入口", if (calendar) tr("日历") else tr("时钟")),
            radioButtonLocation = RadioButtonLocation.End,
            insideMargin = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
            onClick = { error = app.saveClockLaunchApp(key, "") })
    }
    val controls: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            C17TextField(value = query, onValueChange = { query = it }, label = "搜索应用名称或包名", singleLine = true,
                useLabelAsPlaceholder = true, modifier = Modifier.fillMaxWidth(), insideMargin = DpSize(12.dp, 10.dp))
            UiContentTabs(tabs = listOf("全部", "非系统", "系统"), selectedTabIndex = category,
                onTabSelected = { category = it }, modifier = Modifier.fillMaxWidth())
        }
    }
    val dialogTitle = if (component) trf("%s侧文字打开的应用", if (BigClockWidgetsSettings.textSide(key) == BigClockWidgetsSettings.LEFT) tr("左") else tr("右"))
        else if (calendar) "日期打开的应用" else "时间打开的应用"
    C17OverlayDialog(show = true, title = dialogTitle,
        modifier = Modifier.heightIn(max = (window.height * .82f).coerceAtMost(640.dp)),
        insideMargin = DpSize(18.dp, 18.dp), outsideMargin = DpSize(16.dp, 12.dp),
        onDismissRequest = app::dismissClockLaunchPicker) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!compact) {
                Text(trf("当前：%s", selectedLabel), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                defaultRow()
                controls()
            }
            // Keep the result list bounded. In a short window or above the keyboard,
            // the filters join this same scroll area rather than squeezing its rows.
            LazyColumn(Modifier.weight(1f, fill = false).fillMaxWidth(), state = listState,
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (compact) {
                    item { Text(trf("当前：%s", selectedLabel), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary) }
                    item { defaultRow() }
                    item { controls() }
                }
                item { Text(if (app.clockLaunchLoading) "正在读取可启动应用…" else trf("可启动应用 · %s 个", choices.size),
                    fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)) }
                items(choices, key = { it.packageName }) { choice ->
                    C17RadioButtonPreference(title = choice.label, summary = choice.packageName,
                        selected = selected == choice.packageName, enabled = allowed,
                        startAction = { Box(Modifier.padding(end = 12.dp)) { AppIcon(app, choice.packageName) } },
                        radioButtonLocation = RadioButtonLocation.End,
                        insideMargin = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                        onClick = { error = app.saveClockLaunchApp(key, choice.packageName) })
                }
                if (!app.clockLaunchLoading && choices.isEmpty()) item {
                    Text(tr("没有符合条件的可启动应用"), fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(vertical = 12.dp))
                }
                item { Text(tr("所选应用卸载或停用后，自动回到系统默认。"), fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(top = 8.dp)) }
            }
            if (error.isNotBlank()) Text(error, fontSize = 13.sp, color = MiuixTheme.colorScheme.error)
            C17TextButton(text = "关闭", onClick = app::dismissClockLaunchPicker, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun C17Pages(app: ModernMainActivity) {
    val homeVisible = app.page == 0 && app.selectedGroup == null && !app.freeNoticeRequired && !app.welcomeOpen &&
        !app.uiSettingsOpen && !app.diagnosticsOpen && !app.iconManagementOpen && !app.customPlugins.opened
    DisposableEffect(app, homeVisible) {
        app.setHomeStatusVisible(homeVisible)
        onDispose { app.setHomeStatusVisible(false) }
    }
    if (app.customPlugins.opened) {
        SideEffect { app.syncModalLayer(app.customPlugins.hasDialog || app.confirmation != null || app.freeNoticeRequired || app.welcomeOpen) }
        CustomPluginsPage(app)
        return
    }
    if (app.uiSettingsOpen) {
        val visible = app.welcomeOpen || app.freeNoticeRequired || app.thanksOpen || app.performanceOpen || app.confirmation != null || app.editing != null || app.statusBarOrderChoice != null || app.colorEditing != null
        SideEffect { app.syncModalLayer(visible) }
        UiSettingsPage(app)
        return
    }
    if (app.diagnosticsOpen) {
        val visible = app.welcomeOpen || app.freeNoticeRequired || app.thanksOpen || app.performanceOpen || app.confirmation != null || app.editing != null || app.statusBarOrderChoice != null || app.colorEditing != null
        SideEffect { app.syncModalLayer(visible) }
        DiagnosticsPage(app)
        return
    }
    if (app.iconManagementOpen) {
        val visible = app.welcomeOpen || app.freeNoticeRequired || app.thanksOpen || app.performanceOpen || app.confirmation != null || app.editing != null || app.statusBarOrderChoice != null || app.colorEditing != null
        SideEffect { app.syncModalLayer(visible) }
        IconManagementPage(app)
        return
    }
    val modalVisible = app.welcomeOpen || app.freeNoticeRequired || app.thanksOpen || app.performanceOpen || app.donationImage != null || app.confirmation != null || app.editing != null || app.statusBarOrderChoice != null || app.colorEditing != null || app.fontCatalogOpen || app.fontScopeSelection != null || app.marqueePickerOpen || app.configScope != null || app.fontRenameTarget != null || app.downloadingFont != null || app.notificationOverridesOpen || (app.wallpaperEdit != null) || app.clockLaunchEditing != null
    SideEffect { app.syncModalLayer(modalVisible) }
    val group = app.selectedGroup?.let { SettingsCatalog.group(it) }
    var requestedSetting by remember { mutableStateOf<String?>(null) }
    val revealedSetting = remember(group?.id, app.category) { requestedSetting }
    if (group?.id == StatusBarLayoutSettings.GROUP) {
        StatusBarOrderPage(app, requestedSetting, onRequestConsumed = { requestedSetting = null })
        return
    }
    val detailPages = remember(group?.id, app.category, app.panelMode, revealedSetting) {
        SettingsCatalog.editorDetailPages(group, app.panelMode, revealedSetting).filter { group?.id != "carrier" || it.id == carrierScene(app.category) }
    }
    var otherDetailPageId by rememberSaveable(group?.id, app.category) {
        mutableStateOf(detailPages.firstOrNull { page -> page.items.any { it.key == requestedSetting } }?.id
            ?: detailPages.firstOrNull()?.id ?: "all")
    }
    val detailPageId = if (group?.id == "network") app.networkDetailPage else otherDetailPageId
    val selectDetailPage: (String) -> Unit = { id ->
        if (group?.id == "network") app.networkDetailPage = id else otherDetailPageId = id
    }
    val activeDetail = detailPages.firstOrNull { it.id == detailPageId } ?: detailPages.firstOrNull()
    val detailMaster = when (group?.id) {
        "carrier" -> carrierPrefix(app.category) + "_enabled"
        "classic_text" -> if (activeDetail?.id == "clock") ClassicTextSettings.CLOCK_MASTER else ClassicTextSettings.CARRIER + "_enabled"
        "clock" -> when (activeDetail?.id) {
            "common" -> null
            "dual" -> ClockLayoutSettings.DUAL_ENABLED
            else -> group.masterKey
        }
        else -> group?.takeUnless { it.independentSwitches }?.masterKey
    }
    val commonItems = SettingsCatalog.commonDetailItems(group).filter { SettingsCatalog.applicable(it, app.values) || it.key == requestedSetting }
    val commonKeys = commonItems.map { it.key }.toSet()
    val detailIconFamily = when (group?.id) {
        "battery" -> "battery"
        "network" -> if (activeDetail?.id == "wifi") "wifi" else null
        "data" -> "cellular"
        "status_hint_icons" -> "hint"
        else -> null
    }
    val detailSections = activeDetail?.items?.filterNot { it.key == detailMaster }
        ?.filterNot { it.key in commonKeys }
        ?.filterNot { group?.id == "font" }
        ?.filter { SettingsCatalog.applicable(it, app.values) || it.key == revealedSetting || it.pairedKey == revealedSetting }
        ?.filter { item -> group?.id != "notification_icons" || when (item.key) {
            NotificationIconArea.TEXT -> app.value(NotificationIconArea.MODE) == "text" || item.key == revealedSetting
            NotificationIconArea.IMAGE_NAME -> app.value(NotificationIconArea.MODE) == "image" || item.key == revealedSetting
            else -> true
        } }?.groupBy { it.section } ?: emptyMap()
    val homeTopBar = rememberC17TopAppBarState()
    val configTopBar = key(app.category, app.query.isNotBlank()) { rememberC17TopAppBarState() }
    val aboutTopBar = rememberC17TopAppBarState()
    val detailTopBar = key(group?.id, activeDetail?.id) { rememberC17TopAppBarState(initialHeightOffsetLimit = 0f) }
    val topBarState = if (group != null) detailTopBar else listOf(homeTopBar, configTopBar, aboutTopBar)[app.page]
    val scroll = rememberC17ScrollBehavior(state = topBarState)
    val title = group?.let { destinationTitle(it, app.category) } ?: listOf("主页", "配置", "关于")[app.page]
    val homeScroll = rememberLazyListState()
    val configScroll = key(app.category, app.query.isNotBlank()) { rememberLazyListState() }
    val aboutScroll = rememberLazyListState()
    val detailScroll = key(group?.id, activeDetail?.id) { rememberLazyListState() }
    val currentScroll = if (group != null) detailScroll else listOf(homeScroll, configScroll, aboutScroll)[app.page]
    val searching = group == null && app.page == 1 && app.query.isNotBlank()
    // A saved state with both clock modes on is shown and run as the default single row; settle the stored switch too.
    val clockConflict = group?.id == "clock" && app.canEdit && app.bool(ClockLayoutSettings.DUAL_ENABLED) &&
        app.bool(FeatureOptions.masterKey(ClockLayoutSettings.STATUS))
    LaunchedEffect(clockConflict) { if (clockConflict) app.save(ClockLayoutSettings.DUAL_ENABLED, false) }
    LaunchedEffect(group?.id, activeDetail?.id, requestedSetting) {
        val requested = requestedSetting ?: return@LaunchedEffect
        if (group == null) return@LaunchedEffect
        val requestedPage = detailPages.firstOrNull { page -> page.items.any { it.key == requested } }
        if (requestedPage != null && requestedPage.id != activeDetail?.id) {
            selectDetailPage(requestedPage.id); return@LaunchedEffect
        }
        if (requested == detailMaster) {
            detailScroll.scrollToItem(0); requestedSetting = null; return@LaunchedEffect
        }
        if (requested in commonKeys) {
            detailScroll.scrollToItem(1); requestedSetting = null; return@LaunchedEffect
        }
        val sectionIndex = detailSections.values.indexOfFirst { items -> items.any { it.key == requested } }
        if (sectionIndex >= 0) {
            val beforeSections = 1 + (if (commonItems.isNotEmpty()) 1 else 0) +
                (if (group.id == "clock" && detailMaster != null) 1 else 0) + (if (detailIconFamily != null) 1 else 0) +
                (if (group.id == "status_hint_icons") 1 else 0)
            detailScroll.scrollToItem(beforeSections + sectionIndex)
            requestedSetting = null
        }
    }
    C17Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            if (group == null) C17TopAppBar(title = title, scrollBehavior = scroll)
            else C17SmallTopAppBar(title = title, scrollBehavior = scroll, navigationIcon = {
                C17IconButton(onClick = app::closeGroup) { Icon(MiuixIcons.Back, "返回") }
            })
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
        if (group != null && detailPages.size > 1) {
            UiContentTabs(tabs = detailPages.map { it.title }, selectedTabIndex = detailPages.indexOf(activeDetail),
                onTabSelected = { requestedSetting = null; selectDetailPage(detailPages[it].id) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth()
                .nestedScroll(scroll.nestedScrollConnection).edgeFade(currentScroll, app.ui.edgeFade),
            state = currentScroll,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = if (searching) 8.dp else 12.dp,
                bottom = if (group == null) maxOf(padding.calculateBottomPadding(), app.navigationHeight.dp) + 12.dp
                    else padding.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(if (searching) 8.dp else 12.dp),
        ) {
            if (group != null) {
                item { DetailIntro(app, group, detailMaster?.takeIf { group.id != "clock" }, SettingsCatalog.detailModeReason(group, activeDetail, app.panelMode)) }
                if (group.id == "status_hint_icons") item {
                    UiGroupCard { UiRow("逐个位置与排序", "每个提示图标分别选左侧、右侧并排列，同状态栏排序一起保存", "进入",
                        enabled = !app.busy) { app.openHintIconOrder() } }
                }
                if (commonItems.isNotEmpty()) item(key = "${group.id}-common") {
                    DetailSettingsSection(app, group, "共用设置", commonItems, revealedSetting)
                }
                if (group.id == "clock" && detailMaster != null) item { ClockModeSwitch(app, detailMaster, activeDetail?.id == "dual") }
                val iconFamily = detailIconFamily
                if (iconFamily != null) item {
                    val state = app.iconManagementState()
                    val assigned = state.overrides.keys.count { IconRoleRegistry.scopeOfRole(it) == iconFamily }
                    UiGroupCard {
                        UiRow("图案与颜色", "本页设置基础位置与尺寸；图标管理可单独调整图案、颜色和缩放", if (assigned > 0) trf("%s 项", assigned) else "选择", Icons.Outlined.Collections,
                            enabled = app.canEdit && !app.busy) { app.openIconAssignments(iconFamily) }
                    }
                }

                detailSections.forEach { (section, items) ->
                    item(key = "${group.id}-$section") {
                        DetailSettingsSection(app, group, section, items, revealedSetting)
                    }
                }
                if (group.id == "font") item { FontSettingsPanel(app) }
                val resetItems = (if (group.id == "carrier" || detailPages.size > 1) activeDetail?.items ?: emptyList() else group.items).distinctBy { it.key }
                val resetTitle = title + if (detailPages.size > 1) " · ${activeDetail?.title}" else ""
                item { UiGroupCard { UiRow(if (detailPages.size > 1) "恢复当前标签默认设置" else "恢复本页默认设置",
                    trf("只重置%s", resetTitle) + if (commonItems.isNotEmpty() && detailPages.size > 1) "，不包含共用设置" else "", icon = MiuixIcons.Reset,
                    enabled = app.canEdit && !app.busy) {
                    app.resetGroup(group, items = resetItems, title = resetTitle)
                }
                    if (commonItems.isNotEmpty() && detailPages.size > 1) UiRow("恢复共用设置默认值", "只重置共用设置，不包含当前标签", icon = MiuixIcons.Reset,
                        enabled = app.canEdit && !app.busy) { app.resetGroup(group, items = commonItems, title = trf("%s · 共用设置", title)) }
                } }
            } else when (app.page) {
                0 -> {
                    item(key = "home-hero") {
                        val heroVisible by remember(currentScroll) { derivedStateOf {
                            val layout = currentScroll.layoutInfo
                            !currentScroll.isScrollInProgress && layout.visibleItemsInfo.any {
                                it.key == "home-hero" && it.offset + it.size > layout.viewportStartOffset && it.offset < layout.viewportEndOffset
                            }
                        } }
                        ActivationHero(app, heroVisible && !modalVisible)
                    }
                    item { SectionTitle("运行状态") }
                    item { UiGroupCard {
                        UiRow("LSPosed", if (app.lspEnabled) "${app.framework.frameworkName} ${app.framework.frameworkVersion}" else app.framework.message.take(42),
                            if (app.lspEnabled) "已启用" else "未连接", Icons.Outlined.Extension) {
                            app.confirmation = UiConfirmation("LSPosed 状态", app.framework.message + if (app.lspEnabled) "\nAPI ${app.framework.apiVersion}" else "", "知道了") {}
                        }
                        UiRow("当前版本", if (app.runtimeActive) "已在系统界面生效" else "等待当前版本注入系统界面",
                            if (app.runtimeActive) "已生效" else "待注入", Icons.Outlined.CheckCircle) {
                            app.confirmation = UiConfirmation("当前版本", app.runtimeMessage, "知道了") {}
                        }
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
                                                destinationStatus(app, destination, app.category),
                                                groupIcon(destination.id)) { app.openGroup(destination.id) }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        val term = app.query.trim()
                        // Search both the shown language and the source text, field by field (tables hold whole fields).
                        fun hit(text: String) = text.contains(term, true) || tr(text).contains(term, true)
                        val matches = SettingsCatalog.groups().mapNotNull { destination ->
                            val matchingItems = SettingsCatalog.searchItems(destination).filter {
                                hit(SettingsCatalog.displayTitle(it, app.values)) || hit(it.description) || hit(it.section) || it.labels.any(::hit)
                            }
                            if (hit(destination.title) || hit(destination.description) || matchingItems.isNotEmpty()) destination to matchingItems else null
                        }
                        matches.forEach { (destination, matchingItems) ->
                            item(key = "search-${destination.id}") {
                                Column {
                                    Text(tr(destination.title), Modifier.padding(start = 16.dp, top = 2.dp, bottom = 6.dp),
                                        fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                    UiGroupCard {
                                        if (matchingItems.isEmpty()) SearchResultRow(tr(destination.title), tr(destination.description), term,
                                            groupIcon(destination.id)) { app.openGroup(destination.id) }
                                        matchingItems.forEach { setting ->
                                            val displayTitle = tr(SettingsCatalog.displayTitle(setting, app.values))
                                            val section = tr(setting.section)
                                            val summary = if (displayTitle.contains(term, true) || section.contains(term, true)) section
                                                else setting.labels.map(::tr).firstOrNull { it.contains(term, true) }
                                                    ?: tr(setting.description).takeIf { it.contains(term, true) } ?: section
                                            SearchResultRow(displayTitle, summary, term, groupIcon(destination.id)) {
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
                        if (matches.isEmpty()) item { Text(tr("没有找到相关配置"), Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary) }
                    }
                }
                2 -> {
                    item { AboutHero(app) }
                    item { AboutActions(app) }
                    item { Text("Copyright © 2026 aiingjie", Modifier.fillMaxWidth().padding(12.dp), fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary) }
                }
            }
        }
        }
    }
}

@Composable
private fun FreeNoticeDialog(app: ModernMainActivity) {
    var value by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val textColor = MiuixTheme.colorScheme.onSurface.toArgb()
    val hintColor = MiuixTheme.colorScheme.onSurfaceVariantSummary.toArgb()
    val confirm = {
        error = app.acceptFreeNotice(value)
        if (error == null) keyboard?.hide()
    }
    C17OverlayDialog(show = true, title = "永久免费声明", onDismissRequest = app::finish) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(FreeNotice.DESCRIPTION, fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Text(tr("鸣谢墙"), Modifier.clip(RoundedCornerShape(8.dp))
                    .clickable { app.openThanks() }.padding(vertical = 4.dp), fontSize = 14.sp, color = MiuixTheme.colorScheme.primary)
                Text(tr("请手动输入以下短句（包含标点）："), fontSize = 14.sp)
                Text(FreeNotice.PHRASE, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                AndroidView(factory = { context ->
                    ManualConfirmationEditText(context).apply {
                        hint = "请在这里手动输入"
                        setTextSize(15f)
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        setPadding(0, 0, 0, 0)
                        setText(value)
                        addTextChangedListener(object : android.text.TextWatcher {
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { value = s?.toString() ?: ""; error = null }
                            override fun afterTextChanged(s: android.text.Editable?) {}
                        })
                        setOnEditorActionListener { _, action, _ ->
                            if (action == android.view.inputmethod.EditorInfo.IME_ACTION_DONE && FreeNotice.matches(value)) { confirm(); true } else false
                        }
                    }
                }, update = { view ->
                    view.setTextColor(textColor); view.setHintTextColor(hintColor)
                    if (view.text.toString() != value) { view.setText(value); view.setSelection(view.text.length) }
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp)
                    .border(1.dp, MiuixTheme.colorScheme.outline, RoundedCornerShape(12.dp)).padding(12.dp))
                Text(tr("此输入框不提供复制、粘贴或拖入功能。"), fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                error?.let { Text(it, color = Color(0xffd94a4a), fontSize = 13.sp) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                C17TextButton(text = "退出", modifier = Modifier.weight(1f).heightIn(min = 48.dp), onClick = app::finish)
                C17TextButton(text = "确认并继续", enabled = FreeNotice.matches(value),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp), onClick = { confirm() })
            }
        }
    }
}

/** Installation and activation are distinct from the result of the latest compilation. */
private fun performanceHomeStatus(app: ModernMainActivity): String = RootPerformanceModule.homeStatus(
    app.performanceReport, app.performanceBusy, app.performanceError, app.performanceAction)

private fun performanceRecordStatus(app: ModernMainActivity): String {
    val module = app.performanceReport
    return when {
        app.performanceBusy && app.performanceAction != RootPerformanceModule.Action.RUN && app.performanceAction != RootPerformanceModule.Action.AUTOMATIC -> "处理中"
        app.performanceError -> "状态待查询"
        module == null || !module.root -> "状态待查询"
        module.remove -> "待卸载"
        module.disabled -> "已停用"
        module.pending -> "待重启"
        module.outdated -> "可更新"
        !module.installed -> "未安装"
        app.performanceBusy && app.performanceAction in listOf(RootPerformanceModule.Action.RUN, RootPerformanceModule.Action.AUTOMATIC) -> "优化中"
        !module.currentBoot && module.lastState != "unknown" -> "历史记录"
        module.recordVersionCode != module.moduleVersionCode && module.lastState != "unknown" -> "旧版记录"
        module.lastState == "waiting" -> "等待适宜时机"
        module.lastState == "running" -> if (module.homeStatus() == "优化中") "优化中" else "尚无有效运行记录"
        module.lastState == "deferred" -> if (module.trigger == "run") "本次未执行" else "自动优化已延期"
        module.lastState == "failed" -> "本次未完成"
        module.lastState == "ok" -> if (module.verification == "verified") "优化完成" else "编译完成，待核验"
        module.lastState == "current" -> if (module.verification == "verified") "沿用已核验结果" else "沿用上次记录"
        else -> "尚无运行记录"
    }
}

@Composable
private fun PerformanceSheet(app: ModernMainActivity) {
    val window = LocalWindowInfo.current.containerDpSize
    val detailScroll = rememberScrollState()
    C17OverlayDialog(show = true, title = RootPerformanceModule.DISPLAY_NAME,
        modifier = Modifier.heightIn(max = (window.height * .86f).coerceAtMost(720.dp)),
        insideMargin = DpSize(18.dp, 18.dp), outsideMargin = DpSize(16.dp, 12.dp),
        onDismissRequest = { app.performanceOpen = false }) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(detailScroll),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PerformanceModuleCard(app)
            }
            C17TextButton(text = "关闭", onClick = { app.performanceOpen = false }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PerformanceModuleCard(app: ModernMainActivity) {
    PerformanceDashboard(app, performanceHomeStatus(app), performanceRecordStatus(app))
}

@Composable
private fun DonationImage(app: ModernMainActivity, asset: String, ratio: Float, modifier: Modifier = Modifier) {
    val bitmap = remember(asset) {
        app.assets.open(asset).use { android.graphics.BitmapFactory.decodeStream(it) }.asImageBitmap()
    }
    Image(bitmap, if (asset.endsWith("wechat.png")) "微信自愿捐赠收款码" else "支付宝自愿捐赠收款码",
        modifier = modifier.fillMaxWidth().aspectRatio(ratio), contentScale = ContentScale.Fit)
}

@Composable
private fun DonationDialog(app: ModernMainActivity, asset: String) {
    val wechat = asset == "donation/wechat.png"
    C17OverlayDialog(show = true, title = if (wechat) "微信 · 自愿捐赠" else "支付宝 · 自愿捐赠",
        summary = "本模块永久免费，捐赠不会解锁或影响任何功能。" + ProjectContact.DONATION_NOTE, onDismissRequest = { app.donationImage = null }) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                DonationImage(app, asset, if (wechat) 1490f / 2030f else 1440f / 2160f)
            }
            C17TextButton(text = "关闭", modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), onClick = { app.donationImage = null })
        }
    }
}

@Composable
private fun ActivationHero(app: ModernMainActivity, motionVisible: Boolean) {
    val homeTime = rememberHomeCardTime(app, motionVisible)
    val companionStatus = performanceHomeStatus(app)
    val companionPalette = homeCardPalette(companionStatus, LocalC17Dark.current)
    val thanksPalette = homeCardPalette("鸣谢墙", LocalC17Dark.current)
    val safe = app.bool(StatusBarSettings.SAFE_MODE)
    val dark = LocalC17Dark.current
    val activated = app.runtimeActive || app.lspEnabled
    val background = when {
        safe -> if (dark) Color(0xff3b2d16) else Color(0xfffff2d9)
        activated -> if (dark) Color(0xff173820) else Color(0xffdffbe4)
        app.rootGranted -> if (dark) Color(0xff182d49) else Color(0xffe8f1ff)
        else -> if (dark) Color(0xff282b31) else Color(0xffeeeff3)
    }
    val accent = when {
        safe -> Color(0xffd79e25)
        activated -> if (dark) Color(0xff46d970) else Color(0xff2fce60)
        app.rootGranted -> MiuixTheme.colorScheme.primary
        else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
    val mainPalette = homeCardPalette(if (safe) "已停用" else if (activated) "已激活" else "待查询", dark)
    val statusTitle = when {
        safe -> "安全模式"
        activated -> "已激活"
        app.rootGranted -> "Root 已授权"
        app.framework.state == AppFrameworkStatus.State.CHECKING -> "检测中"
        else -> "待激活"
    }
    val frameworkInfo = when {
        app.framework.connected -> buildString {
            append(app.framework.frameworkVersion.ifBlank { app.framework.frameworkName })
            if (app.framework.frameworkVersionCode > 0) append(" (${app.framework.frameworkVersionCode})")
            append(" · API ${app.framework.apiVersion}")
        }
        activated -> "系统界面已加载"
        app.rootGranted -> "可独立编辑配置"
        app.framework.state == AppFrameworkStatus.State.CHECKING -> "正在连接框架"
        else -> "点击检测状态"
    }
    // Measure the same label/title slots for both entrances, including the user's font scale.
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val textWidth = ((maxWidth - 12.dp) / 2 - 32.dp).coerceAtLeast(1.dp)
    val thanksTitleWidth = with(density) { textMeasurer.measure("鸣谢墙", C17CardTypography.title).size.width.toDp() }
    // Only the thanks title shares space with the medal. Other cards have no medal reserve.
    val badgeWidth = (textWidth - thanksTitleWidth + 2.dp).coerceIn(32.dp, 42.dp)
    val inlineBadge = textWidth >= thanksTitleWidth + badgeWidth - 2.dp
    val titleEnd = if (inlineBadge) badgeWidth - 2.dp else 0.dp
    val labelConstraints = Constraints(maxWidth = with(density) { textWidth.roundToPx().coerceAtLeast(1) })
    val titleConstraints = Constraints(maxWidth = with(density) { (textWidth - titleEnd).roundToPx().coerceAtLeast(1) })
    val labelHeight = with(density) { maxOf(
        textMeasurer.measure("附属模块", C17CardTypography.label, constraints = labelConstraints).size.height,
        textMeasurer.measure("快点一下看看！", C17CardTypography.label, constraints = labelConstraints).size.height).toDp() }
    val titleHeight = with(density) { maxOf(
        textMeasurer.measure(statusTitle, C17CardTypography.title, constraints = labelConstraints).size.height,
        textMeasurer.measure(companionStatus, C17CardTypography.title, constraints = labelConstraints).size.height,
        textMeasurer.measure("鸣谢墙", C17CardTypography.title, constraints = titleConstraints).size.height).toDp() }
    val thanksLabelWidth = with(density) { textMeasurer.measure("快点一下看看！", C17CardTypography.label,
        constraints = labelConstraints).size.width.toDp() }
    val badgeHeight = badgeWidth * (17f / 13f)
    val textHeight = 28.dp + labelHeight + 3.dp + titleHeight
    val thanksHeight = when {
        !inlineBadge -> textHeight + badgeHeight + 14.dp
        // At narrow widths the medal starts below the label, with its float travel included.
        thanksLabelWidth + 6.dp > textWidth + 8.dp - badgeWidth ->
            maxOf(textHeight, 14.dp + labelHeight + 4.dp + badgeHeight + 10.dp)
        else -> textHeight
    }
    val smallCardHeight = maxOf(84.dp, textHeight, thanksHeight)
    val supportingHeight = with(density) { textMeasurer.measure(frameworkInfo, C17CardTypography.supporting,
        constraints = labelConstraints).size.height.toDp() }
    val rowHeight = maxOf(180.dp, smallCardHeight * 2 + 12.dp, textHeight + supportingHeight)
    // A measured, finite height avoids weighted intrinsic children magnifying the artwork reserve.
    Row(Modifier.fillMaxWidth().height(rowHeight),
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        C17Card(modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(22.dp)),
            cornerRadius = 22.dp, colors = CardDefaults.defaultColors(color = background), onClick = app::checkActivation) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.size(128.dp).align(Alignment.BottomEnd).offset(x = 24.dp, y = 35.dp),
                    contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val stroke = size.width * .075f
                        drawCircle(accent, radius = (size.minDimension - stroke) / 2f,
                            style = Stroke(width = stroke))
                        if (activated && !safe) {
                            val tick = Path().apply {
                                moveTo(size.width * .31f, size.height * .54f)
                                lineTo(size.width * .45f, size.height * .68f)
                                lineTo(size.width * .77f, size.height * .35f)
                            }
                            drawPath(tick, accent, style = Stroke(width = stroke, cap = StrokeCap.Round,
                                join = androidx.compose.ui.graphics.StrokeJoin.Round))
                        }
                    }
                    if (safe || !activated) Icon(if (safe) MiuixIcons.Pause else if (app.rootGranted) MiuixIcons.Unlock else MiuixIcons.Layers,
                        null, Modifier.size(64.dp), tint = accent)
                }
                Column(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(tr("主模块"), Modifier.fillMaxWidth().heightIn(min = labelHeight),
                        style = C17CardTypography.label, color = mainPalette.secondary)
                    Spacer(Modifier.height(3.dp))
                    Text(statusTitle, Modifier.fillMaxWidth().heightIn(min = titleHeight),
                        style = C17CardTypography.title, color = mainPalette.foreground)
                    Text(frameworkInfo, style = C17CardTypography.supporting,
                        color = mainPalette.secondary)
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            C17Card(modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(22.dp)), cornerRadius = 22.dp,
                colors = CardDefaults.defaultColors(color = companionPalette.base), onClick = app::openPerformance) {
                Box(Modifier.fillMaxSize()) {
                HomeCardBackground(companionStatus, homeTime, Modifier.matchParentSize())
                HomeHeroText("附属模块", companionStatus, labelHeight, titleHeight, 0.dp,
                    companionPalette.secondary, companionPalette.foreground)
                }
            }
            C17Card(modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(22.dp)), cornerRadius = 22.dp,
                colors = CardDefaults.defaultColors(color = thanksPalette.base), onClick = app::openThanks) {
                Box(Modifier.fillMaxSize()) {
                HomeCardBackground("鸣谢墙", homeTime, Modifier.matchParentSize(), badgeWidth = badgeWidth)
                HomeHeroText("快点一下看看！", "鸣谢墙", labelHeight, titleHeight, titleEnd,
                    thanksPalette.secondary, thanksPalette.foreground)
                }
            }
        }
    }
    }
}

@Composable
private fun HomeHeroText(label: String, title: String, labelHeight: Dp, titleHeight: Dp,
    titleEnd: Dp, labelColor: Color, titleColor: Color) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(label, Modifier.fillMaxWidth().heightIn(min = labelHeight),
            style = C17CardTypography.label, color = labelColor)
        Spacer(Modifier.height(3.dp))
        Text(title, Modifier.fillMaxWidth().padding(end = titleEnd).heightIn(min = titleHeight),
            style = C17CardTypography.title, color = titleColor)
    }
}

@Composable
private fun ConfigurationHeader(app: ModernMainActivity) {
    val categories = listOf(SettingsCatalog.STATUSBAR, SettingsCatalog.NOTIFICATION, SettingsCatalog.CONTROL_CENTER, SettingsCatalog.LOCK_SCREEN, SettingsCatalog.OTHER)
    val labels = listOf("状态栏", "通知栏", "控制中心", "锁屏", "其他")
    // The tab row takes the search field's real height, so both bars line up.
    val density = LocalDensity.current
    var searchHeight by remember { mutableStateOf<Dp?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        UiContentTabs(tabs = labels, selectedTabIndex = categories.indexOf(app.category).coerceAtLeast(0),
            onTabSelected = { app.category = categories[it] }, modifier = Modifier.fillMaxWidth(), contour = true,
            height = searchHeight)
        C17TextField(value = app.query, onValueChange = { app.query = it }, label = "搜索所有配置", singleLine = true,
            // The label is translated inside C17TextField.
            useLabelAsPlaceholder = true, insideMargin = DpSize(16.dp, 12.dp),
            leadingIcon = { Icon(MiuixIcons.Search, null, Modifier.padding(start = 16.dp, end = 12.dp).size(20.dp)) },
            modifier = Modifier.fillMaxWidth().onSizeChanged { size ->
                if (size.height > 0) with(density) { size.height.toDp() }.let { if (searchHeight != it) searchHeight = it }
            })
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
    C17BasicComponent(insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp), onClick = onClick,
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

/** The default clock and the two-row clock exclude each other: switching one on switches the other off. */
@Composable
private fun ClockModeSwitch(app: ModernMainActivity, key: String, dual: Boolean) {
    UiGroupCard {
        UiSwitchRow(if (dual) "启用双排时钟" else "启用默认时钟", if (dual) "开启后自动关闭默认时钟" else "开启后自动关闭双排时钟",
            app.bool(key), app.canEdit && !app.busy && PanelMode.freezeReason(key).isEmpty()) { app.save(key, it) }
    }
}

/** One short line for a row: the first sentence, cut at a comma if it is still long. The full text lives in 详细说明. */
private fun brief(text: String, limit: Int = 30): String {
    val sentence = text.substringBefore('\n').split('。', '；').firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    if (sentence.length <= limit) return sentence
    val cut = sentence.substring(0, limit).lastIndexOf('，')
    return if (cut >= 8) sentence.substring(0, cut) else sentence.substring(0, limit).trimEnd('，', '、', ' ') + "…"
}

/** Everything a page can say, for the 详细说明 dialog: the page text, how it interacts with others, then each item. */
private fun fullHelp(app: ModernMainActivity, group: SettingsCatalog.Group, description: String, conflicts: String): String {
    val lines = ArrayList<String>()
    lines.add(description)
    if (conflicts.isNotBlank()) lines.add(conflicts)
    val items = group.items.filter { !it.master && it.description.isNotBlank() && SettingsCatalog.applicable(it, app.values) }
        .filter { it.description != brief(it.description) }
    if (items.isNotEmpty()) lines.add("各项说明：\n" + items.joinToString("\n") { "· ${it.title.substringBefore(" ·")}：${it.description}" })
    return lines.joinToString("\n\n")
}

@Composable
private fun DetailIntro(app: ModernMainActivity, group: SettingsCatalog.Group, master: String?, modeReason: String) {
    val description = if (group.id == "carrier") trf("只作用于%s，可显示运营商、时间或自定义文本。", when (app.category) {
        SettingsCatalog.LOCK_SCREEN -> tr("锁屏")
        SettingsCatalog.CONTROL_CENTER -> tr("控制中心")
        else -> tr("通知栏")
    }) else if (group.id == "notification_icons" &&
        app.value(NotificationIconArea.MODE) == "image" && (app.value(NotificationIconArea.IMAGE_REVISION) as? String).isNullOrBlank())
        group.description + "\n尚未导入图片，当前会保留原生通知图标。" else group.description
    val conflicts = SettingsCatalog.conflictDescription(group.id, app.category)
    val dependency = group.masterKey?.takeIf { it.isNotBlank() }?.let { SettingsCatalog.unavailableReason(it, app.values) }.orEmpty()
    val modeLabel = when (app.panelMode) { PanelMode.CLASSIC -> "当前系统：经典模式"; PanelMode.SEPARATE -> "当前系统：分离模式"; else -> "当前系统：模式尚未确认" }
    val short = brief(description, 56)
    val help = fullHelp(app, group, description, conflicts)
    val more = help.length > short.length + 2
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
        .background(MiuixTheme.colorScheme.primary.copy(alpha = .055f)).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(short, fontSize = 14.sp, lineHeight = 20.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        val guide = SettingsOrganization.guide(group.id)
        if (guide.isNotEmpty()) Text(guide, fontSize = 13.sp, lineHeight = 19.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        if (app.bool(StatusBarSettings.SAFE_MODE)) Text(tr("已暂停：安全模式已开启，以下显示保存的选择。"), fontSize = 13.sp,
            lineHeight = 19.sp, color = MiuixTheme.colorScheme.primary)
        if (group.id.startsWith("shade_wallpaper"))
            Text(tr("功能未完成"), fontSize = 13.sp, color = MiuixTheme.colorScheme.primary)
        if (group.id == "fluid_cloud_capsule")
            Text(tr("外观开关仅控制外观；常驻流体云在共用设置中独立开关。"), fontSize = 13.sp, lineHeight = 19.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        if (group.id == "classic_text" || group.id.startsWith("shade_wallpaper") || group.id.startsWith("notification_big_clock"))
            Text(modeLabel, fontSize = 13.sp, color = MiuixTheme.colorScheme.primary)
        if (modeReason.isNotEmpty()) Text(modeReason, fontSize = 13.sp, lineHeight = 19.sp, color = MiuixTheme.colorScheme.primary)
        if (dependency.isNotBlank()) Text(dependency, fontSize = 13.sp, lineHeight = 19.sp, color = MiuixTheme.colorScheme.primary)
        // Permanent source line of the same-app mini stack; the whole line opens the author's Coolapk page.
        if (group.id == MINI_STACK_GROUP) Text("${NotificationMiniStackSource.INSIDE} · ${NotificationMiniStackSource.LINK}",
            Modifier.clip(RoundedCornerShape(8.dp)).clickable { app.openUrl(NotificationMiniStackSource.URL) }.padding(vertical = 4.dp),
            fontSize = 13.sp, lineHeight = 19.sp, color = MiuixTheme.colorScheme.primary)
        if (more) Text(tr("详细说明"), Modifier.clip(RoundedCornerShape(8.dp))
            .clickable { app.confirmation = UiConfirmation(group.title, help, "知道了") {} }.padding(vertical = 4.dp),
            fontSize = 13.sp, color = MiuixTheme.colorScheme.primary)
    }
    master?.let { key -> SettingsCatalog.item(key)?.let { item -> UiGroupCard { SettingsItem(app, item) } } }
    if (!app.canEdit) UiGroupCard { UiRow("激活模块或授予 Root 后即可修改", "点击前往主页", icon = MiuixIcons.Unlock) { app.closeGroup(); app.selectPage(0) } }
    }
}

@Composable
private fun DetailSettingsSection(app: ModernMainActivity, group: SettingsCatalog.Group, section: String,
    items: List<SettingsCatalog.Item>, requested: String?) {
    val advanced = SettingsOrganization.advanced(group.id, section)
    val switches = items.filter { it.type == SettingsCatalog.BOOLEAN }
    val parameters = items.filterNot { it.type == SettingsCatalog.BOOLEAN }
    val parameterCount = parameters.count { !(it.type == SettingsCatalog.COLOR && it.scene == "dark" &&
        parameters.any { other -> other.key == it.pairedKey }) && !FluidCloudCapsule.MARQUEE_COLOR_KEYS.drop(1).contains(it.key) }
    val folding = parameterCount >= 3 || advanced && parameterCount >= 2
    var expanded by rememberSaveable(group.id, section) { mutableStateOf((!advanced && (switches.firstOrNull()?.let { app.bool(it.key) } ?: true)) || items.any { it.key == requested }) }
    LaunchedEffect(requested) { if (items.any { it.key == requested }) expanded = true }
    Column {
        SectionTitle(section.substringAfter(" · "))
        UiGroupCard {
            switches.forEach { item -> SettingsItem(app, item) }
            if (folding) UiRow(if (advanced) "高级参数" else "参数设置", trf("%s 项 · 展开或收起不改变功能开关", parameterCount), if (expanded) "收起" else "展开") { expanded = !expanded }
            if (!folding || expanded) parameters.forEach { item ->
                val paired = item.type == "color" && item.pairedKey.isNotEmpty() && items.any { it.key == item.pairedKey }
                if (paired && item.scene == "light") {
                    val dark = SettingsCatalog.item(item.pairedKey)
                    val componentAvailable = BigClockWidgetsSettings.textSide(item.key) < 0 || SettingsCatalog.unavailableReason(item.key, app.values).isEmpty()
                    val pairUnavailable = if (group.id.startsWith("notification_big_clock"))
                        SettingsCatalog.unavailableReason(item.key, app.values).ifEmpty { SettingsCatalog.unavailableReason(dark.key, app.values) }
                        else ""
                    UiColorPair(item.title.substringBefore(" ·"),
                        (app.value(item.key) as? Number)?.toInt() ?: 0,
                        (app.value(dark.key) as? Number)?.toInt() ?: 0,
                        app.canEdit && !app.busy && componentAvailable && pairUnavailable.isEmpty(),
                        onLight = { app.pickColor(item) }, onDark = { app.pickColor(dark) })
                    val pairNote = pairUnavailable.ifEmpty { SettingsCatalog.inactivePresentationReason(item, app.values) }
                    if (pairNote.isNotEmpty()) Text(pairNote, Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        fontSize = 13.sp, lineHeight = 19.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                } else if (!paired || item.scene != "dark") SettingsItem(app, item)
            }
        }
    }
}

@Composable
private fun SettingsItem(app: ModernMainActivity, item: SettingsCatalog.Item) {
    val unavailable = SettingsCatalog.unavailableReason(item.key, app.values)
    val paused = SettingsCatalog.inactivePresentationReason(item, app.values)
    // The switch always shows saved intent, even while a dependency pauses its runtime effect.
    val value = if (item.type == "numeric") SettingEditor.storedValue(item, app.values) else app.value(item.key)
    val enabled = app.canEdit && !app.busy && PanelMode.freezeReason(item.key).isEmpty() && (unavailable.isEmpty() || value == true && item.type == SettingsCatalog.BOOLEAN)
    if (item.key == "label_enabled") {
        val mode = NetworkDisplaySettings.mode(app.values)
        val label = when (mode) { NetworkDisplaySettings.TEXT -> "文字"; NetworkDisplaySettings.HIDDEN -> "隐藏"; else -> "原生角标" }
        UiRow("制式显示方式", "文字关闭后转为原生角标；隐藏需单独选择", label,
            enabled = app.canEdit && !app.busy) { app.editing = item }
        return
    }
    if (item.key == NativeStatusIcons.LAYOUT) {
        UiRow(item.title, brief(item.description), "进入", enabled = !app.busy) { app.openHintIconOrder() }; return
    }
    if (item.key == NativeStatusIcons.PRIORITY) {
        UiRow(item.title, brief(item.description), "拖拽排序", Icons.Outlined.SwapVert, enabled = enabled) { app.editing = item }
        return
    }
    if (StatusBarLayoutSettings.orderKey(item.key)) {
        if (item.key == StatusBarLayoutSettings.key(StatusBarLayoutSettings.LABEL, "order")) {
            if (app.bool(StatusBarLayoutSettings.WIFI_LABEL_SHARED)) {
                UiRow(item.title, "在 Wi-Fi 所在区域调整共用顺序", "与 Wi-Fi 共用", enabled = false) {}
                return
            }
            val order = (value as? Number)?.toInt() ?: 0
            UiRow(item.title, "跟随蜂窝所在区域", listOf("跟随系统", "信号前", "信号后").getOrElse(order) { "跟随系统" },
                enabled = enabled) { app.editing = item }
        } else {
            val order = (value as? Number)?.toInt() ?: 0
            UiRow(item.title, "同一侧按优先级排列，同级由系统决定", if (order == 0) "系统顺序" else trf("优先级 %s", order),
                Icons.Outlined.SwapVert, enabled = enabled) { app.editing = item }
        }
        return
    }
    if (SettingEditor.appPickerKey(item.key)) {
        val selectedTitle = remember(item.key, value, app.clockLaunchChoices) { app.clockLaunchLabel(item.key) }
        UiRow(item.title, brief(item.description), selectedTitle, Icons.Outlined.Apps,
            enabled = enabled && app.clockLaunchEnabled(item.key)) { app.openClockLaunchPicker(item.key) }
        return
    }
    if (item.key == IconManagementSettings.KEY) {
        UiRow("图标管理", "图标库、套装与独立外观", "进入", Icons.Outlined.Collections, enabled = !app.busy) { app.openIconManagement() }
        return
    }
    ShadeWallpaperSettings.sceneOfRevisionKey(item.key)?.let { scene ->
        ShadeWallpaperCard(app, scene)
        return
    }
    if (item.key == NotificationIconOverrides.RULES) {
        UiRow(item.title, "按应用替换为文字、表情或图标", "进入", Icons.Outlined.EmojiEmotions, enabled = enabled) { app.openNotificationOverrides() }
        return
    }
    if (item.key == QsWifiAlias.RULES) {
        val count = runCatching { QsWifiAlias.parse(value?.toString() ?: "{}").size }.getOrDefault(0)
        UiRow(item.title, item.description, if (count == 0) "选择网络" else trf("%s 个网络", count), Icons.Outlined.Wifi, enabled = enabled) { app.editing = item }
        return
    }
    if (item.key == FluidCloudCapsule.MARQUEE_PRESET) {
        MarqueePresetRow(app, item, enabled, unavailable)
        return
    }
    if (item.key == FluidCloudCapsule.MARQUEE_COLOR_1) {
        MarqueeColorsRow(app, enabled, unavailable)
        return
    }
    // 颜色 2 到 6 和颜色 1 合并成一排圆点，不再各占一行。
    if (FluidCloudCapsule.MARQUEE_COLOR_KEYS.drop(1).contains(item.key)) return
    if (item.key == StatusBarSettings.FONT_MODE) {
        UiRow("文字来源", "", app.currentFontTitle(), Icons.Outlined.TextFields,
            enabled = !app.busy) { app.chooseOpenFont() }
        return
    }
    when (item.type) {
        "boolean" -> UiSwitchRow(item.title, if (unavailable.isNotEmpty() || paused.isNotEmpty())
            (if (value == true) "已保存开启 · " else "") + unavailable.ifEmpty { paused } else brief(item.description), value == true, enabled) { app.save(item.key, it) }
        "numeric" -> {
            val editingKey = StatusBarLayoutSettings.editingKey(item.key, app.values)
            var current by remember(item.key, editingKey, value) { mutableStateOf<Number>((value as? Number) ?: 0f) }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(SettingsCatalog.displayTitle(item, app.values), Modifier.weight(1f).clickable(enabled = enabled) { app.editing = item }, fontSize = 16.sp)
                    Text(SettingEditor.formatted(item, current), modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = enabled) { app.editing = item }.padding(horizontal = 8.dp, vertical = 6.dp), fontSize = 14.sp, color = MiuixTheme.colorScheme.primary)
                }
                C17Slider(value = current.toFloat().coerceIn(item.min, item.max), onValueChange = {
                    current = if (item.key == "qs_tile_corner_radius") kotlin.math.round(it * 2f) / 2f else roundToStep(it, item.step)
                }, valueRange = item.min..item.max, enabled = enabled, onValueChangeFinished = {
                    app.save(item.key, current)
                })
                val note = unavailable.ifEmpty { paused }.ifEmpty {
                    if (StatusBarLayoutSettings.sharedPositionKey(item.key) && item.groupId == "network" &&
                        app.bool(StatusBarLayoutSettings.WIFI_LABEL_SHARED)) "Wi-Fi 与网络制式共用位置，大小各自独立"
                    else brief(item.description)
                }
                if (note.isNotEmpty()) Text(note, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        }
        "color" -> C17ArrowPreference(title = item.title.substringBefore(" ·"), summary = null, enabled = enabled,
            onClick = { app.pickColor(item) }, endActions = {
                Box(Modifier.size(30.dp).clip(CircleShape).background(Color((value as? Number)?.toInt() ?: 0))
                    .border(0.75.dp, MiuixTheme.colorScheme.dividerLine, CircleShape))
            })
        "font" -> {
            UiRow("导入字体", "TTF / OTF / TTC", "导入", Icons.Outlined.FileUpload,
                enabled = app.canEdit && !app.busy) { app.chooseFont() }
        }
        "image" -> UiRow(item.title, brief(item.description), value?.toString()?.takeIf { it.isNotBlank() }
            ?: if ((app.value(NotificationIconArea.IMAGE_REVISION) as? String).isNullOrBlank()) "选择图片" else "已导入图片",
            icon = Icons.Outlined.Image, enabled = enabled) { app.chooseNotificationIcon() }
        else -> UiRow(item.title, unavailable.ifEmpty { paused }.ifEmpty { brief(item.description) }, displayValue(item, value), enabled = enabled) { app.editing = item }
    }
}

@Composable
private fun EditSettingDialog(app: ModernMainActivity, item: SettingsCatalog.Item) {
    if (item.key == "label_enabled") {
        UiChoiceDialog("制式显示方式", listOf(NetworkDisplaySettings.TEXT, NetworkDisplaySettings.BADGE, NetworkDisplaySettings.HIDDEN),
            listOf("文字", "原生角标", "隐藏"), NetworkDisplaySettings.mode(app.values),
            description = "选择原生角标会关闭独立制式文字；隐藏会隐藏制式文字和原生角标。各自位置、大小、字体与颜色参数继续保留。",
            onDismiss = { app.editing = null }, onChoice = { if (app.saveNetworkDisplayMode(it)) app.editing = null })
        return
    }
    if (item.key == QsWifiAlias.RULES) { WifiAliasRulesDialog(app); return }
    if (item.key == NativeStatusIcons.LAYOUT) { LaunchedEffect(item.key) { app.editing = null; app.openHintIconOrder() }; return }
    if (item.key == IconManagementSettings.KEY) { LaunchedEffect(item.key) { app.editing = null; app.openIconManagement() }; return }
    if (item.key == NotificationIconOverrides.RULES) { LaunchedEffect(item.key) { app.editing = null; app.openNotificationOverrides() }; return }
    if (item.key == NativeStatusIcons.PRIORITY) { HintPriorityDialog(app); return }
    if (StatusBarLayoutSettings.orderKey(item.key)) {
        if (item.key == StatusBarLayoutSettings.key(StatusBarLayoutSettings.LABEL, "order")) {
            val selected = (app.value(item.key) as? Number)?.toInt() ?: 0
            UiChoiceDialog(title = "制式与信号顺序", values = listOf("0", "1", "2"),
                labels = listOf("跟随系统", "在信号前", "在信号后"), selected = selected.toString(),
                description = "网络制式跟随蜂窝区域；不会强制显示被系统隐藏的制式。",
                onDismiss = { app.editing = null }, onChoice = { app.save(item.key, it.toFloat()) })
        } else LaunchedEffect(item.key) { app.editing = null; app.openGroup(StatusBarLayoutSettings.GROUP) }
        return
    }
    val editor = remember(item.key, app.values) { SettingEditor.forItem(item, app.values) }
    val displayTitle = SettingsCatalog.displayTitle(item, app.values)
    if (item.type == "options") {
        UiChoiceDialog(displayTitle, item.values.toList(), item.labels.toList(), app.value(item.key)?.toString() ?: "",
            description = editor.context + if (item.description.isBlank()) "" else "\n${item.description}",
            optionDisabledReason = { app.optionUnavailableReason(item.key, it) },
            onDismiss = { app.editing = null }) {
            if (it == "custom" && (item.key == StatusBarSettings.FONT_MODE || NativeNetworkBadgeSettings.STRINGS.containsKey(item.key) || item.key == NotificationBigClockSettings.FONT || item.key == NotificationBigClockSettings.landscapeKey(NotificationBigClockSettings.FONT))
                && !ConfigTransfer.customFontAvailable(app)) {
                app.toast("请先在文字字体页面导入自选字体")
            } else app.save(item.key, it)
            app.editing = null
        }
    } else UiInputDialog(displayTitle, editor.initial, editor.description, editor.numeric,
        label = editor.label, context = editor.context, valueSummary = editor.valueSummary,
        integer = editor.integer, allowNegative = !editor.integer && item.key != QsTileCorners.RADIUS,
        confirmLabelForValue = { input ->
            if (editor.numeric) {
                val proposed = runCatching { SettingsCatalog.customNumber(item, input) }.getOrNull()
                if (proposed != null && SettingsCatalog.requiresNumericTrial(item, proposed)) "试用 20 秒" else "确定"
            } else "确定"
        },
        pattern = editor.pattern, preview = if (editor.previewable) { input -> SettingEditor.preview(item, input) } else null,
        presets = remember(item.key) { SettingEditor.presets(item).map { it[0] to it[1] } },
        onDismiss = { app.editing = null }) { input ->
        val result: Any = if (item.type == "numeric") {
            try { SettingsCatalog.customNumber(item, input) } catch (error: IllegalArgumentException) { return@UiInputDialog error.message ?: "请输入有效的有限数值" }
        } else input
        val error = SettingsCatalog.validationError(item, result)
        if (error != null) error else {
            if (result is Float && SettingsCatalog.requiresNumericTrial(item, result)) app.tryNumber(item, result)
            else { app.save(item.key, result); app.editing = null }
            null
        }
    }
}

@Composable
private fun AboutHero(app: ModernMainActivity) {
    val version = remember(app) { app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "" }
    val versionLabel = remember(version) {
        // 2.1.0 names its builds "2.1.0_beta.081"; older ones used "1.8.3-beta.95".
        val suffix = version.substringBefore('+').replace('_', '-').substringAfter('-', "").lowercase(Locale.ROOT)
        if (suffix.split('.', '-').any { it.startsWith("alpha") || it.startsWith("beta") || it.startsWith("rc") }) "测试版" else "正式版"
    }
    UiGroupCard {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val iconPx = with(LocalDensity.current) { 96.dp.roundToPx() }
            val icon = remember(app, iconPx) { drawableBitmap(app.packageManager.getApplicationIcon(app.packageName), iconPx) }
            EasterEggIcon(icon, app.appDisplayName, Modifier.size(56.dp))
            Spacer(Modifier.height(14.dp))
            Text(app.appDisplayName, fontSize = 22.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text("$version · $versionLabel", fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Spacer(Modifier.height(6.dp))
            Text(trf("作者 %s · 永久免费，捐赠自愿", ProjectContact.AUTHOR), fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}

@Composable
private fun AboutActions(app: ModernMainActivity) {
    var section by rememberSaveable { mutableStateOf<String?>(null) }
    fun toggle(name: String) { app.cancelPendingHistoricalCleanup(); section = if (section == name) null else name }
    fun fold(name: String) = if (section == name) "收起" else "展开"
    val language = C17Language.current(app)
    val languageName = C17Language.OPTIONS.firstOrNull { it.tag == language }?.nativeName
        ?: stringResource(R.string.welcome_language_follow_system)
    // Three groups instead of a card per entry: everyday entries, upkeep, then the project. Every former action is kept;
    // the donation note lives in the free notice and the component licences in "开源许可".
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("常用")
        UiGroupCard {
            UiRow("扩展插件", "导入插件，扩展功能", "插件", Icons.Outlined.Extension) { app.openCustomPlugins() }
            UiRow("附属优化模块", "安装与自动优化", performanceHomeStatus(app), Icons.Outlined.Speed) { app.openPerformance() }
            UiRow("界面设置", "风格、配色、字号、名称", app.ui.uiStyleTitle, Icons.Outlined.Palette) { app.openUiSettings() }
            UiRow(stringResource(R.string.about_language), stringResource(R.string.about_language_summary), languageName,
                Icons.Outlined.Language) { app.openWelcome(languageOnly = true) }
        }
        SectionTitle("维护")
        UiGroupCard {
            UiRow("配置管理", "备份、分享、导入", value = fold("config"), icon = Icons.Outlined.Settings) { toggle("config") }
            if (section == "config") {
                UiRow("导出配置", "保存为文件，可选择只导出部分页面", icon = Icons.Outlined.FileUpload, enabled = app.canEdit && !app.busy) { app.chooseExport() }
                UiRow("分享配置", "发给朋友或其他应用，可选择只分享部分页面", icon = Icons.Outlined.Share, enabled = app.canEdit && !app.busy) { app.shareConfig() }
                UiRow("导入配置", "读取文件后可选择只导入部分页面", icon = Icons.Outlined.FileDownload, enabled = app.canEdit && !app.busy) { app.chooseImport() }
                UiRow("删除所有配置", "不可恢复；设置导出不含字体、图标和插件布局", icon = Icons.Outlined.DeleteOutline, enabled = app.canEdit && !app.busy) { app.deleteAllSettings() }
                UiRow("历史配置残留清除", "可能误伤：连同当前配置、插件和导入资源一起清除，需 Root", icon = Icons.Outlined.DeleteOutline, enabled = !app.freeNoticeRequired && !app.busy && !app.maintenanceRunning) { app.clearHistoricalResiduals() }
            }
            UiRow("问题诊断", "日志与挂钩状态", "查看", Icons.Outlined.Description) { app.openDiagnostics() }
            UiRow("检查更新", app.updateMessage, if (app.checkingUpdates) "检查中" else "检查", Icons.Outlined.SystemUpdate,
                enabled = !app.checkingUpdates && !app.busy) { app.checkUpdates() }
        }
        SectionTitle("关于")
        UiGroupCard {
            UiRow(stringResource(R.string.about_welcome), stringResource(R.string.about_welcome_summary),
                icon = Icons.Outlined.Info) { app.openWelcome() }
            UiRow("联系与捐赠", value = fold("contact"), icon = Icons.Outlined.FavoriteBorder) { toggle("contact") }
            if (section == "contact") {
                UiRow("联系作者", "QQ ${ProjectContact.QQ}", "复制", Icons.Outlined.ContentCopy) { app.toast(if (ProjectContact.copyQq(app)) "QQ号已复制" else "复制失败") }
                UiRow("酷安", trf("作者主页 @%s", ProjectContact.COOLAPK), "打开", Icons.Outlined.OpenInNew) { app.openUrl(ProjectContact.COOLAPK_URL) }
                UiRow("Telegram 群组", "交流使用问题、反馈错误、获取更新消息", "加入", Icons.Outlined.OpenInNew) { app.openUrl(ProjectContact.TELEGRAM_URL) }
                // App292: two networks with different addresses; the X Layer entry is retired.
                for ((network, address) in listOf(ProjectContact.CRYPTO_ETH_NETWORK to ProjectContact.CRYPTO_ETH_ADDRESS,
                        ProjectContact.CRYPTO_TRON_NETWORK to ProjectContact.CRYPTO_TRON_ADDRESS)) {
                    UiRow(trf("加密货币捐赠 · %s", network), address, "复制", Icons.Outlined.ContentCopy) {
                        app.toast(if (ProjectContact.copyCryptoAddress(app, address)) trf("%s 地址已复制", network) else "复制失败")
                    }
                }
                UiRow("微信捐赠", value = "查看", icon = Icons.Outlined.FavoriteBorder) { app.donationImage = "donation/wechat.png" }
                UiRow("支付宝捐赠", value = "查看", icon = Icons.Outlined.FavoriteBorder) { app.donationImage = "donation/alipay.jpg" }
                UiRow("免费声明", "永久免费，捐赠自愿", icon = Icons.Outlined.Info) { app.confirmation = UiConfirmation("免费声明", FreeNotice.DESCRIPTION, "知道了") {} }
            }
            UiRow("鸣谢墙", "感谢每一位伙伴", "查看", Icons.Outlined.FavoriteBorder) { app.openThanks() }
            UiRow("项目与开源", value = fold("project"), icon = Icons.Outlined.Code) { toggle("project") }
            if (section == "project") {
                UiRow("项目源码", "SANWU5/c17-statusbar", icon = Icons.Outlined.Code) { app.openUrl("https://github.com/SANWU5/c17-statusbar") }
                UiRow("正式发布", icon = Icons.Outlined.OpenInNew) { app.openLatestRelease() }
                UiRow("开源许可", icon = Icons.Outlined.Article) {
                    app.confirmation = UiConfirmation("开源许可", "C17 Statusbar：GPL-3.0-only\nMiuix：Apache-2.0\nCOUI：suqi8/coui · Apache-2.0（基于 Miuix）\nMaterial 3：Google Jetpack Compose Material 3 · Apache-2.0\n液态玻璃底栏：改编自 Miuix 示例与 Kyant0/AndroidLiquidGlass · Apache-2.0\n字体：SIL Open Font License 1.1\n圆角技术参考：MCGA · Zhuangzhi Meng (Gustate XiaoMeng) · GPL-3.0-or-later\n完整许可与版权声明随应用保留。", "知道了") {}
                }
                UiRow("功能鸣谢", NotificationMiniStackSource.SHORT, "酷安", Icons.Outlined.OpenInNew) {
                    app.confirmation = UiConfirmation("功能鸣谢", NotificationMiniStackSource.DECLARATION + "\n\n" + NotificationMiniStackSource.TECHNICAL_ORIGIN
                        + "鸣谢不代表许可声明。", "知道了", linkLabel = trf("%1\$s（酷安 @%2\$s）", NotificationMiniStackSource.LINK, NotificationMiniStackSource.AUTHOR),
                        linkUrl = NotificationMiniStackSource.URL) {}
                }
            }
        }
        UiGroupCard {
            UiSwitchRow("隐藏桌面图标", "可从 LSPosed 模块设置进入并恢复", app.desktopIconHidden,
                enabled = app.desktopIconAvailable && !app.freeNoticeRequired && !app.busy && !app.maintenanceRunning,
                onChecked = app::requestDesktopIconHidden)
        }
    }
}

@Composable
private fun HintPriorityDialog(app: ModernMainActivity) {
    val saved = app.value(NativeStatusIcons.PRIORITY)?.toString() ?: ""
    var ordered by remember(saved) { mutableStateOf(NativeStatusIcons.editablePriority(saved).toList()) }
    var dragging by remember { mutableStateOf<String?>(null) }
    val density = LocalDensity.current
    val step = with(density) { 56.dp.toPx() }
    val move: (String, Int) -> Boolean = { slot, delta ->
        val index = ordered.indexOf(slot)
        val target = (index + delta).coerceIn(0, ordered.lastIndex)
        if (index < 0 || index == target) false else {
            ordered = ordered.toMutableList().apply { removeAt(index); add(target, slot) }
            true
        }
    }
    C17OverlayDialog(show = true, title = "显示优先级", onDismissRequest = { app.editing = null }) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr("长按图标名称后上下拖动，靠前的优先显示。系统当前未启用的提示不会被强制显示。"), fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState(), enabled = dragging == null)) {
                ordered.forEach { slot -> key(slot) {
                    var distance by remember { mutableFloatStateOf(0f) }
                    Row(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (dragging == slot) MiuixTheme.colorScheme.primary.copy(alpha = .12f) else Color.Transparent)
                        .semantics { customActions = listOf(CustomAccessibilityAction("上移") { move(slot, -1) }, CustomAccessibilityAction("下移") { move(slot, 1) }) }
                        .pointerInput(slot) {
                            detectDragGesturesAfterLongPress(onDragStart = { dragging = slot; distance = 0f },
                                onDragEnd = { dragging = null }, onDragCancel = { dragging = null }) { change, amount ->
                                change.consume(); distance += amount.y
                                while (distance >= step) { if (!move(slot, 1)) { distance = 0f; break }; distance -= step }
                                while (distance <= -step) { if (!move(slot, -1)) { distance = 0f; break }; distance += step }
                            }
                        }.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(NativeStatusIcons.priorityLabel(slot), Modifier.weight(1f), fontSize = 16.sp)
                        Icon(Icons.Outlined.DragHandle, "长按拖动", Modifier.size(22.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                C17TextButton(text = "跟随系统", modifier = Modifier.weight(1f), onClick = { app.save(NativeStatusIcons.PRIORITY, ""); app.editing = null })
                C17TextButton(text = "确定", modifier = Modifier.weight(1f), onClick = { app.save(NativeStatusIcons.PRIORITY, ordered.joinToString(",")); app.editing = null })
            }
        }
    }
}

@Composable private fun SectionTitle(title: String) {
    if (c17Material) UiSectionHeader(title)
    else Text(title, Modifier.padding(start = 12.dp, top = 8.dp, bottom = 2.dp), fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
}
private fun concise(description: String) = brief(description, 34)
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
private fun destinationStatus(app: ModernMainActivity, group: SettingsCatalog.Group, category: String): String = when {
    app.bool(StatusBarSettings.SAFE_MODE) && destinationEnabled(app, group, category) -> "安全模式暂停"
    group.id == "status_icon_library" -> if (destinationEnabled(app, group, category)) "已配置" else "跟随系统"
    destinationEnabled(app, group, category) -> "已开启"
    else -> "已关闭"
}
private fun destinationEnabled(app: ModernMainActivity, group: SettingsCatalog.Group, category: String? = null): Boolean =
    when (group.id) {
        "status_icon_library" -> app.iconManagementState().let { it.scopePacks.isNotEmpty() || it.overrides.isNotEmpty() || it.appearances.isNotEmpty() || it.animations.isNotEmpty() }
        "carrier" -> if (category != null) app.bool(carrierPrefix(category) + "_enabled")
            else CarrierPanels.GROUPS.any { app.bool(it + "_enabled") }
        "network" -> app.bool("wifi_enabled") || app.bool(NetworkDisplaySettings.WIFI_HIDDEN) ||
            NetworkDisplaySettings.mode(app.values) != NetworkDisplaySettings.BADGE ||
            app.bool(NativeNetworkBadgeControls.MASTER) || app.bool(NativeDataActivity.MASTER) || app.bool(StatusBarLayoutSettings.WIFI_LABEL_SHARED)
        "notification_clear_landscape" -> app.bool(group.masterKey) || app.bool(NotificationClearAppearance.LANDSCAPE_MASTER)
        "classic_text" -> app.bool(group.masterKey) || app.bool(ClassicTextSettings.CLOCK_MASTER)
        "clock" -> app.bool(group.masterKey) || app.bool(ClockLayoutSettings.DUAL_ENABLED)
        "fluid_cloud_style" -> app.bool(FluidCloudStyle.MASTER) && (app.bool(FluidCloudStyle.FOLLOW_NOTIFICATION) || app.bool(FluidCloudStyle.DESKTOP_BACKGROUND))
        "fluid_cloud_capsule" -> app.bool(group.masterKey) || app.bool(CapsulePersistence.ENABLED)
        else -> group.masterKey?.takeIf { it.isNotBlank() }?.let(app::bool) ?: false
    }
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
    "network", "wifi", QsWifiAlias.GROUP -> Icons.Outlined.Wifi
    "label" -> C17SectionIcons.Network5G
    "native_network_badge" -> Icons.Outlined.TextFields
    "network_order", StatusBarLayoutSettings.GROUP -> Icons.Outlined.SwapHoriz
    "clock", "shade_clock", "notification_big_clock", "notification_big_clock_landscape", "lockscreen_date", "classic_text" -> Icons.Outlined.Schedule
    "lockscreen_lock_icon" -> Icons.Outlined.Lock
    "lockscreen_status_blur" -> Icons.Outlined.BlurOn
    "fluid_cloud_accent", "fluid_cloud_style", "fluid_cloud_capsule" -> Icons.Outlined.Palette
    "carrier" -> Icons.Outlined.CellTower
    "font" -> Icons.Outlined.TextFields
    "battery" -> Icons.Outlined.BatteryFull
    "tiles" -> Icons.Outlined.Swipe
    "qs_appearance" -> Icons.Outlined.Palette
    "tile_2x1_fill" -> Icons.Outlined.ViewModule
    "tile_corners" -> Icons.Outlined.RoundedCorner
    "tile_icon_size" -> Icons.Outlined.GridView
    QsEntrances.GROUP -> Icons.Outlined.Edit
    "shade_status_icons" -> Icons.Outlined.SwapHoriz
    "notification_clear", "notification_clear_landscape" -> Icons.Outlined.DeleteOutline
    "notification_icons" -> C17SectionIcons.NotificationCenter
    "notification_icon_overrides" -> Icons.Outlined.EmojiEmotions
    "status_icon_library" -> Icons.Outlined.Collections
    "shade_wallpaper_portrait", "shade_wallpaper_landscape" -> Icons.Outlined.Wallpaper
    "notification_group_stack", "notification_stack", MINI_STACK_GROUP -> MiuixIcons.Layers
    "status_hint_icons" -> Icons.Outlined.Settings
    "qs_media" -> Icons.Outlined.MusicNote
    else -> Icons.Outlined.Tune
}
