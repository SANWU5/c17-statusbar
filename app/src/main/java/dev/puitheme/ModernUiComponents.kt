// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme

import androidx.compose.foundation.background
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.ColorPicker
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import kotlinx.coroutines.delay
import kotlin.math.ceil
import io.github.suqi8.coui.kmp.basic.ColorPicker as CouiColorPicker
import io.github.suqi8.coui.kmp.basic.TabRow as CouiTabRow
import io.github.suqi8.coui.kmp.basic.TabRowWithContour as CouiTabRowWithContour

/** Reserve the real label width; narrow screens scroll instead of shrinking or truncating text. */
@Composable
fun UiContentTabs(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contour: Boolean = false,
    enabled: Boolean = true,
    /** Total row height, e.g. to match a neighbouring search field; null keeps the measured default. */
    height: Dp? = null,
) {
    if (tabs.isEmpty()) return
    val tabs = tabs.map { tr(it) }
    val coui = c17Coui
    val material = c17Material
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = MiuixTheme.textStyles.body2.copy(fontSize = 14.sp)
    // Reserve both weights so changing selection does not move the remaining tabs.
    val labels = remember(tabs, style, density.fontScale, density.density, measurer) {
        tabs.map { label ->
            val normal = measurer.measure(label, style = style.copy(fontWeight = FontWeight.Normal), softWrap = false)
            val selected = measurer.measure(label, style = style.copy(fontWeight = FontWeight.Medium), softWrap = false)
            maxOf(normal.size.width, selected.size.width) to maxOf(normal.size.height, selected.size.height)
        }
    }
    if (material) {
        C17MaterialTabs(tabs, selectedTabIndex.coerceIn(0, tabs.lastIndex), onTabSelected, connected = contour,
            enabled = enabled, height = height, labelWidths = labels.map { with(density) { it.first.toDp() } },
            labelHeight = with(density) { labels.maxOf { it.second }.toDp() }, modifier = modifier)
        return
    }
    if (coui && enabled) {
        val selected = selectedTabIndex.coerceIn(0, tabs.lastIndex)
        val height = height?.coerceAtLeast(with(density) { labels.maxOf { it.second }.toDp() } + 8.dp)
            ?: (with(density) { labels.maxOf { it.second }.toDp() } + 24.dp).coerceAtLeast(48.dp)
        // Keep full labels at larger text scales; native COUI scrolls when the minimum widths exceed the viewport.
        val minWidth = (with(density) { labels.maxOf { it.first }.toDp() } + 26.dp).coerceAtLeast(64.dp)
        val pick: (Int) -> Unit = { if (enabled && it in tabs.indices) onTabSelected(it) }
        val appearance = modifier.fillMaxWidth().selectableGroup()
        if (contour) CouiTabRowWithContour(tabs, selected, pick, modifier = appearance,
            minWidth = minWidth, maxWidth = Dp.Infinity, height = height)
        else CouiTabRow(tabs, selected, pick, modifier = appearance,
            minWidth = minWidth, maxWidth = Dp.Infinity, height = height)
        return
    }
    // COUI 1.1.0's native tabs have no enabled parameter. A measured static row keeps each
    // disabled tab readable with no click action to input/accessibility, while still allowing scrolling.
    val scroll = rememberScrollState()
    val inset = if (contour) { if (coui) 4.dp else 3.dp } else 0.dp
    val gap = if (coui) 0.dp else 6.dp
    val shape = RoundedCornerShape(if (coui) 24.dp else 12.dp)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val available = (maxWidth - inset * 2).coerceAtLeast(0.dp)
        val natural = labels.map { with(density) { it.first.toDp() } + 26.dp }
            .map { it.coerceAtLeast(64.dp) }
        val total = natural.fold(0.dp) { sum, width -> sum + width } + gap * (tabs.size - 1)
        val extra = ((available - total).coerceAtLeast(0.dp)) / tabs.size
        val widths = natural.map { it + extra }
        val labelHeight = with(density) { labels.maxOf { it.second }.toDp() }
        // The given height is the whole row, contour inset included, so it can line up exactly.
        val controlHeight = height?.coerceAtLeast(labelHeight + 8.dp + inset * 2)
            ?: (labelHeight + 24.dp).coerceAtLeast(48.dp)
        val minimumHeight = if (coui || height != null) controlHeight - inset * 2 else controlHeight
        val selected = selectedTabIndex.coerceIn(0, tabs.lastIndex)
        LaunchedEffect(selected, widths, available, density.density) {
            val start = with(density) { (widths.take(selected).fold(0.dp) { sum, width -> sum + width } + gap * selected).roundToPx() }
            val end = start + with(density) { widths[selected].roundToPx() }
            val viewport = with(density) { available.roundToPx() }
            val target = when {
                start < scroll.value -> start
                end > scroll.value + viewport -> if (end - start > viewport) start else end - viewport
                else -> scroll.value
            }
            scroll.animateScrollTo(target.coerceAtLeast(0))
        }
        Row(Modifier.fillMaxWidth().clip(shape)
            .background(if (contour) MiuixTheme.colorScheme.onSurface.copy(alpha =
                if (coui) { if (LocalC17Dark.current) .12f else .06f } else .08f) else Color.Transparent)
            // A fitting row must not scroll: its drag slop swallowed slightly moving taps.
            .padding(inset).horizontalScroll(scroll, enabled = total > available).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(gap)) {
            tabs.forEachIndexed { index, label ->
                val on = index == selected
                val fill = if (on) {
                    if (coui) {
                        if (LocalC17Dark.current) MiuixTheme.colorScheme.onSurface.copy(alpha = .1f) else Color.White
                    } else if (contour) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surface
                } else Color.Transparent
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                Box(Modifier.width(widths[index]).heightIn(min = minimumHeight).clip(shape)
                    .background(fill)
                    .background(if (pressed && !on) MiuixTheme.colorScheme.onSurface.copy(alpha = .08f) else Color.Transparent)
                    .then(if (coui || on || contour) Modifier else Modifier.border(.75.dp, MiuixTheme.colorScheme.dividerLine, shape))
                    .selectable(selected = on, enabled = enabled, role = Role.Tab, interactionSource = interaction, indication = null, onClick = {
                        if (enabled && index in tabs.indices) onTabSelected(index)
                    })
                    .padding(horizontal = 12.dp, vertical = if (height != null) 4.dp else if (coui && contour) 8.dp else 12.dp), contentAlignment = Alignment.Center) {
                    Text(label, style = style, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                        softWrap = false, color = when {
                            !enabled -> MiuixTheme.colorScheme.disabledOnSecondaryVariant
                            on && contour -> MiuixTheme.colorScheme.onPrimary
                            on -> MiuixTheme.colorScheme.onSurface
                            else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                        })
                }
            }
        }
    }
}

