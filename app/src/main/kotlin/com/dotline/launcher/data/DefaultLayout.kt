package com.dotline.launcher.data

import com.dotline.launcher.data.model.AppInfo
import com.dotline.launcher.data.model.AppItem
import com.dotline.launcher.data.model.AppKey
import com.dotline.launcher.data.model.BuiltinWidget
import com.dotline.launcher.data.model.HomeItem
import com.dotline.launcher.data.model.HomeLayout
import com.dotline.launcher.data.model.Placement
import com.dotline.launcher.data.model.WidgetItem

/** The first-run home screen: a clock widget, a few well-known apps, and a four-app dock. */
object DefaultLayout {
    /** Each inner list is a preference order; the first installed package wins. */
    private val dockPreferences: List<List<String>> = listOf(
        listOf("com.samsung.android.dialer", "com.google.android.dialer", "com.android.dialer"),
        listOf("com.samsung.android.messaging", "com.google.android.apps.messaging", "com.android.mms"),
        listOf("com.android.chrome", "com.sec.android.app.sbrowser", "org.mozilla.firefox"),
        listOf("com.sec.android.app.camera", "com.android.camera", "com.google.android.GoogleCamera"),
    )

    private val pagePreferences: List<List<String>> = listOf(
        listOf("com.sec.android.gallery3d", "com.google.android.apps.photos"),
        listOf("com.sec.android.app.clockpackage", "com.google.android.deskclock"),
        listOf("com.samsung.android.calendar", "com.google.android.calendar"),
        listOf("com.sec.android.app.popupcalculator", "com.google.android.calculator"),
        listOf("com.android.settings"),
        listOf("com.google.android.apps.maps"),
        listOf("com.android.vending"),
        listOf("com.google.android.youtube"),
        listOf("com.google.android.gm"),
        listOf("com.sec.android.app.myfiles", "com.google.android.apps.nbu.files"),
        listOf("com.samsung.android.app.contacts", "com.google.android.contacts"),
        listOf("com.samsung.android.app.notes", "com.google.android.keep"),
    )

    fun build(apps: List<AppInfo>, cols: Int, rows: Int, newId: () -> String): HomeLayout {
        val mine = apps.filter { !it.isWorkProfile }
        val byPackage = LinkedHashMap<String, AppInfo>()
        for (a in mine) byPackage.putIfAbsent(a.packageName, a)
        val used = HashSet<AppKey>()

        fun pick(options: List<String>): AppInfo? =
            options.firstNotNullOfOrNull { byPackage[it] }?.takeIf { used.add(it.key) }

        val dock = ArrayList<HomeItem>()
        var slot = 0
        for (options in dockPreferences) {
            val app = pick(options) ?: continue
            dock += AppItem(newId(), app.key, Placement(slot++, 0))
        }
        // Fewer than four preferred apps installed: fill with the first remaining apps.
        for (a in mine) {
            if (slot >= HomeLayout.DOCK_SLOTS) break
            if (used.add(a.key)) dock += AppItem(newId(), a.key, Placement(slot++, 0))
        }

        val page = ArrayList<HomeItem>()
        val clockRows = if (rows >= 5) 2 else 1
        page += WidgetItem(newId(), BuiltinWidget.CLOCK, Placement(0, 0, cols, clockRows))

        val ordered = ArrayList<AppInfo>()
        for (options in pagePreferences) pick(options)?.let { ordered += it }
        for (a in mine) if (used.add(a.key)) ordered += a

        var layout = HomeLayout(listOf(page), dock)
        for (a in ordered) {
            val next = LayoutEngine.addToFirstFree(layout, AppItem(newId(), a.key, Placement(0, 0)), cols, rows) ?: break
            layout = next
            // First page: the clock card plus a full block of apps under it; the rest go on the later pages.
            if (layout.pages.size > 1 || layout.pages[0].size >= 1 + cols * (rows - clockRows)) break
        }
        return LayoutEngine.normalize(withExtraPages(layout, apps, emptySet(), cols, rows, newId), cols, rows)
    }

    /**
     * Adds the pages after the first: a widgets page (weather, battery and date side by side, world
     * clock) and one page with the apps that are not on the home screen yet, so a fresh install can
     * be swiped through straight away. Nothing already on [layout] is moved or removed.
     */
    fun withExtraPages(
        layout: HomeLayout,
        apps: List<AppInfo>,
        hidden: Set<String>,
        cols: Int,
        rows: Int,
        newId: () -> String,
    ): HomeLayout {
        val pages = ArrayList(layout.pages)
        if (pages.size < HomeLayout.MAX_PAGES) pages += listOf(widgetsPage(cols, rows, newId))
        val placed = LayoutEngine.allApps(layout).toSet()
        val rest = apps
            .filter { !it.isWorkProfile && it.key !in placed && it.key.flat !in hidden && it.packageName !in hidden }
            .distinctBy { it.packageName }
            .take(cols * rows)
        if (rest.isNotEmpty() && pages.size < HomeLayout.MAX_PAGES) {
            pages += listOf(rest.mapIndexed { i, a -> AppItem(newId(), a.key, Placement(i % cols, i / cols)) })
        }
        return layout.copy(pages = pages)
    }

    /** Weather on top, battery next to the date, the world clock at the bottom. Fills a 5 x 6 grid exactly. */
    private fun widgetsPage(cols: Int, rows: Int, newId: () -> String): List<HomeItem> {
        val side = 2.coerceAtMost(cols - 1)
        return listOf(
            WidgetItem(newId(), BuiltinWidget.WEATHER, Placement(0, 0, cols, 2)),
            WidgetItem(newId(), BuiltinWidget.BATTERY, Placement(0, 2, side, 2)),
            WidgetItem(newId(), BuiltinWidget.DATE, Placement(side, 2, cols - side, 2)),
            WidgetItem(newId(), BuiltinWidget.WORLD_CLOCK, Placement(0, 4, cols, if (rows >= 6) 2 else 1)),
        )
    }
}
