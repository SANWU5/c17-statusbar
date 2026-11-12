// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * In-window dialog host for the Material family, like the Miuix and COUI hosts: dialogs stay in the app's own layer,
 * keep its text size and z-order, and need no extra platform window. Later dialogs draw above earlier ones.
 */
@Stable
internal class C17MaterialOverlayHost {
    internal class Entry(val owner: Any) {
        val visible = MutableTransitionState(false)
        // State-backed so the host recomposes the dialog whenever its caller does.
        var dismiss by mutableStateOf<(() -> Unit)?>(null)
        var content by mutableStateOf<@Composable () -> Unit>({})
    }
    internal val entries = mutableStateListOf<Entry>()

    internal fun entry(owner: Any): Entry = entries.firstOrNull { it.owner === owner } ?: Entry(owner).also { entries += it }
    internal fun remove(owner: Any) { entries.removeAll { it.owner === owner } }
}

internal val LocalC17MaterialOverlay = staticCompositionLocalOf<C17MaterialOverlayHost?> { null }

/** Hosts every Material dialog shown by [content]; the scrim and dialogs draw over the whole scaffold. */
@Composable
internal fun C17MaterialOverlayRoot(content: @Composable () -> Unit) {
    val host = remember { C17MaterialOverlayHost() }
    CompositionLocalProvider(LocalC17MaterialOverlay provides host) {
        Box(Modifier.fillMaxSize()) {
            content()
            host.entries.forEach { entry -> C17MaterialOverlayEntry(entry) }
        }
    }
}

@Composable
private fun C17MaterialOverlayEntry(entry: C17MaterialOverlayHost.Entry) {
    val scrim = MaterialTheme.colorScheme.scrim.copy(alpha = .32f)
    // Like the Miuix and COUI hosts, system back dismisses the newest dialog before the page handles it.
    BackHandler(enabled = entry.visible.targetState && entry.dismiss != null) { entry.dismiss?.invoke() }
    AnimatedVisibility(entry.visible, enter = fadeIn(tween(150)), exit = fadeOut(tween(120))) {
        Box(Modifier.fillMaxSize().background(scrim)
            // Taps on the scrim dismiss; taps never reach the page underneath.
            .pointerInput(entry) { detectTapGestures { entry.dismiss?.invoke() } })
    }
    AnimatedVisibility(entry.visible, enter = fadeIn(tween(180)) + scaleIn(tween(220), initialScale = .9f),
        exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = .96f)) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))
            .padding(horizontal = 24.dp, vertical = 24.dp), contentAlignment = Alignment.Center) {
            entry.content()
        }
    }
}

/** A Material 3 basic dialog surface: 28 dp corners, container-high tone, headline title and a scrollable summary. */
@Composable
internal fun C17MaterialDialog(
    show: Boolean,
    title: String?,
    summary: String?,
    titleColor: Color?,
    summaryColor: Color?,
    backgroundColor: Color?,
    onDismissRequest: (() -> Unit)?,
    insideMargin: Dp,
    content: @Composable () -> Unit,
) {
    val host = LocalC17MaterialOverlay.current
    val body: @Composable () -> Unit = {
        C17MaterialDialogSurface(title, summary, titleColor, summaryColor, backgroundColor, insideMargin, content)
    }
    if (host == null) {
        // Outside a scaffold there is no layer to draw over; keep the dialog visible in place.
        if (show) body()
        return
    }
    val owner = remember { Any() }
    val entry = remember(host) { host.entry(owner) }
    SideEffect {
        entry.content = body
        entry.dismiss = onDismissRequest
        entry.visible.targetState = show
    }
    DisposableEffect(host) { onDispose { host.remove(owner) } }
}

@Composable
private fun C17MaterialDialogSurface(
    title: String?, summary: String?, titleColor: Color?, summaryColor: Color?, backgroundColor: Color?,
    insideMargin: Dp, content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(Modifier.widthIn(min = 280.dp, max = 560.dp).semantics { dialog() }
        // Swallow taps inside the dialog so they never reach the scrim's dismiss handler.
        .pointerInput(Unit) { detectTapGestures { } },
        shape = MaterialTheme.shapes.extraLarge, color = backgroundColor ?: colors.surfaceContainerHigh,
        contentColor = colors.onSurface, tonalElevation = 6.dp) {
        BoxWithConstraints {
            val budget = maxHeight
            Column(Modifier.fillMaxWidth().heightIn(max = budget).padding(top = insideMargin, bottom = insideMargin)) {
                if (title != null || summary != null) {
                    Column(Modifier.fillMaxWidth().heightIn(max = budget * .35f).verticalScroll(rememberScrollState())
                        .padding(start = insideMargin, end = insideMargin, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (title != null) Text(title, Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineSmall,
                            color = titleColor ?: colors.onSurface, textAlign = TextAlign.Start)
                        if (summary != null) Text(summary, style = MaterialTheme.typography.bodyMedium,
                            color = summaryColor ?: colors.onSurfaceVariant)
                    }
                }
                Box(Modifier.fillMaxWidth().weight(1f, fill = false).padding(horizontal = insideMargin)) { content() }
            }
        }
    }
}
