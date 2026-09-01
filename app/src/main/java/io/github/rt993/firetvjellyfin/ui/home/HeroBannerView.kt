package io.github.rt993.firetvjellyfin.ui.home

import android.content.Context
import android.graphics.Outline
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import io.github.rt993.firetvjellyfin.R
import io.github.rt993.firetvjellyfin.data.JellyfinRepository
import io.github.rt993.firetvjellyfin.util.formatEndsAt
import io.github.rt993.firetvjellyfin.util.formatRuntimeTicks
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType

private const val BACKDROP_CROSSFADE_MS = 350
private const val CARD_CORNER_RADIUS_DP = 24

/**
 * One big item at a time (title/description/backdrop/Play), paged with D-pad left/right - not
 * a Leanback [androidx.leanback.widget.ListRow], which only supports a horizontally scrolling
 * list of same-sized items, not "one big item with page indicators". Used as the content view of
 * a [HeroRow] via [HeroRowPresenter].
 */
class HeroBannerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val backdrop: ImageView
    private val logo: ImageView
    private val title: TextView
    private val meta: TextView
    private val genres: TextView
    private val description: TextView
    private val btnPlay: Button
    private val dotsContainer: LinearLayout

    private var items: List<BaseItemDto> = emptyList()
    private var currentIndex = 0
    private var repository: JellyfinRepository? = null
    private var onPlayClicked: ((BaseItemDto) -> Unit)? = null
    private var onInfoClicked: ((BaseItemDto) -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.view_hero_banner, this, true)
        backdrop = findViewById(R.id.hero_backdrop)
        logo = findViewById(R.id.hero_logo)
        title = findViewById(R.id.hero_title)
        meta = findViewById(R.id.hero_meta)
        genres = findViewById(R.id.hero_genres)
        description = findViewById(R.id.hero_description)
        btnPlay = findViewById(R.id.hero_btn_play)
        dotsContainer = findViewById(R.id.hero_dots)

        val card = findViewById<View>(R.id.hero_card)
        card.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, dpToPx(CARD_CORNER_RADIUS_DP))
            }
        }
        card.clipToOutline = true
        card.foreground = ContextCompat.getDrawable(context, R.drawable.hero_card_border)

        btnPlay.setOnClickListener { withCurrentItem { item -> routeToPlayOrInfo(item) } }

        // Play is the only focusable control left in the banner, so both edges page instead of
        // moving focus anywhere else.
        btnPlay.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) {
                false
            } else when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> { page(-1); true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { page(1); true }
                else -> false
            }
        }
    }

    fun bind(
        items: List<BaseItemDto>,
        repository: JellyfinRepository,
        onPlayClicked: (BaseItemDto) -> Unit,
        onInfoClicked: (BaseItemDto) -> Unit,
    ) {
        this.items = items
        this.repository = repository
        this.onPlayClicked = onPlayClicked
        this.onInfoClicked = onInfoClicked
        currentIndex = 0
        rebuildDots()
        showCurrent(crossfade = false)
    }

    fun unbind() {
        repository = null
        onPlayClicked = null
        onInfoClicked = null
    }

    private fun page(delta: Int) {
        if (items.size <= 1) return
        currentIndex = (currentIndex + delta + items.size) % items.size
        showCurrent(crossfade = true)
    }

    private inline fun withCurrentItem(action: (BaseItemDto) -> Unit) {
        items.getOrNull(currentIndex)?.let(action)
    }

    private fun routeToPlayOrInfo(item: BaseItemDto) {
        // A series has no single video to play - send it to the details screen (season/episode
        // picker) instead, same as everywhere else this app handles series.
        if (item.type == BaseItemKind.SERIES) onInfoClicked?.invoke(item) else onPlayClicked?.invoke(item)
    }

    private fun showCurrent(crossfade: Boolean) {
        val item = items.getOrNull(currentIndex) ?: return
        val repo = repository ?: return

        val hasLogo = item.imageTags?.containsKey(ImageType.LOGO) == true
        logo.visibility = if (hasLogo) View.VISIBLE else View.GONE
        title.visibility = if (hasLogo) View.GONE else View.VISIBLE
        if (hasLogo) {
            Glide.with(this).load(repo.buildImageUrl(item.id, imageType = ImageType.LOGO, maxWidth = 700)).into(logo)
        } else {
            title.text = item.name
        }

        meta.text = buildMetaLine(item)

        val genreLine = item.genres?.takeIf { it.isNotEmpty() }?.joinToString("  •  ")
        genres.text = genreLine.orEmpty()
        genres.visibility = if (genreLine.isNullOrEmpty()) View.GONE else View.VISIBLE

        val overview = item.overview?.takeIf { it.isNotBlank() }
        description.text = overview.orEmpty()
        description.visibility = if (overview == null) View.GONE else View.VISIBLE

        val backdropUrl = repo.buildImageUrl(item.id, imageType = ImageType.BACKDROP, maxWidth = 1280)
        // Without an explicit placeholder, Glide clears the ImageView to blank the instant this
        // load starts (not just once the new image is ready) - on a slow connection that briefly
        // shows the static background behind the row through the now-empty backdrop. Keep
        // whatever's already on screen showing until the new image lands instead.
        var request = Glide.with(this).load(backdropUrl).placeholder(backdrop.drawable)
        if (crossfade) request = request.transition(DrawableTransitionOptions.withCrossFade(BACKDROP_CROSSFADE_MS))
        request.into(backdrop)

        for (i in 0 until dotsContainer.childCount) {
            dotsContainer.getChildAt(i).isSelected = i == currentIndex
            dotsContainer.getChildAt(i).setBackgroundResource(
                if (i == currentIndex) R.drawable.hero_dot_active else R.drawable.hero_dot_inactive,
            )
        }
    }

    private fun buildMetaLine(item: BaseItemDto): String {
        val parts = mutableListOf<String>()
        item.communityRating?.let { parts += "★ %.1f".format(it) }
        item.criticRating?.let { parts += "🍅 ${it.toInt()}%" }
        item.productionYear?.let { parts += it.toString() }
        formatRuntimeTicks(item.runTimeTicks)?.let { parts += it }
        item.officialRating?.let { parts += it }
        val positionTicks = item.userData?.playbackPositionTicks ?: 0L
        val totalTicks = item.runTimeTicks ?: 0L
        if (positionTicks > 0 && totalTicks > positionTicks) {
            formatEndsAt(totalTicks - positionTicks)?.let { parts += resources.getString(R.string.hero_ends_at_format, it) }
        }
        return parts.joinToString("  •  ")
    }

    private fun rebuildDots() {
        dotsContainer.removeAllViews()
        if (items.size <= 1) return
        val size = dpToPx(8).toInt()
        val spacing = dpToPx(6).toInt()
        items.indices.forEach { i ->
            val dot = View(context)
            val params = LinearLayout.LayoutParams(size, size)
            if (i > 0) params.marginStart = spacing
            dot.layoutParams = params
            dot.setBackgroundResource(if (i == 0) R.drawable.hero_dot_active else R.drawable.hero_dot_inactive)
            dotsContainer.addView(dot)
        }
    }

    private fun dpToPx(dp: Int): Float = dp * resources.displayMetrics.density
}
