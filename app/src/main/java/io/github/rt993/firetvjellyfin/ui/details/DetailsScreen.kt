package io.github.rt993.firetvjellyfin.ui.details

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import io.github.rt993.firetvjellyfin.R
import io.github.rt993.firetvjellyfin.data.JellyfinRepository
import io.github.rt993.firetvjellyfin.ui.theme.AmbientGlow
import io.github.rt993.firetvjellyfin.ui.theme.FocusableCard
import io.github.rt993.firetvjellyfin.ui.theme.TreeHouseAccent
import io.github.rt993.firetvjellyfin.ui.theme.TreeHouseBackground
import io.github.rt993.firetvjellyfin.ui.theme.TreeHouseSurface
import io.github.rt993.firetvjellyfin.ui.theme.TreeHouseTextPrimary
import io.github.rt993.firetvjellyfin.ui.theme.TreeHouseTextSecondary
import io.github.rt993.firetvjellyfin.ui.theme.TreeHouseTheme
import io.github.rt993.firetvjellyfin.ui.theme.ambientColorFor
import io.github.rt993.firetvjellyfin.util.formatRuntimeTicks
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PersonKind
import org.jellyfin.sdk.model.api.VideoRangeType

private const val TAG = "DetailsScreen"
private const val EPISODE_CARD_WIDTH_DP = 220
private const val EPISODE_ASPECT_RATIO = 16f / 9f
private const val POSTER_ASPECT_RATIO = 2f / 3f
private const val MAX_CAST_SHOWN = 6

/**
 * Split-layout details screen, replacing the old Leanback [androidx.leanback.app
 * .DetailsSupportFragment]/[FullWidthDetailsOverviewRowPresenter] one entirely: full-bleed
 * backdrop on the right, a poster with a per-title [AmbientGlow] plus metadata/technical
 * badges/actions on the left (the "trakt.tv show page" look), and - for a series - an inline row
 * of seasons that swaps the episode row below it, instead of a separate season pick screen.
 */
