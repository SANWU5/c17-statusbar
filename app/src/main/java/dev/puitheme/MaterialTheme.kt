// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.theme.TextStyles as MiuixTextStyles
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import top.yukonga.miuix.kmp.theme.Colors as MiuixColors
import top.yukonga.miuix.kmp.theme.LocalContentColor as MiuixContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme as miuixDarkColors
import top.yukonga.miuix.kmp.theme.lightColorScheme as miuixLightColors

/**
 * Google's Material 3 (Compose Material 3, Apache-2.0) with a Miuix role bridge, so every existing page and the shared
 * rows read the same Material colours. The scheme, dark level and text size choices apply unchanged.
 */
@Composable
internal fun C17MaterialTheme(request: C17ThemeRequest, content: @Composable () -> Unit) {
    val context = LocalContext.current
    // The wallpaper palette exactly as Android 12+ publishes it; styled variants derive from its accent instead.
    val system = if (request.systemPalette && Build.VERSION.SDK_INT >= 31 && request.theme == null) {
        remember(context, request.dark) {
            runCatching { if (request.dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context) }.getOrNull()
        }
    } else null
    val colors = remember(request, system) { c17MaterialColors(request, system) }
    val bridge = remember(colors, request.dark) { colors.toMiuixColors(request.dark) }
    val typography = remember { Typography() }
    val bridgeText = remember(typography) { typography.toMiuixTextStyles() }
    CompositionLocalProvider(MiuixContentColor provides colors.onBackground) {
        MiuixTheme(colors = bridge, textStyles = bridgeText) {
            // Innermost: Material owns ripple, content colour and text for its own components.
            MaterialTheme(colorScheme = colors, typography = typography, content = content)
        }
    }
    ApplyC17SystemBars(request.dark, colors.surface)
}

/** Material roles mapped onto the Miuix roles the shared pages use: page, grouped card, summary, divider and states. */
internal fun ColorScheme.toMiuixColors(dark: Boolean): MiuixColors {
    val stock = if (dark) miuixDarkColors() else miuixLightColors()
    val disabled = onSurface.copy(alpha = .38f)
    return stock.copy(
        primary = primary, onPrimary = onPrimary,
        primaryVariant = lerp(primary, onPrimary, .12f), onPrimaryVariant = onPrimary,
        primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
        disabledPrimary = primary.copy(alpha = .38f), disabledOnPrimary = onPrimary.copy(alpha = .6f),
        disabledPrimaryButton = onSurface.copy(alpha = .12f), disabledOnPrimaryButton = disabled,
        disabledPrimarySlider = onSurface.copy(alpha = .38f),
        secondary = secondaryContainer, onSecondary = onSecondaryContainer,
        secondaryVariant = surfaceContainerHighest, onSecondaryVariant = onSurfaceVariant,
        disabledSecondary = onSurface.copy(alpha = .12f), disabledOnSecondary = disabled,
        disabledSecondaryVariant = onSurface.copy(alpha = .12f), disabledOnSecondaryVariant = disabled,
        secondaryContainer = secondaryContainer, onSecondaryContainer = onSecondaryContainer,
        secondaryContainerVariant = surfaceContainerHigh, onSecondaryContainerVariant = onSurfaceVariant,
        tertiaryContainer = tertiaryContainer, onTertiaryContainer = onTertiaryContainer,
        tertiaryContainerVariant = surfaceContainerHigh,
        error = error, onError = onError, errorContainer = errorContainer, onErrorContainer = onErrorContainer,
        background = surface, onBackground = onSurface, onBackgroundVariant = onSurfaceVariant,
        surface = surface, onSurface = onSurface,
        // Grouped settings cards sit one tone above the page, as Material settings lists do.
        surfaceVariant = surfaceContainer, onSurfaceSecondary = onSurfaceVariant,
        onSurfaceVariantSummary = onSurfaceVariant, onSurfaceVariantActions = onSurfaceVariant,
        disabledOnSurface = disabled,
        surfaceContainer = surfaceContainer, onSurfaceContainer = onSurface, onSurfaceContainerVariant = onSurfaceVariant,
        surfaceContainerHigh = surfaceContainerHigh, onSurfaceContainerHigh = onSurface,
        surfaceContainerHighest = surfaceContainerHighest, onSurfaceContainerHighest = onSurface,
        outline = outline, dividerLine = outlineVariant, windowDimming = scrim.copy(alpha = .32f),
        sliderKeyPoint = onPrimary, sliderKeyPointForeground = primary, sliderBackground = surfaceContainerHighest,
    )
}

/** Material palettes for the classic swatch, so the picker shows the colour this family actually uses. */
internal fun c17StockBlue(style: C17UiStyle): Color = when (style) {
    C17UiStyle.MIUIX -> UiSettings.STOCK_BLUE
    C17UiStyle.COUI -> COUI_STOCK_BLUE
    C17UiStyle.MATERIAL -> MATERIAL_STOCK_BLUE
}

/**
 * The Material 3 type scale under the Miuix names the shared pages use, so page text has Material sizes, weights,
 * leading and tracking instead of Miuix metrics. Pages that set an explicit size keep it.
 */
private fun Typography.toMiuixTextStyles(): MiuixTextStyles {
    fun TextStyle.n() = copy(platformStyle = PlatformTextStyle(includeFontPadding = false))
    return MiuixTextStyles(
        main = bodyLarge.n(), paragraph = bodyLarge.n(), body1 = bodyLarge.n(), body2 = bodyMedium.n(),
        button = labelLarge.n(), footnote1 = bodySmall.n(), footnote2 = labelSmall.n(),
        headline1 = titleMedium.n(), headline2 = titleSmall.n(), subtitle = labelLarge.n(),
        title1 = headlineLarge.n(), title2 = headlineSmall.n(), title3 = titleLarge.n(), title4 = titleLarge.n().copy(fontSize = 18.sp),
    )
}
