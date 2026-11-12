// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.TextButtonColors

/** Material list item: a full-width ripple, 56 dp minimum and the shared measured label/value layout. */
@Composable
internal fun C17MaterialPreferenceRow(
    modifier: Modifier = Modifier,
    startAction: @Composable (() -> Unit)? = null,
    endActions: @Composable (RowScope.() -> Unit)? = null,
    bottomAction: (@Composable () -> Unit)? = null,
    insideMargin: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val click = if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier
    Column(modifier.fillMaxWidth().c17MaterialSegment().heightIn(min = 56.dp).then(click)
        .semantics(mergeDescendants = true) { if (!enabled) disabled() }
        .padding(insideMargin)) {
        C17MeasuredRow(startAction, endActions, content)
        if (bottomAction != null) {
            Spacer(Modifier.size(8.dp))
            bottomAction()
        }
    }
}

@Composable
internal fun C17MaterialPreferenceLabel(title: String?, summary: String?, enabled: Boolean) {
    val colors = MaterialTheme.colorScheme
    val disabled = colors.onSurface.copy(alpha = .38f)
    Column {
        if (title != null) Text(title, style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) colors.onSurface else disabled)
        if (summary != null) Text(summary, Modifier.padding(top = if (title == null) 0.dp else 2.dp),
            style = MaterialTheme.typography.bodyMedium, color = if (enabled) colors.onSurfaceVariant else disabled)
    }
}

/**
 * C17's block buttons in Material: the confirming action is a filled button, the others are tonal, so paired
 * buttons keep the same size and reading order as in the other families. Explicit colours map to the container.
 */
@Composable
internal fun C17MaterialButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    primary: Boolean,
    colors: TextButtonColors?,
) {
    val padding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
    val label: @Composable () -> Unit = {
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
    val minimum = modifier.heightIn(min = 48.dp)
    when {
        colors != null -> Button(onClick = onClick, modifier = minimum, enabled = enabled, contentPadding = padding,
            colors = ButtonDefaults.buttonColors(containerColor = colors.color, contentColor = colors.textColor,
                disabledContainerColor = colors.disabledColor, disabledContentColor = colors.disabledTextColor)) { label() }
        primary -> Button(onClick = onClick, modifier = minimum, enabled = enabled, contentPadding = padding) { label() }
        else -> FilledTonalButton(onClick = onClick, modifier = minimum, enabled = enabled, contentPadding = padding) { label() }
    }
}

/** Material 3 navigation bar: filled icon on the selected tab, its pill indicator and ripple, an opaque surface. */
@Composable
internal fun C17MaterialNavigationBar(page: Int, safeInsets: PaddingValues, onPage: (Int) -> Unit) {
    val labels = arrayOf("主页", "配置", "关于")
    val selectedIcons = arrayOf(Icons.Filled.Home, Icons.Filled.Settings, Icons.Filled.Info)
    val idleIcons = arrayOf(Icons.Outlined.Home, Icons.Outlined.Settings, Icons.Outlined.Info)
    NavigationBar(
        modifier = Modifier.pointerInput(Unit) { detectTapGestures { /* Consume taps in the bar's unused inset. */ } },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = NavigationBarDefaults.Elevation,
        // The activity measures the gesture and cutout insets; the surface still reaches the window edge.
        windowInsets = safeInsets.asWindowInsets(LocalLayoutDirection.current),
    ) {
        labels.forEachIndexed { index, label ->
            val selected = page == index
            NavigationBarItem(selected = selected, onClick = { if (!selected) onPage(index) },
                icon = { Icon(if (selected) selectedIcons[index] else idleIcons[index], contentDescription = null) },
                label = { Text(tr(label), maxLines = 1) })
        }
    }
}

private fun PaddingValues.asWindowInsets(direction: LayoutDirection): WindowInsets = WindowInsets(
    left = calculateLeftPadding(direction), top = 0.dp,
    right = calculateRightPadding(direction), bottom = calculateBottomPadding(),
)


/** Shared by a Material settings group and its rows: where the group is, and the page colour of the gaps. */
internal class C17MaterialGroup(val gap: Color) {
    var coordinates: LayoutCoordinates? = null
}

internal val LocalC17MaterialGroup = staticCompositionLocalOf<C17MaterialGroup?> { null }

/**
 * Material 3 Expressive segmented list, as in Android 16 settings: a tonal group with large outer corners whose
 * rows are separated by 2 dp page-coloured gaps. Free-form content inside the group keeps the tonal background.
 */
@Composable
internal fun C17MaterialSegmentedGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    val group = remember(colors.surface) { C17MaterialGroup(colors.surface) }
    CompositionLocalProvider(LocalC17MaterialGroup provides group) {
        Column(modifier.fillMaxWidth().onPlaced { group.coordinates = it }
            .clip(RoundedCornerShape(C17MaterialCardRadius)).background(colors.surfaceContainer), content = content)
    }
}

