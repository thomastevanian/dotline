package com.dotline.launcher.data

import android.content.Context
import com.dotline.launcher.core.CrashLog
import com.dotline.launcher.data.model.AppKey
import com.dotline.launcher.data.model.HomeLayout
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the home controller needs from the layout storage (the real one is [LayoutRepository]). */
interface LayoutStore {
    val layout: StateFlow<HomeLayout>

    /** Applies [transform]; returning null (impossible edit) leaves the layout unchanged. Runs synchronously. */
    fun update(transform: (HomeLayout) -> HomeLayout?)
}

/** Short unique ids for home items. */
object LayoutIds {
    fun newId(): String = "i" + UUID.randomUUID().toString().replace("-", "").take(10)
}

/**
 * Owns the persisted home layout. [layout] always has a value; [ready] turns true once the file
 * has been read (or the first-run default built) so the UI can avoid flashing an empty home.
 *
 * Edits go through [update]; they are applied atomically on a single coroutine, normalised to the
 * current grid size, and written to disk (debounced) as JSON.
 */
class LayoutRepository(
    context: Context,
    private val scope: CoroutineScope,
    private val apps: AppRepository,
    private val settings: SettingsRepository,
) : LayoutStore {
    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, "layout.json")
    private val _layout = MutableStateFlow(HomeLayout())
    override val layout: StateFlow<HomeLayout> = _layout.asStateFlow()
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private var started = false
    private var persistJob: Job? = null

    fun start() {
        if (started) return
        started = true
        scope.launch {
            val loaded = withContext(Dispatchers.IO) { readFromDisk() }
            val s = settings.settings.first { settings.loaded.value }
            if (loaded != null) {
                _layout.value = LayoutEngine.normalize(loaded, s.gridColumns, s.gridRows)
            } else {
                apps.loaded.first { it }
                _layout.value = DefaultLayout.build(apps.apps.value, s.gridColumns, s.gridRows, LayoutIds::newId)
                schedulePersist()
            }
            _ready.value = true

            // Re-fit the layout whenever the grid size changes.
            launch {
                settings.settings.map { it.gridColumns to it.gridRows }.distinctUntilChanged().collect { (c, r) ->
                    val current = _layout.value
                    val fitted = LayoutEngine.normalize(current, c, r)
                    if (fitted != current) {
                        _layout.value = fitted
                        schedulePersist()
                    }
                }
            }
            // Drop placements of apps that are really gone (uninstalled), never merely hidden or unavailable.
            launch {
                combine(apps.apps, apps.loaded) { list, ok -> if (ok) list else null }.collect { list ->
                    if (list != null) pruneUninstalled()
                }
            }
        }
    }

    /** Applies [transform]; returning null (impossible edit) leaves the layout unchanged. */
    override fun update(transform: (HomeLayout) -> HomeLayout?) {
        synchronized(this) {
            val s = settings.settings.value
            val next = transform(_layout.value) ?: return
            // Empty trailing pages are kept while editing (a dragged item may land on a fresh page);
            // HomeController trims them when the edit ends.
            val fitted = LayoutEngine.normalize(next, s.gridColumns, s.gridRows, trim = false)
            if (fitted == _layout.value) return
            _layout.value = fitted
        }
        schedulePersist()
    }

    /** Replaces the whole layout (restore from backup). */
    fun replace(new: HomeLayout) = update { new }

    private fun pruneUninstalled() {
        val pm = appContext.packageManager
        val gone = LayoutEngine.allApps(_layout.value).filter { key -> apps.find(key) == null && !isInstalled(pm, key) }
        if (gone.isNotEmpty()) update { LayoutEngine.removeApps(it, gone.toSet()) }
    }

    @Suppress("DEPRECATION")
    private fun isInstalled(pm: android.content.pm.PackageManager, key: AppKey): Boolean {
        // Only judge the owner's apps: work-profile packages are invisible to us when the profile is paused.
        if (apps.find(key) != null) return true
        val mainSerial = apps.apps.value.firstOrNull { !it.isWorkProfile }?.key?.userSerial
        if (mainSerial == null || key.userSerial != mainSerial) return true
        return try {
            pm.getApplicationInfo(key.packageName, 0)
            true
        } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun schedulePersist() {
        persistJob?.cancel()
        persistJob = scope.launch {
            delay(400)
            val snapshot = _layout.value
            withContext(Dispatchers.IO) { writeToDisk(snapshot) }
        }
    }

    private fun readFromDisk(): HomeLayout? {
        if (!file.exists()) return null
        return runCatching { LayoutJson.decode(file.readText()) }
            .onFailure { CrashLog.record("layout read", it) }
            .getOrNull()
    }

    private fun writeToDisk(layout: HomeLayout) {
        runCatching {
            val tmp = File(file.parentFile, "layout.json.tmp")
            tmp.writeText(LayoutJson.encode(layout).toString())
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        }.onFailure { CrashLog.record("layout write", it) }
    }
}
