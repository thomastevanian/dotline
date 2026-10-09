package com.dotline.launcher.data.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import com.dotline.launcher.core.CrashLog
import com.dotline.launcher.data.SettingsRepository
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Weather from Open-Meteo (free, no key). The ONLY network use in the app.
 *
 * Battery rules: never fetches in the background. [refreshIfStale] is called when the launcher
 * comes to the foreground and is a no-op while the cached value is younger than 30 minutes.
 * Location is optional: a typed city is geocoded once and its coordinates saved in settings; the
 * device location is only read (last known, coarse, never actively requested) if the user opted in.
 */
class WeatherRepository(
    context: Context,
    private val scope: CoroutineScope,
    private val settings: SettingsRepository,
) {
    private val appContext = context.applicationContext
    private val cacheFile = File(appContext.filesDir, "weather.json")
    private val mutex = Mutex()
    private val _weather = MutableStateFlow<WeatherSnapshot?>(null)
    val weather: StateFlow<WeatherSnapshot?> = _weather.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    /** Short human-readable reason when the last refresh failed (shown as a hint, never a crash). */
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            val cached = runCatching { WeatherParser.decode(JSONObject(cacheFile.readText())) }.getOrNull()
            if (cached != null && _weather.value == null) _weather.value = cached
        }
    }

    /** Call when the launcher becomes visible. Cheap when the cache is fresh. */
    fun refreshIfStale(force: Boolean = false) {
        scope.launch(Dispatchers.IO) {
            mutex.withLock {
                val current = _weather.value
                val now = System.currentTimeMillis()
                if (!force && current != null && now - current.fetchedAt < MAX_AGE_MS && sameTarget(current)) return@withLock
                runCatching { fetch(now) }.onFailure {
                    _error.value = "Weather unavailable"
                    if (it !is java.io.IOException) CrashLog.record("weather", it)
                }
            }
        }
    }

    private fun sameTarget(current: WeatherSnapshot): Boolean {
        val s = settings.settings.value
        return s.weatherUseLocation || current.place.equals(s.weatherCity, ignoreCase = true) || s.weatherCity.isBlank()
    }

    private fun fetch(now: Long) {
        val s = settings.settings.value
        var lat = s.weatherLat
        var lon = s.weatherLon
        var place = s.weatherCity

        if (s.weatherUseLocation) {
            val loc = lastKnownCoarseLocation()
            if (loc != null) { lat = loc.latitude; lon = loc.longitude; place = "" }
        }
        if ((lat.isNaN() || lon.isNaN()) && s.weatherCity.isNotBlank()) {
            val geo = geocode(s.weatherCity) ?: run { _error.value = "City not found"; return }
            lat = geo.lat; lon = geo.lon; place = geo.name
            settings.update { it.copy(weatherLat = geo.lat, weatherLon = geo.lon, weatherCity = geo.name) }
        }
        if (lat.isNaN() || lon.isNaN()) { _error.value = "Set a city in settings"; return }

        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,apparent_temperature,weather_code,is_day" +
            "&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1"
        val body = httpGet(url)
        val snapshot = WeatherParser.parseForecast(body, place, now) ?: run { _error.value = "Weather unavailable"; return }
        _weather.value = snapshot
        _error.value = null
        runCatching { cacheFile.writeText(WeatherParser.encode(snapshot).toString()) }
    }

    /** Resolves a typed city name to coordinates (used by settings to validate input). */
    suspend fun lookupCity(name: String): GeoPlace? = withContext(Dispatchers.IO) {
        runCatching { geocode(name) }.getOrNull()
    }

    private fun geocode(name: String): GeoPlace? {
        val q = URLEncoder.encode(name.trim(), "UTF-8")
        val body = httpGet("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1&language=en&format=json")
        return WeatherParser.parseGeocoding(body)
    }

    private fun lastKnownCoarseLocation(): Location? {
        val granted = appContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!granted) return null
        val lm = appContext.getSystemService(LocationManager::class.java) ?: return null
        return runCatching {
            listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
                .mapNotNull { p -> if (lm.isProviderEnabled(p) || p == LocationManager.PASSIVE_PROVIDER) lm.getLastKnownLocation(p) else null }
                .maxByOrNull { it.time }
        }.getOrNull()
    }

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Dotline-Launcher")
            if (conn.responseCode !in 200..299) throw java.io.IOException("HTTP " + conn.responseCode)
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val MAX_AGE_MS = 30 * 60 * 1000L
    }
}
