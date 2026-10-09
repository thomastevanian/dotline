package com.dotline.launcher.sound

import kotlin.math.pow

/** Note names -> Hz (A4 = 440). Accepts C4, F#5, Bb3. */
object Notes {
    private val semis = mapOf('C' to 0, 'D' to 2, 'E' to 4, 'F' to 5, 'G' to 7, 'A' to 9, 'B' to 11)

    fun hz(name: String): Double {
        val letter = name[0].uppercaseChar()
        var i = 1
        var s = semis.getValue(letter)
        if (i < name.length && name[i] == '#') { s += 1; i++ } else if (i < name.length && name[i] == 'b') { s -= 1; i++ }
        val octave = name.substring(i).toInt()
        val midi = 12 * (octave + 1) + s
        return 440.0 * 2.0.pow((midi - 69) / 12.0)
    }
}

/**
 * Original sounds in a minimal, clean, sparse style: sine and square pulses, short rhythmic
 * patterns, plenty of silence. Nothing here imitates any existing tone.
 */
object SoundLibrary {
    /**
     * Step sequencer: tokens are note names, "." for a rest, "_" to hold the previous note one more step.
     * [hold] is the sounding fraction of a step; [releaseMs] fades each note out.
     */
    private fun seq(
        steps: String, stepMs: Int, wave: Wave, startMs: Int = 0, gain: Double = 0.7, hold: Double = 0.7,
        releaseMs: Int = 50, duty: Double = 0.5, attackMs: Int = 3,
    ): List<Voice> {
        val out = ArrayList<Voice>()
        var pos = 0
        var last: Int = -1
        for (token in steps.trim().split(Regex("\\s+"))) {
            when (token) {
                "." -> {}
                "_" -> if (last >= 0) {
                    val v = out[last]
                    out[last] = v.copy(durMs = v.durMs + stepMs)
                }
                else -> {
                    out += Voice(wave, Notes.hz(token), startMs + pos * stepMs, (stepMs * hold).toInt(), gain, attackMs, releaseMs, duty = duty)
                    last = out.lastIndex
                }
            }
            pos++
        }
        return out
    }

    private fun repeat(voices: List<Voice>, times: Int, everyMs: Int): List<Voice> =
        (0 until times).flatMap { n -> voices.map { it.copy(startMs = it.startMs + n * everyMs) } }

    private fun make(id: String, name: String, cat: SoundCategory, voices: List<Voice>, tailMs: Int = 120): SoundSpec {
        val end = voices.maxOf { it.startMs + it.durMs }
        return SoundSpec(id, name, cat, voices, end + tailMs)
    }

    private fun tone(wave: Wave, note: String, start: Int, dur: Int, gain: Double = 0.7, release: Int = dur, glideTo: String? = null, attack: Int = 3, duty: Double = 0.5) =
        Voice(wave, Notes.hz(note), start, dur, gain, attack, release, glideTo?.let { Notes.hz(it) }, duty)

