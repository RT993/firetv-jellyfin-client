package io.github.rt993.firetvjellyfin.ui.details

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.leanback.app.DetailsSupportFragment
import androidx.leanback.widget.AbstractDetailsDescriptionPresenter
import androidx.leanback.widget.Action
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ClassPresenterSelector
import androidx.leanback.widget.DetailsOverviewRow
import androidx.leanback.widget.FullWidthDetailsOverviewRowPresenter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnActionClickedListener
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import io.github.rt993.firetvjellyfin.R
import io.github.rt993.firetvjellyfin.data.JellyfinClientHolder
import io.github.rt993.firetvjellyfin.data.JellyfinRepository
import io.github.rt993.firetvjellyfin.ui.playback.PlaybackActivity
import io.github.rt993.firetvjellyfin.util.formatRuntimeTicks
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.PersonKind
import java.util.UUID

/**
 * Shows metadata for one item, with a Play/Resume action that hands off to [PlaybackActivity] -
 * for a movie, itself; for a series (which has no single video of its own), whichever episode
 * Jellyfin considers "up next" for this user (see [resolveSeriesPlayTarget]). A series also gets
 * one row per season below the overview, each full of episode cards, for picking a specific
 * episode instead.
 */
class ItemDetailsFragment : DetailsSupportFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onItemViewClickedListener = EpisodeClickedListener()

        // DetailsSupportFragment's internal RowsSupportFragment, like BrowseSupportFragment's
        // (see MainBrowseFragment.onCreate), is only created if `adapter` is already a non-empty
        // ObjectAdapter at onCreateView() time - setting it later, once the network calls below
        // resolve, doesn't retroactively fix that. Worth keeping regardless, but this alone turned
        // out NOT to be the cause of the double-press-to-reach-episodes bug - see
        // consumeDownFromOverviewRow() below for the actual fix.
        adapter = ArrayObjectAdapter(ListRowPresenter()).apply {
            add(ListRow(HeaderItem(LOADING_ROW_ID, ""), ArrayObjectAdapter(ListRowPresenter())))
        }

        val api = JellyfinClientHolder.api
        val itemIdString = requireActivity().intent.getStringExtra(ItemDetailsActivity.EXTRA_ITEM_ID)
        val userIdString = JellyfinClientHolder.currentUserId()
        if (api == null || itemIdString == null || userIdString == null) {
            Log.e(TAG, "onCreate: missing session (api=$api, itemId=$itemIdString, userId=$userIdString)")
            Toast.makeText(requireContext(), "Missing session or item id", Toast.LENGTH_LONG).show()
            return
        }
        val itemId = runCatching { UUID.fromString(itemIdString) }.getOrNull()
        val userId = runCatching { UUID.fromString(userIdString) }.getOrNull()
        if (itemId == null || userId == null) {
            Log.e(TAG, "onCreate: bad UUID (itemId=$itemIdString, userId=$userIdString)")
            return
        }

        val repository = JellyfinRepository(api)
        lifecycleScope.launch {
            val item = runCatching { repository.getItem(userId, itemId) }
                .onFailure { Log.e(TAG, "getItem failed", it) }
                .getOrNull()
            if (item == null) {
                Toast.makeText(requireContext(), "Could not load this item's details", Toast.LENGTH_LONG).show()
                return@launch
            }
            Log.i(TAG, "loaded \"${item.name}\": overview=${item.overview?.take(20)}, genres=${item.genres}, cast=${item.people?.size}")
            // getItem() suspends on a network call - the fragment may already be gone (user
            // pressed back before it resolved) by the time this resumes, and Glide.with() throws
            // if a load starts against an already-destroyed fragment/activity.
            if (!isAdded) return@launch

            val playTarget = if (item.type == BaseItemKind.SERIES) {
                resolveSeriesPlayTarget(repository, userId, item)
            } else {
                item
            }
            if (!isAdded) return@launch

            val rowsAdapter = setupRows(repository, item, playTarget)
            loadBackdrop(repository, item)
            if (item.type == BaseItemKind.SERIES) {
                loadSeasonRows(repository, userId, item, rowsAdapter)
            }
        }
    }

    /**
     * Leanback's own row-to-row DOWN handling needs one press to internally settle the
     * transition out of the overview row, and only a second press actually moves selection past
     * it - visually that reads as the first press doing nothing. [ItemDetailsActivity] calls this
     * on every DOWN press before Leanback sees it; driving the row selection here directly, one
     * press early, skips that wasted first press instead of waiting on the internal transition
     * (which three earlier attempts at fixing from that end never managed to avoid).
     */
    fun consumeDownFromOverviewRow(): Boolean {
        val rows = rowsSupportFragment ?: return false
        val rowCount = adapter?.size() ?: 0
        if (rowCount > 1 && rows.selectedPosition == 0) {
            rows.setSelectedPosition(1, true)
            return true
        }
        return false
    }

    /**
     * What a series' own Play/Resume action should start: whatever episode Jellyfin itself
     * considers "up next" for this user (mid-way through it if they have one in progress,
     * otherwise the next unwatched one) - falling back to a direct S1E1 lookup only if that
     * fails, and to nothing at all only for a series with no episodes at all.
     */
    private suspend fun resolveSeriesPlayTarget(repository: JellyfinRepository, userId: UUID, series: BaseItemDto): BaseItemDto? {
        runCatching { repository.getNextUpEpisode(userId, series.id) }.getOrNull()?.let { return it }

        val firstSeason = runCatching { repository.getSeasons(userId, series.id) }
            .getOrDefault(emptyList())
            .minByOrNull { it.indexNumber ?: Int.MAX_VALUE }
            ?: return null
        return runCatching { repository.getEpisodes(userId, series.id, firstSeason.id) }
            .getOrDefault(emptyList())
            .minByOrNull { it.indexNumber ?: Int.MAX_VALUE }
    }

    /**
     * Loaded straight into our own full-screen ImageView (centerCrop) instead of through
     * Leanback's DetailsSupportFragmentBackgroundController - its FitWidthBitmapDrawable only
     * scales to fit the screen's width and derives height from the bitmap's own aspect ratio, so
     * a backdrop proportionally wider than the screen fell short vertically and left a solid-color
     * gap where the details panel's translucency had nothing to reveal. centerCrop guarantees the
     * image always fills the whole screen regardless of its source aspect ratio.
     */
    private fun loadBackdrop(repository: JellyfinRepository, item: BaseItemDto) {
        if (!isAdded || item.backdropImageTags.isNullOrEmpty()) return
        val backdropImage = activity?.findViewById<ImageView>(R.id.details_backdrop) ?: return
        // This is the one image in the app stretched across the *entire* screen, so any upscale
        // shows as visible banding in smooth, low-detail parts of some backdrops (a wide sky,
        // smoke, gradients) - a fixed cap (1280, then 1920) both turned out to still be narrower
        // than some real TV outputs (4K sticks included). Asking for the actual screen width
        // instead removes the guesswork.
        val screenWidth = resources.displayMetrics.widthPixels
        val backdropUrl = repository.buildImageUrl(item.id, imageType = ImageType.BACKDROP, maxWidth = screenWidth)
        Glide.with(this).load(backdropUrl).centerCrop().into(backdropImage)
    }

    /** Builds the overview row (and its Play/Resume action targeting [playTarget]) and returns the adapter it was added to. */
    private fun setupRows(repository: JellyfinRepository, item: BaseItemDto, playTarget: BaseItemDto?): ArrayObjectAdapter {
        // Leanback's own fullwidth-overview layout (lb_fullwidth_details_overview.xml) reserves a
        // 160dp blank strip above the panel - by design, so the backdrop shows through unfiltered
        // there before the panel begins. A flat panel color then starts abruptly right where that
        // strip ends, which against a backdrop is a hard seam across the full screen width - this
        // is "the line"/"box border": not a compression artifact, an actual color step in the UI,
        // which is why bumping the backdrop's resolution twice never touched it. Fading the panel
        // in with a gradient instead of a flat color removes the step entirely.
        val panelColor = ContextCompat.getColor(requireContext(), R.color.details_panel_scrim)
        val panelGradient = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.TRANSPARENT, panelColor))
        val detailsPresenter = object : FullWidthDetailsOverviewRowPresenter(DescriptionPresenter()) {
            override fun onBindRowViewHolder(vh: RowPresenter.ViewHolder, item: Any) {
                super.onBindRowViewHolder(vh, item)
                vh.view.findViewById<View>(androidx.leanback.R.id.details_frame)?.apply {
                    // The overview frame carries a fixed elevation for its own drop shadow, which
                    // renders as a visible dark band right at the panel's top edge no matter how
                    // transparent its background color is. A flat glass panel doesn't need a shadow.
                    elevation = 0f
                    background = panelGradient
                }
            }
        }
        // Leanback's default description/actions panel is fully opaque, which reads as a hard
        // slab against the backdrop behind it. Make it translucent (glass-like) instead so the
        // backdrop stays visible through it. The actions background sits nested inside the frame,
        // so giving it the same translucent color as the frame would stack two translucent layers
        // on top of each other there - visibly darker than the rest of the panel - leave it fully
        // transparent so only the frame's single glass tint shows through everywhere.
        detailsPresenter.backgroundColor = panelColor
        detailsPresenter.actionsBackgroundColor = Color.TRANSPARENT
        // FullWidthDetailsOverviewRowPresenter also layers its own dim overlay - a foreground
        // Drawable covering the whole panel, independent of the colors above - that goes to 60%
        // opaque black whenever the row is considered "unselected" (its default state until the
        // Rows fragment explicitly marks it selected). That dimming serves no purpose here (we
        // don't want anything about this screen going darker as you navigate), so turn it off.
        detailsPresenter.setSelectEffectEnabled(false)
        detailsPresenter.onActionClickedListener = OnActionClickedListener { action ->
            if (action.id == ACTION_PLAY && playTarget != null) {
                startActivity(
                    Intent(requireContext(), PlaybackActivity::class.java)
                        .putExtra(PlaybackActivity.EXTRA_ITEM_ID, playTarget.id.toString())
                        .putExtra(PlaybackActivity.EXTRA_ITEM_NAME, playTarget.name)
                        .putExtra(
                            PlaybackActivity.EXTRA_START_POSITION_TICKS,
                            playTarget.userData?.playbackPositionTicks ?: 0L,
                        ),
                )
            }
        }

        val row = DetailsOverviewRow(item)
        if (playTarget != null) {
            val isResume = (playTarget.userData?.playbackPositionTicks ?: 0L) > 0L
            row.actionsAdapter = ArrayObjectAdapter().apply {
                add(Action(ACTION_PLAY, getString(if (isResume) R.string.details_resume else R.string.details_play)))
            }
        }

        val imageUrl = repository.buildImageUrl(item.id, maxWidth = 600)
        Glide.with(this)
            .asBitmap()
            .load(imageUrl)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    row.setImageBitmap(requireContext(), resource)
                }

                override fun onLoadCleared(placeholder: Drawable?) = Unit
            })

        val presenterSelector = ClassPresenterSelector()
        presenterSelector.addClassPresenter(DetailsOverviewRow::class.java, detailsPresenter)
        presenterSelector.addClassPresenter(ListRow::class.java, ListRowPresenter())
        val rowsAdapter = ArrayObjectAdapter(presenterSelector).apply { add(row) }
        adapter = rowsAdapter
        return rowsAdapter
    }

    /** One row per season, each row full of that season's episode cards. */
    private suspend fun loadSeasonRows(
        repository: JellyfinRepository,
        userId: UUID,
        series: BaseItemDto,
        rowsAdapter: ArrayObjectAdapter,
    ) {
        // The server doesn't guarantee season order in its response - sort by season number so
        // e.g. Season 3 doesn't end up rendered as the first row just because it came back first.
        val seasons = runCatching { repository.getSeasons(userId, series.id) }
            .onFailure { Log.e(TAG, "getSeasons failed", it) }
            .getOrDefault(emptyList())
            .sortedBy { it.indexNumber ?: Int.MAX_VALUE }
        Log.i(TAG, "\"${series.name}\": ${seasons.size} season(s)")
        val episodePresenter = EpisodeCardPresenter(repository)
        seasons.forEachIndexed { index, season ->
            if (!isAdded) return
            val episodes = runCatching { repository.getEpisodes(userId, series.id, season.id) }
                .onFailure { Log.e(TAG, "getEpisodes failed for season ${season.name}", it) }
                .getOrDefault(emptyList())
            if (episodes.isEmpty()) return@forEachIndexed
            val episodeAdapter = ArrayObjectAdapter(episodePresenter).apply { addAll(0, episodes) }
            val header = HeaderItem(index.toLong(), season.name.orEmpty())
            rowsAdapter.add(ListRow(header, episodeAdapter))
        }
    }

    private inner class EpisodeClickedListener : OnItemViewClickedListener {
        override fun onItemClicked(
            itemViewHolder: Presenter.ViewHolder,
            item: Any,
            rowViewHolder: RowPresenter.ViewHolder,
            row: Row,
        ) {
            val episode = item as? BaseItemDto ?: return
            if (episode.type != BaseItemKind.EPISODE) return
            startActivity(
                Intent(requireContext(), PlaybackActivity::class.java)
                    .putExtra(PlaybackActivity.EXTRA_ITEM_ID, episode.id.toString())
                    .putExtra(PlaybackActivity.EXTRA_ITEM_NAME, episode.name)
                    .putExtra(
                        PlaybackActivity.EXTRA_START_POSITION_TICKS,
                        episode.userData?.playbackPositionTicks ?: 0L,
                    ),
            )
        }
    }

    private inner class DescriptionPresenter : AbstractDetailsDescriptionPresenter() {
        override fun onBindDescription(viewHolder: ViewHolder, item: Any) {
            val baseItem = item as BaseItemDto
            viewHolder.title.text = baseItem.name
            viewHolder.subtitle.text = buildSubtitle(baseItem)
            viewHolder.body.text = buildBody(baseItem)
        }

        private fun buildSubtitle(item: BaseItemDto): String {
            val parts = mutableListOf<String>()
            item.communityRating?.let { parts += "★ %.1f".format(it) }
            parts += item.productionYear?.toString() ?: getString(R.string.details_unknown_year)
            formatRuntimeTicks(item.runTimeTicks)?.let { parts += it }
            item.officialRating?.let { parts += it }
            return parts.joinToString("  ·  ")
        }

        private fun buildBody(item: BaseItemDto): String {
            val sections = mutableListOf<String>()
            item.overview?.takeIf { it.isNotBlank() }?.let { sections += it }
            item.genres?.takeIf { it.isNotEmpty() }?.let { sections += it.joinToString(", ") }
            val cast = item.people
                ?.filter { it.type == PersonKind.ACTOR }
                ?.take(MAX_CAST_SHOWN)
                ?.mapNotNull { it.name }
            if (!cast.isNullOrEmpty()) {
                sections += "Starring: " + cast.joinToString(", ")
            }
            return sections.joinToString("\n\n")
        }
    }

    private companion object {
        const val TAG = "ItemDetailsFragment"
        const val ACTION_PLAY = 1L
        const val MAX_CAST_SHOWN = 6
        const val LOADING_ROW_ID = -1L
    }
}
