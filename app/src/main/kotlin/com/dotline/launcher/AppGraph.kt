package com.dotline.launcher

import android.app.Application
import android.content.Context
import com.dotline.launcher.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Hand-rolled service locator holding the process-wide singletons. Created once in
 * [DotlineApp.onCreate]; read it anywhere with `context.graph`.
 */
class AppGraph(val app: Application) {
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsRepository(app, scope)
}

val Context.graph: AppGraph
    get() = (applicationContext as DotlineApp).graph
