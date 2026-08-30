package io.github.rt993.firetvjellyfin.ui.details

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.leanback.app.DetailsSupportFragment
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
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import java.util.UUID

/** Shows metadata for one item plus a Play action that hands off to [PlaybackActivity]. */
class ItemDetailsFragment : DetailsSupportFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val api = JellyfinClientHolder.api ?: return
        val itemIdString = requireActivity().intent.getStringExtra(ItemDetailsActivity.EXTRA_ITEM_ID) ?: return
        val userIdString = JellyfinClientHolder.currentUserId() ?: return
        val itemId = runCatching { UUID.fromString(itemIdString) }.getOrNull() ?: return
        val userId = runCatching { UUID.fromString(userIdString) }.getOrNull() ?: return

        val repository = JellyfinRepository(api)
        lifecycleScope.launch {
            val item = runCatching { repository.getItem(userId, itemId) }.getOrNull() ?: return@launch
            setupRow(repository, item)
        }
    }

    private fun setupRow(repository: JellyfinRepository, item: BaseItemDto) {
        val detailsPresenter = FullWidthDetailsOverviewRowPresenter(DescriptionPresenter())
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
            viewHolder.subtitle.text = baseItem.productionYear?.toString()
                ?: getString(R.string.details_unknown_year)
            viewHolder.body.text = baseItem.overview.orEmpty()
        }
    }

    private companion object {
        const val ACTION_PLAY = 1L
    }
}
