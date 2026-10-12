package com.dotline.launcher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.dotline.launcher.ui.theme.DotlineTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Small UI glyphs drawn in code. All icons use a 24 unit grid (1 unit = 1dp at the default 24dp size),
 * a 2 unit round-capped stroke and scale with whatever size the caller gives them. The caller's
 * modifier is chained first so a caller-provided size wins over the 24dp default.
 */

/** Back arrow (left pointing, with shaft). */
@Composable
fun BackArrowIcon(modifier: Modifier = Modifier, color: Color = DotlineTheme.colors.primary) {
    Canvas(modifier.size(24.dp)) {
        val u = size.minDimension / 24f
        val stroke = Stroke(width = 2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path = Path()
        path.moveTo(19f * u, 12f * u)
        path.lineTo(5f * u, 12f * u)
        path.moveTo(11f * u, 6f * u)
        path.lineTo(5f * u, 12f * u)
        path.lineTo(11f * u, 18f * u)
        drawPath(path, color, style = stroke)
    }
}

/** Chevron pointing right. */
@Composable
fun ChevronIcon(modifier: Modifier = Modifier, color: Color = DotlineTheme.colors.primary) {
    Canvas(modifier.size(24.dp)) {
        val u = size.minDimension / 24f
        val stroke = Stroke(width = 2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path = Path()
        path.moveTo(9f * u, 5f * u)
        path.lineTo(16f * u, 12f * u)
        path.lineTo(9f * u, 19f * u)
        drawPath(path, color, style = stroke)
    }
}

/** Close (x). */
@Composable
fun CloseIcon(modifier: Modifier = Modifier, color: Color = DotlineTheme.colors.primary) {
    Canvas(modifier.size(24.dp)) {
        val u = size.minDimension / 24f
        val stroke = Stroke(width = 2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path = Path()
        path.moveTo(6f * u, 6f * u)
        path.lineTo(18f * u, 18f * u)
        path.moveTo(18f * u, 6f * u)
        path.lineTo(6f * u, 18f * u)
        drawPath(path, color, style = stroke)
    }
}

/** Plus. */
@Composable
fun PlusIcon(modifier: Modifier = Modifier, color: Color = DotlineTheme.colors.primary) {
    Canvas(modifier.size(24.dp)) {
        val u = size.minDimension / 24f
        val stroke = Stroke(width = 2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path = Path()
        path.moveTo(12f * u, 5f * u)
        path.lineTo(12f * u, 19f * u)
        path.moveTo(5f * u, 12f * u)
        path.lineTo(19f * u, 12f * u)
        drawPath(path, color, style = stroke)
    }
}

/** Check mark. */
@Composable
fun CheckIcon(modifier: Modifier = Modifier, color: Color = DotlineTheme.colors.primary) {
    Canvas(modifier.size(24.dp)) {
        val u = size.minDimension / 24f
        val stroke = Stroke(width = 2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path = Path()
        path.moveTo(5f * u, 12.5f * u)
        path.lineTo(10f * u, 17.5f * u)
        path.lineTo(19f * u, 7f * u)
        drawPath(path, color, style = stroke)
    }
}

/** Magnifier: ring plus handle. */
@Composable
fun SearchIcon(modifier: Modifier = Modifier, color: Color = DotlineTheme.colors.primary) {
    Canvas(modifier.size(24.dp)) {
        val u = size.minDimension / 24f
        val stroke = Stroke(width = 2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawCircle(
            color = color,
            radius = 6.5f * u,
            center = Offset(10.5f * u, 10.5f * u),
            style = stroke,
        )
        val handle = Path()
        handle.moveTo(15.5f * u, 15.5f * u)
        handle.lineTo(20f * u, 20f * u)
        drawPath(handle, color, style = stroke)
    }
}

/** Gear: centre ring with eight short spokes. */
@Composable
fun GearIcon(modifier: Modifier = Modifier, color: Color = DotlineTheme.colors.primary) {
    Canvas(modifier.size(24.dp)) {
        val u = size.minDimension / 24f
        val stroke = Stroke(width = 2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val cx = 12f * u
        val cy = 12f * u
        drawCircle(
            color = color,
            radius = 5f * u,
            center = Offset(cx, cy),
            style = stroke,
        )
        val spokes = Path()
        for (i in 0 until 8) {
            val angle = (i * 45f) * (PI.toFloat() / 180f)
            val dx = cos(angle)
            val dy = sin(angle)
            spokes.moveTo(cx + dx * 7.5f * u, cy + dy * 7.5f * u)
            spokes.lineTo(cx + dx * 10f * u, cy + dy * 10f * u)
        }
        drawPath(spokes, color, style = stroke)
    }
}