    val ringtones: List<SoundSpec> = listOf(
        make("rt_pulse", "Pulse", SoundCategory.RINGTONE,
            repeat(seq("A3 . A3 . A3 A3 . .", 170, Wave.SQUARE, gain = 0.5, duty = 0.35), 3, 8 * 170) +
                repeat(seq("E5 . . . . . . .", 170, Wave.SINE, gain = 0.5, hold = 1.6, releaseMs = 500), 3, 8 * 170), 400),
        make("rt_grid", "Grid", SoundCategory.RINGTONE,
            repeat(seq("C5 E5 G5 A5 G5 E5 C5 .", 140, Wave.TRIANGLE, gain = 0.65), 4, 8 * 140), 300),
        make("rt_orbit", "Orbit", SoundCategory.RINGTONE,
            listOf(
                tone(Wave.SINE, "F4", 0, 700, 0.6, 400, "C5"), tone(Wave.SINE, "C5", 900, 700, 0.55, 400, "F5"),
                tone(Wave.SINE, "F5", 1800, 700, 0.5, 500, "A4"), tone(Wave.SINE, "A4", 2700, 900, 0.5, 700, "F4"),
                tone(Wave.SINE, "C6", 3800, 600, 0.3, 600), tone(Wave.SINE, "F6", 4300, 800, 0.2, 800),
            ), 400),
        make("rt_signal", "Signal", SoundCategory.RINGTONE,
            repeat(seq("A5 . A5 . A5 _ _ . . . . .", 110, Wave.SQUARE, gain = 0.45, hold = 0.85, duty = 0.3, releaseMs = 20), 3, 12 * 110), 300),
        make("rt_static", "Static", SoundCategory.RINGTONE,
            repeat(seq("D4 A4 D4 A4 . . D4 A4 D4 A4 . . . .", 70, Wave.SQUARE, gain = 0.4, hold = 0.8, duty = 0.25, releaseMs = 15), 3, 14 * 70 + 700), 300),
        make("rt_matrix", "Matrix", SoundCategory.RINGTONE,
            repeat(seq("E6 B5 G5 E5 B4 G4 E4 .", 150, Wave.SINE, gain = 0.6, hold = 0.9, releaseMs = 120), 3, 9 * 150), 500),
        make("rt_ticktock", "Tick Tock", SoundCategory.RINGTONE,
            repeat(seq("C4 . G4 . C4 . G4 . . . . .", 260, Wave.TRIANGLE, gain = 0.7, hold = 0.45, releaseMs = 200), 2, 12 * 260), 400),
        make("rt_halo", "Halo", SoundCategory.RINGTONE,
            listOf(
                tone(Wave.SINE, "C4", 0, 3200, 0.35, 1400, attack = 300), tone(Wave.SINE, "G4", 500, 3000, 0.3, 1400, attack = 300),
                tone(Wave.SINE, "E5", 1000, 2800, 0.25, 1400, attack = 300), tone(Wave.SINE, "C6", 2600, 1600, 0.12, 1600, attack = 100),
                tone(Wave.SINE, "G5", 3600, 1400, 0.12, 1400, attack = 100),
            ), 600),
    )

    val notifications: List<SoundSpec> = listOf(
        make("nt_dot", "Dot", SoundCategory.NOTIFICATION, listOf(tone(Wave.SINE, "E6", 0, 220, 0.7, 200))),
        make("nt_dash", "Dash", SoundCategory.NOTIFICATION, listOf(tone(Wave.SINE, "A5", 0, 90, 0.6, 40), tone(Wave.SINE, "A5", 150, 260, 0.6, 220))),
        make("nt_drop", "Drop", SoundCategory.NOTIFICATION, listOf(tone(Wave.SINE, "G6", 0, 340, 0.65, 300, glideTo = "C5"))),
        make("nt_blip", "Blip", SoundCategory.NOTIFICATION, listOf(tone(Wave.SQUARE, "A5", 0, 70, 0.4, 25, duty = 0.3))),
        make("nt_chime", "Chime", SoundCategory.NOTIFICATION, seq("C6 E6 G6", 120, Wave.SINE, gain = 0.55, hold = 1.9, releaseMs = 220)),
        make("nt_ping", "Ping", SoundCategory.NOTIFICATION, listOf(tone(Wave.SINE, "B5", 0, 700, 0.7, 700), tone(Wave.SINE, "B6", 0, 300, 0.18, 300))),
        make("nt_tap", "Tap", SoundCategory.NOTIFICATION, listOf(tone(Wave.TRIANGLE, "C5", 0, 120, 0.8, 110))),
        make("nt_pop", "Pop", SoundCategory.NOTIFICATION, listOf(tone(Wave.SINE, "C5", 0, 110, 0.7, 90, glideTo = "G5"))),
        make("nt_clickclack", "Click Clack", SoundCategory.NOTIFICATION, listOf(tone(Wave.SQUARE, "E6", 0, 35, 0.35, 20, duty = 0.2), tone(Wave.SQUARE, "A5", 120, 50, 0.35, 30, duty = 0.2))),
        make("nt_rise", "Rise", SoundCategory.NOTIFICATION, seq("E5 G5 B5", 110, Wave.TRIANGLE, gain = 0.65, hold = 1.3, releaseMs = 120)),
        make("nt_fall", "Fall", SoundCategory.NOTIFICATION, seq("B5 G5 E5", 110, Wave.TRIANGLE, gain = 0.65, hold = 1.3, releaseMs = 120)),
        make("nt_twin", "Twin", SoundCategory.NOTIFICATION, listOf(tone(Wave.SINE, "D6", 0, 500, 0.45, 500), tone(Wave.SINE, "A6", 0, 500, 0.35, 500), tone(Wave.SINE, "D6", 220, 500, 0.35, 500))),
    )

