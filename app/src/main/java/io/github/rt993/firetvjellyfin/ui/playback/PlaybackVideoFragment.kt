package io.github.rt993.firetvjellyfin.ui.playback

import android.os.Bundle
import android.widget.Toast
import androidx.leanback.app.VideoSupportFragment
import androidx.leanback.app.VideoSupportFragmentGlueHost
import androidx.leanback.media.PlaybackTransportControlGlue
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.leanback.LeanbackPlayerAdapter
import io.github.rt993.firetvjellyfin.R
import io.github.rt993.firetvjellyfin.data.JellyfinClientHolder
import io.github.rt993.firetvjellyfin.data.JellyfinRepository
import io.github.rt993.firetvjellyfin.playback.PlaybackDecisionMaker
import io.github.rt993.firetvjellyfin.playback.PlaybackMode
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Hosts a Media3 [ExoPlayer] behind leanback's playback transport controls.
 *
 * The direct-play-vs-transcode choice itself is made in [PlaybackDecisionMaker] before this
 * fragment ever runs - by the time [startPlayback] is called there is just one concrete URL to
 * hand to ExoPlayer, whichever mode won.
 */
class PlaybackVideoFragment : VideoSupportFragment() {

    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val api = JellyfinClientHolder.api ?: return finishWithError()
        val itemIdString = requireActivity().intent.getStringExtra(PlaybackActivity.EXTRA_ITEM_ID)
        val userIdString = JellyfinClientHolder.currentUserId()
        val itemId = itemIdString?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return finishWithError()
        val userId = userIdString?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return finishWithError()

        val repository = JellyfinRepository(api)
        val decisionMaker = PlaybackDecisionMaker(api)
        val itemName = requireActivity().intent.getStringExtra(PlaybackActivity.EXTRA_ITEM_NAME)

        lifecycleScope.launch {
            val playbackInfo = runCatching { repository.getPlaybackInfo(userId, itemId) }.getOrNull()
            val selection = playbackInfo?.let { decisionMaker.decide(itemId, it) }
            if (selection == null) {
                finishWithError()
                return@launch
            }
            startPlayback(selection.streamUrl, selection.mode, itemName)
        }
    }

    private fun startPlayback(streamUrl: String, mode: PlaybackMode, itemName: String?) {
        val exoPlayer = ExoPlayer.Builder(requireContext()).build().also { player = it }

        val playerAdapter = LeanbackPlayerAdapter(requireContext(), exoPlayer, UPDATE_PERIOD_MS)
        val glue = PlaybackTransportControlGlue(requireContext(), playerAdapter)
        glue.host = VideoSupportFragmentGlueHost(this)
        glue.title = itemName
        glue.setSubtitle(
            getString(
                if (mode == PlaybackMode.DIRECT_PLAY) R.string.playback_mode_direct
                else R.string.playback_mode_transcode,
            ),
        )

        exoPlayer.setMediaItem(MediaItem.fromUri(streamUrl))
        exoPlayer.prepare()
        glue.playWhenPrepared()
    }

    private fun finishWithError() {
        Toast.makeText(requireContext(), R.string.playback_error, Toast.LENGTH_LONG).show()
        requireActivity().finish()
    }

    override fun onStop() {
        super.onStop()
        player?.release()
        player = null
    }

    private companion object {
        const val UPDATE_PERIOD_MS = 1000
    }
}
