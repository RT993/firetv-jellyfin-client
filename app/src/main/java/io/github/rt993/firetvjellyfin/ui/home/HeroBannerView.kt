package io.github.rt993.firetvjellyfin.ui.home

import android.content.Context
import android.graphics.Outline
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import io.github.rt993.firetvjellyfin.R
import io.github.rt993.firetvjellyfin.data.JellyfinRepository
import io.github.rt993.firetvjellyfin.util.formatRuntimeTicks
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType

/**
 * Large backdrop panel above the Recently Added row. Purely a display surface driven by
 * whichever card in that row currently has D-pad focus - it takes no focus and no input of its
 * own, unlike a Leanback row (which only supports a horizontally scrolling list, not "one big
 * item's title/description/backdrop").
 */
class HeroBannerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val heroImage: ImageView
    private val heroMeta: TextView
    private val heroTitle: TextView
    private val heroDescription: TextView

    init {
        LayoutInflater.from(context).inflate(R.layout.view_hero_banner, this, true)
        heroImage = findViewById(R.id.hero_image)
        heroMeta = findViewById(R.id.hero_meta)
        heroTitle = findViewById(R.id.hero_title)
        heroDescription = findViewById(R.id.hero_description)

        foreground = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.hero_banner_border)
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, dpToPx(24))
            }
        }
        clipToOutline = true
    }

    /** Updates the panel to reflect [item], the currently focused card in the row below. */
    fun showItem(item: BaseItemDto, repository: JellyfinRepository) {
        heroTitle.text = item.name
        heroDescription.text = item.overview.orEmpty()
        heroMeta.text = buildMetaLine(item)

        val hasBackdrop = !item.backdropImageTags.isNullOrEmpty()
        val imageType = if (hasBackdrop) ImageType.BACKDROP else ImageType.PRIMARY
        val imageUrl = repository.buildImageUrl(item.id, imageType = imageType, maxWidth = 1280)
        Glide.with(this).load(imageUrl).into(heroImage)
    }

    private fun buildMetaLine(item: BaseItemDto): String {
        val parts = mutableListOf<String>()
        item.genres?.takeIf { it.isNotEmpty() }?.let { parts += it.take(2).joinToString(" / ") }
        item.productionYear?.let { parts += it.toString() }
        formatRuntimeTicks(item.runTimeTicks)?.let { parts += it }
        return parts.joinToString("  •  ")
    }

    private fun dpToPx(dp: Int): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), context.resources.displayMetrics)
}
