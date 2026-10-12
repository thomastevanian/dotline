package com.dotline.launcher.home

import com.dotline.launcher.data.LayoutEngine
import com.dotline.launcher.data.model.AppItem
import com.dotline.launcher.data.model.BuiltinWidget
import com.dotline.launcher.data.model.HomeItem
import com.dotline.launcher.data.model.HomeLayout
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Cell size of the home grid in pixels. [left] is where the grid starts inside its page area. */
data class GridMetrics(
    val left: Float,
    val cellW: Float,
    val cellH: Float,
    val cols: Int,
    val rows: Int,
) {
    val width: Float get() = cellW * cols
    val height: Float get() = cellH * rows

    /** Left edge (px, relative to the page area) of column [col]. Rounded so neighbours never leave a seam. */
    fun xEdge(col: Int): Int = (left + col * cellW).roundToInt()

    /** Top edge (px, relative to the page area) of row [row]. */
    fun yEdge(row: Int): Int = (row * cellH).roundToInt()
}

/** Which drop zones are offered while dragging. */
enum class ZoneSet { NONE, REMOVE_ONLY, APP_ONLY, ALL }

/** A plain 2D point (kept free of Compose types so this file is JVM-testable). */
data class FPoint(val x: Float, val y: Float)

/**
 * The three drop zones at the top of the screen: the visible pill and the (larger) area that counts
 * as hovering it. A zone that is not offered for the current drag is null.
 */
data class DropZoneLayout(
    val removePill: FRect?,
    val uninstallPill: FRect?,
    val infoPill: FRect?,
    val removeHit: FRect?,
    val uninstallHit: FRect?,
    val infoHit: FRect?,
)

/** An item found under a point together with its rect (root px) and where it lives. */
data class HitItem(val item: HomeItem, val rect: FRect, val inDock: Boolean)

/**
 * Pure rect / paging / menu maths of the home screen. Everything works in pixels and plain floats so
 * it is compiled and unit-tested on the JVM; the Compose layer only forwards real measurements.
 */
object HomeLayoutMath {
    /** Tile diameter as a fraction of the column width (Nothing OS: 52 dp tile in a 73 dp column). */
    const val ICON_FRACTION = 0.715f

    /** Page count of the pager while infinite scrolling is on. */
    const val VIRTUAL_PAGES = 10_000

    // ---- grid ------------------------------------------------------------------------------

    /**
     * Cell metrics for a page area of [areaWidth] x [areaHeight]: the grid is [marginPx] narrower than
     * the area on each side, cells are [maxCellHeightPx] high at most, and the grid is anchored to the
     * top of the area.
     */
    fun gridMetrics(
        areaWidth: Float,
        areaHeight: Float,
        cols: Int,
        rows: Int,
        marginPx: Float,
        maxCellHeightPx: Float,
    ): GridMetrics {
        val c = max(cols, 1)
        val r = max(rows, 1)
        val margin = max(marginPx, 0f)
        val gridWidth = max(areaWidth - 2f * margin, 1f)
        val cellW = gridWidth / c
        val cellH = max(min(areaHeight / r, maxCellHeightPx), 1f)
        return GridMetrics(margin, cellW, cellH, c, r)
    }

    /** The grid rectangle in root px for a page area given in root px. */
    fun gridRect(area: FRect, m: GridMetrics): FRect =
        FRect(area.left + m.left, area.top, area.left + m.left + m.width, area.top + m.height)

    /** Vertical centre of the icon tile in a cell when tile and label are centred as one block. */
    fun tileCenterY(cellTop: Float, cellH: Float, tilePx: Float, labelBlockPx: Float): Float {
        val block = tilePx + labelBlockPx
        return cellTop + (cellH - block) / 2f + tilePx / 2f
    }

    // ---- paging ----------------------------------------------------------------------------

    fun isInfinite(setting: Boolean, pageCount: Int): Boolean = setting && pageCount > 1

