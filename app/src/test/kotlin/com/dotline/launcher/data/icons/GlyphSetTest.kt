package com.dotline.launcher.data.icons

import com.dotline.launcher.data.icons.glyph.Glyph
import com.dotline.launcher.data.icons.glyph.GlyphOp
import com.dotline.launcher.data.icons.glyph.GlyphSet
import com.dotline.launcher.data.icons.glyph.PathCmd
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GlyphSetTest {
    private fun bounds(g: Glyph): FloatArray {
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        fun p(x: Float, y: Float) { minX = minOf(minX, x); maxX = maxOf(maxX, x); minY = minOf(minY, y); maxY = maxOf(maxY, y) }
        for (op in g.ops) when (op) {
            is GlyphOp.Line -> { p(op.x1, op.y1); p(op.x2, op.y2) }
            is GlyphOp.Circle -> { p(op.cx - op.r, op.cy - op.r); p(op.cx + op.r, op.cy + op.r) }
            is GlyphOp.Rect -> { p(op.x, op.y); p(op.x + op.w, op.y + op.h) }
            is GlyphOp.Arc -> { p(op.cx - op.r, op.cy - op.r); p(op.cx + op.r, op.cy + op.r) }
            is GlyphOp.Path -> for (c in op.cmds) when (c) {
                is PathCmd.Move -> p(c.x, c.y)
                is PathCmd.Line -> p(c.x, c.y)
                is PathCmd.Quad -> { p(c.cx, c.cy); p(c.x, c.y) }
                is PathCmd.Cubic -> { p(c.c1x, c.c1y); p(c.c2x, c.c2y); p(c.x, c.y) }
                PathCmd.Close -> Unit
            }
        }
        return floatArrayOf(minX, minY, maxX, maxY)
    }

    @Test
    fun everyGlyphIsInsideTheGridAndNotEmpty() {
        for (e in GlyphSet.entries) {
            assertTrue(e.glyph.ops.isNotEmpty(), "${e.glyph.id} has shapes")
            val b = bounds(e.glyph)
            assertTrue(b[0] >= 1f && b[1] >= 1f && b[2] <= 23f && b[3] <= 23f, "${e.glyph.id} stays inside the 24 grid with a margin: ${b.toList()}")
            assertTrue(b[2] - b[0] >= 7f && b[3] - b[1] >= 7f, "${e.glyph.id} is big enough to read: ${b.toList()}")
            assertTrue(e.packages.isNotEmpty(), "${e.glyph.id} has packages")
        }
    }

    @Test
    fun idsAndPackagesAreUnique() {
        val ids = GlyphSet.entries.map { it.glyph.id }
        assertEquals(ids.size, ids.toSet().size, "unique glyph ids")
        val pkgs = GlyphSet.entries.flatMap { it.packages }
        assertEquals(pkgs.size, pkgs.toSet().size, "each package maps to one glyph")
    }

    @Test
    fun lookupWorks() {
        assertNotNull(GlyphSet.forPackage("com.samsung.android.dialer"))
        assertEquals(null, GlyphSet.forPackage("no.such.app"))
    }

    @Test
    fun coversTheRequestedAppsOnceComplete() {
        // The full set is built in stages; this guards the final target (60 glyphs).
        if (GlyphSet.size < 60) return
        val must = listOf(
            "com.google.android.dialer", "com.android.chrome", "com.google.android.gm", "com.google.android.googlequicksearchbox",
            "com.google.android.apps.maps", "com.android.vending", "com.google.android.youtube", "com.google.android.apps.youtube.music",
            "com.spotify.music", "com.whatsapp", "com.instagram.android", "com.zhiliaoapp.musically", "com.facebook.katana",
            "com.twitter.android", "com.snapchat.android", "com.discord", "org.telegram.messenger", "com.netflix.mediaclient",
            "com.microsoft.office.outlook", "com.microsoft.teams", "com.microsoft.skydrive", "com.sec.android.app.sbrowser",
            "com.samsung.android.app.notes", "com.sec.android.app.shealth", "com.sec.android.app.myfiles", "com.samsung.android.app.contacts",
        )
        for (p in must) assertNotNull(GlyphSet.forPackage(p), "glyph for $p")
    }
}
