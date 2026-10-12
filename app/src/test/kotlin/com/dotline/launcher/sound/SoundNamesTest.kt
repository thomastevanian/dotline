package com.dotline.launcher.sound

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SoundNamesTest {
    private fun spec(name: String, category: SoundCategory) =
        SoundSpec("id_$name", name, category, listOf(Voice(Wave.SINE, 440.0, 0, 100)), 200)

    /** Sounds in the same exported folder: UI clicks are saved with the notification sounds. */
    private fun folderGroup(category: SoundCategory): String = when (category) {
        SoundCategory.RINGTONE -> "ringtones"
        SoundCategory.NOTIFICATION -> "notifications"
        SoundCategory.ALARM -> "alarms"
        SoundCategory.UI -> "notifications"
    }

    @Test
    fun noTwoLibrarySoundsShareAFileNameInTheSameFolder() {
        val seen = HashMap<String, String>()
        for (s in SoundLibrary.all) {
            val key = folderGroup(s.category) + "/" + SoundNames.fileName(s)
            val clash = seen.put(key, s.id)
            assertTrue(clash == null, "${s.id} and $clash would overwrite each other as $key")
        }
        assertEquals(32, seen.size)
    }

    @Test
    fun uiTapAndNotificationTapStayApart() {
        val ui = SoundLibrary.ui.first { it.name == "Tap" }
        val notification = SoundLibrary.notifications.first { it.name == "Tap" }
        assertEquals("Dotline UI Tap.wav", SoundNames.fileName(ui))
        assertEquals("Dotline Tap.wav", SoundNames.fileName(notification))
    }

    @Test
    fun everyNameLooksLikeAWavFileWithTheDotlinePrefix() {
        for (s in SoundLibrary.all) {
            val file = SoundNames.fileName(s)
            assertTrue(file.startsWith("Dotline "), file)
            assertTrue(file.endsWith(".wav"), file)
            assertEquals(SoundNames.title(s) + ".wav", file)
            assertTrue(file.none { it == '/' || it == '\\' || it == ':' }, file)
        }
    }

    @Test
    fun illegalCharactersAndExtraSpacesAreCleaned() {
        assertEquals("Dotline A B", SoundNames.title(spec("A/B", SoundCategory.RINGTONE)))
        assertEquals("Dotline Click Clack", SoundNames.title(spec("  Click   Clack ", SoundCategory.NOTIFICATION)))
        assertEquals("Dotline UI Why", SoundNames.title(spec("Why?", SoundCategory.UI)))
    }
}
