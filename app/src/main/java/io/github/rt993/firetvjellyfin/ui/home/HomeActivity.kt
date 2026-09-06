package io.github.rt993.firetvjellyfin.ui.home

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import io.github.rt993.firetvjellyfin.R
import io.github.rt993.firetvjellyfin.data.JellyfinClientHolder
import io.github.rt993.firetvjellyfin.ui.details.ItemDetailsActivity
import io.github.rt993.firetvjellyfin.ui.library.LibraryGridActivity
import io.github.rt993.firetvjellyfin.ui.playback.PlaybackActivity
import io.github.rt993.firetvjellyfin.ui.profile.ProfileSelectActivity
import io.github.rt993.firetvjellyfin.ui.profile.ServerListActivity
import io.github.rt993.firetvjellyfin.ui.splash.SplashActivity
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

/**
 * Hosts the Compose-for-TV [HomeScreen] (see that file for the screen itself). The other screens
 * (Library grid, Details, Playback) are unchanged Leanback/View Activities, reached the same way
 * as before via [Intent] - only Home has been rewritten.
 */
class HomeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository = JellyfinClientHolder.repository
        val userIdString = JellyfinClientHolder.currentUserId()
        val userId = userIdString?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        if (repository == null || userId == null) {
            Log.e(TAG, "Missing session (repository=$repository, userId=$userIdString)")
            Toast.makeText(this, "Not signed in", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContent {
            HomeScreen(
                repository = repository,
                userId = userId,
                onOpenDetails = ::openDetails,
                onPlay = ::openPlaybackOrDetails,
                onOpenLibrary = ::openLibraryGrid,
                onShowAccountInfo = ::showAccountInfo,
                onLogout = ::logOut,
                onChangeServer = ::changeServer,
                hasMultipleServers = JellyfinClientHolder.savedServers().size > 1,
                onScanLibrary = ::scanLibrary,
            )
        }
    }

    private fun openDetails(item: BaseItemDto) {
        startActivity(
            Intent(this, ItemDetailsActivity::class.java)
                .putExtra(ItemDetailsActivity.EXTRA_ITEM_ID, item.id.toString()),
        )
    }

    /** A series has no single video to play - route it to the season/episode picker instead. */
    private fun openPlaybackOrDetails(item: BaseItemDto) {
        if (item.type == BaseItemKind.SERIES) {
            openDetails(item)
            return
        }
        startActivity(
            Intent(this, PlaybackActivity::class.java)
                .putExtra(PlaybackActivity.EXTRA_ITEM_ID, item.id.toString())
                .putExtra(PlaybackActivity.EXTRA_ITEM_NAME, item.name)
                .putExtra(PlaybackActivity.EXTRA_START_POSITION_TICKS, item.userData?.playbackPositionTicks ?: 0L),
        )
    }

    private fun openLibraryGrid(library: BaseItemDto) {
        startActivity(
            Intent(this, LibraryGridActivity::class.java)
                .putExtra(LibraryGridActivity.EXTRA_LIBRARY_ID, library.id.toString())
                .putExtra(LibraryGridActivity.EXTRA_TITLE, library.name.orEmpty()),
        )
    }

    /**
     * Asks the server to start a full library scan, same as clicking "Scan All Libraries" in its
     * own dashboard - handy for picking up newly added files without leaving the TV. Fire-and-
     * forget: the scan runs server-side, so this just confirms the request went through rather
     * than tracking its progress.
     */
    private fun scanLibrary() {
        val repository = JellyfinClientHolder.repository ?: return
        lifecycleScope.launch {
            runCatching { repository.refreshLibrary() }
                .onSuccess { Toast.makeText(this@HomeActivity, R.string.library_scan_started, Toast.LENGTH_LONG).show() }
                .onFailure {
                    Log.e(TAG, "refreshLibrary failed", it)
                    Toast.makeText(this@HomeActivity, R.string.library_scan_failed, Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun showAccountInfo() {
        val username = JellyfinClientHolder.currentUsername().orEmpty()
        val serverUrl = JellyfinClientHolder.api?.baseUrl.orEmpty()
        Toast.makeText(this, getString(R.string.user_menu_info_format, username, serverUrl), Toast.LENGTH_LONG).show()
    }

    private fun logOut() {
        JellyfinClientHolder.signOut()
        startActivity(Intent(this, ProfileSelectActivity::class.java).putExtra(SplashActivity.EXTRA_FROM_SPLASH, true))
        finish()
    }

    /**
     * Signs out of the current profile and opens the server list directly, skipping the profile
     * picker for the server being left - picking a different server there takes over navigation
     * from here (see ServerListActivity.selectServer).
     */
    private fun changeServer() {
        JellyfinClientHolder.signOut()
        startActivity(Intent(this, ServerListActivity::class.java))
        finish()
    }

    private companion object {
        const val TAG = "HomeActivity"
    }
}
