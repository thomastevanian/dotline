package com.dotline.launcher.home

import com.dotline.launcher.data.TimeFormat
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Pure helpers of the calendar, world clock and notes widgets: how big the dot-matrix times can be
 * for the space they get, how many lines of text fit, and the small captions. Everything is in dp
 * (plain floats) so it is unit tested without Compose.
 *
 * Times use dots with a gap of one third of the dot (3 dp dots with 1 dp gaps at the largest size),
 * which is the "fine but readable" dot-matrix of the Nothing stats card.
 */
object InfoWidgetMath {
    /** gap = dot * GAP_RATIO. */
    const val GAP_RATIO: Float = 1f / 3f

    /** The AM / PM mark of a 12 hour time is drawn with dots this fraction of the digit dots. */
    const val SUFFIX_SCALE: Float = 0.45f

    /** Space between the digits and the AM / PM mark, in digit dots. */
    const val SUFFIX_SPACE: Float = 2f

    /** "2:05 PM" becomes ("2:05", "PM"); "14:05" becomes ("14:05", null). */
    fun splitTime(time: String): Pair<String, String?> {
        val trimmed = time.trim()
        val cut = trimmed.indexOfFirst { it.isWhitespace() }
        if (cut < 0) return Pair(trimmed, null)
        val main = trimmed.substring(0, cut)
        val rest = trimmed.substring(cut + 1).trim()
        return Pair(main, if (rest.isEmpty()) null else rest)
    }

    /** Width in dp of [time] (with its AM / PM mark) drawn with dots of [dot] dp. */
    fun timeWidth(time: String, dot: Float): Float {
        val parts = splitTime(time)
        var width = WidgetFit.dotTextWidth(parts.first.length, dot, dot * GAP_RATIO)
        val suffix = parts.second
        if (suffix != null) {
            val small = dot * SUFFIX_SCALE
            width += dot * SUFFIX_SPACE + WidgetFit.dotTextWidth(suffix.length, small, small * GAP_RATIO)
        }
        return width
    }

    /** Largest dot (at most [maxDot], at least the smallest drawable dot) with which [time] fits [availW]. */
    fun fitTimeDot(time: String, availW: Float, maxDot: Float): Float {
        val unit = timeWidth(time, 1f)
        if (!(availW > 0f) || !(unit > 0f)) return WidgetFit.MIN_DOT
        return max(WidgetFit.MIN_DOT, min(maxDot, availW / unit))
    }

    /** Same as [fitTimeDot] for plain dot-matrix text such as "ALL DAY" or "9 OCT". */
    fun fitTextDot(text: String, availW: Float, maxDot: Float): Float {
        val unit = WidgetFit.dotTextWidth(text.length, 1f, GAP_RATIO)
        if (!(availW > 0f) || !(unit > 0f)) return WidgetFit.MIN_DOT
        return max(WidgetFit.MIN_DOT, min(maxDot, availW / unit))
    }

    /** Height in dp of one line of dot-matrix text drawn with dots of [dot] dp. */
    fun dotHeight(dot: Float): Float = WidgetFit.dotTextHeight(dot, dot * GAP_RATIO)

    /** How many lines of [lineHeight] dp fit [availH] dp: at least 1, at most [maxLines]. */
    fun fitLines(availH: Float, lineHeight: Float, maxLines: Int): Int {
        val cap = max(1, maxLines)
        if (!(availH > 0f) || !(lineHeight > 0f)) return 1
        val fit = floor(availH / lineHeight).toInt()
        return min(max(fit, 1), cap)
    }

    /** "+1D" or "-1D" for a city on the next or previous calendar day, empty for the same day. */
    fun dayCaption(dayOffset: Int): String = when {
        dayOffset > 0 -> "+" + dayOffset + "D"
        dayOffset < 0 -> "-" + (-dayOffset) + "D"
        else -> ""
    }

    /** True when times are shown on a 24 hour clock. [system24] is the phone's own setting. */
    fun use24h(format: TimeFormat, system24: Boolean): Boolean = when (format) {
        TimeFormat.H24 -> true
        TimeFormat.H12 -> false
        TimeFormat.SYSTEM -> system24
    }

    /** [text] cut to at most [limit] characters without splitting a surrogate pair. */
    fun clampText(text: String, limit: Int): String {
        if (limit <= 0) return ""
        if (text.length <= limit) return text
        var end = limit
        if (text[end - 1].isHighSurrogate()) end -= 1
        return text.substring(0, end)
    }

    /** The part of a note the widget shows: trimmed, never more than [maxChars] characters. */
    fun notePreview(note: String, maxChars: Int): String = clampText(note.trim(), maxChars)
}