/** A list section header: Material uses its primary title-small header; Miuix and COUI their grey caption. */
@Composable
fun UiSectionHeader(title: String, modifier: Modifier = Modifier) {
    if (c17Material) Text(tr(title), modifier.padding(start = 16.dp, top = 12.dp, bottom = 2.dp),
        style = MiuixTheme.textStyles.headline2, fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.primary)
    else Text(tr(title), modifier.padding(start = 12.dp, top = 4.dp), fontSize = 13.sp,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
}

/** Explanatory text under a group, in every family the quiet summary colour. */
@Composable
fun UiSectionNote(text: String, modifier: Modifier = Modifier) {
    Text(tr(text), modifier.padding(start = if (c17Material) 16.dp else 12.dp, end = 12.dp), fontSize = 13.sp,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
}

/** Shared group container for the selected UI component family. */
@Composable
fun UiGroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Material groups are segmented lists, not cards; Miuix and COUI keep their native grouped cards.
    if (c17Material) C17MaterialSegmentedGroup(modifier, content)
    else C17Card(modifier = modifier.fillMaxWidth(), content = content)
}

@Composable
private fun UiRowIcon(icon: ImageVector, enabled: Boolean) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.padding(end = 16.dp).size(24.dp),
        tint = when {
            !enabled -> MiuixTheme.colorScheme.disabledOnSecondaryVariant
            c17Material -> MiuixTheme.colorScheme.onSurfaceVariantSummary
            else -> MiuixTheme.colorScheme.onSurface
        },
    )
}

@Composable
fun UiRow(
    title: String,
    summary: String = "",
    value: String = "",
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val start: (@Composable () -> Unit)? = if (icon == null) null else {
        { UiRowIcon(icon, enabled) }
    }
    if (onClick == null) {
        C17BasicComponent(
            title = title,
            summary = summary.takeIf { it.isNotEmpty() },
            startAction = start,
            enabled = enabled,
            endActions = {
                if (value.isNotEmpty()) UiRowValue(value, enabled)
            },
        )
    } else {
        C17ArrowPreference(
            title = title,
            summary = summary.takeIf { it.isNotEmpty() },
            startAction = start,
            enabled = enabled,
            onClick = onClick,
            endActions = {
                if (value.isNotEmpty()) UiRowValue(value, enabled)
            },
        )
    }
}

