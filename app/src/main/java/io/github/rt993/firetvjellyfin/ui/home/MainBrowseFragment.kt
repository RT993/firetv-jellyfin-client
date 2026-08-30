package io.github.rt993.firetvjellyfin.ui.home

import android.content.Intent
import android.os.Bundle
import android.util.Log
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // BrowseSupportFragment decides whether to create its internal row-content fragment
        // exactly once, synchronously inside its own onCreateView() - which runs before our
        // onViewCreated() - and only if `adapter` is already a non-empty ObjectAdapter at that
        // exact moment. Calling setAdapter() later, even with real data, does not retroactively
        // trigger that creation - it only updates data on a fragment that was never built. Seed a
        // placeholder row here, before the view is created, so that creation succeeds; it's
        // replaced with real rows once the server responds.
        rowsAdapter.add(ListRow(HeaderItem(LOADING_ROW_ID, ""), ArrayObjectAdapter(ListRowPresenter())))
        adapter = rowsAdapter
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        title = getString(R.string.home_title)
        setHeadersState(HEADERS_DISABLED)
        setHeadersTransitionOnBackEnabled(false)
        onItemViewClickedListener = ItemClickedListener()

        loadLibraries()
    }

    private fun loadLibraries() {
        val api = JellyfinClientHolder.api
        val userIdString = JellyfinClientHolder.currentUserId()
        if (api == null || userIdString == null) {
            Log.e(TAG, "loadLibraries: missing session (api=$api, userId=$userIdString)")
            Toast.makeText(requireContext(), "Not signed in (api or userId missing)", Toast.LENGTH_LONG).show()
            return
        }
        val userId = runCatching { UUID.fromString(userIdString) }.getOrNull()
        if (userId == null) {
            Log.e(TAG, "loadLibraries: bad userId string \"$userIdString\"")
            Toast.makeText(requireContext(), "Bad userId: $userIdString", Toast.LENGTH_LONG).show()
            return
        }
        val repository = JellyfinRepository(api)
        val cardPresenter = CardPresenter(repository)

        lifecycleScope.launch {
            runCatching { repository.getUserViews(userId) }
                .onSuccess { views ->
                    Log.i(TAG, "getUserViews returned ${views.size} view(s): ${views.map { it.name to it.collectionType }}")
                    if (views.isEmpty()) {
                        Toast.makeText(requireContext(), "Server returned 0 libraries for this user", Toast.LENGTH_LONG).show()
                    }
                    views.forEach { view -> loadRow(repository, cardPresenter, userId, view) }
                    rowsAdapter.removeItems(0, 1) // drop the placeholder row seeded in onCreate()
                }
                .onFailure {
                    Log.e(TAG, "getUserViews failed", it)
                    Toast.makeText(
                        requireContext(),
                        "${getString(R.string.home_error)}\n${it.javaClass.simpleName}: ${it.message}",
                        Toast.LENGTH_LONG,
                    ).show()
                }
        }
    }

    private suspend fun loadRow(
        repository: JellyfinRepository,
        cardPresenter: CardPresenter,
        userId: UUID,
        view: BaseItemDto,
    ) {
        val items = runCatching { repository.getItems(userId, view.id) }
            .onFailure { Log.e(TAG, "getItems failed for view ${view.name}", it) }
            .getOrDefault(emptyList())
        Log.i(TAG, "view \"${view.name}\" (${view.collectionType}): ${items.size} item(s)")
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

    private companion object {
        const val TAG = "MainBrowseFragment"
        const val LOADING_ROW_ID = -1L
    }
}
