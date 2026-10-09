package com.dotline.launcher.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.dotline.launcher.core.CrashLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Plays one synthesised [SoundSpec] at a time through a static-mode [AudioTrack].
 *
 * The PCM is rendered on Dispatchers.Default (never on the main thread), written into a one-shot
 * static track and played. A single delay for the sound's duration then releases the track and
 * clears [playingId]; nothing runs while idle (no service, no wake lock, no polling).
 * Calling [play] while something is playing stops the previous sound first. [stop] and [release]
 * are idempotent and nothing here ever throws.
 */
class SoundPlayer(@Suppress("UNUSED_PARAMETER") context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()
    private val state = MutableStateFlow<String?>(null)

    /** Id of the sound that is playing (or being prepared), null when idle. */
    val playingId: StateFlow<String?> = state

    // All of the fields below are guarded by [lock].
    private var track: AudioTrack? = null
    private var job: Job? = null
    private var generation = 0L
    private var released = false

    fun play(spec: SoundSpec) {
        runCatching {
            synchronized(lock) {
                if (!released) {
                    stopLocked()
                    generation += 1
                    val mine = generation
                    state.value = spec.id
                    job = scope.launch { runSound(spec, mine) }
                }
            }
        }.onFailure { CrashLog.record("SoundPlayer.play ${spec.id}", it) }
    }

    fun stop() {
        runCatching {
            synchronized(lock) { stopLocked() }
        }.onFailure { CrashLog.record("SoundPlayer.stop", it) }
    }

    fun release() {
        runCatching {
            synchronized(lock) {
                if (!released) {
                    released = true
                    stopLocked()
                    scope.cancel()
                }
            }
        }.onFailure { CrashLog.record("SoundPlayer.release", it) }
    }

    /** Renders, plays and waits for the end of one sound. Runs on Dispatchers.Default. */
    private suspend fun runSound(spec: SoundSpec, mine: Long) {
        try {
            val rendered = Synth.render(spec)
            // Very short clicks are padded with silence: tiny static buffers are rejected on some devices.
            val pcm = if (rendered.size < MIN_SAMPLES) rendered.copyOf(MIN_SAMPLES) else rendered
            val built = buildTrack(pcm)
            var started = false
            synchronized(lock) {
                if (mine == generation && !released) {
                    track = built
                    built.play()
                    started = true
                }
            }
            if (!started) {
                releaseQuietly(built)
                return
            }
            val durationMs = pcm.size.toLong() * 1000L / Synth.SAMPLE_RATE
            delay(durationMs + END_MARGIN_MS)
            finish(mine)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            CrashLog.record("SoundPlayer.runSound ${spec.id}", e)
            finish(mine)
        }
    }

    /** Called when the sound ran to its end (or failed): frees the track if this is still the current sound. */
    private fun finish(mine: Long) {
        runCatching {
            synchronized(lock) {
                if (mine == generation) {
                    releaseTrackLocked()
                    job = null
                    state.value = null
                }
            }
        }.onFailure { CrashLog.record("SoundPlayer.finish", it) }
    }

    private fun stopLocked() {
        val running = job
        job = null
        if (running != null) running.cancel()
        // Anything still being prepared sees a new generation and discards itself.
        generation += 1
        releaseTrackLocked()
        state.value = null
    }

    private fun releaseTrackLocked() {
        val current = track
        track = null
        if (current != null) releaseQuietly(current)
    }

    private fun releaseQuietly(t: AudioTrack) {
        runCatching { t.stop() }
        runCatching { t.release() }
    }

    private fun buildTrack(pcm: ShortArray): AudioTrack {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(Synth.SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        val written = audioTrack.write(pcm, 0, pcm.size)
        if (written <= 0) {
            releaseQuietly(audioTrack)
            throw IllegalStateException("AudioTrack accepted no samples (write returned $written)")
        }
        return audioTrack
    }

    private companion object {
        /** Slack after the last sample so the tail is never cut off by the release. */
        const val END_MARGIN_MS = 60L

        /** Smallest static buffer: 4096 samples, about 93 ms at 44.1 kHz. */
        const val MIN_SAMPLES = 4096
    }
}
