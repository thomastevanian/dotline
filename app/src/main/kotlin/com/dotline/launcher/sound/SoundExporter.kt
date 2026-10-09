package com.dotline.launcher.sound

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.dotline.launcher.core.CrashLog
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Saves a synthesised sound as a WAV file in the shared Ringtones / Notifications / Alarms folder
 * through MediaStore. No storage permission is needed on Android 10+ because Dotline only ever
 * touches the files it created itself.
 */
object SoundExporter {
    private const val MIME_WAV = "audio/wav"
    private const val NEEDS_ANDROID_10 = "Saving sounds needs Android 10 or newer"

    /** Name of the shared folder a sound of [category] is saved to (UI sounds go to Notifications). */
    fun folderName(category: SoundCategory): String = when (category) {
        SoundCategory.RINGTONE -> Environment.DIRECTORY_RINGTONES
        SoundCategory.NOTIFICATION -> Environment.DIRECTORY_NOTIFICATIONS
        SoundCategory.ALARM -> Environment.DIRECTORY_ALARMS
        SoundCategory.UI -> Environment.DIRECTORY_NOTIFICATIONS
    }

    /**
     * File name shown in the system sound pickers. UI sounds carry a "UI" marker because some of
     * their names (Tap) also exist as notification sounds in the same folder.
     */
    fun displayName(spec: SoundSpec): String {
        val clean = spec.name.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim()
        val prefix = if (spec.category == SoundCategory.UI) "Dotline UI " else "Dotline "
        return "$prefix$clean.wav"
    }

    /**
     * Renders [spec] and writes it to the shared sound folder of its category. An earlier export of
     * the same sound is overwritten instead of duplicated. Returns the content Uri of the file, or
     * a failure whose message can be shown to the user. Runs on Dispatchers.IO; never throws.
     */
    suspend fun save(context: Context, spec: SoundSpec): Result<Uri> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return Result.failure(IllegalStateException(NEEDS_ANDROID_10))
        }
        val appContext = context.applicationContext
        return withContext(Dispatchers.IO) {
            runCatching { writeSound(appContext, spec) }
                .onFailure { CrashLog.record("SoundExporter.save ${spec.id}", it) }
        }
    }

    private fun writeSound(context: Context, spec: SoundSpec): Uri {
        val bytes = Wav.encode(Synth.render(spec))
        val resolver = context.contentResolver
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val folder = folderName(spec.category)
        val fileName = displayName(spec)

        val existing = findExisting(context, collection, fileName, folder)
        val target: Uri
        val created: Boolean
        if (existing != null) {
            target = existing
            created = false
            val pending = ContentValues()
            pending.put(MediaStore.MediaColumns.IS_PENDING, 1)
            resolver.update(target, pending, null, null)
        } else {
            val values = ContentValues()
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            values.put(MediaStore.MediaColumns.TITLE, fileName.removeSuffix(".wav"))
            values.put(MediaStore.MediaColumns.MIME_TYPE, MIME_WAV)
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
            values.put(MediaStore.MediaColumns.IS_PENDING, 1)
            putCategoryFlags(values, spec.category)
            target = resolver.insert(collection, values)
                ?: throw IOException("Android refused to create the sound file")
            created = true
        }

        try {
            val stream = resolver.openOutputStream(target, "wt")
                ?: throw IOException("Could not open the sound file for writing")
            stream.use {
                it.write(bytes)
                it.flush()
            }
            val done = ContentValues()
            done.put(MediaStore.MediaColumns.IS_PENDING, 0)
            putCategoryFlags(done, spec.category)
            resolver.update(target, done, null, null)
        } catch (e: Throwable) {
            if (created) {
                runCatching { resolver.delete(target, null, null) }
            }
            throw e
        }
        return target
    }

    /** Marks the file as exactly one kind of sound so the system pickers list it in the right place. */
    private fun putCategoryFlags(values: ContentValues, category: SoundCategory) {
        val ringtone = category == SoundCategory.RINGTONE
        val alarm = category == SoundCategory.ALARM
        val notification = category == SoundCategory.NOTIFICATION || category == SoundCategory.UI
        values.put(MediaStore.Audio.Media.IS_RINGTONE, if (ringtone) 1 else 0)
        values.put(MediaStore.Audio.Media.IS_NOTIFICATION, if (notification) 1 else 0)
        values.put(MediaStore.Audio.Media.IS_ALARM, if (alarm) 1 else 0)
        values.put(MediaStore.Audio.Media.IS_MUSIC, 0)
    }

    /** Looks for a file Dotline already exported with this display name in this folder. */
    private fun findExisting(context: Context, collection: Uri, fileName: String, folder: String): Uri? {
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.RELATIVE_PATH)
        val selection = MediaStore.MediaColumns.DISPLAY_NAME + " = ?"
        var found: Uri? = null
        val cursor = context.contentResolver.query(collection, projection, selection, arrayOf(fileName), null)
        if (cursor != null) {
            cursor.use { c ->
                val wanted = folder.trim('/')
                while (found == null && c.moveToNext()) {
                    val id = c.getLong(0)
                    val path = (c.getString(1) ?: "").trim('/')
                    if (path == wanted) {
                        found = ContentUris.withAppendedId(collection, id)
                    }
                }
            }
        }
        return found
    }
}
