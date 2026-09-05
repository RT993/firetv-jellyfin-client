package io.github.rt993.firetvjellyfin.ui.home

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.rt993.firetvjellyfin.R
import io.github.rt993.firetvjellyfin.data.JellyfinClientHolder
import io.github.rt993.firetvjellyfin.data.JellyfinRepository
import io.github.rt993.firetvjellyfin.ui.details.ItemDetailsActivity
import io.github.rt993.firetvjellyfin.ui.library.LibraryGridActivity
import io.github.rt993.firetvjellyfin.ui.login.LoginActivity
import io.github.rt993.firetvjellyfin.ui.playback.PlaybackActivity
import io.github.rt993.firetvjellyfin.ui.splash.SplashActivity
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

        val api = JellyfinClientHolder.api
        val userIdString = JellyfinClientHolder.currentUserId()
        val userId = userIdString?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        if (api == null || userId == null) {
            Log.e(TAG, "Missing session (api=$api, userId=$userIdString)")
            Toast.makeText(this, "Not signed in", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val repository = JellyfinRepository(api)

        setContent {
            HomeScreen(
                repository = repository,
                userId = userId,
                onOpenDetails = ::openDetails,
                onPlay = ::openPlaybackOrDetails,
                onOpenLibrary = ::openLibraryGrid,
                onSearch = { Toast.makeText(this, R.string.nav_search_unavailable, Toast.LENGTH_SHORT).show() },
                onShowAccountInfo = ::showAccountInfo,
                onLogout = ::logOut,
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

    private fun showAccountInfo() {
        val username = JellyfinClientHolder.currentUsername().orEmpty()
        val serverUrl = JellyfinClientHolder.api?.baseUrl.orEmpty()
        Toast.makeText(this, getString(R.string.user_menu_info_format, username, serverUrl), Toast.LENGTH_LONG).show()
    }

    private fun logOut() {
        JellyfinClientHolder.signOut()
        startActivity(Intent(this, LoginActivity::class.java).putExtra(SplashActivity.EXTRA_FROM_SPLASH, true))
        finish()
    }

    private companion object {
        const val TAG = "HomeActivity"
    }
}
