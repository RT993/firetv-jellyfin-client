package io.github.rt993.firetvjellyfin

import android.app.Application
import io.github.rt993.firetvjellyfin.data.JellyfinClientHolder

class JellyfinTvApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        JellyfinClientHolder.initialize(this)
    }
}
