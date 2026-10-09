package com.dotline.launcher.data

import androidx.compose.runtime.Immutable
import org.json.JSONObject

enum class ThemeMode { DARK, LIGHT, SYSTEM }
enum class ClockStyle { DOT, NORMAL }
enum class TimeFormat { SYSTEM, H12, H24 }
enum class IconStyle { MONOCHROME, ORIGINAL, MONOCHROME_ACCENT }
enum class IconShape { CIRCLE, ROUNDED_SQUARE }
enum class WeatherUnit { CELSIUS, FAHRENHEIT }
enum class GestureAction { NONE, OPEN_DRAWER, OPEN_NOTIFICATIONS, EDIT_MODE, LOCK_SCREEN }

/**
 * Every user-facing setting, immutable. Persisted as one JSON blob (see [SettingsJson]) so that
 * backup/restore and DataStore storage share a single code path and unknown keys never break loading.
 */
@Immutable
data class Settings(
    // Home
    val gridColumns: Int = 5,
    val gridRows: Int = 6,
    val infiniteScroll: Boolean = false,
    val showDock: Boolean = true,
    /** The "Search" pill under the dock (opens the drawer with the keyboard), as on Nothing OS. */
    val showSearchBar: Boolean = true,
    val lockLayout: Boolean = false,
    /** 0f..0.6f black scrim over the wallpaper for legibility. */
    val wallpaperDim: Float = 0f,

    // Icons
    val iconStyle: IconStyle = IconStyle.MONOCHROME,
    val iconShape: IconShape = IconShape.CIRCLE,
    /** 0.7f..1.3f */
    val iconSize: Float = 1f,
    val showLabels: Boolean = true,
    val iconPack: String = "",
    val notificationDots: Boolean = false,

    // Clock & widgets
    val clockStyle: ClockStyle = ClockStyle.DOT,
    val timeFormat: TimeFormat = TimeFormat.SYSTEM,
    val hideClock: Boolean = false,
    val showDate: Boolean = true,
    val weatherCity: String = "",
    val weatherLat: Double = Double.NaN,
    val weatherLon: Double = Double.NaN,
    val weatherUseLocation: Boolean = false,
    val weatherUnit: WeatherUnit = WeatherUnit.CELSIUS,
    val worldClockZones: List<String> = listOf("America/New_York", "Asia/Tokyo"),

    // Drawer
    val drawerAutoKeyboard: Boolean = false,
    val drawerShowRecents: Boolean = false,
    val hiddenApps: Set<String> = emptySet(),
    val hiddenAppsBiometric: Boolean = false,

    // Gestures
    val swipeUp: GestureAction = GestureAction.OPEN_DRAWER,
    val swipeDown: GestureAction = GestureAction.OPEN_NOTIFICATIONS,
    val pinch: GestureAction = GestureAction.EDIT_MODE,
    /** OFF by default: needs an accessibility service. */
    val doubleTap: GestureAction = GestureAction.NONE,

    // Theme
    val themeMode: ThemeMode = ThemeMode.DARK,

    // App state
    val onboardingDone: Boolean = false,
    val defaultHomePrompted: Boolean = false,
)

/** JSON (de)serialisation for [Settings]. Missing or invalid keys fall back to defaults. */
object SettingsJson {
    private inline fun <reified E : Enum<E>> JSONObject.enum(key: String, default: E): E {
        val name = optString(key, "")
        return enumValues<E>().firstOrNull { it.name == name } ?: default
    }

