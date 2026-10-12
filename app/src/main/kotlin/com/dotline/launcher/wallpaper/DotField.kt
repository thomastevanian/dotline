package com.dotline.launcher.wallpaper

import com.dotline.launcher.ui.components.DotFont
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * A set of dots to draw: x, y, radius (px) and an accent flag, as parallel arrays.
 * Pure data, produced by [DotField.generate] with no Android types, so it is unit-tested.
 */
class DotSet(val x: FloatArray, val y: FloatArray, val r: FloatArray, val accent: BooleanArray) {
    val size: Int get() = x.size
}

object DotField {
    private class Builder {
        val xs = ArrayList<Float>(); val ys = ArrayList<Float>(); val rs = ArrayList<Float>(); val ac = ArrayList<Boolean>()
        fun add(x: Float, y: Float, r: Float, accent: Boolean = false) {
            if (r < 0.35f) return
            xs += x; ys += y; rs += r; ac += accent
        }
        fun build() = DotSet(xs.toFloatArray(), ys.toFloatArray(), rs.toFloatArray(), ac.toBooleanArray())
    }

    /**
     * @param timeText "HH:MM" used by DOT_TEXT when [WallpaperSpec.text] is empty
     */
    fun generate(spec: WallpaperSpec, width: Int, height: Int, timeText: String = "12:00"): DotSet {
        val w = width.toFloat()
        val h = height.toFloat()
        val pitch = (spec.spacing.coerceIn(0.01f, 0.12f)) * w
        val maxR = pitch * spec.dotSize.coerceIn(0.1f, 1f) / 2f
        val b = Builder()
        val rnd = Random(spec.seed)
        when (spec.pattern) {
            WallpaperPattern.DOT_GRID -> grid(b, w, h, pitch, maxR, rnd)
            WallpaperPattern.DOT_GRADIENT -> gradient(b, w, h, pitch, maxR, spec.seed)
            WallpaperPattern.CONCENTRIC -> concentric(b, w, h, pitch, maxR, spec.seed)
            WallpaperPattern.DOT_TEXT -> text(b, w, h, pitch, maxR, spec.text.ifBlank { timeText })
            WallpaperPattern.HALFTONE -> halftone(b, w, h, pitch, maxR, spec.seed)
            WallpaperPattern.RED_DOT -> redDot(b, w, h, pitch, maxR, spec.seed)
        }
        return b.build()
    }

    private fun grid(b: Builder, w: Float, h: Float, pitch: Float, maxR: Float, rnd: Random) {
        val cols = (w / pitch).toInt()
        val rows = (h / pitch).toInt()
        val x0 = (w - (cols - 1) * pitch) / 2f
        val y0 = (h - (rows - 1) * pitch) / 2f
        for (row in 0 until rows) for (col in 0 until cols) {
            // A touch of seeded variation so seeds visibly differ: 12 % size jitter.
            val jitter = 0.88f + 0.12f * rnd.nextFloat()
            b.add(x0 + col * pitch, y0 + row * pitch, maxR * jitter)
        }
    }

    /** Dots shrink with distance along a direction (seed 0 down, 1 up, 2 right, 3 left, else radial). */
    private fun gradient(b: Builder, w: Float, h: Float, pitch: Float, maxR: Float, seed: Long) {
        val cols = (w / pitch).toInt()
        val rows = (h / pitch).toInt()
        val x0 = (w - (cols - 1) * pitch) / 2f
        val y0 = (h - (rows - 1) * pitch) / 2f
        val mode = ((seed % 5) + 5) % 5
        val diag = hypot(w / 2f, h / 2f)
        for (row in 0 until rows) for (col in 0 until cols) {
            val x = x0 + col * pitch
            val y = y0 + row * pitch
            val t = when (mode) {
                0L -> y / h
                1L -> 1f - y / h
                2L -> x / w
                3L -> 1f - x / w
                else -> hypot(x - w / 2f, y - h / 2f) / diag
            }
            // t = 0 -> full size, t = 1 -> gone; ease so large dots hold longer.
            val k = (1f - t).coerceIn(0f, 1f)
            b.add(x, y, maxR * k * k * (3f - 2f * k))
        }
    }

    /** Rings of dots around a centre; dot count per ring follows the circumference. */
    private fun concentric(b: Builder, w: Float, h: Float, pitch: Float, maxR: Float, seed: Long) {
        val cx = w / 2f + if (seed % 3L == 0L) 0f else (((seed % 7) - 3) * 0.12f * w)
        val cy = h / 2f + if (seed % 3L == 0L) 0f else (((seed % 5) - 2) * 0.1f * h)
        val reach = max(hypot(cx, cy), max(hypot(w - cx, cy), max(hypot(cx, h - cy), hypot(w - cx, h - cy))))
        var ring = 1
        b.add(cx, cy, maxR)
        while (ring * pitch < reach) {
            val radius = ring * pitch
            val count = max(6, (2.0 * PI * radius / pitch).toInt())
            val fade = (1f - radius / reach).coerceIn(0.15f, 1f)
            for (i in 0 until count) {
                val a = 2.0 * PI * i / count
                val x = cx + (radius * cos(a)).toFloat()
                val y = cy + (radius * sin(a)).toFloat()
                if (x in -pitch..(w + pitch) && y in -pitch..(h + pitch)) b.add(x, y, maxR * fade)
            }
            ring++
        }
    }

