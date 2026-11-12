// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card as MaterialCard
import androidx.compose.material3.CardDefaults as MaterialCardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton as MaterialIconButton
import androidx.compose.material3.LargeTopAppBar as MaterialLargeTopAppBar
import androidx.compose.material3.LocalContentColor as MaterialContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField as MaterialTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.RadioButton as MaterialRadioButton
import androidx.compose.material3.Scaffold as MaterialScaffold
import androidx.compose.material3.Slider as MaterialSlider
import androidx.compose.material3.Switch as MaterialSwitch
import androidx.compose.material3.Text as MaterialText
import androidx.compose.material3.TopAppBar as MaterialTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarState as MaterialTopAppBarState
import androidx.compose.material3.rememberTopAppBarState as rememberMaterialTopAppBarState
import androidx.compose.ui.text.style.TextOverflow
import io.github.suqi8.coui.kmp.basic.ButtonDefaults as CouiButtonDefaults
import io.github.suqi8.coui.kmp.basic.Card as CouiCard
import io.github.suqi8.coui.kmp.basic.CardDefaults as CouiCardDefaults
import io.github.suqi8.coui.kmp.basic.IconButton as CouiIconButton
import io.github.suqi8.coui.kmp.basic.Scaffold as CouiScaffold
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.basic.ScrollBehavior as CouiScrollBehavior
import io.github.suqi8.coui.kmp.basic.TopAppBarState as CouiTopAppBarState
import io.github.suqi8.coui.kmp.basic.rememberTopAppBarState as rememberCouiTopAppBarState
import io.github.suqi8.coui.kmp.basic.Slider as CouiSlider
import io.github.suqi8.coui.kmp.basic.Text as CouiText
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar as CouiSmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Switch as CouiSwitch
import io.github.suqi8.coui.kmp.basic.TextButton as CouiTextButton
import io.github.suqi8.coui.kmp.basic.TextButtonColors as CouiTextButtonColors
import io.github.suqi8.coui.kmp.basic.TextField as CouiTextField
import io.github.suqi8.coui.kmp.basic.TextFieldDefaults as CouiTextFieldDefaults
import io.github.suqi8.coui.kmp.basic.TextFieldMode as CouiTextFieldMode
import io.github.suqi8.coui.kmp.basic.TopAppBar as CouiTopAppBar
import io.github.suqi8.coui.kmp.layout.DialogDefaults as CouiDialogDefaults
import io.github.suqi8.coui.kmp.overlay.OverlayDialog as CouiOverlayDialog
import io.github.suqi8.coui.kmp.preference.RadioButtonLocation as CouiRadioButtonLocation
import io.github.suqi8.coui.kmp.preference.RadioButtonPreference as CouiRadioButtonPreference
import io.github.suqi8.coui.kmp.preference.SwitchPreference as CouiSwitchPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.LocalContentColor as CouiContentColor
import io.github.suqi8.coui.kmp.utils.PressFeedbackType as CouiPressFeedbackType
import top.yukonga.miuix.kmp.basic.BasicComponent as MiuixBasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults as MiuixBasicComponentDefaults
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.CardDefaults as MiuixCardDefaults
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.TopAppBarState as MiuixTopAppBarState
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState as rememberMiuixTopAppBarState
import top.yukonga.miuix.kmp.basic.Slider as MiuixSlider
import top.yukonga.miuix.kmp.basic.SmallTopAppBar as MiuixSmallTopAppBar
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.basic.TextButtonColors
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults as MiuixTextFieldDefaults
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.layout.DialogDefaults as MiuixDialogDefaults
import top.yukonga.miuix.kmp.overlay.OverlayDialog as MiuixOverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference as MiuixArrowPreference
import top.yukonga.miuix.kmp.preference.RadioButtonLocation
import top.yukonga.miuix.kmp.preference.RadioButtonPreference as MiuixRadioButtonPreference
import top.yukonga.miuix.kmp.preference.SliderPreference as MiuixSliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference as MiuixSwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.LocalContentColor as MiuixContentColor
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * Keep the outer Miuix scaffold at the same composition position for every family, so page state survives a style
 * change. Only the active family's scaffold owns bars, insets and its dialog host.
 */
