package com.dotline.launcher

import android.app.Application
import android.content.Context
import com.dotline.launcher.data.AppRepository
import com.dotline.launcher.data.LayoutRepository
import com.dotline.launcher.data.NotesRepository
import com.dotline.launcher.data.SettingsRepository
import com.dotline.launcher.data.icons.IconRepository
import com.dotline.launcher.data.weather.WeatherRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Hand-rolled service locator holding the process-wide singletons. Created once in
 * [DotlineApp.onCreate]; read it anywhere with `context.graph`.
 */
class AppGraph(val app: Application) {
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsRepository(app, scope)
    val apps = AppRepository(app, scope)
    val icons = IconRepository(app, scope, apps)
    val layout = LayoutRepository(app, scope, apps, settings)
    val weather = WeatherRepository(app, scope, settings)
    val notes = NotesRepository(app, scope)

    /** Emits whenever the Home button is pressed while Dotline is already the foreground launcher. */
    val homePressed = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    fun start() {
        apps.start()
        layout.start()
    }

    fun onTrimMemory(level: Int) {
        icons.trimMemory(level)
    }
}

val Context.graph: AppGraph
    get() = (applicationContext as DotlineApp).graph
