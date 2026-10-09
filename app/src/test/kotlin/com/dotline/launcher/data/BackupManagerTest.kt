package com.dotline.launcher.data

import com.dotline.launcher.data.model.AppItem
import com.dotline.launcher.data.model.AppKey
import com.dotline.launcher.data.model.HomeLayout
import com.dotline.launcher.data.model.Placement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.json.JSONArray

class BackupManagerTest {
    private val layout = HomeLayout(
        pages = listOf(listOf(AppItem("a", AppKey("p", "p.M", 0), Placement(1, 2)))),
        dock = listOf(AppItem("d", AppKey("q", "q.M", 0), Placement(0, 0))),
    )

    @Test
    fun roundTripKeepsEverything() {
        val presets = JSONArray("""[{"id":"x","name":"mine"}]""")
        val settings = Settings(themeMode = ThemeMode.LIGHT, gridColumns = 4, hiddenApps = setOf("p/c#0"))
        val text = BackupManager.encode(settings, layout, "buy milk", presets, "1.0.9", 1234L)
        val back = assertNotNull(BackupManager.decode(text))
        assertEquals(settings, back.settings)
        assertEquals(layout, back.layout)
        assertEquals("buy milk", back.notes)
        assertEquals("mine", back.wallpaperPresets!!.getJSONObject(0).getString("name"))
        assertEquals(1234L, back.exportedAt)
        assertEquals("1.0.9", back.appVersion)
    }

    @Test
    fun rejectsForeignAndNewerFiles() {
        assertNull(BackupManager.decode("{}"))
        assertNull(BackupManager.decode("not json"))
        assertNull(BackupManager.decode("""{"format":"other","version":1,"settings":{}}"""))
        assertNull(BackupManager.decode("""{"format":"dotline-backup","version":99,"settings":{}}"""))
        assertNull(BackupManager.decode("""{"format":"dotline-backup","version":1}"""))
    }

    @Test
    fun missingOptionalSectionsAreNull() {
        val back = assertNotNull(BackupManager.decode("""{"format":"dotline-backup","version":1,"settings":{"gridColumns":6}}"""))
        assertEquals(6, back.settings.gridColumns)
        assertNull(back.layout)
        assertNull(back.notes)
        assertNull(back.wallpaperPresets)
    }
}
