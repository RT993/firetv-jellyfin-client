package io.github.rt993.firetvjellyfin

import android.app.Application
import com.bumptech.glide.Glide
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.model.GlideUrl
import io.github.rt993.firetvjellyfin.data.JellyfinClientHolder
import io.github.rt993.firetvjellyfin.util.CrashLogger
import io.github.rt993.firetvjellyfin.util.GlideTimingEventListenerFactory
import okhttp3.OkHttpClient
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
        //
        // Switching to OkHttp cut the delay from ~60s to ~10s. A second timing capture after
        // raising OkHttp's per-host connection cap (tried here briefly, since the first capture
        // showed later requests queued behind the default cap of 5) showed that change made no
        // real difference - total time was about the same, sometimes worse. That rules out
        // connection queueing as the bottleneck: it's genuinely slow TCP connect (1.4-2.7s to a
        // LAN device, which should normally be single-digit ms) and slow server response time on
        // every request, consistent with something server/network-side (a blocking reverse-DNS
        // lookup on accept is the classic cause of exactly this symptom) rather than anything
        // fixable from this client - reverted to OkHttp's own default dispatcher accordingly.
        // GlideTimingLogger stays in; still useful if this needs another look later.
        val okHttpClient = OkHttpClient.Builder().eventListenerFactory(GlideTimingEventListenerFactory).build()
        Glide.get(this).registry.replace(GlideUrl::class.java, InputStream::class.java, OkHttpUrlLoader.Factory(okHttpClient))
        CrashLogger.install(this)
        JellyfinClientHolder.initialize(this)
    }
}
