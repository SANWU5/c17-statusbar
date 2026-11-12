// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import dev.puitheme.liquid.IosLiquidGlassNavigationBar
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Settings-app only preference for the floating liquid glass navigation (same component the
 * Miuix example and the current LSPosed manager use). Stored outside statusbar_settings so it
 * never reaches SystemUI, config export or the activation guard.
 */
object GlassNavigationPreference {
    private const val FILE = "app_ui"
    private const val KEY = "liquid_glass_navigation"

    /** Lens/vibrancy need RuntimeShader; miuix-blur itself requires Android 13. */
    fun supported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun read(context: Context): Boolean = supported() && runCatching {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY, true)
    }.getOrDefault(false)

    fun write(context: Context, enabled: Boolean): Boolean = runCatching {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY, enabled).commit()
    }.getOrDefault(false)
}

/**
 * Page host. Ordinary mode keeps the existing native navigation ComposeView. Glass mode draws the
 * pages into a LayerBackdrop and renders the floating bar in the same composition, because a
 * sibling ComposeView cannot sample the page content behind it.
 */
@Composable
fun C17MainContent(app: ModernMainActivity) {
    if (!app.floatingGlassNavigation) {
        C17Pages(app)
        return
    }
    val surface = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(surface)
        drawContent()
    }
    val density = LocalDensity.current
    val powerSave = rememberC17PowerSave()
    val items = remember {
        listOf(
            NavigationItem(tr("主页"), MiuixIcons.Home),
            NavigationItem(tr("配置"), MiuixIcons.Settings),
            NavigationItem(tr("关于"), MiuixIcons.Info),
        )
    }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().then(if (powerSave || !app.navigationShown) Modifier else Modifier.layerBackdrop(backdrop))) {
            C17Pages(app)
        }
        if (app.navigationShown) {
            IosLiquidGlassNavigationBar(
                items = items,
                selectedIndex = app.page,
                onItemClick = { app.selectPage(it) },
                backdrop = backdrop,
                isBlurActive = !powerSave,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { app.reportNavigationHeight(it.height / density.density) },
            )
        }
    }
}

/** Observe saver changes without page changes, polling, a sensor or an app-wide receiver. */
@Composable
internal fun rememberC17PowerSave(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
    var enabled by remember(manager) { mutableStateOf(manager?.isPowerSaveMode == true) }
    DisposableEffect(context, manager) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { enabled = manager?.isPowerSaveMode == true }
        }
        val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else context.registerReceiver(receiver, filter)
        onDispose { context.unregisterReceiver(receiver) }
    }
    return enabled
}