@Composable
fun C17Scaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    containerColor: Color = MiuixTheme.colorScheme.surface,
    contentWindowInsets: WindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
    content: @Composable (PaddingValues) -> Unit,
) {
    val style = c17Style
    val miuix = style == C17UiStyle.MIUIX
    MiuixScaffold(
        modifier = modifier,
        topBar = { if (miuix) topBar() },
        bottomBar = { if (miuix) bottomBar() },
        containerColor = if (miuix) containerColor else Color.Transparent,
        contentWindowInsets = if (miuix) contentWindowInsets else WindowInsets(0),
    ) { padding ->
        when (style) {
            C17UiStyle.MIUIX -> content(padding)
            C17UiStyle.COUI -> CouiScaffold(topBar = topBar, bottomBar = bottomBar, containerColor = containerColor,
                contentWindowInsets = contentWindowInsets, content = content)
            C17UiStyle.MATERIAL -> C17MaterialOverlayRoot {
                MaterialScaffold(topBar = topBar, bottomBar = bottomBar, containerColor = containerColor,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    contentWindowInsets = contentWindowInsets, content = content)
            }
        }
    }
}

/** Each component family retains its own header position across style changes and page navigation. */
@Stable
class C17TopAppBarState internal constructor(
    internal val miuix: MiuixTopAppBarState,
    internal val coui: CouiTopAppBarState,
    internal val material: MaterialTopAppBarState,
)

@Composable
fun rememberC17TopAppBarState(
    initialHeightOffsetLimit: Float = -Float.MAX_VALUE,
): C17TopAppBarState {
    val miuix = rememberMiuixTopAppBarState(initialHeightOffsetLimit = initialHeightOffsetLimit)
    val coui = rememberCouiTopAppBarState(initialHeightOffsetLimit = initialHeightOffsetLimit)
    val material = rememberMaterialTopAppBarState(initialHeightOffsetLimit = initialHeightOffsetLimit)
    return remember(miuix, coui, material) { C17TopAppBarState(miuix, coui, material) }
}

