package com.dotline.launcher.home

import com.dotline.launcher.data.LayoutEngine
import com.dotline.launcher.data.LayoutStore
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private class FakeStore(initial: HomeLayout) : LayoutStore {
    private val state = MutableStateFlow(initial)
    override val layout: StateFlow<HomeLayout> = state
    override fun update(transform: (HomeLayout) -> HomeLayout?) {
        val next = transform(state.value) ?: return
        state.value = LayoutEngine.normalize(next, 5, 6, trim = false)
    }
}

class HomeControllerTest {
    // 5 x 6 grid: x 40..1040, y 300..1800 (cell 200 x 250); dock y 2000..2200 (slot width 250); zones along the top.
    private val geo = HomeGeometry(
        screen = FRect(0f, 0f, 1080f, 2400f), grid = FRect(40f, 300f, 1040f, 1800f), cols = 5, rows = 6,
        dock = FRect(40f, 2000f, 1040f, 2200f),
        removeZone = FRect(0f, 0f, 360f, 120f), uninstallZone = FRect(360f, 0f, 720f, 120f), infoZone = FRect(720f, 0f, 1080f, 120f),
        edgePx = 30f,
    )
    private fun key(n: String) = AppKey("p.$n", "p.$n.M", 0L)
    private fun app(n: String, c: Int, r: Int) = AppItem("id_$n", key(n), Placement(c, r))
    private var ids = 0
    private fun controller(layout: HomeLayout): Pair<HomeController, FakeStore> {
        val store = FakeStore(layout)
        ids = 0
        return HomeController(store, { 5 to 6 }, { "n${ids++}" }) to store
    }
    // centre of cell (c, r)
    private fun cx(c: Int) = 40f + c * 200f + 100f
    private fun cy(r: Int) = 300f + r * 250f + 125f

    private val base = HomeLayout(listOf(listOf(app("a", 0, 0), app("b", 1, 0), app("c", 3, 3)), emptyList()), dock = listOf(AppItem("d", key("d"), Placement(0, 0))))

    private fun drag(c: HomeController, source: DragSource, fromX: Float, fromY: Float, toX: Float, toY: Float, page: Int = 0): DropOutcome {
        assertTrue(c.beginDrag(source, 20f, 20f, fromX, fromY, geo, page))
        c.updateDrag(toX, toY, geo, page, 0L)
        return c.endDrag(toX, toY, geo, page)
    }

    @Test
    fun moveToEmptyCellAndBlockedCell() {
        val (c, store) = controller(base)
        assertEquals(DropOutcome.Moved, drag(c, DragSource.OnHome("id_a"), cx(0), cy(0), cx(2), cy(4) + 60f))
        assertEquals(Placement(2, 4), LayoutEngine.findItem(store.layout.value, "id_a")!!.placement)
        // cell (3,3) holds app c; dropping on its outer ring is "insert here" -> blocked
        val blocked = drag(c, DragSource.OnHome("id_b"), cx(1), cy(0), 40f + 3 * 200f + 10f, 300f + 3 * 250f + 10f)
        assertEquals(DropOutcome.Rejected, blocked)
        assertEquals(Placement(1, 0), LayoutEngine.findItem(store.layout.value, "id_b")!!.placement, "snaps back")
        assertNull(c.ui.value.drag)
    }

    @Test
    fun dropOnAppCreatesFolderAndOnFolderJoins() {
        val (c, store) = controller(base)
        assertEquals(DropOutcome.FolderCreated, drag(c, DragSource.OnHome("id_a"), cx(0), cy(0), cx(3), cy(3)))
        val folder = store.layout.value.pages[0].filterIsInstance<FolderItem>().single()
        assertEquals(listOf(key("c"), key("a")), folder.apps)
        assertEquals(DropOutcome.AddedToFolder, drag(c, DragSource.OnHome("id_b"), cx(1), cy(0), cx(3), cy(3)))
        assertEquals(3, (LayoutEngine.findItem(store.layout.value, folder.id) as FolderItem).apps.size)
    }

    @Test
    fun dockMoveAndOccupiedDock() {
        val (c, store) = controller(base)
        assertEquals(DropOutcome.Moved, drag(c, DragSource.OnHome("id_a"), cx(0), cy(0), 40f + 250f * 2 + 125f, 2100f))
        assertEquals(LayoutEngine.DOCK, LayoutEngine.locate(store.layout.value, "id_a"))
        // dropping on the occupied dock slot 0 hits app d: folder in the dock
        assertEquals(DropOutcome.FolderCreated, drag(c, DragSource.OnHome("id_b"), cx(1), cy(0), 40f + 125f, 2100f))
    }

    @Test
    fun removeUninstallAndInfoZones() {
        val (c, store) = controller(base)
        assertEquals(DropOutcome.UninstallRequested(key("a")), drag(c, DragSource.OnHome("id_a"), cx(0), cy(0), 500f, 60f))
        assertNotNull(LayoutEngine.findItem(store.layout.value, "id_a"), "uninstall waits for the system dialog")
        assertEquals(DropOutcome.AppInfoRequested(key("b")), drag(c, DragSource.OnHome("id_b"), cx(1), cy(0), 900f, 60f))
        assertEquals(DropOutcome.Removed, drag(c, DragSource.OnHome("id_a"), cx(0), cy(0), 100f, 60f))
        assertNull(LayoutEngine.findItem(store.layout.value, "id_a"))
    }

