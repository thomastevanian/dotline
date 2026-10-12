package com.dotline.launcher.home

import com.dotline.launcher.data.model.AppItem
import com.dotline.launcher.data.model.AppKey
import com.dotline.launcher.data.model.BuiltinWidget
import com.dotline.launcher.data.model.FolderItem
import com.dotline.launcher.data.model.HomeLayout
import com.dotline.launcher.data.model.Placement
import com.dotline.launcher.data.model.WidgetItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeLayoutMathTest {
    private fun key(n: String) = AppKey("p.$n", "p.$n.M", 0L)

    // ---- grid ----

    @Test
    fun cellHeightIsCappedAndGridIsAnchoredToTheTop() {
        // 393 px wide page area, 13 px margins. Plenty of height: the cell height is capped at 112.
        val m = HomeLayoutMath.gridMetrics(393f, 900f, cols = 5, rows = 6, marginPx = 13f, maxCellHeightPx = 112f)
        assertEquals(73.4f, m.cellW, 0.001f)
        assertEquals(112f, m.cellH, 0.001f)
        assertEquals(13f, m.left)
        assertEquals(672f, m.height, 0.001f)
        val rect = HomeLayoutMath.gridRect(FRect(0f, 100f, 393f, 1000f), m)
        assertEquals(FRect(13f, 100f, 380f, 772f), rect)
    }

    @Test
    fun shortAreasShrinkTheCells() {
        val m = HomeLayoutMath.gridMetrics(393f, 450f, cols = 5, rows = 6, marginPx = 13f, maxCellHeightPx = 112f)
        assertEquals(75f, m.cellH, 0.001f)
        assertEquals(450f, m.height, 0.001f)
    }

    @Test
    fun geometryBuiltFromTheGridRectHasTheSameCells() {
        val m = HomeLayoutMath.gridMetrics(1080f, 2000f, cols = 5, rows = 6, marginPx = 34f, maxCellHeightPx = 294f)
        val area = FRect(0f, 120f, 1080f, 2120f)
        val geo = HomeGeometry(
            screen = FRect(0f, 0f, 1080f, 2400f),
            grid = HomeLayoutMath.gridRect(area, m),
            cols = 5, rows = 6, dock = null,
        )
        assertEquals(m.cellW, geo.cellW, 0.001f)
        assertEquals(m.cellH, geo.cellH, 0.001f)
    }

    @Test
    fun edgesAreSeamless() {
        val m = HomeLayoutMath.gridMetrics(1080f, 2000f, cols = 5, rows = 6, marginPx = 34f, maxCellHeightPx = 294f)
        // Neighbouring cells differ by at most one pixel in width and together cover the grid exactly.
        var total = 0
        for (c in 0..4) {
            val w = m.xEdge(c + 1) - m.xEdge(c)
            assertTrue(w == 202 || w == 203, "column $c is $w px")
            total += w
        }
        assertEquals(1012, total)
        assertEquals(34, m.xEdge(0))
        assertEquals(1046, m.xEdge(5))
        assertEquals(0, m.yEdge(0))
        assertEquals(294, m.yEdge(1))
    }

    @Test
    fun tileIsCentredWithItsLabelBlock() {
        // cell 100 high, tile 50 + label 20 => block 70, 15 px above and below
        assertEquals(15f + 25f, HomeLayoutMath.tileCenterY(0f, 100f, 50f, 20f), 0.001f)
        assertEquals(50f, HomeLayoutMath.tileCenterY(0f, 100f, 50f, 0f), 0.001f)
    }

    // ---- paging ----

    @Test
    fun infiniteOnlyWithMoreThanOnePage() {
        assertFalse(HomeLayoutMath.isInfinite(true, 1))
        assertTrue(HomeLayoutMath.isInfinite(true, 2))
        assertFalse(HomeLayoutMath.isInfinite(false, 4))
    }

    @Test
    fun pagerCountAndInitialPage() {
        assertEquals(3, HomeLayoutMath.pagerCount(3, false))
        assertEquals(HomeLayoutMath.VIRTUAL_PAGES, HomeLayoutMath.pagerCount(3, true))
        assertEquals(1, HomeLayoutMath.pagerCount(0, false))
        assertEquals(0, HomeLayoutMath.initialPage(3, false))
        val start = HomeLayoutMath.initialPage(3, true)
        assertEquals(0, start % 3)
        assertEquals(5000 - 5000 % 3, start)
        assertEquals(2, HomeLayoutMath.logicalPage(HomeLayoutMath.initialPage(3, true, 2), 3, true))
    }

    @Test
    fun logicalPageMapsAndClamps() {
        assertEquals(1, HomeLayoutMath.logicalPage(4, 3, true))
        assertEquals(2, HomeLayoutMath.logicalPage(5, 3, false))
        assertEquals(2, HomeLayoutMath.logicalPage(9, 3, false))
        assertEquals(0, HomeLayoutMath.logicalPage(0, 0, false))
    }

    @Test
    fun nearestPagerPagePicksTheClosestCopy() {
        // not infinite: just the logical page
        assertEquals(0, HomeLayoutMath.nearestPagerPage(2, 0, 3, false))
        // infinite, 3 pages, currently on virtual page 5001 (logical 0 when 5001 % 3 == 0? 5001 = 3*1667)
        val current = 5002 // logical 1
        assertEquals(1, HomeLayoutMath.logicalPage(current, 3, true))
        val toZero = HomeLayoutMath.nearestPagerPage(current, 0, 3, true)
        assertEquals(0, HomeLayoutMath.logicalPage(toZero, 3, true))
        assertEquals(1, kotlin.math.abs(toZero - current))
        val toTwo = HomeLayoutMath.nearestPagerPage(current, 2, 3, true)
        assertEquals(2, HomeLayoutMath.logicalPage(toTwo, 3, true))
        assertEquals(1, kotlin.math.abs(toTwo - current))
        // staying put
        assertEquals(current, HomeLayoutMath.nearestPagerPage(current, 1, 3, true))
    }

    @Test
    fun pageForNewItemUsesFirstPageWithRoom() {
        val full = (0 until 4).flatMap { r -> (0 until 4).map { c -> AppItem("a$r$c", key("a$r$c"), Placement(c, r)) } }
        val layout = HomeLayout(pages = listOf(full, emptyList()))
        assertEquals(1, HomeLayoutMath.pageForNewItem(layout, 4, 4, 1, 1))
        val allFull = HomeLayout(pages = listOf(full))
        assertEquals(1, HomeLayoutMath.pageForNewItem(allFull, 4, 4, 1, 1))
        val maxed = HomeLayout(pages = List(HomeLayout.MAX_PAGES) { full })
        assertEquals(-1, HomeLayoutMath.pageForNewItem(maxed, 4, 4, 1, 1))
        // a 4x2 widget does not fit a page with only one free row
        val rowFree = (0 until 3).flatMap { r -> (0 until 4).map { c -> AppItem("b$r$c", key("b$r$c"), Placement(c, r)) } }
        assertEquals(1, HomeLayoutMath.pageForNewItem(HomeLayout(pages = listOf(rowFree, emptyList())), 4, 4, 4, 2))
    }

    // ---- drop zones ----

    private val screen = FRect(0f, 0f, 1000f, 2000f)

    @Test
    fun noZonesForNone() {
        val z = HomeLayoutMath.dropZones(ZoneSet.NONE, screen, 80f, 40f, 20f, 100f, 20f, 40f)
        assertNull(z.removePill)
        assertNull(z.uninstallHit)
        assertNull(z.infoHit)
    }

    @Test
    fun allZonesSplitTheWidthEvenlyAndMeetWithoutGaps() {
        val z = HomeLayoutMath.dropZones(ZoneSet.ALL, screen, 80f, 40f, 20f, 100f, 20f, 40f)
        val r = z.removePill!!
        val u = z.uninstallPill!!
        val i = z.infoPill!!
        // (1000 - 80 - 40) / 3 = 293.33 each
        assertEquals(r.width, u.width, 0.01f)
        assertEquals(u.width, i.width, 0.01f)
        assertEquals(40f, r.left, 0.01f)
        assertEquals(960f, i.right, 0.01f)
        assertEquals(100f, r.top, 0.01f)
        assertEquals(200f, r.bottom, 0.01f)
        // hit areas tile the top strip: right edge of one is the left edge of the next
        assertEquals(z.removeHit!!.right, z.uninstallHit!!.left, 0.01f)
        assertEquals(z.uninstallHit!!.right, z.infoHit!!.left, 0.01f)
        assertEquals(0f, z.removeHit!!.left)
        assertEquals(1000f, z.infoHit!!.right)
        assertEquals(0f, z.removeHit!!.top)
        assertEquals(240f, z.removeHit!!.bottom)
    }

    @Test
    fun appOnlyHasTwoZonesAndNoRemove() {
        val z = HomeLayoutMath.dropZones(ZoneSet.APP_ONLY, screen, 80f, 40f, 20f, 100f, 20f, 40f)
        assertNull(z.removePill)
        assertNotNull(z.uninstallPill)
        assertNotNull(z.infoPill)
        assertTrue(z.uninstallPill!!.right < z.infoPill!!.left)
    }

    @Test
    fun removeOnlyHasOneWideZone() {
        val z = HomeLayoutMath.dropZones(ZoneSet.REMOVE_ONLY, screen, 80f, 40f, 20f, 100f, 20f, 40f)
        assertEquals(920f, z.removePill!!.width, 0.01f)
        assertNull(z.uninstallPill)
    }

    @Test
    fun zoneSetDependsOnWhatIsDragged() {
        val app = AppItem("a", key("a"), Placement(0, 0))
        val folder = FolderItem("f", "F", listOf(key("x"), key("y")), Placement(1, 0))
        val widget = WidgetItem("w", BuiltinWidget.CLOCK, Placement(0, 1, 4, 2))
        val layout = HomeLayout(pages = listOf(listOf(app, folder, widget)))
        assertEquals(ZoneSet.ALL, HomeLayoutMath.zoneSetFor(DragSource.OnHome("a"), layout))
        assertEquals(ZoneSet.REMOVE_ONLY, HomeLayoutMath.zoneSetFor(DragSource.OnHome("f"), layout))
        assertEquals(ZoneSet.REMOVE_ONLY, HomeLayoutMath.zoneSetFor(DragSource.OnHome("w"), layout))
        assertEquals(ZoneSet.NONE, HomeLayoutMath.zoneSetFor(DragSource.OnHome("missing"), layout))
        assertEquals(ZoneSet.ALL, HomeLayoutMath.zoneSetFor(DragSource.FromFolder("f", key("x")), layout))
        assertEquals(ZoneSet.APP_ONLY, HomeLayoutMath.zoneSetFor(DragSource.NewApp(key("n")), layout))
        assertEquals(ZoneSet.NONE, HomeLayoutMath.zoneSetFor(DragSource.NewWidget(BuiltinWidget.DATE, 2, 1), layout))
    }

    @Test
    fun geometryResolvesTheLaidOutZones() {
        val z = HomeLayoutMath.dropZones(ZoneSet.ALL, screen, 80f, 40f, 20f, 100f, 20f, 40f)
        val geo = HomeGeometry(
            screen = screen, grid = FRect(40f, 400f, 960f, 1600f), cols = 5, rows = 6, dock = null,
            removeZone = z.removeHit, uninstallZone = z.uninstallHit, infoZone = z.infoHit,
        )
        val info = DragItemInfo("a", 1, 1, true, 0f, 0f)
        val layout = HomeLayout()
        assertEquals(DropTarget.Remove, geo.resolve(100f, 150f, 0, layout, info))
        assertEquals(DropTarget.Uninstall, geo.resolve(500f, 150f, 0, layout, info))
        assertEquals(DropTarget.AppInfo, geo.resolve(900f, 30f, 0, layout, info))
    }

    // ---- hit testing ----

    private val geo = HomeGeometry(
        screen = FRect(0f, 0f, 1000f, 2000f),
        grid = FRect(50f, 200f, 950f, 1100f),
        cols = 5, rows = 6, dock = FRect(50f, 1500f, 950f, 1700f),
    )
    private val a = AppItem("a", key("a"), Placement(1, 1))
    private val w = WidgetItem("w", BuiltinWidget.CLOCK, Placement(0, 3, 4, 2))
    private val d = AppItem("d", key("d"), Placement(2, 0))
    private val layout = HomeLayout(pages = listOf(listOf(a, w), emptyList()), dock = listOf(d))

    @Test
    fun hitTestFindsGridItemsAndTheirRects() {
        // cells are 180 x 150
        val hit = HomeLayoutMath.itemAt(geo, layout, 0, 300f, 400f)!!
        assertEquals("a", hit.item.id)
        assertFalse(hit.inDock)
        assertEquals(FRect(230f, 350f, 410f, 500f), hit.rect)
        // anywhere in the span of a widget
        assertEquals("w", HomeLayoutMath.itemAt(geo, layout, 0, 600f, 700f)!!.item.id)
    }

    @Test
    fun hitTestFindsDockItemsAndIgnoresEmptySpace() {
        val hit = HomeLayoutMath.itemAt(geo, layout, 0, 50f + 2 * 225f + 10f, 1600f)!!
        assertEquals("d", hit.item.id)
        assertTrue(hit.inDock)
        assertNull(HomeLayoutMath.itemAt(geo, layout, 0, 60f, 1600f), "empty dock slot")
        assertNull(HomeLayoutMath.itemAt(geo, layout, 0, 100f, 250f), "empty cell")
        assertNull(HomeLayoutMath.itemAt(geo, layout, 0, 500f, 1300f), "between grid and dock")
        assertNull(HomeLayoutMath.itemAt(geo, layout, 0, 20f, 400f), "outside the grid margin")
        assertNull(HomeLayoutMath.itemAt(geo, layout, 1, 300f, 400f), "other page is empty")
        assertNull(HomeLayoutMath.itemAt(geo, layout, 7, 300f, 400f), "page that does not exist")
    }

    // ---- menu ----

    @Test
    fun menuGoesBelowTheAnchorWhenThereIsRoom() {
        val p = HomeLayoutMath.menuPosition(FRect(100f, 200f, 200f, 300f), 200f, 300f, 1000f, 2000f, 16f, 8f)
        assertEquals(50f, p.x)
        assertEquals(308f, p.y)
    }

    @Test
    fun menuGoesAboveTheAnchorAtTheBottomAndStaysOnScreen() {
        val p = HomeLayoutMath.menuPosition(FRect(900f, 1800f, 1000f, 1900f), 200f, 300f, 1000f, 2000f, 16f, 8f)
        assertEquals(1000f - 200f - 16f, p.x)
        assertEquals(1800f - 8f - 300f, p.y)
    }

    @Test
    fun menuIsCentredWithoutAnchorAndNeverCrashesOnTinyScreens() {
        val c = HomeLayoutMath.menuPosition(null, 200f, 300f, 1000f, 2000f, 16f, 8f)
        assertEquals(400f, c.x)
        assertEquals(850f, c.y)
        val tiny = HomeLayoutMath.menuPosition(FRect(0f, 0f, 10f, 10f), 500f, 500f, 300f, 300f, 16f, 8f)
        assertEquals(16f, tiny.x)
        assertEquals(16f, tiny.y)
    }

    // ---- widgets ----

    @Test
    fun resizeCyclesThroughTheSupportedSizes() {
        // CLOCK: 4x2, 2x2, 4x1
        assertEquals(listOf(2 to 2, 4 to 1), HomeLayoutMath.resizeCandidates(BuiltinWidget.CLOCK, 4, 2))
        assertEquals(listOf(4 to 1, 4 to 2), HomeLayoutMath.resizeCandidates(BuiltinWidget.CLOCK, 2, 2))
        assertEquals(listOf(4 to 2, 2 to 2), HomeLayoutMath.resizeCandidates(BuiltinWidget.CLOCK, 4, 1))
        // an odd size starts from the first supported one
        assertEquals(listOf(4 to 2, 2 to 2, 4 to 1), HomeLayoutMath.resizeCandidates(BuiltinWidget.CLOCK, 3, 3))
        // two supported sizes: just the other one
        assertEquals(listOf(4 to 1), HomeLayoutMath.resizeCandidates(BuiltinWidget.WORLD_CLOCK, 4, 2))
    }
}
