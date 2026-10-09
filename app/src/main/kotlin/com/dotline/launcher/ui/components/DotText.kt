package com.dotline.launcher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dotline.launcher.ui.theme.DotlineTheme

/**
 * Pixel metrics of a dot-matrix string. [dotPx] is the dot diameter, [pitchPx] the
 * centre-to-centre distance between neighbouring dots, [charGapPx] the extra space between glyphs.
 */
@Immutable
class DotMetrics(val dotPx: Float, val pitchPx: Float, val charGapPx: Float, val length: Int) {
    val glyphWidthPx: Float get() = (DotFont.COLS - 1) * pitchPx + dotPx
    val heightPx: Float get() = (DotFont.ROWS - 1) * pitchPx + dotPx
    val widthPx: Float
        get() = if (length == 0) 0f else length * glyphWidthPx + (length - 1) * charGapPx
}

/**
 * Draws [text] as a grid of round dots on a Canvas. Only lit dots are drawn unless
 * [offColor] is visible, in which case the unlit dots of every cell are drawn faintly too.
 *
 * @param dot dot diameter
 * @param gap empty space between neighbouring dots (pitch = dot + gap)
 * @param charGap empty space between glyph cells; defaults to one pitch
 */
@Composable
fun DotText(
    text: String,
    modifier: Modifier = Modifier,
    dot: Dp = 3.dp,
    gap: Dp = 1.5.dp,
    charGap: Dp = Dp.Unspecified,
    color: Color = DotlineTheme.colors.primary,
    offColor: Color = Color.Transparent,
) {
    val density = LocalDensity.current
    val metrics = remember(text, dot, gap, charGap, density) {
        with(density) {
            val dotPx = dot.toPx()
            val pitch = dotPx + gap.toPx()
            val cg = if (charGap == Dp.Unspecified) pitch else charGap.toPx()
            DotMetrics(dotPx, pitch, cg, text.length)
        }
    }
    val widthDp = with(density) { metrics.widthPx.toDp() }
    val heightDp = with(density) { metrics.heightPx.toDp() }
    Canvas(modifier.size(widthDp, heightDp)) {
        drawDotString(text, Offset.Zero, metrics, color, offColor)
    }
}

/** Draws [text] starting at [origin] (top-left of the first glyph cell). */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDotString(
    text: String,
    origin: Offset,
    m: DotMetrics,
    color: Color,
    offColor: Color = Color.Transparent,
) {
    val r = m.dotPx / 2f
    val drawOff = offColor.alpha > 0f
    var x0 = origin.x
    for (ch in text) {
        val rows = DotFont.rows(ch)
        for (row in 0 until DotFont.ROWS) {
            val cy = origin.y + r + row * m.pitchPx
            for (col in 0 until DotFont.COLS) {
                val on = DotFont.isOn(rows, row, col)
                if (on || drawOff) {
                    drawCircle(if (on) color else offColor, r, Offset(x0 + r + col * m.pitchPx, cy))
                }
            }
        }
        x0 += m.glyphWidthPx + m.charGapPx
    }
}
