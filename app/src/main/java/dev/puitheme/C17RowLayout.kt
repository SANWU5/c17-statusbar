// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp

/** COUI and Material rows share the 16/12 dp list padding. */
internal val C17RowMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp)

/**
 * Start icon, label and trailing value of a settings row, shared by COUI and Material. A trailing value may take at
 * most 44% of the row; a longer one (large fonts, long values) moves to a second line instead of squeezing the title.
 */
@Composable
internal fun C17MeasuredRow(
    startAction: @Composable (() -> Unit)?,
    endActions: @Composable (RowScope.() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Layout(content = {
        if (startAction != null) Box(Modifier.layoutId("start"), contentAlignment = Alignment.Center) { startAction() }
        Column(Modifier.layoutId("label"), content = content)
        if (endActions != null) Row(Modifier.layoutId("value"), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End, content = endActions)
    }) { measurables, constraints ->
        val width = constraints.maxWidth
        val gap = 10.dp.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val start = measurables.firstOrNull { it.layoutId == "start" }?.measure(loose.copy(maxWidth = width / 4))
        val inset = (start?.width ?: 0) + if (start == null) 0 else gap
        val remaining = (width - inset).coerceAtLeast(0)
        val label = measurables.first { it.layoutId == "label" }
        val value = measurables.firstOrNull { it.layoutId == "value" }
        val share = ((remaining - gap).coerceAtLeast(0) * .44f).toInt()
        // An empty trailing slot reports 0 and keeps the one-line path without a second measurement.
        val naturalValue = value?.maxIntrinsicWidth(Constraints.Infinity) ?: 0
        val needsSecondLine = naturalValue > maxOf(48.dp.roundToPx(), share)
        val end = value?.measure(loose.copy(maxWidth = if (needsSecondLine) remaining else share))
        val labelWidth = if (needsSecondLine || end == null || end.width == 0) remaining else
            (remaining - end.width - gap).coerceAtLeast(0)
        val center = label.measure(loose.copy(maxWidth = labelWidth))
        val firstHeight = maxOf(start?.height ?: 0, center.height, if (needsSecondLine) 0 else end?.height ?: 0)
        val secondLine = needsSecondLine && end != null && end.height > 0
        val height = firstHeight + if (secondLine) gap + end.height else 0
        layout(width, constraints.constrainHeight(height)) {
            start?.placeRelative(0, (firstHeight - start.height) / 2)
            center.placeRelative(inset, (firstHeight - center.height) / 2)
            end?.placeRelative(width - end.width, if (needsSecondLine) firstHeight + gap else (firstHeight - end.height) / 2)
        }
    }
}
