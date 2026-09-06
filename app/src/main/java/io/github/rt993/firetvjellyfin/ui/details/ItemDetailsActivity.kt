package io.github.rt993.firetvjellyfin.ui.details

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.rt993.firetvjellyfin.data.JellyfinClientHolder
import io.github.rt993.firetvjellyfin.ui.playback.PlaybackActivity
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto

/** Hosts the Compose-for-TV [DetailsScreen] - see that file for the screen itself. */
class ItemDetailsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository = JellyfinClientHolder.repository
        val itemIdString = intent.getStringExtra(EXTRA_ITEM_ID)
        val userIdString = JellyfinClientHolder.currentUserId()
        val itemId = itemIdString?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val userId = userIdString?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        if (repository == null || itemId == null || userId == null) {
            Log.e(TAG, "Missing session or item id (repository=$repository, itemId=$itemIdString, userId=$userIdString)")
            Toast.makeText(this, "Missing session or item id", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContent {
            DetailsScreen(
                repository = repository,
                userId = userId,
                itemId = itemId,
                onPlay = ::openPlayback,
                onOpenDetails = ::openDetails,
            )
        }
    }

    private fun openPlayback(item: BaseItemDto) {
        startActivity(
            Intent(this, PlaybackActivity::class.java)
                .putExtra(PlaybackActivity.EXTRA_ITEM_ID, item.id.toString())
                .putExtra(PlaybackActivity.EXTRA_ITEM_NAME, item.name)
                .putExtra(PlaybackActivity.EXTRA_START_POSITION_TICKS, item.userData?.playbackPositionTicks ?: 0L),
        )
    }

    /** Opens another item's own Details page - e.g. a movie clicked from inside a Box Set's row. */
    private fun openDetails(item: BaseItemDto) {
        startActivity(
            Intent(this, ItemDetailsActivity::class.java)
                .putExtra(EXTRA_ITEM_ID, item.id.toString()),
        )
    }

    companion object {
        const val EXTRA_ITEM_ID = "extra_item_id"
        private const val TAG = "ItemDetailsActivity"
    }
}
