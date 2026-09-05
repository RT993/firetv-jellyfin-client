package io.github.rt993.firetvjellyfin.data

import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.authenticateUserByName
import org.jellyfin.sdk.api.client.extensions.authenticateWithQuickConnect
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.mediaInfoApi
import org.jellyfin.sdk.api.client.extensions.mediaSegmentsApi
import org.jellyfin.sdk.api.client.extensions.quickConnectApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaSegmentDto
import org.jellyfin.sdk.model.api.MediaSegmentType
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.api.PlaybackInfoResponse
import org.jellyfin.sdk.model.api.QuickConnectResult
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetEpisodesRequest
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetNextUpRequest
import org.jellyfin.sdk.model.api.request.GetResumeItemsRequest
import org.jellyfin.sdk.model.api.request.GetSeasonsRequest
import io.github.rt993.firetvjellyfin.playback.buildDeviceProfile

/**
 * Thin, app-specific wrapper around the raw jellyfin-sdk-kotlin [ApiClient] calls this app needs.
 * Keeps call sites (fragments/activities) free of SDK request/response plumbing.
 */
class JellyfinRepository(private val api: ApiClient) {

    suspend fun loginWithPassword(username: String, password: String): AuthenticationResult {
        val response = api.userApi.authenticateUserByName(username = username, password = password)
        return response.content
    }

    suspend fun initiateQuickConnect(): QuickConnectResult =
        api.quickConnectApi.initiateQuickConnect().content

    suspend fun getQuickConnectState(secret: String): QuickConnectResult =
        api.quickConnectApi.getQuickConnectState(secret).content

    suspend fun completeQuickConnectLogin(secret: String): AuthenticationResult =
        api.userApi.authenticateWithQuickConnect(secret = secret).content

    /** The libraries (Movies, Shows, Music, …) visible to the signed-in user. */
    suspend fun getUserViews(userId: UUID): List<BaseItemDto> =
        api.userViewsApi.getUserViews(userId = userId).content.items.orEmpty()

    /**
     * Items directly inside a library/folder, newest first. Not recursive: a Shows library's
     * direct children are series (not their episodes), and a Movies library's direct children
     * are movies - recursing would pull in every episode of every series instead of one card
     * per show.
     */
    suspend fun getItems(userId: UUID, parentId: UUID, limit: Int = 50): List<BaseItemDto> {
        val request = GetItemsRequest(
            userId = userId,
            parentId = parentId,
            includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
            limit = limit,
        )
        return api.itemsApi.getItems(request).content.items.orEmpty()
    }

    /**
     * A single item with the fields the details screen needs. Jellyfin's /Items endpoint omits
     * overview, genres, and cast by default (to keep list responses small) unless explicitly
     * requested via `fields` - without this, the details screen would silently get an item with
     * a name and not much else.
     */
    suspend fun getItem(userId: UUID, itemId: UUID): BaseItemDto? {
        val request = GetItemsRequest(
            userId = userId,
            ids = listOf(itemId),
            fields = listOf(ItemFields.OVERVIEW, ItemFields.GENRES, ItemFields.PEOPLE),
            enableUserData = true,
        )
        return api.itemsApi.getItems(request).content.items.orEmpty().firstOrNull()
    }

    /** The seasons of a series, in order. */
    suspend fun getSeasons(userId: UUID, seriesId: UUID): List<BaseItemDto> {
        val request = GetSeasonsRequest(seriesId = seriesId, userId = userId)
        return api.tvShowsApi.getSeasons(request).content.items.orEmpty()
    }

    /** The episodes of one season, in order. */
    suspend fun getEpisodes(userId: UUID, seriesId: UUID, seasonId: UUID): List<BaseItemDto> {
        val request = GetEpisodesRequest(
            seriesId = seriesId,
            userId = userId,
            seasonId = seasonId,
            fields = listOf(ItemFields.OVERVIEW),
            enableUserData = true,
        )
        return api.tvShowsApi.getEpisodes(request).content.items.orEmpty()
    }

    /**
     * The most recently added movies and series across the whole library, newest first, with the
     * overview/user-data (favorite state, resume position) the hero banner needs - the plain
     * per-library [getItems] above skips both to keep those responses small.
     */
    suspend fun getRecentlyAdded(userId: UUID, limit: Int = 20): List<BaseItemDto> {
        val request = GetItemsRequest(
            userId = userId,
            recursive = true,
            includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
            sortBy = listOf(ItemSortBy.DATE_CREATED),
            sortOrder = listOf(SortOrder.DESCENDING),
            fields = listOf(ItemFields.OVERVIEW, ItemFields.GENRES),
            enableUserData = true,
            limit = limit,
        )
        return api.itemsApi.getItems(request).content.items.orEmpty()
    }