/** A row below the first one in a segmented group opens with the 2 dp gap; elsewhere this does nothing. */
@Composable
internal fun Modifier.c17MaterialSegment(): Modifier {
    val group = LocalC17MaterialGroup.current ?: return this
    var top by remember { mutableFloatStateOf(0f) }
    return this.onPlaced { coordinates ->
        top = group.coordinates?.takeIf { it.isAttached && coordinates.isAttached }
            ?.localPositionOf(coordinates, Offset.Zero)?.y ?: 0f
    }.drawWithContent {
        drawContent()
        if (top > .5f) drawRect(group.gap, size = Size(size.width, 2.dp.toPx()))
    }
}

/**
 * Material tabs without outlines. A single choice (contour) is a Material 3 Expressive connected button group:
 * filled segments 2 dp apart, full outer corners, small inner corners, and the selected segment becomes a primary
 * pill. Content tabs are filled filter chips with the secondary container on the selected one. Rows that do not fit
 * scroll horizontally, keeping full labels at every text size.
 */
@Composable
internal fun C17MaterialTabs(
    tabs: List<String>,
    selected: Int,
    onTabSelected: (Int) -> Unit,
    connected: Boolean,
    enabled: Boolean,
    height: Dp?,
    labelWidths: List<Dp>,
    labelHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val gap = if (connected) 2.dp else 8.dp
    val scroll = rememberScrollState()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val natural = labelWidths.map { (it + 32.dp).coerceAtLeast(if (connected) 56.dp else 48.dp) }
        val total = natural.fold(0.dp) { sum, width -> sum + width } + gap * (tabs.size - 1)
        val fits = total <= maxWidth
        val extra = if (connected && fits) (maxWidth - total) / tabs.size else 0.dp
        val rowHeight = (height ?: (labelHeight + 24.dp)).coerceAtLeast(if (connected) 40.dp else 32.dp)
        Row(Modifier.fillMaxWidth().horizontalScroll(scroll, enabled = !fits).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(gap)) {
            tabs.forEachIndexed { index, label ->
                val on = index == selected
                val first = index == 0
                val last = index == tabs.lastIndex
                val inner by animateDpAsState(if (on && connected) rowHeight / 2 else 8.dp, label = "c17-material-tab-corner")
                val outer = if (connected) rowHeight / 2 else 8.dp
                val shape = RoundedCornerShape(
                    topStart = if (first || !connected) outer else inner, bottomStart = if (first || !connected) outer else inner,
                    topEnd = if (last || !connected) outer else inner, bottomEnd = if (last || !connected) outer else inner)
                val container by animateColorAsState(when {
                    !enabled -> colors.onSurface.copy(alpha = .12f)
                    on && connected -> colors.primary
                    on -> colors.secondaryContainer
                    connected -> colors.surfaceContainerHighest
                    else -> colors.surfaceContainerHigh
                }, label = "c17-material-tab-container")
                val ink = when {
                    !enabled -> colors.onSurface.copy(alpha = .38f)
                    on && connected -> colors.onPrimary
                    on -> colors.onSecondaryContainer
                    else -> colors.onSurfaceVariant
                }
                Box(Modifier.width(natural[index] + extra).heightIn(min = rowHeight).clip(shape).background(container)
                    .selectable(selected = on, enabled = enabled, role = Role.Tab, onClick = {
                        if (enabled && index in tabs.indices) onTabSelected(index)
                    }).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                    Text(tr(label), style = MaterialTheme.typography.labelLarge, color = ink, softWrap = false)
                }
            }
        }
    }
}

/** Material dialog actions: text buttons aligned to the end, the confirming one last. */
@Composable
internal fun C17MaterialDialogActions(onDismiss: () -> Unit, confirmLabel: String?, confirmEnabled: Boolean, onConfirm: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
        TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(tr("取消")) }
        if (confirmLabel != null && onConfirm != null) TextButton(onClick = onConfirm, enabled = confirmEnabled,
            modifier = Modifier.heightIn(min = 48.dp)) { Text(tr(confirmLabel)) }
    }
}
