package com.dotline.launcher.data

import com.dotline.launcher.data.model.AppInfo
import com.dotline.launcher.data.model.AppItem
import com.dotline.launcher.data.model.AppKey
import com.dotline.launcher.data.model.BuiltinWidget
import com.dotline.launcher.data.model.HomeLayout
import com.dotline.launcher.data.model.WidgetItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultLayoutTest {
    private fun info(pkg: String, work: Boolean = false) =
        AppInfo(AppKey(pkg, "$pkg.Main", if (work) 10L else 0L), pkg, work, false, 1L, 0L)

    private fun ids(): () -> String { var n = 0; return { "id${n++}" } }

    @Test
    fun dockPrefersDialerMessagesBrowserCamera() {
        val apps = listOf("com.android.chrome", "com.samsung.android.dialer", "com.sec.android.app.camera", "com.samsung.android.messaging", "x.other").map { info(it) }
        val layout = DefaultLayout.build(apps, 5, 6, ids())
        val dockPkgs = layout.dock.map { (it as AppItem).app.packageName }
        assertEquals(listOf("com.samsung.android.dialer", "com.samsung.android.messaging", "com.android.chrome", "com.sec.android.app.camera"), dockPkgs)
        assertEquals(listOf(0, 1, 2, 3), layout.dock.map { it.placement.col })
    }

    @Test
    fun firstPageHasClockWidgetAndNoDuplicates() {
        val apps = (1..30).map { info("app.n$it") } + info("com.android.settings") + info("work.app", work = true)
        val layout = DefaultLayout.build(apps, 5, 6, ids())
        assertTrue(layout.pages[0].any { it is WidgetItem && it.kind == BuiltinWidget.CLOCK })
        val keys = LayoutEngine.allApps(layout)
        val count = layout.pages.flatten().count { it is AppItem } + layout.dock.size
        assertEquals(count, keys.size, "no app placed twice")
        assertTrue(keys.none { it.userSerial == 10L }, "work profile apps are not auto-placed")
        assertTrue(layout.pages.all { page -> page.all { LayoutEngine.fits(it.placement, 5, 6) } })
    }

    @Test
    fun emptyDeviceStillYieldsValidLayout() {
        val layout = DefaultLayout.build(emptyList(), 5, 6, ids())
        // No apps: the clock page plus the widgets page, so the home screen can still be swiped.
        assertEquals(2, layout.pages.size)
        assertTrue(layout.dock.isEmpty())
        assertEquals(HomeLayout.MAX_PAGES, 7)
    }

    @Test
    fun freshInstallHasSeveralPagesToSwipeThrough() {
        val apps = (1..40).map { info("app.n$it") } + info("com.android.settings")
        val layout = DefaultLayout.build(apps, 5, 6, ids())
        assertEquals(3, layout.pages.size, "clock page, widgets page, more apps")
        val kinds = layout.pages[1].filterIsInstance<WidgetItem>().map { it.kind }
        assertTrue(BuiltinWidget.WEATHER in kinds && BuiltinWidget.BATTERY in kinds && BuiltinWidget.WORLD_CLOCK in kinds)
        assertTrue(layout.pages.all { page -> page.all { LayoutEngine.fits(it.placement, 5, 6) } })
        assertTrue(layout.pages[1].all { a -> layout.pages[1].none { b -> a !== b && a.placement.overlaps(b.placement) } }, "widgets do not overlap")
    }

    @Test
    fun extraPagesKeepWhatIsAlreadyPlacedAndSkipHiddenApps() {
        val one = HomeLayout(listOf(listOf(AppItem("a", AppKey("app.n1", "app.n1.Main", 0L), com.dotline.launcher.data.model.Placement(0, 0)))))
        val apps = (1..10).map { info("app.n$it") }
        val out = DefaultLayout.withExtraPages(one, apps, setOf("app.n2"), 5, 6, ids())
        assertEquals(one.pages[0], out.pages[0], "first page untouched")
        val placed = LayoutEngine.allApps(out).map { it.packageName }
        assertEquals(1, placed.count { it == "app.n1" }, "no duplicate of the app that was already on page one")
        assertTrue("app.n2" !in placed, "hidden apps are not added")
    }
}
