package io.github.rt993.firetvjellyfin.ui.playback

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import io.github.rt993.firetvjellyfin.R
import io.github.rt993.firetvjellyfin.data.JellyfinClientHolder
import io.github.rt993.firetvjellyfin.data.JellyfinRepository
import io.github.rt993.firetvjellyfin.playback.PlaybackDecisionMaker
import io.github.rt993.firetvjellyfin.playback.PlaybackMode
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

/**
 * A minimal, Apple-TV-style playback screen: full-bleed video with a bottom gradient overlay
 * (title, thin scrubber, rewind/play-pause/forward) that auto-hides after a few seconds of no
 * input and reappears on any remote press. Built by hand on a raw [SurfaceView] + [ExoPlayer]
 * rather than Leanback's boxier PlaybackTransportControlGlue row, which doesn't offer this look -
 * [AspectRatioFrameLayout] (the one piece pulled from media3-ui) is what keeps the video itself
 * letterboxed/pillarboxed correctly instead of stretched to fill the screen.
 */
@OptIn(UnstableApi::class)
class PlaybackActivity : FragmentActivity(R.layout.activity_playback) {

    private var player: ExoPlayer? = null
    private val uiHandler = Handler(Looper.getMainLooper())

    private lateinit var aspectContainer: AspectRatioFrameLayout
    private lateinit var playerSurface: SurfaceView
    private lateinit var controls: View
    private lateinit var titleText: TextView
    private lateinit var subtitleText: TextView
    private lateinit var positionLabel: TextView
    private lateinit var durationLabel: TextView
    private lateinit var progressTrack: View
    private lateinit var progressFill: View
    private lateinit var btnRewind: ImageButton
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnForward: ImageButton

    private val hideControlsRunnable = Runnable { controls.visibility = View.GONE }
    private val progressRunnable = object : Runnable {
        override fun run() {
            updateProgress()
            uiHandler.postDelayed(this, PROGRESS_UPDATE_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // D-pad input to ExoPlayer doesn't count as "user activity" to the OS, so the Fire TV
        // screensaver/sleep timer would otherwise kick in mid-playback as if the screen were idle.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        aspectContainer = findViewById(R.id.player_aspect_container)
        playerSurface = findViewById(R.id.player_surface)
        controls = findViewById(R.id.playback_controls)
        titleText = findViewById(R.id.playback_title)
        subtitleText = findViewById(R.id.playback_subtitle)
        positionLabel = findViewById(R.id.playback_position)
        durationLabel = findViewById(R.id.playback_duration)
        progressTrack = findViewById(R.id.playback_progress_track)
        progressFill = findViewById(R.id.playback_progress_fill)
        btnRewind = findViewById(R.id.btn_rewind)
        btnPlayPause = findViewById(R.id.btn_play_pause)
        btnForward = findViewById(R.id.btn_forward)

        btnRewind.setOnClickListener { seekBy(-SEEK_INCREMENT_MS) }
        btnForward.setOnClickListener { seekBy(SEEK_INCREMENT_MS) }
        btnPlayPause.setOnClickListener { togglePlayPause() }

        val api = JellyfinClientHolder.api ?: return finishWithError()
        val itemIdString = intent.getStringExtra(EXTRA_ITEM_ID)
        val userIdString = JellyfinClientHolder.currentUserId()
        val itemId = itemIdString?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return finishWithError()
        val userId = userIdString?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return finishWithError()

        val repository = JellyfinRepository(api)
        val decisionMaker = PlaybackDecisionMaker(api)
        val startPositionTicks = intent.getLongExtra(EXTRA_START_POSITION_TICKS, 0L)
        titleText.text = intent.getStringExtra(EXTRA_ITEM_NAME)

        lifecycleScope.launch {
            val playbackInfo = runCatching { repository.getPlaybackInfo(userId, itemId) }.getOrNull()
            val selection = playbackInfo?.let { decisionMaker.decide(itemId, it) }
            if (selection == null) {
                finishWithError()
                return@launch
            }
            startPlayback(selection.streamUrl, selection.mode, startPositionTicks)
        }
    }

    private fun startPlayback(streamUrl: String, mode: PlaybackMode, startPositionTicks: Long) {
        subtitleText.text = getString(
            if (mode == PlaybackMode.DIRECT_PLAY) R.string.playback_mode_direct else R.string.playback_mode_transcode,
        )

        val exoPlayer = ExoPlayer.Builder(this).build().also { player = it }
        exoPlayer.setVideoSurfaceView(playerSurface)
        exoPlayer.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width == 0 || videoSize.height == 0) return
                aspectContainer.setAspectRatio(videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                btnPlayPause.setImageResource(if (isPlaying) R.drawable.ic_playback_pause else R.drawable.ic_hero_play)
                btnPlayPause.contentDescription =
                    getString(if (isPlaying) R.string.playback_pause else R.string.playback_play)
            }
        })