    /**
     * "Pick up where you left off": movies and episodes with an in-progress playback position for
     * this user, most recently played first (the server's own default ordering for this endpoint).
     */
    suspend fun getResumeItems(userId: UUID, limit: Int = 20): List<BaseItemDto> {
        val request = GetResumeItemsRequest(
            userId = userId,
            includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE),
            fields = listOf(ItemFields.OVERVIEW),
            enableUserData = true,
            limit = limit,
        )
        return api.itemsApi.getResumeItems(request).content.items.orEmpty()
    }

    /**
     * The Intro segment for an item, for the Skip Intro button - null if the item has none, which
     * is expected unless the server has a plugin (e.g. Intro Skipper) that actually detects and
     * tags them; this app only reads that data, it doesn't do any detection of its own.
     */
    suspend fun getIntroSegment(itemId: UUID): MediaSegmentDto? {
        val request = api.mediaSegmentsApi.getItemSegments(
            itemId = itemId,
            includeSegmentTypes = listOf(MediaSegmentType.INTRO),
        )
        return request.content.items.orEmpty().firstOrNull()
    }

    /**
     * The episode that should play after [episode] for "Play Next" - the next episode in the same
     * season, or the first episode of the next season if [episode] was the season's last. Null if
     * there's no next episode (series finale) or [episode] is missing series/season linkage.
     */
    suspend fun getNextEpisode(userId: UUID, episode: BaseItemDto): BaseItemDto? {
        val seriesId = episode.seriesId ?: return null
        val seasonId = episode.seasonId ?: return null

        val seasonEpisodes = getEpisodes(userId, seriesId, seasonId).sortedBy { it.indexNumber ?: Int.MAX_VALUE }
        val currentIndex = seasonEpisodes.indexOfFirst { it.id == episode.id }
        if (currentIndex != -1 && currentIndex + 1 < seasonEpisodes.size) {
            return seasonEpisodes[currentIndex + 1]
        }

        // Last episode of the season - the next one (if any) is the first episode of the season after this one.
        val seasons = getSeasons(userId, seriesId).sortedBy { it.indexNumber ?: Int.MAX_VALUE }
        val seasonIndex = seasons.indexOfFirst { it.id == seasonId }
        if (seasonIndex == -1) return null
        for (i in seasonIndex + 1 until seasons.size) {
            val nextSeasonEpisodes = getEpisodes(userId, seriesId, seasons[i].id).sortedBy { it.indexNumber ?: Int.MAX_VALUE }
            if (nextSeasonEpisodes.isNotEmpty()) return nextSeasonEpisodes.first()
        }
        return null
    }

    /**
     * The episode Jellyfin itself considers "up next" for this series and user - the one they're
     * mid-way through if any, otherwise the next unwatched one (starting from S1E1 for a series
     * nobody's started). Used for the series' own Play/Resume action on the details screen.
     */
    suspend fun getNextUpEpisode(userId: UUID, seriesId: UUID): BaseItemDto? {
        val request = GetNextUpRequest(
            userId = userId,
            seriesId = seriesId,
            enableUserData = true,
            limit = 1,
        )
        return api.tvShowsApi.getNextUp(request).content.items.orEmpty().firstOrNull()
    }

    fun buildImageUrl(itemId: UUID, imageType: ImageType = ImageType.PRIMARY, maxWidth: Int = 440): String =
        api.imageApi.getItemImageUrl(itemId = itemId, imageType = imageType, maxWidth = maxWidth)

    /**
     * Asks the server which of an item's media sources can be direct-played on this device and
     * which need transcoding, based on the capabilities declared in [buildDeviceProfile].
     * See [io.github.rt993.firetvjellyfin.playback.PlaybackDecisionMaker] for how the result is used.
     *
     * [audioStreamIndex] only matters for a transcode: the output only ever carries the one audio
     * track the server was told to encode, so switching tracks mid-transcode means re-requesting
     * playback info with a different index here (see PlaybackActivity.selectAudioTrack) - it's
     * ignored for direct play, where every track in the file reaches the client either way and
     * ExoPlayer switches locally. [mediaSourceId] pins a re-request to the same media source
     * instead of letting the server pick again from scratch.
     *
     * [subtitleStreamIndex] defaults to -1 (Jellyfin's own "no subtitle" convention), not null -
     * leaving it unset makes the server fall back to the file's own default subtitle track, and
     * for an image-based one (PGS/VobSub, common on Blu-ray rips and often flagged default) that
     * means silently burning it into the video during any transcode, whether anyone asked for
     * subtitles or not. This client never needs the server to do that: every subtitle PlaybackActivity
     * shows is sideloaded client-side from MediaStream.deliveryUrl, so the server should never be
     * asked to touch subtitles at all.
     */
    suspend fun getPlaybackInfo(
        userId: UUID,
        itemId: UUID,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = -1,
        mediaSourceId: String? = null,
    ): PlaybackInfoResponse {
        val request = PlaybackInfoDto(
            userId = userId,
            deviceProfile = buildDeviceProfile(),
            autoOpenLiveStream = true,
            audioStreamIndex = audioStreamIndex,
            subtitleStreamIndex = subtitleStreamIndex,
            mediaSourceId = mediaSourceId,
        )
        return api.mediaInfoApi.getPostedPlaybackInfo(itemId = itemId, data = request).content
    }

    val deviceId: String
        get() = api.deviceInfo.id
}