/** Only the active family's behaviour exists and handles gestures; inactive headers neither consume nor track scroll. */
@Stable
class C17ScrollBehavior internal constructor(
    internal val miuix: ScrollBehavior?,
    internal val coui: CouiScrollBehavior?,
    internal val material: TopAppBarScrollBehavior?,
    val nestedScrollConnection: NestedScrollConnection,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberC17ScrollBehavior(
    state: C17TopAppBarState = rememberC17TopAppBarState(),
): C17ScrollBehavior = when (c17Style) {
    C17UiStyle.MIUIX -> {
        val miuix = MiuixScrollBehavior(state = state.miuix)
        remember(miuix) { C17ScrollBehavior(miuix, null, null, miuix.nestedScrollConnection) }
    }
    C17UiStyle.COUI -> {
        val coui = COUIScrollBehavior(state = state.coui)
        remember(coui) { C17ScrollBehavior(null, coui, null, coui.nestedScrollConnection) }
    }
    C17UiStyle.MATERIAL -> {
        val material = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(state.material)
        remember(material) { C17ScrollBehavior(null, null, material, material.nestedScrollConnection) }
    }
}

/** Material bars keep the page colour when scrolled, so the scroll edge blur and fade meet the bar without a seam. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun C17TopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.surface,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: C17ScrollBehavior? = null,
) {
    val shown = tr(title)
    when (c17Style) {
        C17UiStyle.MIUIX -> MiuixTopAppBar(title = shown, modifier = modifier, color = color,
            navigationIcon = navigationIcon, actions = actions, scrollBehavior = scrollBehavior?.miuix)
        C17UiStyle.COUI -> CouiTopAppBar(title = shown, modifier = modifier, color = color,
            navigationIcon = navigationIcon, actions = actions, scrollBehavior = scrollBehavior?.coui,
            // No hairline under the bar: the page and the bar share one surface, as in ColorOS 17 apps.
            defaultWindowInsetsPadding = false, showDivider = false)
        C17UiStyle.MATERIAL -> MaterialLargeTopAppBar(title = { MaterialText(shown, maxLines = 2) }, modifier = modifier,
            navigationIcon = navigationIcon, actions = actions, scrollBehavior = scrollBehavior?.material,
            colors = TopAppBarDefaults.topAppBarColors(containerColor = color,
                scrolledContainerColor = color))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun C17SmallTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.surface,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: C17ScrollBehavior? = null,
) {
    val shown = tr(title)
    when (c17Style) {
        C17UiStyle.MIUIX -> MiuixSmallTopAppBar(title = shown, modifier = modifier, color = color,
            navigationIcon = navigationIcon, actions = actions, scrollBehavior = scrollBehavior?.miuix)
        C17UiStyle.COUI -> CouiSmallTopAppBar(title = shown, modifier = modifier, color = color,
            navigationIcon = navigationIcon, actions = actions, scrollBehavior = scrollBehavior?.coui,
            // No hairline under the bar: the page and the bar share one surface, as in ColorOS 17 apps.
            defaultWindowInsetsPadding = false, showDivider = false)
        C17UiStyle.MATERIAL -> MaterialTopAppBar(title = { MaterialText(shown, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            modifier = modifier, navigationIcon = navigationIcon, actions = actions,
            scrollBehavior = scrollBehavior?.material,
            colors = TopAppBarDefaults.topAppBarColors(containerColor = color,
                scrolledContainerColor = color))
    }
}

@Composable
fun C17Card(
    modifier: Modifier = Modifier,
    cornerRadius: Dp? = null,
    insideMargin: PaddingValues = MiuixCardDefaults.InsideMargin,
    colors: CardColors? = null,
    pressFeedbackType: PressFeedbackType = PressFeedbackType.None,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    when (c17Style) {
        C17UiStyle.MIUIX -> MiuixCard(modifier = modifier, cornerRadius = cornerRadius ?: MiuixCardDefaults.CornerRadius,
            insideMargin = insideMargin, colors = colors ?: MiuixCardDefaults.defaultColors(),
            pressFeedbackType = pressFeedbackType, onClick = onClick, content = content)
        C17UiStyle.COUI -> {
            val couiColors = if (colors == null) CouiCardDefaults.defaultColors() else CouiCardDefaults.defaultColors(
                color = colors.color, contentColor = colors.contentColor,
                pressedColor = lerp(colors.color, colors.contentColor, .06f),
            )
            CouiCard(modifier = modifier, cornerRadius = cornerRadius ?: CouiCardDefaults.CornerRadius,
                insideMargin = insideMargin, colors = couiColors,
                pressFeedbackType = when (pressFeedbackType) {
                    PressFeedbackType.Sink -> CouiPressFeedbackType.Sink
                    PressFeedbackType.Tilt -> CouiPressFeedbackType.Tilt
                    PressFeedbackType.None -> CouiPressFeedbackType.Tint
                }, onClick = onClick, content = content)
        }
        C17UiStyle.MATERIAL -> {
            val shape = RoundedCornerShape(cornerRadius ?: C17MaterialCardRadius)
            // Inside a segmented group a nested card steps one tone up so it stays visible on the segment.
            val container = colors?.color ?: if (LocalC17MaterialGroup.current != null)
                MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainer
            val ink = colors?.contentColor ?: MaterialTheme.colorScheme.onSurface
            val cardColors = MaterialCardDefaults.cardColors(containerColor = container, contentColor = ink)
            // Material cards ripple; the inner padding stays the caller's, as in the other families.
            if (onClick != null) MaterialCard(onClick = onClick, modifier = modifier, shape = shape, colors = cardColors) {
                Column(Modifier.padding(insideMargin), content = content)
            } else MaterialCard(modifier = modifier, shape = shape, colors = cardColors) {
                Column(Modifier.padding(insideMargin), content = content)
            }
        }
    }
}

/** Material settings groups use the large-container corner of Material 3 lists. */
internal val C17MaterialCardRadius = 20.dp

@Composable
fun C17Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    when (c17Style) {
        C17UiStyle.MIUIX -> MiuixSwitch(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled)
        C17UiStyle.COUI -> Box(modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .toggleable(value = checked, enabled = enabled && onCheckedChange != null,
                role = Role.Switch, onValueChange = { if (enabled) onCheckedChange?.invoke(it) }),
            contentAlignment = Alignment.Center) {
            CouiSwitch(checked = checked, onCheckedChange = null, enabled = enabled,
                modifier = Modifier.clearAndSetSemantics {})
        }
        C17UiStyle.MATERIAL -> MaterialSwitch(checked = checked,
            onCheckedChange = onCheckedChange?.let { change -> { value: Boolean -> if (enabled) change(value) } },
            modifier = modifier, enabled = enabled)
    }
}

