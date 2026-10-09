package com.dotline.launcher.data

import com.dotline.launcher.data.model.AppItem
import com.dotline.launcher.data.model.AppKey
import com.dotline.launcher.data.model.BuiltinWidget
import com.dotline.launcher.data.model.FolderItem
import com.dotline.launcher.data.model.HomeLayout
import com.dotline.launcher.data.model.Placement
import com.dotline.launcher.data.model.WidgetItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LayoutEngineTest {
    private fun key(n: String) = AppKey("com.test.$n", "com.test.$n.Main", 0L)
    private fun app(n: String, c: Int, r: Int) = AppItem("id_$n", key(n), Placement(c, r))
    private val cols = 5
    private val rows = 6

    @Test
    fun appKeyRoundTrip() {
        val k = AppKey("a.b", "a.b.C\$Inner", 10L)
        assertEquals(k, AppKey.parse(k.flat))
        assertNull(AppKey.parse("garbage"))
    }

    @Test
    fun findFreeSkipsOccupiedAndSpans() {
        val items = listOf(app("a", 0, 0), app("b", 1, 0))
        assertEquals(Placement(2, 0), LayoutEngine.findFree(items, cols, rows))
        val wide = LayoutEngine.findFree(items, cols, rows, spanX = 4, spanY = 2)
        assertEquals(Placement(0, 1, 4, 2), wide)
        assertNull(LayoutEngine.findFree(items, cols, rows, spanX = 6))
    }

    @Test
    fun addToFirstFreeAddsPageWhenFull() {
        val full = (0 until cols * rows).map { app("x$it", it % cols, it / cols) }
        val layout = HomeLayout(listOf(full))
        val out = assertNotNull(LayoutEngine.addToFirstFree(layout, app("new", 0, 0), cols, rows))
        assertEquals(2, out.pages.size)
        assertEquals(Placement(0, 0), out.pages[1].single().placement)
    }

    @Test
    fun addToFirstFreeRefusesBeyondMaxPages() {
        val full = (0 until cols * rows).map { app("x$it", it % cols, it / cols) }
        val layout = HomeLayout(List(HomeLayout.MAX_PAGES) { full })
        assertNull(LayoutEngine.addToFirstFree(layout, app("new", 0, 0), cols, rows))
    }

    @Test
    fun moveToPageBlockedAndFree() {
        val layout = HomeLayout(listOf(listOf(app("a", 0, 0), app("b", 1, 0)), emptyList()))
        assertNull(LayoutEngine.moveToPage(layout, "id_a", 0, Placement(1, 0), cols, rows))
        val moved = assertNotNull(LayoutEngine.moveToPage(layout, "id_a", 1, Placement(3, 2), cols, rows))
        assertEquals(1, moved.pages[0].size)
        assertEquals(Placement(3, 2), moved.pages[1].single().placement)
        assertNull(LayoutEngine.moveToPage(layout, "id_a", 0, Placement(5, 0), cols, rows))
    }

    @Test
    fun movingOntoItsOwnCellIsAllowed() {
        val layout = HomeLayout(listOf(listOf(app("a", 2, 2))))
        val out = assertNotNull(LayoutEngine.moveToPage(layout, "id_a", 0, Placement(2, 2), cols, rows))
        assertEquals(Placement(2, 2), out.pages[0].single().placement)
    }

    @Test
    fun dockRules() {
        val widget = WidgetItem("w", BuiltinWidget.CLOCK, Placement(0, 0, 4, 2))
        val layout = HomeLayout(listOf(listOf(app("a", 0, 2), app("b", 1, 2), widget)))
        val docked = assertNotNull(LayoutEngine.moveToDock(layout, "id_a", 1))
        assertEquals(1, docked.dock.single().placement.col)
        assertEquals(LayoutEngine.DOCK, LayoutEngine.locate(docked, "id_a"))
        assertNull(LayoutEngine.moveToDock(docked, "id_b", 1))
        assertNull(LayoutEngine.moveToDock(layout, "w", 0))
        assertNull(LayoutEngine.moveToDock(layout, "id_a", 4))
        val back = assertNotNull(LayoutEngine.moveToPage(docked, "id_a", 0, Placement(0, 2), cols, rows))
        assertTrue(back.dock.isEmpty())
    }

    @Test
    fun createFolderAndAddAndRemove() {
        val layout = HomeLayout(listOf(listOf(app("a", 0, 0), app("b", 1, 0), app("c", 2, 0))))
        val f = assertNotNull(LayoutEngine.createFolder(layout, "id_b", "id_a", "Social", "f1"))
        val folder = f.pages[0].filterIsInstance<FolderItem>().single()
        assertEquals(listOf(key("a"), key("b")), folder.apps)
        assertEquals(Placement(0, 0), folder.placement)
        assertEquals(2, f.pages[0].size)

        val g = assertNotNull(LayoutEngine.addToFolder(f, "id_c", "f1"))
        assertEquals(3, (LayoutEngine.findItem(g, "f1") as FolderItem).apps.size)
        assertEquals(1, g.pages[0].size)

        val h = assertNotNull(LayoutEngine.removeFromFolder(g, "f1", key("c"), 0, cols, rows, "id_c2"))
        assertEquals(2, (LayoutEngine.findItem(h, "f1") as FolderItem).apps.size)
        assertNotNull(LayoutEngine.findItem(h, "id_c2"))

        val i = assertNotNull(LayoutEngine.removeFromFolder(h, "f1", key("b"), 0, cols, rows, "id_b2"))
        assertTrue(LayoutEngine.findItem(i, "f1") is AppItem, "folder with one app dissolves into the app")
    }

    @Test
    fun folderRejectsWidgetsAndSelfDrop() {
        val widget = WidgetItem("w", BuiltinWidget.DATE, Placement(0, 0, 2, 1))
        val layout = HomeLayout(listOf(listOf(widget, app("a", 0, 1))))
        assertNull(LayoutEngine.createFolder(layout, "id_a", "w", "x", "f"))
        assertNull(LayoutEngine.createFolder(layout, "id_a", "id_a", "x", "f"))
    }

    @Test
    fun removeAppCleansFoldersAndDock() {
        val folder = FolderItem("f", "F", listOf(key("a"), key("b")), Placement(0, 0))
        val layout = HomeLayout(listOf(listOf(folder, app("c", 1, 0))), dock = listOf(AppItem("d", key("a"), Placement(0, 0))))
        val out = LayoutEngine.removeApp(layout, key("a"))
        assertTrue(out.dock.isEmpty())
        assertTrue(out.pages[0].first() is AppItem, "folder left with one app becomes that app")
        val none = LayoutEngine.removeApp(out, key("b"))
        assertEquals(1, none.pages[0].size)
    }

    @Test
    fun normalizeResolvesOverlapOutOfGridAndTrims() {
        val a = app("a", 0, 0)
        val b = app("b", 0, 0)
        val c = app("c", 9, 9)
        val layout = HomeLayout(listOf(listOf(a, b, c), emptyList(), emptyList()))
        val out = LayoutEngine.normalize(layout, cols, rows)
        assertEquals(1, out.pages.size, "empty trailing pages trimmed")
        assertEquals(3, out.pages[0].size)
        val spots = out.pages[0].map { it.placement }.toSet()
        assertEquals(3, spots.size, "no two items share a cell")
        assertTrue(spots.all { LayoutEngine.fits(it, cols, rows) })
    }

    @Test
    fun normalizeShrinkingGridMovesOverflowToNewPage() {
        val items = (0 until 30).map { app("x$it", it % 5, it / 5) }
        val out = LayoutEngine.normalize(HomeLayout(listOf(items)), 4, 5)
        val total = out.pages.sumOf { it.size }
        assertEquals(30, total)
        assertTrue(out.pages.size >= 2)
        out.pages.forEach { page -> page.forEach { assertTrue(LayoutEngine.fits(it.placement, 4, 5)) } }
    }

    @Test
    fun normalizeFixesDockSlots() {
        val dock = listOf(
            AppItem("d1", key("a"), Placement(0, 0)),
            AppItem("d2", key("b"), Placement(0, 0)),
            AppItem("d3", key("c"), Placement(9, 0)),
        )
        val out = LayoutEngine.normalize(HomeLayout(listOf(emptyList()), dock), cols, rows)
        assertEquals(3, out.dock.size)
        assertEquals(3, out.dock.map { it.placement.col }.toSet().size)
    }

    @Test
    fun jsonRoundTripAndTolerance() {
        val layout = HomeLayout(
            pages = listOf(
                listOf(
                    app("a", 0, 0),
                    FolderItem("f", "My \"folder\"", listOf(key("b"), key("c")), Placement(1, 0)),
                    WidgetItem("w", BuiltinWidget.WEATHER, Placement(0, 2, 4, 2), "cfg"),
                ),
                emptyList(),
            ),
            dock = listOf(AppItem("d", key("z"), Placement(2, 0))),
        )
        val text = LayoutJson.encode(layout).toString()
        assertEquals(layout, LayoutJson.decode(text))

        val broken = """{"pages":[[{"t":"app","id":"x","app":"nope"},{"t":"widget","id":"y","kind":"NOT_A_KIND"},{"t":"app","id":"ok","app":"p/c#1","c":2,"r":3}]],"dock":"oops"}"""
        val decoded = LayoutJson.decode(broken)
        assertEquals(1, decoded.pages[0].size)
        assertEquals(Placement(2, 3), decoded.pages[0][0].placement)
        assertTrue(decoded.dock.isEmpty())
    }

    @Test
    fun settingsJsonRoundTripAndClamping() {
        val s = Settings(gridColumns = 6, gridRows = 7, iconSize = 1.2f, hiddenApps = setOf("p/c#0"), themeMode = ThemeMode.LIGHT, weatherLat = 51.5, weatherLon = -0.12)
        assertEquals(s, SettingsJson.decode(SettingsJson.encode(s).toString()))
        val wild = SettingsJson.decode("""{"gridColumns":99,"iconSize":9,"themeMode":"BOGUS","swipeUp":"EDIT_MODE"}""")
        assertEquals(6, wild.gridColumns)
        assertEquals(1.3f, wild.iconSize)
        assertEquals(ThemeMode.DARK, wild.themeMode)
        assertEquals(GestureAction.EDIT_MODE, wild.swipeUp)
        assertTrue(wild.weatherLat.isNaN())
    }
}
