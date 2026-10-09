package com.dotline.launcher.data

import com.dotline.launcher.data.model.AppItem
import com.dotline.launcher.data.model.AppKey
import com.dotline.launcher.data.model.FolderItem
import com.dotline.launcher.data.model.HomeItem
import com.dotline.launcher.data.model.HomeLayout
import com.dotline.launcher.data.model.Placement

/**
 * Pure functions that edit a [HomeLayout]: placement, moving, dock, folders, clean-up.
 * No Android or Compose types, so everything here is unit-tested on the JVM.
 *
 * Conventions: page items use (col,row) inside a cols x rows grid; dock items use
 * [Placement.col] as slot index (row 0, span 1x1). Every function returns a new layout
 * (or null when the requested edit is impossible) and never mutates its input.
 */
object LayoutEngine {
    const val MAX_FOLDER_APPS = 12

    // ---- queries ---------------------------------------------------------------------------

    fun fits(p: Placement, cols: Int, rows: Int): Boolean =
        p.spanX >= 1 && p.spanY >= 1 && p.col >= 0 && p.row >= 0 &&
            p.col + p.spanX <= cols && p.row + p.spanY <= rows

    fun isFree(items: List<HomeItem>, p: Placement, ignoreId: String? = null): Boolean =
        items.none { it.id != ignoreId && it.placement.overlaps(p) }

    /** First free spot, scanning row by row, or null when the page is full. */
    fun findFree(items: List<HomeItem>, cols: Int, rows: Int, spanX: Int = 1, spanY: Int = 1): Placement? {
        for (r in 0..(rows - spanY)) {
            for (c in 0..(cols - spanX)) {
                val p = Placement(c, r, spanX, spanY)
                if (isFree(items, p)) return p
            }
        }
        return null
    }

    fun itemAt(items: List<HomeItem>, col: Int, row: Int): HomeItem? =
        items.firstOrNull { it.placement.contains(col, row) }

    fun findItem(layout: HomeLayout, id: String): HomeItem? =
        layout.dock.firstOrNull { it.id == id } ?: layout.pages.firstNotNullOfOrNull { page -> page.firstOrNull { it.id == id } }

    /** Where the item lives: page index, or -1 for the dock, or null when absent. */
    fun locate(layout: HomeLayout, id: String): Int? {
        if (layout.dock.any { it.id == id }) return DOCK
        val page = layout.pages.indexOfFirst { p -> p.any { it.id == id } }
        return if (page >= 0) page else null
    }

    fun allApps(layout: HomeLayout): Set<AppKey> {
        val out = LinkedHashSet<AppKey>()
        fun scan(items: List<HomeItem>) = items.forEach {
            when (it) {
                is AppItem -> out += it.app
                is FolderItem -> out += it.apps
                else -> Unit
            }
        }
        layout.pages.forEach(::scan)
        scan(layout.dock)
        return out
    }

    // ---- adding ----------------------------------------------------------------------------

    /**
     * Adds [item] (its placement is replaced) to the first free spot on any page, adding a page
     * when needed. Returns null when every page is full and [HomeLayout.MAX_PAGES] is reached.
     */
    fun addToFirstFree(layout: HomeLayout, item: HomeItem, cols: Int, rows: Int): HomeLayout? {
        val spanX = item.placement.spanX.coerceIn(1, cols)
        val spanY = item.placement.spanY.coerceIn(1, rows)
        layout.pages.forEachIndexed { index, page ->
            val spot = findFree(page, cols, rows, spanX, spanY)
            if (spot != null) return withPage(layout, index, page + item.withPlacement(spot))
        }
        if (layout.pages.size >= HomeLayout.MAX_PAGES) return null
        val spot = Placement(0, 0, spanX, spanY)
        return layout.copy(pages = layout.pages + listOf(listOf(item.withPlacement(spot))))
    }

    // ---- moving ----------------------------------------------------------------------------

    /** Moves [id] to [target] on [page]. Null when the target is blocked or out of the grid. */
    fun moveToPage(layout: HomeLayout, id: String, page: Int, target: Placement, cols: Int, rows: Int): HomeLayout? {
        if (page !in layout.pages.indices) return null
        val item = findItem(layout, id) ?: return null
        val placement = target.copy(spanX = item.placement.spanX, spanY = item.placement.spanY)
        if (!fits(placement, cols, rows)) return null
        val removed = removeItem(layout, id)
        if (!isFree(removed.pages[page], placement)) return null
        return withPage(removed, page, removed.pages[page] + item.withPlacement(placement))
    }