    /** Big dot-matrix text (5x7 font) centred on the screen over a faint dot grid. */
    private fun text(b: Builder, w: Float, h: Float, pitch: Float, maxR: Float, text: String) {
        // Faint background grid.
        val cols = (w / pitch).toInt()
        val rows = (h / pitch).toInt()
        val gx0 = (w - (cols - 1) * pitch) / 2f
        val gy0 = (h - (rows - 1) * pitch) / 2f
        for (row in 0 until rows) for (col in 0 until cols) b.add(gx0 + col * pitch, gy0 + row * pitch, maxR * 0.28f)

        // Lines: "HH:MM" stacks as HH / MM; any other text wraps to fit.
        val clean = text.uppercase()
        val lines: List<String> = if (clean.length == 5 && clean[2] == ':' ) listOf(clean.substring(0, 2), clean.substring(3)) else wrap(clean, 6)
        val longest = lines.maxOf { it.length }.coerceAtLeast(1)
        val glyphCols = DotFont.COLS
        val unitsWide = longest * glyphCols + (longest - 1)      // dots across, one dot gap between glyphs
        val textPitch = min((w * 0.86f) / unitsWide, (h * 0.12f) / DotFont.ROWS)
        val rDot = textPitch * 0.42f
        val lineHeight = DotFont.ROWS * textPitch + textPitch * 2f
        val totalH = lines.size * lineHeight - textPitch * 2f
        var top = (h - totalH) / 2f
        for (line in lines) {
            val lineW = line.length * glyphCols * textPitch + (line.length - 1) * textPitch
            var left = (w - lineW) / 2f
            for (ch in line) {
                val rows7 = DotFont.rows(ch)
                for (r in 0 until DotFont.ROWS) for (c in 0 until glyphCols) {
                    if (DotFont.isOn(rows7, r, c)) b.add(left + (c + 0.5f) * textPitch, top + (r + 0.5f) * textPitch, rDot)
                }
                left += (glyphCols + 1) * textPitch
            }
            top += lineHeight
        }
    }

    private fun wrap(s: String, maxChars: Int): List<String> {
        if (s.length <= maxChars) return listOf(s.ifEmpty { " " })
        val words = s.split(' ').filter { it.isNotEmpty() }
        val out = ArrayList<String>()
        var cur = ""
        for (word in words) {
            val candidate = if (cur.isEmpty()) word else "$cur $word"
            if (candidate.length <= maxChars) cur = candidate else { if (cur.isNotEmpty()) out += cur; cur = word.take(maxChars) }
        }
        if (cur.isNotEmpty()) out += cur
        return out.ifEmpty { listOf(s.take(maxChars)) }
    }

    /** Halftone: a rotated grid whose dot size follows a smooth seeded field. */
    private fun halftone(b: Builder, w: Float, h: Float, pitch: Float, maxR: Float, seed: Long) {
        val rnd = Random(seed)
        val ph = DoubleArray(6) { rnd.nextDouble() * 2.0 * PI }
        val fx = doubleArrayOf(1.0 + rnd.nextDouble(), 2.2 + rnd.nextDouble(), 0.6 + rnd.nextDouble() * 0.6)
        val fy = doubleArrayOf(0.8 + rnd.nextDouble(), 1.7 + rnd.nextDouble(), 0.5 + rnd.nextDouble() * 0.6)
        val rowStep = pitch * 0.866f // hex-ish stagger like a screen print
        val rows = (h / rowStep).toInt() + 1
        val cols = (w / pitch).toInt() + 1
        for (row in 0 until rows) {
            val y = row * rowStep
            val shift = if (row % 2 == 0) 0f else pitch / 2f
            for (col in 0 until cols) {
                val x = col * pitch + shift
                val u = x / w.toDouble()
                val v = y / h.toDouble()
                val f = 0.5 + 0.22 * sin(2 * PI * fx[0] * u + ph[0]) * cos(2 * PI * fy[0] * v + ph[1]) +
                    0.18 * sin(2 * PI * (fx[1] * u + fy[1] * v) + ph[2]) +
                    0.10 * cos(2 * PI * (fx[2] * u - fy[2] * v) + ph[3])
                val k = f.coerceIn(0.0, 1.0).toFloat()
                b.add(x, y, maxR * 1.15f * k * k)
            }
        }
    }

    /** One accent dot on black; seed picks its position. */
    private fun redDot(b: Builder, w: Float, h: Float, pitch: Float, maxR: Float, seed: Long) {
        val spots = arrayOf(0.5f to 0.5f, 0.5f to 0.28f, 0.5f to 0.74f, 0.3f to 0.62f, 0.7f to 0.38f)
        val (px, py) = spots[(((seed % spots.size) + spots.size) % spots.size).toInt()]
        // Optional faint grid when dots are small (the "dot and grid" preset).
        if (maxR < pitch * 0.2f) {
            val cols = (w / pitch).toInt()
            val rows = (h / pitch).toInt()
            val x0 = (w - (cols - 1) * pitch) / 2f
            val y0 = (h - (rows - 1) * pitch) / 2f
            for (row in 0 until rows) for (col in 0 until cols) b.add(x0 + col * pitch, y0 + row * pitch, maxR)
        }
        b.add(w * px, h * py, w * 0.09f, accent = true)
    }

    /** Largest dot radius in the set, for tests and previews. */
    fun maxRadius(set: DotSet): Float = (0 until set.size).maxOfOrNull { set.r[it] } ?: 0f
}
