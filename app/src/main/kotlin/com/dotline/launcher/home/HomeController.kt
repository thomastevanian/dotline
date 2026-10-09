package com.dotline.launcher.home

import com.dotline.launcher.data.LayoutEngine
import com.dotline.launcher.data.LayoutStore
import com.dotline.launcher.data.model.AppItem
import com.dotline.launcher.data.model.AppKey
import com.dotline.launcher.data.model.BuiltinWidget
import com.dotline.launcher.data.model.FolderItem
import com.dotline.launcher.data.model.HomeLayout
import com.dotline.launcher.data.model.HostedWidgetItem
import com.dotline.launcher.data.model.Placement
import com.dotline.launcher.data.model.WidgetItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Where a drag started. */
sealed interface DragSource {
    /** An item that is already on a page or in the dock. */
    data class OnHome(val itemId: String) : DragSource
    /** An app taken out of an open folder. */
    data class FromFolder(val folderId: String, val app: AppKey) : DragSource
    /** An app dragged out of the app drawer. */
    data class NewApp(val app: AppKey) : DragSource
    /** A built-in widget dragged out of the widget picker. */
    data class NewWidget(val kind: BuiltinWidget, val spanX: Int, val spanY: Int) : DragSource
}

/** Live drag: the finger position (root px) and what it is currently over. */
data class DragState(
    val source: DragSource,
    val info: DragItemInfo,
    val x: Float,
    val y: Float,
    val target: DropTarget,
)

/** The popup opened by a long press without dragging. [anchor] is the pressed item's rect in root px. */
sealed interface HomeMenu {
    val anchor: FRect?
    data class App(val itemId: String?, val app: AppKey, override val anchor: FRect?) : HomeMenu
    data class Folder(val itemId: String, override val anchor: FRect?) : HomeMenu
    data class Widget(val itemId: String, override val anchor: FRect?) : HomeMenu
}

data class HomeUiState(
    val editMode: Boolean = false,
    val drag: DragState? = null,
    val openFolderId: String? = null,
    val menu: HomeMenu? = null,
    val pickerOpen: Boolean = false,
    val drawerOpen: Boolean = false,
)

/** What a drop did, so the UI can react (system dialogs) or snap the item back. */
sealed interface DropOutcome {
    data object Moved : DropOutcome
    data object FolderCreated : DropOutcome
    data object AddedToFolder : DropOutcome
    data object Removed : DropOutcome
    data class UninstallRequested(val app: AppKey) : DropOutcome
    data class AppInfoRequested(val app: AppKey) : DropOutcome
    /** Not possible here (blocked cell, wrong target): the item returns to where it was. */
    data object Rejected : DropOutcome
    data object Cancelled : DropOutcome
}

/** Asks the pager to move while an item is held at a screen edge. */
enum class ScrollRequest { NONE, PREVIOUS, NEXT }

/**
 * All home-screen interaction logic that is not drawing: edit mode, open folder, menus, the widget
 * picker, the drawer flag, and the drag session with its drop rules. The UI renders [ui] and the
 * layout, forwards pointer events here, and acts on the returned [DropOutcome] / [ScrollRequest].
 * Pure Kotlin (no Compose or Android types) so every rule is unit-tested.
 *
 * @param grid current (columns, rows) of the home grid
 */
