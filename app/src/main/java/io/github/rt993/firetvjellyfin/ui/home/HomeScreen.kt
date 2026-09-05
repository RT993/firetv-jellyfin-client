package io.github.rt993.firetvjellyfin.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.NavigationDrawerScope
import androidx.tv.material3.Text
import androidx.tv.material3.rememberDrawerState
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import io.github.rt993.firetvjellyfin.R
import io.github.rt993.firetvjellyfin.data.JellyfinRepository
import io.github.rt993.firetvjellyfin.ui.theme.TreeHouseBackground
import io.github.rt993.firetvjellyfin.ui.theme.TreeHouseSurface
import io.github.rt993.firetvjellyfin.ui.theme.TreeHouseTextPrimary
import io.github.rt993.firetvjellyfin.ui.theme.TreeHouseTextSecondary
import io.github.rt993.firetvjellyfin.ui.theme.TreeHouseTheme
import io.github.rt993.firetvjellyfin.util.formatRuntimeTicks
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType

private const val POSTER_ASPECT_RATIO = 2f / 3f
private const val SPOTLIGHT_ASPECT_RATIO = 16f / 9f
private const val CARD_CORNER_DP = 12
private const val FOCUSED_SCALE = 1.12f
private const val FOCUSED_GLOW_DP = 16

private data class HomeUiState(
    val isLoading: Boolean = true,
    val continueWatching: List<BaseItemDto> = emptyList(),
    val libraries: List<BaseItemDto> = emptyList(),
    val libraryItems: Map<UUID, List<BaseItemDto>> = emptyMap(),
    val trending: List<BaseItemDto> = emptyList(),
)

/**
 * Ground-up rewrite in Jetpack Compose for TV, replacing the Leanback [androidx.leanback.app
 * .BrowseSupportFragment]-based Home screen entirely. A Dynamic Billboard (cinematic backdrop
 * crossfading to whichever card currently has focus) sits behind a Continue Watching row and one
 * poster row per library, with a collapsible left [NavigationDrawer] for navigation - see the
 * design notes this was built from for the full reference.
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun HomeScreen(
    repository: JellyfinRepository,
    userId: UUID,
    onOpenDetails: (BaseItemDto) -> Unit,
    onPlay: (BaseItemDto) -> Unit,
    onOpenLibrary: (BaseItemDto) -> Unit,
    onSearch: () -> Unit,
    onShowAccountInfo: () -> Unit,
    onLogout: () -> Unit,
) {
    var state by remember { mutableStateOf(HomeUiState()) }
    LaunchedEffect(userId) {
        val libraries = runCatching { repository.getUserViews(userId) }.getOrDefault(emptyList())
        val trending = runCatching { repository.getRecentlyAdded(userId) }.getOrDefault(emptyList())
        val continueWatching = runCatching { repository.getResumeItems(userId) }.getOrDefault(emptyList())
        val libraryItems = libraries.associate { library ->
            library.id to runCatching { repository.getItems(userId, library.id) }.getOrDefault(emptyList())
        }
        state = HomeUiState(
            isLoading = false,
            continueWatching = continueWatching,
            libraries = libraries,
            libraryItems = libraryItems,
            trending = trending,
        )
    }

    var spotlight by remember { mutableStateOf<BaseItemDto?>(null) }
    LaunchedEffect(state.continueWatching, state.trending) {
        if (spotlight == null) spotlight = state.continueWatching.firstOrNull() ?: state.trending.firstOrNull()
    }

    val firstCardFocusRequester = remember { FocusRequester() }
    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) runCatching { firstCardFocusRequester.requestFocus() }
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var showAccountMenu by remember { mutableStateOf(false) }

    TreeHouseTheme {
        NavigationDrawer(
            drawerContent = { drawerValue ->
                HomeSidebar(
                    expanded = drawerValue == DrawerValue.Open,
                    libraries = state.libraries,
                    onSearch = onSearch,
                    onLibrary = onOpenLibrary,
                    onSettings = { showAccountMenu = true },
                )
            },
            drawerState = drawerState,
        ) {
            Box(Modifier.fillMaxSize().background(TreeHouseBackground)) {
                HeroBackdrop(item = spotlight, repository = repository)

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(32.dp),
                ) {
                    item {
                        Column {
                            Spacer(Modifier.height(280.dp))
                            SpotlightInfo(item = spotlight, onPlay = onPlay)
                        }
                    }
                    if (state.continueWatching.isNotEmpty()) {
                        item {
                            MediaRow(title = stringResource(R.string.home_continue_watching)) {
                                itemsIndexed(state.continueWatching) { index, mediaItem ->
                                    SpotlightCard(
                                        item = mediaItem,
                                        repository = repository,
                                        modifier = if (index == 0) Modifier.focusRequester(firstCardFocusRequester) else Modifier,
                                        onFocused = { spotlight = mediaItem },
                                        onClick = { onOpenDetails(mediaItem) },
                                    )
                                }
                            }
                        }
                    }
                    items(state.libraries, key = { it.id }) { library ->
                        val libraryItems = state.libraryItems[library.id].orEmpty()
                        if (libraryItems.isNotEmpty()) {
                            MediaRow(title = library.name.orEmpty()) {
                                items(libraryItems, key = { it.id }) { mediaItem ->
                                    PosterCard(
                                        item = mediaItem,
                                        repository = repository,
                                        onFocused = { spotlight = mediaItem },
                                        onClick = { onOpenDetails(mediaItem) },
                                    )
                                }
                            }
                        }
                    }
                }

                if (showAccountMenu) {
                    AccountMenu(
                        onInfo = { onShowAccountInfo(); showAccountMenu = false },
                        onLogout = onLogout,
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.AccountMenu(onInfo: () -> Unit, onLogout: () -> Unit) {
    Column(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(24.dp)
            .background(TreeHouseSurface, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = onInfo) { Text(stringResource(R.string.user_menu_info)) }
        Button(onClick = onLogout) { Text(stringResource(R.string.user_menu_logout)) }
    }
}

@Composable
private fun NavigationDrawerScope.HomeSidebar(
    expanded: Boolean,
    libraries: List<BaseItemDto>,
    onSearch: () -> Unit,
    onLibrary: (BaseItemDto) -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxHeight().padding(vertical = 24.dp, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DrawerItem(R.drawable.ic_nav_search, stringResource(R.string.nav_search), expanded, selected = false, onClick = onSearch)
        DrawerItem(R.drawable.ic_nav_home, stringResource(R.string.nav_home), expanded, selected = true, onClick = {})
        libraries.forEach { library ->
            DrawerItem(R.drawable.ic_nav_library, library.name.orEmpty(), expanded, selected = false, onClick = { onLibrary(library) })
        }
        Spacer(Modifier.weight(1f))
        DrawerItem(R.drawable.ic_nav_settings, stringResource(R.string.nav_settings), expanded, selected = false, onClick = onSettings)
    }
}

@Composable
private fun NavigationDrawerScope.DrawerItem(
    icon: Int,
    label: String,
    expanded: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        selected = selected,
        onClick = onClick,
        leadingContent = { Icon(imageVector = ImageVector.vectorResource(id = icon), contentDescription = if (expanded) null else label) },
    ) {
        Text(label)
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun HeroBackdrop(item: BaseItemDto?, repository: JellyfinRepository) {
    Box(Modifier.fillMaxWidth().fillMaxHeight(0.62f)) {
        if (item != null) {
            GlideImage(
                model = repository.buildImageUrl(item.id, imageType = ImageType.BACKDROP, maxWidth = 1280),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.55f to TreeHouseBackground.copy(alpha = 0.55f),
                    1f to TreeHouseBackground,
                ),
            ),
        )
    }
}

@Composable
private fun SpotlightInfo(item: BaseItemDto?, onPlay: (BaseItemDto) -> Unit) {
    if (item == null) return
    Column(Modifier.padding(start = 48.dp, end = 48.dp)) {
        Text(item.name.orEmpty(), style = MaterialTheme.typography.headlineLarge, color = TreeHouseTextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(buildMetaLine(item), color = TreeHouseTextSecondary, style = MaterialTheme.typography.bodyMedium)
        item.overview?.takeIf { it.isNotBlank() }?.let { overview ->
            Spacer(Modifier.height(8.dp))
            Text(
                overview,
                color = TreeHouseTextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.widthIn(max = 560.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = { onPlay(item) }) {
            Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_hero_play), contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.playback_play))
        }
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

@Composable
private fun MediaRow(title: String, content: LazyListScope.() -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = TreeHouseTextPrimary,
            modifier = Modifier.padding(start = 48.dp, bottom = 12.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            content = content,
        )
    }
}

/** 2:3 vertical poster - the Tile Grid System's default shape for movies/TV shows. */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun PosterCard(
    item: BaseItemDto,
    repository: JellyfinRepository,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FocusableCard(modifier = modifier.width(140.dp), onFocused = onFocused, onClick = onClick) {
        GlideImage(
            model = repository.buildImageUrl(item.id, maxWidth = 280),
            contentDescription = item.name,
            modifier = Modifier.fillMaxWidth().aspectRatio(POSTER_ASPECT_RATIO),
            contentScale = ContentScale.Crop,
        )
    }
}

