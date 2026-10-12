package com.dotline.launcher.data.icons.glyph

/**
 * Original vector pictograms for common apps, defined as plain geometry on a 24 x 24 grid.
 * Pure Kotlin (no Android types) so the same data is drawn by the Android renderer in the app
 * and by a Java2D renderer in tests. Strokes are drawn 1.8 units wide with round caps and joins.
 * The artwork is deliberately generic (a handset, a speech bubble, a lens...), never a copy of a
 * brand logo.
 */
sealed interface GlyphOp {
    data class Line(val x1: Float, val y1: Float, val x2: Float, val y2: Float) : GlyphOp
    data class Circle(val cx: Float, val cy: Float, val r: Float, val fill: Boolean) : GlyphOp
    data class Rect(val x: Float, val y: Float, val w: Float, val h: Float, val radius: Float, val fill: Boolean) : GlyphOp
    data class Arc(val cx: Float, val cy: Float, val r: Float, val startDeg: Float, val sweepDeg: Float) : GlyphOp
    data class Path(val cmds: List<PathCmd>, val fill: Boolean) : GlyphOp
}

sealed interface PathCmd {
    data class Move(val x: Float, val y: Float) : PathCmd
    data class Line(val x: Float, val y: Float) : PathCmd
    data class Quad(val cx: Float, val cy: Float, val x: Float, val y: Float) : PathCmd
    data class Cubic(val c1x: Float, val c1y: Float, val c2x: Float, val c2y: Float, val x: Float, val y: Float) : PathCmd
    data object Close : PathCmd
}

class Glyph(val id: String, val ops: List<GlyphOp>) {
    companion object {
        const val GRID = 24f
        const val STROKE = 1.8f
    }
}

/** A glyph plus the package names it is used for. */
class GlyphEntry(val glyph: Glyph, val packages: List<String>)

/** Builder DSL: `glyph("phone") { circle(12f, 12f, 8f); dot(12f, 12f) }`. */
class GlyphBuilder(private val id: String) {
    private val ops = ArrayList<GlyphOp>()

    fun line(x1: Float, y1: Float, x2: Float, y2: Float) { ops += GlyphOp.Line(x1, y1, x2, y2) }
    fun circle(cx: Float, cy: Float, r: Float, fill: Boolean = false) { ops += GlyphOp.Circle(cx, cy, r, fill) }
    /** Small filled dot. */
    fun dot(cx: Float, cy: Float, r: Float = 1.3f) { ops += GlyphOp.Circle(cx, cy, r, true) }
    fun rect(x: Float, y: Float, w: Float, h: Float, radius: Float = 0f, fill: Boolean = false) { ops += GlyphOp.Rect(x, y, w, h, radius, fill) }
    /** Arc of a circle, angles in degrees, 0 = 3 o'clock, positive sweep = clockwise. */
    fun arc(cx: Float, cy: Float, r: Float, startDeg: Float, sweepDeg: Float) { ops += GlyphOp.Arc(cx, cy, r, startDeg, sweepDeg) }
    /** Polyline / polygon from flat x,y pairs. */
    fun poly(vararg xy: Float, closed: Boolean = false, fill: Boolean = false) {
        require(xy.size >= 4 && xy.size % 2 == 0) { "poly needs x,y pairs" }
        val cmds = ArrayList<PathCmd>()
        cmds += PathCmd.Move(xy[0], xy[1])
        var i = 2
        while (i < xy.size) { cmds += PathCmd.Line(xy[i], xy[i + 1]); i += 2 }
        if (closed || fill) cmds += PathCmd.Close
        ops += GlyphOp.Path(cmds, fill)
    }
    /** Free-form path with curves. */
    fun path(fill: Boolean = false, block: PathBuilder.() -> Unit) {
        val b = PathBuilder()
        b.block()
        ops += GlyphOp.Path(b.cmds, fill)
    }

    fun build(): Glyph = Glyph(id, ops.toList())
}

class PathBuilder {
    internal val cmds = ArrayList<PathCmd>()
    fun move(x: Float, y: Float) { cmds += PathCmd.Move(x, y) }
    fun line(x: Float, y: Float) { cmds += PathCmd.Line(x, y) }
    fun quad(cx: Float, cy: Float, x: Float, y: Float) { cmds += PathCmd.Quad(cx, cy, x, y) }
    fun cubic(c1x: Float, c1y: Float, c2x: Float, c2y: Float, x: Float, y: Float) { cmds += PathCmd.Cubic(c1x, c1y, c2x, c2y, x, y) }
    fun close() { cmds += PathCmd.Close }
}

fun glyph(id: String, block: GlyphBuilder.() -> Unit): Glyph = GlyphBuilder(id).also(block).build()

/** `entry(glyph("phone") {...}, "com.android.dialer", "com.samsung.android.dialer")`. */
fun entry(glyph: Glyph, vararg packages: String): GlyphEntry = GlyphEntry(glyph, packages.toList())
