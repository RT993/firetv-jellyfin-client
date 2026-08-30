package io.github.rt993.firetvjellyfin.ui.details

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.leanback.app.DetailsSupportFragment
import androidx.leanback.app.DetailsSupportFragmentBackgroundController
import androidx.leanback.widget.AbstractDetailsDescriptionPresenter
import androidx.leanback.widget.Action
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ClassPresenterSelector
import androidx.leanback.widget.DetailsOverviewRow
import androidx.leanback.widget.FullWidthDetailsOverviewRowPresenter
import androidx.leanback.widget.OnActionClickedListener
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
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.PersonKind
import java.util.UUID

/** Shows metadata for one item plus a Play action that hands off to [PlaybackActivity]. */
class ItemDetailsFragment : DetailsSupportFragment() {

    private val backgroundController = DetailsSupportFragmentBackgroundController(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        backgroundController.enableParallax()
        backgroundController.setSolidColor(ContextCompat.getColor(requireContext(), R.color.background_dark))

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
            setupRow(repository, item)
            loadBackdrop(repository, item)
        }
    }

    private fun loadBackdrop(repository: JellyfinRepository, item: BaseItemDto) {
        if (!isAdded || item.backdropImageTags.isNullOrEmpty()) return
        val backdropUrl = repository.buildImageUrl(item.id, imageType = ImageType.BACKDROP, maxWidth = 1280)
        Glide.with(this)
            .asBitmap()
            .load(backdropUrl)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    if (!isAdded) return
                    backgroundController.coverBitmap = cropToScreenAspectRatio(resource)
                }

                override fun onLoadCleared(placeholder: Drawable?) = Unit
            })
    }

    /**
     * DetailsSupportFragmentBackgroundController draws the cover bitmap via
     * FitWidthBitmapDrawable, which scales it to fill the screen's width and derives its height
     * from the bitmap's own aspect ratio - it never crops to fill. A backdrop proportionally
     * wider than the screen then falls short of the screen's height, leaving the plain solid
     * background color exposed below it: a hard, high-contrast seam right above the details
     * panel. Center-crop the bitmap to the screen's aspect ratio first so it always fills the
     * full height with no gap.
     */
    private fun cropToScreenAspectRatio(bitmap: Bitmap): Bitmap {
        val metrics = resources.displayMetrics
        val screenRatio = metrics.widthPixels.toFloat() / metrics.heightPixels
        val bitmapRatio = bitmap.width.toFloat() / bitmap.height
        if (bitmapRatio <= screenRatio) return bitmap // already tall enough relative to its width

        val targetWidth = (bitmap.height * screenRatio).toInt().coerceAtMost(bitmap.width)
        val xOffset = (bitmap.width - targetWidth) / 2
        return Bitmap.createBitmap(bitmap, xOffset, 0, targetWidth, bitmap.height)
    }

    private fun setupRow(repository: JellyfinRepository, item: BaseItemDto) {
        val detailsPresenter = FullWidthDetailsOverviewRowPresenter(DescriptionPresenter())
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
        // Rows fragment explicitly marks it selected). With only one row and no related-content
        // rows below to navigate to, that dimming serves no purpose here and stacks yet another
        // dark layer on top of everything, including the text. Turn it off outright.
        detailsPresenter.setSelectEffectEnabled(false)
        detailsPresenter.onActionClickedListener = OnActionClickedListener { action ->
            if (action.id == ACTION_PLAY) {
                startActivity(
                    Intent(requireContext(), PlaybackActivity::class.java)
                        .putExtra(PlaybackActivity.EXTRA_ITEM_ID, item.id.toString())
                        .putExtra(PlaybackActivity.EXTRA_ITEM_NAME, item.name),
                )
            }
        }

        val row = DetailsOverviewRow(item)
        row.actionsAdapter = ArrayObjectAdapter().apply {
            add(Action(ACTION_PLAY, getString(R.string.details_play)))
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
        adapter = ArrayObjectAdapter(presenterSelector).apply { add(row) }
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
