// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class IconManagementDestination(val section: String = "home", val scope: String? = null,
    val packId: String? = null, val targetId: String? = null)

private val iconManagementPreviews = object : android.util.LruCache<String, android.graphics.Bitmap>(1024 * 1024) {
    override fun sizeOf(key: String, value: android.graphics.Bitmap) = value.width * value.height
}

private fun sourceRoleInPack(pack: IconManagementRepository.Pack, role: String): String? =
    IconManagementSelection.sourceRole(pack.manifest, role)
private fun availablePackRoles(pack: IconManagementRepository.Pack): List<String> =
    (pack.manifest.icons.keys.filter { IconRoleRegistry.knownResourceRole(it) } +
        IconRoleRegistry.targets(null).flatMap { it.roles }.filter { sourceRoleInPack(pack, it) != null }).distinct()

private fun roleTitle(role: String): String = IconRoleRegistry.role(role)?.title ?: IconRoleRegistry.target(role)?.title ?: role
private fun iconPackName(pack: IconManagementRepository.Pack): String = if (pack.name.startsWith("C17 Rounded 27"))
    "C17示例库" + pack.name.removePrefix("C17 Rounded 27") else pack.name
private fun packPreviewRoles(pack: IconManagementRepository.Pack, limit: Int): List<String> {
    val representative = listOf("wifi.4", "cellular.4", "battery.100", "tile.wifi.on", "tile.bluetooth.on", "tile.flashlight.on",
        "slider.brightness.default", "media.play_pause.play", "device.headset.default", "footer.settings.default")
        .mapNotNull { sourceRoleInPack(pack, it) }
    val groups = availablePackRoles(pack)
        .groupBy { IconRoleRegistry.role(it)?.targetId ?: it }.values.map { it.first() }
    return (representative + groups).distinct().take(limit)
}
private fun unsupportedArtworkCount(pack: IconManagementRepository.Pack): Int = pack.manifest.icons.keys.count {
    IconRoleRegistry.role(it)?.supported != true && IconRoleRegistry.targetForRole(it)?.supported != true
}
private fun packFormats(pack: IconManagementRepository.Pack): String =
    trf("v3 · PNG %1\$s · 矢量 %2\$s · 动态 %3\$s", pack.pngCount, pack.vectorCount, pack.animationCount)

