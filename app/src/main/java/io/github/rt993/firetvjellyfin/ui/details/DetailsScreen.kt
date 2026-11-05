package io.github.rt993.firetvjellyfin.ui.details

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.bumptech.glide.Glide
import com.bumptech.glide.integration.compose.CrossFade
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import io.github.rt993.firetvjellyfin.R
import io.github.rt993.firetvjellyfin.data.JellyfinRepository
import io.github.rt993.firetvjellyfin.ui.theme.BoxBlurTransformation
import io.github.rt993.firetvjellyfin.ui.theme.FocusableCard
import io.github.rt993.firetvjellyfin.ui.theme.focusParallax
import io.github.rt993.firetvjellyfin.ui.theme.kenBurnsDrift
import io.github.rt993.firetvjellyfin.ui.theme.TreeHouseBackground
import io.github.rt993.firetvjellyfin.ui.theme.TreeHouseTextPrimary
import io.github.rt993.firetvjellyfin.ui.theme.TreeHouseTextSecondary
import io.github.rt993.firetvjellyfin.ui.theme.TreeHouseTheme
import io.github.rt993.firetvjellyfin.util.formatRuntimeTicks
import kotlinx.coroutines.async
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
private const val BOX_SET_MOVIE_CARD_WIDTH_DP = 150
private const val POSTER_ASPECT_RATIO = 2f / 3f
private const val MAX_CAST_SHOWN = 6

// Matches DetailsMetadata's own left inset, so the season/episode row lines up under the title
// above it instead of starting flush at the screen edge.
private val SEASON_ROW_START = 48.dp

// A shared "frosted glass" pill style - translucent white fill plus a thin lighter border - used
// for the season picker, Play, and Watchlist buttons instead of a solid color fill.
private val GlassContainer = Color.White.copy(alpha = 0.12f)
private val GlassContainerActive = Color.White.copy(alpha = 0.24f)
private val GlassBorder = Color.White.copy(alpha = 0.35f)
private val GlassShape = RoundedCornerShape(20.dp)

/**
 * Full-bleed-hero details screen, replacing the old Leanback [androidx.leanback.app
 * .DetailsSupportFragment]/[FullWidthDetailsOverviewRowPresenter] one entirely: the backdrop art
 * fills the whole screen with a bottom gradient, title/metadata/actions sit bottom-left over it
 * and the cast list bottom-right (the Apple TV title-page look), and - for a series - an inline
 * row of seasons that swaps the episode row below it, instead of a separate season pick screen.
 */
@Composable
fun DetailsScreen(
    repository: JellyfinRepository,
    userId: UUID,
    itemId: UUID,
    onPlay: (BaseItemDto) -> Unit,
    onOpenDetails: (BaseItemDto) -> Unit,
) {
    var item by remember { mutableStateOf<BaseItemDto?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var playTarget by remember { mutableStateOf<BaseItemDto?>(null) }
    var seasons by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var boxSetMovies by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var isFavorite by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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

        // playTarget/seasons/boxSetMovies are all independent of each other - awaiting them one
        // after another (as before) meant up to three sequential network round trips before the
        // screen was fully interactive. Running them concurrently cuts that to roughly the
        // slowest single one.
        val seasonsDeferred = async {
            if (loaded.type != BaseItemKind.SERIES) {
                emptyList()
            } else {
                runCatching { repository.getSeasons(userId, loaded.id) }
                    .onFailure { Log.e(TAG, "getSeasons failed", it) }
                    .getOrDefault(emptyList())
                    .sortedBy { it.indexNumber ?: Int.MAX_VALUE }
            }
        }
        val boxSetMoviesDeferred = async {
            if (loaded.type != BaseItemKind.BOX_SET) {
                emptyList()
            } else {
                // Not necessarily meaningfully ordered as returned by the server - sorting by
                // release year gives a coherent "first movie" for the collection's own Play
                // button below, and a sensible left-to-right order for the row itself.
                runCatching { repository.getItems(userId, loaded.id) }
                    .onFailure { Log.e(TAG, "getItems failed for box set ${loaded.name}", it) }
                    .getOrDefault(emptyList())
                    .sortedBy { it.productionYear ?: Int.MAX_VALUE }
            }
        }
        // A Box Set's own Play button starts the first movie in it (awaiting boxSetMoviesDeferred,
        // already running concurrently above), rather than being a no-op or requiring a click into
        // a nested item first - the collection itself has no video stream of its own.
        val playTargetDeferred = async {
            when (loaded.type) {
                BaseItemKind.BOX_SET -> boxSetMoviesDeferred.await().firstOrNull()
                BaseItemKind.SERIES -> resolveSeriesPlayTarget(repository, userId, loaded)
                else -> loaded
            }
        }

        seasons = seasonsDeferred.await()
        boxSetMovies = boxSetMoviesDeferred.await()
        playTarget = playTargetDeferred.await()
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

                if (currentItem.type == BaseItemKind.BOX_SET && boxSetMovies.isNotEmpty()) {
                    BoxSetMovies(
                        repository = repository,
                        movies = boxSetMovies,
                        onOpenDetails = onOpenDetails,
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
            val context = LocalContext.current
            val thumbnailUrl = repository.buildImageUrl(item.id, imageType = ImageType.BACKDROP, maxWidth = 60)
            GlideImage(
                model = repository.buildImageUrl(item.id, imageType = ImageType.BACKDROP, maxWidth = 1920),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().kenBurnsDrift(),
                contentScale = ContentScale.Crop,
                transition = CrossFade,
                // A blurred, near-instant low-res thumbnail shows first and crossfades into the
                // full-resolution backdrop as it loads, instead of the art popping in abruptly
                // over the plain background once the (much larger) full image is fully decoded.
                requestBuilderTransform = { requestBuilder ->
                    requestBuilder.thumbnail(Glide.with(context).load(thumbnailUrl).transform(BoxBlurTransformation()))
                },
            )
        }
        // Bottom-up fade so the full backdrop stays visible top-to-bottom edge-to-edge, per the
        // Apple TV title-page brief, darkening only where the metadata/cast text sits.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.45f to Color.Transparent,
                    0.75f to TreeHouseBackground.copy(alpha = 0.85f),
                    1f to TreeHouseBackground,
                ),
            ),
        )
    }
}

