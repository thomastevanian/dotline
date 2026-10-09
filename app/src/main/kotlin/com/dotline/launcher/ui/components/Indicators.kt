package com.dotline.launcher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.dotline.launcher.ui.theme.DotlineTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Progress clamped to 0..1, NaN treated as 0. */
private fun indicatorClamp(value: Float): Float {
    if (value.isNaN()) return 0f
    if (value < 0f) return 0f
    if (value > 1f) return 1f
    return value
}

/**
 * A row of tiny round dots (1.5dp, 4dp pitch) in the outline colour. Fills the available width,
 * the dots are centred so both ends have equal space.
 */
@Composable
fun DottedDivider(modifier: Modifier = Modifier) {
    val color = DotlineTheme.colors.outline
    Canvas(modifier.fillMaxWidth().height(6.dp)) {
        val diameter = 1.5.dp.toPx()
        val pitch = 4.dp.toPx()
        val radius = diameter / 2f
        if (size.width >= diameter) {
            val count = ((size.width - diameter) / pitch).toInt() + 1
            val used = (count - 1) * pitch + diameter
            val start = (size.width - used) / 2f + radius
            val cy = size.height / 2f
            for (i in 0 until count) {
                drawCircle(color = color, radius = radius, center = Offset(start + i * pitch, cy))
            }
        }
    }
}

/**
 * Page dots: small round dots (6dp), the current page is a larger (8dp) dot in the primary colour,
 * the others use the tertiary colour.
 */
@Composable
fun DotPageIndicator(count: Int, current: Int, modifier: Modifier = Modifier) {
    val activeColor = DotlineTheme.colors.primary
    val inactiveColor = DotlineTheme.colors.tertiary
    val safeCount = if (count < 0) 0 else count
    val pitch = 14.dp
    Canvas(modifier.size(pitch * safeCount, 8.dp)) {
        val pitchPx = pitch.toPx()
        val smallRadius = 3.dp.toPx()
        val largeRadius = 4.dp.toPx()
        val cy = size.height / 2f
        for (i in 0 until safeCount) {
            val isActive = i == current
            drawCircle(
                color = if (isActive) activeColor else inactiveColor,
                radius = if (isActive) largeRadius else smallRadius,
                center = Offset(pitchPx * (i + 0.5f), cy),
            )
        }
    }
}

/**
 * Dotted progress bar: [dots] round dots spread over the available width; the first
 * round(progress * dots) are lit (primary), the rest are unlit (tertiary).
 */
@Composable
fun DotProgressBar(progress: Float, modifier: Modifier = Modifier, dots: Int = 20) {
    val litColor = DotlineTheme.colors.primary
    val unlitColor = DotlineTheme.colors.tertiary
    val total = if (dots < 1) 1 else dots
    val lit = (indicatorClamp(progress) * total).roundToInt()
    Canvas(modifier.fillMaxWidth().height(8.dp)) {
        val pitch = size.width / total
        val radius = min(pitch * 0.32f, size.height / 2f)
        val cy = size.height / 2f
        for (i in 0 until total) {
            drawCircle(
                color = if (i < lit) litColor else unlitColor,
                radius = radius,
                center = Offset(pitch * (i + 0.5f), cy),
            )
        }
    }
}

/**
 * Dotted progress ring: [dots] dots on a circle, lit clockwise from the top in proportion to
 * [progress]. Default size is 96dp; a caller-provided size wins.
 */
@Composable
fun DotRing(progress: Float, modifier: Modifier = Modifier, dots: Int = 40) {
    val litColor = DotlineTheme.colors.primary
    val unlitColor = DotlineTheme.colors.tertiary
    val total = if (dots < 1) 1 else dots
    val lit = (indicatorClamp(progress) * total).roundToInt()
    Canvas(modifier.size(96.dp)) {
        val outer = size.minDimension / 2f
        val twoPi = (2.0 * PI).toFloat()
        val dotRadius = outer * twoPi / total * 0.3f
        val ringRadius = outer - dotRadius
        val cx = size.width / 2f
        val cy = size.height / 2f
        for (i in 0 until total) {
            val angle = -PI.toFloat() / 2f + twoPi * i / total
            drawCircle(
                color = if (i < lit) litColor else unlitColor,
                radius = dotRadius,
                center = Offset(cx + cos(angle) * ringRadius, cy + sin(angle) * ringRadius),
            )
        }
    }
}
