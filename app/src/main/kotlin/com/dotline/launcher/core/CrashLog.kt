package com.dotline.launcher.core

import android.content.Context
import android.os.Build
import com.dotline.launcher.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tiny on-device crash log so a failure can be pasted back for diagnosis.
 * Uncaught exceptions and handled errors passed to [record] are appended to one file,
 * newest first, capped to a few entries. Nothing ever leaves the device.
 */
object CrashLog {
    private const val FILE_NAME = "crash_log.txt"
    private const val SEPARATOR = "\n=====DOTLINE-CRASH=====\n"
    private const val MAX_ENTRIES = 8
    private const val MAX_BYTES = 96 * 1024

    private val lock = Any()
    private var appContext: Context? = null

    fun install(context: Context) {
        appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { record("UNCAUGHT on ${thread.name}", error) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Records a handled or fatal [error]. Never throws. */
    fun record(where: String, error: Throwable) {
        val ctx = appContext ?: return
        runCatching {
            synchronized(lock) {
                val sw = StringWriter()
                error.printStackTrace(PrintWriter(sw))
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val entry = buildString {
                    append(stamp).append("  ").append(where).append('\n')
                    append("Dotline ").append(BuildConfig.VERSION_NAME)
                    append("  Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")")
                    append("  ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
                    append(sw.toString().take(12_000))
                }
                val existing = readEntries(ctx)
                val all = (listOf(entry) + existing).take(MAX_ENTRIES)
                var text = all.joinToString(SEPARATOR)
                if (text.length > MAX_BYTES) text = text.take(MAX_BYTES)
                file(ctx).writeText(text)
            }
        }
    }

    /** The log as one string (newest entry first), or null when empty. */
    fun read(context: Context): String? = synchronized(lock) {
        val entries = readEntries(context.applicationContext)
        if (entries.isEmpty()) null else entries.joinToString("\n\n--------------------\n\n")
    }

    fun clear(context: Context) {
        synchronized(lock) { runCatching { file(context.applicationContext).delete() } }
    }

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    private fun readEntries(context: Context): List<String> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        val text = runCatching { f.readText() }.getOrDefault("")
        return text.split(SEPARATOR).filter { it.isNotBlank() }
    }
}