/** Thumbnail loading shares the production PNG/vector decoder; native/default artwork is never invented. */
@Composable
private fun IconArtwork(app: ModernMainActivity, pack: IconManagementRepository.Pack?, role: String?,
    label: String = "系统图案", tint: Int? = null, opacity: Float = 100f, previewDark: Boolean? = null, geometryRole: String? = null,
    sizePercent: Float = 100f, largePreview: Boolean = false) {
    val asset = role?.let { current -> pack?.let { source -> sourceRoleInPack(source, current)?.let { source.manifest.icons[it] } } }
    val prepared = app.iconPendingImport?.takeIf { it.pack.id == pack?.id }
    val previewKey = if (pack != null && role != null) "${pack.id}/$role/${geometryRole ?: role}/96" else ""
    val loaded by produceState<Pair<String, android.graphics.Bitmap?>?>(null, previewKey) {
        value = null
        if (pack != null && role != null) {
            val decoded = withContext(Dispatchers.IO) {
                iconManagementPreviews.get(previewKey) ?: runCatching {
                    (if (prepared != null) IconManagementRepository.previewPrepared(prepared, role, 96, geometryRole ?: role)
                        else IconManagementRepository.preview(app.applicationContext, pack.id, role, 96, geometryRole ?: role))
                        ?.also { iconManagementPreviews.put(previewKey, it) }
                }.getOrNull()
            }
            value = previewKey to decoded
        }
    }
    val image = loaded?.takeIf { it.first == previewKey && pack != null && role != null }?.second
    val background = previewDark?.let { if (it) Color(0xff232426) else Color(0xfff1f2f3) } ?: MiuixTheme.colorScheme.primary.copy(alpha = .06f)
    Box(Modifier.size(if (largePreview) 80.dp else 48.dp).clip(RoundedCornerShape(12.dp)).background(background),
        contentAlignment = Alignment.Center) {
        if (image != null) Image(image.asImageBitmap(), label, Modifier.size((34f * sizePercent / 100f).dp),
            alpha = (opacity / 100f).coerceIn(0f, 1f), colorFilter = when {
                tint != null -> ColorFilter.tint(Color(tint))
                asset?.render == "mask" -> ColorFilter.tint(MiuixTheme.colorScheme.onSurface)
                else -> null
            })
        else Text(if (pack == null) "系统" else if (loaded?.first == previewKey) "无法预览" else "…", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

private data class IconUiCategory(val title: String, val scopes: Set<String>)
private val iconUiCategories = listOf(
    IconUiCategory("状态栏", setOf("hint", "wifi", "cellular", "battery")),
    IconUiCategory("控制中心", setOf("tile", "slider", "footer")),
    IconUiCategory("其他", setOf("media", "device"))
)
private fun isOriginalCandidate(role: String): Boolean = !IconRoleRegistry.knownResourceRole(role)
private fun iconSectionTitle(scope: String): String = when (scope) {
    "hint" -> "系统提示"; "tile" -> "快捷开关"; "slider" -> "亮度与音量"
    "media" -> "媒体控制"; "device" -> "连接设备"; "footer" -> "页脚入口"
    else -> IconRoleRegistry.scopeTitle(scope)
}
private fun targetGroup(target: IconRoleRegistry.Target): String = if (target.scope != "tile") iconSectionTitle(target.scope) else when (target.id.removePrefix("tile.")) {
    "wifi", "bluetooth", "mobile_data", "hotspot", "airplane", "nfc", "data_saver", "vpn", "otg", "cast" -> "网络与连接"
    "rotation", "dark", "auto_brightness", "auto_color_temperature", "osie", "iris", "small_screen" -> "显示与屏幕"
    "dolby", "dnd", "no_disturb", "vibration", "ringer_mode" -> "声音与免打扰"
    "camera", "microphone", "location", "phone_anti_theft", "lock_screen" -> "隐私与安全"
    "alipay", "ali_scan", "wechat_pay", "wechat_scan", "qr_scanner", "wallet" -> "扫码与付款"
    else -> "常用工具"
}

@Composable
private fun IconSectionLabel(title: String) {
    Text(title, Modifier.padding(horizontal = 12.dp), fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
}

@Composable
internal fun IconManagementPage(app: ModernMainActivity) {
    val destination = app.iconManagementDestination
    val bundledKinds by produceState<Set<String>>(emptySet(), app) {
        value = withContext(Dispatchers.IO) { IconManagementRepository.bundledKinds(app.applicationContext) }
    }
    var rename by remember { mutableStateOf<IconManagementRepository.Pack?>(null) }
    val childBack = remember { arrayOfNulls<() -> Boolean>(1) }
    val targetDrafts = remember { mutableMapOf<String, IconTargetDraft>() }
    val records = app.iconManagementRecords()
    val state = remember(records) { runCatching { IconManagementSettings.parse(records) }.getOrElse { IconManagementSettings.parse(IconManagementSettings.DEFAULT) } }
    val pending = app.iconPendingImport
    val pack = pending?.pack ?: app.iconPacks.firstOrNull { it.id == destination.packId }
    val scope = destination.scope?.takeIf { IconRoleRegistry.validScope(it) }
    var switchError by remember { mutableStateOf("") }
    fun navigate(section: String, nextScope: String? = scope, packId: String? = null, target: String? = null) {
        if (section in listOf("home", "custom", "scope", "appearance")) {
            app.iconLibraryTarget = null
            targetDrafts.clear()
        }
        app.iconManagementDestination = IconManagementDestination(section, nextScope, packId, target)
    }
    fun back() {
        if (app.busy) return
        if (rename != null) { rename = null; return }
        if (childBack[0]?.invoke() == true) return
        val libraryTarget = IconRoleRegistry.target(app.iconLibraryTarget)
        if (libraryTarget != null && (pending != null || destination.section in listOf("pack", "apply"))) {
            if (pending != null) app.cancelIconImport()
            app.iconLibraryTarget = null
            navigate("target", libraryTarget.scope, target = libraryTarget.id)
            return
        }
        if (pending != null) { app.cancelIconImport(); return }
        when (destination.section) {
            "home" -> { app.iconLibraryTarget = null; targetDrafts.clear(); app.closeIconManagement() }
            "target", "target_pick", "appearance", "scope" -> navigate("custom")
            "pack", "apply", "github" -> navigate("library", null)
            "library" -> {
                val target = IconRoleRegistry.target(app.iconLibraryTarget)
                app.iconLibraryTarget = null
                if (target != null) navigate("target", target.scope, target = target.id) else navigate("home", null)
            }
            else -> navigate("home", null)
        }
    }
    fun cancelPackPreview() {
        if (app.busy) return
        val target = IconRoleRegistry.target(app.iconLibraryTarget)
        if (pending != null) app.cancelIconImport()
        if (target != null) {
            app.iconLibraryTarget = null
            navigate("target", target.scope, target = target.id)
        } else navigate("library", null)
    }
    SideEffect { app.iconManagementBackRequest = ::back }
    DisposableEffect(app) { onDispose { app.iconManagementBackRequest = null } }
    val title = when {
        pending != null || destination.section in listOf("pack", "apply") -> if (app.iconLibraryTarget != null && destination.section != "apply") "预览图库" else "预览与应用"
        destination.section == "library" -> if (app.iconLibraryTarget != null) "选择图库" else "选套装"
        destination.section == "github" -> "从 GitHub 导入"
        destination.section == "custom" || destination.section == "scope" -> "自定义图标"
        destination.section == "appearance" -> trf("%s外观", scope?.let { iconSectionTitle(it) }.orEmpty())
        destination.section in listOf("target", "target_pick") -> IconRoleRegistry.target(destination.targetId)?.title ?: "选择图案"
        else -> "图标管理"
    }
    val scroll = rememberC17ScrollBehavior(rememberC17TopAppBarState(initialHeightOffsetLimit = 0f))
    C17Scaffold(containerColor = MiuixTheme.colorScheme.surface, topBar = {
        C17SmallTopAppBar(title = title, scrollBehavior = scroll, navigationIcon = {
            C17IconButton(onClick = ::back, enabled = !app.busy) { Icon(Icons.Outlined.ArrowBack, "返回") }
        })
    }) { padding ->
        key(destination, pending?.pack?.id) {
            LazyColumn(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()).nestedScroll(scroll.nestedScrollConnection),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = padding.calculateBottomPadding() + 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                app.iconMigrationNotice?.let { notice -> item { UiGroupCard {
                    UiRow(if (app.iconMigrationCompleted) "图标管理已升级" else "图标升级需要重试", notice)
                    UiRow(if (app.iconMigrationCompleted) "知道了" else "重试升级", enabled = !app.busy) {
                        if (app.iconMigrationCompleted) app.acknowledgeIconMigration() else app.refreshIconLibraries()
                    }
                } } }
                if (app.busy || app.iconLibrariesLoading) item { UiGroupCard {
                    UiRow(if (app.iconLibrariesLoading) "正在读取图标库" else "正在校验图标资源")
                    if (app.iconPackDownloading) UiRow("取消下载", enabled = true, onClick = app::cancelIconPackDownload)
                } }
                if (!app.iconManagementEnabled() && destination.section != "home") item { UiGroupCard {
                    UiRow("图标管理已关闭", "可以继续选择和保存，开启总开关后生效。")
                } }
                when {
                    pending != null -> item {
                        IconPackApplication(app, pending.pack, records, pending = true, onCancel = ::cancelPackPreview, onApplied = app::finishIconPackApplication)
                    }
                    destination.section == "library" -> {
                        app.iconLibraryTarget?.let { targetId -> item { UiGroupCard {
                            UiRow(trf("为%s选择素材", IconRoleRegistry.target(targetId)?.title ?: tr("当前图标")), "选择图库后直接挑选图案，最后在目标页统一保存。")
                        } } }
                        if (bundledKinds.isNotEmpty()) {
                            item { IconSectionLabel("内置套装") }
                            item { UiGroupCard {
                                if ("pui" in bundledKinds) UiRow("PUI ColorOS 17", "天伞桜&PanL · 原主题真实图案", "预览", Icons.Outlined.Collections,
                                    enabled = app.canEdit && !app.busy) { app.prepareBundledIcons("pui") }
                                if ("original" in bundledKinds) UiRow("C17示例库", "状态图案与动画示例素材", "预览", Icons.Outlined.AutoAwesome,
                                    enabled = app.canEdit && !app.busy) { app.prepareBundledIcons("original") }
                            } }
                        }
                        if (app.iconPacks.isNotEmpty()) item { IconSectionLabel("我的套装") }
                        items(app.iconPacks, key = { it.id }) { listed -> UiGroupCard {
                            UiRow(iconPackName(listed), trf("%1\$s · %2\$s 个图案", listed.author.ifBlank { tr("未署名") }, listed.iconCount), "预览", Icons.Outlined.Collections,
                                enabled = !app.busy) { navigate("pack", null, listed.id) }
                            Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                packPreviewRoles(listed, 5).forEach { role -> IconArtwork(app, listed, role, roleTitle(role)) }
                            }
                            Row(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                C17TextButton(text = "重命名", modifier = Modifier.weight(1f), enabled = app.canEdit && !app.busy, onClick = { rename = listed })
                                C17TextButton(text = "删除", modifier = Modifier.weight(1f), enabled = app.canEdit && !app.busy, onClick = { app.deleteIconPack(listed.id, iconPackName(listed)) })
                            }
                        } }
                        item { UiGroupCard {
                            UiRow("导入图标包", if (app.iconLibraryTarget != null) "选择自己的 v3 ZIP / RAR（含 PUI 图库），预览后挑选图案" else "选择自己的 v3 ZIP / RAR（含 PUI 图库），预览后应用", "文件", Icons.Outlined.FileUpload,
                                enabled = app.canEdit && !app.busy, onClick = app::chooseIconPack)
                            UiRow("从 GitHub 获取", "仓库或图标包下载链接", "进入", Icons.Outlined.CloudDownload, enabled = !app.busy) { navigate("github", null) }
                        } }
                    }
                    destination.section == "github" -> item { IconGithubImport(app) }
                    destination.section in listOf("pack", "apply") && pack != null -> item {
                        IconPackApplication(app, pack, records, pending = false, onCancel = ::cancelPackPreview, onRename = { rename = pack }, onApplied = app::finishIconPackApplication)
                    }
                    destination.section in listOf("custom", "scope") -> item {
                        IconTargetBrowser(app, state, scope, onTarget = { target -> navigate("target", target.scope, target = target.id) },
                            onAppearance = { next -> navigate("appearance", next) })
                    }
                    destination.section in listOf("target", "target_pick") -> item {
                        IconRoleRegistry.target(destination.targetId)?.let { target ->
                            IconTargetEditor(app, target, state, childBack,
                                initialDraft = targetDrafts[target.id], initiallyPick = destination.section == "target_pick", preferredPackId = destination.packId,
                                onDraft = { targetDrafts[target.id] = it }) { navigate("custom", target.scope) }
                        } ?: UiGroupCard { UiRow("目标已变化", "返回后重新选择图标。") }
                    }
                    destination.section == "appearance" && scope != null -> item {
                        IconScopeAppearance(app, scope, state, childBack) { navigate("custom") }
                    }
                    destination.section in listOf("pack", "apply") -> item { UiGroupCard { UiRow("套装已不存在", "返回后重新选择。") } }
                    else -> {
                        item { UiGroupCard {
                            UiSwitchRow("启用图标管理", "关闭后恢复系统或功能默认图案，保留已保存的选择。", app.iconManagementEnabled(), app.canEdit && !app.busy) {
                                switchError = app.setIconManagementEnabled(it)
                            }
                        } }
                        if (switchError.isNotEmpty()) item { Text(switchError, color = MiuixTheme.colorScheme.error, fontSize = 13.sp) }
                        item { UiGroupCard {
                            UiRow("选套装", "预览整套图标，一次应用", "进入", Icons.Outlined.Collections, enabled = !app.busy) {
                                app.iconLibraryTarget = null; targetDrafts.clear(); navigate("library", null)
                            }
                            UiRow("自定义", "先选要改的图标，再选图案", "进入", Icons.Outlined.Tune, enabled = !app.busy) { navigate("custom", null) }
                        } }
                        item { UiGroupCard {
                            UiRow("当前选择", trf("%1\$s 个范围已选套装 · %2\$s 个自定义目标", state.scopePacks.size, state.overrides.keys.mapNotNull { IconRoleRegistry.role(it)?.targetId }.distinct().size))
                            state.scopePacks.forEach { (appliedScope, id) ->
                                val name = app.iconPacks.firstOrNull { it.id == id }?.let(::iconPackName) ?: "来源缺失"
                                UiRow("${iconSectionTitle(appliedScope)} · $name", "移除后立即保存；保留单项图案、大小、颜色、透明度和动画选择。", "移除套装",
                                    enabled = app.canEdit && !app.busy) { switchError = app.clearIconScopePack(appliedScope) }
                            }
                            UiRow("恢复图标默认设置", "清除套装与自定义选择，保留图标库", "恢复", enabled = app.canEdit && !app.busy, onClick = app::resetIconManagement)
                            UiRow("图标包制作说明", "v3 格式与支持范围", "查看") { app.openUrl("https://github.com/SANWU5/c17-icon-packs") }
                        } }
                    }
                }
            }
        }
    }
    rename?.let { selected -> UiInputDialog("重命名图标库", iconPackName(selected), "使用未被其他图标库占用的名称。", onDismiss = { rename = null }) { value ->
        app.iconLibraryNameError(value, selected.id).takeIf { it.isNotEmpty() }
            ?: run { app.renameIconPack(selected.id, value); rename = null; null }
    } }
}

@Composable
private fun IconTargetBrowser(app: ModernMainActivity, state: IconManagementSettings.State, initialScope: String?,
    onTarget: (IconRoleRegistry.Target) -> Unit, onAppearance: (String) -> Unit) {
    var category by remember { mutableIntStateOf(iconUiCategories.indexOfFirst { initialScope in it.scopes }.coerceAtLeast(0)) }
    var query by remember { mutableStateOf("") }
    var showUnsupported by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }
    val allTargets = IconRoleRegistry.targets(null)
    val visible = allTargets.filter { target ->
        (if (query.isBlank()) target.scope in iconUiCategories[category].scopes else true) &&
            (query.isBlank() || target.title.contains(query.trim(), true) || target.id.contains(query.trim(), true))
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        UiContentTabs(tabs = iconUiCategories.map { it.title }, selectedTabIndex = category, onTabSelected = { category = it; showUnsupported = false }, modifier = Modifier.fillMaxWidth())
        C17TextField(value = query, onValueChange = { query = it }, label = "搜索图标名称", modifier = Modifier.fillMaxWidth(), singleLine = true,
            useLabelAsPlaceholder = true, insideMargin = DpSize(12.dp, 10.dp))
        visible.filter { it.supported }.groupBy(::targetGroup).forEach { (title, targets) ->
            IconSectionLabel(title)
            UiGroupCard { targets.forEach { target ->
                val count = target.roles.count { state.overrides.containsKey(it) }
                val packName = state.scopePacks[target.scope]?.let { id -> app.iconPacks.firstOrNull { it.id == id }?.let(::iconPackName) }
                UiRow(target.title, if (count > 0) trf("已自定义 · %s 个状态", count) else packName ?: "系统或功能默认图案", "选择", enabled = !app.busy) { onTarget(target) }
            } }
        }
        if (visible.none { it.supported }) UiGroupCard { UiRow("没有找到可设置的图标", "换个名称搜索，或选择其他分类。") }
        val unsupported = visible.filterNot { it.supported }
        if (unsupported.isNotEmpty()) UiGroupCard {
            UiRow("暂未支持", trf("%s 个目标保留系统图案", unsupported.size), if (showUnsupported) "收起" else "展开") { showUnsupported = !showUnsupported }
            if (showUnsupported) unsupported.forEach { UiRow(it.title, it.reason, "保留原生", enabled = false) }
        }
        UiGroupCard {
            UiRow("分类大小与颜色", "统一设置某类图标；单个目标仍可独立调整", if (advanced) "收起" else "展开") { advanced = !advanced }
            if (advanced) iconUiCategories[category].scopes.forEach { scope -> UiRow(iconSectionTitle(scope), "单个图标的设置仍优先", "外观", enabled = !app.busy) { onAppearance(scope) } }
        }
    }
}

@Composable
private fun IconGithubImport(app: ModernMainActivity) {
    var source by remember { mutableStateOf("SANWU5/c17-icon-packs") }
    var name by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        UiGroupCard { UiRow("公开 v3 图标包", "填写 owner/repo 或 GitHub 官方下载链接。仓库需发布 icon-pack-v3.zip；未发布时不会导入整仓库源码。") }
        UiGroupCard { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            C17TextField(value = source, onValueChange = { source = it }, label = "GitHub 仓库或下载链接", modifier = Modifier.fillMaxWidth(), singleLine = true, useLabelAsPlaceholder = true, insideMargin = DpSize(12.dp, 10.dp))
            C17TextField(value = name, onValueChange = { name = it }, label = "显示名称（可不填）", modifier = Modifier.fillMaxWidth(), singleLine = true, useLabelAsPlaceholder = true, insideMargin = DpSize(12.dp, 10.dp))
        } }
        C17TextButton(text = "下载并检查", modifier = Modifier.fillMaxWidth(), enabled = app.canEdit && !app.busy && source.isNotBlank(), onClick = { app.downloadIconPack(source.trim(), name.trim()) })
    }
}

