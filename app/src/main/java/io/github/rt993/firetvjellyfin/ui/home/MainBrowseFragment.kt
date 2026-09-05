package io.github.rt993.firetvjellyfin.ui.home

import android.content.Intent
import android.graphics.BitmapFactory
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
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.lifecycle.lifecycleScope
import io.github.rt993.firetvjellyfin.R
import io.github.rt993.firetvjellyfin.data.JellyfinClientHolder
import io.github.rt993.firetvjellyfin.data.JellyfinRepository
import io.github.rt993.firetvjellyfin.ui.details.ItemDetailsActivity
import io.github.rt993.firetvjellyfin.ui.playback.PlaybackActivity
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import java.util.UUID

/**
 * Home screen: a Top Shelf hero banner, a Continue Watching row, then one row per library - see
 * [showHome]. [onLibrariesLoaded] lets [HomeActivity] build its sidebar's per-library icons once
 * the server responds.
 */
class MainBrowseFragment : BrowseSupportFragment() {

    private val presenterSelector = ClassPresenterSelector().apply {
        addClassPresenter(ListRow::class.java, ListRowPresenter())
    }
    private val rowsAdapter = ArrayObjectAdapter(presenterSelector)
    private lateinit var backgroundManager: BackgroundManager
    private val loadedRows = mutableListOf<ListRow>()
    private var recentlyAddedRow: HeroRow? = null
    private var continueWatchingRow: ListRow? = null
    var onLibrariesLoaded: ((List<BaseItemDto>) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        backgroundManager = BackgroundManager.getInstance(requireActivity()).apply {
            attach(requireActivity().window)
        }
        setStaticBackground()

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

        // No Leanback title/breadcrumb - HomeActivity's sidebar replaces the nav chrome entirely.
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
        val repo = JellyfinRepository(api)
        // The bigger, more cinematic card style - these rows render directly on Home now, not
        // tucked behind a nav filter, so they get the same treatment as the reference design's
        // "You might like" row. The Movies/TV Shows grid screen keeps the smaller CardPresenter.
        val cardPresenter = PosterCardPresenter(repo)
        presenterSelector.addClassPresenter(
            HeroRow::class.java,
            HeroRowPresenter(
                repository = repo,
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
                    loadContinueWatching(repo, userId)
                    showHome() // also clears the placeholder row seeded in onCreate()
                    onLibrariesLoaded?.invoke(views)
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
        cardPresenter: PosterCardPresenter,
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
        Log.i(TAG, "adding row for \"${view.name}\"")
        loadedRows += ListRow(header, rowAdapter)
    }

    /** Home-only: first row shown, a big paginated banner of newly added movies and series. */
    private suspend fun loadRecentlyAdded(repo: JellyfinRepository, userId: UUID) {
        val items = runCatching { repo.getRecentlyAdded(userId) }
            .onFailure { Log.e(TAG, "getRecentlyAdded failed", it) }
            .getOrDefault(emptyList())
        Log.i(TAG, "recently added: ${items.size} item(s)")
        if (items.isEmpty()) return

        // No label above the banner itself - it's a self-contained visual, see HeroRowPresenter.
        recentlyAddedRow = HeroRow(HeaderItem(RECENTLY_ADDED_ROW_ID, ""), items)
    }

    /** Home-only: movies and episodes with an in-progress watch position, most recent first. */
    private suspend fun loadContinueWatching(repo: JellyfinRepository, userId: UUID) {
        val items = runCatching { repo.getResumeItems(userId) }
            .onFailure { Log.e(TAG, "getResumeItems failed", it) }
            .getOrDefault(emptyList())
        Log.i(TAG, "continue watching: ${items.size} item(s)")
        if (items.isEmpty()) return

        val header = HeaderItem(CONTINUE_WATCHING_ROW_ID, getString(R.string.home_continue_watching))
        val rowAdapter = ArrayObjectAdapter(ContinueWatchingPresenter(repo)).apply { addAll(0, items) }
        continueWatchingRow = ListRow(header, rowAdapter)
    }

    /** The Top Shelf hero, Continue Watching, then one poster row per library. */
    fun showHome() {
        rowsAdapter.clear()
        recentlyAddedRow?.let { rowsAdapter.add(it) }
        continueWatchingRow?.let { rowsAdapter.add(it) }
        loadedRows.forEach { rowsAdapter.add(it) }
    }

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
                .putExtra(PlaybackActivity.EXTRA_ITEM_NAME, item.name)
                .putExtra(PlaybackActivity.EXTRA_START_POSITION_TICKS, item.userData?.playbackPositionTicks ?: 0L),
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

    /**
     * A fixed branded backdrop behind the rows, set once - simpler and steadier than swapping in
     * each focused item's own art (which also meant a network fetch on every D-pad move).
     */
    private fun setStaticBackground() {
        val bitmap = BitmapFactory.decodeResource(resources, R.drawable.home_static_background)
        val scrim = ColorDrawable(ContextCompat.getColor(requireContext(), R.color.backdrop_scrim))
        backgroundManager.drawable = LayerDrawable(arrayOf(BitmapDrawable(resources, bitmap), scrim))
    }

    private companion object {
        const val TAG = "MainBrowseFragment"
        const val LOADING_ROW_ID = -1L
        const val RECENTLY_ADDED_ROW_ID = -2L
        const val CONTINUE_WATCHING_ROW_ID = -3L
    }
}