@Composable
private fun UiRowValue(value: String, enabled: Boolean) {
    Text(
        text = tr(value),
        style = MiuixTheme.textStyles.body2,
        // COUI and Material measure the value and wrap it to a second line instead of cutting it off.
        maxLines = if (c17Style == C17UiStyle.MIUIX) 1 else Int.MAX_VALUE,
        overflow = if (c17Style == C17UiStyle.MIUIX) TextOverflow.Ellipsis else TextOverflow.Clip,
        color = if (enabled) MiuixTheme.colorScheme.onSurfaceVariantActions else
            MiuixTheme.colorScheme.disabledOnSecondaryVariant,
    )
}

@Composable
fun UiSwitchRow(
    title: String,
    summary: String = "",
    checked: Boolean,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onChecked: (Boolean) -> Unit,
) {
    val start: (@Composable () -> Unit)? = if (icon == null) null else {
        { UiRowIcon(icon, enabled) }
    }
    C17SwitchPreference(
        title = title,
        summary = summary.takeIf { it.isNotEmpty() },
        checked = checked,
        onCheckedChange = onChecked,
        startAction = start,
        enabled = enabled,
    )
}

@Composable
fun UiColorPair(
    title: String,
    light: Int,
    dark: Int,
    enabled: Boolean = true,
    onLight: () -> Unit,
    onDark: () -> Unit,
) {
    C17BasicComponent(
        title = title,
        enabled = enabled,
        bottomAction = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                UiColorChip("浅色背景", light, enabled, Modifier.weight(1f), onLight)
                UiColorChip("深色背景", dark, enabled, Modifier.weight(1f), onDark)
            }
        },
    )
}

