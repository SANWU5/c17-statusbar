// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

/** Whether the app is currently drawn dark, after the user's mode choice and the system's automatic dark mode. */
val LocalC17Dark = staticCompositionLocalOf { false }

/** The settings app's look: component family, mode, colour scheme, how dark the dark mode is and the text size. */
@Composable
fun C17Theme(ui: UiSettings, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    // While the dark background is being adjusted the whole app turns dark, so the change is seen as it is made.
    val dark = ui.previewDark || when (ui.themeMode) { 1 -> false; 2 -> true; else -> systemDark }
    val context = LocalContext.current
    C17ThemeFor(C17ThemeRequest.resolve(ui, dark, context), ui.textScale, content)
}

/** The resolved look with the app's text size. [C17Theme] uses it; a page can also be shown in a fixed look. */
@Composable
internal fun C17ThemeFor(request: C17ThemeRequest, textScale: Int, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val scale = density.fontScale * textScale / 100f
    val scaled = remember(density.density, scale) { Density(density.density, scale) }
    CompositionLocalProvider(
        LocalC17Dark provides request.dark,
        LocalC17UiStyle provides request.style,
        LocalDensity provides scaled,
    ) {
        when (request.style) {
            C17UiStyle.MIUIX -> C17MiuixTheme(request, content)
            C17UiStyle.COUI -> C17CouiTheme(request, content)
            C17UiStyle.MATERIAL -> C17MaterialTheme(request, content)
        }
    }
}

/** The original Miuix look, unchanged: stock surfaces, Monet palettes from a key and the published themes. */
@Composable
private fun C17MiuixTheme(request: C17ThemeRequest, content: @Composable () -> Unit) {
    val dark = request.dark
    val mode = when {
        request.monet -> if (dark) ColorSchemeMode.MonetDark else ColorSchemeMode.MonetLight
        else -> if (dark) ColorSchemeMode.Dark else ColorSchemeMode.Light
    }
    val style = miuixPaletteStyle(request.paletteStyle)
    val controller = remember(mode, request.schemeId, style, request.key) {
        ThemeController(colorSchemeMode = mode, keyColor = request.key, paletteStyle = style)
    }
    // Classic blue keeps the stock Miuix surfaces and text in every style; only its accent colours follow the style.
    val accentController = remember(dark, style, request.stockStyled) {
        if (!request.stockStyled) null else ThemeController(
            colorSchemeMode = if (dark) ColorSchemeMode.MonetDark else ColorSchemeMode.MonetLight,
            keyColor = UiSettings.STOCK_BLUE, paletteStyle = style)
    }
    val stock = controller.currentColors()
    val accent = accentController?.currentColors()
    val colors = remember(stock, accent, request) {
        val base = if (accent == null) stock else stock.withAccentOf(accent)
        // A published theme brings its own colours; the soft derivation only fills the primary's disabled and variant states.
        val themed = request.theme?.palette(dark)?.overlay(base) ?: base
        if (dark) tuneDark(themed, request.darkLevel) else themed
    }
    MiuixTheme(colors = colors, content = content)
    ApplyC17SystemBars(dark, colors.surface)
}

internal fun miuixPaletteStyle(value: Int): ThemePaletteStyle = when (value) {
    1 -> ThemePaletteStyle.Vibrant
    2 -> ThemePaletteStyle.Neutral
    else -> ThemePaletteStyle.TonalSpot
}

/** Every primary role, including its disabled states, comes from the styled accent palette. */
private fun Colors.withAccentOf(accent: Colors): Colors = copy(
    primary = accent.primary, onPrimary = accent.onPrimary, primaryVariant = accent.primaryVariant,
    onPrimaryVariant = accent.onPrimaryVariant, primaryContainer = accent.primaryContainer,
    onPrimaryContainer = accent.onPrimaryContainer, disabledPrimary = accent.disabledPrimary,
    disabledOnPrimary = accent.disabledOnPrimary, disabledPrimaryButton = accent.disabledPrimaryButton,
    disabledOnPrimaryButton = accent.disabledOnPrimaryButton, disabledPrimarySlider = accent.disabledPrimarySlider,
)

/** 50 keeps the scheme's own background; above it pure black grows in, below it a lighter grey. Cards stay readable. */
private fun tuneDark(base: Colors, level: Int): Colors {
    if (level == 50) return base
    val deeper = level > 50
    val t = if (deeper) (level - 50) / 50f else (50 - level) / 50f
    fun background(color: Color) = if (deeper) lerp(color, Color.Black, t) else lerp(color, Color(0xFF3A3A3C), t * .55f)
    fun container(color: Color) = if (deeper) lerp(color, Color(0xFF1C1C1E), t * .8f) else lerp(color, Color(0xFF505053), t * .55f)
    return base.copy(
        background = background(base.background), surface = background(base.surface),
        surfaceVariant = container(base.surfaceVariant), surfaceContainer = container(base.surfaceContainer),
        surfaceContainerHigh = container(base.surfaceContainerHigh), surfaceContainerHighest = container(base.surfaceContainerHighest),
    )
}
