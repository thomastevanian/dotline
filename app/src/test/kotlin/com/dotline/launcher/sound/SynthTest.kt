package com.dotline.launcher.sound

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SynthTest {
    @Test
    fun noteFrequencies() {
        assertEquals(440.0, Notes.hz("A4"), 0.001)
        assertEquals(261.6256, Notes.hz("C4"), 0.01)
        assertEquals(Notes.hz("Db5"), Notes.hz("C#5"), 0.001)
    }

    @Test
    fun libraryHasRequestedCounts() {
        assertEquals(8, SoundLibrary.ringtones.size)
        assertEquals(12, SoundLibrary.notifications.size)
        assertEquals(6, SoundLibrary.alarms.size)
        assertEquals(6, SoundLibrary.ui.size)
        assertEquals(32, SoundLibrary.all.map { it.id }.toSet().size, "unique ids")
        assertEquals(32, SoundLibrary.all.map { it.name to it.category }.toSet().size)
    }

    @Test
    fun everySoundRendersCleanly() {
        for (spec in SoundLibrary.all) {
            val pcm = Synth.render(spec)
            val ms = pcm.size * 1000.0 / Synth.SAMPLE_RATE
            when (spec.category) {
                SoundCategory.RINGTONE -> assertTrue(ms in 3000.0..9000.0, "${spec.id} ringtone length $ms")
                SoundCategory.NOTIFICATION -> assertTrue(ms in 150.0..1600.0, "${spec.id} notification length $ms")
                SoundCategory.ALARM -> assertTrue(ms in 3000.0..9000.0, "${spec.id} alarm length $ms")
                SoundCategory.UI -> assertTrue(ms in 25.0..120.0, "${spec.id} ui length $ms")
            }
            var peak = 0
            var sumSq = 0.0
            for (s in pcm) { peak = maxOf(peak, abs(s.toInt())); sumSq += s.toDouble() * s }
            val rms = sqrt(sumSq / pcm.size)
            assertTrue(peak in 3000..32767, "${spec.id} peak $peak")
            assertTrue(peak <= (32767 * 0.91).toInt(), "${spec.id} leaves headroom (no clipping), peak $peak")
            assertTrue(rms > 40, "${spec.id} is audible, rms $rms")
            assertTrue(abs(pcm.first().toInt()) < 2500, "${spec.id} starts near zero (no click)")
            assertTrue(abs(pcm.last().toInt()) < 400, "${spec.id} ends near zero (no click)")
        }
    }

    @Test
    fun renderingIsDeterministic() {
        val spec = SoundLibrary.ringtones[1]
        assertTrue(Synth.render(spec).contentEquals(Synth.render(spec)))
    }

    @Test
    fun squareWaveIsBandLimitedAndBalanced() {
        val spec = SoundSpec("t", "t", SoundCategory.NOTIFICATION, listOf(Voice(Wave.SQUARE, 1000.0, 0, 200, 0.8, 1, 5)), 220)
        val pcm = Synth.render(spec)
        val mean = pcm.map { it.toDouble() }.average()
        assertTrue(abs(mean) < 600, "square wave has near-zero DC, mean $mean")
    }

    @Test
    fun wavHeaderIsValid() {
        val bytes = Wav.encode(shortArrayOf(1, -1, 300), 44_100)
        assertEquals("RIFF", String(bytes, 0, 4))
        assertEquals("WAVE", String(bytes, 8, 4))
        assertEquals("data", String(bytes, 36, 4))
        assertEquals(44 + 6, bytes.size)
        assertEquals(6, bytes[40].toInt())
        assertEquals(44100 and 0xFF, bytes[24].toInt() and 0xFF)
        assertEquals(300 and 0xFF, bytes[48].toInt() and 0xFF)
    }
}