@Composable
fun C17Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    when (c17Style) {
        C17UiStyle.MIUIX -> MiuixSlider(value = value, onValueChange = onValueChange, modifier = modifier, enabled = enabled,
            valueRange = valueRange, steps = steps, onValueChangeFinished = onValueChangeFinished)
        C17UiStyle.COUI -> {
            // Upstream 1.1.0 still installs setProgress when disabled. Guard callbacks and remove that action.
            val semantics = if (enabled) Modifier else Modifier.clearAndSetSemantics {
                disabled()
                progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(valueRange), valueRange, steps)
            }
            CouiSlider(value = value, onValueChange = { if (enabled) onValueChange(it) },
                modifier = modifier.then(semantics).heightIn(min = 48.dp), enabled = enabled,
                valueRange = valueRange, steps = steps,
                onValueChangeFinished = { if (enabled) onValueChangeFinished?.invoke() })
        }
        C17UiStyle.MATERIAL -> MaterialSlider(value = value.coerceIn(valueRange), onValueChange = { if (enabled) onValueChange(it) },
            modifier = modifier.heightIn(min = 48.dp), enabled = enabled, valueRange = valueRange, steps = steps,
            onValueChangeFinished = { if (enabled) onValueChangeFinished?.invoke() })
    }
}

@Composable
fun C17IconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val appearance = modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
    when (c17Style) {
        C17UiStyle.MIUIX -> MiuixIconButton(onClick = onClick, modifier = appearance, enabled = enabled, content = content)
        C17UiStyle.COUI -> {
            val tint = if (enabled) MiuixContentColor.current else COUITheme.colorScheme.disabledOnSurface
            CompositionLocalProvider(CouiContentColor provides tint, MiuixContentColor provides tint) {
                CouiIconButton(onClick = onClick, modifier = appearance.semantics { if (!enabled) disabled() },
                    enabled = enabled, minHeight = 48.dp, minWidth = 48.dp, content = content)
            }
        }
        C17UiStyle.MATERIAL -> {
            // Pages draw Miuix icons, which read the Miuix content colour; keep both locals on the same ink.
            val tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f)
            CompositionLocalProvider(MaterialContentColor provides tint, MiuixContentColor provides tint) {
                MaterialIconButton(onClick = onClick, modifier = appearance, enabled = enabled, content = content)
            }
        }
    }
}

/**
 * [primary] marks the confirming action of a dialog or panel. Explicit [colors] (custom palettes such as the thanks
 * wall) take precedence in every family.
 */
@Composable
fun C17TextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
    colors: TextButtonColors? = null,
) {
    val shown = tr(text)
    when (c17Style) {
        C17UiStyle.MIUIX -> MiuixTextButton(text = shown, onClick = onClick, modifier = modifier, enabled = enabled,
            colors = colors ?: if (primary) MiuixButtonDefaults.textButtonColorsPrimary() else MiuixButtonDefaults.textButtonColors())
        C17UiStyle.COUI -> {
            val couiColors = colors?.let {
                CouiTextButtonColors(color = it.color, disabledColor = it.disabledColor,
                    textColor = it.textColor, disabledTextColor = it.disabledTextColor)
            } ?: if (primary) CouiButtonDefaults.textButtonColorsPrimary() else CouiButtonDefaults.textButtonColors()
            CouiTextButton(text = shown, onClick = onClick, modifier = modifier, enabled = enabled,
                colors = couiColors, minHeight = 48.dp, insideMargin = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                textStyle = COUITheme.textStyles.button.copy(textAlign = TextAlign.Center))
        }
        C17UiStyle.MATERIAL -> C17MaterialButton(shown, onClick, modifier, enabled, primary, colors)
    }
}

