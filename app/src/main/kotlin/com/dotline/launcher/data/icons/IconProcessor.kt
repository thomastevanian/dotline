package com.dotline.launcher.data.icons

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import com.dotline.launcher.data.IconShape
import com.dotline.launcher.data.IconStyle
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Pure android.graphics icon renderer (no Compose). Turns a [Drawable] into an ARGB_8888 bitmap that is
 * exactly [IconRequest.sizePx] square. Strictly flat: fills, 1dp-ish outline strokes and silhouettes only.
 * Safe to call from any background thread; every call uses its own Paint and Bitmap objects.
 */
object IconProcessor {

    /** Glyph edge as a fraction of the tile for plain (non adaptive) drawables. */
    private const val GLYPH_FRACTION = 0.58f
    private const val GLYPH_ONLY_FRACTION = 0.80f

    /** Adaptive icons: fraction of the tile occupied by the 72dp visible window of the 108dp layer. */
    private const val ADAPTIVE_WINDOW = 0.62f
    private const val ADAPTIVE_WINDOW_GLYPH_ONLY = 0.84f

    /** Adaptive layers are 108dp while the visible mask window is 72dp. */
    private const val LAYER_SCALE = 108f / 72f

    private const val ROUNDED_SQUARE_RADIUS = 0.28f

    private val NEUTRAL_TILE: Int = 0xFF2A2A2A.toInt()
    private val NEUTRAL_RING: Int = 0xFFCFCFCF.toInt()

    /** Slight gamma on source alpha: keeps edges soft but suppresses faint shadows and halos. */
    private val ALPHA_LUT: IntArray = IntArray(256) { i ->
        if (i < 6) {
            0
        } else {
            val v = (255.0 * (i / 255.0).pow(1.2) + 0.5).toInt()
            if (v < 0) 0 else if (v > 255) 255 else v
        }
    }

    /**
     * Renders [drawable] according to [request]. Never throws for ordinary drawable problems: a null,
     * zero-size, empty or failing drawable produces a generic ring glyph so no app is ever blank.
     */
    fun process(drawable: Drawable?, request: IconRequest): Bitmap {
        val size = max(1, request.sizePx)
        val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        try {
            drawStyle(canvas, drawable, size, request)
        } catch (e: Exception) {
            result.eraseColor(0)
            try {
                drawStyle(canvas, null, size, request)
            } catch (e2: Exception) {
                // Leave the bitmap transparent; nothing more can be done.
            }
        }
        return result
    }

