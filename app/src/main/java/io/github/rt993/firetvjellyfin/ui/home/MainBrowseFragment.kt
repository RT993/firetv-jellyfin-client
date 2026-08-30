package io.github.rt993.firetvjellyfin.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.lifecycle.lifecycleScope
import io.github.rt993.firetvjellyfin.R
import io.github.rt993.firetvjellyfin.data.JellyfinClientHolder
import io.github.rt993.firetvjellyfin.data.JellyfinRepository
import io.github.rt993.firetvjellyfin.ui.details.ItemDetailsActivity
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import java.util.UUID

/** Home screen: one row per Jellyfin library, each row filled with that library's items. */
class MainBrowseFragment : BrowseSupportFragment() {

    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        title = getString(R.string.home_title)
        setHeadersState(HEADERS_DISABLED)
        setHeadersTransitionOnBackEnabled(false)
        adapter = rowsAdapter
        onItemViewClickedListener = ItemClickedListener()

        loadLibraries()
    }

    private fun loadLibraries() {
        val api = JellyfinClientHolder.api ?: return
        val userIdString = JellyfinClientHolder.currentUserId() ?: return
        val userId = runCatching { UUID.fromString(userIdString) }.getOrNull() ?: return
        val repository = JellyfinRepository(api)
        val cardPresenter = CardPresenter(repository)

        lifecycleScope.launch {
            runCatching { repository.getUserViews(userId) }
                .onSuccess { views -> views.forEach { view -> loadRow(repository, cardPresenter, userId, view) } }
                .onFailure {
                    Toast.makeText(requireContext(), R.string.home_error, Toast.LENGTH_LONG).show()
                }
        }
    }

    private suspend fun loadRow(
        repository: JellyfinRepository,
        cardPresenter: CardPresenter,
        userId: UUID,
        view: BaseItemDto,
    ) {
        val items = runCatching { repository.getItems(userId, view.id) }.getOrDefault(emptyList())
        if (items.isEmpty()) return

        val rowAdapter = ArrayObjectAdapter(cardPresenter).apply { addAll(0, items) }
        val header = HeaderItem(rowsAdapter.size().toLong(), view.name.orEmpty())
        rowsAdapter.add(ListRow(header, rowAdapter))
    }

    private inner class ItemClickedListener : OnItemViewClickedListener {
        override fun onItemClicked(
            itemViewHolder: Presenter.ViewHolder,
            item: Any,
            rowViewHolder: RowPresenter.ViewHolder,
            row: Row,
        ) {
            val baseItem = item as? BaseItemDto ?: return
            val intent = Intent(requireContext(), ItemDetailsActivity::class.java)
                .putExtra(ItemDetailsActivity.EXTRA_ITEM_ID, baseItem.id.toString())
            startActivity(intent)
        }
    }
}
