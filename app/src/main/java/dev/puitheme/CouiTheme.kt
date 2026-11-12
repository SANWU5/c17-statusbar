// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.ColorSchemeMode as CouiColorSchemeMode
import io.github.suqi8.coui.kmp.theme.Colors as CouiColors
import io.github.suqi8.coui.kmp.theme.LocalContentColor as CouiContentColor
import io.github.suqi8.coui.kmp.theme.TextStyles as CouiTextStyles
import io.github.suqi8.coui.kmp.theme.ThemeController as CouiThemeController
import io.github.suqi8.coui.kmp.theme.ThemePaletteStyle as CouiPaletteStyle
import io.github.suqi8.coui.kmp.theme.defaultTextStyles as couiDefaultTextStyles
import top.yukonga.miuix.kmp.theme.Colors as MiuixColors
import top.yukonga.miuix.kmp.theme.LocalContentColor as MiuixContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.TextStyles as MiuixTextStyles

/**
 * Real COUI 1.1.0 theme with a Miuix bridge for existing content and the liquid navigation bar.
 * C17Theme owns dark-mode resolution, the component-family local and the single application of the user's font scale.
 *
 * Native colours and text metrics come from https://github.com/suqi8/coui (Apache-2.0).
 * Colour schemes, wallpaper colours and published palettes remain independently selectable.
 */
@Composable
internal fun C17CouiTheme(request: C17ThemeRequest, content: @Composable () -> Unit) {
    val dark = request.dark
    val paletteStyle = when (request.paletteStyle) {
        1 -> CouiPaletteStyle.Vibrant
        2 -> CouiPaletteStyle.Neutral
        else -> CouiPaletteStyle.TonalSpot
    }
    val mode = if (request.monet) {
        if (dark) CouiColorSchemeMode.MonetDark else CouiColorSchemeMode.MonetLight
    } else {
        if (dark) CouiColorSchemeMode.Dark else CouiColorSchemeMode.Light
    }
    val controller = remember(mode, request.schemeId, paletteStyle, request.key) {
        CouiThemeController(colorSchemeMode = mode, keyColor = request.key, paletteStyle = paletteStyle)
    }
    // Classic blue preserves native COUI surfaces; vivid/muted only changes its accent family.
    val accentController = remember(dark, paletteStyle, request.stockStyled) {
        if (!request.stockStyled) null else CouiThemeController(
            colorSchemeMode = if (dark) CouiColorSchemeMode.MonetDark else CouiColorSchemeMode.MonetLight,
            keyColor = COUI_STOCK_BLUE, paletteStyle = paletteStyle,
        )
    }
    // The controllers remember their palettes, so identity changes only with a real palette change. Contrast searches
    // and both theme bridges therefore run once per change instead of on every recomposition of the theme root.
    val stock = controller.currentColors()
    val accent = accentController?.currentColors()
    val colors = remember(stock, accent, request) { c17CouiColors(stock, accent, request) }
    val textStyles = remember { couiDefaultTextStyles().withC17TextMetrics() }
    val bridgeColors = remember(colors) { colors.toMiuixColors() }
    val bridgeText = remember(textStyles) { textStyles.toMiuixTextStyles() }
    CompositionLocalProvider(
        // Both libraries' colors overloads leave LocalContentColor unchanged; set the two roots explicitly.
        CouiContentColor provides colors.onBackground,
        MiuixContentColor provides colors.onBackground,
    ) {
        MiuixTheme(colors = bridgeColors, textStyles = bridgeText) {
            // Innermost theme owns press feedback and overscroll, including shared custom Compose rows.
            COUITheme(colors = colors, textStyles = textStyles, content = content)
        }
    }
    ApplyC17SystemBars(dark, colors.surface)
}

/** COUI's own stock blue: the key a vivid or muted classic blue is derived from, and the classic swatch colour. */
internal val COUI_STOCK_BLUE = Color(0xFF0066FF)

/** Pure palette derivation, shared by the theme and the desktop checks: accent, published theme, dark level, ink. */
internal fun c17CouiColors(stock: CouiColors, accent: CouiColors?, request: C17ThemeRequest): CouiColors {
    val base = if (accent == null) stock else stock.copy(
        primary = accent.primary, onPrimary = accent.onPrimary, primaryVariant = accent.primaryVariant,
        onPrimaryVariant = accent.onPrimaryVariant, primaryContainer = accent.primaryContainer,
        onPrimaryContainer = accent.onPrimaryContainer, disabledPrimary = accent.disabledPrimary,
        disabledOnPrimary = accent.disabledOnPrimary, disabledPrimaryButton = accent.disabledPrimaryButton,
        disabledOnPrimaryButton = accent.disabledOnPrimaryButton, disabledPrimarySlider = accent.disabledPrimarySlider,
    )
    val published = request.theme
    // UiPalette has one implementation shared by every UI family; keep published roles exact at default.
    val themed = if (published == null) base else published.palette(request.dark).overlay(base.toMiuixColors()).toCouiColors()
    val adjusted = if (request.dark) themed.tuneC17Dark(request.darkLevel) else themed
    return if (published == null || request.dark && request.darkLevel != 50) adjusted.withC17ReadableText() else adjusted
}

