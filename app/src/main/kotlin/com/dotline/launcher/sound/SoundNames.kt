package com.dotline.launcher.sound

/**
 * Names of exported sound files. Pure Kotlin (no Android classes) so the rules can be unit tested.
 *
 * The exporter overwrites an existing file with the same name instead of creating a duplicate, so two
 * different sounds that share a folder must never share a file name. UI clicks are saved into the
 * Notifications folder and some of their names (Tap) also exist as notification sounds, which is why
 * UI sounds carry a "UI" marker.
 */
object SoundNames {
    private val illegalCharacters = Regex("[\\\\/:*?\"<>|]")
    private val whitespace = Regex("\\s+")

    /** Title shown in the system sound pickers, for example "Dotline Pulse" or "Dotline UI Tap". */
    fun title(spec: SoundSpec): String {
        val clean = spec.name.replace(illegalCharacters, " ").replace(whitespace, " ").trim()
        val prefix = if (spec.category == SoundCategory.UI) "Dotline UI " else "Dotline "
        return (prefix + clean).trim()
    }

    /** File name with extension, for example "Dotline Pulse.wav". */
    fun fileName(spec: SoundSpec): String = title(spec) + ".wav"
}
