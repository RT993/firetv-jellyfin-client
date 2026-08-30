package io.github.rt993.firetvjellyfin.data

import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.authenticateUserByName
import org.jellyfin.sdk.api.client.extensions.authenticateWithQuickConnect
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.mediaInfoApi
import org.jellyfin.sdk.api.client.extensions.quickConnectApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.api.PlaybackInfoResponse
import org.jellyfin.sdk.model.api.QuickConnectResult
import org.jellyfin.sdk.model.api.request.GetItemsRequest
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

    /** The most recently added movies across the whole library, newest first. For the Home carousel. */
    suspend fun getRecentlyAddedMovies(userId: UUID, limit: Int = 15): List<BaseItemDto> {
        val request = GetItemsRequest(
            userId = userId,
            recursive = true,
            includeItemTypes = listOf(BaseItemKind.MOVIE),
            sortBy = listOf(ItemSortBy.DATE_CREATED),
            sortOrder = listOf(SortOrder.DESCENDING),
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
        )
        return api.itemsApi.getItems(request).content.items.orEmpty().firstOrNull()
    }

    fun buildImageUrl(itemId: UUID, imageType: ImageType = ImageType.PRIMARY, maxWidth: Int = 440): String =
        api.imageApi.getItemImageUrl(itemId = itemId, imageType = imageType, maxWidth = maxWidth)

    /**
     * Asks the server which of an item's media sources can be direct-played on this device and
     * which need transcoding, based on the capabilities declared in [buildDeviceProfile].
     * See [io.github.rt993.firetvjellyfin.playback.PlaybackDecisionMaker] for how the result is used.
     */
    suspend fun getPlaybackInfo(userId: UUID, itemId: UUID): PlaybackInfoResponse {
        val request = PlaybackInfoDto(
            userId = userId,
            deviceProfile = buildDeviceProfile(),
            autoOpenLiveStream = true,
        )
        return api.mediaInfoApi.getPostedPlaybackInfo(itemId = itemId, data = request).content
    }

    val deviceId: String
        get() = api.deviceInfo.id
}
