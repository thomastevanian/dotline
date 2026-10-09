package com.dotline.launcher.home

import kotlin.math.max
import kotlin.math.min

/** Size of a widget surface in dp. */
data class WidgetBox(val width: Float, val height: Float)

/**
 * Pure geometry for the built-in Nothing style widgets. Every value is in dp so the numbers can be
 * checked in plain unit tests; the Compose layer only converts them with `.dp`.
 *
 * The dot-matrix helpers mirror the metrics of the DotText composable: a glyph is 5 x 7 dots, the
 * dot pitch is dot + gap and glyphs are separated by one extra pitch.
 */
object WidgetFit {
    private const val GLYPH_COLS = 5
    private const val GLYPH_ROWS = 7

    /** Gap / dot ratio of the big clock digits (the DotClock default of 6 dp dots with 1.6 dp gaps). */
    const val CLOCK_GAP_RATIO: Float = 1.6f / 6f

    /** DotClock draws AM / PM with dots that are 0.3 times the digit dots. */
    const val AMPM_SCALE: Float = 0.3f

    /** DotClock leaves two big dots between the time and the AM / PM mark. */
    const val AMPM_SPACER: Float = 2f

    /** Very fine dot-matrix text of the Nothing widgets: 1.0 dp dots, 0.4 dp gaps, 18 dp between lines. */
    const val FINE_DOT: Float = 1.0f
    const val FINE_GAP: Float = 0.4f
    const val FINE_LINE_PITCH: Float = 18f

    /** Nothing OS metrics: a circle widget is 66 dp wide, a capsule is 66 dp high. */
    const val CIRCLE_DIAMETER: Float = 66f
    const val CAPSULE_HEIGHT: Float = 66f

    /** Smallest dot diameter that is still worth drawing. */
    const val MIN_DOT: Float = 0.5f

    private const val DEFAULT_CARD_WIDTH = 146f
    private const val DEFAULT_CARD_HEIGHT = 140f
    private const val DEFAULT_CAPSULE_WIDTH = 150f

    private fun usable(v: Float): Boolean = v.isFinite() && v > 0f

    /** Width of [chars] dot-matrix glyphs. */
    fun dotTextWidth(chars: Int, dot: Float, gap: Float): Float {
        if (chars <= 0) return 0f
        val pitch = dot + gap
        val glyph = (GLYPH_COLS - 1) * pitch + dot
        return chars * glyph + (chars - 1) * pitch
    }

    /** Height of one line of dot-matrix glyphs. */
    fun dotTextHeight(dot: Float, gap: Float): Float = (GLYPH_ROWS - 1) * (dot + gap) + dot

    /** Dot diameter at which [chars] glyphs with gap = dot * [gapRatio] are exactly [width] wide. */
    fun dotForWidth(chars: Int, width: Float, gapRatio: Float): Float {
        val unit = dotTextWidth(chars, 1f, gapRatio)
        if (unit <= 0f || !usable(width)) return 0f
        return width / unit
    }

    private fun clampDot(dot: Float, maxDot: Float): Float {
        val capped = if (dot.isNaN()) MIN_DOT else min(dot, maxDot)
        return max(capped, MIN_DOT)
    }

    /**
     * Dot size of the inline clock ("HH:MM", plus a small AM / PM mark in 12 hour mode) that fits a
     * box of [availW] x [availH] dp. Two hour digits are always assumed so the size does not jump
     * when 9:59 becomes 10:00.
     */
    fun clockInlineDot(availW: Float, availH: Float, hasAmPm: Boolean, maxDot: Float): Float {
        val k = CLOCK_GAP_RATIO
        var unitWidth = dotTextWidth(5, 1f, k)
        if (hasAmPm) {
            unitWidth += AMPM_SPACER + dotTextWidth(2, AMPM_SCALE, AMPM_SCALE * k)
        }
        val unitHeight = dotTextHeight(1f, k)
        val byW = if (usable(availW)) availW / unitWidth else MIN_DOT
        val byH = if (usable(availH)) availH / unitHeight else MIN_DOT
        return clampDot(min(byW, byH), maxDot)
    }

    /** Dot size of the stacked clock (hours above minutes, AM / PM below in 12 hour mode). */
    fun clockStackedDot(availW: Float, availH: Float, hasAmPm: Boolean, maxDot: Float): Float {
        val k = CLOCK_GAP_RATIO
        val unitWidth = dotTextWidth(2, 1f, k)
        val digitsHeight = dotTextHeight(1f, k)
        var unitHeight = 2f * digitsHeight + 2f
        if (hasAmPm) {
            unitHeight += 2f + dotTextHeight(AMPM_SCALE, AMPM_SCALE * k)
        }
        val byW = if (usable(availW)) availW / unitWidth else MIN_DOT
        val byH = if (usable(availH)) availH / unitHeight else MIN_DOT
        return clampDot(min(byW, byH), maxDot)
    }

    /**
     * Dot size of a big two digit number (the day of the month) that fills [fill] of [availW] and
     * leaves [reservedH] of the height to other content.
     */
    fun numberDot(availW: Float, availH: Float, reservedH: Float, fill: Float, maxDot: Float): Float {
        val k = CLOCK_GAP_RATIO
        val byW = if (usable(availW)) fill * availW / dotTextWidth(2, 1f, k) else MIN_DOT
        val free = availH - reservedH
        val byH = if (usable(free)) free / dotTextHeight(1f, k) else MIN_DOT
        return clampDot(min(byW, byH), maxDot)
    }

