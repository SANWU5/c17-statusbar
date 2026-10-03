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
    val Network5G: ImageVector = ImageVector.Builder("C17Network5G", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(5f, 4.25f); horizontalLineTo(19f)
            curveTo(20.93f, 4.25f, 22.5f, 5.82f, 22.5f, 7.75f)
            verticalLineTo(16.25f)
            curveTo(22.5f, 18.18f, 20.93f, 19.75f, 19f, 19.75f)
            horizontalLineTo(5f)
            curveTo(3.07f, 19.75f, 1.5f, 18.18f, 1.5f, 16.25f)
            verticalLineTo(7.75f)
            curveTo(1.5f, 5.82f, 3.07f, 4.25f, 5f, 4.25f)
            close()
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(10.6f, 7.65f); horizontalLineTo(5.45f); lineTo(5.1f, 12.45f)
            curveTo(5.86f, 12.22f, 6.51f, 12.1f, 7.25f, 12.1f)
            curveTo(8.69f, 12.1f, 9.36f, 12.63f, 9.36f, 13.58f)
            curveTo(9.36f, 14.56f, 8.58f, 15.11f, 7.38f, 15.11f)
            curveTo(6.43f, 15.11f, 5.72f, 14.88f, 5.03f, 14.4f)
            verticalLineTo(15.98f)
            curveTo(5.67f, 16.34f, 6.45f, 16.54f, 7.39f, 16.54f)
            curveTo(9.45f, 16.54f, 10.79f, 15.4f, 10.79f, 13.49f)
            curveTo(10.79f, 11.73f, 9.56f, 10.7f, 7.73f, 10.7f)
            curveTo(7.35f, 10.7f, 6.98f, 10.73f, 6.64f, 10.79f)
            lineTo(6.79f, 9.08f); horizontalLineTo(10.6f); close()

            moveTo(19.53f, 8.24f); lineTo(18.62f, 9.36f)
            curveTo(18.07f, 8.91f, 17.48f, 8.73f, 16.7f, 8.73f)
            curveTo(14.98f, 8.73f, 13.96f, 9.96f, 13.96f, 12.11f)
            curveTo(13.96f, 14.18f, 14.96f, 15.2f, 16.61f, 15.2f)
            curveTo(17.19f, 15.2f, 17.79f, 15.08f, 18.21f, 14.84f)
            verticalLineTo(12.8f); horizontalLineTo(16.49f)
            verticalLineTo(11.4f); horizontalLineTo(19.64f); verticalLineTo(15.61f)
            curveTo(18.83f, 16.23f, 17.83f, 16.58f, 16.51f, 16.58f)
            curveTo(13.97f, 16.58f, 12.43f, 14.94f, 12.43f, 12.13f)
            curveTo(12.43f, 9.3f, 14.07f, 7.34f, 16.63f, 7.34f)
            curveTo(17.83f, 7.34f, 18.83f, 7.63f, 19.53f, 8.24f)
            close()
        }
    }.build()
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
