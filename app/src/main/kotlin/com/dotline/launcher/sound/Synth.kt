package com.dotline.launcher.sound

import androidx.compose.runtime.Immutable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

enum class Wave { SINE, SQUARE, TRIANGLE }

enum class SoundCategory(val title: String) {
    RINGTONE("Ringtones"),
    NOTIFICATION("Notifications"),
    ALARM("Alarms"),
    UI("UI clicks"),
}

/** One tone: a waveform at [hz] starting at [startMs] for [durMs], optionally gliding to [glideToHz]. */
@Immutable
data class Voice(
    val wave: Wave,
    val hz: Double,
    val startMs: Int,
    val durMs: Int,
    val gain: Double = 0.7,
    val attackMs: Int = 3,
    /** Fade-out at the end of the voice; equal to [durMs] means a pure decay. */
    val releaseMs: Int = 40,
    val glideToHz: Double? = null,
    /** Pulse width for SQUARE. */
    val duty: Double = 0.5,
)

@Immutable
data class SoundSpec(
    val id: String,
    val name: String,
    val category: SoundCategory,
    val voices: List<Voice>,
    val lengthMs: Int,
)

/** Offline mono 16-bit synthesiser: no assets, no samples, everything computed from [SoundSpec]. */
object Synth {
    const val SAMPLE_RATE = 44_100
    private const val PEAK = 0.9

    fun render(spec: SoundSpec, sampleRate: Int = SAMPLE_RATE): ShortArray {
        val total = (spec.lengthMs.toLong() * sampleRate / 1000).toInt().coerceAtLeast(1)
        val mix = DoubleArray(total)
        for (v in spec.voices) addVoice(mix, v, sampleRate)

        var peak = 0.0
        for (s in mix) peak = max(peak, abs(s))
        val scale = if (peak > PEAK) PEAK / peak else 1.0
        return ShortArray(total) { i -> (mix[i] * scale * 32767.0).toInt().coerceIn(-32767, 32767).toShort() }
    }

    private fun addVoice(mix: DoubleArray, v: Voice, sr: Int) {
        val start = (v.startMs.toLong() * sr / 1000).toInt()
        val n = (v.durMs.toLong() * sr / 1000).toInt()
        if (n <= 0 || start >= mix.size) return
        val attack = max(1, (v.attackMs.toLong() * sr / 1000).toInt())
        val release = min(n, max(1, (v.releaseMs.toLong() * sr / 1000).toInt()))
        var phase = 0.0
        for (i in 0 until n) {
            val idx = start + i
            if (idx >= mix.size) break
            val progress = i.toDouble() / n
            val hz = if (v.glideToHz != null) v.hz + (v.glideToHz - v.hz) * progress else v.hz
            val dt = hz / sr
            val sample = when (v.wave) {
                Wave.SINE -> sin(2.0 * PI * phase)
                Wave.TRIANGLE -> 1.0 - 4.0 * abs(phase - 0.5)
                Wave.SQUARE -> square(phase, dt, v.duty)
            }
            val a = min(1.0, (i + 1).toDouble() / attack)
            val tail = n - i
            val r = if (tail >= release) 1.0 else (tail.toDouble() / release).pow(1.6)
            mix[idx] += sample * v.gain * a * r
            phase += dt
            if (phase >= 1.0) phase -= 1.0
        }
    }

    // PolyBLEP-corrected pulse wave: keeps the edges band-limited so there is no harsh aliasing.
    private fun square(phase: Double, dt: Double, duty: Double): Double {
        var v = if (phase < duty) 1.0 else -1.0
        v += blep(phase, dt)
        v -= blep((phase + 1.0 - duty) % 1.0, dt)
        return v
    }

    private fun blep(t: Double, dt: Double): Double = when {
        t < dt -> { val x = t / dt; x + x - x * x - 1.0 }
        t > 1.0 - dt -> { val x = (t - 1.0) / dt; x * x + x + x + 1.0 }
        else -> 0.0
    }
}

/** 16-bit mono PCM -> RIFF/WAVE bytes. */
object Wav {
    fun encode(pcm: ShortArray, sampleRate: Int = Synth.SAMPLE_RATE): ByteArray {
        val dataBytes = pcm.size * 2
        val out = ByteArray(44 + dataBytes)
        fun putInt(at: Int, v: Int) { for (i in 0 until 4) out[at + i] = ((v shr (8 * i)) and 0xFF).toByte() }
        fun putShort(at: Int, v: Int) { out[at] = (v and 0xFF).toByte(); out[at + 1] = ((v shr 8) and 0xFF).toByte() }
        "RIFF".forEachIndexed { i, c -> out[i] = c.code.toByte() }
        putInt(4, 36 + dataBytes)
        "WAVEfmt ".forEachIndexed { i, c -> out[8 + i] = c.code.toByte() }
        putInt(16, 16)
        putShort(20, 1)            // PCM
        putShort(22, 1)            // mono
        putInt(24, sampleRate)
        putInt(28, sampleRate * 2) // byte rate
        putShort(32, 2)            // block align
        putShort(34, 16)           // bits per sample
        "data".forEachIndexed { i, c -> out[36 + i] = c.code.toByte() }
        putInt(40, dataBytes)
        for (i in pcm.indices) putShort(44 + i * 2, pcm[i].toInt())
        return out
    }
}
