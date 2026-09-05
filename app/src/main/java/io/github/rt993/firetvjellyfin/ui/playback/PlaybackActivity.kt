package io.github.rt993.firetvjellyfin.ui.playback

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import io.github.rt993.firetvjellyfin.R
import io.github.rt993.firetvjellyfin.data.JellyfinClientHolder
import io.github.rt993.firetvjellyfin.data.JellyfinRepository
import io.github.rt993.firetvjellyfin.playback.PlaybackDecisionMaker
import io.github.rt993.firetvjellyfin.playback.PlaybackMode
import io.github.rt993.firetvjellyfin.playback.PlaybackSelection
import io.github.rt993.firetvjellyfin.playback.resolveJellyfinUrl
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaSegmentDto
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import java.util.Locale
import java.util.UUID

/**
 * A minimal, Apple-TV-style playback screen: full-bleed video with a bottom gradient overlay
 * (title, thin scrubber, rewind/play-pause/forward/next) that auto-hides after a few seconds of
 * no input and reappears on any remote press. Built by hand on a raw [SurfaceView] + [ExoPlayer]
 * rather than Leanback's boxier PlaybackTransportControlGlue row, which doesn't offer this look -
 * [AspectRatioFrameLayout] (the one piece pulled from media3-ui) is what keeps the video itself
 * letterboxed/pillarboxed correctly instead of stretched to fill the screen.
 *
 * Also drives Skip Intro (via the server's Media Segments API - only populated if the server has
 * a plugin, e.g. Intro Skipper, that actually detects and tags intros; this app only reads that
 * data) and Play Next (auto-computed sibling episode, with both a manual button and an auto-shown
 * "Up Next" prompt in the closing seconds that also auto-advances if playback runs to the end).
 */
@OptIn(UnstableApi::class)
class PlaybackActivity : FragmentActivity(R.layout.activity_playback) {

    private var player: ExoPlayer? = null
    private val uiHandler = Handler(Looper.getMainLooper())

    private var api: ApiClient? = null
    private var repository: JellyfinRepository? = null
    private var itemId: UUID? = null
    private var userId: UUID? = null
    private var introSegment: MediaSegmentDto? = null
    private var nextEpisode: BaseItemDto? = null
    private var nextEpisodeStarted = false

    // The active selection's own state - re-populated by applySelection() whenever playback
    // (re)starts, including the audio-track-triggered restarts described on selectAudioTrack().
    private var currentMode: PlaybackMode = PlaybackMode.DIRECT_PLAY
    private var currentStreamUrl: String = ""
    private var currentMediaSourceId: String? = null
    private var mediaStreams: List<MediaStream> = emptyList()
    private var currentAudioStreamIndex: Int? = null
    private var currentSubtitleStreamIndex: Int? = null

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
    private lateinit var btnNextEpisode: ImageButton
    private lateinit var btnAudioTrack: ImageButton
    private lateinit var btnSubtitleTrack: ImageButton
    private lateinit var btnSkipIntro: View
    private lateinit var upNextOverlay: View
    private lateinit var upNextTitle: TextView
    private lateinit var btnUpNextPlay: View

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
        btnNextEpisode = findViewById(R.id.btn_next_episode)
        btnAudioTrack = findViewById(R.id.btn_audio_track)
        btnSubtitleTrack = findViewById(R.id.btn_subtitle_track)
        btnSkipIntro = findViewById(R.id.btn_skip_intro)
        upNextOverlay = findViewById(R.id.up_next_overlay)
        upNextTitle = findViewById(R.id.up_next_title)
        btnUpNextPlay = findViewById(R.id.btn_up_next_play)

        btnRewind.setOnClickListener { seekBy(-SEEK_INCREMENT_MS) }
        btnForward.setOnClickListener { seekBy(SEEK_INCREMENT_MS) }
        btnPlayPause.setOnClickListener { togglePlayPause() }
        btnNextEpisode.setOnClickListener { playNextEpisodeIfAvailable() }
        btnAudioTrack.setOnClickListener { showAudioTrackPicker() }
        btnSubtitleTrack.setOnClickListener { showSubtitleTrackPicker() }
        btnUpNextPlay.setOnClickListener { playNextEpisodeIfAvailable() }
        btnSkipIntro.setOnClickListener { skipIntro() }

