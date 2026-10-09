package com.dotline.launcher.wallpaper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.json.JSONObject

class WallpaperSpecJsonTest {
    private val defaults = WallpaperSpec()

    @Test
    fun everyPresetRoundTripsThroughJsonObject() {
        for (preset in WallpaperPresets.all) {
            val back = WallpaperSpecJson.decode(WallpaperSpecJson.encode(preset.spec))
            assertEquals(preset.spec, back, "preset ${preset.id}")
        }
    }

    @Test
    fun everyPresetRoundTripsThroughJsonText() {
        for (preset in WallpaperPresets.all) {
            val text = WallpaperSpecJson.encode(preset.spec).toString()
            val back = WallpaperSpecJson.decode(JSONObject(text))
            assertEquals(preset.spec, back, "preset ${preset.id} via text")
        }
    }

    @Test
    fun everyFieldIsEncodedAndDecoded() {
        val spec = WallpaperSpec(
            pattern = WallpaperPattern.HALFTONE,
            spacing = 0.0625f,
            dotSize = 0.8125f,
            color = 0xFF112233.toInt(),
            background = 0xFFEEDDCC.toInt(),
            seed = -123456789012L,
            invert = true,
            text = "Hi \"there\" é",
            accent = 0xFF00FF00.toInt(),
        )
        val o = WallpaperSpecJson.encode(spec)
        assertEquals("HALFTONE", o.getString("pattern"))
        assertEquals(0xFF112233.toInt(), o.getInt("color"))
        assertEquals(0xFFEEDDCC.toInt(), o.getInt("background"))
        assertEquals(-123456789012L, o.getLong("seed"))
        assertEquals(true, o.getBoolean("invert"))
        assertEquals(0xFF00FF00.toInt(), o.getInt("accent"))
        assertEquals(spec, WallpaperSpecJson.decode(JSONObject(o.toString())))
    }

    @Test
    fun extremeSeedsAndColoursSurvive() {
        for (seed in longArrayOf(Long.MIN_VALUE, Long.MAX_VALUE, 0L, -1L)) {
            val spec = WallpaperSpec(seed = seed, color = Int.MIN_VALUE, background = Int.MAX_VALUE)
            assertEquals(spec, WallpaperSpecJson.decode(JSONObject(WallpaperSpecJson.encode(spec).toString())))
        }
    }

    @Test
    fun emptyObjectGivesDefaults() {
        assertEquals(defaults, WallpaperSpecJson.decode(JSONObject()))
    }

    @Test
    fun missingFieldsFallBackIndividually() {
        val o = JSONObject()
        o.put("pattern", "CONCENTRIC")
        o.put("invert", true)
        val spec = WallpaperSpecJson.decode(o)
        assertEquals(WallpaperPattern.CONCENTRIC, spec.pattern)
        assertEquals(true, spec.invert)
        assertEquals(defaults.spacing, spec.spacing)
        assertEquals(defaults.dotSize, spec.dotSize)
        assertEquals(defaults.color, spec.color)
        assertEquals(defaults.background, spec.background)
        assertEquals(defaults.seed, spec.seed)
        assertEquals(defaults.text, spec.text)
        assertEquals(defaults.accent, spec.accent)
    }

    @Test
    fun unknownOrBrokenPatternFallsBack() {
        assertEquals(defaults.pattern, WallpaperSpecJson.decode(JSONObject("{\"pattern\":\"SPIRAL\"}")).pattern)
        assertEquals(defaults.pattern, WallpaperSpecJson.decode(JSONObject("{\"pattern\":null}")).pattern)
        assertEquals(defaults.pattern, WallpaperSpecJson.decode(JSONObject("{\"pattern\":5}")).pattern)
        assertEquals(WallpaperPattern.RED_DOT, WallpaperSpecJson.decode(JSONObject("{\"pattern\":\"RED_DOT\"}")).pattern)
    }

    @Test
    fun wrongTypesAndNullsFallBack() {
        val o = JSONObject(
            "{\"spacing\":\"oops\",\"dotSize\":null,\"color\":true,\"background\":[1],\"seed\":\"x\"," +
                "\"invert\":\"maybe\",\"text\":7,\"accent\":{}}",
        )
        assertEquals(defaults, WallpaperSpecJson.decode(o))
    }

    @Test
    fun numbersAreClampedToWhatTheRendererUses() {
        val low = WallpaperSpecJson.decode(JSONObject("{\"spacing\":-5,\"dotSize\":-1}"))
        assertEquals(0.01f, low.spacing)
        assertEquals(0.1f, low.dotSize)
        val high = WallpaperSpecJson.decode(JSONObject("{\"spacing\":9,\"dotSize\":42}"))
        assertEquals(0.12f, high.spacing)
        assertEquals(1f, high.dotSize)
    }

    @Test
    fun numbersGivenAsTextAreAccepted() {
        val spec = WallpaperSpecJson.decode(JSONObject("{\"spacing\":\"0.05\",\"seed\":\"99\",\"invert\":\"TRUE\"}"))
        assertEquals(0.05f, spec.spacing)
        assertEquals(99L, spec.seed)
        assertEquals(true, spec.invert)
    }

    @Test
    fun coloursAcceptUnsignedLongsAndHexText() {
        val a = WallpaperSpecJson.decode(JSONObject("{\"color\":4294967295,\"background\":\"#102030\",\"accent\":\"#80FF0000\"}"))
        assertEquals(-1, a.color)
        assertEquals(0xFF102030.toInt(), a.background)
        assertEquals(0x80FF0000.toInt(), a.accent)
        val b = WallpaperSpecJson.decode(JSONObject("{\"color\":\"not a colour\",\"background\":\"#12\"}"))
        assertEquals(defaults.color, b.color)
        assertEquals(defaults.background, b.background)
    }

    @Test
    fun customWordIsCapped() {
        val long = "X".repeat(500)
        val spec = WallpaperSpecJson.decode(JSONObject().put("text", long))
        assertEquals(WallpaperSpecJson.MAX_TEXT, spec.text.length)
    }

    @Test
    fun jsonStringHelpers() {
        val spec = WallpaperSpec(WallpaperPattern.DOT_TEXT, text = "HELLO", seed = 31L, invert = true)
        assertEquals(spec, WallpaperSpecJson.fromJsonString(WallpaperSpecJson.toJsonString(spec)))
        assertNull(WallpaperSpecJson.fromJsonString(null))
        assertNull(WallpaperSpecJson.fromJsonString(""))
        assertNull(WallpaperSpecJson.fromJsonString("   "))
        assertNull(WallpaperSpecJson.fromJsonString("not json"))
        assertNull(WallpaperSpecJson.fromJsonString("[1,2]"))
        assertNotNull(WallpaperSpecJson.fromJsonString("{}"))
    }
}