@Composable
fun DetailsScreen(
    repository: JellyfinRepository,
    userId: UUID,
    itemId: UUID,
    onPlay: (BaseItemDto) -> Unit,
) {
    var item by remember { mutableStateOf<BaseItemDto?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var playTarget by remember { mutableStateOf<BaseItemDto?>(null) }
    var seasons by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var isFavorite by remember { mutableStateOf(false) }
    var ambientColor by remember { mutableStateOf<Color?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(itemId) {
        val loaded = runCatching { repository.getItem(userId, itemId) }
            .onFailure { Log.e(TAG, "getItem failed", it) }
            .getOrNull()
        if (loaded == null) {
            loadError = "Could not load this item's details."
            return@LaunchedEffect
        }
        item = loaded
        isFavorite = loaded.userData?.isFavorite ?: false
        playTarget = if (loaded.type == BaseItemKind.SERIES) {
            resolveSeriesPlayTarget(repository, userId, loaded)
        } else {
            loaded
        }
        if (loaded.type == BaseItemKind.SERIES) {
            seasons = runCatching { repository.getSeasons(userId, loaded.id) }
                .onFailure { Log.e(TAG, "getSeasons failed", it) }
                .getOrDefault(emptyList())
                .sortedBy { it.indexNumber ?: Int.MAX_VALUE }
        }
        // The "trakt.tv show page" ambient glow behind the poster - one extraction per screen
        // open, not per focus/frame, so it's cheap enough even on the low-end Fire Stick hardware
        // this app targets.
        ambientColor = ambientColorFor(context, repository.buildImageUrl(loaded.id, maxWidth = 200))
    }

    val currentItem = item
    if (currentItem == null) {
        TreeHouseTheme {
            Box(Modifier.fillMaxSize().background(TreeHouseBackground)) {
                loadError?.let {
                    Text(it, color = TreeHouseTextSecondary, modifier = Modifier.align(Alignment.Center).padding(48.dp))
                }
            }
        }
        return
    }

    TreeHouseTheme {
        Box(Modifier.fillMaxSize().background(TreeHouseBackground)) {
            DetailsBackdrop(item = currentItem, repository = repository)

            Column(Modifier.fillMaxSize()) {
                DetailsInfoPanel(
                    item = currentItem,
                    repository = repository,
                    ambientColor = ambientColor,
                    playTarget = playTarget,
                    isFavorite = isFavorite,
                    onPlay = { playTarget?.let(onPlay) },
                    onToggleFavorite = {
                        val newValue = !isFavorite
                        isFavorite = newValue
                        scope.launch {
                            runCatching { repository.setFavorite(userId, itemId, newValue) }
                                .onFailure { Log.e(TAG, "setFavorite failed", it) }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )

                if (currentItem.type == BaseItemKind.SERIES && seasons.isNotEmpty()) {
                    SeasonsAndEpisodes(
                        repository = repository,
                        userId = userId,
                        series = currentItem,
                        seasons = seasons,
                        onPlayEpisode = onPlay,
                        modifier = Modifier.fillMaxWidth().height(260.dp),
                    )
                }
            }
        }
    }
}

/**
 * What a series' own Play/Resume action should start: whatever episode Jellyfin itself considers
 * "up next" for this user, falling back to a direct S1E1 lookup only if that fails.
 */
private suspend fun resolveSeriesPlayTarget(repository: JellyfinRepository, userId: UUID, series: BaseItemDto): BaseItemDto? {
    runCatching { repository.getNextUpEpisode(userId, series.id) }
        .onFailure { Log.e(TAG, "getNextUpEpisode failed", it) }
        .getOrNull()
        ?.let { return it }

    val firstSeason = runCatching { repository.getSeasons(userId, series.id) }
        .onFailure { Log.e(TAG, "getSeasons failed", it) }
        .getOrDefault(emptyList())
        .minByOrNull { it.indexNumber ?: Int.MAX_VALUE }
        ?: return null
    return runCatching { repository.getEpisodes(userId, series.id, firstSeason.id) }
        .onFailure { Log.e(TAG, "getEpisodes failed", it) }
        .getOrDefault(emptyList())
        .minByOrNull { it.indexNumber ?: Int.MAX_VALUE }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun DetailsBackdrop(item: BaseItemDto, repository: JellyfinRepository) {
    Box(Modifier.fillMaxSize()) {
        if (!item.backdropImageTags.isNullOrEmpty()) {
            GlideImage(
                model = repository.buildImageUrl(item.id, imageType = ImageType.BACKDROP, maxWidth = 1920),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        // Left-to-right mask so the info panel stays readable against the art; the backdrop
        // itself stays visible further right, per the "split layout" brief.
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to TreeHouseBackground,
                    0.45f to TreeHouseBackground.copy(alpha = 0.8f),
                    0.75f to Color.Transparent,
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color.Transparent, 0.65f to Color.Transparent, 1f to TreeHouseBackground),
            ),
        )
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun DetailsInfoPanel(
    item: BaseItemDto,
    repository: JellyfinRepository,
    ambientColor: Color?,
    playTarget: BaseItemDto?,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.padding(start = 48.dp, top = 64.dp, end = 24.dp)) {
        AmbientGlow(color = ambientColor, modifier = Modifier.size(width = 320.dp, height = 420.dp)) {
            GlideImage(
                model = repository.buildImageUrl(item.id, maxWidth = 400),
                contentDescription = null,
                modifier = Modifier
                    .width(200.dp)
                    .aspectRatio(POSTER_ASPECT_RATIO)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
            )
        }
        Spacer(Modifier.width(28.dp))
        // A capped max width, not weight(1f) - the latter stretched this column across whatever
        // was left of the row's full width (previously squeezed narrow by a sibling Spacer that
        // split the screen in half, then widened to the full remaining width once that Spacer was
        // removed) - either way, every Text inside ends up as wide as this Modifier says, and an
        // unconstrained weight(1f) here doesn't reserve room for the backdrop art on the right
        // (too wide) or wraps almost every word onto its own line (too narrow, "vertical" text).
        DetailsMetadata(
            item = item,
            playTarget = playTarget,
            isFavorite = isFavorite,
            onPlay = onPlay,
            onToggleFavorite = onToggleFavorite,
            modifier = Modifier.widthIn(max = 560.dp),
        )
    }
}

@Composable
private fun DetailsMetadata(
    item: BaseItemDto,
    playTarget: BaseItemDto?,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(item.name.orEmpty(), style = MaterialTheme.typography.headlineLarge, color = TreeHouseTextPrimary)
        Spacer(Modifier.height(12.dp))
        Text(buildMetaLine(item), color = TreeHouseTextSecondary, style = MaterialTheme.typography.bodyMedium)

        val badges = buildTechnicalBadges(item)
        if (badges.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                badges.forEach { TechnicalBadge(it) }
            }
        }

        item.genres?.takeIf { it.isNotEmpty() }?.let { genres ->
            Spacer(Modifier.height(12.dp))
            Text(genres.joinToString("  •  "), color = TreeHouseTextSecondary, style = MaterialTheme.typography.bodyMedium)
        }

        item.overview?.takeIf { it.isNotBlank() }?.let { overview ->
            Spacer(Modifier.height(16.dp))
            Text(
                overview,
                color = TreeHouseTextSecondary,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.widthIn(max = 520.dp),
            )
        }

        val cast = item.people?.filter { it.type == PersonKind.ACTOR }?.take(MAX_CAST_SHOWN)?.mapNotNull { it.name }
        if (!cast.isNullOrEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.details_starring_format, cast.joinToString(", ")),
                color = TreeHouseTextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.widthIn(max = 520.dp),
            )
        }

        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (playTarget != null) {
                val isResume = (playTarget.userData?.playbackPositionTicks ?: 0L) > 0L
                Button(onClick = onPlay) {
                    Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_hero_play), contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (isResume) R.string.details_resume else R.string.details_play))
                }
            }
            Button(
                onClick = onToggleFavorite,
                colors = ButtonDefaults.colors(containerColor = if (isFavorite) TreeHouseAccent else TreeHouseSurface),
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(if (isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_outline),
                    contentDescription = null,
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (isFavorite) R.string.details_watchlisted else R.string.details_watchlist))
            }
        }
    }
}

