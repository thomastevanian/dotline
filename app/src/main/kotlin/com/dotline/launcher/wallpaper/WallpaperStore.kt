package com.dotline.launcher.wallpaper

import android.content.Context
import com.dotline.launcher.core.CrashLog
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * The wallpapers the user saved in Wallpaper Studio (the 24 built-in presets are not stored here).
 * Persisted as one JSON file in the app's private files directory. The file is read once on
 * Dispatchers.IO when the store is created; writes are debounced and atomic (temp file, then rename).
 * Newest presets come first.
 */
class WallpaperStore(context: Context, private val scope: CoroutineScope) {
    private val file = File(context.applicationContext.filesDir, FILE_NAME)
    private val lock = Any()
    private val ioLock = Any()
    private val _presets = MutableStateFlow<List<WallpaperPreset>>(emptyList())

    /** User-saved presets only, newest first. */
    val presets: StateFlow<List<WallpaperPreset>> = _presets.asStateFlow()

    // Edits made before the file has been read are replayed on top of what the file holds.
    // Nothing is written to disk until the file has been read, so an early edit can never
    // overwrite the presets that are still being loaded.
    private var loaded = false
    private val pendingOps = ArrayList<(List<WallpaperPreset>) -> List<WallpaperPreset>>()
    private var persistJob: Job? = null

    init {
        scope.launch(Dispatchers.IO) {
            val fromDisk = readFromDisk()
            var replayed = false
            synchronized(lock) {
                var list = fromDisk
                if (pendingOps.isNotEmpty()) {
                    replayed = true
                    for (op in pendingOps) list = op(list)
                    pendingOps.clear()
                }
                loaded = true
                _presets.value = list
            }
            if (replayed) schedulePersist()
        }
    }

    /** Saves [spec] under [name] and returns the new preset id ("u" + short random hex). */
    fun save(name: String, spec: WallpaperSpec): String {
        val cleanName = WallpaperPresetsJson.cleanName(name)
        val newId = synchronized(lock) { WallpaperPresetsJson.newId(_presets.value.map { it.id }) }
        val preset = WallpaperPreset(newId, cleanName, spec)
        mutate { list -> (listOf(preset) + list).take(WallpaperPresetsJson.MAX_PRESETS) }
        return newId
    }

    fun delete(id: String) {
        mutate { list -> list.filter { it.id != id } }
    }

    /** All user presets as a JSON array of {id, name, spec} objects. */
    fun exportJson(): JSONArray = WallpaperPresetsJson.toJson(_presets.value)

    /** Replaces all user presets with those in [arr]; entries that cannot be read are skipped. */
    fun importJson(arr: JSONArray) {
        val parsed = WallpaperPresetsJson.parse(arr)
        mutate { _ -> parsed }
    }

    private fun mutate(op: (List<WallpaperPreset>) -> List<WallpaperPreset>) {
        synchronized(lock) {
            _presets.value = op(_presets.value)
            if (!loaded) pendingOps.add(op)
        }
        schedulePersist()
    }

    private fun schedulePersist() {
        synchronized(lock) {
            persistJob?.cancel()
            persistJob = scope.launch {
                delay(PERSIST_DELAY_MS)
                // Not loaded yet: the loader replays the early edits and schedules the write itself.
                val snapshot: List<WallpaperPreset>? = synchronized(lock) { if (loaded) _presets.value else null }
                if (snapshot != null) {
                    withContext(Dispatchers.IO) { writeToDisk(snapshot) }
                }
            }
        }
    }

    private fun readFromDisk(): List<WallpaperPreset> {
        return try {
            if (!file.exists()) {
                emptyList()
            } else {
                WallpaperPresetsJson.parse(JSONArray(file.readText()))
            }
        } catch (e: Exception) {
            CrashLog.record("wallpaper presets read", e)
            emptyList()
        }
    }

    private fun writeToDisk(list: List<WallpaperPreset>) {
        synchronized(ioLock) {
            try {
                val tmp = File(file.parentFile, "$FILE_NAME.tmp")
                tmp.writeText(WallpaperPresetsJson.toJson(list).toString())
                if (!tmp.renameTo(file)) {
                    file.writeText(tmp.readText())
                    tmp.delete()
                }
            } catch (e: Exception) {
                CrashLog.record("wallpaper presets write", e)
            }
        }
    }

    private companion object {
        const val FILE_NAME = "wallpaper_presets.json"
        const val PERSIST_DELAY_MS = 400L
    }
}

/**
 * The pure part of [WallpaperStore]: turning the preset list into JSON and back. It has no Android
 * types, so it is unit-tested on the JVM.
 */
internal object WallpaperPresetsJson {
    const val MAX_NAME = 40
    const val MAX_PRESETS = 60
    private const val DEFAULT_NAME = "Preset"

    fun toJson(list: List<WallpaperPreset>): JSONArray {
        val arr = JSONArray()
        for (preset in list) {
            val o = JSONObject()
            o.put("id", preset.id)
            o.put("name", preset.name)
            o.put("spec", WallpaperSpecJson.encode(preset.spec))
            arr.put(o)
        }
        return arr
    }

    /** A user preset name: trimmed, at most [MAX_NAME] characters, never empty. */
    fun cleanName(name: String): String {
        val trimmed = name.trim().take(MAX_NAME).trim()
        return if (trimmed.isEmpty()) DEFAULT_NAME else trimmed
    }

    /** "u" + 8 random hex digits, different from every id in [existing]. */
    fun newId(existing: Collection<String>): String {
        var candidate = "u" + randomHex()
        while (candidate in existing) candidate = "u" + randomHex()
        return candidate
    }

    /**
     * Reads {id, name, spec} entries. Entries without a readable spec are skipped; names are cleaned;
     * ids are made unique and always start with "u" so they can never clash with a built-in preset.
     * At most [MAX_PRESETS] entries are kept.
     */
    fun parse(arr: JSONArray): List<WallpaperPreset> {
        val out = ArrayList<WallpaperPreset>()
        val seen = HashSet<String>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val specObject = o.optJSONObject("spec") ?: continue
            val spec = try {
                WallpaperSpecJson.decode(specObject)
            } catch (e: Exception) {
                continue
            }
            val rawName = if (o.has("name") && !o.isNull("name")) o.optString("name", "") else ""
            var id = if (o.has("id") && !o.isNull("id")) o.optString("id", "").trim() else ""
            if (id.isEmpty() || !id.startsWith("u") || id in seen) {
                id = newId(seen)
            }
            seen.add(id)
            out.add(WallpaperPreset(id, cleanName(rawName), spec))
            if (out.size >= MAX_PRESETS) break
        }
        return out
    }

    private fun randomHex(): String = UUID.randomUUID().toString().replace("-", "").take(8)
}
