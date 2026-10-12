package com.dotline.launcher.sound

/** Number of bars in the small waveform strip shown next to every sound. */
const val WAVEFORM_BARS = 24

/**
 * Coarse amplitude summary of [pcm]: the samples are cut into [bars] equal slices and each slice
 * is reduced to its peak absolute amplitude. The result is scaled so the loudest slice is 1f;
 * silence (or empty input) gives all zeros. Always returns exactly [bars] values (none when
 * [bars] is zero or negative). Pure and allocation-light, safe to call off the main thread.
 */
fun amplitudeSummary(pcm: ShortArray, bars: Int = WAVEFORM_BARS): FloatArray {
    if (bars <= 0) return FloatArray(0)
    val out = FloatArray(bars)
    val n = pcm.size
    if (n == 0) return out

    var overall = 0
    for (b in 0 until bars) {
        val start = (b.toLong() * n / bars).toInt().coerceAtMost(n - 1)
        val rawEnd = ((b + 1).toLong() * n / bars).toInt()
        val end = rawEnd.coerceAtLeast(start + 1).coerceAtMost(n)
        var peak = 0
        for (i in start until end) {
            val v = pcm[i].toInt()
            val a = if (v < 0) -v else v
            if (a > peak) peak = a
        }
        out[b] = peak.toFloat()
        if (peak > overall) overall = peak
    }

    if (overall == 0) {
        for (b in 0 until bars) out[b] = 0f
        return out
    }
    val scale = 1f / overall.toFloat()
    for (b in 0 until bars) {
        val scaled = out[b] * scale
        out[b] = if (scaled > 1f) 1f else scaled
    }
    return out
}
