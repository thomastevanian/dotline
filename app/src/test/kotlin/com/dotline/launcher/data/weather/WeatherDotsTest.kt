package com.dotline.launcher.data.weather

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeatherDotsTest {
    @Test
    fun everyIconIsAFullGridWithLitDots() {
        for (kind in WeatherKind.entries) {
            val rows = WeatherDots.rows(kind)
            assertEquals(WeatherDots.ROWS, rows.size, "$kind rows")
            for (r in rows) {
                assertEquals(WeatherDots.COLS, r.length, "$kind row width")
                assertTrue(r.all { it == '#' || it == '.' }, "$kind only # and .")
            }
            assertTrue(rows.sumOf { row -> row.count { it == '#' } } >= 4, "$kind has dots")
        }
    }
}