    val alarms: List<SoundSpec> = listOf(
        make("al_wake", "Wake", SoundCategory.ALARM,
            repeat(seq("C5 E5 G5 . . .", 130, Wave.SQUARE, gain = 0.5, hold = 0.8, duty = 0.4, releaseMs = 30), 4, 6 * 130 + 380), 300),
        make("al_rise", "Rise", SoundCategory.ALARM,
            (0 until 8).map { tone(Wave.SINE, listOf("C5", "D5", "E5", "G5", "A5", "C6", "D6", "E6")[it], it * 500, 380, 0.45 + it * 0.05, 300) }, 400),
        make("al_beacon", "Beacon", SoundCategory.ALARM,
            repeat(listOf(tone(Wave.SQUARE, "B5", 0, 200, 0.45, 20, duty = 0.3)), 8, 400), 300),
        make("al_sweep", "Sweep", SoundCategory.ALARM,
            repeat(listOf(tone(Wave.SINE, "G4", 0, 900, 0.6, 200, glideTo = "G6", attack = 20)), 4, 1100), 400),
        make("al_countdown", "Countdown", SoundCategory.ALARM,
            repeat(seq("A5 . A5 . A5 . A5 . E6 _ _ . . .", 150, Wave.SINE, gain = 0.6, hold = 0.7, releaseMs = 60), 2, 14 * 150), 400),
        make("al_twotone", "Two Tone", SoundCategory.ALARM,
            repeat(listOf(tone(Wave.TRIANGLE, "A5", 0, 300, 0.6, 40), tone(Wave.TRIANGLE, "E5", 350, 300, 0.6, 40)), 5, 800), 300),
    )

    val ui: List<SoundSpec> = listOf(
        make("ui_click", "Click", SoundCategory.UI, listOf(tone(Wave.SQUARE, "B6", 0, 14, 0.35, 10, duty = 0.25, attack = 1)), 20),
        make("ui_tick", "Tick", SoundCategory.UI, listOf(tone(Wave.SINE, "F#7", 0, 10, 0.5, 8, attack = 1)), 20),
        make("ui_tap", "Tap", SoundCategory.UI, listOf(tone(Wave.TRIANGLE, "G5", 0, 28, 0.7, 26, attack = 1)), 20),
        make("ui_knock", "Knock", SoundCategory.UI, listOf(tone(Wave.SINE, "F#3", 0, 45, 0.9, 45, attack = 1)), 20),
        make("ui_snap", "Snap", SoundCategory.UI, listOf(tone(Wave.SQUARE, "D6", 0, 22, 0.35, 20, glideTo = "D5", duty = 0.2, attack = 1)), 20),
        make("ui_pip", "Pip", SoundCategory.UI, listOf(tone(Wave.SINE, "G6", 0, 34, 0.6, 30, attack = 1)), 20),
    )

    val all: List<SoundSpec> = ringtones + notifications + alarms + ui

    fun byCategory(c: SoundCategory): List<SoundSpec> = all.filter { it.category == c }
}
