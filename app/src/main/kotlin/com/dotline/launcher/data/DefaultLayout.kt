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
            // Keep the first-run page tidy: the preferred apps only, the rest live in the drawer.
            if (layout.pages.size > 1 || layout.pages[0].size >= 1 + cols * 2) break
        }
        return LayoutEngine.normalize(layout, cols, rows)
    }
}
