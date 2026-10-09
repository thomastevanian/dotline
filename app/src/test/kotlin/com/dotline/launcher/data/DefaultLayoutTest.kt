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
        assertEquals(1, layout.pages.size)
        assertTrue(layout.dock.isEmpty())
        assertEquals(HomeLayout.MAX_PAGES, 7)
    }
}
