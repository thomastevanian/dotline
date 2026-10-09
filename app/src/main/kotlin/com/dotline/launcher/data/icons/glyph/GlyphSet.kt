package com.dotline.launcher.data.icons.glyph

/**
 * All built-in glyphs, matched by package name. Each category lives in its own file
 * (GlyphsSystem, GlyphsGoogle, GlyphsSocial, GlyphsMedia, GlyphsSamsung) so they can be edited independently.
 */
object GlyphSet {
    val entries: List<GlyphEntry> by lazy {
        GlyphsSystem.entries + GlyphsGoogle.entries + GlyphsSocial.entries + GlyphsMedia.entries + GlyphsSamsung.entries
    }

    private val byPackage: Map<String, Glyph> by lazy {
        val map = HashMap<String, Glyph>()
        for (e in entries) for (p in e.packages) map.putIfAbsent(p, e.glyph)
        map
    }

    /** The glyph for [packageName], or null when there is no hand-drawn one (use the silhouette instead). */
    fun forPackage(packageName: String): Glyph? = byPackage[packageName]

    val size: Int get() = entries.size
}
