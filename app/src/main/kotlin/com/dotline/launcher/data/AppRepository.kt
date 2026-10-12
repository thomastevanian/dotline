package com.dotline.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.compose.runtime.Immutable
import com.dotline.launcher.core.CrashLog
import com.dotline.launcher.data.model.AppInfo
import com.dotline.launcher.data.model.AppKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.text.Collator
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/** One app shortcut (dynamic, manifest or pinned) as shown in an app's long-press menu. */
@Immutable
data class AppShortcut(
    val id: String,
    val packageName: String,
    val label: String,
    val userSerial: Long,
)

/**
 * Source of truth for every launchable activity of every user profile (including the work profile).
 *
 * - [apps] is sorted by label with a locale [Collator] and republished as an immutable list.
 * - All loading happens on Dispatchers.Default; every state change is serialised by one [Mutex].
 * - Package changes arrive through a [LauncherApps.Callback] and are coalesced (a short debounce) into
 *   per-package reloads, so a bulk Play Store update does not republish the list once per package.
 * - Labels are cached per [AppKey] and only reloaded when the package's version stamp changes.
 *
 * The blocking members ([shortcuts], [launch], [openAppInfo], [requestUninstall], [launchShortcut],
 * [userHandle]) are cheap binder calls meant for user actions, never for composition.
 */
class AppRepository(context: Context, private val scope: CoroutineScope) {

