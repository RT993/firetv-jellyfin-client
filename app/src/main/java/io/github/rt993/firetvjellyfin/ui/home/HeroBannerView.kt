package io.github.rt993.firetvjellyfin.ui.home

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import io.github.rt993.firetvjellyfin.R
import io.github.rt993.firetvjellyfin.data.JellyfinRepository
import io.github.rt993.firetvjellyfin.util.formatEndsAt
import io.github.rt993.firetvjellyfin.util.formatRuntimeTicks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType

private const val BACKDROP_CROSSFADE_MS = 350

/**
 * One big item at a time (title/description/backdrop/actions), paged with D-pad left/right - not
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
    private val btnInfo: ImageButton
    private val btnFavorite: ImageButton
    private val dotsContainer: LinearLayout

    private var items: List<BaseItemDto> = emptyList()
    private var currentIndex = 0
    private var repository: JellyfinRepository? = null
    private var userId: UUID? = null
    private var onPlayClicked: ((BaseItemDto) -> Unit)? = null
    private var onInfoClicked: ((BaseItemDto) -> Unit)? = null
    private var scope: CoroutineScope? = null
    private var favoriteJob: Job? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.view_hero_banner, this, true)
        backdrop = findViewById(R.id.hero_backdrop)
        logo = findViewById(R.id.hero_logo)
        title = findViewById(R.id.hero_title)
        meta = findViewById(R.id.hero_meta)
        genres = findViewById(R.id.hero_genres)
        description = findViewById(R.id.hero_description)
        btnPlay = findViewById(R.id.hero_btn_play)
        btnInfo = findViewById(R.id.hero_btn_info)
        btnFavorite = findViewById(R.id.hero_btn_favorite)
        dotsContainer = findViewById(R.id.hero_dots)

        btnPlay.setOnClickListener { withCurrentItem { item -> routeToPlayOrInfo(item) } }
        btnInfo.setOnClickListener { withCurrentItem { item -> onInfoClicked?.invoke(item) } }
        btnFavorite.setOnClickListener { toggleFavorite() }

        // Play is the leftmost focusable control and Favorite the rightmost - past either edge
        // there's nothing else to focus within the banner, so treat that as "page" instead.
        btnPlay.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN) {
                page(-1)
                true
            } else {
                false
            }
        }
        btnFavorite.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN) {
                page(1)
                true
            } else {
                false
            }
        }
    }

    fun bind(
        items: List<BaseItemDto>,
        repository: JellyfinRepository,
        userId: UUID,
        onPlayClicked: (BaseItemDto) -> Unit,
        onInfoClicked: (BaseItemDto) -> Unit,
    ) {
        this.items = items
        this.repository = repository
        this.userId = userId
        this.onPlayClicked = onPlayClicked
        this.onInfoClicked = onInfoClicked
        this.scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        currentIndex = 0
        rebuildDots()
        showCurrent(crossfade = false)
    }

    fun unbind() {
        favoriteJob?.cancel()
        favoriteJob = null
        scope?.cancel()
        scope = null
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
        // picker) the same as the Info button, same as everywhere else this app handles series.
        if (item.type == BaseItemKind.SERIES) onInfoClicked?.invoke(item) else onPlayClicked?.invoke(item)
    }

    private fun toggleFavorite() {
        val repo = repository ?: return
        val uid = userId ?: return
        val item = items.getOrNull(currentIndex) ?: return
        val target = item.userData?.isFavorite != true

        btnFavorite.isEnabled = false
        favoriteJob = scope?.launch {
            val result = runCatching { repo.setFavorite(uid, item.id, target) }
            btnFavorite.isEnabled = true
            val newFavorite = result.getOrNull() ?: return@launch
            val updated = item.copy(userData = item.userData?.copy(isFavorite = newFavorite))
            items = items.toMutableList().also { it[currentIndex] = updated }
            if (items.getOrNull(currentIndex)?.id == updated.id) applyFavoriteIcon(updated)
        }
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

        applyFavoriteIcon(item)

        val backdropUrl = repo.buildImageUrl(item.id, imageType = ImageType.BACKDROP, maxWidth = 1280)
        val request = Glide.with(this).load(backdropUrl)
        if (crossfade) request.transition(DrawableTransitionOptions.withCrossFade(BACKDROP_CROSSFADE_MS))
        request.into(backdrop)

        for (i in 0 until dotsContainer.childCount) {
            dotsContainer.getChildAt(i).isSelected = i == currentIndex
            dotsContainer.getChildAt(i).setBackgroundResource(
                if (i == currentIndex) R.drawable.hero_dot_active else R.drawable.hero_dot_inactive,
            )
        }
    }

    private fun applyFavoriteIcon(item: BaseItemDto) {
        btnFavorite.setImageResource(
            if (item.userData?.isFavorite == true) R.drawable.ic_hero_heart_filled else R.drawable.ic_hero_heart_outline,
        )
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
