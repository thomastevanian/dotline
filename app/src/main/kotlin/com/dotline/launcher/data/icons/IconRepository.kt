package com.dotline.launcher.data.icons

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.os.Process
import android.util.LruCache
import android.util.Xml
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.dotline.launcher.core.CrashLog
import com.dotline.launcher.data.AppRepository
import com.dotline.launcher.data.IconShape
import com.dotline.launcher.data.IconStyle
import com.dotline.launcher.data.model.AppInfo
import com.dotline.launcher.data.model.AppKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Everything that determines how one icon looks. Colours are ARGB ints.
 *
 * MONOCHROME = tile + silhouette glyph, ORIGINAL = the app icon clipped to the shape (no tile),
 * MONOCHROME_ACCENT = monochrome with an accent-tinted glyph. [glyphOnly] = transparent background,
 * just the glyph (folder previews).
 */
@Immutable
data class IconRequest(
    val style: IconStyle,
    val shape: IconShape,
    val sizePx: Int,
    val tileColor: Int,
    val glyphColor: Int,
    val outlineColor: Int,
    val accentColor: Int,
    val glyphOnly: Boolean = false,
    val packPackage: String = "",
)

/** Bump when [IconProcessor] output changes so stale disk entries are never reused. */
private const val PROCESSOR_VERSION = 1

private const val MEMORY_CAP_BYTES = 24 * 1024 * 1024
private const val DISK_CAP_BYTES = 40L * 1024L * 1024L
private const val DISK_TRIM_TARGET_BYTES = 32L * 1024L * 1024L
private const val DISK_TRIM_EVERY_N_WRITES = 24
private const val MAX_LOGGED_FAILURES = 5
private const val HEX_DIGITS = "0123456789abcdef"

// ComponentCallbacks2 trim levels, spelled out because several constants are deprecated on new SDKs.
private const val TRIM_RUNNING_LOW = 10
private const val TRIM_RUNNING_CRITICAL = 15
private const val TRIM_UI_HIDDEN = 20
private const val TRIM_BACKGROUND = 40
private const val TRIM_COMPLETE = 80

/**
 * Loads, processes and caches app icons. Each icon is processed ONCE: the result is kept in a
 * byte-bounded memory LruCache and as a PNG in the disk cache, and is never re-rendered on scroll or
 * recomposition. Concurrent requests for the same key share a single piece of work.
 */
