package io.github.rt993.firetvjellyfin

import android.app.Application
import com.bumptech.glide.Glide
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.model.GlideUrl
import io.github.rt993.firetvjellyfin.data.JellyfinClientHolder
import io.github.rt993.firetvjellyfin.util.CrashLogger
import java.io.InputStream

class JellyfinTvApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Glide's own default network loader is HttpURLConnection-based - a separate, more
        // primitive stack from the OkHttp-backed client the Jellyfin SDK already uses for every
        // other request, and independently paying its own slow first-connection cost (DNS lookup,
        // TLS/TCP setup) is what was behind images taking a very long time to even start loading:
        // the SDK's own client was already warm from sign-in, Glide's never had a reason to be.
        // Glide.get() is the normal, safe lazy-init call (unlike the Glide.init() approach tried
        // earlier - no startup-timing dependency, no "already initialized" failure mode), so this
        // just swaps the loader on the singleton Glide already creates for itself.
        Glide.get(this).registry.replace(GlideUrl::class.java, InputStream::class.java, OkHttpUrlLoader.Factory())
        CrashLogger.install(this)
        JellyfinClientHolder.initialize(this)
    }
}
