package io.github.rt993.firetvjellyfin.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The most visually prominent color in a poster/backdrop, for a per-title ambient glow (the
 * "trakt.tv show page" look) - vibrant first since a muted/dominant swatch is often just the
 * image's dark background, which would make the glow invisible against this app's own dark theme.
 */
suspend fun ambientColorFor(context: Context, imageUrl: String): Color? = withContext(Dispatchers.IO) {
    val bitmap = runCatching { Glide.with(context).asBitmap().load(imageUrl).submit().get() }.getOrNull() ?: return@withContext null
    val palette = Palette.Builder(bitmap).generate()
    val swatch = palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.mutedSwatch ?: palette.dominantSwatch
    swatch?.let { Color(it.rgb) }
}
