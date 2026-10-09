package com.dotline.launcher

import android.app.Application
import com.dotline.launcher.core.CrashLog

class DotlineApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        graph = AppGraph(this)
    }
}