@Composable
fun C17OverlayDialog(
    show: Boolean,
    modifier: Modifier = Modifier,
    title: String? = null,
    titleColor: Color? = null,
    summary: String? = null,
    summaryColor: Color? = null,
    backgroundColor: Color? = null,
    onDismissRequest: (() -> Unit)? = null,
    insideMargin: DpSize? = null,
    outsideMargin: DpSize? = null,
    content: @Composable () -> Unit,
) {
    val style = c17Style
    val title = trn(title)
    val summary = trn(summary)
    if (style == C17UiStyle.MATERIAL) {
        C17MaterialDialog(show = show, title = title, summary = summary, titleColor = titleColor,
            summaryColor = summaryColor, backgroundColor = backgroundColor, onDismissRequest = onDismissRequest,
            insideMargin = insideMargin?.width ?: 24.dp, content = content)
    } else if (style == C17UiStyle.COUI) {
        val contentMargin = insideMargin ?: CouiDialogDefaults.insideMargin
        CouiOverlayDialog(show = show, modifier = modifier,
            titleColor = titleColor ?: CouiDialogDefaults.titleColor(),
            summaryColor = summaryColor ?: CouiDialogDefaults.summaryColor(),
            backgroundColor = backgroundColor ?: CouiDialogDefaults.backgroundColor(),
            onDismissRequest = onDismissRequest,
            insideMargin = contentMargin, outsideMargin = outsideMargin ?: CouiDialogDefaults.outsideMargin) {
            C17CouiDialogContent(title = title, summary = summary,
                titleColor = titleColor ?: CouiDialogDefaults.titleColor(),
                summaryColor = summaryColor ?: CouiDialogDefaults.summaryColor(),
                insideMargin = contentMargin, content = content)
        }
    } else {
        MiuixOverlayDialog(show = show, modifier = modifier, title = title, summary = summary,
            titleColor = titleColor ?: MiuixDialogDefaults.titleColor(),
            summaryColor = summaryColor ?: MiuixDialogDefaults.summaryColor(),
            backgroundColor = backgroundColor ?: MiuixDialogDefaults.backgroundColor(),
            onDismissRequest = onDismissRequest,
            insideMargin = insideMargin ?: MiuixDialogDefaults.insideMargin,
            outsideMargin = outsideMargin ?: MiuixDialogDefaults.outsideMargin, content = content)
    }
}

@Composable
fun C17TextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    insideMargin: DpSize? = null,
    label: String = "",
    useLabelAsPlaceholder: Boolean = false,
    enabled: Boolean = true,
    singleLine: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    leadingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    errorMessage: String? = null,
) {
    val label = tr(label)
    val appearance = modifier.semantics {
        if (!enabled) disabled()
        if (isError) error(tr(errorMessage ?: "输入内容有误"))
    }
    if (c17Material) {
        // A filled field: tonal container and an indicator line, Material's default instead of an outlined box.
        MaterialTextField(value = value, onValueChange = onValueChange, modifier = appearance,
            label = if (label.isEmpty() || useLabelAsPlaceholder) null else ({ MaterialText(label) }),
            placeholder = if (label.isNotEmpty() && useLabelAsPlaceholder) ({ MaterialText(label) }) else null,
            enabled = enabled, singleLine = singleLine, keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
            // The dialogs print the message once below the field; the field only shows the error state.
            leadingIcon = leadingIcon, isError = isError,
            colors = TextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest))
    } else if (c17Coui) {
        CouiTextField(value = value, onValueChange = onValueChange, modifier = appearance,
            insideMargin = insideMargin ?: CouiTextFieldDefaults.insideMargin(CouiTextFieldMode.Line),
            label = label, useLabelAsPlaceholder = useLabelAsPlaceholder, enabled = enabled,
            singleLine = singleLine, keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
            leadingIcon = leadingIcon, isError = isError, showClearButton = singleLine)
    } else {
        MiuixTextField(value = value, onValueChange = onValueChange, modifier = appearance,
            insideMargin = insideMargin ?: MiuixTextFieldDefaults.InsideMargin,
            label = label, useLabelAsPlaceholder = useLabelAsPlaceholder, enabled = enabled,
            singleLine = singleLine, keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
            leadingIcon = leadingIcon)
    }
}

