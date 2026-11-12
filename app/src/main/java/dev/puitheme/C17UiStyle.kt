// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The settings app's component family. Only the app reads it; SystemUI hooks, module settings and exports never do.
 * The id is what `app_ui/ui_style` stores, so existing ids must never change.
 */
enum class C17UiStyle(val id: String, val title: String, val summary: String) {
    MIUIX("miuix", "Miuix", "HyperOS 风格，经典默认"),
    COUI("coui", "COUI", "ColorOS 风格，原生 COUI 组件 · 未完成，部分页面尚未适配"),
    MATERIAL("material", "Material", "Google Material 3 风格");

    companion object {
        /** Missing, retired or unknown ids keep the original Miuix look. */
        fun of(id: String?): C17UiStyle = entries.firstOrNull { it.id == id } ?: MIUIX
        fun valid(id: String?): Boolean = entries.any { it.id == id }
    }
}

/** Provided once by C17Theme for each ComposeView; every adapter reads this instead of the stored preference. */
val LocalC17UiStyle = staticCompositionLocalOf { C17UiStyle.MIUIX }

/** Shorthands for adapters and shared components; reading a static local is cheap and needs no remember. */
internal val c17Style: C17UiStyle
    @Composable @ReadOnlyComposable get() = LocalC17UiStyle.current
internal val c17Coui: Boolean
    @Composable @ReadOnlyComposable get() = LocalC17UiStyle.current == C17UiStyle.COUI
internal val c17Material: Boolean
    @Composable @ReadOnlyComposable get() = LocalC17UiStyle.current == C17UiStyle.MATERIAL
