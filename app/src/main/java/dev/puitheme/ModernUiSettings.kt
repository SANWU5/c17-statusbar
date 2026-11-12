// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

/** 关于 → 界面设置: the look of this app only. Few words, one row per choice, the same rows as everywhere else. */
@Composable
fun UiSettingsPage(app: ModernMainActivity) {
    val ui = app.ui
    var choosingUi by rememberSaveable { mutableStateOf(false) }
    var typing by remember { mutableStateOf<String?>(null) }
    // The dark preview stays a moment after the finger lifts, so the result can be seen.
    val scope = rememberCoroutineScope()
    var release by remember { mutableStateOf<Job?>(null) }
    fun preview(on: Boolean) {
        release?.cancel()
        if (on) ui.previewDark = true else release = scope.launch { delay(1500); ui.previewDark = false }
    }
    LaunchedEffect(typing) { if (typing == "dark") preview(true) else if (ui.previewDark) preview(false) }
    DisposableEffect(Unit) { onDispose { release?.cancel(); ui.previewDark = false } }
    val scroll = rememberC17ScrollBehavior(rememberC17TopAppBarState(initialHeightOffsetLimit = 0f))
    val list = rememberLazyListState()
    C17Scaffold(containerColor = MiuixTheme.colorScheme.surface, topBar = {
        C17SmallTopAppBar(title = "界面设置", scrollBehavior = scroll, navigationIcon = {
            C17IconButton(onClick = app::closeUiSettings) { Icon(Icons.Outlined.ArrowBack, "返回") }
        })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())
            .nestedScroll(scroll.nestedScrollConnection).edgeFade(list, ui.edgeFade), state = list,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp,
                bottom = padding.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "ui-style") { UiGroupCard {
                UiRow("界面风格", "选择应用界面的设计风格", ui.uiStyleTitle) { choosingUi = true }
            } }
            item { UiSectionTitle("主题") }
            item { UiGroupCard {
                UiChoiceRow("深浅色", listOf("跟随系统", "浅色", "深色"), ui.themeMode) { ui.updateThemeMode(it) }
            } }
            item { UiSectionTitle("配色") }
            item { UiGroupCard {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("配色方案"), Modifier.weight(1f), fontSize = 16.sp)
                        Text(ui.scheme(ui.scheme).title, fontSize = 14.sp, color = MiuixTheme.colorScheme.primary)
                    }
                    // Keep labels readable on narrow screens and with the user's larger text setting.
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
                        val columns = (maxWidth / (56.dp * fontScale)).toInt().coerceIn(2, 5)
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            UiSettings.SCHEMES.chunked(columns).forEach { line ->
                                Row(Modifier.fillMaxWidth()) {
                                    line.forEach { scheme -> Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { SchemeSwatch(ui, scheme) } }
                                    repeat(columns - line.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
                UiChoiceRow("色彩风格", listOf("柔和", "鲜艳", "素净"), ui.paletteStyle, enabled = ui.scheme(ui.scheme).theme == null) { ui.updatePaletteStyle(it) }
                UiTypableSlider("深色背景", ui.darkLevel.toFloat(), 0f..100f,
                    when { ui.darkLevel >= 98 -> "纯黑"; ui.darkLevel == 50 -> "默认"; ui.darkLevel > 50 -> "偏黑"; else -> "偏灰" },
                    onType = { typing = "dark" }, onDragging = ::preview) { ui.updateDarkLevel(it.roundToInt()) }
            } }
            item { UiSectionTitle("名称") }
            item { UiGroupCard {
                UiSwitchRow("恢复旧名称", trf("桌面图标显示“%1\$s”；系统设置和 LSPosed 里的应用名仍是 %2\$s", LauncherIcon.LEGACY_NAME, LauncherIcon.NAME),
                    app.legacyName, enabled = app.desktopIconAvailable && !app.busy && !app.maintenanceRunning,
                    onChecked = app::requestLegacyName)
            } }
            item { UiSectionTitle("开源主题") }
            item { UiGroupCard {
                UiThemes.ALL.forEach { theme -> ThemeRow(ui, theme) }
            } }
            item { UiSectionNote("主题配色来自开源项目，保持原作颜色；选用后色彩风格不再适用，深浅色与深色背景仍然有效。") }
            item { UiSectionTitle("显示") }
            item { UiGroupCard {
                UiTypableSlider("界面字号", ui.textScale.toFloat(), 80f..130f, "${ui.textScale}%", onType = { typing = "scale" }) { ui.updateTextScale(it.roundToInt()) }
                UiSwitchRow("滚动渐隐", checked = ui.edgeFade) { ui.updateEdgeFade(it) }
                val glass = GlassNavigationPreference.supported()
                val material = ui.style == C17UiStyle.MATERIAL
                UiSwitchRow("液态玻璃底栏", when {
                    !glass -> "需要 Android 13 或更新版本"
                    material -> "Material 风格使用贴底导航栏，切换到其他风格后生效"
                    else -> ""
                }, checked = app.glassNavigation && !material, enabled = glass && !material) { app.updateGlassNavigation(it) }
            } }
            item { UiGroupCard {
                UiRow("恢复默认", icon = Icons.Outlined.RestartAlt) { app.resetUi(); app.toast("界面设置已恢复默认") }
            } }
        }
        if (choosingUi) UiChoiceDialog(
            title = "界面风格",
            values = C17UiStyle.entries.map { it.id },
            labels = C17UiStyle.entries.map { it.title },
            selected = ui.uiStyle,
            optionSummary = { id -> C17UiStyle.of(id).summary },
            onDismiss = { choosingUi = false },
            onChoice = { app.updateUiStyle(it) },
        )
        // Tapping the value types it in, like every other number in the app.
        when (typing) {
            "scale" -> UiInputDialog("界面字号", ui.textScale.toString(), description = "80–130，单位 %", numeric = true, integer = true,
                allowNegative = false, label = "字号 %", onDismiss = { typing = null }) { text ->
                val value = text.trim().toDoubleOrNull()
                if (value == null || !value.isFinite() || value < 80 || value > 130) "请输入 80 到 130 之间的数字"
                else { ui.updateTextScale(value.roundToInt()); null }
            }
            "dark" -> UiInputDialog("深色背景", ui.darkLevel.toString(), description = "0 偏灰，50 默认，100 纯黑", numeric = true, integer = true,
                allowNegative = false, label = "黑度", onDismiss = { typing = null }) { text ->
                val value = text.trim().toDoubleOrNull()
                if (value == null || !value.isFinite() || value < 0 || value > 100) "请输入 0 到 100 之间的数字"
                else { ui.updateDarkLevel(value.roundToInt()); null }
            }
        }
    }
}

