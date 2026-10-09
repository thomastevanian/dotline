package com.dotline.launcher.home

import com.dotline.launcher.data.LayoutEngine
import com.dotline.launcher.data.model.AppItem
import com.dotline.launcher.data.model.FolderItem
import com.dotline.launcher.data.model.HomeLayout
import com.dotline.launcher.data.model.Placement

/** Axis-aligned rectangle in root (screen) pixels. Plain floats so this file stays JVM-testable. */
data class FRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    fun contains(x: Float, y: Float): Boolean = x >= left && x < right && y >= top && y < bottom
}

/** What the finger is over while dragging. */
sealed interface DropTarget {
    /** Nothing useful (e.g. between pages). */
    data object None : DropTarget
    /** A grid cell on [page]; [placement] is where the dragged item's top-left would land. */
    data class Cell(val page: Int, val placement: Placement) : DropTarget
    /** Dock slot. */
    data class Dock(val slot: Int) : DropTarget
    /** On top of an app or folder: dropping makes / joins a folder. */
    data class OnItem(val page: Int, val itemId: String) : DropTarget
    data object Remove : DropTarget
    data object Uninstall : DropTarget
    data object AppInfo : DropTarget
    /** Near the left/right edge: auto-scroll to the previous/next page. */
    data object EdgeLeft : DropTarget
    data object EdgeRight : DropTarget
}

/** The item being dragged, as far as geometry is concerned. */
data class DragItemInfo(
    /** Id of the item already on the home screen, or null when it comes from the drawer/picker. */
    val itemId: String?,
    val spanX: Int,
    val spanY: Int,
    /** True for apps (they can create or join folders). */
    val isApp: Boolean,
    /** Finger offset from the item's top-left, px. */
    val grabX: Float,
    val grabY: Float,
)

/**
 * Screen geometry of the home screen in root pixels. Pages share one grid area; pages are
 * assumed to be settled (fully snapped), the pager is held while dragging.
 */
data class HomeGeometry(
    val screen: FRect,
    val grid: FRect,
    val cols: Int,
    val rows: Int,
    val dock: FRect?,
    val dockSlots: Int = HomeLayout.DOCK_SLOTS,
    val removeZone: FRect? = null,
    val uninstallZone: FRect? = null,
    val infoZone: FRect? = null,
    /** Width of the left/right bands that trigger page auto-scroll. */
    val edgePx: Float = 40f,
) {
    val cellW: Float get() = grid.width / cols
    val cellH: Float get() = grid.height / rows

    /** Cell index under a point inside the grid (clamped to the grid). */
    fun cellAt(x: Float, y: Float): Pair<Int, Int> {
        val c = ((x - grid.left) / cellW).toInt().coerceIn(0, cols - 1)
        val r = ((y - grid.top) / cellH).toInt().coerceIn(0, rows - 1)
        return c to r
    }

    /** Pixel rect of a placement. */
    fun rectOf(p: Placement): FRect = FRect(
        grid.left + p.col * cellW,
        grid.top + p.row * cellH,
        grid.left + (p.col + p.spanX) * cellW,
        grid.top + (p.row + p.spanY) * cellH,
    )

    fun dockSlotRect(slot: Int): FRect? {
        val d = dock ?: return null
        val w = d.width / dockSlots
        return FRect(d.left + slot * w, d.top, d.left + (slot + 1) * w, d.bottom)
    }

    /**
     * Resolves what the pointer at ([x],[y]) is over. Order matters: drop zones win over the
     * dock, the dock over the grid, edges last (they only apply on the grid area).
     */
    fun resolve(x: Float, y: Float, page: Int, layout: HomeLayout, item: DragItemInfo): DropTarget {
        if (removeZone?.contains(x, y) == true) return DropTarget.Remove
        if (uninstallZone?.contains(x, y) == true) return DropTarget.Uninstall
        if (infoZone?.contains(x, y) == true) return DropTarget.AppInfo

        val d = dock
        if (d != null && d.contains(x, y)) {
            val slot = (((x - d.left) / (d.width / dockSlots)).toInt()).coerceIn(0, dockSlots - 1)
            val occupant = layout.dock.firstOrNull { it.placement.col == slot }
            if (occupant != null && occupant.id != item.itemId && item.isApp && (occupant is AppItem || occupant is FolderItem)) {
                return DropTarget.OnItem(DOCK_PAGE, occupant.id)
            }
            return DropTarget.Dock(slot)
        }

        if (x < screen.left + edgePx) return DropTarget.EdgeLeft
        if (x >= screen.right - edgePx) return DropTarget.EdgeRight

        if (y < grid.top - cellH || y >= grid.bottom + cellH) return DropTarget.None

        // Anchor multi-cell items by their top-left corner.
        val anchorX = if (item.spanX > 1 || item.spanY > 1) x - item.grabX + cellW / 2 else x
        val anchorY = if (item.spanX > 1 || item.spanY > 1) y - item.grabY + cellH / 2 else y
        var (col, row) = cellAt(anchorX, anchorY)
        col = col.coerceIn(0, (cols - item.spanX).coerceAtLeast(0))
        row = row.coerceIn(0, (rows - item.spanY).coerceAtLeast(0))

        val items = layout.pages.getOrNull(page).orEmpty()
        if (item.isApp && item.spanX == 1 && item.spanY == 1) {
            val occupant = LayoutEngine.itemAt(items, col, row)
            if (occupant != null && occupant.id != item.itemId && (occupant is AppItem || occupant is FolderItem)) {
                // Only the middle of the cell makes a folder; the outer ring means "insert here".
                val rect = rectOf(occupant.placement)
                val insetX = rect.width * FOLDER_INSET
                val insetY = rect.height * FOLDER_INSET
                if (x >= rect.left + insetX && x < rect.right - insetX && y >= rect.top + insetY && y < rect.bottom - insetY) {
                    return DropTarget.OnItem(page, occupant.id)
                }
            }
        }
        return DropTarget.Cell(page, Placement(col, row, item.spanX, item.spanY))
    }

    companion object {
        const val DOCK_PAGE = -1
        /** Fraction of the cell ignored on each side when deciding a folder hover. */
        const val FOLDER_INSET = 0.2f
    }
}

/**
 * Debounces drop-target changes so a folder is only created after the finger rests on an item, and
 * edge auto-scroll only fires after dwelling in the edge band.
 */
class DwellTracker(private val dwellMs: Long) {
    private var target: DropTarget = DropTarget.None
    private var since: Long = 0L

    /** Feed the latest target; returns true once it has been stable for [dwellMs]. */
    fun update(now: Long, t: DropTarget): Boolean {
        if (t != target) {
            target = t
            since = now
            return false
        }
        return now - since >= dwellMs
    }

    fun reset() { target = DropTarget.None; since = 0L }
}
