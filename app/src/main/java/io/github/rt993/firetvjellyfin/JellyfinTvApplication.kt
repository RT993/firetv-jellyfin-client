package io.github.rt993.firetvjellyfin

import android.app.Application
import io.github.rt993.firetvjellyfin.data.JellyfinClientHolder
import io.github.rt993.firetvjellyfin.util.CrashLogger

class JellyfinTvApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
        JellyfinClientHolder.initialize(this)
    }
}
