package com.dotline.launcher.home

import com.dotline.launcher.data.model.AppItem
import com.dotline.launcher.data.model.AppKey
import com.dotline.launcher.data.model.FolderItem
import com.dotline.launcher.data.model.HomeLayout
import com.dotline.launcher.data.model.Placement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeGeometryTest {
    // 1080 x 2400 screen, 5 x 6 grid starting at y=300, each cell 200 x 250 (grid 40..1040 x 300..1800), dock at y 2000..2200
    private val geo = HomeGeometry(
        screen = FRect(0f, 0f, 1080f, 2400f),
        grid = FRect(40f, 300f, 1040f, 1800f),
        cols = 5, rows = 6,
        dock = FRect(40f, 2000f, 1040f, 2200f),
        removeZone = FRect(0f, 0f, 540f, 120f),
        uninstallZone = FRect(540f, 0f, 1080f, 120f),
        edgePx = 30f,
    )
    private fun key(n: String) = AppKey("p.$n", "p.$n.M", 0L)
    private val a = AppItem("a", key("a"), Placement(1, 1))
    private val f = FolderItem("f", "F", listOf(key("x"), key("y")), Placement(3, 1))
    private val layout = HomeLayout(listOf(listOf(a, f)), dock = listOf(AppItem("d", key("d"), Placement(2, 0))))
    private val appDrag = DragItemInfo(itemId = "new", spanX = 1, spanY = 1, isApp = true, grabX = 0f, grabY = 0f)

    @Test
    fun dropZonesWin() {
        assertEquals(DropTarget.Remove, geo.resolve(100f, 50f, 0, layout, appDrag))
        assertEquals(DropTarget.Uninstall, geo.resolve(800f, 50f, 0, layout, appDrag))
    }

    @Test
    fun gridCellAndRect() {
        assertEquals(200f, geo.cellW)
        assertEquals(250f, geo.cellH)
        assertEquals(FRect(240f, 550f, 440f, 800f), geo.rectOf(Placement(1, 1)))
        val t = geo.resolve(100f, 330f, 0, layout, appDrag)
        assertEquals(DropTarget.Cell(0, Placement(0, 0)), t)
    }

    @Test
    fun hoverCenterOfAppMakesFolderButEdgeOfCellDoesNot() {
        // centre of cell (1,1) = (340, 675)
        assertEquals(DropTarget.OnItem(0, "a"), geo.resolve(340f, 675f, 0, layout, appDrag))
        // near the left edge of the cell: just inserts at that cell
        assertEquals(DropTarget.Cell(0, Placement(1, 1)), geo.resolve(250f, 560f, 0, layout, appDrag))
    }

    @Test
    fun hoverFolderJoinsIt() {
        assertEquals(DropTarget.OnItem(0, "f"), geo.resolve(740f, 675f, 0, layout, appDrag))
    }

    @Test
    fun draggingItemOverItselfIsPlainCell() {
        val self = appDrag.copy(itemId = "a")
        assertEquals(DropTarget.Cell(0, Placement(1, 1)), geo.resolve(340f, 675f, 0, layout, self))
    }

    @Test
    fun widgetsNeverMakeFolders() {
        val wide = appDrag.copy(isApp = false, spanX = 4, spanY = 2)
        val t = geo.resolve(340f, 675f, 0, layout, wide)
        assertTrue(t is DropTarget.Cell)
        val c = t as DropTarget.Cell
        assertTrue(c.placement.col + 4 <= 5 && c.placement.row + 2 <= 6, "span kept inside the grid")
    }

    @Test
    fun dockSlotsAndDockFolders() {
        // dock slot width = 250 (1000 / 4); slot 2 spans x 540..790 and is occupied by app d
        assertEquals(DropTarget.Dock(0), geo.resolve(100f, 2100f, 0, layout, appDrag))
        assertEquals(DropTarget.OnItem(HomeGeometry.DOCK_PAGE, "d"), geo.resolve(600f, 2100f, 0, layout, appDrag))
        val widget = appDrag.copy(isApp = false)
        assertEquals(DropTarget.Dock(2), geo.resolve(600f, 2100f, 0, layout, widget))
    }

    @Test
    fun screenEdgesAutoScroll() {
        assertEquals(DropTarget.EdgeLeft, geo.resolve(10f, 1000f, 0, layout, appDrag))
        assertEquals(DropTarget.EdgeRight, geo.resolve(1075f, 1000f, 0, layout, appDrag))
    }

    @Test
    fun dwellTrackerNeedsStableTarget() {
        val d = DwellTracker(500)
        val t = DropTarget.OnItem(0, "a")
        assertEquals(false, d.update(0, t))
        assertEquals(false, d.update(300, t))
        assertEquals(true, d.update(500, t))
        assertEquals(false, d.update(600, DropTarget.None))
        assertEquals(false, d.update(900, DropTarget.None))
        assertEquals(true, d.update(1100, DropTarget.None))
    }
}
