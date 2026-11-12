// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Tab icons drawn in the ColorOS 17 manner (24 dp grid, 1.8 dp rounded strokes for the idle state, solid shapes with
 * knock-outs for the selected state), so the COUI capsule reads like the system Gallery tabs. Original drawings.
 */
internal object C17ColorOsIcons {
    private const val STROKE = 1.8f

    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

    private fun ImageVector.Builder.stroke(block: PathBuilder.() -> Unit) =
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = block)

    private fun ImageVector.Builder.solid(block: PathBuilder.() -> Unit) =
        path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd, pathBuilder = block)

    private fun PathBuilder.house() {
        moveTo(4.6f, 9.4f)
        lineTo(10.7f, 4.3f)
        curveTo(11.45f, 3.67f, 12.55f, 3.67f, 13.3f, 4.3f)
        lineTo(19.4f, 9.4f)
        curveTo(19.78f, 9.72f, 20f, 10.19f, 20f, 10.68f)
        lineTo(20f, 18.4f)
        curveTo(20f, 19.5f, 19.1f, 20.4f, 18f, 20.4f)
        lineTo(6f, 20.4f)
        curveTo(4.9f, 20.4f, 4f, 19.5f, 4f, 18.4f)
        lineTo(4f, 10.68f)
        curveTo(4f, 10.19f, 4.22f, 9.72f, 4.6f, 9.4f)
        close()
    }

    val HomeOutline: ImageVector by lazy {
        icon("C17HomeOutline") {
            stroke { house() }
            stroke { moveTo(9.6f, 20.2f); lineTo(9.6f, 16f); curveTo(9.6f, 15.34f, 10.14f, 14.8f, 10.8f, 14.8f)
                lineTo(13.2f, 14.8f); curveTo(13.86f, 14.8f, 14.4f, 15.34f, 14.4f, 16f); lineTo(14.4f, 20.2f) }
        }
    }

    val HomeFilled: ImageVector by lazy {
        icon("C17HomeFilled") {
            solid {
                house()
                // Door knock-out.
                moveTo(10.9f, 15.4f); lineTo(13.1f, 15.4f); curveTo(13.6f, 15.4f, 14f, 15.8f, 14f, 16.3f)
                lineTo(14f, 20.4f); lineTo(10f, 20.4f); lineTo(10f, 16.3f); curveTo(10f, 15.8f, 10.4f, 15.4f, 10.9f, 15.4f); close()
            }
        }
    }

    private fun PathBuilder.knob(cx: Float, cy: Float, r: Float) {
        moveTo(cx + r, cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx - r, y1 = cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx + r, y1 = cy)
        close()
    }

    /** 配置: three rounded slider tracks with their knobs. */
    val TuneOutline: ImageVector by lazy {
        icon("C17TuneOutline") {
            stroke { moveTo(4f, 7f); lineTo(6.6f, 7f); moveTo(11.4f, 7f); lineTo(20f, 7f) }
            stroke { knob(9f, 7f, 2.4f) }
            stroke { moveTo(4f, 17f); lineTo(12.6f, 17f); moveTo(17.4f, 17f); lineTo(20f, 17f) }
            stroke { knob(15f, 17f, 2.4f) }
        }
    }

    val TuneFilled: ImageVector by lazy {
        icon("C17TuneFilled") {
            stroke { moveTo(4f, 7f); lineTo(6.2f, 7f); moveTo(11.8f, 7f); lineTo(20f, 7f) }
            solid { knob(9f, 7f, 3.1f) }
            stroke { moveTo(4f, 17f); lineTo(12.2f, 17f); moveTo(17.8f, 17f); lineTo(20f, 17f) }
            solid { knob(15f, 17f, 3.1f) }
        }
    }

    val InfoOutline: ImageVector by lazy {
        icon("C17InfoOutline") {
            stroke { knob(12f, 12f, 8.6f) }
            stroke { moveTo(12f, 11f); lineTo(12f, 16.4f) }
            solid { knob(12f, 7.9f, 1.15f) }
        }
    }

    val InfoFilled: ImageVector by lazy {
        icon("C17InfoFilled") {
            solid {
                knob(12f, 12f, 9.5f)
                // The "i" is knocked out of the disc.
                knob(12f, 7.9f, 1.25f)
                moveTo(11.1f, 11f); curveTo(11.1f, 10.5f, 11.5f, 10.1f, 12f, 10.1f); curveTo(12.5f, 10.1f, 12.9f, 10.5f, 12.9f, 11f)
                lineTo(12.9f, 16.4f); curveTo(12.9f, 16.9f, 12.5f, 17.3f, 12f, 17.3f); curveTo(11.5f, 17.3f, 11.1f, 16.9f, 11.1f, 16.4f); close()
            }
        }
    }
}
