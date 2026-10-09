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

    suspend fun apply(context: Context, bitmap: Bitmap, target: Target): Result<Unit> {
        val appContext = context.applicationContext
        return withContext(Dispatchers.IO) {
            runCatching {
                val manager = WallpaperManager.getInstance(appContext)
                val flags = when (target) {
                    Target.HOME -> WallpaperManager.FLAG_SYSTEM
                    Target.LOCK -> WallpaperManager.FLAG_LOCK
                    Target.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                }
                manager.setBitmap(bitmap, null, true, flags)
                Unit
            }.onFailure { error ->
                CrashLog.record("WallpaperApplier.apply " + target.name, error)
            }
        }
    }
}