    /**
     * Font size (dp) of the NORMAL (non dot-matrix) clock style. Space Grotesk digits are about
     * 0.66 em wide; the estimate errs on the small side so the text never overflows.
     */
    fun normalClockFontSize(availW: Float, availH: Float, stacked: Boolean, hasAmPm: Boolean, maxSize: Float): Float {
        val reserveW = if (hasAmPm && !stacked) 30f else 0f
        val reserveH = if (hasAmPm && stacked) 18f else 0f
        val widthEm = if (stacked) 1.4f else 3.0f
        val heightEm = if (stacked) 2.0f else 1.0f
        val byW = if (usable(availW - reserveW)) (availW - reserveW) / widthEm else 8f
        val byH = if (usable(availH - reserveH)) (availH - reserveH) / heightEm else 8f
        return max(min(min(byW, byH), maxSize), 8f)
    }

    /** Width of a block of fine dot lines whose longest line has [maxChars] characters, at [scale]. */
    fun fineLinesWidth(maxChars: Int, scale: Float): Float =
        dotTextWidth(maxChars, FINE_DOT * scale, FINE_GAP * scale)

    /** Height of [lineCount] fine dot lines, [FINE_LINE_PITCH] * scale apart. */
    fun fineLinesHeight(lineCount: Int, scale: Float): Float {
        if (lineCount <= 0) return 0f
        return (lineCount - 1) * FINE_LINE_PITCH * scale + dotTextHeight(FINE_DOT * scale, FINE_GAP * scale)
    }

    /** Largest scale (within [minScale]..[maxScale]) at which the fine lines fit [availW] x [availH]. */
    fun fineScale(
        maxChars: Int,
        lineCount: Int,
        availW: Float,
        availH: Float,
        minScale: Float,
        maxScale: Float,
    ): Float {
        if (maxChars <= 0 || lineCount <= 0) return minScale
        if (!(availW > 0f) || !(availH > 0f)) return minScale
        var s = maxScale
        if (availW.isFinite()) s = min(s, availW / fineLinesWidth(maxChars, 1f))
        if (availH.isFinite()) s = min(s, availH / fineLinesHeight(lineCount, 1f))
        return max(s, minScale)
    }

    /**
     * Scale of the content of the weather card (date, condition and temperature in fine dots) for a
     * card of [availW] x [availH]: 1.0 on the 131 dp tall reference card, growing a little on taller
     * cards so the text keeps the proportions of the screenshot, never wider than the card.
     */
    fun weatherCardScale(maxChars: Int, availW: Float, availH: Float): Float {
        if (!(availW > 0f) || !(availH > 0f)) return 0.6f
        var s = min(availH / WEATHER_CARD_REFERENCE_HEIGHT, WEATHER_CARD_MAX_SCALE)
        if (maxChars > 0 && availW.isFinite()) {
            val needed = WEATHER_CARD_PAD_X * 2f + fineLinesWidth(maxChars, 1f)
            s = min(s, availW / needed)
        }
        return max(min(s, WEATHER_CARD_MAX_SCALE), 0.6f)
    }

    const val WEATHER_CARD_REFERENCE_HEIGHT: Float = 131f
    const val WEATHER_CARD_MAX_SCALE: Float = 1.35f
    const val WEATHER_CARD_PAD_X: Float = 14f

    /**
     * Dot diameter of the percentage drawn inside a dot ring of [ringDiameter] dp: the digits take
     * about half of the ring width.
     */
    fun ringDigitDot(chars: Int, ringDiameter: Float, maxDot: Float): Float {
        val d = dotForWidth(chars, ringDiameter * 0.5f, 0.4f)
        return clampDot(d, maxDot)
    }

    /**
     * Size of the visible widget surface inside the box the home grid gives the widget: cards fill
     * it, a capsule is at most [CAPSULE_HEIGHT] high, a circle is at most [CIRCLE_DIAMETER] wide and
     * always round. Unbounded or empty boxes fall back to sensible defaults.
     */
    fun surface(shape: WidgetShape, availW: Float, availH: Float): WidgetBox = when (shape) {
        WidgetShape.CIRCLE -> {
            val side = when {
                usable(availW) && usable(availH) -> min(availW, availH)
                usable(availW) -> availW
                usable(availH) -> availH
                else -> CIRCLE_DIAMETER
            }
            val d = min(side, CIRCLE_DIAMETER)
            WidgetBox(d, d)
        }
        WidgetShape.CAPSULE -> {
            val w = if (usable(availW)) availW else DEFAULT_CAPSULE_WIDTH
            val h = if (usable(availH)) min(availH, CAPSULE_HEIGHT) else CAPSULE_HEIGHT
            WidgetBox(w, h)
        }
        WidgetShape.CARD -> {
            val w = if (usable(availW)) availW else DEFAULT_CARD_WIDTH
            val h = if (usable(availH)) availH else DEFAULT_CARD_HEIGHT
            WidgetBox(w, h)
        }
    }
}

/** Battery numbers from the sticky ACTION_BATTERY_CHANGED broadcast, kept pure so they can be tested. */
object BatteryMath {
    /** android.os.BatteryManager.BATTERY_STATUS_CHARGING */
    const val STATUS_CHARGING = 2

    /** android.os.BatteryManager.BATTERY_STATUS_FULL (plugged in and full) */
    const val STATUS_FULL = 5

    /** Battery percentage 0..100 from the broadcast extras, or -1 when they are missing or invalid. */
    fun percent(level: Int, scale: Int): Int {
        if (level < 0 || scale <= 0) return -1
        val p = Math.round(level * 100.0 / scale).toInt()
        return if (p < 0) 0 else if (p > 100) 100 else p
    }

    fun isCharging(status: Int): Boolean = status == STATUS_CHARGING || status == STATUS_FULL

    /** 0..1 for the progress indicators; unknown (-1) counts as empty. */
    fun progress(percent: Int): Float {
        if (percent <= 0) return 0f
        if (percent >= 100) return 1f
        return percent / 100f
    }
}
