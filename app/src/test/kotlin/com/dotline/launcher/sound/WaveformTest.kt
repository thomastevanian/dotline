package com.dotline.launcher.sound

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WaveformTest {
    @Test
    fun alwaysReturnsRequestedBarCount() {
        assertEquals(24, amplitudeSummary(ShortArray(1000) { 5 }).size)
        assertEquals(24, amplitudeSummary(ShortArray(7) { 5 }).size)
        assertEquals(24, amplitudeSummary(ShortArray(0)).size)
        assertEquals(10, amplitudeSummary(ShortArray(500) { 5 }, 10).size)
        assertEquals(0, amplitudeSummary(ShortArray(500) { 5 }, 0).size)
        assertEquals(0, amplitudeSummary(ShortArray(500) { 5 }, -3).size)
    }

    @Test
    fun silenceGivesZeros() {
        val bars = amplitudeSummary(ShortArray(4800))
        assertTrue(bars.all { it == 0f })
        assertTrue(amplitudeSummary(ShortArray(0)).all { it == 0f })
    }

    @Test
    fun loudestSliceIsOne() {
        // Quiet first half, louder second half.
        val pcm = ShortArray(2400) { i -> if (i < 1200) 1000 else 4000 }
        val bars = amplitudeSummary(pcm, 24)
        assertEquals(1f, bars.max(), 0.0001f)
        assertEquals(0.25f, bars[0], 0.0001f)
        assertEquals(1f, bars[23], 0.0001f)
    }

    @Test
    fun shapeFollowsTheSignal() {
        // Loud first half, silent second half.
        val pcm = ShortArray(2400) { i -> if (i < 1200) (if (i % 2 == 0) 20000 else -20000).toShort() else 0 }
        val bars = amplitudeSummary(pcm, 24)
        for (b in 0 until 12) assertEquals(1f, bars[b], 0.0001f, "bar $b")
        for (b in 12 until 24) assertEquals(0f, bars[b], 0.0001f, "bar $b")
    }

    @Test
    fun negativeSamplesCountAndMinValueDoesNotOverflow() {
        val pcm = ShortArray(240) { Short.MIN_VALUE }
        val bars = amplitudeSummary(pcm, 24)
        assertTrue(bars.all { it == 1f })
        val mixed = ShortArray(240) { i -> if (i == 130) (-100).toShort() else 0 }
        val m = amplitudeSummary(mixed, 24)
        assertEquals(1f, m[13], 0.0001f)
        assertEquals(0f, m[0], 0.0001f)
    }

    @Test
    fun shortInputStillFillsEveryBar() {
        val pcm = shortArrayOf(100, -200, 300)
        val bars = amplitudeSummary(pcm, 24)
        assertEquals(24, bars.size)
        assertTrue(bars.all { it in 0f..1f })
        assertEquals(1f, bars.max(), 0.0001f)
    }

    @Test
    fun everyLibrarySoundHasAVisibleShape() {
        for (spec in SoundLibrary.all) {
            val bars = amplitudeSummary(Synth.render(spec))
            assertEquals(WAVEFORM_BARS, bars.size, spec.id)
            assertEquals(1f, bars.max(), 0.0001f, spec.id)
            assertTrue(bars.all { it in 0f..1f }, spec.id)
        }
    }
}
