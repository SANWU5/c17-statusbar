// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme

import android.content.Context
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

/** One selectable colour scheme. A null key is the stock Miuix palette; dynamic follows the wallpaper (Android 12+);
 * a theme is a published palette (Material 3, Catppuccin) whose colours are used as they are. */
class UiScheme(val id: String, val title: String, val key: Color?, val dynamic: Boolean = false, val theme: UiTheme? = null)

/** Settings-app look only. Stored outside statusbar_settings so it never reaches SystemUI, exports or the guard. */
class UiSettings(context: Context) {
    private val context: Context = context.applicationContext
    private val prefs = this.context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** The component family belongs to this app only; missing or invalid choices keep the existing UI. */
    var style by mutableStateOf(C17UiStyle.of(runCatching { prefs.getString(UI_STYLE, MIUIX) }.getOrNull())); private set
    val uiStyle: String get() = style.id
    val uiStyleTitle: String get() = style.title

    /** 0 follow the system's automatic dark mode, 1 always light, 2 always dark. */
    var themeMode by mutableIntStateOf(read(THEME_MODE, 0, 0, 2)); private set
    var scheme by mutableStateOf(prefs.getString(SCHEME, "miuix") ?: "miuix"); private set
    /** 0 soft (tonal), 1 vivid, 2 muted. */
    var paletteStyle by mutableIntStateOf(read(PALETTE, 0, 0, 2)); private set
    /** Dark background: 0 lighter dark grey .. 50 the scheme's own .. 100 pure black. */
    var darkLevel by mutableIntStateOf(read(DARK_LEVEL, 50, 0, 100)); private set
    /** Content that scrolls under the top edge fades out; the length follows the scroll, nothing to tune. */
    var edgeFade by mutableStateOf(runCatching { prefs.getBoolean(EDGE_FADE, true) }.getOrDefault(true)); private set
    /** Settings app text size, percent. */
    var textScale by mutableIntStateOf(read(TEXT_SCALE, 100, 80, 130)); private set

    /** True while the dark background is being adjusted: the whole app shows dark so the change can be judged. Never saved. */
    var previewDark by mutableStateOf(false)

    val dynamicSupported: Boolean get() = Build.VERSION.SDK_INT >= 31

    fun scheme(id: String): UiScheme = SCHEMES.firstOrNull { it.id == id } ?: THEME_SCHEMES.firstOrNull { it.id == id } ?: SCHEMES[0]
    fun style(): ThemePaletteStyle = when (paletteStyle) {
        1 -> ThemePaletteStyle.Vibrant
        2 -> ThemePaletteStyle.Neutral
        else -> ThemePaletteStyle.TonalSpot
    }

    fun updateThemeMode(value: Int) { themeMode = value.coerceIn(0, 2); put(THEME_MODE, themeMode) }
    fun updateScheme(id: String) { scheme = scheme(id).id; prefs.edit().putString(SCHEME, scheme).apply(); note("ui_changed") }
    fun updatePaletteStyle(value: Int) { paletteStyle = value.coerceIn(0, 2); put(PALETTE, paletteStyle) }
    fun updateDarkLevel(value: Int) { darkLevel = value.coerceIn(0, 100); put(DARK_LEVEL, darkLevel) }
    fun updateEdgeFade(value: Boolean) { edgeFade = value; prefs.edit().putBoolean(EDGE_FADE, value).apply(); note("ui_changed") }
    fun updateTextScale(value: Int) { textScale = value.coerceIn(80, 130); put(TEXT_SCALE, textScale) }

    fun updateUiStyle(value: String): Boolean {
        if (!C17UiStyle.valid(value)) return false
        val target = C17UiStyle.of(value)
        if (target == style) return true
        if (!prefs.edit().putString(UI_STYLE, target.id).commit()) return false
        style = target
        note("ui_style_changed")
        return true
    }

    /** Restores only the appearance; the bottom bar switch and every module setting are untouched. */
    fun reset() {
        style = C17UiStyle.MIUIX; themeMode = 0; scheme = "miuix"; paletteStyle = 0; darkLevel = 50; edgeFade = true; textScale = 100
        prefs.edit().putInt(THEME_MODE, 0).putString(SCHEME, "miuix").putInt(PALETTE, 0).putInt(DARK_LEVEL, 50)
            .putBoolean(EDGE_FADE, true).putInt(TEXT_SCALE, 100).putString(UI_STYLE, MIUIX).apply()
        note("ui_reset")
    }

    private fun put(key: String, value: Int) { prefs.edit().putInt(key, value).apply(); note("ui_changed") }
    private fun read(key: String, fallback: Int, min: Int, max: Int): Int =
        runCatching { prefs.getInt(key, fallback) }.getOrDefault(fallback).coerceIn(min, max)

    /** One structured record of the current look, so a report shows which theme and scheme the app was using. */
    fun note(reason: String) {
        ModuleDiagnostics.appFact(context, "ui", "界面设置", "theme_mode", themeMode, "scheme", scheme, "black_level", darkLevel,
            "dynamic", dynamicSupported, "blur_top", edgeFade, "glass_nav", GlassNavigationPreference.read(context),
            "ui_style", uiStyle, "reason", reason)
    }

    companion object {
        /** The same file the bottom bar switch already uses. */
        const val FILE = "app_ui"
        const val UI_STYLE = "ui_style"
        const val MIUIX = "miuix"
        const val COUI = "coui"
        const val MATERIAL = "material"
        const val THEME_MODE = "ui_theme_mode"
        const val SCHEME = "ui_scheme"
        const val PALETTE = "ui_palette_style"
        const val DARK_LEVEL = "ui_dark_level"
        const val EDGE_FADE = "ui_edge_fade"
        const val TEXT_SCALE = "ui_text_scale"

        /** Miuix's own blue: the key colour a vivid or muted classic blue is derived from. */
        val STOCK_BLUE = Color(0xFF3482FF)

        /** Curated key colours; Miuix derives the whole palette from each. */
        val SCHEMES: List<UiScheme> = listOf(
            UiScheme("miuix", "经典蓝", null),
            UiScheme("dynamic", "系统取色", null, dynamic = true),
            UiScheme("green", "清新绿", Color(0xFF34C759)),
            UiScheme("orange", "落日橙", Color(0xFFFF9500)),
            UiScheme("pink", "樱花粉", Color(0xFFFF2D55)),
            UiScheme("purple", "薰衣草", Color(0xFFAF52DE)),
            UiScheme("teal", "湖水青", Color(0xFF30B0C7)),
            UiScheme("indigo", "午夜靛", Color(0xFF5E5CE6)),
            UiScheme("red", "珊瑚红", Color(0xFFFF3B30)),
            UiScheme("graphite", "石墨灰", Color(0xFF8E8E93)),
        )

        /** Open-source themes, shown in their own section; the key is only the primary the rest of the palette is derived around. */
        val THEME_SCHEMES: List<UiScheme> = UiThemes.ALL.map { UiScheme(it.id, it.title, it.light.primary, theme = it) }
    }
}