@Composable
fun C17BasicComponent(
    modifier: Modifier = Modifier,
    title: String? = null,
    summary: String? = null,
    startAction: @Composable (() -> Unit)? = null,
    endActions: @Composable (RowScope.() -> Unit)? = null,
    bottomAction: (@Composable () -> Unit)? = null,
    insideMargin: PaddingValues? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val title = trn(title)
    val summary = trn(summary)
    if (c17Material) {
        C17MaterialPreferenceRow(modifier = modifier, startAction = startAction, endActions = endActions,
            bottomAction = bottomAction, insideMargin = insideMargin ?: C17RowMargin, onClick = onClick, enabled = enabled) {
            C17MaterialPreferenceLabel(title, summary, enabled)
        }
    } else if (c17Coui) {
        C17CouiPreferenceRow(modifier = modifier,
            startAction = startAction, endActions = endActions, bottomAction = bottomAction,
            insideMargin = insideMargin ?: C17RowMargin,
            onClick = onClick, enabled = enabled) {
            C17CouiPreferenceLabel(title, summary, enabled)
        }
    } else {
        MiuixBasicComponent(modifier = modifier, title = title, summary = summary,
            startAction = startAction, endActions = endActions, bottomAction = bottomAction,
            insideMargin = insideMargin ?: MiuixBasicComponentDefaults.InsideMargin,
            onClick = onClick, enabled = enabled)
    }
}

@Composable
fun C17BasicComponent(
    modifier: Modifier = Modifier,
    startAction: @Composable (() -> Unit)? = null,
    endActions: @Composable (RowScope.() -> Unit)? = null,
    bottomAction: (@Composable () -> Unit)? = null,
    insideMargin: PaddingValues? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (c17Material) {
        C17MaterialPreferenceRow(modifier = modifier, startAction = startAction, endActions = endActions,
            bottomAction = bottomAction, insideMargin = insideMargin ?: C17RowMargin, onClick = onClick,
            enabled = enabled, content = content)
    } else if (c17Coui) {
        C17CouiPreferenceRow(modifier = modifier, startAction = startAction, endActions = endActions,
            bottomAction = bottomAction, insideMargin = insideMargin ?: C17RowMargin,
            onClick = onClick, enabled = enabled, content = content)
    } else {
        MiuixBasicComponent(modifier = modifier, startAction = startAction, endActions = endActions,
            bottomAction = bottomAction, insideMargin = insideMargin ?: MiuixBasicComponentDefaults.InsideMargin,
            onClick = onClick, enabled = enabled, content = content)
    }
}

@Composable
fun C17ArrowPreference(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    startAction: @Composable (() -> Unit)? = null,
    endActions: @Composable RowScope.() -> Unit = {},
    insideMargin: PaddingValues? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val title = tr(title)
    val summary = trn(summary)
    if (c17Material) {
        C17MaterialPreferenceRow(modifier = modifier, startAction = startAction,
            // Material settings rows navigate without a chevron; the whole row is the affordance.
            endActions = endActions, insideMargin = insideMargin ?: C17RowMargin,
            onClick = onClick, enabled = enabled) {
            C17MaterialPreferenceLabel(title, summary, enabled)
        }
    } else if (c17Coui) {
        C17CouiPreferenceRow(modifier = modifier, startAction = startAction,
            endActions = { endActions(); C17CouiArrow(enabled) },
            insideMargin = insideMargin ?: C17RowMargin,
            onClick = onClick, enabled = enabled) {
            C17CouiPreferenceLabel(title, summary, enabled)
        }
    } else {
        MiuixArrowPreference(title = title, modifier = modifier, summary = summary,
            startAction = startAction, endActions = endActions,
            insideMargin = insideMargin ?: MiuixBasicComponentDefaults.InsideMargin,
            onClick = onClick, enabled = enabled)
    }
}

@Composable
fun C17SwitchPreference(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    startAction: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val title = tr(title)
    val summary = trn(summary)
    if (c17Material) {
        // The whole row toggles; the switch itself is decorative so TalkBack reads one switch.
        C17MaterialPreferenceRow(modifier = modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch,
            onValueChange = { if (enabled) onCheckedChange(it) }), startAction = startAction,
            endActions = { MaterialSwitch(checked = checked, onCheckedChange = null, enabled = enabled,
                modifier = Modifier.clearAndSetSemantics {}) }, enabled = enabled) {
            C17MaterialPreferenceLabel(title, summary, enabled)
        }
    } else if (c17Coui) {
        CouiSwitchPreference(checked = checked, onCheckedChange = { if (enabled) onCheckedChange(it) }, title = title,
            modifier = modifier.heightIn(min = 56.dp).semantics { if (!enabled) disabled() },
            summary = summary, startAction = startAction, enabled = enabled,
            insideMargin = C17RowMargin)
    } else {
        MiuixSwitchPreference(checked = checked, onCheckedChange = onCheckedChange, title = title,
            modifier = modifier, summary = summary, startAction = startAction, enabled = enabled)
    }
}

