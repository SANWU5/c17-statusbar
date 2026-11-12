// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Content that has scrolled up past the list's top edge fades out, the same Miuix fade in every component family.
 * The fade is as long as the list has scrolled, up to a fixed 33 dp, so a list at rest is untouched and nothing
 * needs to be tuned. The offscreen layer exists only while the fade is actually drawn. (App242's progressive blur
 * for COUI/Material flickered on the device and was retired at the user's request.)
 */
fun Modifier.edgeFade(state: LazyListState, enabled: Boolean): Modifier = if (!enabled) this else
    topFade { if (state.firstVisibleItemIndex > 0) Float.MAX_VALUE else state.firstVisibleItemScrollOffset.toFloat() }

/** The same fade for a plain scrolling column (welcome and language pages). Place it before `verticalScroll`. */
fun Modifier.edgeFade(state: ScrollState, enabled: Boolean): Modifier = if (!enabled) this else
    topFade { state.value.toFloat() }

/** [scrolled] is how far the content has moved up, in pixels; the fade grows with it up to 33 dp. */
private fun Modifier.topFade(scrolled: () -> Float): Modifier =
    graphicsLayer {
        compositingStrategy = if (scrolled() > 0f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
    }.drawWithContent {
        drawContent()
        val length = minOf(33.dp.toPx(), scrolled())
        if (length > 0f) drawRect(
            brush = Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, startY = 0f, endY = length),
            size = Size(size.width, length), blendMode = BlendMode.DstIn)
    }
