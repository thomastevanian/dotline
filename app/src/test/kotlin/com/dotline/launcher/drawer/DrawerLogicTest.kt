package com.dotline.launcher.drawer

import com.dotline.launcher.data.model.AppInfo
import com.dotline.launcher.data.model.AppKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DrawerLogicTest {
    private fun app(label: String, pkg: String = "p." + label.lowercase().filter { it.isLetterOrDigit() }) =
        AppInfo(AppKey(pkg, "$pkg.M", 0L), label, false, false, 1L, 0L)

    private val apps = listOf("1Password", "Calendar", "Camera", "Chrome", "Clock", "Éclair", "Gmail", "Maps", "YouTube", "YouTube Music", "Zoom")
        .map { app(it) }

    @Test
    fun normalizeStripsAccentsAndCase() {
        assertEquals("cafe", DrawerLogic.normalize("  Café "))
        assertEquals("eclair", DrawerLogic.normalize("Éclair"))
    }

    @Test
    fun sectionLetters() {
        assertEquals('C', DrawerLogic.sectionLetter("Camera"))
        assertEquals('E', DrawerLogic.sectionLetter("Éclair"))
        assertEquals('#', DrawerLogic.sectionLetter("1Password"))
        assertEquals('#', DrawerLogic.sectionLetter("日本語"))
        assertEquals('#', DrawerLogic.sectionLetter("   "))
    }

    @Test
    fun searchRanksPrefixThenWordThenInitialsThenContains() {
        assertEquals(apps, DrawerLogic.search(apps, "  "))
        assertEquals(listOf("Camera", "Calendar").sorted(), DrawerLogic.search(apps, "ca").map { it.label }.sorted())
        assertEquals(listOf("YouTube Music"), DrawerLogic.search(apps, "music").map { it.label })
        assertEquals("YouTube Music", DrawerLogic.search(apps, "ym").first().label)
        val you = DrawerLogic.search(apps, "you").map { it.label }
        assertEquals(listOf("YouTube", "YouTube Music"), you)
        assertTrue(DrawerLogic.search(apps, "ube").map { it.label }.containsAll(listOf("YouTube", "YouTube Music")))
        assertEquals(listOf("Éclair"), DrawerLogic.search(apps, "eclair").map { it.label })
        assertTrue(DrawerLogic.search(apps, "qqq").isEmpty())
    }

    @Test
    fun searchMatchesPackageNameOnlyForLongerQueries() {
        val a = app("Notes", "com.samsung.android.app.notes")
        assertTrue(DrawerLogic.search(listOf(a), "samsung").isNotEmpty())
        assertTrue(DrawerLogic.search(listOf(a), "sa").isEmpty(), "short queries do not hit package names")
    }

    @Test
    fun hiddenAppsAreFiltered() {
        val hiddenByKey = DrawerLogic.visible(apps, setOf(apps[1].key.flat))
        assertEquals(apps.size - 1, hiddenByKey.size)
        val hiddenByPkg = DrawerLogic.visible(apps, setOf("p.maps"))
        assertTrue(hiddenByPkg.none { it.label == "Maps" })
        assertEquals(apps, DrawerLogic.visible(apps, emptySet()))
    }

    @Test
    fun sectionsAndScrollerJump() {
        val sorted = apps
        val sections = DrawerLogic.sections(sorted)
        assertEquals(listOf('#', 'C', 'E', 'G', 'M', 'Y', 'Z'), sections.map { it.letter })
        assertEquals(1, sections.first { it.letter == 'C' }.startIndex)
        assertEquals(sections.first { it.letter == 'G' }.startIndex, DrawerLogic.indexForLetter(sections, 'G'))
        assertEquals(sections.first { it.letter == 'M' }.startIndex, DrawerLogic.indexForLetter(sections, 'K'), "missing letter jumps to the next one")
        assertEquals(sections.last().startIndex, DrawerLogic.indexForLetter(sections, 'Z'))
        assertEquals(0, DrawerLogic.indexForLetter(emptyList(), 'A'))
        assertEquals(sections.first { it.letter == 'Z' }.startIndex, DrawerLogic.indexForLetter(sections, 'Z'))
    }

    @Test
    fun scrollerLettersAndTouchMapping() {
        val withOther = DrawerLogic.scrollerLetters(DrawerLogic.sections(apps))
        assertEquals(27, withOther.size)
        assertEquals('#', withOther.last())
        val plain = DrawerLogic.scrollerLetters(DrawerLogic.sections(listOf(app("Alpha"))))
        assertEquals(26, plain.size)
        assertEquals('A', DrawerLogic.letterAt(0f, 2600f, plain))
        assertEquals('Z', DrawerLogic.letterAt(2599f, 2600f, plain))
        assertEquals('A', DrawerLogic.letterAt(-50f, 2600f, plain))
        assertEquals('N', DrawerLogic.letterAt(1350f, 2600f, plain))
    }

    @Test
    fun recentsPromoteDedupeAndCap() {
        val keys = (1..10).map { AppKey("p$it", "p$it.M", 0L) }
        var list = emptyList<AppKey>()
        for (k in keys) list = RecentAppsRepository.promote(list, k)
        assertEquals(RecentAppsRepository.MAX, list.size)
        assertEquals(keys.last(), list.first())
        list = RecentAppsRepository.promote(list, keys[5])
        assertEquals(keys[5], list.first())
        assertEquals(list.size, list.toSet().size)
    }
}