@Composable
private fun IconPackApplication(app: ModernMainActivity, pack: IconManagementRepository.Pack, records: String,
    pending: Boolean, onCancel: () -> Unit, onRename: (() -> Unit)? = null, onApplied: () -> Unit) {
    val targetSelection = app.iconLibraryTarget != null && app.iconManagementDestination.section != "apply"
    val target = IconRoleRegistry.target(app.iconLibraryTarget)
    val availableScopes = remember(pack.id) { IconRoleRegistry.SCOPES.filter { scope ->
        IconRoleRegistry.targets(scope).any { target -> target.supported && target.roles.any { sourceRoleInPack(pack, it) != null } }
    }.toSet() }
    var selected by remember(pack.id) { mutableStateOf(runCatching {
        IconManagementSettings.parse(records).scopePacks.filterValues { it == pack.id }.keys.intersect(availableScopes)
            .ifEmpty { availableScopes }
    }.getOrDefault(availableScopes)) }
    var clearOverrides by remember(pack.id) { mutableStateOf(false) }
    var presetId by remember(pack.id) { mutableStateOf<String?>(null) }
    var presetDark by remember(pack.id) { mutableStateOf(false) }
    var editScopes by remember(pack.id) { mutableStateOf(false) }
    var advanced by remember(pack.id) { mutableStateOf(false) }
    var browse by remember(pack.id) { mutableStateOf(false) }
    var error by remember(pack.id) { mutableStateOf("") }
    val existing = if (pending) app.iconImportExisting else null
    val nameError = if (pending && existing == null) app.iconLibraryNameError(app.iconImportName, pack.id) else ""
    val palettes = remember(pack.id) { IconColorPresets.options(pack.manifest) }
    val preset = presetId?.let { palettes[it] }
    val previewRecords = remember(records, presetId, selected) {
        runCatching { if (preset == null || selected.isEmpty()) records else IconManagementSettings.applyColorPreset(records, preset, selected) }.getOrDefault(records)
    }
    val presetState = remember(previewRecords) { IconManagementSettings.parse(previewRecords) }
    val preview = remember(previewRecords, pack.id, selected, clearOverrides, targetSelection) {
        if (targetSelection || selected.isEmpty()) null else runCatching { IconManagementSelection.previewApplication(previewRecords, pack, selected, clearOverrides) }.getOrNull()
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (target != null) UiGroupCard {
            if (targetSelection) {
                UiRow(trf("仅为%s选择图案", target.title), "选择后返回目标预览，图案和高级设置仍需在目标页保存。")
                UiRow("改为应用整套", "选择状态栏、控制中心等范围；当前目标草稿会保留。", "选择范围", enabled = !app.busy) {
                    app.iconManagementDestination = IconManagementDestination("apply", packId = pack.id)
                }
            } else {
                UiRow("应用整套", trf("只保存下面所选范围，%s的未保存草稿会保留。", target.title))
                UiRow("返回单个目标选图", trf("继续为%s挑选图案", target.title), "返回", enabled = !app.busy) {
                    app.iconManagementDestination = IconManagementDestination("pack", packId = pack.id)
                }
            }
        }
        if (pending) UiGroupCard {
            if (existing != null) {
                UiRow("相同内容已在图库中", trf("将使用“%s”，不会重复导入或更改已有名称。需要改名时可在图库列表操作。", iconPackName(existing)))
            } else {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    C17TextField(value = app.iconImportName, onValueChange = { app.iconImportName = it; app.iconImportError = "" },
                        label = "图标库名称", modifier = Modifier.fillMaxWidth(), singleLine = true,
                        enabled = app.canEdit && !app.busy, isError = nameError.isNotEmpty(), errorMessage = nameError.takeIf { it.isNotEmpty() },
                        useLabelAsPlaceholder = true, insideMargin = DpSize(12.dp, 10.dp))
                    Text(tr("不同内容不能使用已有名称。修改名称后再保存，原有图标库保持不变。"), fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
        }
        UiGroupCard {
            UiRow(existing?.let(::iconPackName) ?: iconPackName(pack), trf("%1\$s · %2\$s 个图案", pack.author.ifBlank { tr("未署名") }, pack.iconCount) + if (pack.animationCount > 0) trf(" · %s 个动画", pack.animationCount) else "")
            val samples = packPreviewRoles(pack, 12)
            samples.chunked(4).forEach { row -> Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { role -> Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    val targetRole = IconRoleRegistry.role(role)?.id ?: IconRoleRegistry.roles(null).firstOrNull { sourceRoleInPack(pack, it) == role } ?: role
                    val color = IconManagementSelection.appearance(presetState, targetRole, presetDark, pack.manifest.icons[role]?.render ?: "mask")
                    IconArtwork(app, pack, role, roleTitle(role), tint = when {
                        color.mode == "custom" -> color.color
                        color.artwork -> null
                        presetDark -> 0xffffffff.toInt()
                        else -> 0xff000000.toInt()
                    }, opacity = color.opacity, previewDark = presetDark)
                    Text(roleTitle(role), fontSize = 11.sp, maxLines = 2, modifier = Modifier.padding(top = 4.dp), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                } }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            } }
            UiRow("浏览全部图案", "按分类查看素材与原始候选", if (browse) "收起" else "展开") { browse = !browse }
        }
        if (browse) IconArtworkBrowser(app, pack)
        if (!targetSelection) UiGroupCard {
            UiRow("应用范围", "选择要替换的区域，缺失状态继续使用默认图案。")
            iconUiCategories.forEach { category ->
                val scopes = category.scopes.intersect(availableScopes)
                val chosen = scopes.intersect(selected)
                val summary = if (scopes.isEmpty()) "此库没有可应用图案" else
                    (if (chosen.isEmpty()) "未选择 · " else if (chosen.size < scopes.size) "部分已选 · " else "已选择 · ") +
                        scopes.joinToString("、") { iconSectionTitle(it) }
                UiSwitchRow(category.title, summary, chosen.isNotEmpty(), app.canEdit && !app.busy && scopes.isNotEmpty()) { checked ->
                    selected = if (checked) selected + scopes else selected - scopes
                }
            }
            UiRow("细分范围", "单独选择快捷开关、亮度、页脚等", if (editScopes) "收起" else "展开") { editScopes = !editScopes }
            if (editScopes) {
                iconUiCategories.forEach { category ->
                    val scopes = category.scopes.filter { it in availableScopes }
                    if (scopes.isNotEmpty()) {
                        UiRow(category.title)
                        scopes.forEach { scope -> UiSwitchRow(iconSectionTitle(scope), "缺失的状态继续使用默认图案", scope in selected, app.canEdit && !app.busy) { checked ->
                            selected = if (checked) selected + scope else selected - scope
                        } }
                    }
                }
            }
            preview?.let { result ->
                UiRow("应用后", trf("替换 %1\$s 个状态 · 缺失 %2\$s 个状态使用默认图案", result.replaceCount, result.missingCount) +
                    if (result.preserveCount > 0) trf("\n保留 %s 个已有单项选择", result.preserveCount) else "")
            }
            if (availableScopes.isEmpty()) UiRow("没有可直接应用的目标", "这套资源没有已接入的匹配状态，可以加入图标库后手动选取。")
        }
        if (!targetSelection) UiGroupCard {
            UiRow("图库颜色预设", "选择后先预览；仅影响上方选中的范围，大小与图案来源独立保留。")
            UiRow("保留当前颜色", "应用套装时沿用已有颜色与透明度", if (presetId == null) "已选择" else "", enabled = !app.busy) { presetId = null }
            palettes.forEach { (id, palette) ->
                UiRow(palette.name, trf("%s 项配色", palette.appearances.size), if (presetId == id) "已选择" else "", enabled = !app.busy) { presetId = id }
            }
            UiContentTabs(tabs = listOf("浅色背景预览", "深色背景预览"), selectedTabIndex = if (presetDark) 1 else 0,
                onTabSelected = { presetDark = it == 1 }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp))
            C17TextButton("仅应用颜色预设", enabled = app.canEdit && !app.busy && preset != null && selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), onClick = {
                    preset?.let { palette ->
                        error = app.applyIconColorPreset(palette, selected)
                        if (error.isEmpty()) app.toast("颜色预设已保存，图案来源与大小保持原样")
                    }
                })
        }
        if (pending && (nameError.isNotEmpty() || app.iconImportError.isNotEmpty()))
            Text(nameError.ifEmpty { app.iconImportError }, color = MiuixTheme.colorScheme.error, fontSize = 13.sp)
        if (error.isNotEmpty()) Text(error, color = MiuixTheme.colorScheme.error, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            C17TextButton(text = "取消", modifier = Modifier.weight(1f), enabled = !app.busy, onClick = onCancel)
            C17TextButton(text = if (targetSelection) { if (existing != null) "使用已有库选图" else "选择图案" }
                else if (app.iconManagementEnabled()) { if (existing != null) "使用已有库并应用" else "应用套装" } else "保存选择",
                modifier = Modifier.weight(1f), enabled = app.canEdit && !app.busy && nameError.isEmpty() && (targetSelection || selected.isNotEmpty() && preview != null), onClick = {
                if (targetSelection) {
                    if (pending) app.confirmIconImport() else app.finishIconLibraryForTarget(pack.id)
                } else if (pending) app.commitIconImportAndApply(selected, clearOverrides, presetId)
                else {
                    error = app.applyIconPack(pack.id, selected, clearOverrides, presetId)
                    if (error.isEmpty()) onApplied()
                }
            })
        }
        UiGroupCard {
            UiRow(if (targetSelection) "素材信息" else "更多选项", if (targetSelection) "来源、格式与支持范围" else "单项选择与来源信息", if (advanced) "收起" else "展开") { advanced = !advanced }
            if (advanced) {
                if (!targetSelection) UiSwitchRow("替换已有的单项图案", "只清除所选范围的图案覆盖，保留颜色、透明度和动画设置", clearOverrides, app.canEdit && !app.busy) { clearOverrides = it }
                UiRow("素材信息", "${packFormats(pack)}\n${pack.license.ifBlank { "未提供许可信息" }}")
                val unsupported = unsupportedArtworkCount(pack)
                if (unsupported > 0) UiRow("原始候选与未接入素材", trf("%s 个资源不自动映射到原生宿主。", unsupported))
                preview?.let { result ->
                    if (result.missingCount > 0) UiRow("缺失状态", result.missing.take(12).joinToString("、") { roleTitle(it) } + if (result.missingCount > 12) "…" else "")
                    if (result.unsupportedCount > 0) UiRow("未接入目标", trf("%s 个状态继续使用原生图案。", result.unsupportedCount))
                }
                if (!targetSelection && pending) UiRow(if (existing != null) "使用已有图标库" else "仅加入图标库",
                    if (existing != null) "打开已保存的相同内容，不修改应用范围" else "保存素材，稍后用于自定义", if (existing != null) "打开" else "保存",
                    enabled = app.canEdit && !app.busy && nameError.isEmpty(), onClick = app::confirmIconImport)
            }
        }
        if (!pending) UiGroupCard {
            onRename?.let { UiRow("重命名图标库", enabled = app.canEdit && !app.busy, onClick = it) }
            UiRow("删除图标库", "删除前会显示受影响的选择", enabled = app.canEdit && !app.busy) { app.deleteIconPack(pack.id, iconPackName(pack)) }
        }
        Text(tr("预览为素材图案；显示位置、状态与动画以实际支持范围为准。“仅应用颜色预设”会立即保存配色；其余选择在应用套装后保存。"), fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
private fun IconArtworkBrowser(app: ModernMainActivity, pack: IconManagementRepository.Pack,
    target: IconRoleRegistry.Target? = null, selected: IconManagementSettings.Override? = null, onChoose: ((String) -> Unit)? = null) {
    var query by remember(pack.id) { mutableStateOf("") }
    var category by remember(pack.id) { mutableStateOf(if (target == null) "all" else "matching") }
    var showRaw by remember(pack.id) { mutableStateOf(false) }
    var page by remember(pack.id, query, category, showRaw) { mutableIntStateOf(0) }
    val matching = target?.roles?.filter { sourceRoleInPack(pack, it) != null }?.toSet().orEmpty()
    val categories = buildList {
        if (target != null) add("matching" to "当前目标")
        add("all" to "全部分类")
        IconRoleRegistry.SCOPES.filter { scope -> availablePackRoles(pack).any { IconRoleRegistry.scopeOfRole(it) == scope } }
            .forEach { add(it to iconSectionTitle(it)) }
    }
    val roles = availablePackRoles(pack).filter { role ->
        !isOriginalCandidate(role) && (category == "all" || category == "matching" && role in matching || IconRoleRegistry.scopeOfRole(role) == category) &&
            (query.isBlank() || roleTitle(role).contains(query.trim(), true) || role.contains(query.trim(), true))
    }
    val raw = pack.manifest.icons.keys.filter { role ->
        isOriginalCandidate(role) && (category in listOf("all", "matching") || IconRoleRegistry.scopeOfRole(role) == category) &&
            (query.isBlank() || role.contains(query.trim(), true) || IconRoleRegistry.scopeOfRole(role)?.let { iconSectionTitle(it).contains(query.trim(), true) } == true)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        C17TextField(value = query, onValueChange = { query = it }, label = "搜索图案或状态", modifier = Modifier.fillMaxWidth(), singleLine = true,
            useLabelAsPlaceholder = true, insideMargin = DpSize(12.dp, 10.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            categories.forEach { (id, label) -> C17TextButton(text = if (id == category) "✓ $label" else label, onClick = { category = id }) }
        }
        UiGroupCard {
            if (roles.isEmpty()) UiRow("没有匹配图案", "试试其他分类或搜索名称。")
            else IconArtworkGrid(app, pack, roles, page, selected, onChoose)
            val lastPage = (roles.size - 1).coerceAtLeast(0) / 24
            if (lastPage > 0) Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                C17TextButton(text = "上一页", modifier = Modifier.weight(1f), enabled = page > 0, onClick = { page-- })
                Text("${page + 1}/${lastPage + 1}", Modifier.align(Alignment.CenterVertically), fontSize = 12.sp)
                C17TextButton(text = "下一页", modifier = Modifier.weight(1f), enabled = page < lastPage, onClick = { page++ })
            }
        }
        if (raw.isNotEmpty()) UiGroupCard {
            UiRow("原始素材", trf("%s 个来源原图，可手动选择；已核对的状态也列在上方分类", raw.size), if (showRaw) "收起" else "展开") { showRaw = !showRaw }
            if (showRaw) {
                Text(tr("选择后仅使用静态图形，不代表新增宿主或动态支持。"), Modifier.padding(horizontal = 12.dp, vertical = 8.dp), fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                IconRawArtwork(app, pack, raw, selected, onChoose)
            }
        }
    }
}

@Composable
private fun IconRawArtwork(app: ModernMainActivity, pack: IconManagementRepository.Pack, roles: List<String>,
    selected: IconManagementSettings.Override?, onChoose: ((String) -> Unit)?) {
    var page by remember(pack.id, roles) { mutableIntStateOf(0) }
    IconArtworkGrid(app, pack, roles, page, selected, onChoose)
    val lastPage = (roles.size - 1).coerceAtLeast(0) / 24
    if (lastPage > 0) Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        C17TextButton(text = "上一页", modifier = Modifier.weight(1f), enabled = page > 0, onClick = { page-- })
        Text("${page + 1}/${lastPage + 1}", Modifier.align(Alignment.CenterVertically), fontSize = 12.sp)
        C17TextButton(text = "下一页", modifier = Modifier.weight(1f), enabled = page < lastPage, onClick = { page++ })
    }
}

@Composable
private fun IconArtworkGrid(app: ModernMainActivity, pack: IconManagementRepository.Pack, roles: List<String>, page: Int,
    selected: IconManagementSettings.Override?, onChoose: ((String) -> Unit)?) {
    roles.drop(page * 24).take(24).chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row.forEach { role ->
                val source = sourceRoleInPack(pack, role) ?: role
                Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(enabled = onChoose != null && app.canEdit && !app.busy) { onChoose?.invoke(source) }
                    .padding(horizontal = 4.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    IconArtwork(app, pack, role, roleTitle(role))
                    val label = IconRoleRegistry.role(role)?.title ?: IconRoleRegistry.target(role)?.title ?: trf("原始 · %s", role.substringAfterLast('.'))
                    Text((if (selected?.packId == pack.id && selected.sourceRole == source) "✓ " else "") + label,
                        fontSize = 12.sp, maxLines = 3, modifier = Modifier.padding(top = 6.dp))
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

private data class IconLookDraft(val mode: String = "inherit", val light: Int = 0xff000000.toInt(),
    val dark: Int = 0xffffffff.toInt(), val opacity: Float? = null, val sizePercent: Float? = null) {
    fun value(): IconManagementSettings.Appearance? = if (mode == "inherit" && opacity == null && sizePercent == null) null
        else IconManagementSettings.Appearance(mode, light or 0xff000000.toInt(), dark or 0xff000000.toInt(), opacity, sizePercent)
}
private fun iconLook(value: IconManagementSettings.Appearance?): IconLookDraft = if (value == null) IconLookDraft()
    else IconLookDraft(value.mode, value.lightColor, value.darkColor, value.opacity, value.sizePercent)

private data class IconTargetDraft(
    val baseRecords: String,
    val sources: Map<String, IconManagementSettings.Override>, val look: IconLookDraft,
    val stateLooks: Map<String, IconLookDraft>, val animation: Boolean, val activeState: String,
    val pickingRole: String?, val previewDark: Boolean, val advanced: Boolean, val individual: Boolean
) {
    fun records(targetId: String): String = IconManagementSettings.applyTarget(baseRecords, targetId, sources, look.value(),
        stateLooks.mapNotNull { (role, value) -> value.value()?.let { role to it } }.toMap(), animation)

    fun rebase(target: IconRoleRegistry.Target, currentRecords: String, availablePacks: Map<String, IconManagementManifest>?): IconTargetDraft {
        if (baseRecords == currentRecords && availablePacks == null) return this
        val draftRecords = records(target.id)
        val mergedRecords = IconManagementSettings.rebaseTargetDraft(baseRecords, draftRecords, currentRecords, target.id, availablePacks)
        if (baseRecords == currentRecords && mergedRecords == draftRecords) return this
        val merged = IconManagementSettings.parse(mergedRecords)
        return copy(baseRecords = currentRecords,
            sources = target.roles.mapNotNull { role -> merged.overrides[role]?.let { role to it } }.toMap(),
            look = iconLook(merged.appearances["target:${target.id}"]),
            stateLooks = target.roles.mapNotNull { role -> merged.appearances["role:$role"]?.let { role to iconLook(it) } }.toMap(),
            animation = merged.animations[target.id] != false)
    }
}

@Composable
private fun IconTargetEditor(app: ModernMainActivity, target: IconRoleRegistry.Target, state: IconManagementSettings.State,
    childBack: Array<(() -> Boolean)?>, initialDraft: IconTargetDraft?, initiallyPick: Boolean, preferredPackId: String?,
    onDraft: (IconTargetDraft) -> Unit, onApplied: () -> Unit) {
    val currentRecords = remember(state) { IconManagementSettings.encode(state) }
    val manifests = remember(app.iconPacks) { app.iconPacks.associate { it.id to it.manifest } }
    // Refresh retains the previous library list. Its interim state is never proof of deletion.
    val availablePacks = if (app.iconLibrariesLoading || app.busy) null else manifests
    var working by remember(target.id) { mutableStateOf(initialDraft ?: IconTargetDraft(currentRecords,
        target.roles.mapNotNull { role -> state.overrides[role]?.let { role to it } }.toMap(),
        iconLook(state.appearances["target:${target.id}"]),
        target.roles.mapNotNull { role -> state.appearances["role:$role"]?.let { role to iconLook(it) } }.toMap(),
        state.animations[target.id] != false, target.roles.first(), null, false, false, false)) }
    // Rebase synchronously for this frame; click handlers also recheck the latest saved records.
    val draft = remember(working, currentRecords, target.id, availablePacks) { working.rebase(target, currentRecords, availablePacks) }
    val sources = draft.sources
    val look = draft.look
    val stateLooks = draft.stateLooks
    val animation = draft.animation
    val activeState = draft.activeState
    val pickingRole = draft.pickingRole
    val previewDark = draft.previewDark
    val advanced = draft.advanced
    val individual = draft.individual
    var pickingSource by remember(target.id) { mutableStateOf(initiallyPick) }
    var error by remember(target.id) { mutableStateOf("") }
    SideEffect {
        if (working != draft) working = draft
        onDraft(draft)
    }
    val back by rememberUpdatedState<() -> Boolean> { if (pickingSource) { pickingSource = false; true } else false }
    DisposableEffect(target.id) { childBack[0] = { back() }; onDispose { childBack[0] = null } }
    if (pickingSource) {
        IconSourcePicker(app, target, pickingRole, sources, preferredPackId, onBack = { pickingSource = false }, onOpenLibrary = {
            onDraft(draft)
            app.openIconLibraryForTarget(target.id)
        }) { selected ->
            working = draft.copy(sources = selected)
            pickingSource = false
        }
        return
    }
    val draftState = remember(draft, target.id) {
        runCatching { IconManagementSettings.parse(draft.records(target.id)) }.getOrDefault(state)
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        UiGroupCard {
            UiRow("图案预览", if (sources.isEmpty()) "跟随套装或系统默认" else trf("已选择 %s 个状态图案", sources.size))
            UiContentTabs(tabs = listOf("浅色背景", "深色背景"), selectedTabIndex = if (previewDark) 1 else 0,
                onTabSelected = { working = draft.copy(previewDark = it == 1) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp))
            target.roles.chunked(3).forEach { row -> Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { role ->
                    val plan = IconManagementSelection.plan(draftState, role, manifests)
                    val asset = plan.candidates.firstOrNull()
                    val pack = app.iconPacks.firstOrNull { it.id == asset?.packId }
                    val color = IconManagementSelection.appearance(draftState, role, previewDark, asset?.asset?.render ?: "mask")
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        IconArtwork(app, pack, asset?.role, roleTitle(role), tint = when {
                            color.mode == "custom" -> color.color
                            color.artwork -> null
                            previewDark -> 0xffffffff.toInt()
                            else -> 0xff000000.toInt()
                        }, opacity = color.opacity, previewDark = previewDark, geometryRole = role, sizePercent = color.sizePercent, largePreview = true)
                        Text(IconRoleRegistry.role(role)?.let { target.stateLabels[it.state] } ?: roleTitle(role), fontSize = 11.sp,
                            maxLines = 2, modifier = Modifier.padding(top = 4.dp), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            } }
            UiRow("选择图案", "为整个目标选择状态组或统一图案", "更换", enabled = app.canEdit && !app.busy) {
                working = draft.copy(pickingRole = null); pickingSource = true
            }
            if (target.reason.isNotBlank()) UiRow("显示范围", target.reason)
        }
        IconAppearanceEditor(if (target.scope == "hint") "图标大小与浅深背景颜色" else "此图标的大小与颜色",
            look, app.canEdit && !app.busy, childBack) { working = draft.copy(look = it) }
        if (target.scope == "tile" && target.roles.size > 1) {
            IconSectionLabel("各状态独立颜色")
            UiGroupCard {
                UiContentTabs(tabs = target.roles.map { target.stateLabels[IconRoleRegistry.role(it)?.state] ?: roleTitle(it) },
                    selectedTabIndex = target.roles.indexOf(activeState).coerceAtLeast(0),
                    onTabSelected = { working = draft.copy(activeState = target.roles[it]) }, modifier = Modifier.fillMaxWidth().padding(8.dp))
                UiRow("只调整当前状态", roleTitle(activeState))
            }
            IconAppearanceEditor(trf("%s配色", roleTitle(activeState)), stateLooks[activeState] ?: IconLookDraft(),
                app.canEdit && !app.busy, childBack, showSize = false) { value ->
                working = draft.copy(stateLooks = stateLooks.toMutableMap().apply { if (value.value() == null) remove(activeState) else put(activeState, value) })
            }
        }
        if (error.isNotEmpty()) Text(error, color = MiuixTheme.colorScheme.error, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            C17TextButton(text = "取消", modifier = Modifier.weight(1f), enabled = !app.busy, onClick = onApplied)
            C17TextButton(text = if (app.iconManagementEnabled()) "应用到此图标" else "保存选择", modifier = Modifier.weight(1f), enabled = app.canEdit && !app.busy && !app.iconLibrariesLoading && target.supported, onClick = {
                error = runCatching {
                    check(!app.iconLibrariesLoading) { "图标库仍在读取，请稍后保存" }
                    val latest = draft.rebase(target, app.iconManagementRecords(), app.iconPacks.associate { it.id to it.manifest })
                    app.applyIconTarget(target.id, latest.sources, latest.look.value(), latest.stateLooks.mapNotNull { (role, value) -> value.value()?.let { role to it } }.toMap(), latest.animation)
                }.getOrElse { it.message ?: "图标设置已变化，请返回后重新选择" }
                if (error.isEmpty()) onApplied()
            })
        }
        UiGroupCard {
            UiRow("其他设置", "逐个状态的图案与动画", if (advanced) "收起" else "展开") { working = draft.copy(advanced = !advanced) }
        }
        if (advanced) {
            UiGroupCard {
                UiRow("逐个状态设置", "只调整选中的状态", if (individual) "收起" else "展开") { working = draft.copy(individual = !individual) }
                if (individual) {
                    target.roles.forEach { role -> UiRow(roleTitle(role), value = if (activeState == role) "已选择" else "") { working = draft.copy(activeState = role) } }
                    UiRow("更换当前状态图案", roleTitle(activeState), "选择", enabled = app.canEdit && !app.busy) {
                        working = draft.copy(pickingRole = activeState); pickingSource = true
                    }
                }
                UiSwitchRow("使用套装动画", if (target.dynamicSupported) "跟随真实状态变化；仅在宿主允许时播放" else "此目标仅支持静态图案", animation,
                    app.canEdit && !app.busy && target.dynamicSupported) { working = draft.copy(animation = it) }
            }
            if (individual && target.scope != "tile") IconAppearanceEditor(trf("%s外观", roleTitle(activeState)), stateLooks[activeState] ?: IconLookDraft(), app.canEdit && !app.busy, childBack) { value ->
                working = draft.copy(stateLooks = stateLooks.toMutableMap().apply { if (value.value() == null) remove(activeState) else put(activeState, value) })
            }
            UiGroupCard {
                UiRow("恢复此目标的默认设置", "清空本页草稿，点击应用后保存", "恢复", enabled = app.canEdit && !app.busy) {
                    working = draft.copy(sources = emptyMap(), look = IconLookDraft(), stateLooks = emptyMap(), animation = true)
                }
            }
        }
        Text(tr("图案与高级设置统一保存；取消或返回会丢弃本次草稿。切换套装或应用颜色预设后，已保存的新值优先，其他草稿修改保留。"), fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
private fun IconSourcePicker(app: ModernMainActivity, target: IconRoleRegistry.Target, role: String?,
    sources: Map<String, IconManagementSettings.Override>, preferredPackId: String?, onBack: () -> Unit, onOpenLibrary: () -> Unit,
    onChoose: (Map<String, IconManagementSettings.Override>) -> Unit) {
    val selected = sources[role ?: target.roles.first()]
    var selectedPack by remember(target.id, role, preferredPackId) { mutableStateOf(
        preferredPackId?.takeIf { id -> app.iconPacks.any { it.id == id } } ?: selected?.packId?.takeIf { it.isNotBlank() } ?: app.iconPacks.firstOrNull()?.id.orEmpty()) }
    var choosePack by remember { mutableStateOf(false) }
    fun chooseSingle(value: IconManagementSettings.Override?) {
        val roles = role?.let { listOf(it) } ?: target.roles
        onChoose((if (role == null) emptyMap() else sources).toMutableMap().apply { roles.forEach { current ->
            if (value == null) remove(current) else put(current, value)
        } })
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        UiGroupCard {
            UiRow(if (role == null) target.title else roleTitle(role), if (role == null) "默认一起设置全部状态；选择后返回预览。" else "只更改当前状态，其他状态保持草稿。")
            UiRow("跟随套装", "使用此范围的套装，缺失时使用默认图案", enabled = !app.busy) { chooseSingle(null) }
            UiRow("使用系统图案", if (role == null) "此目标不使用套装替换" else "此状态不使用套装替换", enabled = !app.busy) { chooseSingle(IconManagementSettings.Override("system", "", "")) }
        }
        if (role == null) {
            val matchingPacks = app.iconPacks.filter { pack -> target.roles.any { sourceRoleInPack(pack, it) != null } }
                .sortedBy { if (it.id == selectedPack) 0 else 1 }
            if (matchingPacks.isNotEmpty()) IconSectionLabel("整组选择 · 保留各状态的区别")
            matchingPacks.forEach { pack ->
                val matches = target.roles.mapNotNull { current -> sourceRoleInPack(pack, current)?.let { current to IconManagementSettings.Override("pack", pack.id, it) } }.toMap()
                UiGroupCard {
                    UiRow(iconPackName(pack), trf("匹配 %1\$s/%2\$s 个状态，缺失状态跟随套装或系统", matches.size, target.roles.size), "选用", enabled = app.canEdit && !app.busy) { onChoose(matches) }
                    Row(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        matches.keys.take(5).forEach { IconArtwork(app, pack, it, roleTitle(it)) }
                    }
                }
            }
        }
        val pack = app.iconPacks.firstOrNull { it.id == selectedPack }
        if (pack != null) {
            IconSectionLabel(if (role == null) "或选择统一图案" else "选择此状态的图案")
            UiGroupCard {
                UiRow("图案来源", iconPackName(pack), if (choosePack) "收起" else "切换") { choosePack = !choosePack }
                if (choosePack) app.iconPacks.forEach { entry -> UiRow(iconPackName(entry), value = if (selectedPack == entry.id) "已选择" else "") {
                    selectedPack = entry.id; choosePack = false
                } }
            }
            IconArtworkBrowser(app, pack, target, selected) { source -> chooseSingle(IconManagementSettings.Override("pack", pack.id, source)) }
        }
        UiGroupCard {
            UiRow(if (pack == null) "从图库选择图案" else "添加或选择其他图库", "选择已有图库、当前内置套装，或导入自己的 v3 图标包", "选择", Icons.Outlined.Collections,
                enabled = app.canEdit && !app.busy, onClick = onOpenLibrary)
        }
        C17TextButton(text = "返回图标预览", onClick = onBack, enabled = !app.busy, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun IconScopeAppearance(app: ModernMainActivity, scope: String, state: IconManagementSettings.State,
    childBack: Array<(() -> Boolean)?>, onApplied: () -> Unit) {
    var draft by remember(scope) { mutableStateOf(iconLook(state.appearances["scope:$scope"])) }
    var error by remember { mutableStateOf("") }
    DisposableEffect(scope) { childBack[0] = null; onDispose { childBack[0] = null } }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        IconAppearanceEditor("范围外观", draft, app.canEdit && !app.busy, childBack) { draft = it }
        Text(tr("只改变此范围的默认外观，各目标和状态的独立设置仍优先。返回不保存草稿。"), fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        if (error.isNotEmpty()) Text(error, color = MiuixTheme.colorScheme.error, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            C17TextButton(text = "取消", modifier = Modifier.weight(1f), enabled = !app.busy, onClick = onApplied)
            C17TextButton(text = if (app.iconManagementEnabled()) "应用外观" else "保存外观", modifier = Modifier.weight(1f), enabled = app.canEdit && !app.busy, onClick = {
                error = app.applyIconAppearance("scope:$scope", draft.value())
                if (error.isEmpty()) onApplied()
            })
        }
    }
}

@Composable
private fun IconAppearanceEditor(title: String, draft: IconLookDraft, enabled: Boolean, childBack: Array<(() -> Boolean)?>,
    showSize: Boolean = true, onChange: (IconLookDraft) -> Unit) {
    var colorSide by remember { mutableStateOf<String?>(null) }
    val closeColor by rememberUpdatedState<() -> Boolean> { colorSide = null; true }
    DisposableEffect(colorSide) {
        if (colorSide != null) {
            val previous = childBack[0]
            val dismiss: () -> Boolean = { closeColor() }
            childBack[0] = dismiss
            onDispose { if (childBack[0] === dismiss) childBack[0] = previous }
        } else onDispose { }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.padding(horizontal = 12.dp), fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        UiGroupCard {
            if (showSize) {
                UiSwitchRow("独立图案缩放", "100%使用功能页或系统的基础尺寸；仅调整图案，位置、占位和点击区域保持原有设置", draft.sizePercent != null, enabled) {
                    onChange(draft.copy(sizePercent = if (it) 100f else null))
                }
                draft.sizePercent?.let { size ->
                    UiSliderRow("图案缩放", size, 25f..200f, "${size.toInt()}%", enabled = enabled) { onChange(draft.copy(sizePercent = it)) }
                    C17TextButton("恢复100%大小", enabled = enabled, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), onClick = { onChange(draft.copy(sizePercent = 100f)) })
                }
            }
            val modes = listOf("inherit" to "跟随上级设置", "system" to "跟随系统颜色", "custom" to "自定义颜色", "artwork" to "保留素材原色")
            modes.forEach { (mode, label) -> UiRow(label,
                if (mode == "artwork") "彩色素材保留原色；蒙版仍按系统着色" else "", if (draft.mode == mode) "已选择" else "", enabled = enabled) { onChange(draft.copy(mode = mode)) } }
            if (draft.mode == "custom") UiColorPair("浅色背景 / 深色背景", draft.light, draft.dark, enabled,
                onLight = { colorSide = "light" }, onDark = { colorSide = "dark" })
            UiSwitchRow("独立透明度", "关闭时继承上级，默认100%；保留系统状态与过渡透明度", draft.opacity != null, enabled) {
                onChange(draft.copy(opacity = if (it) 100f else null))
            }
            draft.opacity?.let { opacity -> UiSliderRow("图标透明度", opacity, 0f..100f, "${opacity.toInt()}%", enabled = enabled) { onChange(draft.copy(opacity = it)) } }
        }
    }
    colorSide?.let { side -> IconRgbDialog(if (side == "light") "浅色背景颜色" else "深色背景颜色", if (side == "light") draft.light else draft.dark,
        onDismiss = { colorSide = null }) { color ->
        onChange(if (side == "light") draft.copy(light = color) else draft.copy(dark = color)); colorSide = null
    } }
}

@Composable
private fun IconRgbDialog(title: String, initial: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var color by remember(initial) { mutableIntStateOf(initial or 0xff000000.toInt()) }
    fun hex(value: Int) = "#%06X".format(java.util.Locale.ROOT, value and 0xffffff)
    var text by remember(initial) { mutableStateOf(hex(initial)) }
    var error by remember { mutableStateOf("") }
    C17OverlayDialog(show = true, title = title, summary = "只选择 RGB 颜色，图标透明度在外观中单独设置。", onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(color)))
            listOf(16 to "红", 8 to "绿", 0 to "蓝").forEach { (shift, label) ->
                val value = (color ushr shift) and 255
                UiSliderRow(label, value.toFloat(), 0f..255f, value.toString()) { selected ->
                    color = (color and (255 shl shift).inv()) or (selected.toInt().coerceIn(0, 255) shl shift) or 0xff000000.toInt()
                    text = hex(color); error = ""
                }
            }
            C17TextField(value = text, onValueChange = { input ->
                text = input; error = ""
                val digits = input.removePrefix("#")
                if (digits.matches(Regex("[0-9a-fA-F]{6}"))) color = digits.toInt(16) or 0xff000000.toInt()
            }, label = "#RRGGBB", singleLine = true, modifier = Modifier.fillMaxWidth(), insideMargin = DpSize(12.dp, 10.dp))
            if (error.isNotEmpty()) Text(error, color = MiuixTheme.colorScheme.error, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                C17TextButton(text = "取消", modifier = Modifier.weight(1f), onClick = onDismiss)
                C17TextButton(text = "确定", modifier = Modifier.weight(1f), onClick = {
                    if (!text.removePrefix("#").matches(Regex("[0-9a-fA-F]{6}"))) error = "请输入6位 RGB 颜色"
                    else onConfirm(color)
                })
            }
        }
    }
}