class IconRepository(
    context: Context,
    private val scope: CoroutineScope,
    private val apps: AppRepository,
) {
    private val appContext: Context = context.applicationContext
    private val launcherApps: LauncherApps? = appContext.getSystemService(LauncherApps::class.java)
    private val packageManager: PackageManager = appContext.packageManager
    private val diskDir: File = File(appContext.cacheDir, "icons")
    private val diskLock = Any()
    private val diskWrites = AtomicInteger(0)
    private val failures = AtomicInteger(0)
    private val gate = Semaphore(4)
    private val inFlight = ConcurrentHashMap<String, Deferred<ImageBitmap>>()
    private val packResolver = IconPackResolver(appContext)

    private val memory = object : LruCache<String, ImageBitmap>(MEMORY_CAP_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    /** Memory cache only. Never blocks, safe to call during composition. */
    fun peek(app: AppInfo, request: IconRequest): ImageBitmap? = memory.get(cacheKey(app, request))

    /** memory -> disk -> process. Concurrent calls for the same key process once. */
    suspend fun load(app: AppInfo, request: IconRequest): ImageBitmap {
        val key = cacheKey(app, request)
        val cached = memory.get(key)
        if (cached != null) return cached
        val running = inFlight[key]
        val job: Deferred<ImageBitmap> = running ?: startLoad(key, app, request)
        return job.await()
    }

    /**
     * Called from onTrimMemory. Everything is released on TRIM_MEMORY_COMPLETE / RUNNING_CRITICAL, half
     * under other memory pressure. TRIM_MEMORY_UI_HIDDEN is ignored on purpose: a launcher is hidden
     * every time an app is launched and must not drop its icons then.
     */
    fun trimMemory(level: Int) {
        if (level >= TRIM_COMPLETE || level == TRIM_RUNNING_CRITICAL) {
            memory.evictAll()
            packResolver.clear()
            return
        }
        val halve = level >= TRIM_BACKGROUND || (level >= TRIM_RUNNING_LOW && level < TRIM_UI_HIDDEN)
        if (halve) {
            memory.trimToSize(memory.maxSize() / 2)
        }
    }

    /** Clears memory and disk caches. */
    fun clearAll() {
        memory.evictAll()
        packResolver.clear()
        scope.launch(Dispatchers.IO) {
            try {
                synchronized(diskLock) {
                    val files = diskDir.listFiles()
                    if (files != null) {
                        for (f in files) f.delete()
                    }
                }
            } catch (e: Exception) {
                // Best effort.
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Loading pipeline
    // ---------------------------------------------------------------------------------------------

    private fun startLoad(key: String, app: AppInfo, request: IconRequest): Deferred<ImageBitmap> {
        val created = scope.async(start = CoroutineStart.LAZY) { produce(key, app, request) }
        val previous = inFlight.putIfAbsent(key, created)
        if (previous != null) {
            created.cancel()
            return previous
        }
        created.start()
        return created
    }

    private suspend fun produce(key: String, app: AppInfo, request: IconRequest): ImageBitmap {
        try {
            val inMemory = memory.get(key)
            if (inMemory != null) return inMemory

            val fromDisk = readDisk(key)
            if (fromDisk != null) {
                memory.put(key, fromDisk)
                return fromDisk
            }

            val bitmap = gate.withPermit { render(app, request) }
            val image = bitmap.asImageBitmap()
            memory.put(key, image)
            writeDiskAsync(key, bitmap)
            return image
        } finally {
            inFlight.remove(key)
        }
    }

    private suspend fun render(app: AppInfo, request: IconRequest): Bitmap = withContext(Dispatchers.Default) {
        var drawable: Drawable? = null
        try {
            drawable = loadDrawable(app, request)
        } catch (e: Exception) {
            logFailure("IconRepository.loadDrawable ${app.key.flat}", e)
        }
        try {
            IconProcessor.process(drawable, request)
        } catch (e: OutOfMemoryError) {
            memory.evictAll()
            IconProcessor.process(null, request)
        } catch (e: Exception) {
            logFailure("IconRepository.process ${app.key.flat}", e)
            IconProcessor.process(null, request)
        }
    }

    private fun logFailure(where: String, error: Throwable) {
        if (failures.incrementAndGet() <= MAX_LOGGED_FAILURES) {
            CrashLog.record(where, error)
        }
    }

    private fun loadDrawable(app: AppInfo, request: IconRequest): Drawable? {
        val key = app.key
        if (request.packPackage.isNotEmpty()) {
            val fromPack = packResolver.resolve(request.packPackage, key)
            if (fromPack != null) return fromPack
        }

        val handle = apps.userHandle(key.userSerial) ?: Process.myUserHandle()
        val la = launcherApps
        if (la != null) {
            try {
                val list = la.getActivityList(key.packageName, handle)
                val match = list.firstOrNull { it.componentName.className == key.className } ?: list.firstOrNull()
                if (match != null) {
                    // The work badge is only meaningful for ORIGINAL; a silhouette must not contain it.
                    val badged = app.isWorkProfile && request.style == IconStyle.ORIGINAL
                    val icon: Drawable? = if (badged) match.getBadgedIcon(0) else match.getIcon(0)
                    if (icon != null) return icon
                }
            } catch (e: Exception) {
                // Fall through to the PackageManager path.
            }
        }

        try {
            return packageManager.getActivityIcon(ComponentName(key.packageName, key.className))
        } catch (e: Exception) {
            // Fall through.
        }
        try {
            return packageManager.getApplicationIcon(key.packageName)
        } catch (e: Exception) {
            // Fall through.
        }
        return null
    }

    // ---------------------------------------------------------------------------------------------
    // Cache key
    // ---------------------------------------------------------------------------------------------

    /**
     * package + class + userSerial + versionStamp + the request fields that influence the output
     * (fields a style ignores are left out so that, for example, a theme switch does not invalidate
     * ORIGINAL icons).
     */
    private fun cacheKey(app: AppInfo, r: IconRequest): String {
        val sb = StringBuilder(160)
        sb.append('v').append(PROCESSOR_VERSION).append('|')
        sb.append(app.key.packageName).append('/').append(app.key.className)
        sb.append('#').append(app.key.userSerial)
        sb.append('@').append(app.versionStamp)
        sb.append('|').append(r.style.name)
        sb.append('|').append(r.shape.name)
        sb.append('|').append(r.sizePx)
        if (r.style != IconStyle.ORIGINAL) {
            sb.append('|').append(r.tileColor)
            sb.append('|').append(r.outlineColor)
            sb.append('|').append(r.glyphOnly)
            if (r.style == IconStyle.MONOCHROME_ACCENT) {
                sb.append('|').append(r.accentColor)
            } else {
                sb.append('|').append(r.glyphColor)
            }
        }
        sb.append('|').append(r.packPackage)
        return sb.toString()
    }

    private fun sha1Hex(text: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(text.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            val v = b.toInt() and 0xFF
            sb.append(HEX_DIGITS[v ushr 4])
            sb.append(HEX_DIGITS[v and 0x0F])
        }
        return sb.toString()
    }

    // ---------------------------------------------------------------------------------------------
    // Disk cache
    // ---------------------------------------------------------------------------------------------

    private suspend fun readDisk(key: String): ImageBitmap? = withContext(Dispatchers.IO) {
        try {
            val file = File(diskDir, sha1Hex(key) + ".png")
            if (!file.isFile) {
                null
            } else {
                val options = BitmapFactory.Options()
                options.inScaled = false
                options.inPreferredConfig = Bitmap.Config.ARGB_8888
                val decoded = BitmapFactory.decodeFile(file.absolutePath, options)
                if (decoded == null) {
                    file.delete()
                    null
                } else {
                    // Touch so that eviction by lastModified behaves like LRU.
                    file.setLastModified(System.currentTimeMillis())
                    decoded.asImageBitmap()
                }
            }
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    private fun writeDiskAsync(key: String, bitmap: Bitmap) {
        scope.launch(Dispatchers.IO) {
            try {
                if (!diskDir.isDirectory && !diskDir.mkdirs() && !diskDir.isDirectory) return@launch
                val name = sha1Hex(key)
                val tmp = File(diskDir, "$name.tmp")
                val dest = File(diskDir, "$name.png")
                val written = FileOutputStream(tmp).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                if (!written || !tmp.renameTo(dest)) {
                    tmp.delete()
                }
                if (diskWrites.incrementAndGet() % DISK_TRIM_EVERY_N_WRITES == 1) {
                    trimDisk()
                }
            } catch (e: Exception) {
                // The disk cache is an optimisation only.
            }
        }
    }

    /** Evicts the oldest files (by lastModified) while the cache is over its cap. */
    private fun trimDisk() {
        synchronized(diskLock) {
            val files = diskDir.listFiles() ?: return
            var total = 0L
            for (f in files) total += f.length()
            if (total <= DISK_CAP_BYTES) return
            val oldestFirst = files.sortedBy { it.lastModified() }
            for (f in oldestFirst) {
                if (total <= DISK_TRIM_TARGET_BYTES) break
                val length = f.length()
                if (f.delete()) total -= length
            }
        }
    }
}

/**
 * Optional third-party icon pack support (ADW / Nova style appfilter.xml). Isolated here: any failure
 * simply yields null so the caller falls back to normal processing. The parsed map is cached per pack.
 */
private class IconPackResolver(private val context: Context) {

    private class Pack(
        val resources: Resources?,
        val packageName: String,
        val byComponent: Map<String, String>,
        val byPackage: Map<String, String>,
    )

    private val cache = ConcurrentHashMap<String, Pack>()

    fun clear() {
        cache.clear()
    }

    /** The pack's drawable for [key], or null when the pack has none or anything goes wrong. */
    fun resolve(packPackage: String, key: AppKey): Drawable? {
        try {
            val pack = cache.getOrPut(packPackage) { loadPack(packPackage) }
            val res = pack.resources ?: return null
            val name = pack.byComponent[key.packageName + "/" + key.className]
                ?: pack.byPackage[key.packageName]
                ?: return null
            return drawableFor(res, pack.packageName, name)
        } catch (e: Exception) {
            return null
        }
    }

    private fun loadPack(packPackage: String): Pack {
        val res: Resources = try {
            context.packageManager.getResourcesForApplication(packPackage)
        } catch (e: Exception) {
            return Pack(null, packPackage, emptyMap(), emptyMap())
        }
        val byComponent = HashMap<String, String>()
        val byPackage = HashMap<String, String>()
        try {
            parseAppFilter(res, packPackage, byComponent, byPackage)
        } catch (e: Exception) {
            // Keep whatever was parsed before the failure.
        }
        return Pack(res, packPackage, byComponent, byPackage)
    }

    private fun parseAppFilter(
        res: Resources,
        packPackage: String,
        byComponent: MutableMap<String, String>,
        byPackage: MutableMap<String, String>,
    ) {
        val xmlId = res.getIdentifier("appfilter", "xml", packPackage)
        if (xmlId != 0) {
            val parser = res.getXml(xmlId)
            try {
                readItems(parser, byComponent, byPackage)
            } finally {
                parser.close()
            }
            return
        }
        res.assets.open("appfilter.xml").use { stream ->
            val parser = Xml.newPullParser()
            parser.setInput(stream, "UTF-8")
            readItems(parser, byComponent, byPackage)
        }
    }

    private fun readItems(
        parser: XmlPullParser,
        byComponent: MutableMap<String, String>,
        byPackage: MutableMap<String, String>,
    ) {
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "item") {
                val component = parser.getAttributeValue(null, "component")
                val drawable = parser.getAttributeValue(null, "drawable")
                if (component != null && drawable != null && drawable.isNotEmpty()) {
                    val open = component.indexOf('{')
                    val close = component.lastIndexOf('}')
                    val inner = if (open >= 0 && close > open) component.substring(open + 1, close) else component
                    val slash = inner.indexOf('/')
                    if (slash > 0 && slash < inner.length - 1) {
                        val pkg = inner.substring(0, slash)
                        var cls = inner.substring(slash + 1)
                        if (cls.startsWith(".")) cls = pkg + cls
                        byComponent[pkg + "/" + cls] = drawable
                        if (!byPackage.containsKey(pkg)) byPackage[pkg] = drawable
                    }
                }
            }
            event = parser.next()
        }
    }

    private fun drawableFor(res: Resources, packPackage: String, name: String): Drawable? {
        var id = res.getIdentifier(name, "drawable", packPackage)
        if (id == 0) id = res.getIdentifier(name, "mipmap", packPackage)
        if (id == 0) return null
        return res.getDrawable(id, null)
    }
}