@Composable
private fun TechnicalBadge(text: String) {
    Box(
        Modifier
            .border(1.dp, TreeHouseTextSecondary, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(text, color = TreeHouseTextPrimary, style = MaterialTheme.typography.labelSmall)
    }
}

private fun buildMetaLine(item: BaseItemDto): String {
    val parts = mutableListOf<String>()
    item.communityRating?.let { parts += "★ %.1f".format(it) }
    item.productionYear?.let { parts += it.toString() }
    formatRuntimeTicks(item.runTimeTicks)?.let { parts += it }
    item.officialRating?.let { parts += it }
    return parts.joinToString("  •  ")
}

/** Resolution, dynamic range (Dolby Vision/HDR), and audio (Dolby Atmos/surround) badges. */
private fun buildTechnicalBadges(item: BaseItemDto): List<String> {
    val videoStream = item.mediaStreams?.firstOrNull { it.type == MediaStreamType.VIDEO }
    val audioStream = item.mediaStreams?.firstOrNull { it.type == MediaStreamType.AUDIO }
    return listOfNotNull(
        resolutionBadge(videoStream, item),
        dynamicRangeBadge(videoStream),
        audioBadge(audioStream),
    )
}

private fun resolutionBadge(videoStream: MediaStream?, item: BaseItemDto): String? {
    val height = videoStream?.height ?: item.height ?: return null
    return when {
        height >= 2000 -> "4K"
        height >= 1000 -> "1080p"
        height >= 700 -> "720p"
        else -> "SD"
    }
}

private fun dynamicRangeBadge(videoStream: MediaStream?): String? = when (videoStream?.videoRangeType) {
    VideoRangeType.DOVI, VideoRangeType.DOVI_WITH_HDR10, VideoRangeType.DOVI_WITH_HLG, VideoRangeType.DOVI_WITH_SDR -> "Dolby Vision"
    VideoRangeType.HDR10, VideoRangeType.HDR10_PLUS -> "HDR10"
    VideoRangeType.HLG -> "HLG"
    else -> null
}

private fun audioBadge(audioStream: MediaStream?): String? {
    if (audioStream == null) return null
    val descriptor = listOfNotNull(audioStream.profile, audioStream.displayTitle, audioStream.codec).joinToString(" ")
    if (descriptor.contains("atmos", ignoreCase = true)) return "Dolby Atmos"
    return when (audioStream.channels) {
        8 -> "7.1 Surround"
        6 -> "5.1 Surround"
        else -> audioStream.codec?.uppercase()
    }
}

@Composable
private fun SeasonsAndEpisodes(
    repository: JellyfinRepository,
    userId: UUID,
    series: BaseItemDto,
    seasons: List<BaseItemDto>,
    onPlayEpisode: (BaseItemDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedSeason by remember(seasons) { mutableStateOf(seasons.firstOrNull()) }
    var episodes by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }

    LaunchedEffect(selectedSeason?.id) {
        val season = selectedSeason
        episodes = if (season != null) {
            runCatching { repository.getEpisodes(userId, series.id, season.id) }
                .onFailure { Log.e(TAG, "getEpisodes failed for season ${season.name}", it) }
                .getOrDefault(emptyList())
        } else {
            emptyList()
        }
    }

    Column(modifier.padding(top = 12.dp)) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(seasons, key = { it.id }) { season ->
                val selected = season.id == selectedSeason?.id
                Button(
                    onClick = { selectedSeason = season },
                    colors = ButtonDefaults.colors(containerColor = if (selected) TreeHouseAccent else TreeHouseSurface),
                ) {
                    Text(season.name.orEmpty())
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(episodes, key = { it.id }) { episode ->
                EpisodeCard(episode = episode, repository = repository, onClick = { onPlayEpisode(episode) })
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun EpisodeCard(episode: BaseItemDto, repository: JellyfinRepository, onClick: () -> Unit) {
    Column {
        FocusableCard(onClick = onClick, modifier = Modifier.width(EPISODE_CARD_WIDTH_DP.dp)) {
            GlideImage(
                model = repository.buildImageUrl(episode.id, imageType = ImageType.PRIMARY, maxWidth = 440),
                contentDescription = episode.name,
                modifier = Modifier.fillMaxWidth().aspectRatio(EPISODE_ASPECT_RATIO),
                contentScale = ContentScale.Crop,
            )
        }
        Spacer(Modifier.height(6.dp))
        val number = episode.indexNumber?.let { "$it. " }.orEmpty()
        Text(
            "$number${episode.name.orEmpty()}",
            color = TreeHouseTextPrimary,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(EPISODE_CARD_WIDTH_DP.dp),
        )
    }
}
