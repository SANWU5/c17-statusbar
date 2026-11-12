// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val orderRegions = listOf(StatusBarOrderDraft.LEFT, StatusBarOrderDraft.RIGHT, StatusBarOrderDraft.NATIVE)
private val hintRegions = listOf(HintIconOrderDraft.LEFT, HintIconOrderDraft.RIGHT, HintIconOrderDraft.INHERIT, HintIconOrderDraft.NATIVE)
private fun hintToken(slot: String) = "hint:$slot"
private fun hintSlot(token: String?) = token?.takeIf { it.startsWith("hint:") }?.substring(5)?.takeIf { it in HintIconOrderDraft.SLOTS }
private fun hintRegionTitle(side: String) = when(side) {
    HintIconOrderDraft.LEFT -> "左侧提示"
    HintIconOrderDraft.RIGHT -> "右侧提示"
    HintIconOrderDraft.NATIVE -> "跟随原生侧边"
    else -> "跟随提示组"
}
private fun editorTokenTitle(token: String, shared: Boolean) = hintSlot(token)?.let(NativeStatusIcons::priorityLabel) ?: orderElementTitle(token, shared)
private fun orderRegionTitle(side: String) = when (side) {
    StatusBarOrderDraft.LEFT -> "左侧"
    StatusBarOrderDraft.RIGHT -> "右侧"
    else -> "跟随系统"
}
private fun orderElementTitle(token: String, shared: Boolean) =
    if (shared && token == StatusBarLayoutSettings.WIFI) "Wi-Fi / 网络制式" else StatusBarLayoutSettings.label(token)
/** Short names for the one-line status bar preview. */
private fun orderShortTitle(token: String) = when (token) {
    StatusBarLayoutSettings.NOTIFICATION -> "通知"
    StatusBarLayoutSettings.MOBILE -> "信号"
    StatusBarLayoutSettings.SYSTEM -> "提示"
    StatusBarLayoutSettings.CARRIER -> "运营商(下拉)"
    else -> StatusBarLayoutSettings.label(token)
}
private data class OrderDrop(val side: String, val index: Int)
/** The carrier exists only in the pull-down header and on the lock screen, and may be hidden. */
private const val CARRIER_NOTE = "运营商只在下拉栏顶部和锁屏显示，普通状态栏没有运营商；系统或本模块隐藏运营商时，这一项的位置不会生效。"