    /** Moves an app or folder into dock [slot]. Widgets cannot be docked. Null when the slot is taken. */
    fun moveToDock(layout: HomeLayout, id: String, slot: Int): HomeLayout? {
        if (slot !in 0 until HomeLayout.DOCK_SLOTS) return null
        val item = findItem(layout, id) ?: return null
        if (item !is AppItem && item !is FolderItem) return null
        val removed = removeItem(layout, id)
        if (removed.dock.any { it.placement.col == slot }) return null
        return removed.copy(dock = removed.dock + item.withPlacement(Placement(slot, 0)))
    }

    // ---- removing --------------------------------------------------------------------------

    fun removeItem(layout: HomeLayout, id: String): HomeLayout = layout.copy(
        pages = layout.pages.map { page -> page.filterNot { it.id == id } },
        dock = layout.dock.filterNot { it.id == id },
    )

    /** An app was uninstalled: drop it everywhere, dissolving folders that fall to 0 or 1 apps. */
    fun removeApp(layout: HomeLayout, key: AppKey): HomeLayout {
        fun clean(items: List<HomeItem>): List<HomeItem> = items.mapNotNull { item ->
            when (item) {
                is AppItem -> if (item.app == key) null else item
                is FolderItem -> dissolveIfSmall(item.copy(apps = item.apps.filterNot { it == key }))
                else -> item
            }
        }
        return layout.copy(pages = layout.pages.map(::clean), dock = clean(layout.dock))
    }

    /** Removes every app in [gone] (batch form of [removeApp]). */
    fun removeApps(layout: HomeLayout, gone: Set<AppKey>): HomeLayout =
        gone.fold(layout) { acc, key -> removeApp(acc, key) }

    private fun dissolveIfSmall(folder: FolderItem): HomeItem? = when (folder.apps.size) {
        0 -> null
        1 -> AppItem(id = folder.id, app = folder.apps[0], placement = folder.placement)
        else -> folder
    }

    // ---- folders ---------------------------------------------------------------------------

    /**
     * Drops app [draggedId] onto app [targetId], making a folder at the target's position.
     * Both must be [AppItem]s. Null otherwise.
     */
    fun createFolder(layout: HomeLayout, draggedId: String, targetId: String, name: String, newId: String): HomeLayout? {
        if (draggedId == targetId) return null
        val dragged = findItem(layout, draggedId) as? AppItem ?: return null
        val target = findItem(layout, targetId) as? AppItem ?: return null
        val folder = FolderItem(newId, name, listOf(target.app, dragged.app), target.placement)
        return replaceItem(removeItem(layout, draggedId), targetId, folder)
    }

    /** Drops app [draggedId] into folder [folderId]. Null when full or types do not match. */
    fun addToFolder(layout: HomeLayout, draggedId: String, folderId: String): HomeLayout? {
        val dragged = findItem(layout, draggedId) as? AppItem ?: return null
        val folder = findItem(layout, folderId) as? FolderItem ?: return null
        if (folder.apps.size >= MAX_FOLDER_APPS || dragged.app in folder.apps) return null
        return replaceItem(removeItem(layout, draggedId), folderId, folder.copy(apps = folder.apps + dragged.app))
    }

    /**
     * Takes [app] out of folder [folderId] and puts it on the first free spot of [page]
     * (any page if that one is full). Dissolves a folder left with 0 or 1 apps.
     */
    fun removeFromFolder(layout: HomeLayout, folderId: String, app: AppKey, page: Int, cols: Int, rows: Int, newId: String): HomeLayout? {
        val folder = findItem(layout, folderId) as? FolderItem ?: return null
        if (app !in folder.apps) return null
        val shrunk = folder.copy(apps = folder.apps.filterNot { it == app })
        val dissolved = dissolveIfSmall(shrunk)
        val without = if (dissolved == null) removeItem(layout, folderId) else replaceItem(layout, folderId, dissolved)
        val newItem = AppItem(newId, app, Placement(0, 0))
        val onPage = without.pages.getOrNull(page)?.let { items -> findFree(items, cols, rows)?.let { page to it } }
        return if (onPage != null) {
            withPage(without, onPage.first, without.pages[onPage.first] + newItem.withPlacement(onPage.second))
        } else {
            addToFirstFree(without, newItem, cols, rows)
        }
    }

