package com.dotline.launcher.wallpaper

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.json.JSONArray
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

class WallpaperPresetsJsonTest {
    private val specJson = WallpaperSpecJson.encode(WallpaperSpec())

    private fun preset(id: String, name: String, seed: Long) =
        WallpaperPreset(id, name, WallpaperSpec(WallpaperPattern.HALFTONE, seed = seed, text = "x"))

    private fun entry(id: String?, name: String?): JSONObject {
        val o = JSONObject()
        if (id != null) o.put("id", id)
        if (name != null) o.put("name", name)
        o.put("spec", specJson)
        return o
    }

    @Test
    fun listRoundTripsThroughJsonText() {
        val list = listOf(preset("uaaaa1111", "One", 1L), preset("ubbbb2222", "Two", 2L), preset("ucccc3333", "Three", -9L))
        val back = WallpaperPresetsJson.parse(JSONArray(WallpaperPresetsJson.toJson(list).toString()))
        assertEquals(list, back)
    }

    @Test
    fun emptyAndGarbageInputGiveNoPresets() {
        assertEquals(0, WallpaperPresetsJson.parse(JSONArray()).size)
        assertEquals(0, WallpaperPresetsJson.parse(JSONArray("[[],[1],\"a\",null]")).size)
    }

    @Test
    fun entriesWithoutAReadableSpecAreSkipped() {
        val arr = JSONArray(
            "[1, null, \"text\", {\"id\":\"u1\",\"name\":\"no spec\"}, " +
                "{\"id\":\"u2\",\"name\":\"ok\",\"spec\":{\"pattern\":\"CONCENTRIC\"}}, {\"spec\":5}]",
        )
        val out = WallpaperPresetsJson.parse(arr)
        assertEquals(1, out.size)
        assertEquals("u2", out[0].id)
        assertEquals("ok", out[0].name)
        assertEquals(WallpaperPattern.CONCENTRIC, out[0].spec.pattern)
    }

    @Test
    fun oddFieldTypesFallBack() {
        val out = WallpaperPresetsJson.parse(JSONArray("[{\"id\":5,\"name\":null,\"spec\":{}}]"))
        assertEquals(1, out.size)
        assertEquals("Preset", out[0].name)
        assertEquals(WallpaperSpec(), out[0].spec)
        assertTrue(out[0].id.startsWith("u"))
    }

    @Test
    fun namesAreCleaned() {
        assertEquals("Preset", WallpaperPresetsJson.cleanName(""))
        assertEquals("Preset", WallpaperPresetsJson.cleanName("   "))
        assertEquals("Night", WallpaperPresetsJson.cleanName("  Night  "))
        assertEquals(WallpaperPresetsJson.MAX_NAME, WallpaperPresetsJson.cleanName("N".repeat(100)).length)
        // A cut that ends in spaces leaves no trailing space.
        assertEquals("ab", WallpaperPresetsJson.cleanName("ab" + " ".repeat(60) + "c"))
    }

    @Test
    fun idsAreMadeUniqueAndStartWithU() {
        val arr = JSONArray()
        for (id in listOf("u1", "u1", "grid_dark", "", "x9")) arr.put(entry(id, "n"))
        arr.put(entry(null, "no id"))
        val out = WallpaperPresetsJson.parse(arr)
        assertEquals(6, out.size)
        assertEquals("u1", out[0].id)
        val ids = out.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        for (id in ids) assertTrue(id.startsWith("u"), id)
    }

    @Test
    fun builtInIdsNeverClashWithUserIds() {
        for (preset in WallpaperPresets.all) assertFalse(preset.id.startsWith("u"), preset.id)
    }

    @Test
    fun newIdLooksRightAndAvoidsExistingIds() {
        val id = WallpaperPresetsJson.newId(emptyList())
        assertEquals(9, id.length)
        assertTrue(id.startsWith("u"))
        assertTrue(id.substring(1).all { it in "0123456789abcdef" }, id)
        val taken = HashSet<String>()
        repeat(200) {
            val next = WallpaperPresetsJson.newId(taken)
            assertFalse(next in taken)
            taken.add(next)
        }
    }

    @Test
    fun atMostSixtyPresetsAreKept() {
        val arr = JSONArray()
        for (i in 0 until 100) arr.put(entry("u" + i, "n" + i))
        val out = WallpaperPresetsJson.parse(arr)
        assertEquals(WallpaperPresetsJson.MAX_PRESETS, out.size)
        assertEquals("u0", out.first().id)
    }
}

class WallpaperSizingTest {
    private fun check(a: Int, b: Int, w: Int, h: Int) {
        val size = WallpaperSizing.exportSize(a, b)
        assertEquals(w, size.width, "width for " + a + " x " + b)
        assertEquals(h, size.height, "height for " + a + " x " + b)
    }

    @Test
    fun commonPhonesKeepTheirExactSize() {
        check(1080, 2400, 1080, 2400)
        check(1080, 2340, 1080, 2340)
        check(720, 1600, 720, 1600)
        check(1440, 3120, 1440, 3120)
        check(1440, 3200, 1440, 3200)
    }

    @Test
    fun orderOfTheTwoNumbersDoesNotMatter() {
        check(2400, 1080, 1080, 2400)
        check(3120, 1440, 1440, 3120)
    }

    @Test
    fun nothingIsScaledUp() {
        check(540, 960, 540, 960)
    }

    @Test
    fun bigScreensAreScaledDownKeepingTheirShape() {
        check(2160, 4800, 1440, 3200)
        val size = WallpaperSizing.exportSize(1812, 3840)
        assertTrue(size.width <= WallpaperSizing.MAX_WIDTH && size.height <= WallpaperSizing.MAX_HEIGHT)
        assertTrue(abs(size.width.toDouble() / size.height - 1812.0 / 3840.0) < 0.002)
    }

    @Test
    fun badInputGivesACommonPhoneSize() {
        check(0, 0, 1080, 2400)
        check(-5, 100, 1080, 2400)
        check(100, 0, 1080, 2400)
    }

    @Test
    fun extremeShapesNeverReachZeroPixels() {
        val size = WallpaperSizing.exportSize(1, 100000)
        assertTrue(size.width >= 1)
        assertEquals(WallpaperSizing.MAX_HEIGHT, size.height)
    }

    @Test
    fun previewHeightFollowsTheScreenShape() {
        assertEquals(800, WallpaperSizing.heightFor(360, PixelSize(1080, 2400)))
        assertEquals(190, WallpaperSizing.heightFor(90, PixelSize(1080, 2280)))
        assertEquals(1, WallpaperSizing.heightFor(1, PixelSize(1000, 1)))
    }
}

class WallpaperPresetRangesTest {
    @Test
    fun thereAreTwentyFourBuiltInPresetsWithUniqueIds() {
        assertEquals(24, WallpaperPresets.all.size)
        assertEquals(24, WallpaperPresets.all.map { it.id }.toSet().size)
    }

    @Test
    fun builtInPresetsFitTheStudioSliders() {
        // The Studio sliders cover dot size 0.15..1 and spacing 0.012..0.09.
        for (preset in WallpaperPresets.all) {
            assertTrue(preset.spec.dotSize in 0.15f..1f, preset.id + " dotSize " + preset.spec.dotSize)
            assertTrue(preset.spec.spacing in 0.012f..0.09f, preset.id + " spacing " + preset.spec.spacing)
        }
    }
}
