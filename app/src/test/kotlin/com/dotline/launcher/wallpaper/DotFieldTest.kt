package com.dotline.launcher.wallpaper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DotFieldTest {
    private val w = 1080
    private val h = 2400

    @Test
    fun everyPresetProducesInBoundsDots() {
        assertEquals(24, WallpaperPresets.all.size)
        assertEquals(24, WallpaperPresets.all.map { it.id }.toSet().size, "unique preset ids")
        for (preset in WallpaperPresets.all) {
            val set = DotField.generate(preset.spec, w, h, "09:41")
            assertTrue(set.size > 0, "${preset.id} has dots")
            assertTrue(set.size < 120_000, "${preset.id} stays drawable: ${set.size}")
            for (i in 0 until set.size) {
                assertTrue(set.x[i] > -60f && set.x[i] < w + 60f, "${preset.id} x in bounds")
                assertTrue(set.y[i] > -60f && set.y[i] < h + 60f, "${preset.id} y in bounds")
                assertTrue(set.r[i] > 0f && set.r[i] <= w * 0.1f, "${preset.id} radius sane ${set.r[i]}")
            }
        }
    }

    @Test
    fun deterministicPerSeedAndDiffersAcrossSeeds() {
        val base = WallpaperSpec(WallpaperPattern.HALFTONE, seed = 5)
        val a = DotField.generate(base, w, h)
        val b = DotField.generate(base, w, h)
        assertTrue(a.r.contentEquals(b.r))
        val c = DotField.generate(base.copy(seed = 6), w, h)
        assertTrue(!a.r.contentEquals(c.r))
        val g1 = DotField.generate(WallpaperSpec(WallpaperPattern.DOT_GRID, seed = 1), w, h)
        val g2 = DotField.generate(WallpaperSpec(WallpaperPattern.DOT_GRID, seed = 2), w, h)
        assertTrue(!g1.r.contentEquals(g2.r), "grid jitter depends on seed")
    }

    @Test
    fun scaleInvariantLayout() {
        val spec = WallpaperSpec(WallpaperPattern.DOT_GRID)
        val big = DotField.generate(spec, 1080, 2400)
        val small = DotField.generate(spec, 270, 600)
        assertEquals(big.size, small.size, "preview has the same dot structure as the full-size render")
    }

    @Test
    fun sizeAndSpacingControlsWork() {
        val dense = DotField.generate(WallpaperSpec(WallpaperPattern.DOT_GRID, spacing = 0.02f), w, h)
        val sparse = DotField.generate(WallpaperSpec(WallpaperPattern.DOT_GRID, spacing = 0.08f), w, h)
        assertTrue(dense.size > sparse.size * 8)
        val small = DotField.generate(WallpaperSpec(WallpaperPattern.DOT_GRID, dotSize = 0.2f), w, h)
        val large = DotField.generate(WallpaperSpec(WallpaperPattern.DOT_GRID, dotSize = 0.9f), w, h)
        assertTrue(DotField.maxRadius(large) > DotField.maxRadius(small) * 3)
    }

    @Test
    fun gradientShrinksAlongDirection() {
        val set = DotField.generate(WallpaperSpec(WallpaperPattern.DOT_GRADIENT, seed = 0), w, h)
        val topR = (0 until set.size).filter { set.y[it] < h * 0.2f }.map { set.r[it] }.average()
        val bottomR = (0 until set.size).filter { set.y[it] > h * 0.8f }.map { set.r[it] }.average().let { if (it.isNaN()) 0.0 else it }
        assertTrue(topR > bottomR, "top $topR > bottom $bottomR")
    }

    @Test
    fun redDotIsExactlyOneAccentDot() {
        val set = DotField.generate(WallpaperSpec(WallpaperPattern.RED_DOT), w, h)
        assertEquals(1, set.accent.count { it })
        assertEquals(1, set.size)
    }

    @Test
    fun textRendersTimeOrWord() {
        val time = DotField.generate(WallpaperSpec(WallpaperPattern.DOT_TEXT), w, h, "10:08")
        val word = DotField.generate(WallpaperSpec(WallpaperPattern.DOT_TEXT, text = "hello"), w, h)
        assertNotEquals(time.size, word.size)
        assertTrue(time.size > 400)
        val empty = DotField.generate(WallpaperSpec(WallpaperPattern.DOT_TEXT, text = "A VERY LONG CUSTOM WORD INDEED"), w, h)
        for (i in 0 until empty.size) assertTrue(empty.x[i] > -60f && empty.x[i] < w + 60f)
    }

    @Test
    fun invertSwapsColours() {
        val s = WallpaperSpec(color = 0xFFFFFFFF.toInt(), background = 0xFF000000.toInt(), invert = true)
        assertEquals(0xFF000000.toInt(), s.effectiveColor)
        assertEquals(0xFFFFFFFF.toInt(), s.effectiveBackground)
    }
}
