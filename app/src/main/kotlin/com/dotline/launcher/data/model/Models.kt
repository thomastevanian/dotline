package com.dotline.launcher.data.model

import androidx.compose.runtime.Immutable

/**
 * Stable identity of a launchable activity for one user profile.
 * [userSerial] is UserManager's serial number (persistable, unlike UserHandle).
 */
@Immutable
data class AppKey(
    val packageName: String,
    val className: String,
    val userSerial: Long,
) {
    /** Compact, reversible string form used in layout JSON and as cache key. */
    val flat: String get() = "$packageName/$className#$userSerial"

    companion object {
        fun parse(s: String): AppKey? {
            val hash = s.lastIndexOf('#')
            val slash = s.indexOf('/')
            if (hash < 0 || slash < 0 || slash > hash) return null
            val serial = s.substring(hash + 1).toLongOrNull() ?: return null
            return AppKey(s.substring(0, slash), s.substring(slash + 1, hash), serial)
        }
    }
}

@Immutable
data class AppInfo(
    val key: AppKey,
    val label: String,
    val isWorkProfile: Boolean,
    val isSuspended: Boolean,
    /** Changes whenever the package is updated; part of the icon cache key. */
    val versionStamp: Long,
    val installTime: Long,
) {
    val packageName: String get() = key.packageName
    val sortKey: String = label.lowercase()
}

/** Where an item sits on a home page. Spans are in grid cells. */
@Immutable
data class Placement(val col: Int, val row: Int, val spanX: Int = 1, val spanY: Int = 1) {
    fun contains(c: Int, r: Int): Boolean = c >= col && c < col + spanX && r >= row && r < row + spanY
    fun overlaps(o: Placement): Boolean =
        col < o.col + o.spanX && o.col < col + spanX && row < o.row + o.spanY && o.row < row + spanY
}

enum class BuiltinWidget(val defaultSpanX: Int, val defaultSpanY: Int) {
    CLOCK(4, 2),
    DATE(2, 1),
    BATTERY(2, 2),
    WEATHER(4, 2),
    CALENDAR(4, 2),
    WORLD_CLOCK(4, 2),
    NOTES(2, 2),
}

/** Something that lives on a home page or in the dock. */
sealed interface HomeItem {
    val id: String
    val placement: Placement
    fun withPlacement(p: Placement): HomeItem
}

@Immutable
data class AppItem(
    override val id: String,
    val app: AppKey,
    override val placement: Placement,
) : HomeItem {
    override fun withPlacement(p: Placement): HomeItem = copy(placement = p)
}

@Immutable
data class FolderItem(
    override val id: String,
    val name: String,
    val apps: List<AppKey>,
    override val placement: Placement,
) : HomeItem {
    override fun withPlacement(p: Placement): HomeItem = copy(placement = p)
}

/** One of Dotline's own Compose widgets. */
@Immutable
data class WidgetItem(
    override val id: String,
    val kind: BuiltinWidget,
    override val placement: Placement,
    /** Free-form per-widget config, e.g. world-clock city ids. */
    val config: String = "",
) : HomeItem {
    override fun withPlacement(p: Placement): HomeItem = copy(placement = p)
}

/** A normal Android app widget bound through AppWidgetHost. */
@Immutable
data class HostedWidgetItem(
    override val id: String,
    val appWidgetId: Int,
    val provider: String,
    override val placement: Placement,
) : HomeItem {
    override fun withPlacement(p: Placement): HomeItem = copy(placement = p)
}

/**
 * The whole home screen: up to [MAX_PAGES] pages plus the dock.
 * Dock items use [Placement.col] as the slot index (0 until [DOCK_SLOTS]); row is always 0.
 */
@Immutable
data class HomeLayout(
    val pages: List<List<HomeItem>> = listOf(emptyList()),
    val dock: List<HomeItem> = emptyList(),
) {
    companion object {
        const val MAX_PAGES = 7
        const val DOCK_SLOTS = 4
    }
}
