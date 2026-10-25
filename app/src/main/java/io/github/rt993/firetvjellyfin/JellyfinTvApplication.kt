package io.github.rt993.firetvjellyfin

import android.app.Application
import com.bumptech.glide.Glide
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.model.GlideUrl
import io.github.rt993.firetvjellyfin.data.JellyfinClientHolder
import io.github.rt993.firetvjellyfin.util.CrashLogger
import io.github.rt993.firetvjellyfin.util.GlideTimingEventListenerFactory
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.io.InputStream

// A logged timing capture showed TCP connect (~1.4s) and server response (~1.6s) are each slow but
// unavoidable from here - what actually compounded that into a 12s+ total was OkHttp's default
// per-host connection cap (5): with a whole screen's worth of poster rows wanting images at once,
// only the first 5 could even start connecting, and everything past that queued, each later batch
// paying its own ~1.4s connect tax serially instead of all of them paying it at once. This is a
// local, single-server connection with no reason to hold back - letting far more requests through
// at once means the whole visible screen's worth of images finishes around the same ~3s mark
// instead of stretching out as later ones wait their turn.
private const val MAX_CONCURRENT_IMAGE_REQUESTS = 24

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
        //
        // Switching to OkHttp cut the delay from ~60s to ~10s, not to zero - GlideTimingLogger is
        // temporary instrumentation (kept in for now to confirm this next change actually helps)
        // that showed exactly where that remaining time goes - see MAX_CONCURRENT_IMAGE_REQUESTS.
        val dispatcher = Dispatcher().apply {
            maxRequests = MAX_CONCURRENT_IMAGE_REQUESTS
            maxRequestsPerHost = MAX_CONCURRENT_IMAGE_REQUESTS
        }
        val okHttpClient = OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .eventListenerFactory(GlideTimingEventListenerFactory)
            .build()
        Glide.get(this).registry.replace(GlideUrl::class.java, InputStream::class.java, OkHttpUrlLoader.Factory(okHttpClient))
        CrashLogger.install(this)
        JellyfinClientHolder.initialize(this)
    }
}
