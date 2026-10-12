package com.dotline.launcher.wallpaper

import org.json.JSONObject

/**
 * JSON form of a [WallpaperSpec]: the pattern by enum name, colours as ARGB Ints, sizes as
 * numbers. [decode] is tolerant: a missing, null or malformed field falls back to the default
 * of a fresh [WallpaperSpec], an unknown pattern name falls back to the default pattern, and
 * numbers are clamped to the range the renderer works with.
 */
object WallpaperSpecJson {
    /** Longest custom word kept; anything beyond is cut. */
    const val MAX_TEXT = 40

    fun encode(spec: WallpaperSpec): JSONObject {
        val d = WallpaperSpec()
        val o = JSONObject()
        o.put("pattern", spec.pattern.name)
        o.put("spacing", finiteOr(spec.spacing, d.spacing).toDouble())
        o.put("dotSize", finiteOr(spec.dotSize, d.dotSize).toDouble())
        o.put("color", spec.color)
        o.put("background", spec.background)
        o.put("seed", spec.seed)
        o.put("invert", spec.invert)
        o.put("text", spec.text)
        o.put("accent", spec.accent)
        return o
    }

    fun decode(o: JSONObject): WallpaperSpec {
        val d = WallpaperSpec()
        return WallpaperSpec(
            pattern = readPattern(o, d.pattern),
            spacing = readFloat(o, "spacing", d.spacing).coerceIn(0.01f, 0.12f),
            dotSize = readFloat(o, "dotSize", d.dotSize).coerceIn(0.1f, 1f),
            color = readColor(o, "color", d.color),
            background = readColor(o, "background", d.background),
            seed = readLong(o, "seed", d.seed),
            invert = readBoolean(o, "invert", d.invert),
            text = readString(o, "text", d.text).take(MAX_TEXT),
            accent = readColor(o, "accent", d.accent),
        )
    }

    /** Compact JSON text of [spec]; used to keep the working spec across rotation. */
    fun toJsonString(spec: WallpaperSpec): String = encode(spec).toString()

    /** Parses [json] produced by [toJsonString]; null when it is null, blank or not a JSON object. */
    fun fromJsonString(json: String?): WallpaperSpec? {
        if (json.isNullOrBlank()) return null
        return try {
            decode(JSONObject(json))
        } catch (e: Exception) {
            null
        }
    }

    /** JSON cannot hold NaN or infinity (put would throw), so those become [fallback]. */
    private fun finiteOr(value: Float, fallback: Float): Float {
        return if (value.isNaN() || value.isInfinite()) fallback else value
    }

    private fun readPattern(o: JSONObject, fallback: WallpaperPattern): WallpaperPattern {
        if (!o.has("pattern") || o.isNull("pattern")) return fallback
        val name = o.opt("pattern")?.toString() ?: return fallback
        for (candidate in WallpaperPattern.values()) {
            if (candidate.name == name) return candidate
        }
        return fallback
    }

    private fun readFloat(o: JSONObject, key: String, fallback: Float): Float {
        if (!o.has(key) || o.isNull(key)) return fallback
        val raw = o.opt(key)
        val value: Double = when (raw) {
            is Number -> raw.toDouble()
            is String -> raw.trim().toDoubleOrNull() ?: return fallback
            else -> return fallback
        }
        if (value.isNaN() || value.isInfinite()) return fallback
        return value.toFloat()
    }

    private fun readLong(o: JSONObject, key: String, fallback: Long): Long {
        if (!o.has(key) || o.isNull(key)) return fallback
        val raw = o.opt(key)
        return when (raw) {
            is Long -> raw
            is Int -> raw.toLong()
            is Number -> {
                val v = raw.toDouble()
                if (v.isNaN() || v.isInfinite()) fallback else raw.toLong()
            }
            is String -> raw.trim().toLongOrNull() ?: fallback
            else -> fallback
        }
    }

    private fun readBoolean(o: JSONObject, key: String, fallback: Boolean): Boolean {
        if (!o.has(key) || o.isNull(key)) return fallback
        val raw = o.opt(key)
        return when (raw) {
            is Boolean -> raw
            is String -> if (raw.equals("true", ignoreCase = true)) true else if (raw.equals("false", ignoreCase = true)) false else fallback
            else -> fallback
        }
    }

    private fun readString(o: JSONObject, key: String, fallback: String): String {
        if (!o.has(key) || o.isNull(key)) return fallback
        val raw = o.opt(key)
        return if (raw is String) raw else fallback
    }

    /** ARGB Int; also accepts "#RRGGBB" / "#AARRGGBB" text, which is treated as opaque when it has no alpha. */
    private fun readColor(o: JSONObject, key: String, fallback: Int): Int {
        if (!o.has(key) || o.isNull(key)) return fallback
        val raw = o.opt(key)
        return when (raw) {
            is Int -> raw
            is Long -> raw.toInt()
            is Number -> {
                val v = raw.toDouble()
                if (v.isNaN() || v.isInfinite()) fallback else raw.toLong().toInt()
            }
            is String -> parseHexColor(raw) ?: fallback
            else -> fallback
        }
    }

    private fun parseHexColor(text: String): Int? {
        var s = text.trim()
        if (s.startsWith("#")) s = s.substring(1)
        if (s.length != 6 && s.length != 8) return null
        val parsed = s.toLongOrNull(16) ?: return null
        return if (s.length == 6) (0xFF000000L or parsed).toInt() else parsed.toInt()
    }
}