        exoPlayer.setMediaItem(MediaItem.fromUri(streamUrl))
        // Ticks are Jellyfin's own 100-ns unit; ExoPlayer wants milliseconds - 10,000 ticks/ms.
        if (startPositionTicks > 0) exoPlayer.seekTo(startPositionTicks / 10_000L)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true

        uiHandler.post(progressRunnable)
        showControls()
        btnPlayPause.requestFocus()
    }

    private fun seekBy(deltaMs: Long) {
        val exoPlayer = player ?: return
        val duration = exoPlayer.duration.coerceAtLeast(0L)
        exoPlayer.seekTo((exoPlayer.currentPosition + deltaMs).coerceIn(0L, duration))
        updateProgress()
    }

    private fun togglePlayPause() {
        val exoPlayer = player ?: return
        exoPlayer.playWhenReady = !exoPlayer.playWhenReady
    }

    private fun updateProgress() {
        val exoPlayer = player ?: return
        val duration = exoPlayer.duration
        val position = exoPlayer.currentPosition
        if (duration <= 0) return

        positionLabel.text = formatTime(position)
        durationLabel.text = formatTime(duration)

        val trackWidth = progressTrack.width
        if (trackWidth > 0) {
            val fraction = (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            progressFill.layoutParams = progressFill.layoutParams.apply { width = (trackWidth * fraction).toInt() }
            progressFill.requestLayout()
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
        }
    }

    private fun showControls() {
        controls.visibility = View.VISIBLE
        uiHandler.removeCallbacks(hideControlsRunnable)
        uiHandler.postDelayed(hideControlsRunnable, HIDE_DELAY_MS)
    }

    /**
     * Any remote input should keep the controls up and reset the auto-hide timer - but the press
     * that wakes a hidden overlay only reveals it rather than also acting (e.g. pressing center to
     * unhide shouldn't also toggle play/pause the instant the button appears under it). Back is
     * exempted entirely so it always exits playback immediately, never just dismisses the overlay.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode != KeyEvent.KEYCODE_BACK) {
            val wasHidden = controls.visibility != View.VISIBLE
            showControls()
            if (wasHidden) {
                btnPlayPause.requestFocus()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun finishWithError() {
        Toast.makeText(this, R.string.playback_error, Toast.LENGTH_LONG).show()
        finish()
    }

    override fun onStop() {
        super.onStop()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        uiHandler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
    }

    companion object {
        const val EXTRA_ITEM_ID = "extra_item_id"
        const val EXTRA_ITEM_NAME = "extra_item_name"
        /** Where to start playback from, in Jellyfin's 100-ns ticks - 0 (the default) starts from the beginning. */
        const val EXTRA_START_POSITION_TICKS = "extra_start_position_ticks"

        private const val HIDE_DELAY_MS = 4000L
        private const val PROGRESS_UPDATE_MS = 500L
        private const val SEEK_INCREMENT_MS = 10_000L
    }
}
