// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme

import android.content.Context
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext

/**
 * One resolved description of the user's appearance choices, shared by every component family. Each family turns it
 * into its own palette; equal requests are equal values, so a family can key its derived colours on the request alone.
 */
internal data class C17ThemeRequest(
    val style: C17UiStyle,
    val dark: Boolean,
    val schemeId: String,
    /** 0 soft, 1 vivid, 2 muted; a published theme always derives its primary states softly. */
    val paletteStyle: Int,
    val darkLevel: Int,
    /** The colour the family's palette is derived from; null keeps the family's stock palette or the system palette. */
    val key: Color?,
    /** True when the family derives a Monet palette (from [key], or from the wallpaper when [key] is null). */
    val monet: Boolean,
    /** Stock blue with a vivid or muted style: stock surfaces, accent derived from the family's own blue. */
    val stockStyled: Boolean,
    val theme: UiTheme?,
) {
    /** The wallpaper palette as the system publishes it, without a derived key. */
    val systemPalette: Boolean get() = monet && key == null

    companion object {
        fun resolve(ui: UiSettings, dark: Boolean, context: Context): C17ThemeRequest {
            val preset = ui.scheme(ui.scheme)
            val theme = preset.theme
            // "System" is the wallpaper palette as it is; vivid and muted derive from the wallpaper's accent colour,
            // because the system palette itself has no styles.
            val systemKey = if (preset.dynamic && ui.dynamicSupported && ui.paletteStyle != 0)
                runCatching { Color(context.getColor(android.R.color.system_accent1_500)) }.getOrNull() else null
            val key = when {
                theme != null -> theme.palette(dark).primary
                preset.dynamic -> systemKey
                else -> preset.key
            }
            return C17ThemeRequest(
                style = ui.style, dark = dark, schemeId = preset.id,
                paletteStyle = if (theme != null) 0 else ui.paletteStyle,
                darkLevel = ui.darkLevel.coerceIn(0, 100), key = key,
                monet = preset.key != null || preset.dynamic && ui.dynamicSupported,
                stockStyled = preset.key == null && !preset.dynamic && ui.paletteStyle != 0,
                theme = theme,
            )
        }
    }
}

/**
 * Edge-to-edge system bars follow the app's own dark state and page surface, for every component family.
 * The page, navigation and dialog ComposeViews all theme themselves; the window is updated once per real change.
 */
@Composable
internal fun ApplyC17SystemBars(dark: Boolean, surface: Color) {
    val activity = LocalContext.current as? ComponentActivity ?: return
    val argb = surface.toArgb()
    SideEffect { C17SystemBars.apply(activity, dark, argb) }
}

private object C17SystemBars {
    private var activity: java.lang.ref.WeakReference<ComponentActivity>? = null
    private var dark = false
    private var argb = 0

    fun apply(target: ComponentActivity, dark: Boolean, argb: Int) {
        if (activity?.get() === target && this.dark == dark && this.argb == argb) return
        activity = java.lang.ref.WeakReference(target); this.dark = dark; this.argb = argb
        val clear = android.graphics.Color.TRANSPARENT
        val style = if (dark) SystemBarStyle.dark(clear) else SystemBarStyle.light(clear, clear)
        runCatching { target.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style) }
        runCatching { target.window.setBackgroundDrawable(ColorDrawable(argb)) }
    }
}
