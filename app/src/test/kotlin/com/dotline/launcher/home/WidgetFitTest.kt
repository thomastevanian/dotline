package com.dotline.launcher.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WidgetFitTest {
    private val eps = 0.01f

    @Test
    fun dotTextMetricsMatchTheDotTextComposable() {
        // 6 dp dots, 1.6 dp gaps: glyph = 4 * 7.6 + 6 = 36.4, "12:34" = 5 * 36.4 + 4 * 7.6
        assertEquals(5 * 36.4f + 4 * 7.6f, WidgetFit.dotTextWidth(5, 6f, 1.6f), eps)
        assertEquals(6 * 7.6f + 6f, WidgetFit.dotTextHeight(6f, 1.6f), eps)
        assertEquals(0f, WidgetFit.dotTextWidth(0, 6f, 1.6f))
    }

    @Test
    fun inlineClockFillsTheWidthWithoutOverflowing() {
        for (hasAmPm in listOf(false, true)) {
            val avail = 260f
            val dot = WidgetFit.clockInlineDot(avail, 500f, hasAmPm, 50f)
            val k = WidgetFit.CLOCK_GAP_RATIO
            var width = WidgetFit.dotTextWidth(5, dot, dot * k)
            if (hasAmPm) {
                width += WidgetFit.AMPM_SPACER * dot +
                    WidgetFit.dotTextWidth(2, dot * WidgetFit.AMPM_SCALE, dot * WidgetFit.AMPM_SCALE * k)
            }
            assertEquals(avail, width, 0.5f)
        }
    }

    @Test
    fun inlineClockIsLimitedByHeightAndMaxDot() {
        val byHeight = WidgetFit.clockInlineDot(1000f, 40f, false, 50f)
        val k = WidgetFit.CLOCK_GAP_RATIO
        assertEquals(40f, WidgetFit.dotTextHeight(byHeight, byHeight * k), 0.1f)
        assertEquals(4f, WidgetFit.clockInlineDot(1000f, 1000f, false, 4f))
    }

    @Test
    fun stackedClockFitsTheCard() {
        val k = WidgetFit.CLOCK_GAP_RATIO
        for (hasAmPm in listOf(false, true)) {
            val dot = WidgetFit.clockStackedDot(114f, 168f, hasAmPm, 50f)
            val width = WidgetFit.dotTextWidth(2, dot, dot * k)
            var height = 2 * WidgetFit.dotTextHeight(dot, dot * k) + 2 * dot
            if (hasAmPm) {
                height += 2 * dot + WidgetFit.dotTextHeight(dot * WidgetFit.AMPM_SCALE, dot * WidgetFit.AMPM_SCALE * k)
            }
            assertTrue(width <= 114f + 0.5f, "width $width")
            assertTrue(height <= 168f + 0.5f, "height $height")
            assertTrue(dot > 5f, "dot $dot is big")
        }
    }

    @Test
    fun degenerateBoxesNeverGiveNanOrZero() {
        assertEquals(WidgetFit.MIN_DOT, WidgetFit.clockInlineDot(0f, 0f, false, 8f))
        assertEquals(WidgetFit.MIN_DOT, WidgetFit.clockInlineDot(Float.NaN, 40f, true, 8f))
        assertEquals(WidgetFit.MIN_DOT, WidgetFit.clockStackedDot(-3f, 10f, false, 8f))
        assertTrue(WidgetFit.normalClockFontSize(0f, 0f, true, true, 80f) >= 8f)
    }

    @Test
    fun fineScaleRespectsBothAxesAndLimits() {
        // plenty of room: limited by maxScale
        assertEquals(1.8f, WidgetFit.fineScale(6, 2, 500f, 500f, 0.6f, 1.8f))
        // narrow: limited by width
        val narrow = WidgetFit.fineScale(10, 1, 40f, 500f, 0.3f, 2f)
        assertEquals(40f, WidgetFit.fineLinesWidth(10, narrow), 0.1f)
        // short: limited by height
        val short = WidgetFit.fineScale(3, 2, 500f, 20f, 0.3f, 2f)
        assertEquals(20f, WidgetFit.fineLinesHeight(2, short), 0.1f)
        // impossible: falls back to the minimum
        assertEquals(0.6f, WidgetFit.fineScale(30, 2, 20f, 10f, 0.6f, 2f))
        assertEquals(0.6f, WidgetFit.fineScale(0, 2, 20f, 10f, 0.6f, 2f))
    }

    @Test
    fun fineLinesUseEighteenDpLinePitch() {
        assertEquals(18f * 2 + 9.4f, WidgetFit.fineLinesHeight(3, 1f), eps)
        assertEquals(9.4f, WidgetFit.fineLinesHeight(1, 1f), eps)
        assertEquals(0f, WidgetFit.fineLinesHeight(0, 1f))
    }

    @Test
    fun weatherCardScaleGrowsWithHeightButStaysInsideTheCard() {
        assertEquals(1f, WidgetFit.weatherCardScale(8, 351f, 131f), eps)
        assertEquals(WidgetFit.WEATHER_CARD_MAX_SCALE, WidgetFit.weatherCardScale(8, 292f, 200f), eps)
        // a very long line shrinks the card content so it fits the width
        val s = WidgetFit.weatherCardScale(22, 200f, 200f)
        assertTrue(WidgetFit.WEATHER_CARD_PAD_X * 2 + WidgetFit.fineLinesWidth(22, s) <= 200.5f)
        assertEquals(0.6f, WidgetFit.weatherCardScale(8, 0f, 100f))
    }

    @Test
    fun ringDigitsStayInsideTheRing() {
        for (chars in 1..3) {
            val dot = WidgetFit.ringDigitDot(chars, 100f, 6f)
            assertTrue(dot <= 6f && dot >= WidgetFit.MIN_DOT)
            assertTrue(WidgetFit.dotTextWidth(chars, dot, dot * 0.4f) <= 50.5f || dot == WidgetFit.MIN_DOT)
        }
    }

    @Test
    fun numberDotUsesTheSmallerOfWidthAndHeight() {
        val byWidth = WidgetFit.numberDot(114f, 1000f, 0f, 0.8f, 100f)
        assertEquals(0.8f * 114f, WidgetFit.dotTextWidth(2, byWidth, byWidth * WidgetFit.CLOCK_GAP_RATIO), 0.5f)
        val byHeight = WidgetFit.numberDot(1000f, 100f, 40f, 0.8f, 100f)
        assertEquals(60f, WidgetFit.dotTextHeight(byHeight, byHeight * WidgetFit.CLOCK_GAP_RATIO), 0.5f)
    }

    @Test
    fun surfaceKeepsNothingProportions() {
        // circle: round, at most 66 dp
        assertEquals(WidgetBox(66f, 66f), WidgetFit.surface(WidgetShape.CIRCLE, 73f, 100f))
        assertEquals(WidgetBox(61f, 61f), WidgetFit.surface(WidgetShape.CIRCLE, 61f, 100f))
        assertEquals(WidgetBox(66f, 66f), WidgetFit.surface(WidgetShape.CIRCLE, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY))
        // capsule: full width, at most 66 dp high
        assertEquals(WidgetBox(146f, 66f), WidgetFit.surface(WidgetShape.CAPSULE, 146f, 100f))
        assertEquals(WidgetBox(292f, 50f), WidgetFit.surface(WidgetShape.CAPSULE, 292f, 50f))
        // card: fills the cells
        assertEquals(WidgetBox(292f, 200f), WidgetFit.surface(WidgetShape.CARD, 292f, 200f))
        // unbounded or empty boxes fall back to defaults
        val fallback = WidgetFit.surface(WidgetShape.CARD, Float.POSITIVE_INFINITY, 0f)
        assertTrue(fallback.width > 0f && fallback.height > 0f)
    }

    @Test
    fun batteryPercentAndCharging() {
        assertEquals(66, BatteryMath.percent(66, 100))
        assertEquals(50, BatteryMath.percent(2500, 5000))
        assertEquals(100, BatteryMath.percent(120, 100))
        assertEquals(0, BatteryMath.percent(0, 100))
        assertEquals(-1, BatteryMath.percent(-1, 100))
        assertEquals(-1, BatteryMath.percent(50, 0))
        assertTrue(BatteryMath.isCharging(2))
        assertTrue(BatteryMath.isCharging(5))
        assertTrue(!BatteryMath.isCharging(3))
        assertTrue(!BatteryMath.isCharging(-1))
        assertEquals(0f, BatteryMath.progress(-1))
        assertEquals(0.66f, BatteryMath.progress(66), 0.0001f)
        assertEquals(1f, BatteryMath.progress(100))
    }
}
