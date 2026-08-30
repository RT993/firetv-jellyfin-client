package io.github.rt993.firetvjellyfin.playback

import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.videosApi
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.PlaybackInfoResponse

/** How a media source ended up being played. Surfaced in the UI so this is never a silent choice. */
enum class PlaybackMode { DIRECT_PLAY, TRANSCODE }

data class PlaybackSelection(
    val mode: PlaybackMode,
    val streamUrl: String,
    val mediaSourceId: String,
)

/**
 * The single, isolated place where this app decides direct-play vs. transcode.
 *
 * The server already told us, per media source, whether it [MediaSourceInfo.supportsDirectPlay]
 * given the [DeviceProfile][buildDeviceProfile] we submitted with the playback info request (see
 * [io.github.rt993.firetvjellyfin.data.JellyfinRepository.getPlaybackInfo]). This class does not
 * re-evaluate codec compatibility itself - it only turns that server decision into a concrete,
 * playable URL. Swap this implementation if you want the client to make the call locally instead
 * (e.g. by inspecting MediaCodecList) rather than trusting the server's DeviceProfile matching.
 */
class PlaybackDecisionMaker(private val api: ApiClient) {

    fun decide(itemId: UUID, playbackInfo: PlaybackInfoResponse): PlaybackSelection? {
        val source = playbackInfo.mediaSources.firstOrNull() ?: return null
        val mediaSourceId = source.id ?: return null

        return when {
            source.supportsDirectPlay -> directPlay(itemId, source, mediaSourceId, playbackInfo.playSessionId)
            source.supportsTranscoding -> transcode(source, mediaSourceId)
            else -> null
        }
    }

    private fun directPlay(
        itemId: UUID,
        source: MediaSourceInfo,
        mediaSourceId: String,
        playSessionId: String?,
    ): PlaybackSelection {
        val url = api.videosApi.getVideoStreamUrl(
            itemId = itemId,
            static = true,
            container = source.container,
            mediaSourceId = mediaSourceId,
            deviceId = api.deviceInfo.id,
            playSessionId = playSessionId,
        )
        return PlaybackSelection(PlaybackMode.DIRECT_PLAY, withAccessToken(url), mediaSourceId)
    }

    private fun transcode(source: MediaSourceInfo, mediaSourceId: String): PlaybackSelection? {
        val relativeOrAbsoluteUrl = source.transcodingUrl ?: return null
        val absoluteUrl = if (relativeOrAbsoluteUrl.startsWith("http", ignoreCase = true)) {
            relativeOrAbsoluteUrl
        } else {
            (api.baseUrl.orEmpty().trimEnd('/')) + "/" + relativeOrAbsoluteUrl.trimStart('/')
        }
        return PlaybackSelection(PlaybackMode.TRANSCODE, withAccessToken(absoluteUrl), mediaSourceId)
    }

    /**
     * The generated URL helpers do not embed auth, since the SDK normally injects it via HTTP
     * headers on requests it makes itself. ExoPlayer fetches this URL directly, outside the SDK's
     * HTTP client, so the access token must be attached to the URL explicitly.
     */
    private fun withAccessToken(url: String): String {
        val token = api.accessToken ?: return url
        val separator = if (url.contains('?')) '&' else '?'
        return "$url$separator${ApiClient.QUERY_ACCESS_TOKEN}=$token"
    }
}