@Composable
fun C17SliderPreference(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    summary: String? = null,
    valueText: String? = null,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val title = trn(title)
    val summary = trn(summary)
    val valueText = trn(valueText)
    if (c17Material) {
        C17MaterialPreferenceRow(modifier = modifier, enabled = enabled,
            endActions = valueText?.let { text -> { MaterialText(text, style = MaterialTheme.typography.labelLarge,
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f)) } },
            bottomAction = { C17Slider(value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(),
                enabled = enabled, valueRange = valueRange, steps = steps, onValueChangeFinished = onValueChangeFinished) }) {
            C17MaterialPreferenceLabel(title, summary, enabled)
        }
    } else if (c17Coui) {
        C17CouiPreferenceRow(modifier = modifier, enabled = enabled, bottomAction = {
            Column(Modifier.fillMaxWidth()) {
                if (valueText != null) CouiText(valueText, Modifier.fillMaxWidth(), textAlign = TextAlign.End,
                    style = COUITheme.textStyles.body2,
                    color = if (enabled) COUITheme.colorScheme.primary else COUITheme.colorScheme.disabledOnSecondaryVariant)
                C17Slider(value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(),
                    enabled = enabled, valueRange = valueRange, steps = steps, onValueChangeFinished = onValueChangeFinished)
            }
        }) { C17CouiPreferenceLabel(title, summary, enabled) }
    } else {
        MiuixSliderPreference(value = value, onValueChange = onValueChange, modifier = modifier,
            title = title, summary = summary, valueText = valueText, enabled = enabled,
            valueRange = valueRange, steps = steps, onValueChangeFinished = onValueChangeFinished)
    }
}

@Composable
fun C17RadioButtonPreference(
    title: String,
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    summary: String? = null,
    startAction: @Composable (() -> Unit)? = null,
    radioButtonLocation: RadioButtonLocation = RadioButtonLocation.Start,
    insideMargin: PaddingValues? = null,
    enabled: Boolean = true,
) {
    val title = tr(title)
    val summary = trn(summary)
    if (c17Material) {
        val radio: @Composable () -> Unit = { MaterialRadioButton(selected = selected, onClick = null, enabled = enabled,
            modifier = Modifier.clearAndSetSemantics {}) }
        C17MaterialPreferenceRow(modifier = modifier.selectable(selected = selected, enabled = enabled && onClick != null,
            role = Role.RadioButton, onClick = { if (enabled) onClick?.invoke() }),
            startAction = if (radioButtonLocation == RadioButtonLocation.Start) radio else startAction,
            endActions = if (radioButtonLocation == RadioButtonLocation.End) ({ radio() }) else null,
            insideMargin = insideMargin ?: C17RowMargin, enabled = enabled) {
            C17MaterialPreferenceLabel(title, summary, enabled)
        }
    } else if (c17Coui) {
        CouiRadioButtonPreference(title = title, selected = selected, onClick = { if (enabled) onClick?.invoke() },
            modifier = modifier.heightIn(min = 56.dp).semantics { if (!enabled) disabled() }, summary = summary, startAction = startAction,
            radioButtonLocation = when (radioButtonLocation) {
                RadioButtonLocation.Start -> CouiRadioButtonLocation.Start
                RadioButtonLocation.End -> CouiRadioButtonLocation.End
            },
            insideMargin = insideMargin ?: C17RowMargin, enabled = enabled)
    } else {
        MiuixRadioButtonPreference(title = title, selected = selected, onClick = onClick,
            modifier = modifier, summary = summary, startAction = startAction,
            radioButtonLocation = radioButtonLocation,
            insideMargin = insideMargin ?: MiuixBasicComponentDefaults.InsideMargin, enabled = enabled)
    }
}