    @Test
    fun widgetsCannotBeUninstalledOrDocked() {
        val widget = WidgetItem("w", BuiltinWidget.CLOCK, Placement(0, 1, 4, 2))
        val (c, store) = controller(HomeLayout(listOf(listOf(widget, app("a", 0, 0))), dock = emptyList()))
        assertEquals(DropOutcome.Rejected, drag(c, DragSource.OnHome("w"), cx(0), cy(1), 500f, 60f))
        // docking a widget lands on a Dock target but is refused by the engine
        assertEquals(DropOutcome.Rejected, drag(c, DragSource.OnHome("w"), cx(0), cy(1), 300f, 2100f))
        assertNotNull(LayoutEngine.findItem(store.layout.value, "w"))
    }

    @Test
    fun dragFromDrawerPlacesNewAppsAndFolders() {
        val (c, store) = controller(base)
        assertEquals(DropOutcome.Moved, drag(c, DragSource.NewApp(key("x")), 500f, 1000f, cx(4), cy(5)))
        assertEquals(key("x"), (store.layout.value.pages[0].first { it.placement == Placement(4, 5) } as AppItem).app)
        assertEquals(DropOutcome.FolderCreated, drag(c, DragSource.NewApp(key("y")), 500f, 1000f, cx(3), cy(3)))
        assertEquals(DropOutcome.Cancelled, drag(c, DragSource.NewApp(key("z")), 500f, 1000f, 100f, 60f))
        assertEquals(DropOutcome.UninstallRequested(key("z")), drag(c, DragSource.NewApp(key("z")), 500f, 1000f, 500f, 60f))
    }

    @Test
    fun dragOutOfFolderToCellDockAndZones() {
        val folder = FolderItem("f", "F", listOf(key("x"), key("y"), key("z")), Placement(2, 2))
        val (c, store) = controller(HomeLayout(listOf(listOf(folder, app("a", 0, 0))), dock = emptyList()))
        assertEquals(DropOutcome.Moved, drag(c, DragSource.FromFolder("f", key("x")), 500f, 900f, cx(4), cy(5)))
        assertEquals(2, (LayoutEngine.findItem(store.layout.value, "f") as FolderItem).apps.size)
        assertEquals(DropOutcome.Moved, drag(c, DragSource.FromFolder("f", key("y")), 500f, 900f, 40f + 125f, 2100f))
        assertTrue(LayoutEngine.findItem(store.layout.value, "f") is AppItem, "one app left: folder dissolves")
        // the dissolved folder is now a plain app item with the same id: drag it to Remove
        assertEquals(DropOutcome.Rejected, drag(c, DragSource.FromFolder("f", key("z")), 500f, 900f, 100f, 60f))
        assertEquals(DropOutcome.Removed, drag(c, DragSource.OnHome("f"), cx(2), cy(2), 100f, 60f))
        assertNull(LayoutEngine.findItem(store.layout.value, "f"))
    }

    @Test
    fun droppingAFolderAppBackOnItsOwnFolderIsRejected() {
        val folder = FolderItem("f", "F", listOf(key("x"), key("y")), Placement(2, 2))
        val (c, store) = controller(HomeLayout(listOf(listOf(folder)), emptyList()))
        assertEquals(DropOutcome.Rejected, drag(c, DragSource.FromFolder("f", key("x")), 500f, 900f, cx(2), cy(2)))
        assertEquals(2, (LayoutEngine.findItem(store.layout.value, "f") as FolderItem).apps.size)
    }

    @Test
    fun edgeDwellFlipsPagesAndCreatesAFreshLastPage() {
        val (c, store) = controller(HomeLayout(listOf(listOf(app("a", 0, 0))), emptyList()))
        assertTrue(c.beginDrag(DragSource.OnHome("id_a"), 10f, 10f, cx(0), cy(0), geo, 0))
        assertEquals(ScrollRequest.NONE, c.updateDrag(1075f, 1000f, geo, 0, 0L))
        assertEquals(ScrollRequest.NONE, c.updateDrag(1075f, 1000f, geo, 0, 400L))
        assertEquals(ScrollRequest.NEXT, c.updateDrag(1075f, 1000f, geo, 0, 700L))
        assertEquals(2, store.layout.value.pages.size, "a new empty page was added")
        // another dwell is required before the next flip
        assertEquals(ScrollRequest.NONE, c.updateDrag(1075f, 1000f, geo, 1, 800L))
        // dropping the item on the new page keeps it
        assertEquals(DropOutcome.Moved, c.endDrag(cx(1), cy(1), geo, 1))
        assertEquals(2, store.layout.value.pages.size)
        assertEquals(1, LayoutEngine.locate(store.layout.value, "id_a"))
    }