    /** Number of pages the pager itself is told about. */
    fun pagerCount(pageCount: Int, infinite: Boolean): Int = if (infinite) VIRTUAL_PAGES else max(pageCount, 1)

    /** Pager page that shows logical page [logical]; infinite mode starts in the middle of the virtual range. */
    fun initialPage(pageCount: Int, infinite: Boolean, logical: Int = 0): Int {
        val n = max(pageCount, 1)
        val l = logical.coerceIn(0, n - 1)
        if (!infinite) return l
        val half = VIRTUAL_PAGES / 2
        return half - half % n + l
    }

    /** Logical page behind a pager page. */
    fun logicalPage(pagerPage: Int, pageCount: Int, infinite: Boolean): Int {
        val n = max(pageCount, 1)
        if (!infinite) return pagerPage.coerceIn(0, n - 1)
        return ((pagerPage % n) + n) % n
    }

    /** The pager page showing [logicalTarget] that is closest to [current] (the same page when not infinite). */
    fun nearestPagerPage(current: Int, logicalTarget: Int, pageCount: Int, infinite: Boolean): Int {
        val n = max(pageCount, 1)
        val target = logicalTarget.coerceIn(0, n - 1)
        if (!infinite) return target
        val base = current - (((current % n) + n) % n)
        var best = base + target
        for (candidate in intArrayOf(base + target - n, base + target + n)) {
            if (abs(candidate - current) < abs(best - current)) best = candidate
        }
        return best.coerceIn(0, VIRTUAL_PAGES - 1)
    }

    /**
     * Page that [LayoutEngine.addToFirstFree] will use for a new item of the given span: the first page
     * with room, or the next new page; -1 when every page is full and no page can be added.
     */
    fun pageForNewItem(layout: HomeLayout, cols: Int, rows: Int, spanX: Int, spanY: Int): Int {
        val sx = spanX.coerceIn(1, max(cols, 1))
        val sy = spanY.coerceIn(1, max(rows, 1))
        val index = layout.pages.indexOfFirst { LayoutEngine.findFree(it, cols, rows, sx, sy) != null }
        if (index >= 0) return index
        return if (layout.pages.size < HomeLayout.MAX_PAGES) layout.pages.size else -1
    }

    // ---- drop zones ------------------------------------------------------------------------

    /**
     * Lays the offered zones out as equal pills side by side below the status bar. Each pill's hit
     * area reaches up to the screen top and a little below the pill, and neighbouring areas meet in
     * the middle of the gap, so a finger never falls between two zones.
     */
    fun dropZones(
        set: ZoneSet,
        screen: FRect,
        safeTop: Float,
        marginPx: Float,
        gapPx: Float,
        pillHeightPx: Float,
        topGapPx: Float,
        hitBelowPx: Float,
    ): DropZoneLayout {
        val kinds: List<Int> = when (set) {
            ZoneSet.NONE -> emptyList()
            ZoneSet.REMOVE_ONLY -> listOf(KIND_REMOVE)
            ZoneSet.APP_ONLY -> listOf(KIND_UNINSTALL, KIND_INFO)
            ZoneSet.ALL -> listOf(KIND_REMOVE, KIND_UNINSTALL, KIND_INFO)
        }
        if (kinds.isEmpty()) return DropZoneLayout(null, null, null, null, null, null)
        val count = kinds.size
        val available = max(screen.width - 2f * marginPx - gapPx * (count - 1), 1f)
        val pillW = available / count
        val top = screen.top + safeTop + topGapPx
        val bottom = top + pillHeightPx
        val pills = arrayOfNulls<FRect>(3)
        val hits = arrayOfNulls<FRect>(3)
        for (i in 0 until count) {
            val left = screen.left + marginPx + i * (pillW + gapPx)
            val right = left + pillW
            val hitLeft = if (i == 0) screen.left else left - gapPx / 2f
            val hitRight = if (i == count - 1) screen.right else right + gapPx / 2f
            pills[kinds[i]] = FRect(left, top, right, bottom)
            hits[kinds[i]] = FRect(hitLeft, screen.top, hitRight, bottom + hitBelowPx)
        }
        return DropZoneLayout(
            removePill = pills[KIND_REMOVE], uninstallPill = pills[KIND_UNINSTALL], infoPill = pills[KIND_INFO],
            removeHit = hits[KIND_REMOVE], uninstallHit = hits[KIND_UNINSTALL], infoHit = hits[KIND_INFO],
        )
    }