/** 16:9 landscape thumbnail with a watch-progress bar - the Continue Watching row's card shape. */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun SpotlightCard(
    item: BaseItemDto,
    repository: JellyfinRepository,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isEpisode = item.type == BaseItemKind.EPISODE
    val imageType = if (isEpisode) ImageType.PRIMARY else ImageType.BACKDROP
    val positionTicks = item.userData?.playbackPositionTicks ?: 0L
    val totalTicks = item.runTimeTicks ?: 0L
    val percent = if (totalTicks > 0) (positionTicks.toFloat() / totalTicks).coerceIn(0f, 1f) else 0f

    FocusableCard(modifier = modifier.width(280.dp), onFocused = onFocused, onClick = onClick) {
        Box {
            GlideImage(
                model = repository.buildImageUrl(item.id, imageType = imageType, maxWidth = 560),
                contentDescription = item.name,
                modifier = Modifier.fillMaxWidth().aspectRatio(SPOTLIGHT_ASPECT_RATIO),
                contentScale = ContentScale.Crop,
            )
            if (percent > 0f) {
                Box(Modifier.fillMaxWidth().height(4.dp).background(Color.Black.copy(alpha = 0.4f)))
                Box(
                    Modifier
                        .fillMaxWidth(percent)
                        .height(4.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

@Composable
private fun FocusableCard(
    modifier: Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(CARD_CORNER_DP.dp)
    Card(
        onClick = onClick,
        modifier = modifier.onFocusChanged { if (it.isFocused) onFocused() },
        shape = CardDefaults.shape(shape = shape, focusedShape = shape),
        scale = CardDefaults.scale(focusedScale = FOCUSED_SCALE),
        border = CardDefaults.border(
            focusedBorder = Border(border = BorderStroke(2.dp, Color.White), shape = shape),
        ),
        glow = CardDefaults.glow(focusedGlow = Glow(elevationColor = Color.White, elevation = FOCUSED_GLOW_DP.dp)),
    ) {
        content()
    }
}