    @Test
    fun unusedEmptyPageIsTrimmedWhenTheDragEnds() {
        val (c, store) = controller(HomeLayout(listOf(listOf(app("a", 0, 0))), emptyList()))
        c.beginDrag(DragSource.OnHome("id_a"), 10f, 10f, cx(0), cy(0), geo, 0)
        c.updateDrag(1075f, 1000f, geo, 0, 0L)
        c.updateDrag(1075f, 1000f, geo, 0, 700L)
        assertEquals(2, store.layout.value.pages.size)
        c.cancelDrag()
        assertEquals(1, store.layout.value.pages.size)
    }

    @Test
    fun leftEdgeNeverScrollsBeforeTheFirstPage() {
        val (c, _) = controller(base)
        c.beginDrag(DragSource.OnHome("id_a"), 10f, 10f, cx(0), cy(0), geo, 0)
        c.updateDrag(5f, 1000f, geo, 0, 0L)
        assertEquals(ScrollRequest.NONE, c.updateDrag(5f, 1000f, geo, 0, 900L))
        c.cancelDrag()
        c.beginDrag(DragSource.OnHome("id_a"), 10f, 10f, cx(0), cy(0), geo, 1)
        c.updateDrag(5f, 1000f, geo, 1, 1000L)
        assertEquals(ScrollRequest.PREVIOUS, c.updateDrag(5f, 1000f, geo, 1, 1700L))
    }

    @Test
    fun panelsAndBackOrder() {
        val (c, _) = controller(base)
        assertFalse(c.closeTopmost(), "root home: nothing to close")
        c.enterEdit(); c.openFolder("x"); c.showMenu(HomeMenu.App("id_a", key("a"), null)); c.openPicker(); c.setDrawerOpen(true)
        assertFalse(c.ui.value.editMode, "picker closes edit mode")
        assertNull(c.ui.value.menu, "opening the picker or drawer dismisses a menu")
        assertTrue(c.closeTopmost()); assertFalse(c.ui.value.pickerOpen)
        assertTrue(c.closeTopmost()); assertNull(c.ui.value.openFolderId)
        assertTrue(c.closeTopmost()); assertFalse(c.ui.value.drawerOpen)
        assertFalse(c.closeTopmost())
        c.enterEdit(); c.showMenu(HomeMenu.App("id_a", key("a"), null))
        assertTrue(c.closeTopmost()); assertNull(c.ui.value.menu); assertTrue(c.ui.value.editMode)
        assertTrue(c.closeTopmost()); assertFalse(c.ui.value.editMode)
        c.enterEdit(); c.closeAll()
        assertEquals(HomeUiState(), c.ui.value)
    }

    @Test
    fun menuIsDismissedWhenADragStarts() {
        val (c, _) = controller(base)
        c.showMenu(HomeMenu.App("id_a", key("a"), null))
        c.beginDrag(DragSource.OnHome("id_a"), 10f, 10f, cx(0), cy(0), geo, 0)
        assertNull(c.ui.value.menu)
        assertNotNull(c.ui.value.drag)
        assertFalse(c.beginDrag(DragSource.OnHome("ghost"), 0f, 0f, 0f, 0f, geo, 0), "unknown item cannot be dragged")
    }

    @Test
    fun nonDragEdits() {
        val (c, store) = controller(base)
        assertTrue(c.addAppToHome(key("n1")))
        assertTrue(c.addWidget(BuiltinWidget.CLOCK, 4, 2))
        assertTrue(c.addHostedWidget(77, "com.x/.W", 2, 2))
        val kinds = store.layout.value.pages.flatten().map { it::class.simpleName }
        assertTrue("WidgetItem" in kinds && "HostedWidgetItem" in kinds)
        c.removeFromHome("id_a")
        assertNull(LayoutEngine.findItem(store.layout.value, "id_a"))
        val folder = FolderItem("f", "F", listOf(key("x"), key("y")), Placement(4, 5))
        val (c2, s2) = controller(HomeLayout(listOf(listOf(folder)), emptyList()))
        c2.renameFolder("f", "  Games  ")
        assertEquals("Games", (LayoutEngine.findItem(s2.layout.value, "f") as FolderItem).name)
        c2.renameFolder("f", "   ")
        assertEquals("Folder", (LayoutEngine.findItem(s2.layout.value, "f") as FolderItem).name)
    }

    @Test
    fun resizeWidgetOnlyWhenItFits() {
        val w = WidgetItem("w", BuiltinWidget.CLOCK, Placement(0, 0, 2, 2))
        val (c, store) = controller(HomeLayout(listOf(listOf(w, app("a", 2, 0))), emptyList()))
        assertFalse(c.resizeWidget("w", 4, 2), "app a blocks the wider widget")
        assertTrue(c.resizeWidget("w", 2, 3))
        assertEquals(3, LayoutEngine.findItem(store.layout.value, "w")!!.placement.spanY)
        assertFalse(c.resizeWidget("w", 6, 2))
    }
}
