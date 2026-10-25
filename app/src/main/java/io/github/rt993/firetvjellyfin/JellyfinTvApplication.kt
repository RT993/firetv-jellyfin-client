package io.github.rt993.firetvjellyfin

import android.app.Application
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import io.github.rt993.firetvjellyfin.data.JellyfinClientHolder
import io.github.rt993.firetvjellyfin.util.CrashLogger

// Glide's own default disk cache cap is ~250MB - plenty for a handful of posters, but a real
// library's worth of art, cached separately per screen-specific size (Home's rows want 280px,
// Details wants 400px, the hero carousel wants 440px, the backdrop wants 1920px...), adds up fast
// and starts evicting older entries well before anything about the server's own content actually
// changed. Jellyfin metadata on a personal library is stable for weeks at a time, so there's little
// point treating cached art as disposable - a much larger cap just means posters load instantly
// from disk on every return visit instead of re-fetching over Wi-Fi, for as long as the art itself
// hasn't actually changed server-side.
private const val IMAGE_DISK_CACHE_BYTES = 500L * 1024 * 1024

class JellyfinTvApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Must happen before anything else touches Glide (Glide.with()/GlideImage) anywhere in the
        // process - this app has no Glide annotation-processor module set up to configure the
        // singleton at compile time, so a plain runtime Glide.init() is how it's done instead; it
        // only takes effect if it runs before Glide's own default lazy-init does.
        Glide.init(this, GlideBuilder().setDiskCache(InternalCacheDiskCacheFactory(this, IMAGE_DISK_CACHE_BYTES)))
        CrashLogger.install(this)
        JellyfinClientHolder.initialize(this)
    }
}
