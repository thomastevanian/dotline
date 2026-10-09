package com.dotline.launcher.data.icons.glyph

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/** Draws a [Glyph] with Android's Canvas, scaled from the 24-unit grid into a square. */
object GlyphRenderer {
    /**
     * @param left,top top-left of the square the glyph is drawn into
     * @param size side of that square in px
     * @param color glyph colour (ARGB)
     */
    fun draw(canvas: Canvas, glyph: Glyph, left: Float, top: Float, size: Float, color: Int) {
        val s = size / Glyph.GRID
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = Glyph.STROKE * s
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        val rect = RectF()
        for (op in glyph.ops) {
            when (op) {
                is GlyphOp.Line -> canvas.drawLine(left + op.x1 * s, top + op.y1 * s, left + op.x2 * s, top + op.y2 * s, stroke)
                is GlyphOp.Circle -> canvas.drawCircle(left + op.cx * s, top + op.cy * s, op.r * s, if (op.fill) fill else stroke)
                is GlyphOp.Rect -> {
                    rect.set(left + op.x * s, top + op.y * s, left + (op.x + op.w) * s, top + (op.y + op.h) * s)
                    canvas.drawRoundRect(rect, op.radius * s, op.radius * s, if (op.fill) fill else stroke)
                }
                is GlyphOp.Arc -> {
                    rect.set(left + (op.cx - op.r) * s, top + (op.cy - op.r) * s, left + (op.cx + op.r) * s, top + (op.cy + op.r) * s)
                    canvas.drawArc(rect, op.startDeg, op.sweepDeg, false, stroke)
                }
                is GlyphOp.Path -> canvas.drawPath(toPath(op, left, top, s), if (op.fill) fill else stroke)
            }
        }
    }

    private fun toPath(op: GlyphOp.Path, left: Float, top: Float, s: Float): Path {
        val p = Path()
        for (c in op.cmds) {
            when (c) {
                is PathCmd.Move -> p.moveTo(left + c.x * s, top + c.y * s)
                is PathCmd.Line -> p.lineTo(left + c.x * s, top + c.y * s)
                is PathCmd.Quad -> p.quadTo(left + c.cx * s, top + c.cy * s, left + c.x * s, top + c.y * s)
                is PathCmd.Cubic -> p.cubicTo(left + c.c1x * s, top + c.c1y * s, left + c.c2x * s, top + c.c2y * s, left + c.x * s, top + c.y * s)
                PathCmd.Close -> p.close()
            }
        }
        return p
    }
}
