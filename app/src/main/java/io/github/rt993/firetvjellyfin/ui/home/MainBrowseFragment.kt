package io.github.rt993.firetvjellyfin.ui.home

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.leanback.app.BackgroundManager
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ClassPresenterSelector
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
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
import io.github.rt993.firetvjellyfin.ui.details.ItemDetailsActivity
import io.github.rt993.firetvjellyfin.ui.playback.PlaybackActivity
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ImageType
import java.util.UUID

/** Home screen: one row per Jellyfin library, each row filled with that library's items. */
class MainBrowseFragment : BrowseSupportFragment() {

    private val presenterSelector = ClassPresenterSelector().apply {
        addClassPresenter(ListRow::class.java, ListRowPresenter())
    }
    private val rowsAdapter = ArrayObjectAdapter(presenterSelector)
    private lateinit var backgroundManager: BackgroundManager
    private var repository: JellyfinRepository? = null
    private val loadedRows = mutableListOf<LoadedRow>()
    private var recentlyAddedRow: HeroRow? = null

    private data class LoadedRow(val collectionType: CollectionType?, val row: ListRow)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        backgroundManager = BackgroundManager.getInstance(requireActivity()).apply {
            attach(requireActivity().window)
        }

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

        // No Leanback title/breadcrumb - HomeActivity's own top bar (logo, nav, username) replaces it.
        setHeadersState(HEADERS_DISABLED)
        setHeadersTransitionOnBackEnabled(false)
        onItemViewClickedListener = ItemClickedListener()
        onItemViewSelectedListener = ItemSelectedListener()

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
        val repo = JellyfinRepository(api)
        repository = repo
        val cardPresenter = CardPresenter(repo)
        presenterSelector.addClassPresenter(
            HeroRow::class.java,
            HeroRowPresenter(
                repository = repo,
                userId = userId,
                onPlayClicked = ::openPlayback,
                onInfoClicked = ::openDetails,
            ),
        )

        lifecycleScope.launch {
            runCatching { repo.getUserViews(userId) }
                .onSuccess { views ->
                    Log.i(TAG, "getUserViews returned ${views.size} view(s): ${views.map { it.name to it.collectionType }}")
                    if (views.isEmpty()) {
                        Toast.makeText(requireContext(), "Server returned 0 libraries for this user", Toast.LENGTH_LONG).show()
                    }
                    views.forEach { view -> loadRow(repo, cardPresenter, userId, view) }
                    loadRecentlyAdded(repo, userId)
                    showLibrary(null) // all rows - also clears the placeholder row seeded in onCreate()
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
        repo: JellyfinRepository,
        cardPresenter: CardPresenter,
        userId: UUID,
        view: BaseItemDto,
    ) {
        val items = runCatching { repo.getItems(userId, view.id) }
            .onFailure { Log.e(TAG, "getItems failed for view ${view.name}", it) }
            .getOrDefault(emptyList())
        Log.i(TAG, "view \"${view.name}\" (${view.collectionType}): ${items.size} item(s)")
        if (items.isEmpty()) return

        val rowAdapter = ArrayObjectAdapter(cardPresenter).apply { addAll(0, items) }
        val header = HeaderItem(loadedRows.size.toLong(), view.name.orEmpty())
        Log.i(TAG, "adding row for \"${view.name}\" with collectionType=${view.collectionType}")
        loadedRows += LoadedRow(view.collectionType, ListRow(header, rowAdapter))
    }

    /** Home-only: first row shown, a big paginated banner of newly added movies and series. */
    private suspend fun loadRecentlyAdded(repo: JellyfinRepository, userId: UUID) {
        val items = runCatching { repo.getRecentlyAdded(userId) }
            .onFailure { Log.e(TAG, "getRecentlyAdded failed", it) }
            .getOrDefault(emptyList())
        Log.i(TAG, "recently added: ${items.size} item(s)")
        if (items.isEmpty()) return

        val header = HeaderItem(RECENTLY_ADDED_ROW_ID, getString(R.string.home_recently_added))
        recentlyAddedRow = HeroRow(header, items)
    }

    /**
     * Shows only the row for [type] (a Movies/TV Shows nav filter), or every row - the Recently
     * Added hero first, then one per library - if null. Used by the top nav bar.
     */
    fun showLibrary(type: CollectionType?) {
        Log.i(TAG, "showLibrary(type=$type), loadedRows=${loadedRows.map { it.collectionType }}")
        rowsAdapter.clear()
        if (type == null) {
            recentlyAddedRow?.let { rowsAdapter.add(it) }
            loadedRows.forEach { rowsAdapter.add(it.row) }
        } else {
            loadedRows.filter { it.collectionType == type }.forEach { rowsAdapter.add(it.row) }
        }
    }

    /** True when the topmost row is selected, i.e. pressing up has nowhere left to go. */
    fun isAtTopRow(): Boolean = selectedPosition <= 0

    private fun openDetails(item: BaseItemDto) {
        startActivity(
            Intent(requireContext(), ItemDetailsActivity::class.java)
                .putExtra(ItemDetailsActivity.EXTRA_ITEM_ID, item.id.toString()),
        )
    }

    private fun openPlayback(item: BaseItemDto) {
        startActivity(
            Intent(requireContext(), PlaybackActivity::class.java)
                .putExtra(PlaybackActivity.EXTRA_ITEM_ID, item.id.toString())
                .putExtra(PlaybackActivity.EXTRA_ITEM_NAME, item.name),
        )
    }

    private inner class ItemClickedListener : OnItemViewClickedListener {
        override fun onItemClicked(
            itemViewHolder: Presenter.ViewHolder,
            item: Any,
            rowViewHolder: RowPresenter.ViewHolder,
            row: Row,
        ) {
            val baseItem = item as? BaseItemDto ?: return
            openDetails(baseItem)
        }
    }

    private inner class ItemSelectedListener : OnItemViewSelectedListener {
        override fun onItemSelected(
            itemViewHolder: Presenter.ViewHolder?,
            item: Any?,
            rowViewHolder: RowPresenter.ViewHolder?,
            row: Row?,
        ) {
            val baseItem = item as? BaseItemDto ?: return
            updateBackground(baseItem)
        }
    }

    /** Sets the blurred/dimmed backdrop behind the rows to the currently focused item's art. */
    private fun updateBackground(item: BaseItemDto) {
        if (!isAdded) return
        val repo = repository ?: return
        if (item.backdropImageTags.isNullOrEmpty()) return

        val url = repo.buildImageUrl(item.id, imageType = ImageType.BACKDROP, maxWidth = 1280)
        Glide.with(this)
            .asBitmap()
            .load(url)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    if (!isAdded) return
                    val scrim = ColorDrawable(ContextCompat.getColor(requireContext(), R.color.backdrop_scrim))
                    backgroundManager.drawable = LayerDrawable(arrayOf(BitmapDrawable(resources, resource), scrim))
                }

                override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) = Unit
            })
    }

    private companion object {
        const val TAG = "MainBrowseFragment"
        const val LOADING_ROW_ID = -1L
        const val RECENTLY_ADDED_ROW_ID = -2L
    }
}
