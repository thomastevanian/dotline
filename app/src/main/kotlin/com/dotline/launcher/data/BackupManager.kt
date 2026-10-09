package com.dotline.launcher.data

import android.content.Context
import android.net.Uri
import com.dotline.launcher.data.model.HomeLayout
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Everything that is restored from a backup file. Missing sections stay null. */
data class BackupData(
    val settings: Settings,
    val layout: HomeLayout?,
    val notes: String?,
    /** Raw user wallpaper presets (opaque to the backup; owned by Wallpaper Studio). */
    val wallpaperPresets: JSONArray?,
    val exportedAt: Long,
    val appVersion: String,
)

/**
 * Full layout and settings backup as one JSON document, written and read through the system file
 * picker (Storage Access Framework), so no storage permission is needed.
 */
object BackupManager {
    const val FORMAT = "dotline-backup"
    const val VERSION = 1
    const val MIME = "application/json"

    fun suggestedFileName(): String = "dotline-backup.json"

    fun encode(
        settings: Settings,
        layout: HomeLayout,
        notes: String,
        wallpaperPresets: JSONArray?,
        appVersion: String,
        now: Long,
    ): String = JSONObject().apply {
        put("format", FORMAT)
        put("version", VERSION)
        put("exportedAt", now)
        put("appVersion", appVersion)
        put("settings", SettingsJson.encode(settings))
        put("layout", LayoutJson.encode(layout))
        put("notes", notes)
        if (wallpaperPresets != null) put("wallpaperPresets", wallpaperPresets)
    }.toString(2)

    /** Parses a backup; null when the text is not a Dotline backup. Never throws. */
    fun decode(text: String): BackupData? = runCatching {
        val o = JSONObject(text)
        if (o.optString("format") != FORMAT) return@runCatching null
        if (o.optInt("version", 0) > VERSION) return@runCatching null
        val settingsJson = o.optJSONObject("settings") ?: return@runCatching null
        BackupData(
            settings = SettingsJson.decode(settingsJson),
            layout = o.optJSONObject("layout")?.let { LayoutJson.decode(it) },
            notes = if (o.has("notes")) o.optString("notes") else null,
            wallpaperPresets = o.optJSONArray("wallpaperPresets"),
            exportedAt = o.optLong("exportedAt", 0L),
            appVersion = o.optString("appVersion", ""),
        )
    }.getOrNull()

    suspend fun writeTo(context: Context, uri: Uri, json: String) = withContext(Dispatchers.IO) {
        val out = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot open the file for writing")
        out.use { it.write(json.toByteArray(Charsets.UTF_8)) }
    }

    suspend fun readFrom(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open the file for reading")
        input.use { it.readBytes().toString(Charsets.UTF_8) }
    }
}