@Composable
private fun DetailsInfoPanel(
    item: BaseItemDto,
    repository: JellyfinRepository,
    playTarget: BaseItemDto?,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.padding(start = 48.dp, end = 48.dp, bottom = 40.dp)) {
        DetailsMetadata(
            item = item,
            repository = repository,
            playTarget = playTarget,
            isFavorite = isFavorite,
            onPlay = onPlay,
            onToggleFavorite = onToggleFavorite,
            modifier = Modifier.align(Alignment.BottomStart).widthIn(max = 720.dp),
        )
        DetailsCast(item = item, modifier = Modifier.align(Alignment.BottomEnd).widthIn(max = 300.dp))
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun DetailsMetadata(
    item: BaseItemDto,
    repository: JellyfinRepository,
    playTarget: BaseItemDto?,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        // Apple TV doesn't apply a special font to the title text at all - it shows the title's
        // own official title-treatment artwork (a transparent PNG from the metadata provider).
        // Jellyfin exposes the same thing as ImageType.LOGO when a provider supplied one; fall
        // back to plain text for the (common) case where it didn't.
        if (item.imageTags?.containsKey(ImageType.LOGO) == true) {
            GlideImage(
                model = repository.buildImageUrl(item.id, imageType = ImageType.LOGO, maxWidth = 800),
                contentDescription = item.name,
                modifier = Modifier.heightIn(max = 96.dp).widthIn(max = 480.dp),
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart,
            )
        } else {
            Text(item.name.orEmpty(), style = MaterialTheme.typography.displaySmall, color = TreeHouseTextPrimary)
        }

        typeGenreLine(item).takeIf { it.isNotBlank() }?.let { line ->
            Spacer(Modifier.height(8.dp))
            Text(line, color = TreeHouseTextSecondary, style = MaterialTheme.typography.bodyMedium)
        }

        item.overview?.takeIf { it.isNotBlank() }?.let { overview ->
            Spacer(Modifier.height(14.dp))
            Text(
                overview,
                color = TreeHouseTextSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.widthIn(max = 640.dp),
            )
        }

        val metaLine = buildMetaLine(item)
        val badges = buildTechnicalBadges(item)
        if (metaLine.isNotBlank() || badges.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (metaLine.isNotBlank()) {
                    Text(metaLine, color = TreeHouseTextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
                badges.forEach { TechnicalBadge(it) }
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (playTarget != null) {
                val isResume = (playTarget.userData?.playbackPositionTicks ?: 0L) > 0L
                Button(
                    onClick = onPlay,
                    colors = ButtonDefaults.colors(containerColor = GlassContainerActive),
                    border = ButtonDefaults.border(border = Border(BorderStroke(1.dp, GlassBorder), shape = GlassShape)),
                    shape = ButtonDefaults.shape(shape = GlassShape),
                ) {
                    Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_hero_play), contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (isResume) R.string.details_resume else R.string.details_play))
                }
            }
            // A Box Set is a grouping of other titles, not a title of its own to favorite - its
            // Play button (starting the first movie in it) replaces this entirely rather than
            // sitting next to it.
            if (item.type != BaseItemKind.BOX_SET) {
                Button(
                    onClick = onToggleFavorite,
                    colors = ButtonDefaults.colors(containerColor = if (isFavorite) GlassContainerActive else GlassContainer),
                    border = ButtonDefaults.border(border = Border(BorderStroke(1.dp, GlassBorder), shape = GlassShape)),
                    shape = ButtonDefaults.shape(shape = GlassShape),
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
}

/** The cast list pinned to the hero's bottom-right, at the same baseline as the action buttons. */
@Composable
private fun DetailsCast(item: BaseItemDto, modifier: Modifier = Modifier) {
    val cast = item.people?.filter { it.type == PersonKind.ACTOR }?.take(MAX_CAST_SHOWN)?.mapNotNull { it.name }
    if (cast.isNullOrEmpty()) return
    Text(
        stringResource(R.string.details_starring_format, cast.joinToString(", ")),
        color = TreeHouseTextSecondary,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.End,
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier,
    )
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

private fun typeLabel(item: BaseItemDto): String = when (item.type) {
    BaseItemKind.SERIES -> "TV Show"
    BaseItemKind.MOVIE -> "Movie"
    BaseItemKind.BOX_SET -> "Collection"
    else -> ""
}

/** "TV Show · Thriller · Drama" - the item's kind plus its genres, shown right under the title. */
private fun typeGenreLine(item: BaseItemDto): String =
    listOfNotNull(
        typeLabel(item).takeIf { it.isNotBlank() },
        item.genres?.takeIf { it.isNotEmpty() }?.joinToString(" · "),
    ).joinToString(" · ")

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
            contentPadding = PaddingValues(start = SEASON_ROW_START, end = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(seasons, key = { it.id }) { season ->
                val selected = season.id == selectedSeason?.id
                Button(
                    onClick = { selectedSeason = season },
                    colors = ButtonDefaults.colors(
                        containerColor = if (selected) GlassContainerActive else GlassContainer,
                    ),
                    border = ButtonDefaults.border(border = Border(BorderStroke(1.dp, GlassBorder), shape = GlassShape)),
                    shape = ButtonDefaults.shape(shape = GlassShape),
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
        FocusableCard(onClick = onClick, modifier = Modifier.width(EPISODE_CARD_WIDTH_DP.dp)) { isFocused ->
            GlideImage(
                model = repository.buildImageUrl(episode.id, imageType = ImageType.PRIMARY, maxWidth = 440),
                contentDescription = episode.name,
                modifier = Modifier.fillMaxWidth().aspectRatio(EPISODE_ASPECT_RATIO).focusParallax(isFocused),
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

/**
 * A Box Set's own movies, in a plain poster row - unlike [SeasonsAndEpisodes] there's no picker
 * needed (a collection has no further grouping below it), and clicking a movie opens its own full
 * Details page rather than playing it directly, since a movie (unlike an episode) has its own rich
 * metadata/actions worth seeing first.
 */
@Composable
private fun BoxSetMovies(
    repository: JellyfinRepository,
    movies: List<BaseItemDto>,
    onOpenDetails: (BaseItemDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(top = 12.dp)) {
        LazyRow(
            contentPadding = PaddingValues(start = SEASON_ROW_START, end = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(movies, key = { it.id }) { movie ->
                BoxSetMovieCard(movie = movie, repository = repository, onClick = { onOpenDetails(movie) })
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun BoxSetMovieCard(movie: BaseItemDto, repository: JellyfinRepository, onClick: () -> Unit) {
    Column {
        FocusableCard(onClick = onClick, modifier = Modifier.width(BOX_SET_MOVIE_CARD_WIDTH_DP.dp)) { isFocused ->
            GlideImage(
                model = repository.buildImageUrl(movie.id, maxWidth = 280),
                contentDescription = movie.name,
                modifier = Modifier.fillMaxWidth().aspectRatio(POSTER_ASPECT_RATIO).focusParallax(isFocused),
                contentScale = ContentScale.Crop,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            movie.name.orEmpty(),
            color = TreeHouseTextPrimary,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(BOX_SET_MOVIE_CARD_WIDTH_DP.dp),
        )
    }
}
