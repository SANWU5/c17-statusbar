// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
    val visible = app.freeNoticeRequired || app.donationImage != null || app.confirmation != null || app.editing != null || app.colorEditing != null || app.fontCatalogOpen || app.fontRenameTarget != null || app.downloadingFont != null || app.notificationOverridesOpen || app.shadeWallpaperOpen
    SideEffect { app.syncModalLayer(visible) }
    Scaffold(containerColor = Color.Transparent) {
        C17Dialogs(app)
    }
}

@Composable
private fun C17Dialogs(app: ModernMainActivity) {
    if (app.freeNoticeRequired) {
        FreeNoticeDialog(app)
        return
    }
    app.donationImage?.let { DonationDialog(app, it) }
    if (app.notificationOverridesOpen) NotificationOverridesDialog(app)
    if (app.shadeWallpaperOpen) ShadeWallpaperDialog(app)
    if (app.fontCatalogOpen) FontCatalogDialog(app)
    app.fontRenameTarget?.let { entry ->
        UiInputDialog("重命名字体", entry.name, "只修改字体列表中的名称，不改变字形、粗细或其他已导入字体。",
            onDismiss = { app.fontRenameTarget = null; app.fontCatalogOpen = true }) { app.renameLibraryFont(entry, it) }
    }
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
    if (app.iconLibraryOpen || app.iconAssignmentsOpen) {
        val visible = app.freeNoticeRequired || app.confirmation != null || app.editing != null || app.colorEditing != null
        SideEffect { app.syncModalLayer(visible) }
        if (app.iconLibraryOpen) IconLibraryDialog(app) else IconAssignmentsDialog(app)
        return
    }
    val modalVisible = app.freeNoticeRequired || app.donationImage != null || app.confirmation != null || app.editing != null || app.colorEditing != null || app.fontCatalogOpen || app.fontRenameTarget != null || app.downloadingFont != null || app.notificationOverridesOpen || app.shadeWallpaperOpen
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
    val detailMaster = when (group?.id) {
        "carrier" -> carrierPrefix(app.category) + "_enabled"
        "classic_text" -> if (activeDetail?.id == "clock") ClassicTextSettings.CLOCK_MASTER else ClassicTextSettings.CARRIER + "_enabled"
        "native_network_badge" -> if (activeDetail?.id == "activity") NativeDataActivity.MASTER else NativeNetworkBadgeControls.MASTER
        else -> group?.masterKey
    }
    val revealedSetting = remember(group?.id, app.category) { requestedSetting }
    val detailSections = activeDetail?.items?.filterNot { it.key == detailMaster }
        ?.filterNot { group?.id == "font" && it.key == StatusBarSettings.FONT_NAME }
        ?.filter { SettingsCatalog.applicable(it, app.values) }
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
                    val canSwitch = SettingsCatalog.unavailableReason(key, app.values).isEmpty() || app.bool(key)
                    Switch(checked = app.bool(key), onCheckedChange = { app.save(key, it) }, enabled = app.canEdit && !app.busy && canSwitch && PanelMode.freezeReason(key).isEmpty())
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
                bottom = if (group == null) maxOf(padding.calculateBottomPadding(), app.navigationHeight.dp) + 12.dp
                    else padding.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(if (searching) 8.dp else 12.dp),
        ) {
            if (group != null) {
                item { DetailIntro(app, group) }
                val iconFamily = when (group.id) { "battery" -> "battery"; "wifi" -> "wifi"; "data" -> "cellular"; "status_hint_icons" -> "hint"; else -> null }
                if (iconFamily != null) item {
                    val assigned = runCatching { IconPackAssignments.rules(app.value(IconPackAssignments.ASSIGNMENTS)?.toString() ?: "[]") }
                        .getOrDefault(emptyList()).count { it.targetRole.startsWith("$iconFamily.") }
                    UiGroupCard {
                        UiRow("自定义图标", "逐个选择图案，位置和大小沿用本页设置", if (assigned > 0) "$assigned 项" else "选择", Icons.Outlined.Collections,
                            enabled = app.canEdit && !app.busy) { app.openIconAssignments(iconFamily) }
                    }
                }

                detailSections.forEach { (section, items) ->
                    item(key = "${group.id}-$section") {
                        DetailSettingsSection(app, group, section, items, revealedSetting)
                    }
                }
                if (group.id == "font") item { FontLicenseFooter(app) }
                val resetItems = if (group.id == "carrier" || detailPages.size > 1) activeDetail?.items ?: emptyList() else group.items
                val resetTitle = title + if (detailPages.size > 1) " · ${activeDetail?.title}" else ""
                item { UiGroupCard { UiRow("恢复本页默认设置", "只重置$resetTitle", icon = MiuixIcons.Reset, enabled = app.canEdit && !app.busy) {
                    app.resetGroup(group, items = resetItems, title = resetTitle)
                } } }
            } else when (app.page) {
                0 -> {
                    item { ActivationHero(app) }
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
                    item { UiGroupCard {
                        UiSwitchRow("隐藏桌面图标", "可从 LSPosed 模块设置进入并恢复", app.desktopIconHidden,
                            enabled = app.desktopIconAvailable && !app.freeNoticeRequired && !app.busy && !app.maintenanceRunning,
                            onChecked = app::requestDesktopIconHidden)
                    } }
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
                            val matchingItems = destination.items.filterNot { it.master }.filter { SettingsCatalog.applicable(it, app.values) }.filter {
                                (SettingsCatalog.displayTitle(it, app.values) + it.description + it.section + it.labels.joinToString(" ")).contains(term, true)
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
                                            val displayTitle = SettingsCatalog.displayTitle(setting, app.values)
                                            val summary = if (displayTitle.contains(term, true) || setting.section.contains(term, true)) setting.section
                                                else setting.labels.firstOrNull { it.contains(term, true) }
                                                    ?: setting.description.takeIf { it.contains(term, true) } ?: setting.section
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
                        if (matches.isEmpty()) item { Text("没有找到相关配置", Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
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
    OverlayDialog(show = true, title = "永久免费声明", onDismissRequest = app::finish) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(FreeNotice.DESCRIPTION, fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Text("请手动输入以下短句（包含标点）：", fontSize = 14.sp)
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
                Text("此输入框不提供复制、粘贴或拖入功能。", fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                error?.let { Text(it, color = Color(0xffd94a4a), fontSize = 13.sp) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(text = "退出", modifier = Modifier.weight(1f).heightIn(min = 48.dp), onClick = app::finish)
                TextButton(text = "确认并继续", enabled = FreeNotice.matches(value),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp), onClick = { confirm() })
            }
        }
    }
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
    OverlayDialog(show = true, title = if (wechat) "微信 · 自愿捐赠" else "支付宝 · 自愿捐赠",
        summary = "本模块永久免费，捐赠不会解锁或影响任何功能。", onDismissRequest = { app.donationImage = null }) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                DonationImage(app, asset, if (wechat) 1490f / 2030f else 1440f / 2160f)
            }
            TextButton(text = "关闭", modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), onClick = { app.donationImage = null })
        }
    }
}

