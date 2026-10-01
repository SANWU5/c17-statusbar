// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Matching optical size and rounded strokes for the two overview destinations. */
object C17SectionIcons {
    val StatusBar: ImageVector = ImageVector.Builder("C17StatusBar", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2.4f,
            strokeLineCap = StrokeCap.Round) {
            moveTo(4f, 19f); lineTo(4f, 15f)
            moveTo(9f, 19f); lineTo(9f, 11f)
            moveTo(14f, 19f); lineTo(14f, 7f)
            moveTo(19f, 19f); lineTo(19f, 3f)
        }
    }.build()
    val NotificationCenter: ImageVector = ImageVector.Builder("C17NotificationCenter", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.9f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(5f, 17f)
            curveTo(6.5f, 15.5f, 7f, 14.5f, 7f, 11f)
            curveTo(7f, 7.5f, 8.9f, 5f, 12f, 5f)
            curveTo(15.1f, 5f, 17f, 7.5f, 17f, 11f)
            curveTo(17f, 14.5f, 17.5f, 15.5f, 19f, 17f)
            close()
            moveTo(12f, 2.7f); lineTo(12f, 4.5f)
            moveTo(10f, 20f); curveTo(11f, 21.2f, 13f, 21.2f, 14f, 20f)
        }
    }.build()
}
