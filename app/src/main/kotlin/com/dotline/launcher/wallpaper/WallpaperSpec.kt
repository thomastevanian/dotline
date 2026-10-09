package com.dotline.launcher.wallpaper

import androidx.compose.runtime.Immutable

enum class WallpaperPattern(val title: String) {
    DOT_GRID("Dot grid"),
    DOT_GRADIENT("Dot gradient"),
    CONCENTRIC("Concentric"),
    DOT_TEXT("Dot text"),
    HALFTONE("Halftone"),
    RED_DOT("Single dot"),
}

/**
 * Everything that defines a wallpaper. All sizes are relative to the screen width so the very
 * same spec renders identically as a small preview and at the phone's exact resolution.
 */
@Immutable
data class WallpaperSpec(
    val pattern: WallpaperPattern = WallpaperPattern.DOT_GRID,
    /** Distance between dot centres as a fraction of screen width (0.012 .. 0.09). */
    val spacing: Float = 0.04f,
    /** Dot diameter as a fraction of the spacing (0.15 .. 1.0). */
    val dotSize: Float = 0.55f,
    /** ARGB dot colour. */
    val color: Int = 0xFFFFFFFF.toInt(),
    /** ARGB background colour. */
    val background: Int = 0xFF000000.toInt(),
    val seed: Long = 7L,
    /** Swaps dot and background colours. */
    val invert: Boolean = false,
    /** For [WallpaperPattern.DOT_TEXT]: custom word; empty means the current time. */
    val text: String = "",
    /** Accent for the RED_DOT pattern (and the single accent dot elsewhere). */
    val accent: Int = 0xFFD71921.toInt(),
) {
    val effectiveColor: Int get() = if (invert) background else color
    val effectiveBackground: Int get() = if (invert) color else background
}

/** A named, ready-made spec shown in the preset strip. */
@Immutable
data class WallpaperPreset(val id: String, val name: String, val spec: WallpaperSpec)

object WallpaperPresets {
    private const val BLACK = 0xFF000000.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val GREY = 0xFF8A8A8A.toInt()
    private const val LIGHT = 0xFFE3E3E3.toInt()
    private const val RED = 0xFFD71921.toInt()

    /** 24 presets: 6 patterns x (dark, light, grey/red variants). */
    val all: List<WallpaperPreset> = buildList {
        fun p(id: String, name: String, spec: WallpaperSpec) = add(WallpaperPreset(id, name, spec))
        p("grid_dark", "Grid", WallpaperSpec(WallpaperPattern.DOT_GRID, 0.05f, 0.5f))
        p("grid_fine", "Fine grid", WallpaperSpec(WallpaperPattern.DOT_GRID, 0.025f, 0.45f, GREY))
        p("grid_light", "Grid light", WallpaperSpec(WallpaperPattern.DOT_GRID, 0.05f, 0.5f, BLACK, LIGHT))
        p("grid_bold", "Bold grid", WallpaperSpec(WallpaperPattern.DOT_GRID, 0.08f, 0.8f))
        p("grad_down", "Fade", WallpaperSpec(WallpaperPattern.DOT_GRADIENT, 0.035f, 0.95f, seed = 0))
        p("grad_up", "Rise", WallpaperSpec(WallpaperPattern.DOT_GRADIENT, 0.035f, 0.95f, seed = 1))
        p("grad_radial", "Bloom", WallpaperSpec(WallpaperPattern.DOT_GRADIENT, 0.03f, 0.95f, seed = 4))
        p("grad_light", "Fade light", WallpaperSpec(WallpaperPattern.DOT_GRADIENT, 0.035f, 0.95f, BLACK, LIGHT, seed = 0))
        p("ring_center", "Rings", WallpaperSpec(WallpaperPattern.CONCENTRIC, 0.045f, 0.55f, seed = 0))
        p("ring_off", "Orbit", WallpaperSpec(WallpaperPattern.CONCENTRIC, 0.04f, 0.5f, seed = 3))
        p("ring_light", "Rings light", WallpaperSpec(WallpaperPattern.CONCENTRIC, 0.045f, 0.55f, BLACK, LIGHT, seed = 0))
        p("ring_fine", "Ripple", WallpaperSpec(WallpaperPattern.CONCENTRIC, 0.028f, 0.5f, GREY, seed = 5))
        p("text_time", "Time", WallpaperSpec(WallpaperPattern.DOT_TEXT, 0.03f, 0.7f))
        p("text_nothing", "Dotline", WallpaperSpec(WallpaperPattern.DOT_TEXT, 0.03f, 0.7f, text = "DOTLINE"))
        p("text_light", "Time light", WallpaperSpec(WallpaperPattern.DOT_TEXT, 0.03f, 0.7f, BLACK, LIGHT))
        p("text_hello", "Hello", WallpaperSpec(WallpaperPattern.DOT_TEXT, 0.03f, 0.7f, text = "HELLO"))
        p("half_a", "Halftone", WallpaperSpec(WallpaperPattern.HALFTONE, 0.03f, 0.95f, seed = 11))
        p("half_b", "Halftone 2", WallpaperSpec(WallpaperPattern.HALFTONE, 0.025f, 0.95f, seed = 23))
        p("half_light", "Halftone light", WallpaperSpec(WallpaperPattern.HALFTONE, 0.03f, 0.95f, BLACK, LIGHT, seed = 11))
        p("half_grey", "Smoke", WallpaperSpec(WallpaperPattern.HALFTONE, 0.02f, 0.95f, GREY, seed = 42))
        p("red_dot", "One dot", WallpaperSpec(WallpaperPattern.RED_DOT, 0.05f, 0.5f, seed = 0))
        p("red_low", "Low dot", WallpaperSpec(WallpaperPattern.RED_DOT, 0.05f, 0.5f, seed = 2))
        p("red_light", "One dot light", WallpaperSpec(WallpaperPattern.RED_DOT, 0.05f, 0.5f, BLACK, LIGHT, seed = 0))
        p("red_grid", "Dot and grid", WallpaperSpec(WallpaperPattern.RED_DOT, 0.03f, 0.35f, GREY, seed = 1))
    }
}
