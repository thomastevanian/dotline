package com.dotline.launcher.home

import com.dotline.launcher.data.TimeFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InfoWidgetMathTest {
    private fun close(expected: Float, actual: Float, tolerance: Float = 0.001f): Boolean =
        kotlin.math.abs(expected - actual) <= tolerance

    @Test
    fun splitTimeSeparatesAmPm() {
        assertEquals(Pair("2:05", "PM"), InfoWidgetMath.splitTime("2:05 PM"))
        assertEquals(Pair("12:30", "AM"), InfoWidgetMath.splitTime("12:30 AM"))
        val plain = InfoWidgetMath.splitTime("14:05")
        assertEquals("14:05", plain.first)
        assertNull(plain.second)
    }

    @Test
    fun splitTimeHandlesNonBreakingSpaces() {
        assertEquals(Pair("2:05", "PM"), InfoWidgetMath.splitTime("2:05 PM"))
        assertEquals(Pair("2:05", "PM"), InfoWidgetMath.splitTime("2:05 PM"))
        assertEquals(Pair("2:05", null), InfoWidgetMath.splitTime(" 2:05 "))
    }

    @Test
    fun timeWidthOfFiveDigitsAtThreeDp() {
        // 5 glyphs of 19 dp (4 x 4 + 3) and 4 gaps of 4 dp: 111 dp.
        assertTrue(close(111f, InfoWidgetMath.timeWidth("14:05", 3f)))
    }

    @Test
    fun timeWidthIncludesTheAmPmMark() {
        val digitsOnly = InfoWidgetMath.timeWidth("2:05", 3f)
        val withMark = InfoWidgetMath.timeWidth("2:05 PM", 3f)
        assertTrue(withMark > digitsOnly + 6f)
    }

    @Test
    fun timeWidthScalesLinearlyWithTheDot() {
        val one = InfoWidgetMath.timeWidth("2:05 PM", 1f)
        val three = InfoWidgetMath.timeWidth("2:05 PM", 3f)
        assertTrue(close(one * 3f, three, 0.01f))
    }

    @Test
    fun fitTimeDotKeepsTheMaximumWhenThereIsRoom() {
        assertEquals(3f, InfoWidgetMath.fitTimeDot("14:05", 200f, 3f))
    }

    @Test
    fun fitTimeDotShrinksToTheWidth() {
        val dot = InfoWidgetMath.fitTimeDot("14:05", 55.5f, 3f)
        assertTrue(close(1.5f, dot))
        assertTrue(InfoWidgetMath.timeWidth("14:05", dot) <= 55.5f + 0.01f)
    }

    @Test
    fun fitTimeDotNeverGoesBelowTheSmallestDot() {
        assertEquals(WidgetFit.MIN_DOT, InfoWidgetMath.fitTimeDot("14:05", 1f, 3f))
        assertEquals(WidgetFit.MIN_DOT, InfoWidgetMath.fitTimeDot("14:05", 0f, 3f))
        assertEquals(WidgetFit.MIN_DOT, InfoWidgetMath.fitTimeDot("14:05", Float.NaN, 3f))
    }

    @Test
    fun fitTextDotFitsPlainText() {
        // "ALL DAY" is 7 glyphs: 7 x 19 + 6 x 4 = 157 dp at 3 dp.
        assertEquals(3f, InfoWidgetMath.fitTextDot("ALL DAY", 160f, 3f))
        val dot = InfoWidgetMath.fitTextDot("ALL DAY", 78.5f, 3f)
        assertTrue(close(1.5f, dot))
    }

    @Test
    fun dotHeightOfSevenRows() {
        // 6 pitches of 4 dp plus one dot of 3 dp.
        assertTrue(close(27f, InfoWidgetMath.dotHeight(3f)))
    }

    @Test
    fun fitLinesIsClamped() {
        assertEquals(1, InfoWidgetMath.fitLines(10f, 19f, 5))
        assertEquals(3, InfoWidgetMath.fitLines(60f, 19f, 5))
        assertEquals(5, InfoWidgetMath.fitLines(500f, 19f, 5))
        assertEquals(1, InfoWidgetMath.fitLines(0f, 19f, 5))
        assertEquals(1, InfoWidgetMath.fitLines(100f, 0f, 5))
        assertEquals(1, InfoWidgetMath.fitLines(100f, 19f, 0))
        assertEquals(1, InfoWidgetMath.fitLines(Float.NaN, 19f, 5))
    }

    @Test
    fun dayCaptions() {
        assertEquals("", InfoWidgetMath.dayCaption(0))
        assertEquals("+1D", InfoWidgetMath.dayCaption(1))
        assertEquals("-1D", InfoWidgetMath.dayCaption(-1))
    }

    @Test
    fun use24hFollowsTheSetting() {
        assertTrue(InfoWidgetMath.use24h(TimeFormat.H24, false))
        assertTrue(!InfoWidgetMath.use24h(TimeFormat.H12, true))
        assertTrue(InfoWidgetMath.use24h(TimeFormat.SYSTEM, true))
        assertTrue(!InfoWidgetMath.use24h(TimeFormat.SYSTEM, false))
    }

    @Test
    fun clampTextCutsToTheLimit() {
        assertEquals("abc", InfoWidgetMath.clampText("abcdef", 3))
        assertEquals("abc", InfoWidgetMath.clampText("abc", 3))
        assertEquals("", InfoWidgetMath.clampText("abc", 0))
    }

    @Test
    fun clampTextNeverSplitsASurrogatePair() {
        val smiley = "😀"
        val text = "ab" + smiley + "cd"
        // Cutting at 3 would keep only the high surrogate.
        assertEquals("ab", InfoWidgetMath.clampText(text, 3))
        assertEquals("ab" + smiley, InfoWidgetMath.clampText(text, 4))
    }

    @Test
    fun notePreviewTrimsAndClamps() {
        assertEquals("hello", InfoWidgetMath.notePreview("  hello \n\n", 100))
        assertEquals("hel", InfoWidgetMath.notePreview("  hello \n\n", 3))
        assertEquals("", InfoWidgetMath.notePreview("   \n", 10))
    }
}
