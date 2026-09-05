package io.github.rt993.firetvjellyfin.ui.library

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.FocusHighlight
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.leanback.widget.VerticalGridPresenter
import androidx.lifecycle.lifecycleScope
import io.github.rt993.firetvjellyfin.data.JellyfinClientHolder
import io.github.rt993.firetvjellyfin.data.JellyfinRepository
import io.github.rt993.firetvjellyfin.ui.details.ItemDetailsActivity
import io.github.rt993.firetvjellyfin.ui.home.CardPresenter
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import java.util.UUID

/**
 * A full poster grid for one library - the Apple-TV-style "Movies"/"TV Shows" tab destination,
 * replacing the single horizontal shelf that nav tap used to just scroll to on the Home screen.
 * Reuses [CardPresenter] as-is: its own focus scale/elevation animation is the only per-item
 * highlight here, so the grid's own zoom is explicitly turned off to avoid stacking both.
 */
class LibraryGridFragment : VerticalGridSupportFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val gridPresenter = VerticalGridPresenter(FocusHighlight.ZOOM_FACTOR_NONE, false)
        gridPresenter.numberOfColumns = GRID_COLUMNS
        setGridPresenter(gridPresenter)
        onItemViewClickedListener = ItemClickedListener()

        title = requireActivity().intent.getStringExtra(LibraryGridActivity.EXTRA_TITLE).orEmpty()

        val api = JellyfinClientHolder.api
        val libraryIdString = requireActivity().intent.getStringExtra(LibraryGridActivity.EXTRA_LIBRARY_ID)
        val userIdString = JellyfinClientHolder.currentUserId()
        val libraryId = libraryIdString?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val userId = userIdString?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        if (api == null || libraryId == null || userId == null) {
            Log.e(TAG, "onCreate: missing session/library (api=$api, libraryId=$libraryIdString, userId=$userIdString)")
            return
        }

        val repository = JellyfinRepository(api)
        lifecycleScope.launch {
            val items = runCatching { repository.getItems(userId, libraryId, limit = GRID_ITEM_LIMIT) }
                .onFailure { Log.e(TAG, "getItems failed for library $libraryId", it) }
                .getOrDefault(emptyList())
            Log.i(TAG, "library $libraryId: ${items.size} item(s)")
            if (!isAdded) return@launch
            adapter = ArrayObjectAdapter(CardPresenter(repository)).apply { addAll(0, items) }
        }
    }

    private fun openDetails(item: BaseItemDto) {
        startActivity(
            Intent(requireContext(), ItemDetailsActivity::class.java)
                .putExtra(ItemDetailsActivity.EXTRA_ITEM_ID, item.id.toString()),
        )
    }

    private inner class ItemClickedListener : OnItemViewClickedListener {
        override fun onItemClicked(
            itemViewHolder: Presenter.ViewHolder,
            item: Any,
            rowViewHolder: RowPresenter.ViewHolder,
            row: Row,
        ) {
            (item as? BaseItemDto)?.let(::openDetails)
        }
    }

    private companion object {
        const val TAG = "LibraryGridFragment"
        const val GRID_COLUMNS = 6
        // No pagination yet - fine for a personal library, but a library past this size will be
        // truncated. Worth revisiting with real paging if that turns out to matter.
        const val GRID_ITEM_LIMIT = 500
    }
}