    private val appContext: Context = context.applicationContext
    private val launcherApps: LauncherApps =
        appContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val userManager: UserManager =
        appContext.getSystemService(Context.USER_SERVICE) as UserManager
    private val pm: PackageManager = appContext.packageManager

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())

    /** Every launchable activity of every profile, sorted by label then package. */
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    private val _loaded = MutableStateFlow(false)

    /** False until the first full load finished. */
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    /** Lookup table mirroring [apps]; replaced (never mutated) just before [apps] is updated. */
    @Volatile
    private var index: Map<AppKey, AppInfo> = emptyMap()

    private val started = AtomicBoolean(false)

    /** Serialises every read-modify-write of [_apps] and of the fields below it. */
    private val mutex = Mutex()

    // Guarded by [mutex].
    private val labelCache = HashMap<AppKey, CachedLabel>()
    private var labelLocale: Locale = Locale.getDefault()
    private var knownSerials: Set<Long> = emptySet()

    // Package events waiting to be applied. Guarded by [pendingLock].
    private val pendingLock = Any()
    private val pending = HashMap<PendingUpdate, Boolean>()
    private val drainScheduled = AtomicBoolean(false)

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) {
            enqueue(listOf(packageName), user, true)
        }

        override fun onPackageAdded(packageName: String, user: UserHandle) {
            enqueue(listOf(packageName), user, false)
        }

        override fun onPackageChanged(packageName: String, user: UserHandle) {
            enqueue(listOf(packageName), user, false)
        }

        override fun onPackagesAvailable(
            packageNames: Array<out String>,
            user: UserHandle,
            replacing: Boolean,
        ) {
            enqueue(packageNames.asList(), user, false)
        }

        override fun onPackagesUnavailable(
            packageNames: Array<out String>,
            user: UserHandle,
            replacing: Boolean,
        ) {
            enqueue(packageNames.asList(), user, !replacing)
        }

        override fun onPackagesSuspended(packageNames: Array<out String>, user: UserHandle) {
            enqueue(packageNames.asList(), user, false)
        }

        override fun onPackagesUnsuspended(packageNames: Array<out String>, user: UserHandle) {
            enqueue(packageNames.asList(), user, false)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------------------------

    /**
     * Starts the initial asynchronous load and registers for package changes. Idempotent: only the
     * first call does anything.
     */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        // Register first so a package change during the initial load is not missed; the resulting
        // update waits on the mutex until the initial load is done.
        try {
            launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            CrashLog.record("AppRepository.registerCallback", e)
        }
        scope.launch(Dispatchers.Default) {
            guarded("AppRepository.initialLoad") {
                mutex.withLock { loadAllLocked() }
            }
            // Never leave the UI waiting forever, even if the load failed (the error is in CrashLog).
            _loaded.value = true
        }
    }

    /** Reloads everything from scratch (for example after a locale change). Safe to call any time. */
    fun refresh() {
        scope.launch(Dispatchers.Default) {
            guarded("AppRepository.refresh") {
                mutex.withLock { loadAllLocked() }
            }
        }
    }

    fun find(key: AppKey): AppInfo? = index[key]

    /** Starts the app's main activity. Never throws; failures are recorded in [CrashLog]. */
    fun launch(key: AppKey, sourceBounds: Rect? = null): Boolean {
        val user = userHandle(key.userSerial) ?: return missingUser("launch", key.userSerial)
        return attempt("AppRepository.launch ${key.flat}") {
            launcherApps.startMainActivity(
                ComponentName(key.packageName, key.className),
                user,
                sourceBounds,
                null,
            )
        }
    }

    /** Opens the system "App info" screen for the app. Never throws. */
    fun openAppInfo(key: AppKey, sourceBounds: Rect? = null): Boolean {
        val user = userHandle(key.userSerial) ?: return missingUser("openAppInfo", key.userSerial)
        return attempt("AppRepository.openAppInfo ${key.flat}") {
            launcherApps.startAppDetailsActivity(
                ComponentName(key.packageName, key.className),
                user,
                sourceBounds,
                null,
            )
        }
    }

    /** Opens the system uninstall confirmation for the app (work profile aware). Never throws. */
    fun requestUninstall(key: AppKey): Boolean {
        val user = userHandle(key.userSerial) ?: return missingUser("requestUninstall", key.userSerial)
        return attempt("AppRepository.requestUninstall ${key.flat}") {
            val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", key.packageName, null))
            intent.putExtra(Intent.EXTRA_USER, user)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
        }
    }

    fun userHandle(serial: Long): UserHandle? {
        return try {
            userManager.getUserForSerialNumber(serial)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Dynamic, manifest and pinned shortcuts of the app, best ranked first. Empty when Dotline is
     * not the default launcher (the system then denies access) or the profile is locked.
     */
    fun shortcuts(key: AppKey): List<AppShortcut> {
        val user = userHandle(key.userSerial) ?: return emptyList()
        val result: List<AppShortcut> = try {
            val query = LauncherApps.ShortcutQuery()
            query.setPackage(key.packageName)
            query.setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
            )
            val found: List<ShortcutInfo>? = launcherApps.getShortcuts(query, user)
            if (found == null) {
                emptyList<AppShortcut>()
            } else {
                mapShortcuts(found, key)
            }
        } catch (e: SecurityException) {
            emptyList<AppShortcut>()
        } catch (e: IllegalStateException) {
            emptyList<AppShortcut>()
        } catch (e: Exception) {
            CrashLog.record("AppRepository.shortcuts ${key.flat}", e)
            emptyList<AppShortcut>()
        }
        return result
    }

    fun launchShortcut(shortcut: AppShortcut, sourceBounds: Rect? = null): Boolean {
        val user = userHandle(shortcut.userSerial) ?: return missingUser("launchShortcut", shortcut.userSerial)
        return attempt("AppRepository.launchShortcut ${shortcut.packageName}/${shortcut.id}") {
            launcherApps.startShortcut(shortcut.packageName, shortcut.id, sourceBounds, null, user)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Actions
    // ---------------------------------------------------------------------------------------------

    private inline fun attempt(where: String, block: () -> Unit): Boolean {
        return try {
            block()
            true
        } catch (e: Exception) {
            CrashLog.record(where, e)
            false
        }
    }

    private fun missingUser(where: String, serial: Long): Boolean {
        CrashLog.record("AppRepository.$where", IllegalStateException("No user for serial $serial"))
        return false
    }

    private fun mapShortcuts(found: List<ShortcutInfo>, key: AppKey): List<AppShortcut> {
        val out = ArrayList<AppShortcut>(found.size)
        for (info in found.sortedBy { it.rank }) {
            if (!info.isEnabled) continue
            val id: String = info.id
            val raw: String? = (info.shortLabel ?: info.longLabel)?.toString()
            val label: String = if (raw.isNullOrBlank()) id else raw.trim()
            out.add(AppShortcut(id, key.packageName, label, key.userSerial))
        }
        return out
    }

    // ---------------------------------------------------------------------------------------------
    // Package events
    // ---------------------------------------------------------------------------------------------

    private fun enqueue(packageNames: List<String>, user: UserHandle, removed: Boolean) {
        if (packageNames.isEmpty()) return
        synchronized(pendingLock) {
            for (name in packageNames) {
                val change = PendingUpdate(name, user)
                val previous: Boolean = pending[change] ?: false
                pending[change] = previous || removed
            }
        }
        scheduleDrain()
    }

    private fun scheduleDrain() {
        if (!drainScheduled.compareAndSet(false, true)) return
        scope.launch(Dispatchers.Default) {
            delay(UPDATE_DEBOUNCE_MS)
            // Reset the flag BEFORE taking the snapshot: an event arriving after this point schedules
            // another drain instead of being lost.
            drainScheduled.set(false)
            val batch: Map<PendingUpdate, Boolean> = synchronized(pendingLock) {
                val copy = HashMap<PendingUpdate, Boolean>(pending)
                pending.clear()
                copy
            }
            if (batch.isEmpty()) return@launch
            guarded("AppRepository.packageUpdate") {
                mutex.withLock { applyBatchLocked(batch) }
            }
        }
    }

    private suspend fun guarded(where: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CrashLog.record(where, e)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Loading (all of these must be called with [mutex] held, on a background thread)
    // ---------------------------------------------------------------------------------------------

    private fun loadAllLocked() {
        resetLabelCacheOnLocaleChange()
        val profiles: List<UserHandle> = userManager.userProfiles
        val stamps: Map<String, Long> = installedStamps()
        val myUser: UserHandle = Process.myUserHandle()
        val previous: List<AppInfo> = _apps.value
        val result = ArrayList<AppInfo>(previous.size + 16)
        val serials = HashSet<Long>()

        for (user in profiles) {
            val serial: Long = userManager.getSerialNumberForUser(user)
            if (serial < 0L) continue
            serials.add(serial)
            val isWork: Boolean = user != myUser
            try {
                val activities: List<LauncherActivityInfo> = launcherApps.getActivityList(null, user)
                val forUser = ArrayList<AppInfo>(activities.size)
                for (activity in activities) {
                    val stamp: Long? = stamps[activity.componentName.packageName]
                    forUser.add(buildInfo(activity, serial, isWork, stamp))
                }
                result.addAll(forUser)
            } catch (e: Exception) {
                // Keep what we already knew about this profile rather than dropping all of its apps.
                CrashLog.record("AppRepository.loadProfile $serial", e)
                for (old in previous) {
                    if (old.key.userSerial == serial) result.add(old)
                }
            }
        }

        val sorted: List<AppInfo> = sortApps(result)
        val liveKeys = HashSet<AppKey>(sorted.size * 2)
        for (app in sorted) liveKeys.add(app.key)
        labelCache.keys.retainAll(liveKeys)
        knownSerials = serials
        publish(sorted)
        _loaded.value = true
    }

    private fun applyBatchLocked(batch: Map<PendingUpdate, Boolean>) {
        resetLabelCacheOnLocaleChange()

        // A profile appeared or disappeared (or we have not loaded yet): a full reload is the
        // simplest correct answer and happens rarely.
        val live: Set<Long>? = liveSerials()
        if (live != null && live != knownSerials) {
            loadAllLocked()
            return
        }

        val myUser: UserHandle = Process.myUserHandle()
        var working: List<AppInfo> = _apps.value
        for ((change, removedHint) in batch) {
            val serial: Long = userManager.getSerialNumberForUser(change.user)
            if (serial < 0L) continue
            val packageName: String = change.packageName
            val isWork: Boolean = change.user != myUser

            val fresh: List<AppInfo>? = try {
                queryPackage(packageName, change.user, serial, isWork)
            } catch (e: Exception) {
                if (!removedHint) CrashLog.record("AppRepository.reloadPackage $packageName", e)
                null
            }
            // The query failed and nothing says the package is gone: keep what we have.
            if (fresh == null && !removedHint) continue

            val kept = ArrayList<AppInfo>(working.size)
            for (app in working) {
                if (app.key.packageName == packageName && app.key.userSerial == serial) continue
                kept.add(app)
            }
            if (fresh != null) kept.addAll(fresh)
            if (fresh == null || fresh.isEmpty()) evictLabels(packageName, serial)
            working = kept
        }

        if (working == _apps.value) return
        publish(sortApps(working))
    }

    /** Fresh entries for one package of one profile; empty when the package no longer has launcher activities. */
    private fun queryPackage(
        packageName: String,
        user: UserHandle,
        serial: Long,
        isWork: Boolean,
    ): List<AppInfo> {
        // A package event means its label may have changed, so never reuse cached labels for it.
        evictLabels(packageName, serial)
        val activities: List<LauncherActivityInfo> = launcherApps.getActivityList(packageName, user)
        if (activities.isEmpty()) return emptyList<AppInfo>()
        val stamp: Long? = packageStamp(packageName)
        val out = ArrayList<AppInfo>(activities.size)
        for (activity in activities) {
            out.add(buildInfo(activity, serial, isWork, stamp))
        }
        return out
    }

    private fun buildInfo(
        activity: LauncherActivityInfo,
        serial: Long,
        isWork: Boolean,
        knownStamp: Long?,
    ): AppInfo {
        val component: ComponentName = activity.componentName
        val key = AppKey(component.packageName, component.className, serial)
        val appInfo: ApplicationInfo = activity.applicationInfo
        val stamp: Long = knownStamp ?: apkStamp(appInfo)
        return AppInfo(
            key = key,
            label = labelFor(activity, key, stamp),
            isWorkProfile = isWork,
            isSuspended = (appInfo.flags and ApplicationInfo.FLAG_SUSPENDED) != 0,
            versionStamp = stamp,
            installTime = activity.firstInstallTime,
        )
    }

    private fun labelFor(activity: LauncherActivityInfo, key: AppKey, stamp: Long): String {
        val cached: CachedLabel? = labelCache[key]
        if (cached != null && stamp != 0L && cached.stamp == stamp) return cached.label
        val raw: String? = activity.label?.toString()?.trim()
        val label: String = if (raw.isNullOrEmpty()) key.packageName else raw
        labelCache[key] = CachedLabel(label, stamp)
        return label
    }

    private fun evictLabels(packageName: String, serial: Long) {
        labelCache.keys.removeAll { it.packageName == packageName && it.userSerial == serial }
    }

    private fun resetLabelCacheOnLocaleChange() {
        val now: Locale = Locale.getDefault()
        if (now != labelLocale) {
            labelCache.clear()
            labelLocale = now
        }
    }

    private fun liveSerials(): Set<Long>? {
        return try {
            val out = HashSet<Long>()
            for (user in userManager.userProfiles) {
                val serial: Long = userManager.getSerialNumberForUser(user)
                if (serial >= 0L) out.add(serial)
            }
            out
        } catch (e: Exception) {
            null
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Version stamps (part of the icon cache key)
    // ---------------------------------------------------------------------------------------------

    /** Stamp for every package visible to the current user, from one PackageManager pass. */
    @Suppress("DEPRECATION")
    private fun installedStamps(): Map<String, Long> {
        return try {
            val packages: List<PackageInfo> = pm.getInstalledPackages(0)
            val map = HashMap<String, Long>(packages.size * 2)
            for (info in packages) {
                map[info.packageName] = stampOf(info)
            }
            map
        } catch (e: Exception) {
            CrashLog.record("AppRepository.installedStamps", e)
            emptyMap<String, Long>()
        }
    }

    @Suppress("DEPRECATION")
    private fun packageStamp(packageName: String): Long? {
        return try {
            stampOf(pm.getPackageInfo(packageName, 0))
        } catch (e: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun stampOf(info: PackageInfo): Long {
        val version: Long = if (Build.VERSION.SDK_INT >= 28) {
            info.longVersionCode
        } else {
            info.versionCode.toLong()
        }
        return info.lastUpdateTime xor version
    }

    /**
     * Fallback for packages PackageManager cannot describe to us (installed only in another profile):
     * the APK file's modification time changes whenever the package is replaced. 0L when unknown.
     */
    private fun apkStamp(appInfo: ApplicationInfo): Long {
        return try {
            val dir: String? = appInfo.sourceDir
            if (dir.isNullOrEmpty()) 0L else File(dir).lastModified()
        } catch (e: Exception) {
            0L
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Sorting and publishing
    // ---------------------------------------------------------------------------------------------

    private fun sortApps(list: List<AppInfo>): List<AppInfo> {
        val collator: Collator = Collator.getInstance(Locale.getDefault())
        return list.sortedWith(Comparator<AppInfo> { a, b -> compareApps(collator, a, b) })
    }

    private fun compareApps(collator: Collator, a: AppInfo, b: AppInfo): Int {
        val byLabel: Int = collator.compare(a.label, b.label)
        if (byLabel != 0) return byLabel
        val byPackage: Int = a.key.packageName.compareTo(b.key.packageName)
        if (byPackage != 0) return byPackage
        val byClass: Int = a.key.className.compareTo(b.key.className)
        if (byClass != 0) return byClass
        return a.key.userSerial.compareTo(b.key.userSerial)
    }

    private fun publish(sorted: List<AppInfo>) {
        val map = HashMap<AppKey, AppInfo>(sorted.size * 2)
        for (app in sorted) map[app.key] = app
        index = map
        _apps.update { sorted }
    }

    // ---------------------------------------------------------------------------------------------

    private class CachedLabel(val label: String, val stamp: Long)

    /** A package of one profile whose launcher entries must be re-read. */
    private data class PendingUpdate(val packageName: String, val user: UserHandle)

    private companion object {
        /** Package events within this window are merged into one republish. */
        const val UPDATE_DEBOUNCE_MS = 120L
    }
}