@Composable
private fun ActivationHero(app: ModernMainActivity) {
    val safe = app.bool(StatusBarSettings.SAFE_MODE)
    val dark = isSystemInDarkTheme()
    val activated = app.runtimeActive || app.lspEnabled
    val groups = remember { SettingsCatalog.groups() }
    val enabledCount = groups.count { destinationEnabled(app, it) }
    val cardHeight = 180.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
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
    Row(Modifier.fillMaxWidth().height(cardHeight), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(22.dp)),
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
                Column(Modifier.align(Alignment.TopStart).padding(horizontal = 16.dp, vertical = 16.dp)) {
                    Text(statusTitle, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(3.dp))
                    Text(frameworkInfo, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface)
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("功能" to groups.size, "已启用" to enabledCount).forEach { (label, count) ->
                Card(modifier = Modifier.weight(1f).fillMaxWidth(), cornerRadius = 22.dp) {
                    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.Center) {
                        Text(label, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        Text(count.toString(), fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigurationHeader(app: ModernMainActivity) {
    val categories = listOf(SettingsCatalog.STATUSBAR, SettingsCatalog.NOTIFICATION, SettingsCatalog.CONTROL_CENTER, SettingsCatalog.LOCK_SCREEN, SettingsCatalog.OTHER)
    val labels = listOf("状态栏", "通知栏", "控制中心", "锁屏", "其他")
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val textStyle = MiuixTheme.textStyles.body2
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val itemWidth = (maxWidth - 10.dp - (4 * (categories.size - 1)).dp) / categories.size
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
    val dependency = SettingsCatalog.unavailableReason(group.masterKey, app.values)
    val modeLabel = when (app.panelMode) { PanelMode.CLASSIC -> "当前系统：经典模式"; PanelMode.SEPARATE -> "当前系统：分离模式"; else -> "当前系统：模式尚未确认" }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
        .background(MiuixTheme.colorScheme.primary.copy(alpha = .055f))
        .border(1.dp, MiuixTheme.colorScheme.primary.copy(alpha = .14f), RoundedCornerShape(16.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Info, null, Modifier.size(18.dp), tint = MiuixTheme.colorScheme.primary)
            Text("使用说明", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        Text(description, fontSize = 14.sp, lineHeight = 21.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        if (group.id == "classic_text" || group.id == "shade_wallpaper" || group.id.startsWith("notification_big_clock"))
            Text(modeLabel, fontSize = 13.sp, color = MiuixTheme.colorScheme.primary)
        if (dependency.isNotBlank()) Text(dependency, fontSize = 13.sp, lineHeight = 20.sp,
            color = MiuixTheme.colorScheme.primary)
        if (conflicts.isNotBlank()) Text(conflicts, fontSize = 13.sp, lineHeight = 20.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
    if (!app.canEdit) UiGroupCard { UiRow("激活模块或授予 Root 后即可修改", "点击前往主页", icon = MiuixIcons.Unlock) { app.closeGroup(); app.selectPage(0) } }
}

@Composable
private fun DetailSettingsSection(app: ModernMainActivity, group: SettingsCatalog.Group, section: String,
    items: List<SettingsCatalog.Item>, requested: String?) {
    val advanced = group.id.startsWith("notification_big_clock") && section in listOf("收起样式", "收起位置")
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
    val unavailable = SettingsCatalog.unavailableReason(item.key, app.values)
    val enabled = app.canEdit && !app.busy && PanelMode.freezeReason(item.key).isEmpty() && (unavailable.isEmpty() || value == true &&
        (item.master || item.key == FeatureOptions.STACK_LANDSCAPE_ENABLED ||
            item.key == C17HighlightRemoval.UNIFORM_NOTIFICATION_ENABLED))
    if (item.key == NativeStatusIcons.PRIORITY) {
        UiRow(item.title, item.description, "拖拽排序", Icons.Outlined.SwapVert, enabled = enabled) { app.editing = item }
        return
    }
    if (item.key == IconPackRepository.LAYERS) {
        UiRow(item.title, "文件与 GitHub 导入 · 排序、重命名、删除、禁用", "进入", Icons.Outlined.Collections, enabled = enabled) { app.openIconLibrary() }
        return
    }
    if (item.key == IconPackAssignments.ASSIGNMENTS) {
        UiRow(item.title, item.description, "进入", Icons.Outlined.Tune, enabled = enabled) { app.openIconAssignments() }
        return
    }
    if (item.key == "shade_wallpaper_manage") {
        UiRow(item.title, unavailable.ifEmpty { item.description }, "未完成", Icons.Outlined.Wallpaper, enabled = enabled) { app.shadeWallpaperOpen = true }
        return
    }
    if (item.key == NotificationIconOverrides.RULES) {
        UiRow(item.title, "按应用替换为文字、表情或其他应用图标", "进入", Icons.Outlined.EmojiEmotions, enabled = enabled) { app.openNotificationOverrides() }
        return
    }
    if (item.key == StatusBarSettings.FONT_MODE) {
        UiRow("文字来源", "系统、苹方、已导入字体和 10 款开源可变字体", app.currentFontTitle(), Icons.Outlined.TextFields,
            enabled = !app.busy) { app.chooseOpenFont() }
        return
    }
    when (item.type) {
        "boolean" -> UiSwitchRow(item.title, unavailable.ifEmpty { item.description }, value == true, enabled) { app.save(item.key, it) }
        "numeric" -> {
            var current by remember(item.key, value) { mutableStateOf<Number>((value as? Number) ?: 0f) }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(SettingsCatalog.displayTitle(item, app.values), Modifier.weight(1f).clickable(enabled = enabled) { app.editing = item }, fontSize = 16.sp)
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
            UiRow("导入字体", "TTF / OTF / TTC；导入后保留在文字来源列表", "导入", Icons.Outlined.FileUpload,
                enabled = app.canEdit && !app.busy) { app.chooseFont() }
        }
        "image" -> UiRow(item.title, item.description, value?.toString()?.takeIf { it.isNotBlank() }
            ?: if ((app.value(NotificationIconArea.IMAGE_REVISION) as? String).isNullOrBlank()) "选择图片" else "已导入图片",
            icon = Icons.Outlined.Image, enabled = enabled) { app.chooseNotificationIcon() }
        else -> UiRow(item.title, item.description, displayValue(item, value), enabled = enabled) { app.editing = item }
    }
}

@Composable
private fun FontCatalogDialog(app: ModernMainActivity) {
    val mode = app.value(StatusBarSettings.FONT_MODE)?.toString() ?: "system"
    val revision = app.value(StatusBarSettings.FONT_REVISION)?.toString() ?: ""
    val ready = app.fontLibraryEntries.associateBy { it.revision }
    OverlayDialog(show = true, title = "文字来源",
        summary = "所有字体在这里选择。标记“需下载”的字体选中后会下载并校验；字号与粗细在各功能里调节。",
        onDismissRequest = { app.fontCatalogOpen = false }) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { UiGroupCard {
                    UiRow("跟随系统", "保留系统字体家族", if (mode == "system") "已选择" else "", enabled = app.canEdit && !app.busy) {
                        app.save(StatusBarSettings.FONT_MODE, "system"); app.fontCatalogOpen = false
                    }
                    UiRow("苹方", "内置可变字体，粗细 100–900；缺失字符由系统补齐", if (mode == "pingfang") "已选择" else "", enabled = app.canEdit && !app.busy) {
                        app.save(StatusBarSettings.FONT_MODE, "pingfang"); app.fontCatalogOpen = false
                    }
                    UiRow("导入字体", "TTF / OTF / TTC；保留已有字体", "导入", Icons.Outlined.FileUpload,
                        enabled = app.canEdit && !app.busy) { app.chooseFont() }
                } }
                items(app.fontLibraryEntries.filter { it.catalog == null }, key = { "import-${it.revision}" }) { entry ->
                    UiGroupCard { FontSourceRow(app, entry.name, "已导入 · 字重支持取决于字体文件", mode == "custom" && revision == entry.revision,
                        entry, canRename = true) { app.selectLibraryFont(entry) } }
                }
                items(FontCatalog.entries(), key = { it.id }) { entry ->
                    UiGroupCard {
                        val stored = ready[entry.sha256]
                        FontSourceRow(app, entry.displayName,
                            "${entry.coverage}\n粗细 ${entry.minWeight}–${entry.maxWeight} · %.1f MB · ${if (stored == null) "需下载" else "已下载"}".format(Locale.ROOT, entry.bytes / 1048576.0),
                            mode == "custom" && revision == entry.sha256, stored) {
                            if (stored == null) app.downloadFont(entry) else app.selectLibraryFont(stored)
                        }
                    }
                }
                item { FontLicenseFooter(app) }
            }
            TextButton(text = "关闭", onClick = { app.fontCatalogOpen = false }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun FontSourceRow(app: ModernMainActivity, title: String, summary: String, selected: Boolean,
    stored: FontLibrary.Entry?, canRename: Boolean = false, onClick: () -> Unit) {
    ArrowPreference(title = title, summary = summary, enabled = app.canEdit && !app.busy,
        onClick = onClick, endActions = {
            if (selected) Icon(Icons.Outlined.Check, "已选择", tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            if (stored != null) {
                if (canRename) IconButton(enabled = app.canEdit && !app.busy, onClick = { app.fontCatalogOpen = false; app.fontRenameTarget = stored }) {
                    Icon(Icons.Outlined.Edit, "重命名 $title", modifier = Modifier.size(20.dp))
                }
                IconButton(enabled = app.canEdit && !app.busy, onClick = { app.fontCatalogOpen = false; app.deleteLibraryFont(stored) }) {
                    Icon(Icons.Outlined.DeleteOutline, "删除 $title", modifier = Modifier.size(20.dp))
                }
            }
        })
}

@Composable
private fun FontLicenseFooter(app: ModernMainActivity) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("字体来源与许可")
        UiGroupCard {
            UiRow("内置苹方", "Apple 原始设计 · ACT-02 可变字体重建；应用保留来源说明", "来源", Icons.Outlined.Code) {
                app.openUrl("https://github.com/ACT-02/PingFangUI-VF")
            }
            UiRow("开源可变字体", "10 款字体均使用 SIL Open Font License 1.1，来源与许可文件随应用保留", if (expanded) "收起" else "查看", Icons.Outlined.Article) {
                expanded = !expanded
            }
            if (expanded) FontCatalog.entries().forEach { entry ->
                ArrowPreference(title = entry.displayName, summary = entry.licenseName,
                    onClick = { app.openUrl(entry.sourceUrl) }, endActions = {
                        IconButton(onClick = { app.openUrl(entry.licenseUrl) }) { Icon(Icons.Outlined.Article, "查看 ${entry.displayName} 许可", modifier = Modifier.size(20.dp)) }
                    })
            }
            UiRow("导入字体", "字体文件保存在本机；导入前请确认你拥有使用权限。可变粗细由文件支持范围决定。")
        }
    }
}

@Composable
private fun EditSettingDialog(app: ModernMainActivity, item: SettingsCatalog.Item) {
    if (item.key == IconPackRepository.LAYERS) { LaunchedEffect(item.key) { app.editing = null; app.openIconLibrary() }; return }
    if (item.key == NotificationIconOverrides.RULES) { LaunchedEffect(item.key) { app.editing = null; app.openNotificationOverrides() }; return }
    if (item.key == NativeStatusIcons.PRIORITY) { HintPriorityDialog(app); return }
    val editor = remember(item.key, app.values) { SettingEditor.forItem(item, app.values) }
    val displayTitle = SettingsCatalog.displayTitle(item, app.values)
    if (item.type == "options") {
        UiChoiceDialog(displayTitle, item.values.toList(), item.labels.toList(), app.value(item.key)?.toString() ?: "",
            description = editor.context + if (item.description.isBlank()) "" else "\n${item.description}",
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
        pattern = editor.pattern, onDismiss = { app.editing = null }) { input ->
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
        val suffix = version.substringBefore('+').substringAfter('-', "").lowercase(Locale.ROOT)
        if (suffix.split('.', '-').any { it.startsWith("alpha") || it.startsWith("beta") || it.startsWith("rc") }) "测试版" else "正式版"
    }
    UiGroupCard {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AndroidView(factory = { context -> android.widget.ImageView(context).apply {
                setImageDrawable(context.packageManager.getApplicationIcon(context.packageName))
                contentDescription = context.getString(dev.puitheme.iosstatusbar.R.string.app_name)
            } }, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(14.dp))
            Text(app.getString(dev.puitheme.iosstatusbar.R.string.app_name), fontSize = 22.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text("$version · $versionLabel", fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Spacer(Modifier.height(6.dp))
            Text("作者 ${ProjectContact.AUTHOR}", fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurface)
            Spacer(Modifier.height(12.dp))
            Text("永久免费 · 捐赠自愿", fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}

@Composable
private fun AboutActions(app: ModernMainActivity) {
    var section by rememberSaveable { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        UiGroupCard {
            UiRow("检查更新", app.updateMessage, if (app.checkingUpdates) "检查中" else "检查", Icons.Outlined.SystemUpdate,
                enabled = !app.checkingUpdates && !app.busy) { app.checkUpdates() }
        }
        UiGroupCard {
            UiRow("联系与捐赠", "aiingjie · QQ ${ProjectContact.QQ}", if (section == "contact") "收起" else "展开", Icons.Outlined.FavoriteBorder) {
                section = if (section == "contact") null else "contact"
            }
            if (section == "contact") {
                UiRow("联系作者", "QQ ${ProjectContact.QQ}", "复制", Icons.Outlined.ContentCopy) { app.toast(if (ProjectContact.copyQq(app)) "QQ号已复制" else "复制失败") }
                UiRow("酷安", ProjectContact.COOLAPK, "复制", Icons.Outlined.ContentCopy) { app.toast(if (ProjectContact.copyCoolapk(app)) "酷安用户名已复制" else "复制失败") }
                UiRow("微信捐赠", "完全自愿，不影响功能", "查看", Icons.Outlined.FavoriteBorder) { app.donationImage = "donation/wechat.png" }
                UiRow("支付宝捐赠", "完全自愿，不影响功能", "查看", Icons.Outlined.FavoriteBorder) { app.donationImage = "donation/alipay.jpg" }
                Text("如果这个小工具让你每天用手机时多了一点顺心，欢迎留下一份心意。收到支持，我会很开心，也会更有动力继续打磨那些不起眼却影响体验的细节。量力而行就好；愿意使用、反馈和分享，同样是对我的支持。",
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), fontSize = 13.sp, lineHeight = 20.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                UiRow("免费声明", "永久免费，请勿付费购买", icon = Icons.Outlined.Info) { app.confirmation = UiConfirmation("免费声明", FreeNotice.DESCRIPTION, "知道了") {} }
            }
        }
        UiGroupCard {
            UiRow("项目与开源", "GitHub · ${ProjectContact.GITHUB}", if (section == "project") "收起" else "展开", Icons.Outlined.Code) {
                section = if (section == "project") null else "project"
            }
            if (section == "project") {
                UiRow("项目源码", "SANWU5/c17-statusbar", icon = Icons.Outlined.Code) { app.openUrl("https://github.com/SANWU5/c17-statusbar") }
                UiRow("正式发布", "发布记录与安装包", icon = Icons.Outlined.OpenInNew) { app.openLatestRelease() }
                UiRow("开源许可", "GPL · Apache · OFL", icon = Icons.Outlined.Article) {
                    app.confirmation = UiConfirmation("开源许可", "C17 Statusbar：GPL-3.0-only\nMiuix：Apache-2.0\n字体：SIL Open Font License 1.1\n圆角技术参考：MCGA · Zhuangzhi Meng (Gustate XiaoMeng) · GPL-3.0-or-later\n完整许可与版权声明随应用保留。", "知道了") {}
                }
                UiRow("Miuix", "界面组件 · Apache 2.0", "0.9.4", Icons.Outlined.Palette) { app.openUrl("https://github.com/compose-miuix-ui/miuix") }
            }
        }
        UiGroupCard {
            UiRow("配置与诊断", "备份、恢复和问题报告", if (section == "tools") "收起" else "展开", Icons.Outlined.Settings) {
                section = if (section == "tools") null else "tools"
            }
            if (section == "tools") {
                UiRow("导出配置", "保存当前设置", icon = Icons.Outlined.FileUpload, enabled = app.canEdit && !app.busy) { app.chooseExport() }
                UiRow("导入配置", "确认后应用设置", icon = Icons.Outlined.FileDownload, enabled = app.canEdit && !app.busy) { app.chooseImport() }
                UiSwitchRow("诊断日志", "开启后复现问题，再导出报告", app.bool(StatusBarSettings.DIAGNOSTICS_ENABLED), app.canEdit) { app.save(StatusBarSettings.DIAGNOSTICS_ENABLED, it) }
                UiRow("导出问题报告", "包含设备信息、配置快照与运行记录", icon = Icons.Outlined.Description) { app.chooseLog() }
                UiRow("清空历史日志", "删除运行记录与分享副本", icon = Icons.Outlined.DeleteOutline, enabled = !app.busy) { app.clearLogHistory() }
                UiRow("删除所有配置", "恢复默认并清除备份", icon = Icons.Outlined.DeleteOutline, enabled = app.canEdit && !app.busy) { app.deleteAllSettings() }
            }
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
    OverlayDialog(show = true, title = "显示优先级", onDismissRequest = { app.editing = null }) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("长按图标名称后上下拖动，靠前的优先显示。系统当前未启用的提示不会被强制显示。", fontSize = 14.sp,
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
                TextButton(text = "跟随系统", modifier = Modifier.weight(1f), onClick = { app.save(NativeStatusIcons.PRIORITY, ""); app.editing = null })
                TextButton(text = "确定", modifier = Modifier.weight(1f), onClick = { app.save(NativeStatusIcons.PRIORITY, ordered.joinToString(",")); app.editing = null })
            }
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
    when (group.id) {
        "carrier" -> if (category != null) app.bool(carrierPrefix(category) + "_enabled")
            else CarrierPanels.GROUPS.any { app.bool(it + "_enabled") }
        "native_network_badge" -> app.bool(group.masterKey) || app.bool(NativeDataActivity.MASTER)
        "notification_clear_landscape" -> app.bool(group.masterKey) || app.bool(NotificationClearAppearance.LANDSCAPE_MASTER)
        "classic_text" -> app.bool(group.masterKey) || app.bool(ClassicTextSettings.CLOCK_MASTER)
        else -> app.bool(group.masterKey)
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
    "wifi" -> Icons.Outlined.Wifi
    "label" -> C17SectionIcons.Network5G
    "native_network_badge" -> Icons.Outlined.TextFields
    "network_order" -> Icons.Outlined.SwapHoriz
    "clock", "shade_clock", "notification_big_clock", "notification_big_clock_landscape", "lockscreen_date", "classic_text" -> Icons.Outlined.Schedule
    "lockscreen_lock_icon" -> Icons.Outlined.Lock
    "lockscreen_status_blur" -> Icons.Outlined.BlurOn
    "fluid_cloud_accent" -> Icons.Outlined.Palette
    "carrier" -> Icons.Outlined.CellTower
    "font" -> Icons.Outlined.TextFields
    "battery" -> Icons.Outlined.BatteryFull
    "tiles" -> Icons.Outlined.Swipe
    "qs_appearance" -> Icons.Outlined.Palette
    "tile_corners" -> Icons.Outlined.RoundedCorner
    "tile_icon_size" -> Icons.Outlined.GridView
    "shade_status_icons" -> Icons.Outlined.SwapHoriz
    "notification_clear", "notification_clear_landscape" -> Icons.Outlined.DeleteOutline
    "notification_icons" -> C17SectionIcons.NotificationCenter
    "notification_icon_overrides" -> Icons.Outlined.EmojiEmotions
    "status_icon_library" -> Icons.Outlined.Collections
    "shade_wallpaper" -> Icons.Outlined.Wallpaper
    "notification_group_stack", "notification_stack" -> MiuixIcons.Layers
    "status_hint_icons" -> Icons.Outlined.Settings
    "qs_media" -> Icons.Outlined.MusicNote
    else -> Icons.Outlined.Tune
}
