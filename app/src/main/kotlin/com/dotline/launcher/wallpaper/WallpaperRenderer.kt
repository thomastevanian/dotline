package com.dotline.launcher.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.runtime.Immutable

/**
 * Draws a [WallpaperSpec] into a bitmap of an exact pixel size. Flat only: a solid background and
 * anti-aliased filled circles, no gradients, no blur. Blocking and CPU-bound, so call it from
 * Dispatchers.Default (previews) or Dispatchers.IO / Default (full-size export), never the main thread.
 */
object WallpaperRenderer {
    /** Smallest bitmap edge we ever create (a zero size would make Bitmap.createBitmap throw). */
    private const val MIN_EDGE = 1

    /**
     * @param timeText "HH:mm" used by the DOT_TEXT pattern when the spec has no custom word
     * @return an ARGB_8888 bitmap of exactly [width] x [height] pixels
     */
    fun render(spec: WallpaperSpec, width: Int, height: Int, timeText: String): Bitmap {
        val w = if (width < MIN_EDGE) MIN_EDGE else width
        val h = if (height < MIN_EDGE) MIN_EDGE else height
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(spec.effectiveBackground)

        val dots = DotField.generate(spec, w, h, timeText)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.FILL
        val baseColor = spec.effectiveColor
        val accentColor = spec.accent
        var current = baseColor
        paint.color = current
        for (i in 0 until dots.size) {
            val wanted = if (dots.accent[i]) accentColor else baseColor
            if (wanted != current) {
                paint.color = wanted
                current = wanted
            }
            canvas.drawCircle(dots.x[i], dots.y[i], dots.r[i], paint)
        }
        return bitmap
    }
}

/** Width x height in pixels. */
@Immutable
data class PixelSize(val width: Int, val height: Int)

/** Pure size maths for the studio: the phone's portrait pixel size, capped, and preview heights. */
object WallpaperSizing {
    const val MAX_WIDTH = 1440
    const val MAX_HEIGHT = 3200
    private const val FALLBACK_WIDTH = 1080
    private const val FALLBACK_HEIGHT = 2400

    /**
     * Portrait pixel size for a screen reporting [screenA] x [screenB] (either order, so a phone held
     * sideways still gives a portrait wallpaper). Scaled down proportionally to fit [MAX_WIDTH] x
     * [MAX_HEIGHT]; never scaled up. Zero or negative input gives a common 1080 x 2400 phone.
     */
    fun exportSize(screenA: Int, screenB: Int): PixelSize {
        if (screenA <= 0 || screenB <= 0) return PixelSize(FALLBACK_WIDTH, FALLBACK_HEIGHT)
        val w = if (screenA < screenB) screenA else screenB
        val h = if (screenA < screenB) screenB else screenA
        var scale = 1f
        val byWidth = MAX_WIDTH.toFloat() / w
        val byHeight = MAX_HEIGHT.toFloat() / h
        if (byWidth < scale) scale = byWidth
        if (byHeight < scale) scale = byHeight
        val outW = Math.round(w * scale)
        val outH = Math.round(h * scale)
        return PixelSize(if (outW < 1) 1 else outW, if (outH < 1) 1 else outH)
    }

    /** Height in pixels of a [widthPx] wide bitmap with the same aspect ratio as [of]. */
    fun heightFor(widthPx: Int, of: PixelSize): Int {
        val h = Math.round(widthPx.toFloat() * of.height / of.width)
        return if (h < 1) 1 else h
    }
}
