// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ApplicationChoice(val packageName: String, val label: String, val system: Boolean = false)

@Composable
private fun InstalledApplicationChoices(app: ModernMainActivity, allowed: Boolean, onChoose: (ApplicationChoice) -> Unit) {
    var query by remember { mutableStateOf("") }
    var systemOnly by remember { mutableStateOf(false) }
    val choices = remember(app.applicationChoices, query, systemOnly) {
        app.applicationChoices.filter { it.system == systemOnly &&
            (query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true)) }
    }
    var page by remember(app.applicationChoices, query, systemOnly) { mutableIntStateOf(0) }
    val lastPage = ((choices.size - 1).coerceAtLeast(0)) / 16
    TextField(value = query, onValueChange = { query = it }, label = "搜索全部已安装应用", singleLine = true,
        useLabelAsPlaceholder = true, modifier = Modifier.fillMaxWidth(), insideMargin = DpSize(12.dp, 10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(text = if (!systemOnly) "✓ 非系统应用" else "非系统应用", modifier = Modifier.weight(1f), onClick = { systemOnly = false })
        TextButton(text = if (systemOnly) "✓ 系统应用" else "系统应用", modifier = Modifier.weight(1f), onClick = { systemOnly = true })
    }
    choices.drop(page * 16).take(16).forEach { choice -> key(choice.packageName) {
        UiRow(choice.label, choice.packageName, enabled = allowed) { onChoose(choice) }
    } }
    if (choices.isEmpty()) Text(if (app.applicationChoices.isEmpty()) "正在读取应用列表，可先填写包名。" else "没有符合条件的应用。",
        fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    else {
        Text("共 ${choices.size} 个应用 · 第 ${page + 1} / ${lastPage + 1} 页，包含没有桌面入口的应用。", fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        if (lastPage > 0) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(text = "上一页", enabled = page > 0, modifier = Modifier.weight(1f), onClick = { page-- })
            TextButton(text = "下一页", enabled = page < lastPage, modifier = Modifier.weight(1f), onClick = { page++ })
        }
    }
}

@Composable
fun NotificationOverridesDialog(app: ModernMainActivity) {
    val json = app.value(NotificationIconOverrides.RULES)?.toString() ?: "[]"
    val rules = remember(json) { runCatching { NotificationIconOverrides.rules(json) }.getOrDefault(emptyList()) }
    var editingPackage by remember { mutableStateOf<String?>(null) }
    var selectedPackage by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var replacementType by remember { mutableStateOf(NotificationIconOverrides.TYPE_TEXT) }
    var sourcePackage by remember { mutableStateOf("") }
    var selectingTarget by remember { mutableStateOf(false) }
    var selectingSource by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val allowed = app.canEdit && !app.busy
    fun edit(rule: NotificationIconOverrides.Rule?) {
        editingPackage = rule?.packageName ?: ""
        selectedPackage = rule?.packageName ?: ""
        replacement = rule?.text ?: ""
        replacementType = rule?.type ?: NotificationIconOverrides.TYPE_TEXT
        sourcePackage = rule?.sourcePackage ?: ""
        selectingTarget = rule == null; selectingSource = false; error = ""
    }
    OverlayDialog(show = true, title = if (editingPackage == null) "指定应用通知图标" else "设置替换图标",
        onDismissRequest = { app.notificationOverridesOpen = false }) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (editingPackage == null) {
                Text("选择文字、表情或另一个应用的彩色图标。只替换状态栏小图标，通知内容和分组不变。", fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    if (rules.isEmpty()) UiRow("尚未添加应用", "点击下方添加，其他应用保留原生图标")
                    rules.forEach { rule -> key(rule.packageName) {
                        UiGroupCard {
                            UiSwitchRow(app.applicationLabel(rule.packageName), if (rule.isAppIcon()) "图标来自 ${app.applicationLabel(rule.sourcePackage)}" else "替换为 ${rule.text}", rule.enabled, allowed) {
                                if (rule.isAppIcon()) {
                                    val message = app.saveNotificationAppIconOverride(rule.packageName, rule.sourcePackage, it, rule.packageName)
                                    if (message.isNotEmpty()) app.toast(message)
                                }
                                else app.saveNotificationOverride(rule.packageName, rule.text, it, rule.packageName)
                            }
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(text = "编辑", enabled = allowed, modifier = Modifier.weight(1f), onClick = { edit(rule) })
                                TextButton(text = "删除", enabled = allowed, modifier = Modifier.weight(1f), onClick = { app.removeNotificationOverride(rule.packageName) })
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    } }
                }
                TextButton(text = "添加应用", enabled = allowed && rules.size < 128,
                    modifier = Modifier.fillMaxWidth(), onClick = { edit(null) })
                TextButton(text = "完成", modifier = Modifier.fillMaxWidth(), onClick = { app.notificationOverridesOpen = false })
            } else {
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextField(value = selectedPackage, onValueChange = { selectedPackage = it; error = "" }, label = "应用包名",
                        singleLine = true, useLabelAsPlaceholder = true, modifier = Modifier.fillMaxWidth(), insideMargin = DpSize(12.dp, 10.dp))
                    TextButton(text = if (selectingTarget) "收起通知应用列表" else "选择通知所属应用", enabled = allowed,
                        modifier = Modifier.fillMaxWidth(), onClick = { selectingTarget = !selectingTarget })
                    if (selectingTarget) {
                        InstalledApplicationChoices(app, allowed) { selectedPackage = it.packageName; selectingTarget = false; error = "" }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(text = if (replacementType == NotificationIconOverrides.TYPE_TEXT) "✓ 文字与表情" else "文字与表情",
                            enabled = allowed, modifier = Modifier.weight(1f), onClick = { replacementType = NotificationIconOverrides.TYPE_TEXT; error = "" })
                        TextButton(text = if (replacementType == NotificationIconOverrides.TYPE_APP_ICON) "✓ 应用图标" else "应用图标",
                            enabled = allowed, modifier = Modifier.weight(1f), onClick = { replacementType = NotificationIconOverrides.TYPE_APP_ICON; selectingSource = sourcePackage.isBlank(); error = "" })
                    }
                    if (replacementType == NotificationIconOverrides.TYPE_APP_ICON) {
                        TextField(value = sourcePackage, onValueChange = { sourcePackage = it; error = "" }, label = "图标来源应用包名",
                            singleLine = true, useLabelAsPlaceholder = true, modifier = Modifier.fillMaxWidth(), insideMargin = DpSize(12.dp, 10.dp))
                        TextButton(text = if (selectingSource) "收起图标来源应用列表" else "选择提供图标的应用", enabled = allowed,
                            modifier = Modifier.fillMaxWidth(), onClick = { selectingSource = !selectingSource })
                        if (selectingSource) InstalledApplicationChoices(app, allowed) {
                            sourcePackage = it.packageName; selectingSource = false; error = ""
                        }
                        Text("保留来源应用图标的颜色。来源应用卸载或图标读取失败时，恢复原生通知图标。", fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    } else {
                        TextField(value = replacement, onValueChange = { replacement = it; error = "" }, label = "替换文字或表情",
                            singleLine = true, useLabelAsPlaceholder = true, modifier = Modifier.fillMaxWidth(), insideMargin = DpSize(12.dp, 10.dp))
                        Text("最多 8 个字符，可使用 ❤️、✨ 等表情。尺寸跟随原生图标或通知图标区域的大小设置。", fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                    if (error.isNotEmpty()) Text(error, fontSize = 13.sp, color = MiuixTheme.colorScheme.primary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(text = "返回", modifier = Modifier.weight(1f), onClick = { editingPackage = null })
                    TextButton(text = "确定", enabled = allowed, modifier = Modifier.weight(1f), onClick = {
                        val old = rules.firstOrNull { it.packageName == editingPackage }
                        error = if (replacementType == NotificationIconOverrides.TYPE_APP_ICON)
                            app.saveNotificationAppIconOverride(selectedPackage.trim(), sourcePackage.trim(), old?.enabled ?: true, editingPackage)
                        else app.saveNotificationOverride(selectedPackage.trim(), replacement, old?.enabled ?: true, editingPackage)
                        if (error.isEmpty()) editingPackage = null
                    })
                }
            }
        }
    }
}

private val iconPatternPreviews = object : android.util.LruCache<String, android.graphics.Bitmap>(1024 * 1024) {
    override fun sizeOf(key: String, value: android.graphics.Bitmap) = value.width * value.height
}

@Composable
private fun IconPatternChoice(app: ModernMainActivity, pack: IconPackRepository.Pack, sourceRole: String,
                              selected: Boolean, allowed: Boolean, onChoose: () -> Unit) {
    val path = pack.manifest.icons[sourceRole] ?: return
    val previewKey = "${pack.id}/$path"
    val pixels by produceState<android.graphics.Bitmap?>(null, previewKey) {
        value = withContext(Dispatchers.IO) {
            iconPatternPreviews.get(previewKey) ?: runCatching {
                app.contentResolver.openInputStream(IconPackRepository.assetUri(pack.id, path))?.use { input ->
                    val data = IconPackArchive.readEntry(input, IconPackArchive.MAX_ENTRY)
                    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
                    if (bounds.outWidth !in 1..256 || bounds.outHeight !in 1..256) null
                    else android.graphics.BitmapFactory.decodeByteArray(data, 0, data.size)?.also { iconPatternPreviews.put(previewKey, it) }
                }
            }.getOrNull()
        }
    }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
        .background(if (selected) MiuixTheme.colorScheme.primary.copy(alpha = .10f) else MiuixTheme.colorScheme.surface)
        .clickable(enabled = allowed, onClick = onChoose).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        val preview = pixels
        if (preview != null) Image(remember(preview) { preview.asImageBitmap() }, IconPackAssignments.label(sourceRole), Modifier.size(36.dp),
            colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurface))
        else Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { Text("…", fontSize = 20.sp) }
        Column(Modifier.weight(1f)) {
            Text((if (selected) "✓ " else "") + IconPackAssignments.label(sourceRole), fontSize = 15.sp)
            Text(if (selected) "已选择用于替换目标图标" else "点选这张图案", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}

@Composable
fun IconAssignmentsDialog(app: ModernMainActivity) {
    val categories = remember { listOf("hint" to "系统提示", "wifi" to "Wi-Fi", "cellular" to "蜂窝", "battery" to "电池") }
    val json = app.value(IconPackAssignments.ASSIGNMENTS)?.toString() ?: "[]"
    val assignments = remember(json) { runCatching { IconPackAssignments.rules(json) }.getOrDefault(emptyList()) }
    val layersJson = app.value(IconPackRepository.LAYERS)?.toString() ?: "[]"
    val layers = remember(layersJson) { runCatching { IconPackRepository.layers(layersJson) }.getOrDefault(emptyList()) }
    val activePacks = remember(layers, app.iconPacks) {
        val enabled = layers.filter { it.enabled }.map { it.id }.toSet()
        app.iconPacks.filter { it.id in enabled }
    }
    var target by remember { mutableStateOf<String?>(null) }
    var selectedPack by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("") }
    var category by remember(app.iconAssignmentCategory) {
        mutableStateOf(app.iconAssignmentCategory.takeIf { id -> categories.any { it.first == id } } ?: "hint")
    }
    var error by remember { mutableStateOf("") }
    val allowed = app.canEdit && !app.busy
    val scroll = MiuixScrollBehavior(rememberTopAppBarState(initialHeightOffsetLimit = 0f))
    fun edit(role: String) {
        target = role
        val old = assignments.firstOrNull { it.targetRole == role }
        selectedPack = old?.packId ?: activePacks.firstOrNull()?.id.orEmpty()
        selectedRole = old?.sourceRole.orEmpty()
        error = ""
    }
    fun back() {
        if (target != null) target = null else app.iconAssignmentsOpen = false
    }
    Scaffold(containerColor = MiuixTheme.colorScheme.surface, topBar = {
        SmallTopAppBar(title = if (target == null) "逐项替换系统图标" else "选择替换图案",
            scrollBehavior = scroll, navigationIcon = {
                IconButton(onClick = ::back) { Icon(Icons.Outlined.ArrowBack, "返回") }
            })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            if (target == null) TabRow(tabs = categories.map { it.second },
                selectedTabIndex = categories.indexOfFirst { it.first == category }.coerceAtLeast(0),
                onTabSelected = {
                    category = categories[it].first
                    app.iconAssignmentCategory = category
                }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                minWidth = 88.dp, itemSpacing = 6.dp)
            key(target, category) {
                Column(Modifier.weight(1f).fillMaxWidth().nestedScroll(scroll.nestedScrollConnection)
                    .verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 12.dp,
                        bottom = padding.calculateBottomPadding() + 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val editingTarget = target
                    if (editingTarget == null) {
                        UiGroupCard {
                            UiRow("逐项选择图案", "先选择要修改的系统图标，再挑选图标库中的图案。每个目标单独保存，优先于整库自动匹配。")
                        }
                        val roles = remember(category, assignments) {
                            (IconPackAssignments.targetRoles() + assignments.map { it.targetRole })
                                .distinct().filter { it.startsWith("$category.") }
                        }
                        UiGroupCard {
                            roles.forEach { role -> key(role) {
                                val assignment = assignments.firstOrNull { it.targetRole == role }
                                val pack = app.iconPacks.firstOrNull { it.id == assignment?.packId }
                                val available = assignment != null && activePacks.any { it.id == assignment.packId }
                                    && pack?.manifest?.icons?.containsKey(assignment.sourceRole) == true
                                val unavailable = IconPackAssignments.unavailableTargetReason(role)
                                UiRow(IconPackAssignments.label(role),
                                    when {
                                        unavailable.isNotEmpty() -> unavailable
                                        available -> "${pack?.name} · ${IconPackAssignments.label(assignment?.sourceRole)}"
                                        assignment != null -> "来源库已停用或缺失，当前跟随整库或系统图标"
                                        else -> "选择用于替换的图案"
                                    }, value = if (assignment != null) "已设置" else "", enabled = allowed) { edit(role) }
                            } }
                        }
                        UiGroupCard {
                            UiRow("管理图标库", "导入、启用图标库后，就可以在这里选择图案", enabled = !app.busy) {
                                app.iconAssignmentsOpen = false
                                app.openIconLibrary()
                            }
                        }
                    } else {
                        val old = assignments.firstOrNull { it.targetRole == editingTarget }
                        val unavailable = IconPackAssignments.unavailableTargetReason(editingTarget)
                        UiGroupCard {
                            UiRow(IconPackAssignments.label(editingTarget),
                                if (unavailable.isNotEmpty()) unavailable else "选择下方的一张图案，只替换这个目标图标。")
                            UiRow("恢复默认", "移除此项分配，跟随整库自动匹配或系统图标", enabled = allowed && old != null) {
                                app.removeIconAssignment(editingTarget)
                                target = null
                            }
                        }
                        if (activePacks.isEmpty()) {
                            UiGroupCard {
                                UiRow("还没有启用的图标库", "先导入并启用一个图标库，再返回选择图案", enabled = !app.busy) {
                                    app.iconAssignmentsOpen = false
                                    app.openIconLibrary()
                                }
                            }
                        } else {
                            UiGroupCard {
                                UiRow("图案来源", "选择提供图案的图标库")
                                activePacks.forEach { pack -> key(pack.id) {
                                    UiRow(pack.name, "${pack.iconCount} 个图案",
                                        value = if (selectedPack == pack.id) "已选择" else "", enabled = allowed) {
                                        selectedPack = pack.id
                                        selectedRole = ""
                                        error = ""
                                    }
                                } }
                            }
                            val pack = activePacks.firstOrNull { it.id == selectedPack }
                            if (pack != null) {
                                val sourceRoles = remember(pack.id, pack.manifest) { pack.manifest.icons.keys.toList() }
                                var page by remember(pack.id, editingTarget) {
                                    mutableIntStateOf(sourceRoles.indexOf(selectedRole).coerceAtLeast(0) / 16)
                                }
                                val lastPage = (sourceRoles.size - 1).coerceAtLeast(0) / 16
                                UiGroupCard {
                                    sourceRoles.drop(page * 16).take(16).forEach { sourceRole -> key(sourceRole) {
                                        IconPatternChoice(app, pack, sourceRole, selectedRole == sourceRole, allowed) {
                                            selectedRole = sourceRole
                                            error = ""
                                        }
                                    } }
                                }
                                if (lastPage > 0) UiGroupCard {
                                    UiRow("图案列表", "共 ${sourceRoles.size} 个图案", value = "${page + 1}/${lastPage + 1}")
                                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(text = "上一页", enabled = page > 0, modifier = Modifier.weight(1f), onClick = { page-- })
                                        TextButton(text = "下一页", enabled = page < lastPage, modifier = Modifier.weight(1f), onClick = { page++ })
                                    }
                                }
                            }
                            if (error.isNotEmpty()) UiGroupCard { UiRow("无法保存", error) }
                            UiGroupCard {
                                val canSave = allowed && activePacks.any {
                                    it.id == selectedPack && it.manifest.icons.containsKey(selectedRole)
                                }
                                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(text = "返回", modifier = Modifier.weight(1f), onClick = { target = null })
                                    TextButton(text = "确认替换", enabled = canSave, modifier = Modifier.weight(1f), onClick = {
                                        error = app.saveIconAssignment(editingTarget, selectedPack, selectedRole)
                                        if (error.isEmpty()) target = null
                                    })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IconLibraryDialog(app: ModernMainActivity) {
    var mode by remember { mutableStateOf("list") }
    var selectedId by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val layersJson = app.value(IconPackRepository.LAYERS)?.toString() ?: "[]"
    val layers = remember(layersJson) { runCatching { IconPackRepository.layers(layersJson) }.getOrDefault(emptyList()) }
    val orderedPacks = remember(layers, app.iconPacks) {
        val order = layers.mapIndexed { i, item -> item.id to i }.toMap()
        app.iconPacks.sortedWith(compareBy<IconPackRepository.Pack> { order[it.id] ?: Int.MAX_VALUE }.thenBy { it.name })
    }
    var sorting by remember { mutableStateOf<List<String>>(emptyList()) }
    var dragging by remember { mutableStateOf<String?>(null) }
    val step = with(LocalDensity.current) { 72.dp.toPx() }
    val allowed = app.canEdit && !app.busy
    val scroll = MiuixScrollBehavior(rememberTopAppBarState(initialHeightOffsetLimit = 0f))
    fun move(id: String, delta: Int): Boolean {
        val index = sorting.indexOf(id)
        val destination = (index + delta).coerceIn(0, sorting.lastIndex.coerceAtLeast(0))
        if (index < 0 || index == destination) return false
        sorting = sorting.toMutableList().apply { removeAt(index); add(destination, id) }
        return true
    }
    fun back() {
        if (app.busy) return
        if (mode == "list") app.iconLibraryOpen = false else { mode = "list"; dragging = null }
    }
    Scaffold(containerColor = MiuixTheme.colorScheme.surface, topBar = {
        SmallTopAppBar(title = when (mode) {
            "github" -> "从 GitHub 导入"
            "rename" -> "重命名图标库"
            "sort" -> "图标库优先级"
            else -> "图标库"
        }, scrollBehavior = scroll, navigationIcon = {
            IconButton(onClick = ::back, enabled = !app.busy) { Icon(Icons.Outlined.ArrowBack, "返回") }
        })
    }) { padding ->
        key(mode) {
            Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())
                .nestedScroll(scroll.nestedScrollConnection)
                .verticalScroll(rememberScrollState(), enabled = dragging == null)
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = padding.calculateBottomPadding() + 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (mode) {
                    "github", "rename" -> {
                        UiGroupCard {
                            UiRow(if (mode == "github") "导入图标库" else "修改名称",
                                if (mode == "github") "支持 GitHub 仓库与下载链接。导入后默认关闭整库自动匹配。"
                                else "只修改这个图标库的显示名称，已选择的图案保持不变。")
                        }
                        UiGroupCard {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (mode == "github") TextField(value = source, onValueChange = { source = it; error = "" },
                                    label = "GitHub 仓库或 ZIP / RAR 链接", singleLine = true,
                                    useLabelAsPlaceholder = true, modifier = Modifier.fillMaxWidth(), insideMargin = DpSize(12.dp, 10.dp))
                                TextField(value = name, onValueChange = { name = it; error = "" },
                                    label = if (mode == "github") "名称（可不填）" else "图标库名称", singleLine = true,
                                    useLabelAsPlaceholder = true, modifier = Modifier.fillMaxWidth(), insideMargin = DpSize(12.dp, 10.dp))
                                if (mode == "github") Text("可填写 owner/repo，或 GitHub 上的图标包下载链接。", fontSize = 13.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            }
                        }
                        if (error.isNotEmpty()) UiGroupCard { UiRow("请检查输入", error) }
                        UiGroupCard {
                            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(text = "返回", enabled = !app.busy, modifier = Modifier.weight(1f), onClick = { mode = "list" })
                                TextButton(text = if (mode == "github") "下载并导入" else "确定", enabled = allowed,
                                    modifier = Modifier.weight(1f), onClick = {
                                        if (mode == "github") { app.downloadIconPack(source.trim(), name); mode = "list" }
                                        else if (name.trim().isEmpty()) error = "请填写名称"
                                        else { app.renameIconPack(selectedId, name); mode = "list" }
                                    })
                            }
                        }
                    }
                    "sort" -> {
                        UiGroupCard {
                            UiRow("调整图标库优先级", "长按名称上下拖动。开启整库自动匹配的图标库按这个顺序使用，靠前的优先。")
                        }
                        sorting.forEach { id -> key(id) {
                            val pack = app.iconPacks.firstOrNull { it.id == id }
                            var distance by remember { mutableFloatStateOf(0f) }
                            UiGroupCard {
                                Row(Modifier.fillMaxWidth().height(60.dp)
                                    .background(if (dragging == id) MiuixTheme.colorScheme.primary.copy(alpha = .10f) else MiuixTheme.colorScheme.surface)
                                    .semantics { customActions = listOf(
                                        CustomAccessibilityAction("上移") { allowed && move(id, -1) },
                                        CustomAccessibilityAction("下移") { allowed && move(id, 1) }) }
                                    .pointerInput(id, allowed) {
                                        if (!allowed) return@pointerInput
                                        detectDragGesturesAfterLongPress(onDragStart = { dragging = id; distance = 0f },
                                            onDragEnd = { dragging = null }, onDragCancel = { dragging = null }) { change, amount ->
                                            change.consume()
                                            distance += amount.y
                                            while (distance >= step) { if (!move(id, 1)) { distance = 0f; break }; distance -= step }
                                            while (distance <= -step) { if (!move(id, -1)) { distance = 0f; break }; distance += step }
                                        }
                                    }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(pack?.name ?: "未找到的图标库", Modifier.weight(1f), fontSize = 16.sp)
                                    Icon(Icons.Outlined.DragHandle, "长按拖动", Modifier.size(22.dp),
                                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                }
                            }
                        } }
                        UiGroupCard {
                            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(text = "返回", modifier = Modifier.weight(1f), onClick = { mode = "list" })
                                TextButton(text = "确定", enabled = allowed, modifier = Modifier.weight(1f),
                                    onClick = { if (app.saveIconPackOrder(sorting)) mode = "list" })
                            }
                        }
                    }
                    else -> {
                        UiGroupCard {
                            UiRow("图案与整库设置", "启用图标库后，可逐项选择图案。整库自动匹配会替换这套库支持的图标，逐项设置优先。")
                        }
                        UiGroupCard {
                            UiRow("从文件导入", "选择 ZIP、RAR 4 或 RAR 5 图标包", enabled = allowed, onClick = app::chooseIconPack)
                            UiRow("从 GitHub 导入", "填写仓库或下载链接", enabled = allowed) { mode = "github"; error = "" }
                            UiRow("导入 PUI 主题", "选择 PUI 主题 ZIP，提取支持的图标并加入图标库", enabled = allowed) { app.openPuiThemeImport() }
                        }
                        UiGroupCard {
                            UiRow("PUI 主题图案", "需要选择自己的主题文件。应用不附带 PUI 原素材，导入后可在逐项替换中选择图案。")
                        }
                        if (app.busy) UiGroupCard {
                            UiRow("正在处理图标库", "下载、解包与校验在后台完成")
                            if (app.iconPackDownloading) UiRow("取消下载", enabled = true, onClick = app::cancelIconPackDownload)
                        }
                        if (orderedPacks.isEmpty() && !app.busy) UiGroupCard {
                            UiRow("尚未导入图标库", "从上方选择文件、GitHub 或 PUI 主题导入")
                        }
                        orderedPacks.forEach { pack -> key(pack.id) {
                            val layer = layers.firstOrNull { it.id == pack.id }
                            UiGroupCard {
                                UiSwitchRow(pack.name, "${pack.author} · ${pack.iconCount} 个图案",
                                    layer?.enabled == true, allowed) { app.setIconPackEnabled(pack.id, it) }
                                UiSwitchRow("整库自动匹配", "替换这套图标库支持的所有图标", layer?.autoMatch == true,
                                    allowed && layer?.enabled == true) { app.setIconLibraryAutoMatch(pack.id, it) }
                                UiRow("重命名", "当前名称：${pack.name}", enabled = allowed) {
                                    selectedId = pack.id; name = pack.name; error = ""; mode = "rename"
                                }
                                UiRow("删除图标库", "删除后，使用这个库的图标恢复整库或系统图案", enabled = allowed) {
                                    app.deleteIconPack(pack.id, pack.name)
                                }
                            }
                        } }
                        UiGroupCard {
                            UiRow("图标库优先级", "长按排序，靠前的图标库优先", enabled = allowed && orderedPacks.size > 1) {
                                sorting = orderedPacks.map { it.id }; mode = "sort"
                            }
                            UiRow("制作规范", "查看图标包格式与制作说明", enabled = !app.busy) {
                                app.openUrl("https://github.com/SANWU5/c17-icon-packs")
                            }
                        }
                    }
                }
            }
        }
    }
}