/**
 * Status bar order editor. Each side is a left-to-right row of chips, exactly as on the status bar:
 * tap a chip and use the panel at the bottom, or long-press and drag it to another place.
 * Nothing is written until the draft is applied in one transaction.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StatusBarOrderPage(app: ModernMainActivity, requested: String?, onRequestConsumed: () -> Unit, active: Boolean = true) {
    val current = remember(app.values) { app.statusBarOrderValues() }
    var baseline by rememberSaveable { mutableStateOf(HashMap(current)) }
    var fields by rememberSaveable { mutableStateOf(HashMap(current)) }
    var history by rememberSaveable { mutableStateOf(arrayListOf<HashMap<String, Any>>()) }
    var historyTokens by rememberSaveable { mutableStateOf(arrayListOf<String?>()) }
    var selectedToken by rememberSaveable { mutableStateOf<String?>(null) }
    val draft = remember(fields) { StatusBarOrderDraft.from(fields) }
    val hintDraft = remember(fields) { HintIconOrderDraft.from(fields[NativeStatusIcons.LAYOUT] as? String) }
    var hintsExpanded by rememberSaveable { mutableStateOf(false) }
    var revealHints by remember { mutableStateOf(false) }
    var hintAnchor by remember { mutableIntStateOf(-1) }
    var hintLayoutReady by remember { mutableStateOf(false) }
    val boardScroll = rememberScrollState()
    val dirty = fields != baseline
    val sourceChanged = current != baseline
    val allowed = active && app.canEdit && !app.busy
    val editable = allowed && !sourceChanged
    val shared = app.bool(StatusBarLayoutSettings.WIFI_LABEL_SHARED)
    val enabled = app.bool(StatusBarLayoutSettings.MASTER)
    val paused = if (app.bool(StatusBarSettings.SAFE_MODE)) "安全模式已开启，排序选择仍保存" else ""
    val applying = enabled && paused.isEmpty()
    val labelKey = StatusBarLayoutSettings.key(StatusBarLayoutSettings.LABEL, "order")
    val labelOrder = (fields[labelKey] as? Number)?.toInt() ?: 0
    val topState = rememberC17TopAppBarState(initialHeightOffsetLimit = 0f)
    val scroll = rememberC17ScrollBehavior(topState)
    val chipBounds = remember { mutableStateMapOf<String, Rect>() }
    val zoneBounds = remember { mutableStateMapOf<String, Rect>() }
    var boardOrigin by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragPoint by remember { mutableStateOf(Offset.Zero) }
    var dragGrab by remember { mutableStateOf(Offset.Zero) }
    val latestEditable by rememberUpdatedState(editable)
    val latestDraft by rememberUpdatedState(draft)
    val latestHints by rememberUpdatedState(hintDraft)
    fun updateFields(next: HashMap<String, Any>, editedToken: String? = null) {
        if (!latestEditable || next == fields) return
        history = ArrayList(history.takeLast(29)).apply { add(HashMap(fields)) }
        historyTokens = ArrayList(historyTokens.takeLast(29)).apply { add(editedToken) }
        fields = next
    }
    fun updateDraft(next: StatusBarOrderDraft, editedToken: String? = null) {
        updateFields(HashMap(fields).apply { putAll(next.sideValues()); putAll(next.orderValues()) }, editedToken)
    }
    fun updateHints(next: HintIconOrderDraft, editedToken: String? = null) {
        updateFields(HashMap(fields).apply { put(NativeStatusIcons.LAYOUT, next.serialize()) }, editedToken)
    }
    fun reload() {
        val latest = app.statusBarOrderValues()
        baseline = HashMap(latest); fields = HashMap(latest); history = arrayListOf(); historyTokens = arrayListOf()
    }
    fun requestDiscard(leave: Boolean) {
        if (fields == baseline) { if (leave) app.finishStatusBarOrder() else reload(); return }
        app.confirmation = UiConfirmation("放弃未应用的排列？", "区域和顺序的更改尚未保存。",
            if (leave) "放弃并返回" else "放弃更改") {
            reload()
            if (leave) app.finishStatusBarOrder()
        }
    }
    val latestClose by rememberUpdatedState<() -> Unit>({
        if (dragging != null) dragging = null else requestDiscard(true)
    })
    DisposableEffect(app, active) {
        val close: () -> Unit = { latestClose() }
        if (active) app.statusBarOrderCloseRequest = close
        onDispose { if (app.statusBarOrderCloseRequest === close) app.statusBarOrderCloseRequest = null }
    }
    LaunchedEffect(current) {
        if (!dirty) { baseline = HashMap(current); fields = HashMap(current); history = arrayListOf(); historyTokens = arrayListOf() }
    }
    LaunchedEffect(editable) { if (!editable) dragging = null }
    LaunchedEffect(requested, active, app.hintIconOrderRequested) {
        if (!active) return@LaunchedEffect
        if (requested == NativeStatusIcons.LAYOUT || app.hintIconOrderRequested) {
            if (!hintsExpanded) hintLayoutReady = false
            hintsExpanded = true; revealHints = true; selectedToken = null
            if (requested == NativeStatusIcons.LAYOUT) onRequestConsumed()
            return@LaunchedEffect
        }
        if (requested == null) return@LaunchedEffect
        StatusBarLayoutSettings.TOKENS.firstOrNull {
            requested == StatusBarLayoutSettings.key(it, "side") || requested == StatusBarLayoutSettings.key(it, "order")
        }?.let { selectedToken = it }
        onRequestConsumed()
    }
    LaunchedEffect(revealHints, hintAnchor, hintsExpanded, hintLayoutReady) {
        if (revealHints && hintsExpanded && hintLayoutReady && hintAnchor >= 0) {
            boardScroll.scrollTo(hintAnchor); revealHints = false; app.hintIconOrderRequested = false
        }
    }
    fun place(token: String, side: String, index: Int) {
        if (!latestEditable) return
        val slot = hintSlot(token)
        if (slot != null) {
            val next = latestHints.place(slot, side, index)
            if (next != latestHints) updateHints(next, token)
        } else {
            val next = latestDraft.place(token, side, index)
            if (next != latestDraft) updateDraft(next, token)
        }
        selectedToken = token
    }
    fun moveToSide(token: String, side: String) {
        // A new side appends at its physical right end; the arrows then refine it.
        val slot = hintSlot(token)
        place(token, side, if(slot == null) latestDraft.tokens(side).count { it != token } else latestHints.slots(side).count { it != slot })
    }
    fun moveWithin(token: String, amount: Int) {
        if (!latestEditable) return
        val slot = hintSlot(token)
        if (slot != null) {
            val next = latestHints.moveWithin(slot, amount)
            if (next != latestHints) updateHints(next, token)
        } else {
            val next = latestDraft.moveWithin(token, amount)
            if (next != latestDraft) updateDraft(next, token)
        }
    }
    // Rows are read like text: the drop index counts the chips before the finger.
    fun dropTarget(point: Offset): OrderDrop? {
        val token = dragging ?: return null
        val hint = hintSlot(token) != null
        val side = (if(hint) hintRegions else orderRegions).firstOrNull { zoneBounds[if(hint) "hint-$it" else it]?.contains(point) == true } ?: return null
        if (side == StatusBarOrderDraft.NATIVE || side == HintIconOrderDraft.INHERIT) return OrderDrop(side, 0)
        var index = 0
        val members = if(hint) latestHints.slots(side).map(::hintToken) else latestDraft.tokens(side)
        members.filter { it != token }.forEach { member ->
            val bounds = chipBounds[member] ?: return@forEach
            val sameRow = point.y >= bounds.top && point.y <= bounds.bottom
            if (bounds.bottom < point.y || sameRow && bounds.center.x < point.x) index++
        }
        return OrderDrop(side, index)
    }
    fun endDrag(commit: Boolean) {
        val token = dragging ?: return
        val drop = if (commit && latestEditable) dropTarget(dragPoint) else null
        dragging = null
        if (drop != null) place(token, drop.side, drop.index)
    }
    val latestDragStart by rememberUpdatedState<(String, Offset) -> Unit>({ token, grab ->
        if (latestEditable && dragging == null) chipBounds[token]?.let { bounds ->
            dragging = token; selectedToken = token; dragGrab = grab; dragPoint = bounds.topLeft + grab
        }
    })
    val latestDrag by rememberUpdatedState<(Offset) -> Unit>({ amount -> if (dragging != null) dragPoint += amount })
    val latestDragEnd by rememberUpdatedState<(Boolean) -> Unit>({ endDrag(it) })
    val drop = if (dragging == null) null else dropTarget(dragPoint)
    val selectedElement = selectedToken?.takeIf { it in StatusBarLayoutSettings.TOKENS }
    val selectedHint = hintSlot(selectedToken)
    val controlsEditable = editable && dragging == null
    C17Scaffold(containerColor = MiuixTheme.colorScheme.surface,
        topBar = { C17SmallTopAppBar(title = "状态栏排序（实验性）", scrollBehavior = scroll, navigationIcon = {
            C17IconButton(onClick = app::closeGroup) { Icon(MiuixIcons.Back, "返回") }
        }) }, bottomBar = {
            Column(Modifier.fillMaxWidth().background(MiuixTheme.colorScheme.surface)
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                .padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selectedElement != null) OrderSelectionPanel(selectedElement, orderElementTitle(selectedElement, shared), draft, controlsEditable,
                    onSide = { moveToSide(selectedElement, it) }, onMove = { moveWithin(selectedElement, it) }, onClose = { selectedToken = null })
                if (selectedHint != null) HintOrderSelectionPanel(selectedHint, hintDraft, controlsEditable,
                    onSide = { moveToSide(hintToken(selectedHint), it) }, onMove = { moveWithin(hintToken(selectedHint), it) }, onClose = { selectedToken = null })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    C17TextButton("撤销", modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        enabled = controlsEditable && history.isNotEmpty(), onClick = {
                            val editedToken = historyTokens.lastOrNull()
                            fields = HashMap(history.last()); history = ArrayList(history.dropLast(1))
                            historyTokens = ArrayList(historyTokens.dropLast(1))
                            if (editedToken != null) selectedToken = editedToken
                        })
                    C17TextButton(if (applying) "应用排列" else "保存排列", modifier = Modifier.weight(2f).heightIn(min = 48.dp),
                        enabled = controlsEditable && dirty, primary = true, onClick = {
                            if (app.saveStatusBarOrderDraft(fields, baseline)) {
                                baseline = HashMap(fields); history = arrayListOf(); historyTokens = arrayListOf()
                                app.toast(if (applying) "排列已应用" else if (paused.isNotEmpty()) "排列已保存，解除暂停后生效" else "排列已保存，开启自定义排序后生效")
                            }
                        })
                }
            }
        }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).clipToBounds()
            .onGloballyPositioned { boardOrigin = it.positionInRoot() }) {
            Column(Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection)
                .verticalScroll(boardScroll, enabled = dragging == null)
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                UiGroupCard {
                    val canSwitch = SettingsCatalog.unavailableReason(StatusBarLayoutSettings.MASTER, app.values).isEmpty() || enabled
                    UiSwitchRow("启用自定义排序", if (paused.isNotEmpty()) (if (enabled) "已保存开启 · " else "") + paused
                        else if (enabled) "已启用；调整后点“应用排列”生效" else "关闭时按系统排列；可以先排好再保存",
                        enabled, allowed && canSwitch) { app.save(StatusBarLayoutSettings.MASTER, it) }
                    if (sourceChanged) UiRow("已保存的排列有更新", "重新载入后继续编辑", "重新载入", enabled = allowed) { requestDiscard(false) }
                    if (!app.canEdit) UiRow("激活模块或授予 Root 后即可修改", "当前可以查看排列")
                }
                OrderPreview(draft)
                Text(tr("点一下元素，再用底部按钮换区或左右移动；也可以长按元素直接拖到想要的位置。"),
                    Modifier.padding(horizontal = 4.dp), fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                orderRegions.forEach { side ->
                    val members = draft.tokens(side)
                    OrderZone(side, members, highlighted = hintSlot(dragging) == null && drop?.side == side,
                        onBounds = { zoneBounds[side] = it }) {
                        members.forEach { token ->
                            val order = draft.order(token)
                            OrderChip(orderElementTitle(token, shared), side,
                                index = if (side != StatusBarOrderDraft.NATIVE && order > 0) members.indexOf(token) + 1 else 0,
                                legacyOrder = if (side == StatusBarOrderDraft.NATIVE) order else 0,
                                tag = if (token == StatusBarLayoutSettings.CARRIER) "仅下拉栏" else null,
                                selected = selectedElement == token, lifted = dragging == token, draggable = editable,
                                onBounds = { chipBounds[token] = it }, onRemoved = { chipBounds.remove(token) },
                                onClick = { selectedToken = if (selectedToken == token) null else token },
                                onDragStart = { latestDragStart(token, it) }, onDrag = { latestDrag(it) },
                                onDragEnd = { latestDragEnd(it) },
                                actions = if (!controlsEditable) emptyList() else buildList {
                                    val rank = members.indexOf(token)
                                    if (side != StatusBarOrderDraft.NATIVE && rank > 0) add(CustomAccessibilityAction("向左移") { moveWithin(token, -1); true })
                                    if (side != StatusBarOrderDraft.NATIVE && rank < members.size - 1) add(CustomAccessibilityAction("向右移") { moveWithin(token, 1); true })
                                    orderRegions.filter { it != side }.forEach { destination ->
                                        add(CustomAccessibilityAction(if (destination == StatusBarOrderDraft.NATIVE) "交给系统" else trf("放到%s", orderRegionTitle(destination))) {
                                            moveToSide(token, destination); true
                                        })
                                    }
                                })
                        }
                    }
                }
                Column(Modifier.onGloballyPositioned {
                    hintAnchor = (it.positionInRoot().y - boardOrigin.y + boardScroll.value).toInt().coerceAtLeast(0)
                    hintLayoutReady = hintsExpanded
                },
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    UiGroupCard {
                        val arranged = HintIconOrderDraft.SLOTS.count { hintDraft.side(it) != HintIconOrderDraft.INHERIT }
                        UiRow("系统提示图标逐个排序", if(arranged == 0) "全部跟随上面的提示组" else trf("已单独安排 %s 项；与本页排列一起应用", arranged),
                            if(hintsExpanded) "收起" else "展开", enabled = dragging == null) { hintsExpanded = !hintsExpanded }
                    }
                    if(hintsExpanded) {
                        Text(tr("同侧提示图标按这里的先后排列；整组位置仍用上面的“系统提示图标”锚点。跟随提示组会继承整组设置，跟随原生侧边只让系统决定所在的一侧。数量不足时的显示优先级仍在系统提示图标页设置。"),
                            Modifier.padding(horizontal = 4.dp), fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        hintRegions.forEach { side ->
                            val members = hintDraft.slots(side)
                            OrderZone(side, members, highlighted = dragging?.let(::hintSlot) != null && drop?.side == side,
                                onBounds = { zoneBounds["hint-$side"] = it }, title = hintRegionTitle(side),
                                note = when(side) { HintIconOrderDraft.INHERIT -> "  保留整组的位置与顺序"; HintIconOrderDraft.NATIVE -> "  系统决定侧边，仍使用提示组锚点"; else -> "  同侧提示从左往右" }) {
                                members.forEach { slot -> key(hintToken(slot)) {
                                    val token = hintToken(slot); val rank = members.indexOf(slot)
                                    OrderChip(NativeStatusIcons.priorityLabel(slot), side,
                                        index = if((side == HintIconOrderDraft.LEFT || side == HintIconOrderDraft.RIGHT) && hintDraft.order(slot) > 0) rank + 1 else 0,
                                        legacyOrder = 0, tag = null, selected = selectedHint == slot, lifted = dragging == token, draggable = editable,
                                        onBounds = { chipBounds[token] = it }, onRemoved = { chipBounds.remove(token) },
                                        onClick = { selectedToken = if(selectedHint == slot) null else token },
                                        onDragStart = { latestDragStart(token, it) }, onDrag = { latestDrag(it) }, onDragEnd = { latestDragEnd(it) },
                                        regionName = hintRegionTitle(side), actions = if(!controlsEditable) emptyList() else buildList {
                                            if(side == HintIconOrderDraft.LEFT || side == HintIconOrderDraft.RIGHT) {
                                                if(rank > 0) add(CustomAccessibilityAction("向左移") { moveWithin(token,-1);true })
                                                if(rank < members.size-1) add(CustomAccessibilityAction("向右移") { moveWithin(token,1);true })
                                            }
                                            hintRegions.filter { it != side }.forEach { target -> add(CustomAccessibilityAction(hintRegionTitle(target)) { moveToSide(token,target);true }) }
                                        })
                                } }
                            }
                        }
                        UiGroupCard { UiRow("所有提示跟随整组", "只重置提示图标的单独安排，应用后生效", enabled = controlsEditable) {
                            updateHints(latestHints.resetAll()); selectedToken = null
                        } }
                    }
                }
                UiGroupCard {
                    C17BasicComponent(insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(tr("网络制式与信号"), fontSize = 16.sp)
                        Text(if (shared) "已与 Wi-Fi 共用位置；共用开关在网络页面" else "只调整蜂窝内部先后，不改变上面的排列", fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                    if (!shared) Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("系统顺序", "制式在前", "信号在前").forEachIndexed { index, title ->
                            C17TextButton(title, enabled = controlsEditable, modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                .semantics { this.selected = index == labelOrder },
                                primary = index == labelOrder,
                                onClick = { updateFields(HashMap(fields).apply { put(labelKey, index.toFloat()) }) })
                        }
                    }
                }
                UiGroupCard {
                    UiRow("全部恢复系统排列", "所有元素交给系统，应用后生效", icon = Icons.Outlined.Restore, enabled = controlsEditable) {
                        app.confirmation = UiConfirmation("恢复系统排列？", "所有元素恢复系统区域和顺序，点“应用排列”后生效。", "恢复") {
                            val restored = latestDraft.resetAll()
                            updateFields(HashMap(fields).apply { putAll(restored.sideValues()); putAll(restored.orderValues()); put(labelKey, 0f); put(NativeStatusIcons.LAYOUT, latestHints.resetAll().serialize()) })
                            selectedToken = null
                        }
                    }
                    if (dirty) UiRow("放弃未应用的更改", "回到上次保存的排列", enabled = allowed) { requestDiscard(false) }
                }
                Text(tr("这里列出全部可排序的元素，系统当前没有显示的元素不会被强制显示。各元素的左右间距在它自己的页面设置，不需要开启排序。"),
                    Modifier.padding(horizontal = 4.dp), fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            // Insertion mark and the dragged chip float above the board.
            if (drop != null && drop.side != StatusBarOrderDraft.NATIVE && drop.side != HintIconOrderDraft.INHERIT) {
                val token = dragging
                val hint = hintSlot(token) != null
                val members = (if(hint) hintDraft.slots(drop.side).map(::hintToken) else draft.tokens(drop.side)).filter { it != token }
                val anchor = members.getOrNull(drop.index)?.let { chipBounds[it] }
                val last = members.lastOrNull()?.let { chipBounds[it] }
                val zone = zoneBounds[if(hint) "hint-${drop.side}" else drop.side]
                val mark = when {
                    anchor != null -> Rect(anchor.left - 5f, anchor.top, anchor.left - 2f, anchor.bottom)
                    last != null -> Rect(last.right + 2f, last.top, last.right + 5f, last.bottom)
                    zone != null -> with(LocalDensity.current) {
                        Rect(zone.left + 16.dp.toPx(), zone.bottom - 52.dp.toPx(), zone.left + 19.dp.toPx(), zone.bottom - 16.dp.toPx())
                    }
                    else -> null
                }
                if (mark != null) Box(Modifier.zIndex(1f)
                    .offset { IntOffset((mark.left - boardOrigin.x).toInt(), (mark.top - boardOrigin.y).toInt()) }
                    .size(width = 3.dp, height = with(LocalDensity.current) { mark.height.toDp() })
                    .background(MiuixTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
            }
            dragging?.let { token ->
                val position = dragPoint - dragGrab - boardOrigin
                Text(editorTokenTitle(token, shared), Modifier.zIndex(2f)
                    .offset { IntOffset(position.x.toInt(), position.y.toInt()) }
                    .background(MiuixTheme.colorScheme.primary, RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                    color = MiuixTheme.colorScheme.onPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** One-line sketch of the result: manual elements first on each side, then what the system keeps there. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OrderPreview(draft: StatusBarOrderDraft) {
    val native = draft.tokens(StatusBarOrderDraft.NATIVE)
    UiGroupCard {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("预览（从左到右）"), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Row(Modifier.fillMaxWidth().heightIn(min = 36.dp).clip(RoundedCornerShape(10.dp))
                .background(MiuixTheme.colorScheme.onSurface.copy(alpha = .06f)).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.Start),
                    verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    draft.tokens(StatusBarOrderDraft.LEFT).forEach { PreviewText(orderShortTitle(it)) }
                    if (native.isNotEmpty()) PreviewText("系统…", faint = true)
                }
                Box(Modifier.padding(horizontal = 8.dp).size(8.dp).background(MiuixTheme.colorScheme.onSurface.copy(alpha = .55f), CircleShape))
                FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    draft.tokens(StatusBarOrderDraft.RIGHT).forEach { PreviewText(orderShortTitle(it)) }
                    if (native.isNotEmpty()) PreviewText("系统…", faint = true)
                }
            }
            Text(if (native.isEmpty()) "所有元素都已手动安排。"
                else "“系统…”表示跟随系统的元素：它们留在系统原来的一侧，排在同侧手动元素的右边。",
                fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Text(tr("流体云胶囊出现时保留自定义排序，图标的显示、隐藏和缩放继续跟随系统交互。") +
                if (draft.side(StatusBarLayoutSettings.CARRIER) != StatusBarOrderDraft.NATIVE) "\n$CARRIER_NOTE" else "",
                fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}

@Composable
private fun PreviewText(text: String, faint: Boolean = false) {
    Text(text, fontSize = 12.sp, maxLines = 1, fontWeight = if (faint) FontWeight.Normal else FontWeight.Medium,
        color = if (faint) MiuixTheme.colorScheme.onSurfaceVariantSummary else MiuixTheme.colorScheme.onSurface)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OrderZone(side: String, members: List<String>, highlighted: Boolean, onBounds: (Rect) -> Unit,
    title: String = orderRegionTitle(side), note: String? = null,
    content: @Composable FlowRowScope.() -> Unit) {
    val border by animateColorAsState(if (highlighted) MiuixTheme.colorScheme.primary else Color.Transparent,
        tween(C17Motion.FastMillis), label = "order-zone")
    C17Card(modifier = Modifier.fillMaxWidth().onGloballyPositioned { onBounds(it.boundsInRoot()) }
        .border(2.dp, border, RoundedCornerShape(16.dp)), cornerRadius = 16.dp, insideMargin = PaddingValues(0.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(note ?: when (side) {
                    StatusBarOrderDraft.NATIVE -> "  位置和先后由系统决定"
                    StatusBarOrderDraft.LEFT -> "  从左往右，排在系统左侧元素前面"
                    else -> "  从左往右，排在系统右侧元素前面"
                }, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            FlowRow(Modifier.fillMaxWidth().heightIn(min = 40.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content()
                if (members.isEmpty()) Text(if (side == StatusBarOrderDraft.NATIVE || side == HintIconOrderDraft.INHERIT) "没有元素" else "空 · 选中元素后选择这一侧，或拖到这里",
                    Modifier.padding(vertical = 10.dp), fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        }
    }
}

@Composable
private fun OrderChip(title: String, side: String, index: Int, legacyOrder: Int, tag: String?, selected: Boolean, lifted: Boolean, draggable: Boolean,
    onBounds: (Rect) -> Unit, onRemoved: () -> Unit, onClick: () -> Unit,
    onDragStart: (Offset) -> Unit, onDrag: (Offset) -> Unit, onDragEnd: (Boolean) -> Unit,
    actions: List<CustomAccessibilityAction>, regionName: String = orderRegionTitle(side)) {
    val container by animateColorAsState(if (selected) MiuixTheme.colorScheme.primary
        else MiuixTheme.colorScheme.onSurface.copy(alpha = .07f), tween(C17Motion.FastMillis), label = "order-chip")
    val content = if (selected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurface
    val start by rememberUpdatedState(onDragStart)
    val move by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onDragEnd)
    val removed by rememberUpdatedState(onRemoved)
    DisposableEffect(Unit) { onDispose { removed() } }
    Row(Modifier.heightIn(min = 40.dp).onGloballyPositioned { onBounds(it.boundsInRoot()) }
        .graphicsLayer { alpha = if (lifted) .3f else 1f }
        .clip(RoundedCornerShape(20.dp)).background(container)
        // Keyed on page editability only: a key that flips when the drag starts would cancel it.
        .pointerInput(draggable) {
            if (!draggable) return@pointerInput
            detectDragGesturesAfterLongPress(onDragStart = { start(it) },
                onDragEnd = { end(true) }, onDragCancel = { end(false) },
                onDrag = { change, amount -> change.consume(); move(amount) })
        }
        .clickable(role = Role.Button, onClickLabel = if (selected) trf("取消选择%s", title) else trf("选择%s", title), onClick = onClick)
        .semantics {
            this.selected = selected
            stateDescription = regionName + if (index > 0) trf("第%s个", index) else ""
            customActions = actions
        }
        .padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (index > 0) Text(index.toString(), Modifier.size(20.dp).background(
            if (selected) MiuixTheme.colorScheme.onPrimary.copy(alpha = .25f) else MiuixTheme.colorScheme.primary.copy(alpha = .14f), CircleShape)
            .wrapContentHeight(), fontSize = 11.sp, fontWeight = FontWeight.Medium,
            color = if (selected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.primary,
            textAlign = TextAlign.Center)
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = content)
        if (tag != null) Text(tag, fontSize = 11.sp,
            color = if (selected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurfaceVariantSummary)
        if (legacyOrder > 0) Text(trf("旧顺序%s", legacyOrder), fontSize = 11.sp,
            color = if (selected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

/** Always-visible controls for the selected element, above apply/undo. */
@Composable
private fun OrderSelectionPanel(token: String, title: String, draft: StatusBarOrderDraft, editable: Boolean,
    onSide: (String) -> Unit, onMove: (Int) -> Unit, onClose: () -> Unit) {
    val side = draft.side(token)
    val members = draft.tokens(side)
    val rank = members.indexOf(token)
    C17Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp, insideMargin = PaddingValues(0.dp)) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(if (side == StatusBarOrderDraft.NATIVE) "跟随系统" else trf("%1\$s第 %2\$s 个（共 %3\$s 个）", orderRegionTitle(side), rank + 1, members.size),
                        fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    if (token == StatusBarLayoutSettings.CARRIER) Text(CARRIER_NOTE, fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
                C17IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "取消选择") }
            }
            Row(Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(StatusBarOrderDraft.LEFT, StatusBarOrderDraft.NATIVE, StatusBarOrderDraft.RIGHT).forEach { destination ->
                    val current = destination == side
                    C17TextButton(if (destination == StatusBarOrderDraft.NATIVE) "跟随系统" else trf("放到%s", orderRegionTitle(destination)),
                        modifier = Modifier.weight(1f).heightIn(min = 44.dp).semantics { this.selected = current },
                        enabled = editable && !current, primary = current,
                        onClick = { onSide(destination) })
                }
            }
            if (side != StatusBarOrderDraft.NATIVE) Row(Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                C17TextButton("← 往左", modifier = Modifier.weight(1f).heightIn(min = 44.dp), enabled = editable && rank > 0, onClick = { onMove(-1) })
                C17TextButton("往右 →", modifier = Modifier.weight(1f).heightIn(min = 44.dp), enabled = editable && rank in 0 until members.size - 1, onClick = { onMove(1) })
            }
        }
    }
}