class HomeController(
    private val store: LayoutStore,
    private val grid: () -> Pair<Int, Int>,
    private val newId: () -> String,
    edgeDwellMs: Long = 600L,
) {
    private val _ui = MutableStateFlow(HomeUiState())
    val ui: StateFlow<HomeUiState> = _ui.asStateFlow()
    private val dwell = DwellTracker(edgeDwellMs)

    // ---- panels ----------------------------------------------------------------------------

    fun enterEdit() = _ui.update { it.copy(editMode = true, menu = null, openFolderId = null) }

    fun exitEdit() {
        cancelDrag()
        trimEmptyPages()
        _ui.update { it.copy(editMode = false) }
    }

    fun openFolder(folderId: String) = _ui.update { it.copy(openFolderId = folderId, menu = null) }
    fun closeFolder() = _ui.update { it.copy(openFolderId = null) }
    fun showMenu(menu: HomeMenu) = _ui.update { it.copy(menu = menu) }
    fun dismissMenu() = _ui.update { it.copy(menu = null) }
    fun openPicker() = _ui.update { it.copy(pickerOpen = true, menu = null, editMode = false) }
    fun closePicker() = _ui.update { it.copy(pickerOpen = false) }
    fun setDrawerOpen(open: Boolean) = _ui.update { it.copy(drawerOpen = open, menu = if (open) null else it.menu) }

    /** Back: closes the topmost thing. Returns false when nothing was open (root home). */
    fun closeTopmost(): Boolean {
        val s = _ui.value
        when {
            s.drag != null -> cancelDrag()
            s.menu != null -> dismissMenu()
            s.pickerOpen -> closePicker()
            s.openFolderId != null -> closeFolder()
            s.drawerOpen -> setDrawerOpen(false)
            s.editMode -> exitEdit()
            else -> return false
        }
        return true
    }

    /** Home button: close everything and leave edit mode. */
    fun closeAll() {
        cancelDrag()
        trimEmptyPages()
        _ui.value = HomeUiState()
    }

    // ---- drag session ----------------------------------------------------------------------

    /** Starts dragging [source]. [grabX]/[grabY] = finger offset inside the item; x/y = finger in root px. */
    fun beginDrag(source: DragSource, grabX: Float, grabY: Float, x: Float, y: Float, geometry: HomeGeometry, page: Int): Boolean {
        val layout = store.layout.value
        val info = infoFor(source, layout, grabX, grabY) ?: return false
        dwell.reset()
        val target = geometry.resolve(x, y, page, layout, info)
        _ui.update {
            it.copy(
                drag = DragState(source, info, x, y, target),
                menu = null,
                drawerOpen = false,
                openFolderId = if (source is DragSource.FromFolder) null else it.openFolderId,
            )
        }
        return true
    }

    /** Moves the held item; returns whether the pager should scroll (after dwelling at an edge). */
    fun updateDrag(x: Float, y: Float, geometry: HomeGeometry, page: Int, nowMs: Long): ScrollRequest {
        val drag = _ui.value.drag ?: return ScrollRequest.NONE
        val layout = store.layout.value
        val target = geometry.resolve(x, y, page, layout, drag.info)
        _ui.update { s -> s.drag?.let { s.copy(drag = it.copy(x = x, y = y, target = target)) } ?: s }

        val atEdge = target == DropTarget.EdgeLeft || target == DropTarget.EdgeRight
        if (!atEdge) {
            dwell.reset()
            return ScrollRequest.NONE
        }
        if (!dwell.update(nowMs, target)) return ScrollRequest.NONE
        dwell.reset() // another full dwell is needed before the next flip
        return if (target == DropTarget.EdgeRight) {
            if (page >= layout.pages.lastIndex) {
                if (layout.pages.size >= HomeLayout.MAX_PAGES) return ScrollRequest.NONE
                store.update { LayoutEngine.addPage(it) }
            }
            ScrollRequest.NEXT
        } else {
            if (page > 0) ScrollRequest.PREVIOUS else ScrollRequest.NONE
        }
    }

    /** Drops the held item at ([x],[y]) and applies the edit. */
    fun endDrag(x: Float, y: Float, geometry: HomeGeometry, page: Int): DropOutcome {
        val drag = _ui.value.drag ?: return DropOutcome.Cancelled
        val target = geometry.resolve(x, y, page, store.layout.value, drag.info)
        _ui.update { it.copy(drag = null) }
        dwell.reset()
        val outcome = applyDrop(drag.source, target)
        trimEmptyPages()
        return outcome
    }

    fun cancelDrag() {
        if (_ui.value.drag != null) {
            _ui.update { it.copy(drag = null) }
            dwell.reset()
            trimEmptyPages()
        }
    }

    private fun infoFor(source: DragSource, layout: HomeLayout, grabX: Float, grabY: Float): DragItemInfo? = when (source) {
        is DragSource.OnHome -> LayoutEngine.findItem(layout, source.itemId)?.let { item ->
            DragItemInfo(source.itemId, item.placement.spanX, item.placement.spanY, item is AppItem || item is FolderItem, grabX, grabY)
        }
        is DragSource.FromFolder -> DragItemInfo(null, 1, 1, true, grabX, grabY)
        is DragSource.NewApp -> DragItemInfo(null, 1, 1, true, grabX, grabY)
        is DragSource.NewWidget -> DragItemInfo(null, source.spanX, source.spanY, false, grabX, grabY)
    }

    private fun applyDrop(source: DragSource, target: DropTarget): DropOutcome {
        val (cols, rows) = grid()
        var outcome: DropOutcome = DropOutcome.Rejected
        fun edit(success: DropOutcome, block: (HomeLayout) -> HomeLayout?) {
            store.update { layout ->
                val next = block(layout)
                if (next != null) outcome = success
                next
            }
        }
        fun newApp(app: AppKey) = AppItem(newId(), app, Placement(0, 0))

        when (target) {
            is DropTarget.Cell -> when (source) {
                is DragSource.OnHome -> edit(DropOutcome.Moved) {
                    LayoutEngine.moveToPage(it, source.itemId, target.page, target.placement, cols, rows)
                }
                is DragSource.FromFolder -> edit(DropOutcome.Moved) { layout ->
                    LayoutEngine.takeFromFolder(layout, source.folderId, source.app)
                        ?.let { LayoutEngine.placeNew(it, newApp(source.app), target.page, target.placement, cols, rows) }
                }
                is DragSource.NewApp -> edit(DropOutcome.Moved) {
                    LayoutEngine.placeNew(it, newApp(source.app), target.page, target.placement, cols, rows)
                }
                is DragSource.NewWidget -> edit(DropOutcome.Moved) {
                    val widget = WidgetItem(newId(), source.kind, Placement(0, 0, source.spanX, source.spanY))
                    LayoutEngine.placeNew(it, widget, target.page, target.placement, cols, rows)
                }
            }
            is DropTarget.Dock -> when (source) {
                is DragSource.OnHome -> edit(DropOutcome.Moved) { LayoutEngine.moveToDock(it, source.itemId, target.slot) }
                is DragSource.FromFolder -> edit(DropOutcome.Moved) { layout ->
                    LayoutEngine.takeFromFolder(layout, source.folderId, source.app)
                        ?.let { LayoutEngine.placeNewInDock(it, newApp(source.app), target.slot) }
                }
                is DragSource.NewApp -> edit(DropOutcome.Moved) { LayoutEngine.placeNewInDock(it, newApp(source.app), target.slot) }
                is DragSource.NewWidget -> Unit
            }
            is DropTarget.OnItem -> {
                val targetItem = if (target.page == HomeGeometry.DOCK_PAGE) {
                    store.layout.value.dock.firstOrNull { it.id == target.itemId }
                } else {
                    LayoutEngine.findItem(store.layout.value, target.itemId)
                }
                when (source) {
                    is DragSource.OnHome -> when (targetItem) {
                        is AppItem -> edit(DropOutcome.FolderCreated) {
                            LayoutEngine.createFolder(it, source.itemId, target.itemId, DEFAULT_FOLDER_NAME, newId())
                        }
                        is FolderItem -> edit(DropOutcome.AddedToFolder) { LayoutEngine.addToFolder(it, source.itemId, target.itemId) }
                        else -> Unit
                    }
                    is DragSource.FromFolder -> if (target.itemId != source.folderId) when (targetItem) {
                        is AppItem -> edit(DropOutcome.FolderCreated) { layout ->
                            LayoutEngine.takeFromFolder(layout, source.folderId, source.app)
                                ?.let { LayoutEngine.createFolderWith(it, target.itemId, source.app, DEFAULT_FOLDER_NAME, newId()) }
                        }
                        is FolderItem -> edit(DropOutcome.AddedToFolder) { layout ->
                            LayoutEngine.takeFromFolder(layout, source.folderId, source.app)
                                ?.let { LayoutEngine.addAppToFolder(it, target.itemId, source.app) }
                        }
                        else -> Unit
                    }
                    is DragSource.NewApp -> when (targetItem) {
                        is AppItem -> edit(DropOutcome.FolderCreated) {
                            LayoutEngine.createFolderWith(it, target.itemId, source.app, DEFAULT_FOLDER_NAME, newId())
                        }
                        is FolderItem -> edit(DropOutcome.AddedToFolder) { LayoutEngine.addAppToFolder(it, target.itemId, source.app) }
                        else -> Unit
                    }
                    is DragSource.NewWidget -> Unit
                }
            }
            DropTarget.Remove -> when (source) {
                is DragSource.OnHome -> edit(DropOutcome.Removed) { LayoutEngine.removeItem(it, source.itemId) }
                is DragSource.FromFolder -> edit(DropOutcome.Removed) { LayoutEngine.takeFromFolder(it, source.folderId, source.app) }
                else -> outcome = DropOutcome.Cancelled
            }
            DropTarget.Uninstall -> appOf(source)?.let { outcome = DropOutcome.UninstallRequested(it) }
            DropTarget.AppInfo -> appOf(source)?.let { outcome = DropOutcome.AppInfoRequested(it) }
            DropTarget.None, DropTarget.EdgeLeft, DropTarget.EdgeRight -> Unit
        }
        return outcome
    }

    /** The app behind a drag, when the dragged thing is a single app. */
    private fun appOf(source: DragSource): AppKey? = when (source) {
        is DragSource.OnHome -> (LayoutEngine.findItem(store.layout.value, source.itemId) as? AppItem)?.app
        is DragSource.FromFolder -> source.app
        is DragSource.NewApp -> source.app
        is DragSource.NewWidget -> null
    }

    // ---- non-drag edits --------------------------------------------------------------------

    fun removeFromHome(itemId: String) = store.update { LayoutEngine.removeItem(it, itemId) }

    fun renameFolder(folderId: String, name: String) =
        store.update { LayoutEngine.renameFolder(it, folderId, name.trim().ifEmpty { DEFAULT_FOLDER_NAME }.take(MAX_FOLDER_NAME)) }

    fun reorderFolder(folderId: String, apps: List<AppKey>) = store.update { LayoutEngine.reorderFolder(it, folderId, apps) }

    /** "Add to home screen": first free spot on any page. False when the home screen is full. */
    fun addAppToHome(app: AppKey): Boolean {
        val (cols, rows) = grid()
        var ok = false
        store.update { layout ->
            val next = LayoutEngine.addToFirstFree(layout, AppItem(newId(), app, Placement(0, 0)), cols, rows)
            ok = next != null
            next
        }
        return ok
    }

    fun addWidget(kind: BuiltinWidget, spanX: Int, spanY: Int): Boolean {
        val (cols, rows) = grid()
        var ok = false
        store.update { layout ->
            val next = LayoutEngine.addToFirstFree(layout, WidgetItem(newId(), kind, Placement(0, 0, spanX, spanY)), cols, rows)
            ok = next != null
            next
        }
        return ok
    }

    fun addHostedWidget(appWidgetId: Int, provider: String, spanX: Int, spanY: Int): Boolean {
        val (cols, rows) = grid()
        var ok = false
        store.update { layout ->
            val item = HostedWidgetItem(newId(), appWidgetId, provider, Placement(0, 0, spanX, spanY))
            val next = LayoutEngine.addToFirstFree(layout, item, cols, rows)
            ok = next != null
            next
        }
        return ok
    }

    /** Resizes a widget in place when the new size fits. */
    fun resizeWidget(itemId: String, spanX: Int, spanY: Int): Boolean {
        val (cols, rows) = grid()
        var ok = false
        store.update { layout ->
            val item = LayoutEngine.findItem(layout, itemId) as? WidgetItem
            val next = if (item == null) null else {
                val p = item.placement.copy(spanX = spanX, spanY = spanY)
                val page = layout.pages.indexOfFirst { pg -> pg.any { it.id == itemId } }
                if (page < 0 || !LayoutEngine.fits(p, cols, rows) || !LayoutEngine.isFree(layout.pages[page], p, ignoreId = itemId)) null
                else layout.copy(pages = layout.pages.mapIndexed { i, pg -> if (i == page) pg.map { if (it.id == itemId) item.copy(placement = p) else it } else pg })
            }
            ok = next != null
            next
        }
        return ok
    }

    /** Drops empty trailing pages (e.g. one created for a drag that was never used). */
    fun trimEmptyPages() = store.update { LayoutEngine.trimPages(it) }

    companion object {
        const val DEFAULT_FOLDER_NAME = "Folder"
        const val MAX_FOLDER_NAME = 24
    }
}
