package com.dotline.launcher.widgets

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.dotline.launcher.ui.components.DotFont
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws dot-matrix content into bitmaps for RemoteViews (app widgets cannot run Compose).
 * Everything is flat: solid circles only, no gradients, shadows or blur.
 */
object WidgetBitmaps {
    private fun paint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }

    /** Width in px of [text] rendered with [pitch] px between dot centres. */
    fun textWidth(text: String, pitch: Float): Float =
        if (text.isEmpty()) 0f else text.length * DotFont.COLS * pitch + (text.length - 1) * pitch

    /** [text] as dot-matrix, [pitch] px between dots, dot diameter = pitch * [fill]. */
    fun dotText(text: String, pitch: Float, color: Int, fill: Float = 0.62f, padding: Int = 0): Bitmap {
        val w = (textWidth(text, pitch) + padding * 2).toInt().coerceAtLeast(1)
        val h = (DotFont.ROWS * pitch + padding * 2).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawText(canvas, text, padding.toFloat(), padding.toFloat(), pitch, paint(color), fill)
        return bmp
    }

    fun drawText(canvas: Canvas, text: String, left: Float, top: Float, pitch: Float, p: Paint, fill: Float = 0.62f) {
        val r = pitch * fill / 2f
        var x0 = left
        for (ch in text) {
            val rows = DotFont.rows(ch)
            for (row in 0 until DotFont.ROWS) for (col in 0 until DotFont.COLS) {
                if (DotFont.isOn(rows, row, col)) canvas.drawCircle(x0 + (col + 0.5f) * pitch, top + (row + 0.5f) * pitch, r, p)
            }
            x0 += (DotFont.COLS + 1) * pitch
        }
    }

    /**
     * Battery: a ring of [dots] dots, the first [percent] percent lit (clockwise from the top),
     * with the percentage in dot-matrix digits in the middle. [accent] marks a charging bolt dot.
     */
    fun batteryRing(percent: Int, charging: Boolean, sizePx: Int, lit: Int, unlit: Int, accent: Int, dots: Int = 40): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val c = sizePx / 2f
        val ringR = sizePx * 0.44f
        val dotR = sizePx * 0.022f
        val litCount = (percent.coerceIn(0, 100) * dots / 100f).toInt()
        val pLit = paint(lit)
        val pOff = paint(unlit)
        for (i in 0 until dots) {
            val a = -PI / 2 + 2 * PI * i / dots
            canvas.drawCircle(c + (ringR * cos(a)).toFloat(), c + (ringR * sin(a)).toFloat(), dotR, if (i < litCount) pLit else pOff)
        }
        val text = percent.coerceIn(0, 100).toString()
        val pitch = sizePx * 0.5f / textWidth(text, 1f).coerceAtLeast(1f)
        val tw = textWidth(text, pitch)
        drawText(canvas, text, c - tw / 2f, c - DotFont.ROWS * pitch / 2f, pitch, pLit, 0.7f)
        if (charging) canvas.drawCircle(c, c + sizePx * 0.30f, dotR * 1.6f, paint(accent))
        return bmp
    }
}
