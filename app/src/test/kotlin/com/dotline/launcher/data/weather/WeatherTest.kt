package com.dotline.launcher.data.weather

import com.dotline.launcher.data.WeatherUnit
import com.dotline.launcher.data.WorldClock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WeatherTest {
    private val forecast = """
        {"latitude":51.5,"longitude":-0.12,"current":{"time":"2026-10-09T10:00","temperature_2m":14.6,"apparent_temperature":12.9,"is_day":1,"weather_code":3},
         "daily":{"time":["2026-10-09"],"temperature_2m_max":[17.2],"temperature_2m_min":[9.4]}}
    """.trimIndent()

    @Test
    fun parsesForecast() {
        val s = assertNotNull(WeatherParser.parseForecast(forecast, "London", 123L))
        assertEquals(14.6, s.tempC)
        assertEquals(WeatherKind.CLOUDY, s.kind)
        assertEquals(17.2, s.highC)
        assertEquals(9.4, s.lowC)
        assertEquals("London", s.place)
    }

    @Test
    fun badForecastIsNull() {
        assertNull(WeatherParser.parseForecast("{}", "x", 0))
        assertNull(WeatherParser.parseForecast("not json", "x", 0))
    }

    @Test
    fun parsesGeocoding() {
        val g = assertNotNull(WeatherParser.parseGeocoding("""{"results":[{"name":"Sydney","country":"Australia","latitude":-33.87,"longitude":151.21}]}"""))
        assertEquals("Sydney", g.name)
        assertEquals(-33.87, g.lat)
        assertNull(WeatherParser.parseGeocoding("""{"generationtime_ms":0.5}"""))
    }

    @Test
    fun wmoMapping() {
        assertEquals(WeatherKind.CLEAR_NIGHT, WeatherFormat.kindFromWmo(0, false))
        assertEquals(WeatherKind.RAIN, WeatherFormat.kindFromWmo(81, true))
        assertEquals(WeatherKind.THUNDER, WeatherFormat.kindFromWmo(99, true))
        assertEquals(WeatherKind.UNKNOWN, WeatherFormat.kindFromWmo(1234, true))
    }

    @Test
    fun temperatureUnits() {
        assertEquals("33°", WeatherFormat.degrees(33.2, WeatherUnit.CELSIUS))
        assertEquals("92°", WeatherFormat.degrees(33.4, WeatherUnit.FAHRENHEIT))
        assertEquals("-3°", WeatherFormat.degrees(-2.6, WeatherUnit.CELSIUS))
    }

    @Test
    fun snapshotRoundTrip() {
        val s = assertNotNull(WeatherParser.parseForecast(forecast, "London", 5L))
        assertEquals(s, WeatherParser.decode(WeatherParser.encode(s)))
    }

    @Test
    fun worldClockOffsetsAndLabels() {
        val now = Instant.parse("2026-10-09T20:30:00Z")
        val e = WorldClock.entries(now, ZoneId.of("UTC"), listOf("America/New_York", "Asia/Tokyo", "Bogus/Zone", "Asia/Kolkata"), true)
        assertEquals(3, e.size)
        assertEquals("NEW YORK", e[0].label)
        assertEquals("16:30", e[0].time)
        assertEquals(0, e[0].dayOffset)
        assertEquals("05:30", e[1].time)
        assertEquals(1, e[1].dayOffset)
        assertEquals(5.5, e[2].hoursFromLocal)
        val e12 = WorldClock.entries(now, ZoneId.of("UTC"), listOf("America/New_York"), false)
        assertEquals("4:30 PM", e12[0].time)
    }
}
