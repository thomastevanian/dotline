package com.dotline.launcher.data.weather

import androidx.compose.runtime.Immutable
import com.dotline.launcher.data.WeatherUnit
import org.json.JSONObject
import kotlin.math.roundToInt

/** Coarse condition buckets used to pick a line icon and a short dot-matrix label. */
enum class WeatherKind(val label: String) {
    CLEAR_DAY("CLEAR"),
    CLEAR_NIGHT("CLEAR"),
    PARTLY_CLOUDY("PARTLY CLOUDY"),
    CLOUDY("CLOUDY"),
    FOG("FOG"),
    DRIZZLE("DRIZZLE"),
    RAIN("RAIN"),
    SNOW("SNOW"),
    THUNDER("STORM"),
    UNKNOWN("--"),
}

@Immutable
data class WeatherSnapshot(
    val tempC: Double,
    val feelsLikeC: Double,
    val highC: Double,
    val lowC: Double,
    val kind: WeatherKind,
    val place: String,
    /** Epoch millis when this was fetched. */
    val fetchedAt: Long,
)

@Immutable
data class GeoPlace(val name: String, val country: String, val lat: Double, val lon: Double)

object WeatherFormat {
    fun kindFromWmo(code: Int, isDay: Boolean): WeatherKind = when (code) {
        0, 1 -> if (isDay) WeatherKind.CLEAR_DAY else WeatherKind.CLEAR_NIGHT
        2 -> WeatherKind.PARTLY_CLOUDY
        3 -> WeatherKind.CLOUDY
        45, 48 -> WeatherKind.FOG
        51, 53, 55, 56, 57 -> WeatherKind.DRIZZLE
        61, 63, 65, 66, 67, 80, 81, 82 -> WeatherKind.RAIN
        71, 73, 75, 77, 85, 86 -> WeatherKind.SNOW
        95, 96, 99 -> WeatherKind.THUNDER
        else -> WeatherKind.UNKNOWN
    }

    fun convert(celsius: Double, unit: WeatherUnit): Int {
        val v = if (unit == WeatherUnit.FAHRENHEIT) celsius * 9.0 / 5.0 + 32.0 else celsius
        return v.roundToInt()
    }

    /** "33°" style text (no unit letter, as on the Nothing widget). */
    fun degrees(celsius: Double, unit: WeatherUnit): String = convert(celsius, unit).toString() + "°"
}

object WeatherParser {
    /** Parses an Open-Meteo forecast response; null when required fields are missing. */
    fun parseForecast(json: String, place: String, now: Long): WeatherSnapshot? = runCatching {
        val root = JSONObject(json)
        val current = root.getJSONObject("current")
        val daily = root.getJSONObject("daily")
        val temp = current.getDouble("temperature_2m")
        val feels = if (current.has("apparent_temperature")) current.getDouble("apparent_temperature") else temp
        val isDay = current.optInt("is_day", 1) == 1
        val code = current.optInt("weather_code", -1)
        val high = daily.getJSONArray("temperature_2m_max").getDouble(0)
        val low = daily.getJSONArray("temperature_2m_min").getDouble(0)
        WeatherSnapshot(temp, feels, high, low, WeatherFormat.kindFromWmo(code, isDay), place, now)
    }.getOrNull()

    /** Parses the first hit of an Open-Meteo geocoding response. */
    fun parseGeocoding(json: String): GeoPlace? = runCatching {
        val results = JSONObject(json).optJSONArray("results") ?: return@runCatching null
        val first = results.getJSONObject(0)
        GeoPlace(
            name = first.getString("name"),
            country = first.optString("country", ""),
            lat = first.getDouble("latitude"),
            lon = first.getDouble("longitude"),
        )
    }.getOrNull()

    fun encode(s: WeatherSnapshot): JSONObject = JSONObject().apply {
        put("t", s.tempC); put("f", s.feelsLikeC); put("h", s.highC); put("l", s.lowC)
        put("k", s.kind.name); put("p", s.place); put("at", s.fetchedAt)
    }

    fun decode(o: JSONObject): WeatherSnapshot? = runCatching {
        WeatherSnapshot(
            tempC = o.getDouble("t"), feelsLikeC = o.getDouble("f"), highC = o.getDouble("h"), lowC = o.getDouble("l"),
            kind = WeatherKind.entries.firstOrNull { it.name == o.optString("k") } ?: WeatherKind.UNKNOWN,
            place = o.optString("p", ""), fetchedAt = o.getLong("at"),
        )
    }.getOrNull()
}