/** Lossless role bridge, also usable before UiPalette.overlay or the shared dark-background adjustment. */
fun CouiColors.toMiuixColors(): MiuixColors = MiuixColors(
    primary = primary,
    onPrimary = onPrimary,
    primaryVariant = primaryVariant,
    onPrimaryVariant = onPrimaryVariant,
    error = error,
    onError = onError,
    errorContainer = errorContainer,
    onErrorContainer = onErrorContainer,
    disabledPrimary = disabledPrimary,
    disabledOnPrimary = disabledOnPrimary,
    disabledPrimaryButton = disabledPrimaryButton,
    disabledOnPrimaryButton = disabledOnPrimaryButton,
    disabledPrimarySlider = disabledPrimarySlider,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = secondary,
    onSecondary = onSecondary,
    secondaryVariant = secondaryVariant,
    onSecondaryVariant = onSecondaryVariant,
    disabledSecondary = disabledSecondary,
    disabledOnSecondary = disabledOnSecondary,
    disabledSecondaryVariant = disabledSecondaryVariant,
    disabledOnSecondaryVariant = disabledOnSecondaryVariant,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    secondaryContainerVariant = secondaryContainerVariant,
    onSecondaryContainerVariant = onSecondaryContainerVariant,
    tertiaryContainer = tertiaryContainer,
    onTertiaryContainer = onTertiaryContainer,
    tertiaryContainerVariant = tertiaryContainerVariant,
    background = background,
    onBackground = onBackground,
    onBackgroundVariant = onBackgroundVariant,
    surface = surface,
    onSurface = onSurface,
    surfaceVariant = surfaceVariant,
    onSurfaceSecondary = onSurfaceSecondary,
    onSurfaceVariantSummary = onSurfaceVariantSummary,
    onSurfaceVariantActions = onSurfaceVariantActions,
    disabledOnSurface = disabledOnSurface,
    surfaceContainer = surfaceContainer,
    onSurfaceContainer = onSurfaceContainer,
    onSurfaceContainerVariant = onSurfaceContainerVariant,
    surfaceContainerHigh = surfaceContainerHigh,
    onSurfaceContainerHigh = onSurfaceContainerHigh,
    surfaceContainerHighest = surfaceContainerHighest,
    onSurfaceContainerHighest = onSurfaceContainerHighest,
    outline = outline,
    dividerLine = dividerLine,
    windowDimming = windowDimming,
    sliderKeyPoint = sliderKeyPoint,
    sliderKeyPointForeground = sliderKeyPointForeground,
    sliderBackground = sliderBackground,
)

/** Reverse bridge keeps custom palettes and every disabled/container state identical in both libraries. */
fun MiuixColors.toCouiColors(): CouiColors = CouiColors(
    primary = primary,
    onPrimary = onPrimary,
    primaryVariant = primaryVariant,
    onPrimaryVariant = onPrimaryVariant,
    error = error,
    onError = onError,
    errorContainer = errorContainer,
    onErrorContainer = onErrorContainer,
    disabledPrimary = disabledPrimary,
    disabledOnPrimary = disabledOnPrimary,
    disabledPrimaryButton = disabledPrimaryButton,
    disabledOnPrimaryButton = disabledOnPrimaryButton,
    disabledPrimarySlider = disabledPrimarySlider,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = secondary,
    onSecondary = onSecondary,
    secondaryVariant = secondaryVariant,
    onSecondaryVariant = onSecondaryVariant,
    disabledSecondary = disabledSecondary,
    disabledOnSecondary = disabledOnSecondary,
    disabledSecondaryVariant = disabledSecondaryVariant,
    disabledOnSecondaryVariant = disabledOnSecondaryVariant,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    secondaryContainerVariant = secondaryContainerVariant,
    onSecondaryContainerVariant = onSecondaryContainerVariant,
    tertiaryContainer = tertiaryContainer,
    onTertiaryContainer = onTertiaryContainer,
    tertiaryContainerVariant = tertiaryContainerVariant,
    background = background,
    onBackground = onBackground,
    onBackgroundVariant = onBackgroundVariant,
    surface = surface,
    onSurface = onSurface,
    surfaceVariant = surfaceVariant,
    onSurfaceSecondary = onSurfaceSecondary,
    onSurfaceVariantSummary = onSurfaceVariantSummary,
    onSurfaceVariantActions = onSurfaceVariantActions,
    disabledOnSurface = disabledOnSurface,
    surfaceContainer = surfaceContainer,
    onSurfaceContainer = onSurfaceContainer,
    onSurfaceContainerVariant = onSurfaceContainerVariant,
    surfaceContainerHigh = surfaceContainerHigh,
    onSurfaceContainerHigh = onSurfaceContainerHigh,
    surfaceContainerHighest = surfaceContainerHighest,
    onSurfaceContainerHighest = onSurfaceContainerHighest,
    outline = outline,
    dividerLine = dividerLine,
    windowDimming = windowDimming,
    sliderKeyPoint = sliderKeyPoint,
    sliderKeyPointForeground = sliderKeyPointForeground,
    sliderBackground = sliderBackground,
)

