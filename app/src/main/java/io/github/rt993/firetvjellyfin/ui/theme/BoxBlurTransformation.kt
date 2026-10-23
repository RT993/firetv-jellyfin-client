package io.github.rt993.firetvjellyfin.ui.theme

import android.graphics.Bitmap
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

private const val ID = "io.github.rt993.firetvjellyfin.ui.theme.BoxBlurTransformation"
private val ID_BYTES = ID.toByteArray(StandardCharsets.UTF_8)

/**
 * A plain, dependency-free box blur (repeated a couple of times, which approximates a Gaussian
 * closely enough for a blur-up placeholder - see [DynamicBlueBackground]'s own reasoning for why
 * this app avoids real-time/RenderEffect-based blur entirely). Meant to run only on the tiny
 * thumbnail request backing a screen's blur-up placeholder (see DetailsScreen's DetailsBackdrop),
 * never on a full-resolution image - at thumbnail size its O(width*height*radius) cost is trivial,
 * and it runs on Glide's own background decode thread, not the UI thread.
 */
class BoxBlurTransformation(private val radius: Int = 12, private val passes: Int = 2) : BitmapTransformation() {

    override fun transform(pool: BitmapPool, toTransform: Bitmap, outWidth: Int, outHeight: Int): Bitmap {
        val width = toTransform.width
        val height = toTransform.height
        val pixels = IntArray(width * height)
        toTransform.getPixels(pixels, 0, width, 0, 0, width, height)
        repeat(passes) {
            boxBlurPass(pixels, width, height, radius, horizontal = true)
            boxBlurPass(pixels, width, height, radius, horizontal = false)
        }
        val result = pool.get(width, height, toTransform.config ?: Bitmap.Config.ARGB_8888)
        result.setHasAlpha(toTransform.hasAlpha())
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    private fun boxBlurPass(pixels: IntArray, width: Int, height: Int, radius: Int, horizontal: Boolean) {
        val lineLength = if (horizontal) width else height
        val lineCount = if (horizontal) height else width
        val line = IntArray(lineLength)
        for (i in 0 until lineCount) {
            for (j in 0 until lineLength) {
                line[j] = if (horizontal) pixels[i * width + j] else pixels[j * width + i]
            }
            for (j in 0 until lineLength) {
                var a = 0
                var r = 0
                var g = 0
                var b = 0
                var count = 0
                for (d in -radius..radius) {
                    val k = j + d
                    if (k in 0 until lineLength) {
                        val p = line[k]
                        a += (p ushr 24) and 0xFF
                        r += (p ushr 16) and 0xFF
                        g += (p ushr 8) and 0xFF
                        b += p and 0xFF
                        count++
                    }
                }
                val averaged = ((a / count) shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
                if (horizontal) pixels[i * width + j] = averaged else pixels[j * width + i] = averaged
            }
        }
    }

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update(ID_BYTES)
        messageDigest.update(ByteBuffer.allocate(8).putInt(radius).putInt(passes).array())
    }

    override fun equals(other: Any?): Boolean =
        other is BoxBlurTransformation && other.radius == radius && other.passes == passes

    override fun hashCode(): Int = (ID.hashCode() * 31 + radius) * 31 + passes
}