        val resolvedApi = JellyfinClientHolder.api ?: return finishWithError()
        api = resolvedApi
        val itemIdString = intent.getStringExtra(EXTRA_ITEM_ID)
        val userIdString = JellyfinClientHolder.currentUserId()
        val resolvedItemId = itemIdString?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return finishWithError()
        val resolvedUserId = userIdString?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return finishWithError()
        itemId = resolvedItemId
        userId = resolvedUserId

        val repo = JellyfinRepository(resolvedApi)
        repository = repo
        val decisionMaker = PlaybackDecisionMaker(resolvedApi)
        val startPositionTicks = intent.getLongExtra(EXTRA_START_POSITION_TICKS, 0L)
        titleText.text = intent.getStringExtra(EXTRA_ITEM_NAME)

        lifecycleScope.launch {
            // Kicked off alongside the essential playback-info call, not after it, so fetching the
            // full item (for Play Next) and its intro segment doesn't delay when the video starts.
            val itemDeferred = async { runCatching { repo.getItem(resolvedUserId, resolvedItemId) }.getOrNull() }
            val introDeferred = async { runCatching { repo.getIntroSegment(resolvedItemId) }.getOrNull() }

            val playbackInfo = runCatching { repo.getPlaybackInfo(resolvedUserId, resolvedItemId) }.getOrNull()
            val selection = playbackInfo?.let { decisionMaker.decide(resolvedItemId, it) }
            if (selection == null) {
                finishWithError()
                return@launch
            }
            applySelection(selection)
            currentAudioStreamIndex = selection.defaultAudioStreamIndex
            currentSubtitleStreamIndex = selection.defaultSubtitleStreamIndex
            startPlayback(resolvedApi, startPositionTicks)
            setupTrackButtons()

            introSegment = introDeferred.await()?.takeIf { it.endTicks > it.startTicks }
            val item = itemDeferred.await()
            if (item?.type == BaseItemKind.EPISODE) {
                nextEpisode = runCatching { repo.getNextEpisode(resolvedUserId, item) }.getOrNull()
                btnNextEpisode.visibility = if (nextEpisode != null) View.VISIBLE else View.GONE
            }
        }
    }

    /** Captures a decision's mode/URL/track metadata - called on initial load and on every audio-triggered restart. */
    private fun applySelection(selection: PlaybackSelection) {
        currentMode = selection.mode
        currentStreamUrl = selection.streamUrl
        currentMediaSourceId = selection.mediaSourceId
        mediaStreams = selection.mediaStreams
    }

    private fun setupTrackButtons() {
        val audioTrackCount = mediaStreams.count { it.type == MediaStreamType.AUDIO }
        btnAudioTrack.visibility = if (audioTrackCount > 1) View.VISIBLE else View.GONE
        // Image-based subtitle formats (PGS/VobSub) aren't offered - see DeviceProfileFactory for why.
        val hasTextSubtitles = mediaStreams.any { it.type == MediaStreamType.SUBTITLE && it.isTextSubtitleStream }
        btnSubtitleTrack.visibility = if (hasTextSubtitles) View.VISIBLE else View.GONE
    }

    private fun startPlayback(api: ApiClient, startPositionTicks: Long) {
        subtitleText.text = getString(
            if (currentMode == PlaybackMode.DIRECT_PLAY) R.string.playback_mode_direct else R.string.playback_mode_transcode,
        )

        // A direct-play URL is one request, and the query-string access token
        // (PlaybackDecisionMaker.withAccessToken) covers that fine. An HLS transcode is a *chain*
        // of requests instead - master playlist, then the sub-playlist it references, then each
        // segment - and there's no guarantee Jellyfin carries that query param into every URL it
        // generates along the way (it doesn't: this is exactly what was producing the 401s on
        // transcoded playback while direct play always worked). Attaching the same Authorization
        // header the SDK itself sends on every one of its own requests, as a default header on
        // ExoPlayer's HTTP data source, covers every request in the chain instead of just the first
        // - sideloaded subtitle fetches included, since they share this same data source factory.
        val authHeader = AuthorizationHeaderBuilder.buildHeader(
            api.clientInfo.name,
            api.clientInfo.version,
            api.deviceInfo.id,
            api.deviceInfo.name,
            api.accessToken,
        )
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(mapOf("Authorization" to authHeader))
        val mediaSourceFactory = DefaultMediaSourceFactory(this).setDataSourceFactory(httpDataSourceFactory)

        val exoPlayer = ExoPlayer.Builder(this).setMediaSourceFactory(mediaSourceFactory).build().also { player = it }
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

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) playNextEpisodeIfAvailable()
            }

            // Without this, a failed stream (a stalled/broken transcode session, a bad segment, a
            // network drop) just goes quiet - ExoPlayer stops, nothing plays, and there is no
            // other signal that anything went wrong at all. Logging errorCodeName here is what
            // tells you whether a given "it doesn't play" is a source/network problem
            // (e.g. IO errors reaching the transcode URL) versus something ExoPlayer itself
            // couldn't decode.
            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "Playback error (mode=$currentMode): ${error.errorCodeName}", error)
                finishWithError()
            }
        })

        // Ticks are Jellyfin's own 100-ns unit; ExoPlayer wants milliseconds - 10,000 ticks/ms.
        val startPositionMs = if (startPositionTicks > 0) startPositionTicks / 10_000L else 0L
        setMediaItemAndPrepare(startPositionMs)
        exoPlayer.playWhenReady = true

        uiHandler.post(progressRunnable)
        showControls()
        btnPlayPause.requestFocus()
    }

    /**
     * (Re)builds the [MediaItem] from [currentStreamUrl] plus whatever text subtitle is currently
     * selected, and re-prepares at [startPositionMs]. Used both for the initial start and for a
     * subtitle-only switch (selectSubtitleTrack) - a subtitle sideload attaches at MediaItem
     * creation time in ExoPlayer, there's no "just swap this one track" API, but re-preparing the
     * existing player instance (rather than tearing it down) is still cheap and needs no server
     * round trip, unlike switching audio mid-transcode (see selectAudioTrack).
     */
    private fun setMediaItemAndPrepare(startPositionMs: Long) {
        val exoPlayer = player ?: return
        val itemBuilder = MediaItem.Builder().setUri(currentStreamUrl)
        subtitleConfigurationFor(currentSubtitleStreamIndex)?.let { itemBuilder.setSubtitleConfigurations(listOf(it)) }
        exoPlayer.setMediaItem(itemBuilder.build(), startPositionMs)
        exoPlayer.prepare()
    }

    /** Null if [index] is null, isn't a subtitle stream, or has no external delivery URL (see DeviceProfileFactory). */
    private fun subtitleConfigurationFor(index: Int?): MediaItem.SubtitleConfiguration? {
        val resolvedApi = api ?: return null
        val stream = index?.let { i -> mediaStreams.firstOrNull { it.type == MediaStreamType.SUBTITLE && it.index == i } }
        val deliveryUrl = stream?.deliveryUrl ?: return null
        return MediaItem.SubtitleConfiguration.Builder(resolveJellyfinUrl(resolvedApi, deliveryUrl).toUri())
            .setMimeType(MimeTypes.TEXT_VTT)
            .setLanguage(stream.language)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
    }

    private fun trackLabel(stream: MediaStream): String =
        stream.displayTitle
            ?: listOfNotNull(stream.language, stream.codec).joinToString(" · ").takeIf { it.isNotBlank() }
            ?: "Track ${stream.index}"

    private fun showAudioTrackPicker() {
        val audioStreams = mediaStreams.filter { it.type == MediaStreamType.AUDIO }.sortedBy { it.index }
        if (audioStreams.size < 2) return
        val checkedIndex = audioStreams.indexOfFirst { it.index == currentAudioStreamIndex }.coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.playback_audio_track)
            .setSingleChoiceItems(audioStreams.map { trackLabel(it) }.toTypedArray(), checkedIndex) { dialog, which ->
                dialog.dismiss()
                selectAudioTrack(audioStreams[which])
            }
            .show()
    }

    private fun showSubtitleTrackPicker() {
        val subtitleStreams = mediaStreams.filter { it.type == MediaStreamType.SUBTITLE && it.isTextSubtitleStream }.sortedBy { it.index }
        val labels = (listOf(getString(R.string.playback_subtitles_off)) + subtitleStreams.map { trackLabel(it) }).toTypedArray()
        val checkedIndex = subtitleStreams.indexOfFirst { it.index == currentSubtitleStreamIndex }.let { if (it == -1) 0 else it + 1 }
        AlertDialog.Builder(this)
            .setTitle(R.string.playback_subtitle_track)
            .setSingleChoiceItems(labels, checkedIndex) { dialog, which ->
                dialog.dismiss()
                selectSubtitleTrack(if (which == 0) null else subtitleStreams[which - 1])
            }
            .show()
    }

    private fun selectSubtitleTrack(stream: MediaStream?) {
        val exoPlayer = player ?: return
        currentSubtitleStreamIndex = stream?.index
        setMediaItemAndPrepare(exoPlayer.currentPosition)
    }

    /**
     * Direct play sends the whole file through, so every audio track ExoPlayer parsed out of the
     * container is already available locally - a plain track-selection override switches it with
     * no server involvement. A transcode, though, only ever contains the one audio track Jellyfin
     * was told to encode; the other tracks were never sent to the client at all, so switching
     * means asking the server for a new stream with the chosen index, which needs a restart.
     */
    private fun selectAudioTrack(stream: MediaStream) {
        val exoPlayer = player ?: return
        if (currentMode == PlaybackMode.TRANSCODE) {
            restartWithAudioTrack(stream.index)
            return
        }
        val audioStreamsInOrder = mediaStreams.filter { it.type == MediaStreamType.AUDIO }.sortedBy { it.index }
        val position = audioStreamsInOrder.indexOf(stream)
        val group = exoPlayer.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.getOrNull(position)?.mediaTrackGroup ?: return
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(group, 0))
            .build()
        currentAudioStreamIndex = stream.index
    }

    private fun restartWithAudioTrack(audioStreamIndex: Int) {
        val resolvedApi = api ?: return
        val repo = repository ?: return
        val resolvedItemId = itemId ?: return
        val resolvedUserId = userId ?: return
        val exoPlayer = player ?: return
        val resumePositionMs = exoPlayer.currentPosition
        lifecycleScope.launch {
            val playbackInfo = runCatching {
                repo.getPlaybackInfo(resolvedUserId, resolvedItemId, audioStreamIndex = audioStreamIndex, mediaSourceId = currentMediaSourceId)
            }.getOrNull()
            val selection = playbackInfo?.let { PlaybackDecisionMaker(resolvedApi).decide(resolvedItemId, it) }
            if (selection == null) {
                finishWithError()
                return@launch
            }
            applySelection(selection)
            currentAudioStreamIndex = audioStreamIndex
            setMediaItemAndPrepare(resumePositionMs)
        }
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

    private fun skipIntro() {
        val segment = introSegment ?: return
        player?.seekTo(segment.endTicks / 10_000L)
        hideSkipIntro()
        updateProgress()
    }

    /**
     * Starts the next episode (if one was found) in a fresh instance of this same screen. Guarded
     * against firing twice - the manual buttons and the auto-advance-on-end listener could
     * otherwise both fire for the same transition (e.g. pressing "Play Now" right as playback
     * reaches its natural end), which would stack two next-episode screens instead of one.
     */
    private fun playNextEpisodeIfAvailable() {
        if (nextEpisodeStarted) return
        val next = nextEpisode ?: return
        nextEpisodeStarted = true
        startActivity(
            Intent(this, PlaybackActivity::class.java)
                .putExtra(EXTRA_ITEM_ID, next.id.toString())
                .putExtra(EXTRA_ITEM_NAME, next.name),
        )
        finish()
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

        updateSkipIntroVisibility(position)
        updateUpNextVisibility(position, duration)
    }

    private fun updateSkipIntroVisibility(positionMs: Long) {
        val segment = introSegment ?: return hideSkipIntro()
        val startMs = segment.startTicks / 10_000L
        val endMs = segment.endTicks / 10_000L
        if (positionMs in startMs until endMs) {
            if (btnSkipIntro.visibility != View.VISIBLE) {
                btnSkipIntro.visibility = View.VISIBLE
                btnSkipIntro.requestFocus()
            }
        } else {
            hideSkipIntro()
        }
    }

    private fun hideSkipIntro() {
        btnSkipIntro.visibility = View.GONE
    }

    private fun updateUpNextVisibility(positionMs: Long, durationMs: Long) {
        val next = nextEpisode ?: return
        val remaining = durationMs - positionMs
        if (remaining in 0..UP_NEXT_THRESHOLD_MS) {
            if (upNextOverlay.visibility != View.VISIBLE) {
                upNextTitle.text = getString(R.string.playback_up_next_format, next.name)
                upNextOverlay.visibility = View.VISIBLE
                btnUpNextPlay.requestFocus()
            }
        } else {
            upNextOverlay.visibility = View.GONE
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

        private const val TAG = "PlaybackActivity"
        private const val HIDE_DELAY_MS = 4000L
        private const val PROGRESS_UPDATE_MS = 500L
        private const val SEEK_INCREMENT_MS = 10_000L
        private const val UP_NEXT_THRESHOLD_MS = 30_000L
    }
}
