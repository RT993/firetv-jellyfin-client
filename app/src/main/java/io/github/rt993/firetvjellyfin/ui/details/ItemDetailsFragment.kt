package io.github.rt993.firetvjellyfin.ui.details

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
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
 * Shows metadata for one item. Movies get a Play action that hands off to [PlaybackActivity]
 * directly; series have no single video to play, so instead show one row per season, each row
 * full of episode cards - picking an episode is what starts playback.
 */
class ItemDetailsFragment : DetailsSupportFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onItemViewClickedListener = EpisodeClickedListener()

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
            val rowsAdapter = setupRows(repository, item)
            loadBackdrop(repository, item)
            if (item.type == BaseItemKind.SERIES) {
                loadSeasonRows(repository, userId, item, rowsAdapter)
            }
        }
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
        val backdropUrl = repository.buildImageUrl(item.id, imageType = ImageType.BACKDROP, maxWidth = 1280)
        Glide.with(this).load(backdropUrl).centerCrop().into(backdropImage)
    }

    /** Builds the overview row (and its Play action, for movies) and returns the adapter it was added to. */
    private fun setupRows(repository: JellyfinRepository, item: BaseItemDto): ArrayObjectAdapter {
        val isSeries = item.type == BaseItemKind.SERIES
        val detailsPresenter = object : FullWidthDetailsOverviewRowPresenter(DescriptionPresenter()) {
            override fun onBindRowViewHolder(vh: RowPresenter.ViewHolder, item: Any) {
                super.onBindRowViewHolder(vh, item)
                // The overview frame carries a fixed elevation for its own drop shadow, which
                // renders as a visible dark band right at the panel's top edge no matter how
                // transparent its background color is. A flat glass panel doesn't need a shadow.
                vh.view.findViewById<View>(androidx.leanback.R.id.details_frame)?.elevation = 0f
            }
        }
        // Leanback's default description/actions panel is fully opaque, which reads as a hard
        // slab against the backdrop behind it. Make it translucent (glass-like) instead so the
        // backdrop stays visible through it. The actions background sits nested inside the frame,
        // so giving it the same translucent color as the frame would stack two translucent layers
        // on top of each other there - visibly darker than the rest of the panel - leave it fully
        // transparent so only the frame's single glass tint shows through everywhere.
        val panelColor = ContextCompat.getColor(requireContext(), R.color.details_panel_scrim)
        detailsPresenter.backgroundColor = panelColor
        detailsPresenter.actionsBackgroundColor = Color.TRANSPARENT
        // FullWidthDetailsOverviewRowPresenter also layers its own dim overlay - a foreground
        // Drawable covering the whole panel, independent of the colors above - that goes to 60%
        // opaque black whenever the row is considered "unselected" (its default state until the
        // Rows fragment explicitly marks it selected). That dimming serves no purpose here (we
        // don't want anything about this screen going darker as you navigate), so turn it off.
        detailsPresenter.setSelectEffectEnabled(false)
        if (!isSeries) {
            detailsPresenter.onActionClickedListener = OnActionClickedListener { action ->
                if (action.id == ACTION_PLAY) {
                    startActivity(
                        Intent(requireContext(), PlaybackActivity::class.java)
                            .putExtra(PlaybackActivity.EXTRA_ITEM_ID, item.id.toString())
                            .putExtra(PlaybackActivity.EXTRA_ITEM_NAME, item.name)
                            .putExtra(
                                PlaybackActivity.EXTRA_START_POSITION_TICKS,
                                item.userData?.playbackPositionTicks ?: 0L,
                            ),
                    )
                }
            }
        }

        val row = DetailsOverviewRow(item)
        if (!isSeries) {
            // A series has no single video to play - picking an episode from the season rows
            // below is what starts playback instead, so it gets no Play action here.
            row.actionsAdapter = ArrayObjectAdapter().apply {
                add(Action(ACTION_PLAY, getString(R.string.details_play)))
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
        val seasons = runCatching { repository.getSeasons(userId, series.id) }
            .onFailure { Log.e(TAG, "getSeasons failed", it) }
            .getOrDefault(emptyList())
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
    }
}