    private fun drawStyle(canvas: Canvas, drawable: Drawable?, size: Int, request: IconRequest) {
        when (request.style) {
            IconStyle.ORIGINAL -> drawOriginal(canvas, drawable, size, request)
            IconStyle.MONOCHROME -> drawMonochrome(canvas, drawable, size, request, request.glyphColor)
            IconStyle.MONOCHROME_ACCENT -> drawMonochrome(canvas, drawable, size, request, request.accentColor)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Shapes and tile
    // ---------------------------------------------------------------------------------------------

    private fun newPaint(argb: Int, style: Paint.Style, strokeWidth: Float): Paint {
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        p.style = style
        p.strokeWidth = strokeWidth
        p.color = argb
        return p
    }

    /** Circle or rounded square inside a [size] square, inset by [inset] px on every side. */
    private fun shapePath(size: Float, inset: Float, shape: IconShape): Path {
        val path = Path()
        val edge = size - 2f * inset
        when (shape) {
            IconShape.CIRCLE -> {
                path.addCircle(size / 2f, size / 2f, max(0.5f, edge / 2f), Path.Direction.CW)
            }
            IconShape.ROUNDED_SQUARE -> {
                val radius = max(0f, size * ROUNDED_SQUARE_RADIUS - inset)
                path.addRoundRect(inset, inset, size - inset, size - inset, radius, radius, Path.Direction.CW)
            }
        }
        return path
    }

    private fun drawTile(canvas: Canvas, size: Int, request: IconRequest) {
        val s = size.toFloat()
        val stroke = max(1f, s / 56f)
        canvas.drawPath(shapePath(s, 0f, request.shape), newPaint(request.tileColor, Paint.Style.FILL, 0f))
        if ((request.outlineColor ushr 24) > 0) {
            // Outline is drawn inside the bounds: the stroke is centred half a stroke width inwards.
            val line = newPaint(request.outlineColor, Paint.Style.STROKE, stroke)
            canvas.drawPath(shapePath(s, stroke / 2f, request.shape), line)
        }
    }

    private fun drawRing(canvas: Canvas, size: Int, fraction: Float, argb: Int) {
        val s = size.toFloat()
        val stroke = max(1.5f, s * 0.07f)
        val radius = max(1f, s * fraction / 2f - stroke / 2f)
        canvas.drawCircle(s / 2f, s / 2f, radius, newPaint(argb, Paint.Style.STROKE, stroke))
    }

    // ---------------------------------------------------------------------------------------------
    // ORIGINAL: the app icon untouched, clipped to the chosen shape
    // ---------------------------------------------------------------------------------------------

    private fun drawOriginal(canvas: Canvas, drawable: Drawable?, size: Int, request: IconRequest) {
        val s = size.toFloat()
        if (drawable == null || isZeroSize(drawable)) {
            canvas.drawPath(shapePath(s, 0f, request.shape), newPaint(NEUTRAL_TILE, Paint.Style.FILL, 0f))
            drawRing(canvas, size, 0.42f, NEUTRAL_RING)
            return
        }
        val content = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val contentCanvas = Canvas(content)
        if (drawable is AdaptiveIconDrawable) {
            val edge = max(2, (size * LAYER_SCALE).roundToInt())
            val offset = (size - edge) / 2
            drawInto(contentCanvas, drawable.background, offset, offset, edge, edge)
            drawInto(contentCanvas, drawable.foreground, offset, offset, edge, edge)
        } else {
            drawInto(contentCanvas, drawable, 0, 0, size, size)
        }
        // A BitmapShader fill gives a properly anti-aliased shape edge on a software canvas.
        val paint = newPaint(0xFF000000.toInt(), Paint.Style.FILL, 0f)
        paint.shader = BitmapShader(content, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        canvas.drawPath(shapePath(s, 0f, request.shape), paint)
        content.recycle()
    }

    // ---------------------------------------------------------------------------------------------
    // MONOCHROME / MONOCHROME_ACCENT: tile + silhouette glyph
    // ---------------------------------------------------------------------------------------------

    private fun drawMonochrome(canvas: Canvas, drawable: Drawable?, size: Int, request: IconRequest, tint: Int) {
        if (!request.glyphOnly) {
            drawTile(canvas, size, request)
        }
        val glyph = buildGlyph(drawable, size, request.glyphOnly, tint)
        if (glyph != null) {
            canvas.drawBitmap(glyph, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            glyph.recycle()
        } else {
            drawRing(canvas, size, if (request.glyphOnly) 0.60f else 0.42f, tint)
        }
    }

    /**
     * Returns a [size] x [size] bitmap holding only the glyph silhouette (RGB = [tint], alpha from the
     * source), already positioned and scaled for the tile. Returns null when there is nothing usable.
     */
    private fun buildGlyph(drawable: Drawable?, size: Int, glyphOnly: Boolean, tint: Int): Bitmap? {
        if (drawable == null) return null

        val isAdaptive = drawable is AdaptiveIconDrawable
        var source: Drawable? = drawable
        var trustAlpha = false
        if (drawable is AdaptiveIconDrawable) {
            var mono: Drawable? = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                mono = drawable.monochrome
            }
            if (mono != null) {
                source = mono
                trustAlpha = true
            } else {
                source = drawable.foreground
            }
        }
        val src: Drawable = source ?: return null
        if (!isAdaptive && isZeroSize(src)) return null

        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val area: Float
        if (isAdaptive) {
            val window = if (glyphOnly) ADAPTIVE_WINDOW_GLYPH_ONLY else ADAPTIVE_WINDOW
            val edge = max(2, (size * window * LAYER_SCALE).roundToInt())
            val offset = (size - edge) / 2
            drawInto(canvas, src, offset, offset, edge, edge)
            val visibleEdge = min(edge, size)
            area = visibleEdge.toFloat() * visibleEdge.toFloat()
        } else {
            val fraction = if (glyphOnly) GLYPH_ONLY_FRACTION else GLYPH_FRACTION
            val box = max(2, (size * fraction).roundToInt())
            var w = box
            var h = box
            val iw = src.intrinsicWidth
            val ih = src.intrinsicHeight
            if (iw > 0 && ih > 0) {
                val scale = min(box.toFloat() / iw.toFloat(), box.toFloat() / ih.toFloat())
                w = max(1, (iw * scale).roundToInt())
                h = max(1, (ih * scale).roundToInt())
            }
            drawInto(canvas, src, (size - w) / 2, (size - h) / 2, w, h)
            area = w.toFloat() * h.toFloat()
        }

        val count = size * size
        val px = IntArray(count)
        bmp.getPixels(px, 0, size, 0, 0, size, size)

        var alphaOverride: IntArray? = null
        if (!trustAlpha) {
            var opaque = 0
            for (c in px) {
                if ((c ushr 24) >= 250) opaque++
            }
            val limit = if (isAdaptive) 0.6f else 0.68f
            if (opaque.toFloat() > limit * area) {
                // Full-bleed artwork (opaque background baked in): separate the glyph from the background.
                alphaOverride = extractForegroundAlpha(px, area)
            }
        }

        val visible = applySilhouette(px, tint, alphaOverride)
        if (visible < max(4, count / 1000)) {
            bmp.recycle()
            return null
        }
        bmp.setPixels(px, 0, size, 0, 0, size, size)
        return bmp
    }

    /** Rewrites [px] in place to tint RGB with the (gamma-softened) alpha. Returns the count of visible pixels. */
    private fun applySilhouette(px: IntArray, tint: Int, alphaOverride: IntArray?): Int {
        val rgb = tint and 0x00FFFFFF
        val tintAlpha = tint ushr 24
        var visible = 0
        for (i in px.indices) {
            val a = if (alphaOverride != null) alphaOverride[i] else (px[i] ushr 24)
            var na = ALPHA_LUT[a]
            if (tintAlpha != 255) na = na * tintAlpha / 255
            if (na > 0) visible++
            px[i] = (na shl 24) or rgb
        }
        return visible
    }

    private fun colorBin(c: Int): Int =
        (((c shr 20) and 0xF) shl 8) or (((c shr 12) and 0xF) shl 4) or ((c shr 4) and 0xF)

    /**
     * For artwork whose background is opaque and baked in: finds the dominant colour (the background),
     * and returns a per-pixel alpha array based on the distance from it. Returns null when the result
     * does not look like a glyph (then the caller keeps the plain source alpha).
     */
    private fun extractForegroundAlpha(px: IntArray, area: Float): IntArray? {
        val hist = IntArray(4096)
        for (c in px) {
            if ((c ushr 24) >= 250) {
                hist[colorBin(c)]++
            }
        }
        var best = 0
        for (i in 1 until 4096) {
            if (hist[i] > hist[best]) best = i
        }
        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        var counted = 0L
        for (c in px) {
            if ((c ushr 24) >= 250 && colorBin(c) == best) {
                sumR += ((c shr 16) and 0xFF).toLong()
                sumG += ((c shr 8) and 0xFF).toLong()
                sumB += (c and 0xFF).toLong()
                counted++
            }
        }
        if (counted == 0L) return null
        val bgR = (sumR / counted).toInt()
        val bgG = (sumG / counted).toInt()
        val bgB = (sumB / counted).toInt()

        val lowEdge = 36
        val highEdge = 120
        val out = IntArray(px.size)
        var covered = 0
        for (i in px.indices) {
            val c = px[i]
            val a = c ushr 24
            if (a == 0) continue
            val dr = abs(((c shr 16) and 0xFF) - bgR)
            val dg = abs(((c shr 8) and 0xFF) - bgG)
            val db = abs((c and 0xFF) - bgB)
            val d = max(dr, max(dg, db))
            val t = if (d <= lowEdge) 0 else if (d >= highEdge) 255 else ((d - lowEdge) * 255) / (highEdge - lowEdge)
            val na = a * t / 255
            out[i] = na
            if (na > 128) covered++
        }
        val ratio = covered.toFloat() / area
        return if (ratio >= 0.015f && ratio <= 0.5f) out else null
    }

    // ---------------------------------------------------------------------------------------------
    // Drawable helpers
    // ---------------------------------------------------------------------------------------------

    private fun isZeroSize(d: Drawable): Boolean = d.intrinsicWidth == 0 || d.intrinsicHeight == 0

    /**
     * Draws [d] scaled into the given box of [canvas]. The drawable is rendered straight at its final
     * size (never into a larger intermediate). Hardware-backed bitmaps are copied to software first.
     * Any failure draws nothing for this drawable.
     */
    private fun drawInto(canvas: Canvas, d: Drawable?, left: Int, top: Int, w: Int, h: Int) {
        if (d == null || w <= 0 || h <= 0) return
        try {
            if (d is BitmapDrawable) {
                val bitmap = d.bitmap
                if (bitmap != null && bitmap.config == Bitmap.Config.HARDWARE) {
                    val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                    if (copy != null) {
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                        canvas.drawBitmap(copy, null, Rect(left, top, left + w, top + h), paint)
                        copy.recycle()
                    }
                    return
                }
            }
            val saved = d.copyBounds()
            d.setBounds(left, top, left + w, top + h)
            d.draw(canvas)
            d.setBounds(saved)
        } catch (e: Exception) {
            // This layer draws nothing; the caller falls back to the ring glyph when everything is empty.
        }
    }
}