    fun renameFolder(layout: HomeLayout, folderId: String, name: String): HomeLayout {
        val folder = findItem(layout, folderId) as? FolderItem ?: return layout
        return replaceItem(layout, folderId, folder.copy(name = name))
    }

    fun reorderFolder(layout: HomeLayout, folderId: String, apps: List<AppKey>): HomeLayout {
        val folder = findItem(layout, folderId) as? FolderItem ?: return layout
        return replaceItem(layout, folderId, folder.copy(apps = apps))
    }

    // ---- pages -----------------------------------------------------------------------------

    fun addPage(layout: HomeLayout): HomeLayout? =
        if (layout.pages.size >= HomeLayout.MAX_PAGES) null else layout.copy(pages = layout.pages + listOf(emptyList()))

    /** Drops empty trailing pages (always keeps at least one page). */
    fun trimPages(layout: HomeLayout): HomeLayout {
        var pages = layout.pages
        while (pages.size > 1 && pages.last().isEmpty()) pages = pages.dropLast(1)
        if (pages.isEmpty()) pages = listOf(emptyList())
        return layout.copy(pages = pages)
    }

    // ---- normalising -----------------------------------------------------------------------

    /**
     * Makes any layout valid for a cols x rows grid (used after loading, restoring a backup, or
     * changing the grid size): clamps spans, fixes dock slots, resolves overlaps by relocating
     * items to the first free spot (adding pages up to the limit), then trims empty pages.
     * Items that cannot be placed anywhere are dropped.
     */
    fun normalize(layout: HomeLayout, cols: Int, rows: Int): HomeLayout {
        val keptPages = ArrayList<List<HomeItem>>()
        val overflow = ArrayList<HomeItem>()

        for (page in layout.pages.take(HomeLayout.MAX_PAGES)) {
            val accepted = ArrayList<HomeItem>()
            val ordered = page.sortedWith(compareBy({ it.placement.row }, { it.placement.col }))
            for (item in ordered) {
                val clamped = item.placement.copy(
                    spanX = item.placement.spanX.coerceIn(1, cols),
                    spanY = item.placement.spanY.coerceIn(1, rows),
                )
                val ok = clamped.col >= 0 && clamped.row >= 0 && fits(clamped, cols, rows) && isFree(accepted, clamped)
                if (ok) {
                    accepted += item.withPlacement(clamped)
                } else {
                    val spot = findFree(accepted, cols, rows, clamped.spanX, clamped.spanY)
                    if (spot != null) accepted += item.withPlacement(spot) else overflow += item.withPlacement(clamped)
                }
            }
            keptPages += accepted
        }
        if (keptPages.isEmpty()) keptPages += emptyList<HomeItem>()

        val dockSlots = HashSet<Int>()
        val dock = ArrayList<HomeItem>()
        for (item in layout.dock.sortedBy { it.placement.col }) {
            val dockable = item is AppItem || item is FolderItem
            val slot = item.placement.col
            if (dockable && slot in 0 until HomeLayout.DOCK_SLOTS && dockSlots.add(slot)) {
                dock += item.withPlacement(Placement(slot, 0))
            } else if (dockable) {
                val free = (0 until HomeLayout.DOCK_SLOTS).firstOrNull { it !in dockSlots }
                if (free != null) {
                    dockSlots += free
                    dock += item.withPlacement(Placement(free, 0))
                } else {
                    overflow += item
                }
            } else {
                overflow += item
            }
        }

        var result = HomeLayout(keptPages, dock)
        for (item in overflow) {
            result = addToFirstFree(result, item, cols, rows) ?: result
        }
        return trimPages(result)
    }

    // ---- helpers ---------------------------------------------------------------------------

    const val DOCK = -1

    private fun withPage(layout: HomeLayout, index: Int, items: List<HomeItem>): HomeLayout =
        layout.copy(pages = layout.pages.mapIndexed { i, p -> if (i == index) items else p })

    private fun replaceItem(layout: HomeLayout, id: String, replacement: HomeItem): HomeLayout = layout.copy(
        pages = layout.pages.map { page -> page.map { if (it.id == id) replacement else it } },
        dock = layout.dock.map { if (it.id == id) replacement else it },
    )
}
