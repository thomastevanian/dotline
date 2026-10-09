package com.dotline.launcher.sound

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.dotline.launcher.core.CrashLog
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Saves a synthesised sound as a WAV file in the shared Ringtones / Notifications / Alarms folder
 * through MediaStore. No storage permission is needed on Android 10+ because Dotline only ever
 * creates and touches the files it made itself.
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
        val fileName = SoundNames.fileName(spec)

        val existing = findExisting(resolver, collection, fileName, folder)
        if (existing != null) {
            try {
                overwrite(resolver, existing, bytes, spec.category)
                return existing
            } catch (e: FileNotFoundException) {
                // The row is stale: the file was deleted behind MediaStore's back (for example in a
                // file manager). Drop the row and fall through to create the sound again.
                runCatching { resolver.delete(existing, null, null) }
            }
        }
        return create(resolver, collection, folder, fileName, bytes, spec.category)
    }

    /** Inserts a new pending entry, writes the WAV into it and publishes it. */
    private fun create(
        resolver: ContentResolver,
        collection: Uri,
        folder: String,
        fileName: String,
        bytes: ByteArray,
        category: SoundCategory,
    ): Uri {
        val values = ContentValues()
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        values.put(MediaStore.MediaColumns.TITLE, fileName.removeSuffix(".wav"))
        values.put(MediaStore.MediaColumns.MIME_TYPE, MIME_WAV)
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
        values.put(MediaStore.MediaColumns.IS_PENDING, 1)
        putCategoryFlags(values, category)
        val target = resolver.insert(collection, values)
            ?: throw IOException("Android refused to create the sound file")
        try {
            writeBytes(resolver, target, bytes)
            publish(resolver, target, category)
        } catch (e: Throwable) {
            runCatching { resolver.delete(target, null, null) }
            throw e
        }
        return target
    }

    /** Replaces the content of an entry Dotline created earlier. */
    private fun overwrite(resolver: ContentResolver, target: Uri, bytes: ByteArray, category: SoundCategory) {
        val pending = ContentValues()
        pending.put(MediaStore.MediaColumns.IS_PENDING, 1)
        resolver.update(target, pending, null, null)
        try {
            writeBytes(resolver, target, bytes)
        } catch (e: Throwable) {
            // Never leave the entry hidden as pending: the next successful save repairs its content.
            runCatching { publish(resolver, target, category) }
            throw e
        }
        publish(resolver, target, category)
    }

    private fun writeBytes(resolver: ContentResolver, target: Uri, bytes: ByteArray) {
        val stream = resolver.openOutputStream(target, "wt")
            ?: throw IOException("Could not open the sound file for writing")
        stream.use {
            it.write(bytes)
            it.flush()
        }
    }

    /** Clears the pending flag so the file shows up in the system pickers. */
    private fun publish(resolver: ContentResolver, target: Uri, category: SoundCategory) {
        val done = ContentValues()
        done.put(MediaStore.MediaColumns.IS_PENDING, 0)
        putCategoryFlags(done, category)
        resolver.update(target, done, null, null)
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
    private fun findExisting(resolver: ContentResolver, collection: Uri, fileName: String, folder: String): Uri? {
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.RELATIVE_PATH)
        val selection = MediaStore.MediaColumns.DISPLAY_NAME + " = ?"
        val cursor = resolver.query(collection, projection, selection, arrayOf(fileName), null) ?: return null
        cursor.use { c ->
            val wanted = folder.trim('/')
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val path = (c.getString(1) ?: "").trim('/')
                if (path == wanted) {
                    return ContentUris.withAppendedId(collection, id)
                }
            }
        }
        return null
    }
}