/** Slot controls edit the same page draft; they never save display-retention priority. */
@Composable
private fun HintOrderSelectionPanel(slot: String, draft: HintIconOrderDraft, editable: Boolean,
    onSide: (String) -> Unit, onMove: (Int) -> Unit, onClose: () -> Unit) {
    val side = draft.side(slot)
    val members = draft.slots(side)
    val rank = members.indexOf(slot)
    val physical = side == HintIconOrderDraft.LEFT || side == HintIconOrderDraft.RIGHT
    C17Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp, insideMargin = PaddingValues(0.dp)) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(NativeStatusIcons.priorityLabel(slot), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(if(physical && draft.order(slot) > 0) trf("%1\$s第 %2\$s 个（仅提示图标）", hintRegionTitle(side), rank + 1)
                        else hintRegionTitle(side), fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
                C17IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "取消选择") }
            }
            listOf(listOf(HintIconOrderDraft.LEFT, HintIconOrderDraft.RIGHT),listOf(HintIconOrderDraft.INHERIT, HintIconOrderDraft.NATIVE)).forEach { pair ->
                Row(Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    pair.forEach { destination ->
                        C17TextButton(hintRegionTitle(destination), modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                            .semantics { selected = destination == side }, enabled = editable && destination != side,
                            primary = destination == side,
                            onClick = { onSide(destination) })
                    }
                }
            }
            if(physical) Row(Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                C17TextButton("← 往左",modifier = Modifier.weight(1f).heightIn(min = 44.dp),enabled = editable && rank > 0,onClick = { onMove(-1) })
                C17TextButton("往右 →",modifier = Modifier.weight(1f).heightIn(min = 44.dp),enabled = editable && rank in 0 until members.size-1,onClick = { onMove(1) })
            }
        }
    }
}
