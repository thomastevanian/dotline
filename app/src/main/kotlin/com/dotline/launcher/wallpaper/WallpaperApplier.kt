package com.dotline.launcher.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import com.dotline.launcher.core.CrashLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sets a rendered bitmap as the system wallpaper. Needs only the normal SET_WALLPAPER permission
 * (no runtime prompt). The call is blocking, so it runs on Dispatchers.IO.
 */
object WallpaperApplier {
    enum class Target { HOME, LOCK, BOTH }

    /** The device or its policy does not allow a wallpaper change: a normal outcome, not a crash. */
    private class NotAllowed(message: String) : Exception(message)

    /**
     * Applies [bitmap] to the screens named by [target]. The result is a failure with a readable
     * message when the device cannot or may not change its wallpaper, or when the system refuses
     * the image; unexpected failures are also written to the crash log.
     */
    suspend fun apply(context: Context, bitmap: Bitmap, target: Target): Result<Unit> {
        val appContext = context.applicationContext
        return withContext(Dispatchers.IO) {
            runCatching {
                val manager = WallpaperManager.getInstance(appContext)
                if (!manager.isWallpaperSupported) {
                    throw NotAllowed("This device does not support changing the wallpaper.")
                }
                if (!manager.isSetWallpaperAllowed) {
                    throw NotAllowed("Changing the wallpaper is not allowed on this device.")
                }
                val flags = when (target) {
                    Target.HOME -> WallpaperManager.FLAG_SYSTEM
                    Target.LOCK -> WallpaperManager.FLAG_LOCK
                    Target.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                }
                manager.setBitmap(bitmap, null, true, flags)
                Unit
            }.onFailure { error ->
                if (error !is NotAllowed) {
                    CrashLog.record("WallpaperApplier.apply " + target.name, error)
                }
            }
        }
    }
}
