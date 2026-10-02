// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.ColorPicker
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationBarDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import kotlinx.coroutines.delay
import kotlin.math.ceil

/** Shared Miuix 0.9.4 components for the settings pages and standard navigation. */
@Composable
fun UiGroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = modifier.fillMaxWidth(), content = content)
}

@Composable
private fun UiRowIcon(icon: ImageVector, enabled: Boolean) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.padding(end = 16.dp).size(24.dp),
        tint = if (enabled) MiuixTheme.colorScheme.onSurface else
            MiuixTheme.colorScheme.disabledOnSecondaryVariant,
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
        BasicComponent(
            title = title,
            summary = summary.takeIf { it.isNotEmpty() },
            startAction = start,
            enabled = enabled,
            endActions = {
                if (value.isNotEmpty()) UiRowValue(value, enabled)
            },
        )
    } else {
        ArrowPreference(
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
        text = value,
        style = MiuixTheme.textStyles.body2,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
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
    SwitchPreference(
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
    BasicComponent(
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
    Card(
        modifier = modifier.squircleBorder(width = { 0.75.dp }, color = { outline }, cornerRadius = 12.dp),
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
                Text(text = label, fontSize = 12.sp, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    SliderPreference(
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

/** Must be hosted within the page's Miuix Scaffold so its overlay host owns dismissal. */
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
    OverlayDialog(
        show = true,
        title = title,
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (context.isNotEmpty()) Text(context, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                TextField(
                    value = value,
                    onValueChange = { value = it; error = null },
                    label = label,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = when {
                            pattern || scientific -> KeyboardType.Ascii
                            integer -> KeyboardType.Number
                            numeric -> KeyboardType.Decimal
                            else -> KeyboardType.Text
                        },
                        autoCorrectEnabled = !(numeric || pattern),
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { confirm() }),
                )
                if (numeric) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (allowNegative) TextButton(text = "正 / 负", modifier = Modifier.weight(1f), onClick = {
                        value = when { value.startsWith("-") -> value.removePrefix("-"); value.startsWith("+") -> "-" + value.removePrefix("+"); else -> "-$value" }
                        error = null
                    })
                    TextButton(text = if (scientific) "小数键盘" else "科学计数", modifier = Modifier.weight(1f), onClick = { scientific = !scientific })
                }
                if (valueSummary.isNotEmpty()) Text(valueSummary, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                if (description.isNotEmpty()) Text(description, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                if (numeric && scientific) Text("科学计数示例：1.2e3 = 1200；可直接粘贴完整数值。", fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            UiDialogError(error)
            UiDialogActions(onDismiss = { keyboard?.hide(); onDismiss() }, confirmLabel = if (numeric) "试用 20 秒" else "保存", onConfirm = confirm)
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
    OverlayDialog(
        show = true,
        title = title,
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                Text(summary + if (expiresAt > 0) "\n${trialRemaining} 秒后自动恢复" else "",
                    fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            UiDialogActions(
                onDismiss = onDismiss,
                confirmLabel = if (remaining > 0) "$confirmLabel（${remaining}秒）" else confirmLabel,
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
    onDismiss: () -> Unit,
    onChoice: (String) -> Unit,
) {
    OverlayDialog(show = true, title = title, onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .selectableGroup(),
            ) {
                if (description.isNotEmpty()) Text(description, Modifier.padding(bottom = 10.dp),
                    fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                values.forEachIndexed { index, value ->
                    RadioButtonPreference(
                        title = labels.getOrNull(index) ?: value,
                        selected = selected == value,
                        onClick = { onChoice(value); onDismiss() },
                    )
                }
                if (values.isEmpty()) Text(text = "暂无可选项")
            }
            TextButton(text = "取消", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
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
    OverlayDialog(
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
                SwitchPreference(
                    title = "自定义透明度",
                    summary = "关闭后跟随系统透明度",
                    checked = customAlpha,
                    onCheckedChange = {
                        customAlpha = it
                        hex = colorText(color.toArgb(), it)
                        error = null
                    },
                )
                ColorPicker(
                    color = color,
                    onColorChanged = { selected ->
                        if ((selected.toArgb() ushr 24) != (color.toArgb() ushr 24)) customAlpha = true
                        color = selected
                        hex = colorText(selected.toArgb(), customAlpha)
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
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
            text = error,
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(text = "取消", onClick = onDismiss, modifier = Modifier.weight(1f))
        TextButton(
            text = confirmLabel,
            onClick = onConfirm,
            enabled = confirmEnabled,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.textButtonColorsPrimary(),
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

/** Standard edge-to-edge navigation, adjoining the system navigation inset. */
@Composable
fun C17Navigation(page: Int, onPage: (Int) -> Unit) {
    val labels = arrayOf("主页", "配置", "关于")
    val icons = arrayOf(MiuixIcons.Home, MiuixIcons.Settings, MiuixIcons.Info)
    Column(
        modifier = Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surfaceContainer),
        verticalArrangement = Arrangement.Center,
    ) {
        NavigationBar(
            color = MiuixTheme.colorScheme.surfaceContainer,
            showDivider = true,
            defaultWindowInsetsPadding = false,
        ) {
            labels.forEachIndexed { index, label ->
                NavigationBarItem(
                    selected = page == index,
                    onClick = { onPage(index) },
                    icon = icons[index],
                    label = label,
                    colors = NavigationBarDefaults.navigationBarItemColors(
                        unselectedContentColor = MiuixTheme.colorScheme.onSurface,
                        selectedContentColor = MiuixTheme.colorScheme.primary,
                    ),
                )
            }
        }
    }
}