    fun encode(s: Settings): JSONObject = JSONObject().apply {
        put("gridColumns", s.gridColumns)
        put("gridRows", s.gridRows)
        put("infiniteScroll", s.infiniteScroll)
        put("showDock", s.showDock)
        put("showSearchBar", s.showSearchBar)
        put("lockLayout", s.lockLayout)
        put("wallpaperDim", s.wallpaperDim.toDouble())
        put("iconStyle", s.iconStyle.name)
        put("iconShape", s.iconShape.name)
        put("iconSize", s.iconSize.toDouble())
        put("showLabels", s.showLabels)
        put("iconPack", s.iconPack)
        put("notificationDots", s.notificationDots)
        put("clockStyle", s.clockStyle.name)
        put("timeFormat", s.timeFormat.name)
        put("hideClock", s.hideClock)
        put("showDate", s.showDate)
        put("weatherCity", s.weatherCity)
        if (!s.weatherLat.isNaN()) put("weatherLat", s.weatherLat)
        if (!s.weatherLon.isNaN()) put("weatherLon", s.weatherLon)
        put("weatherUseLocation", s.weatherUseLocation)
        put("weatherUnit", s.weatherUnit.name)
        put("worldClockZones", org.json.JSONArray(s.worldClockZones))
        put("drawerAutoKeyboard", s.drawerAutoKeyboard)
        put("drawerShowRecents", s.drawerShowRecents)
        put("hiddenApps", org.json.JSONArray(s.hiddenApps.toList()))
        put("hiddenAppsBiometric", s.hiddenAppsBiometric)
        put("swipeUp", s.swipeUp.name)
        put("swipeDown", s.swipeDown.name)
        put("pinch", s.pinch.name)
        put("doubleTap", s.doubleTap.name)
        put("themeMode", s.themeMode.name)
        put("onboardingDone", s.onboardingDone)
        put("defaultHomePrompted", s.defaultHomePrompted)
    }

    fun decode(o: JSONObject): Settings {
        val d = Settings()
        fun strings(key: String, default: List<String>): List<String> {
            val arr = o.optJSONArray(key) ?: return default
            return List(arr.length()) { arr.optString(it, "") }.filter { it.isNotEmpty() }
        }
        return Settings(
            gridColumns = o.optInt("gridColumns", d.gridColumns).coerceIn(4, 6),
            gridRows = o.optInt("gridRows", d.gridRows).coerceIn(5, 7),
            infiniteScroll = o.optBoolean("infiniteScroll", d.infiniteScroll),
            showDock = o.optBoolean("showDock", d.showDock),
            showSearchBar = o.optBoolean("showSearchBar", d.showSearchBar),
            lockLayout = o.optBoolean("lockLayout", d.lockLayout),
            wallpaperDim = o.optDouble("wallpaperDim", d.wallpaperDim.toDouble()).toFloat().coerceIn(0f, 0.6f),
            iconStyle = o.enum("iconStyle", d.iconStyle),
            iconShape = o.enum("iconShape", d.iconShape),
            iconSize = o.optDouble("iconSize", d.iconSize.toDouble()).toFloat().coerceIn(0.7f, 1.3f),
            showLabels = o.optBoolean("showLabels", d.showLabels),
            iconPack = o.optString("iconPack", d.iconPack),
            notificationDots = o.optBoolean("notificationDots", d.notificationDots),
            clockStyle = o.enum("clockStyle", d.clockStyle),
            timeFormat = o.enum("timeFormat", d.timeFormat),
            hideClock = o.optBoolean("hideClock", d.hideClock),
            showDate = o.optBoolean("showDate", d.showDate),
            weatherCity = o.optString("weatherCity", d.weatherCity),
            weatherLat = if (o.has("weatherLat")) o.optDouble("weatherLat", Double.NaN) else Double.NaN,
            weatherLon = if (o.has("weatherLon")) o.optDouble("weatherLon", Double.NaN) else Double.NaN,
            weatherUseLocation = o.optBoolean("weatherUseLocation", d.weatherUseLocation),
            weatherUnit = o.enum("weatherUnit", d.weatherUnit),
            worldClockZones = strings("worldClockZones", d.worldClockZones),
            drawerAutoKeyboard = o.optBoolean("drawerAutoKeyboard", d.drawerAutoKeyboard),
            drawerShowRecents = o.optBoolean("drawerShowRecents", d.drawerShowRecents),
            hiddenApps = strings("hiddenApps", emptyList()).toSet(),
            hiddenAppsBiometric = o.optBoolean("hiddenAppsBiometric", d.hiddenAppsBiometric),
            swipeUp = o.enum("swipeUp", d.swipeUp),
            swipeDown = o.enum("swipeDown", d.swipeDown),
            pinch = o.enum("pinch", d.pinch),
            doubleTap = o.enum("doubleTap", d.doubleTap),
            themeMode = o.enum("themeMode", d.themeMode),
            onboardingDone = o.optBoolean("onboardingDone", d.onboardingDone),
            defaultHomePrompted = o.optBoolean("defaultHomePrompted", d.defaultHomePrompted),
        )
    }

    fun decode(text: String): Settings = decode(JSONObject(text))
}