/** A slider whose value can be tapped to type an exact number. */
@Composable
private fun UiTypableSlider(title: String, value: Float, range: ClosedFloatingPointRange<Float>, valueText: String,
    onType: () -> Unit, onDragging: (Boolean) -> Unit = {}, onChange: (Float) -> Unit) {
    var current by remember(value) { mutableStateOf(value) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable(onClick = onType), fontSize = 16.sp)
            Text(valueText, Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onType).padding(horizontal = 8.dp, vertical = 6.dp),
                fontSize = 14.sp, color = MiuixTheme.colorScheme.primary)
        }
        C17Slider(value = current, onValueChange = { onDragging(true); current = it; onChange(it) }, valueRange = range,
            onValueChangeFinished = { onDragging(false) })
    }
}

@Composable
private fun UiSectionTitle(title: String) = UiSectionHeader(title)

/** The choices grow with the labels and font scale, sharing the content-sized tab control. */
@Composable
private fun UiChoiceRow(title: String, labels: List<String>, selected: Int, enabled: Boolean = true, onSelected: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp).alpha(if (enabled) 1f else .4f),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, fontSize = 16.sp)
        UiSegmented(labels, selected.coerceIn(0, labels.lastIndex), enabled, onSelected)
    }
}

/** One rounded track with a filled thumb on the chosen segment: a single, uniform control. */
@Composable
private fun UiSegmented(labels: List<String>, selected: Int, enabled: Boolean, onSelected: (Int) -> Unit) {
    UiContentTabs(labels, selected, onSelected, contour = true, enabled = enabled)
}

/** An open-source theme: the colours it uses in the current mode, its name and where its palette comes from. */
@Composable
private fun ThemeRow(ui: UiSettings, theme: UiTheme) {
    val selected = ui.scheme == theme.id
    val ink = MiuixTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth()
        .selectable(selected = selected, role = Role.RadioButton,
            interactionSource = remember { MutableInteractionSource() }, indication = null) { ui.updateScheme(theme.id) }
        .padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        val shape = RoundedCornerShape(10.dp)
        Row(Modifier.clip(shape).border(1.dp, ink.copy(alpha = .12f), shape)) {
            theme.swatch(LocalC17Dark.current).forEach { Box(Modifier.size(width = 14.dp, height = 36.dp).background(it)) }
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(theme.title, fontSize = 16.sp)
            Text(theme.summary, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        Box(Modifier.size(22.dp).clip(CircleShape)
            .background(if (selected) MiuixTheme.colorScheme.primary else Color.Transparent)
            .border(if (selected) 0.dp else 1.5.dp, ink.copy(alpha = .3f), CircleShape),
            contentAlignment = Alignment.Center) {
            if (selected) Box(Modifier.size(8.dp).clip(CircleShape).background(MiuixTheme.colorScheme.onPrimary))
        }
    }
}

@Composable
private fun SchemeSwatch(ui: UiSettings, scheme: UiScheme) {
    val selected = ui.scheme == scheme.id
    val enabled = !scheme.dynamic || ui.dynamicSupported
    val ring = MiuixTheme.colorScheme.onSurface
    val fill: Brush = when {
        scheme.dynamic -> Brush.sweepGradient(listOf(Color(0xFFFF5F57), Color(0xFFFFBD2E), Color(0xFF28C840), Color(0xFF30B0C7), Color(0xFF3482FF), Color(0xFFAF52DE), Color(0xFFFF5F57)))
        scheme.key == null -> {
            val blue = c17StockBlue(ui.style)
            Brush.linearGradient(listOf(blue, blue))
        }
        else -> Brush.linearGradient(listOf(scheme.key, scheme.key))
    }
    Column(Modifier.alpha(if (enabled) 1f else .4f).selectable(
        selected = selected, enabled = enabled, role = Role.RadioButton,
        interactionSource = remember { MutableInteractionSource() }, indication = null) { ui.updateScheme(scheme.id) },
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(fill)
            .border(if (selected) 3.dp else 1.dp, if (selected) ring.copy(alpha = .85f) else ring.copy(alpha = .12f), CircleShape))
        Text(scheme.title, Modifier.padding(top = 4.dp), fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}
