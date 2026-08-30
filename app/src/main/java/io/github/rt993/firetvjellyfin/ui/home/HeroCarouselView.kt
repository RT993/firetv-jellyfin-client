package io.github.rt993.firetvjellyfin.ui.home

import android.content.Context
import android.content.Intent
import android.graphics.Outline
import android.util.AttributeSet
import android.util.TypedValue
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.bumptech.glide.Glide
import io.github.rt993.firetvjellyfin.R
import io.github.rt993.firetvjellyfin.data.JellyfinRepository
import io.github.rt993.firetvjellyfin.ui.details.ItemDetailsActivity
import io.github.rt993.firetvjellyfin.util.formatRuntimeTicks
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType

/**
 * Single large paginated hero banner (title/description/backdrop/dots), cycled with D-pad
 * left/right - not a Leanback row, which only supports a horizontally scrolling list of items,
 * not "one big item at a time with page indicators".
 */
class HeroCarouselView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val heroImage: ImageView
    private val heroMeta: TextView
    private val heroTitle: TextView
    private val heroDescription: TextView
    private val dotsContainer: LinearLayout

    private var items: List<BaseItemDto> = emptyList()
    private var repository: JellyfinRepository? = null
    private var currentIndex = 0

    init {
        LayoutInflater.from(context).inflate(R.layout.view_hero_carousel, this, true)
        heroImage = findViewById(R.id.hero_image)
        heroMeta = findViewById(R.id.hero_meta)
        heroTitle = findViewById(R.id.hero_title)
        heroDescription = findViewById(R.id.hero_description)
        dotsContainer = findViewById(R.id.hero_dots)

        isFocusable = true
        isFocusableInTouchMode = true
        foreground = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.hero_card_border)
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, dpToPx(24))
            }
        }
        clipToOutline = true

        setOnClickListener { openCurrentItemDetails() }
    }

    fun setItems(items: List<BaseItemDto>, repository: JellyfinRepository) {
        this.items = items
        this.repository = repository
        currentIndex = 0
        rebuildDots()
        showCurrent()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (items.size > 1 && isFocused) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    currentIndex = (currentIndex - 1 + items.size) % items.size
                    showCurrent()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    currentIndex = (currentIndex + 1) % items.size
                    showCurrent()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun openCurrentItemDetails() {
        val item = items.getOrNull(currentIndex) ?: return
        context.startActivity(
            Intent(context, ItemDetailsActivity::class.java)
                .putExtra(ItemDetailsActivity.EXTRA_ITEM_ID, item.id.toString()),
        )
    }

    private fun showCurrent() {
        val item = items.getOrNull(currentIndex) ?: return
        val repo = repository ?: return

        heroTitle.text = item.name
        heroDescription.text = item.overview.orEmpty()
        heroMeta.text = buildMetaLine(item)

        for (i in 0 until dotsContainer.childCount) {
            val dot = dotsContainer.getChildAt(i)
            val isActive = i == currentIndex
            dot.setBackgroundResource(if (isActive) R.drawable.hero_dot_active else R.drawable.hero_dot_inactive)
            val size = dpToPx(if (isActive) 8 else 6).toInt()
            val params = dot.layoutParams as LinearLayout.LayoutParams
            if (params.width != size || params.height != size) {
                params.width = size
                params.height = size
                dot.layoutParams = params
            }
        }

        val hasBackdrop = !item.backdropImageTags.isNullOrEmpty()
        val imageType = if (hasBackdrop) ImageType.BACKDROP else ImageType.PRIMARY
        val imageUrl = repo.buildImageUrl(item.id, imageType = imageType, maxWidth = 1280)
        Glide.with(this).load(imageUrl).into(heroImage)
    }

    private fun buildMetaLine(item: BaseItemDto): String {
        val parts = mutableListOf<String>()
        item.genres?.takeIf { it.isNotEmpty() }?.let { parts += it.take(2).joinToString(" / ") }
        item.productionYear?.let { parts += it.toString() }
        formatRuntimeTicks(item.runTimeTicks)?.let { parts += it }
        return parts.joinToString("  •  ")
    }

    private fun rebuildDots() {
        dotsContainer.removeAllViews()
        if (items.size <= 1) return
        val dotSpacing = dpToPx(6).toInt()
        items.indices.forEach { i ->
            val dot = View(context)
            val size = dpToPx(if (i == 0) 8 else 6).toInt()
            val params = LinearLayout.LayoutParams(size, size)
            if (i > 0) params.marginStart = dotSpacing
            dot.layoutParams = params
            dot.setBackgroundResource(if (i == 0) R.drawable.hero_dot_active else R.drawable.hero_dot_inactive)
            dotsContainer.addView(dot)
        }
    }

    private fun dpToPx(dp: Int): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), context.resources.displayMetrics)
}
