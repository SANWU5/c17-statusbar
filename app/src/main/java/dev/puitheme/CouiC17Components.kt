// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.github.suqi8.coui.kmp.basic.BasicComponent as CouiBasicComponent
import io.github.suqi8.coui.kmp.basic.Text as CouiText
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.basic.ArrowRight
import io.github.suqi8.coui.kmp.theme.COUITheme

/** COUI's real row and press surface around the shared measured label/value layout. */
@Composable
internal fun C17CouiPreferenceRow(
    modifier: Modifier = Modifier,
    startAction: @Composable (() -> Unit)? = null,
    endActions: @Composable (RowScope.() -> Unit)? = null,
    bottomAction: (@Composable () -> Unit)? = null,
    insideMargin: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    CouiBasicComponent(
        modifier = modifier.heightIn(min = 56.dp).semantics(mergeDescendants = true) {
            if (!enabled) disabled()
        },
        insideMargin = insideMargin,
        onClick = onClick,
        enabled = enabled,
        bottomAction = bottomAction,
    ) {
        C17MeasuredRow(startAction, endActions, content)
    }
}

@Composable
internal fun C17CouiArrow(enabled: Boolean) {
    val color = if (enabled) COUITheme.colorScheme.onSurfaceVariantActions else COUITheme.colorScheme.disabledOnSecondaryVariant
    val filter = remember(color) { ColorFilter.tint(color) }
    Spacer(Modifier.width(8.dp))
    Image(COUIIcons.Basic.ArrowRight, contentDescription = null,
        modifier = Modifier.size(width = 12.dp, height = 24.dp), colorFilter = filter)
}

@Composable
internal fun C17CouiPreferenceLabel(title: String?, summary: String?, enabled: Boolean) {
    Column {
        if (title != null) CouiText(title, style = COUITheme.textStyles.headline1, fontWeight = FontWeight.Medium,
            color = if (enabled) COUITheme.colorScheme.onBackground else COUITheme.colorScheme.disabledOnSecondaryVariant)
        if (summary != null) CouiText(summary, Modifier.padding(top = if (title == null) 0.dp else 2.dp),
            style = COUITheme.textStyles.body2, lineHeight = 1.4.em,
            color = if (enabled) COUITheme.colorScheme.onSurfaceVariantSummary else COUITheme.colorScheme.disabledOnSecondaryVariant)
    }
}

/** Keep long headings readable while leaving finite room for C17's scrollable forms and action buttons. */
@Composable
internal fun C17CouiDialogContent(
    title: String?,
    summary: String?,
    titleColor: Color,
    summaryColor: Color,
    insideMargin: DpSize,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val compactScroll = rememberScrollState()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val availableHeight = maxHeight
        val compact = availableHeight < 220.dp * density.fontScale.coerceAtLeast(1f)
        val contentBudget = if (compact) maxOf(360.dp, 220.dp * density.fontScale + 100.dp).coerceAtMost(720.dp)
            else availableHeight
        // Keep the form at the same composition position when IME changes the viewport.
        // In a very short window, scroll a finite inner form rather than dropping its weighted body.
        Box(Modifier.fillMaxWidth().then(if (compact) Modifier.verticalScroll(compactScroll) else Modifier)) {
        Column(Modifier.fillMaxWidth().then(if (compact) Modifier.height(contentBudget)
            else Modifier.heightIn(max = contentBudget))) {
            if (title != null || summary != null) {
                Column(Modifier.fillMaxWidth().heightIn(max = contentBudget * .35f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = insideMargin.width, end = insideMargin.width,
                        top = insideMargin.height, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (title != null) CouiText(title, Modifier.fillMaxWidth(),
                        style = COUITheme.textStyles.title4, lineHeight = 1.3.em,
                        fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, color = titleColor)
                    if (summary != null) CouiText(summary, Modifier.fillMaxWidth(),
                        style = COUITheme.textStyles.body2, lineHeight = 1.45.em, color = summaryColor)
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f, fill = false)
                .padding(start = insideMargin.width, end = insideMargin.width,
                    top = if (title == null && summary == null) insideMargin.height else 0.dp,
                    bottom = insideMargin.height)) { content() }
        }
        }
    }
}