private fun CouiTextStyles.toMiuixTextStyles(): MiuixTextStyles = MiuixTextStyles(
    main = main,
    paragraph = paragraph,
    body1 = body1,
    body2 = body2,
    button = button,
    footnote1 = footnote1,
    footnote2 = footnote2,
    headline1 = headline1,
    headline2 = headline2,
    subtitle = subtitle,
    title1 = title1,
    title2 = title2,
    title3 = title3,
    title4 = title4,
)

/** Both component families share COUI's native font tiers and leading, without extra Android padding. */
private fun CouiTextStyles.withC17TextMetrics(): CouiTextStyles {
    fun TextStyle.normalized() = copy(platformStyle = PlatformTextStyle(includeFontPadding = false))
    return copy(
        main = main.normalized(), paragraph = paragraph.normalized(),
        body1 = body1.normalized(), body2 = body2.normalized(), button = button.normalized(),
        footnote1 = footnote1.normalized(), footnote2 = footnote2.normalized(),
        headline1 = headline1.normalized(), headline2 = headline2.normalized(), subtitle = subtitle.normalized(),
        title1 = title1.normalized(), title2 = title2.normalized(), title3 = title3.normalized(), title4 = title4.normalized(),
    )
}

/** Resolve translucent card layers against the page, so text is checked against what is actually drawn. */
private fun CouiColors.withC17ReadableText(): CouiColors {
    val page = surface.compositeOver(background).copy(alpha = 1f)
    val card = surfaceVariant.compositeOver(page)
    val container = surfaceContainer.compositeOver(page)
    val high = surfaceContainerHigh.compositeOver(page)
    return copy(
        onBackground = onBackground.readableOn(background),
        onBackgroundVariant = onBackgroundVariant.readableOn(background),
        onSurface = onSurface.readableOn(page),
        onSurfaceSecondary = onSurfaceSecondary.readableOn(card),
        onSurfaceVariantSummary = onSurfaceVariantSummary.readableOn(card),
        onSurfaceVariantActions = onSurfaceVariantActions.readableOn(card, 3f),
        onSurfaceContainer = onSurfaceContainer.readableOn(container),
        onSurfaceContainerVariant = onSurfaceContainerVariant.readableOn(container),
        onSurfaceContainerHigh = onSurfaceContainerHigh.readableOn(high),
        onSurfaceContainerHighest = onSurfaceContainerHighest.readableOn(surfaceContainerHighest.compositeOver(page)),
        onSecondaryContainer = onSecondaryContainer.readableOn(secondaryContainer.compositeOver(card)),
        onSecondaryContainerVariant = onSecondaryContainerVariant.readableOn(secondaryContainerVariant.compositeOver(card)),
    )
}

/** Increase only the ink needed for readability; retain an already readable foreground unchanged. */
private fun Color.readableOn(background: Color, minimum: Float = 4.5f): Color {
    return c17ReadableInk(this, listOf(background), minimum)
}

/** Same dark-background control as Miuix: midpoint is the palette, extremes retain readable cards. */
fun CouiColors.tuneC17Dark(level: Int): CouiColors {
    val bounded = level.coerceIn(0, 100)
    if (bounded == 50) return this
    val deeper = bounded > 50
    val amount = if (deeper) (bounded - 50) / 50f else (50 - bounded) / 50f
    fun background(color: Color) = if (deeper) lerp(color, Color.Black, amount)
        else lerp(color, Color(0xFF3A3A3C), amount * .55f)
    // Native dark COUI cards are translucent white. Interpolating their RGB and alpha separately
    // brightens them unexpectedly; tune their resolved layer colour instead.
    val page = surface.compositeOver(background).copy(alpha = 1f)
    fun container(color: Color): Color {
        val resolved = color.compositeOver(page)
        val darkTarget = Color(0xFF1C1C1E)
        val noBrighter = Color(minOf(resolved.red, darkTarget.red), minOf(resolved.green, darkTarget.green),
            minOf(resolved.blue, darkTarget.blue), 1f)
        return if (deeper) lerp(resolved, noBrighter, amount * .8f)
            else lerp(resolved, Color(0xFF505053), amount * .55f)
    }
    return copy(
        background = background(background), surface = background(surface),
        surfaceVariant = container(surfaceVariant), surfaceContainer = container(surfaceContainer),
        surfaceContainerHigh = container(surfaceContainerHigh), surfaceContainerHighest = container(surfaceContainerHighest),
    )
}
