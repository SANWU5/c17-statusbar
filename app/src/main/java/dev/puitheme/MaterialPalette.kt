// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.SchemeNeutral
import com.materialkolor.scheme.SchemeTonalSpot
import com.materialkolor.scheme.SchemeVibrant

/** Google's Material blue: the classic scheme's key in the Material family (Miuix and COUI keep their own blues). */
internal val MATERIAL_STOCK_BLUE = Color(0xFF0B57D0)

/**
 * Material 3 colour roles for one request, without composition: a key derives a full Material Dynamic Color scheme
 * through the material-color-utilities the app already ships; published themes keep their own colours.
 * The wallpaper palette is supplied by the caller, because only Android can read it.
 */
internal fun c17MaterialColors(request: C17ThemeRequest, system: ColorScheme?): ColorScheme {
    val dark = request.dark
    val base = when {
        request.theme?.id == UiThemes.MATERIAL.id -> if (dark) darkColorScheme() else lightColorScheme()
        system != null -> system
        else -> materialSchemeFrom(request.key ?: MATERIAL_STOCK_BLUE, request.paletteStyle, dark)
    }
    val themed = request.theme?.takeIf { it.id != UiThemes.MATERIAL.id }?.palette(dark)?.let { base.overlay(it) } ?: base
    return if (dark) themed.tuneDark(request.darkLevel) else themed
}

private fun materialSchemeFrom(key: Color, style: Int, dark: Boolean): ColorScheme {
    val source = Hct.fromInt(key.argb())
    val scheme: DynamicScheme = when (style) {
        1 -> SchemeVibrant(source, dark, 0.0)
        2 -> SchemeNeutral(source, dark, 0.0)
        else -> SchemeTonalSpot(source, dark, 0.0)
    }
    fun c(argb: Int) = Color(argb)
    return (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = c(scheme.primary), onPrimary = c(scheme.onPrimary),
        primaryContainer = c(scheme.primaryContainer), onPrimaryContainer = c(scheme.onPrimaryContainer),
        inversePrimary = c(scheme.inversePrimary),
        secondary = c(scheme.secondary), onSecondary = c(scheme.onSecondary),
        secondaryContainer = c(scheme.secondaryContainer), onSecondaryContainer = c(scheme.onSecondaryContainer),
        tertiary = c(scheme.tertiary), onTertiary = c(scheme.onTertiary),
        tertiaryContainer = c(scheme.tertiaryContainer), onTertiaryContainer = c(scheme.onTertiaryContainer),
        background = c(scheme.background), onBackground = c(scheme.onBackground),
        surface = c(scheme.surface), onSurface = c(scheme.onSurface),
        surfaceVariant = c(scheme.surfaceVariant), onSurfaceVariant = c(scheme.onSurfaceVariant),
        surfaceTint = c(scheme.surfaceTint),
        inverseSurface = c(scheme.inverseSurface), inverseOnSurface = c(scheme.inverseOnSurface),
        error = c(scheme.error), onError = c(scheme.onError),
        errorContainer = c(scheme.errorContainer), onErrorContainer = c(scheme.onErrorContainer),
        outline = c(scheme.outline), outlineVariant = c(scheme.outlineVariant), scrim = c(scheme.scrim),
        surfaceBright = c(scheme.surfaceBright), surfaceDim = c(scheme.surfaceDim),
        surfaceContainer = c(scheme.surfaceContainer), surfaceContainerHigh = c(scheme.surfaceContainerHigh),
        surfaceContainerHighest = c(scheme.surfaceContainerHighest), surfaceContainerLow = c(scheme.surfaceContainerLow),
        surfaceContainerLowest = c(scheme.surfaceContainerLowest),
    )
}

private fun Color.argb(): Int = android.graphics.Color.argb(
    (alpha * 255f + .5f).toInt(), (red * 255f + .5f).toInt(), (green * 255f + .5f).toInt(), (blue * 255f + .5f).toInt())

/** A published theme (Catppuccin) over a Material scheme: its page, card, text and outline roles are its own. */
private fun ColorScheme.overlay(palette: UiPalette): ColorScheme = copy(
    primary = palette.primary, onPrimary = palette.onPrimary,
    primaryContainer = palette.primaryContainer ?: primaryContainer, onPrimaryContainer = palette.onPrimaryContainer ?: onPrimaryContainer,
    secondaryContainer = palette.secondaryContainer ?: secondaryContainer, onSecondaryContainer = palette.onSecondaryContainer ?: onSecondaryContainer,
    tertiaryContainer = palette.tertiaryContainer ?: tertiaryContainer, onTertiaryContainer = palette.onTertiaryContainer ?: onTertiaryContainer,
    error = palette.error, onError = palette.onError,
    errorContainer = palette.errorContainer ?: errorContainer, onErrorContainer = palette.onErrorContainer ?: onErrorContainer,
    background = palette.background, onBackground = palette.onBackground,
    surface = palette.background, onSurface = palette.onBackground, onSurfaceVariant = palette.onSurfaceVariant,
    surfaceContainerLowest = palette.background, surfaceContainerLow = palette.container,
    surfaceContainer = palette.container, surfaceContainerHigh = palette.containerHigh,
    surfaceContainerHighest = palette.containerHighest, surfaceVariant = palette.containerHigh,
    outline = palette.outline, outlineVariant = palette.divider,
)

/** The shared dark-background control: 50 is the scheme's own, above it pure black grows in, below it lighter grey. */
private fun ColorScheme.tuneDark(level: Int): ColorScheme {
    val bounded = level.coerceIn(0, 100)
    if (bounded == 50) return this
    val deeper = bounded > 50
    val t = if (deeper) (bounded - 50) / 50f else (50 - bounded) / 50f
    fun page(color: Color) = if (deeper) lerp(color, Color.Black, t) else lerp(color, Color(0xFF3A3A3C), t * .55f)
    fun card(color: Color) = if (deeper) lerp(color, Color(0xFF1C1C1E), t * .8f) else lerp(color, Color(0xFF505053), t * .55f)
    return copy(
        background = page(background), surface = page(surface), surfaceDim = page(surfaceDim),
        surfaceContainerLowest = page(surfaceContainerLowest), surfaceContainerLow = card(surfaceContainerLow),
        surfaceContainer = card(surfaceContainer), surfaceContainerHigh = card(surfaceContainerHigh),
        surfaceContainerHighest = card(surfaceContainerHighest), surfaceVariant = card(surfaceVariant),
    )
}