@Composable
private fun UiColorChip(
    label: String,
    color: Int,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val outline = MiuixTheme.colorScheme.dividerLine
    val textColor = if (enabled) MiuixTheme.colorScheme.onSurface else
        MiuixTheme.colorScheme.disabledOnSecondaryVariant
    val secondaryColor = if (enabled) MiuixTheme.colorScheme.onSurfaceVariantSummary else
        MiuixTheme.colorScheme.disabledOnSecondaryVariant
    C17Card(
        // Material chips are tonal surfaces; the hairline frame belongs to the Miuix and COUI looks.
        modifier = if (c17Material) modifier else modifier.squircleBorder(width = { 0.75.dp }, color = { outline }, cornerRadius = 12.dp),
        cornerRadius = 12.dp,
        insideMargin = PaddingValues(10.dp),
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = if (enabled) onClick else null,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(Color(color))
                    .border(0.75.dp, outline, CircleShape),
            )
            Column(Modifier.weight(1f)) {
                Text(text = tr(label), fontSize = 12.sp, color = textColor)
                Text(text = "#%06X".format(color and 0xFFFFFF), fontSize = 11.sp,
                    color = secondaryColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun UiSliderRow(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueText: String,
    summary: String = "",
    steps: Int = 0,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    onValueChange: (Float) -> Unit,
) {
    C17SliderPreference(
        title = title,
        summary = summary.takeIf { it.isNotEmpty() },
        value = value,
        valueRange = valueRange,
        valueText = valueText,
        steps = steps,
        enabled = enabled,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
    )
}

/** Hosted within C17Scaffold so the selected component family's overlay owns dismissal. */
@Composable
fun UiInputDialog(
    title: String,
    initial: String,
    description: String = "",
    numeric: Boolean = false,
    label: String = "",
    context: String = "",
    valueSummary: String = "",
    integer: Boolean = false,
    allowNegative: Boolean = true,
    pattern: Boolean = false,
    confirmLabelForValue: (String) -> String = { "确定" },
    preview: ((String) -> CharSequence)? = null,
    presets: List<Pair<String, String>> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (String) -> String?,
) {
    var value by remember(title, initial) { mutableStateOf(initial) }
    var error by remember(title, initial) { mutableStateOf<String?>(null) }
    var scientific by remember(title, initial) { mutableStateOf(numeric && initial.contains('e', ignoreCase = true)) }
    val keyboard = LocalSoftwareKeyboardController.current
    val confirm = {
        error = onConfirm(value)
        if (error == null) { keyboard?.hide(); onDismiss() }
    }
    C17OverlayDialog(
        show = true,
        title = title,
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (context.isNotEmpty()) Text(tr(context), fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                C17TextField(
                    value = value,
                    onValueChange = { value = it; error = null },
                    label = label,
                    isError = error != null,
                    errorMessage = error,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = when {
                            scientific -> KeyboardType.Ascii
                            integer -> KeyboardType.Number
                            numeric -> KeyboardType.Decimal
                            else -> KeyboardType.Text
                        },
                        autoCorrectEnabled = !(numeric || pattern),
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { confirm() }),
                )
                val shown = if (preview == null) "" else preview(value)
                if (shown.isNotEmpty()) UiPreviewLine(shown)
                if (presets.isNotEmpty()) UiPresetChips(presets, value, pattern) { value = it; error = null }
                if (numeric) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (allowNegative) C17TextButton(text = "正 / 负", modifier = Modifier.weight(1f), onClick = {
                        value = when { value.startsWith("-") -> value.removePrefix("-"); value.startsWith("+") -> "-" + value.removePrefix("+"); else -> "-$value" }
                        error = null
                    })
                    C17TextButton(text = if (scientific) "小数键盘" else "科学计数", modifier = Modifier.weight(1f), onClick = { scientific = !scientific })
                }
                if (valueSummary.isNotEmpty()) Text(tr(valueSummary), fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                if (description.isNotEmpty()) Text(tr(description), fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                if (numeric && scientific) Text(tr("科学计数示例：1.2e3 = 1200；可直接粘贴完整数值。"), fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            UiDialogError(error)
            UiDialogActions(onDismiss = { keyboard?.hide(); onDismiss() }, confirmLabel = confirmLabelForValue(value), onConfirm = confirm)
        }
    }
}

/** One line of preview. Text that carries sketched icons (spans) is drawn by a TextView, which can place them inline. */
@Composable
private fun UiPreviewLine(shown: CharSequence) {
    val error = shown.startsWith("格式有误")
    val color = if (error) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary
    val sketched = shown is android.text.Spanned && shown.getSpans(0, shown.length, android.text.style.ReplacementSpan::class.java).isNotEmpty()
    if (!sketched) {
        Text(tr("预览：") + shown, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = color)
        return
    }
    val argb = color.toArgb()
    androidx.compose.ui.viewinterop.AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { context ->
            android.widget.TextView(context).apply {
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = android.graphics.Typeface.create(typeface, android.graphics.Typeface.BOLD)
                setIncludeFontPadding(true)
            }
        },
        update = { view ->
            view.setTextColor(argb)
            view.text = android.text.SpannableStringBuilder(tr("预览：")).append(shown)
        },
    )
}

/** "预设": ready-made values for a field, one tap fills the input (the preview and the check still apply). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun UiPresetChips(presets: List<Pair<String, String>>, current: String, pattern: Boolean, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr("预设"), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        val groups = if (pattern) listOf("常用", "中文", "英文", "组合") else listOf("")
        var selectedGroup by remember(presets) {
            val name = presets.firstOrNull { it.first == current }?.second.orEmpty()
            mutableStateOf(groups.firstOrNull { it != "常用" && name.startsWith("$it：") } ?: groups.first())
        }
        if (pattern) {
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                groups.forEach { group ->
                    val selected = group == selectedGroup
                    Box(Modifier.clip(RoundedCornerShape(10.dp))
                        .background(if (selected) MiuixTheme.colorScheme.primary.copy(alpha = .16f) else Color.Transparent)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { selectedGroup = group }
                        .padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Text(tr(group), fontSize = 13.sp,
                            color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                }
            }
        }
        val choices = if (!pattern) presets else presets.filter { (_, name) ->
            when (selectedGroup) {
                "常用" -> !name.startsWith("中文：") && !name.startsWith("英文：") && !name.startsWith("组合：")
                else -> name.startsWith("$selectedGroup：")
            }
        }
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { (format, name) ->
                val on = format == current
                Box(Modifier.clip(RoundedCornerShape(10.dp))
                    .background(if (on) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface.copy(alpha = .08f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onPick(format) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)) {
                    // A translated name is shown whole; the source drops the group prefix the tab already says.
                    val translated = tr(name)
                    Text(if (translated != name) translated else if (pattern && selectedGroup != "常用") name.substringAfter('：') else name,
                        fontSize = 13.sp, color = if (on) Color.White else MiuixTheme.colorScheme.onSurface)
                    }
            }
        }
    }
}

@Composable
fun UiConfirmDialog(
    title: String,
    summary: String,
    confirmLabel: String = "确认",
    delaySeconds: Int = 0,
    expiresAt: Long = 0,
    linkLabel: String = "",
    onLink: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    var remaining by remember(title, summary, delaySeconds) {
        mutableIntStateOf(delaySeconds.coerceAtLeast(0))
    }
    LaunchedEffect(title, summary, delaySeconds) {
        val deadline = android.os.SystemClock.elapsedRealtime() + delaySeconds.coerceAtLeast(0).toLong() * 1000L
        while (remaining > 0) {
            remaining = ceil((deadline - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0L) / 1000.0).toInt()
            if (remaining > 0) delay(200)
        }
    }
    var trialRemaining by remember(expiresAt) { mutableIntStateOf(20) }
    LaunchedEffect(expiresAt) {
        if (expiresAt > 0) {
            while (true) {
                trialRemaining = ceil((expiresAt - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0L) / 1000.0).toInt()
                if (trialRemaining == 0) { onDismiss(); break }
                delay(200)
            }
        }
    }
    C17OverlayDialog(
        show = true,
        title = title,
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                Text(tr(summary) + if (expiresAt > 0) "\n" + trf("%d 秒后自动恢复", trialRemaining) else "",
                    fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                // An outbound link keeps the dialog open: returning from the browser still shows the same choice.
                if (onLink != null && linkLabel.isNotEmpty()) Text(tr(linkLabel), Modifier.padding(top = 8.dp)
                    .clip(RoundedCornerShape(8.dp)).clickable(onClick = onLink).padding(vertical = 4.dp),
                    fontSize = 14.sp, color = MiuixTheme.colorScheme.primary)
            }
            UiDialogActions(
                onDismiss = onDismiss,
                confirmLabel = if (remaining > 0) trf("%1\$s（%2\$d秒）", tr(confirmLabel), remaining) else confirmLabel,
                confirmEnabled = remaining == 0,
                onConfirm = {
                    if (remaining == 0) onConfirm()
                },
            )
        }
    }
}

@Composable
fun UiChoiceDialog(
    title: String,
    values: List<String>,
    labels: List<String>,
    selected: String,
    description: String = "",
    optionDisabledReason: (String) -> String = { "" },
    /** A short description under an enabled option; a disabled option shows its reason instead. */
    optionSummary: (String) -> String = { "" },
    onDismiss: () -> Unit,
    onChoice: (String) -> Unit,
) {
    C17OverlayDialog(show = true, title = title, onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .selectableGroup(),
            ) {
                if (description.isNotEmpty()) Text(tr(description), Modifier.padding(bottom = 10.dp),
                    fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                values.forEachIndexed { index, value ->
                    val disabledReason = optionDisabledReason(value)
                    C17RadioButtonPreference(
                        title = labels.getOrNull(index) ?: value,
                        summary = disabledReason.ifEmpty { optionSummary(value) }.takeIf { it.isNotEmpty() },
                        enabled = disabledReason.isEmpty(),
                        selected = selected == value,
                        onClick = { if (disabledReason.isEmpty()) { onChoice(value); onDismiss() } },
                    )
                }
                if (values.isEmpty()) Text(text = tr("暂无可选项"))
            }
            if (c17Material) C17MaterialDialogActions(onDismiss, null, true, null)
            else C17TextButton(text = "取消", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Uses the upstream HSV/alpha picker with a synchronized ARGB text field. */
@Composable
fun UiColorDialog(
    title: String,
    initial: Int,
    initialCustomAlpha: Boolean,
    description: String = "",
    alphaDescription: String = "关闭后跟随系统透明度",
    onDismiss: () -> Unit,
    onConfirm: (Int, Boolean) -> Unit,
) {
    var color by remember(title, initial, initialCustomAlpha) {
        mutableStateOf(Color(if (initialCustomAlpha) initial else initial or 0xFF000000.toInt()))
    }
    var customAlpha by remember(title, initial, initialCustomAlpha) { mutableStateOf(initialCustomAlpha) }
    var hex by remember(title, initial, initialCustomAlpha) { mutableStateOf(colorText(initial, initialCustomAlpha)) }
    var error by remember(title, initial, initialCustomAlpha) { mutableStateOf<String?>(null) }
    val confirm = {
        val parsed = parseArgb(hex)
        if (parsed == null) {
            error = "请输入 #RRGGBB 或 #AARRGGBB 格式的颜色"
        } else {
            onConfirm(parsed, customAlpha)
            onDismiss()
        }
    }
    C17OverlayDialog(
        show = true,
        title = title,
        summary = description.takeIf { it.isNotEmpty() },
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                C17SwitchPreference(
                    title = "自定义透明度",
                    summary = alphaDescription,
                    checked = customAlpha,
                    onCheckedChange = {
                        customAlpha = it
                        hex = colorText(color.toArgb(), it)
                        error = null
                    },
                )
                val pick: (Color) -> Unit = { selected ->
                    if ((selected.toArgb() ushr 24) != (color.toArgb() ushr 24)) customAlpha = true
                    color = selected
                    hex = colorText(selected.toArgb(), customAlpha)
                    error = null
                }
                if (c17Coui) CouiColorPicker(color = color, onColorChanged = pick, modifier = Modifier.fillMaxWidth())
                else ColorPicker(color = color, onColorChanged = pick, modifier = Modifier.fillMaxWidth())
                C17TextField(
                    value = hex,
                    onValueChange = { text ->
                        hex = text
                        error = null
                        parseArgb(text)?.let {
                            color = Color(it)
                            customAlpha = text.trim().removePrefix("#").length == 8
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = "颜色值",
                    isError = error != null,
                    errorMessage = error,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { confirm() }),
                )
                UiDialogError(error)
            }
            UiDialogActions(onDismiss = onDismiss, onConfirm = confirm)
        }
    }
}

@Composable
private fun UiDialogError(error: String?) {
    if (error != null) {
        Text(
            text = tr(error),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.error,
        )
    }
}

@Composable
private fun UiDialogActions(
    onDismiss: () -> Unit,
    confirmLabel: String = "保存",
    confirmEnabled: Boolean = true,
    onConfirm: () -> Unit,
) {
    if (c17Material) {
        C17MaterialDialogActions(onDismiss, tr(confirmLabel), confirmEnabled, onConfirm)
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        C17TextButton(text = "取消", onClick = onDismiss, modifier = Modifier.weight(1f))
        C17TextButton(
            text = confirmLabel,
            onClick = onConfirm,
            enabled = confirmEnabled,
            modifier = Modifier.weight(1f),
            primary = true,
        )
    }
}

private fun colorText(color: Int, customAlpha: Boolean): String =
    if (customAlpha) "#%08X".format(color) else "#%06X".format(color and 0xFFFFFF)

private fun parseArgb(text: String): Int? {
    val value = text.trim().removePrefix("#")
    if ((value.length != 6 && value.length != 8) || value.any { it.digitToIntOrNull(16) == null }) {
        return null
    }
    return try {
        val argb = value.toLong(16)
        (if (value.length == 6) argb or 0xFF000000L else argb).toInt()
    } catch (_: NumberFormatException) {
        null
    }
}

/** Ordinary navigation: an opaque surface through the gesture inset, with safe tab targets. */
@Composable
fun C17Navigation(page: Int, safeInsets: PaddingValues = PaddingValues(), onPage: (Int) -> Unit) {
    if (c17Material) {
        C17MaterialNavigationBar(page, safeInsets, onPage)
        return
    }
    val labels = arrayOf("主页", "配置", "关于")
    val icons = arrayOf(MiuixIcons.Home, MiuixIcons.Settings, MiuixIcons.Info)
    Column(
        modifier = Modifier.fillMaxWidth().background(MiuixTheme.colorScheme.surfaceContainer),
    ) {
        Box(Modifier.fillMaxWidth().height(0.5.dp)
            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f)))
        Row(Modifier.fillMaxWidth().padding(safeInsets).heightIn(min = 68.dp).selectableGroup(),
            verticalAlignment = Alignment.CenterVertically) {
            labels.forEachIndexed { index, label ->
                val selected = page == index
                val tint by animateColorAsState(
                    if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    animationSpec = tween(180), label = "navigation-content-$index")
                val indicator by animateColorAsState(
                    if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.13f) else Color.Transparent,
                    animationSpec = tween(180), label = "navigation-indicator-$index")
                Column(Modifier.weight(1f)
                    .heightIn(min = 64.dp)
                    .selectable(selected = selected, role = Role.Tab,
                        interactionSource = remember { MutableInteractionSource() }, indication = null,
                        onClick = { onPage(index) })
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(Modifier.size(56.dp, 32.dp).clip(RoundedCornerShape(16.dp))
                        .background(indicator), contentAlignment = Alignment.Center) {
                        Icon(icons[index], contentDescription = null, modifier = Modifier.size(24.dp), tint = tint)
                    }
                    Text(tr(label), color = tint, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