    private const val KIND_REMOVE = 0
    private const val KIND_UNINSTALL = 1
    private const val KIND_INFO = 2

    /** Which zones to offer while dragging [source]: only apps can be uninstalled or inspected. */
    fun zoneSetFor(source: DragSource, layout: HomeLayout): ZoneSet = when (source) {
        is DragSource.OnHome -> {
            val item = LayoutEngine.findItem(layout, source.itemId)
            if (item == null) ZoneSet.NONE else if (item is AppItem) ZoneSet.ALL else ZoneSet.REMOVE_ONLY
        }
        is DragSource.FromFolder -> ZoneSet.ALL
        is DragSource.NewApp -> ZoneSet.APP_ONLY
        is DragSource.NewWidget -> ZoneSet.NONE
    }

    // ---- hit testing -----------------------------------------------------------------------

    /** The home item under ([x],[y]): a dock item, or an item of [page] inside the grid. Null for empty space. */
    fun itemAt(geometry: HomeGeometry, layout: HomeLayout, page: Int, x: Float, y: Float): HitItem? {
        val dock = geometry.dock
        if (dock != null && dock.contains(x, y)) {
            val slotW = dock.width / geometry.dockSlots
            val slot = ((x - dock.left) / slotW).toInt().coerceIn(0, geometry.dockSlots - 1)
            val item = layout.dock.firstOrNull { it.placement.col == slot } ?: return null
            val rect = geometry.dockSlotRect(slot) ?: return null
            return HitItem(item, rect, true)
        }
        if (!geometry.grid.contains(x, y)) return null
        val cell = geometry.cellAt(x, y)
        val items = layout.pages.getOrNull(page) ?: return null
        val item = LayoutEngine.itemAt(items, cell.first, cell.second) ?: return null
        return HitItem(item, geometry.rectOf(item.placement), false)
    }

    // ---- menu ------------------------------------------------------------------------------

    /**
     * Top-left of a [menuW] x [menuH] popup next to [anchor]: centred on it horizontally, below it when
     * there is room and above it otherwise, always inside the screen with [margin]. Centred on the
     * screen when there is no anchor.
     */
    fun menuPosition(
        anchor: FRect?,
        menuW: Float,
        menuH: Float,
        screenW: Float,
        screenH: Float,
        margin: Float,
        gap: Float,
    ): FPoint {
        var x = (screenW - menuW) / 2f
        var y = (screenH - menuH) / 2f
        if (anchor != null) {
            val centre = (anchor.left + anchor.right) / 2f
            x = (centre - menuW / 2f).coerceIn(margin, max(margin, screenW - menuW - margin))
            val below = anchor.bottom + gap
            y = if (below + menuH + margin <= screenH) below else max(margin, anchor.top - gap - menuH)
        }
        return FPoint(x, y)
    }

    // ---- widgets ---------------------------------------------------------------------------

    /**
     * The sizes "Resize" tries, in order: every supported size of [kind] after the current one
     * (wrapping around), never the current size itself.
     */
    fun resizeCandidates(kind: BuiltinWidget, spanX: Int, spanY: Int): List<Pair<Int, Int>> {
        val sizes = WidgetSizes.supported(kind)
        val current = sizes.indexOf(spanX to spanY)
        val out = ArrayList<Pair<Int, Int>>(sizes.size)
        for (i in 1..sizes.size) {
            val index = if (current >= 0) (current + i) % sizes.size else i - 1
            val size = sizes[index]
            if (size.first != spanX || size.second != spanY) out.add(size)
        }
        return out
    }
}
