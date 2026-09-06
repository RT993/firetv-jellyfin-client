package io.github.rt993.firetvjellyfin.ui.home

import android.util.Log
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import io.github.rt993.firetvjellyfin.R
import io.github.rt993.firetvjellyfin.data.JellyfinRepository
import io.github.rt993.firetvjellyfin.ui.theme.FocusableCard
import io.github.rt993.firetvjellyfin.ui.theme.TreeHouseAccent
import io.github.rt993.firetvjellyfin.ui.theme.TreeHouseBackground
import io.github.rt993.firetvjellyfin.ui.theme.TreeHouseSurface
import io.github.rt993.firetvjellyfin.ui.theme.TreeHouseTextPrimary
import io.github.rt993.firetvjellyfin.ui.theme.TreeHouseTextSecondary
import io.github.rt993.firetvjellyfin.ui.theme.TreeHouseTheme
import io.github.rt993.firetvjellyfin.util.formatRuntimeTicks
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType

private const val POSTER_ASPECT_RATIO = 2f / 3f
private const val SPOTLIGHT_ASPECT_RATIO = 16f / 9f
private val SIDEBAR_WIDTH_COLLAPSED = 64.dp
private val SIDEBAR_WIDTH_EXPANDED = 220.dp

// A safe-zone margin so the backdrop's top edge doesn't land flush against the physical top of
// the screen - many TVs overscan (crop) a few percent off every edge, which was cutting off the
// top of the hero image entirely.
private val HERO_TOP_SAFE_MARGIN = 32.dp

// Boxes the hero backdrop in from the screen edges with rounded corners, rather than having it
// bleed full-bleed into the sidebar/edge - reads as a distinct "card" rather than a raw background.
private val HERO_HORIZONTAL_MARGIN = 48.dp
private val HERO_CORNER_RADIUS = 20.dp

// Gap between the bottom of the boxed hero and the first row of content below it.
private val HERO_BOTTOM_GAP = 30.dp

private const val TAG = "HomeScreen"

private data class HomeUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val continueWatching: List<BaseItemDto> = emptyList(),
    val libraries: List<BaseItemDto> = emptyList(),
    val libraryItems: Map<UUID, List<BaseItemDto>> = emptyMap(),
    val trending: List<BaseItemDto> = emptyList(),
)

/**
 * Ground-up rewrite in Jetpack Compose for TV, replacing the Leanback [androidx.leanback.app
 * .BrowseSupportFragment]-based Home screen entirely. A Dynamic Billboard (cinematic backdrop)
 * sits behind a hero carousel of trending movies/shows - pageable with D-pad left/right on its
 * Play button, see [HeroInfo] - one poster row per library, then a Continue Watching row last. The
 * backdrop also crossfades to whichever card currently has focus further down. [HomeSidebar] is a
 * plain hand-built nav rail (not androidx.tv.material3's NavigationDrawer/ModalNavigationDrawer -
 * both turned out to have real, hard-to-verify quirks around measuring collapsed-vs-expanded width
 * that cost two rounds of bugs): no background of its own, icon-only until D-pad focus actually
 * lands on it, then it expands to show labels.
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun HomeScreen(
    repository: JellyfinRepository,
    userId: UUID,
    onOpenDetails: (BaseItemDto) -> Unit,
    onPlay: (BaseItemDto) -> Unit,
    onOpenLibrary: (BaseItemDto) -> Unit,
    onShowAccountInfo: () -> Unit,
    onLogout: () -> Unit,
    onChangeServer: () -> Unit,
    hasMultipleServers: Boolean,
) {
    var state by remember { mutableStateOf(HomeUiState()) }
    LaunchedEffect(userId) {
        // These three were previously awaited one after another - each a full network round trip -
        // even though none depends on another's result. Running them concurrently cuts Home's load
        // time from roughly the sum of all requests to roughly the slowest single one, a real,
        // noticeable difference on a Fire Stick talking to a home server over Wi-Fi.
        val librariesDeferred = async { runCatching { repository.getUserViews(userId) }.onFailure { Log.e(TAG, "getUserViews failed", it) } }
        val trendingDeferred = async { runCatching { repository.getRecentlyAdded(userId) }.onFailure { Log.e(TAG, "getRecentlyAdded failed", it) } }
        val continueWatchingDeferred = async { runCatching { repository.getResumeItems(userId) }.onFailure { Log.e(TAG, "getResumeItems failed", it) } }

        val librariesResult = librariesDeferred.await()
        val libraries = librariesResult.getOrDefault(emptyList())
        val trending = trendingDeferred.await().getOrDefault(emptyList())
        val continueWatching = continueWatchingDeferred.await().getOrDefault(emptyList())

        // Same idea for the per-library item fetches - one network call per library, all
        // independent of each other, so they run concurrently instead of queued back to back.
        val libraryItems = libraries
            .map { library ->
                async {
                    library.id to runCatching { repository.getItems(userId, library.id) }
                        .onFailure { Log.e(TAG, "getItems failed for library ${library.name}", it) }
                        .getOrDefault(emptyList())
                }
            }
            .awaitAll()
            .toMap()

        state = HomeUiState(
            isLoading = false,
            error = librariesResult.exceptionOrNull()?.let { "${it.javaClass.simpleName}: ${it.message}" },
            continueWatching = continueWatching,
            libraries = libraries,
            libraryItems = libraryItems,
            trending = trending,
        )
    }

    // The hero only ever reflects its own paging state - it used to also switch to whatever poster
    // card currently had D-pad focus, but that made it feel like it wasn't really a fixed "top
    // shelf" banner at all, just a preview that changed as you browsed.
    var heroIndex by remember { mutableStateOf(0) }
    val heroItem = state.trending.getOrNull(heroIndex) ?: state.continueWatching.firstOrNull()
    fun pageHero(delta: Int) {
        val trending = state.trending
        if (trending.isEmpty()) return
        heroIndex = (heroIndex + delta + trending.size) % trending.size
    }

    val heroFocusRequester = remember { FocusRequester() }
    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) runCatching { heroFocusRequester.requestFocus() }
    }

    var showAccountMenu by remember { mutableStateOf(false) }

    // Computed once from screen configuration rather than via BoxWithConstraints, which disables
    // Compose's recomposition-skipping for its entire content - on this app's low-end Fire Stick
    // target that meant the whole LazyColumn (every row, every card) re-executed on every
    // recomposition of this screen (e.g. every D-pad hero page), causing real, visible input lag.
    val heroHeight = LocalConfiguration.current.screenHeightDp.dp * 0.62f

    // A FocusRequester on the sidebar's own focus group, not one specific item inside it: Compose
    // remembers whichever child was last focused within a focus group, so requesting focus on the
    // group itself re-enters at that item (Home the first time, whatever the user last landed on
    // after that) instead of always snapping back to a fixed spot.
    val sidebarFocusRequester = remember { FocusRequester() }

    TreeHouseTheme {
        Row(Modifier.fillMaxSize().background(TreeHouseBackground)) {
            HomeSidebar(
                libraries = state.libraries,
                onLibrary = onOpenLibrary,
                onSettings = { showAccountMenu = true },
                modifier = Modifier.fillMaxHeight(),
                focusRequester = sidebarFocusRequester,
            )

            Box(Modifier.weight(1f).fillMaxHeight()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(HERO_BOTTOM_GAP),
                ) {
                    item {
                        // The backdrop now scrolls away as a normal part of the list instead of
                        // sitting fixed behind it - it used to be drawn as a static sibling of this
                        // LazyColumn, which meant it never actually left the screen: scrolling down
                        // to Shows just moved the row content over it, and the backdrop kept
                        // bleeding through every gap between cards. Bundling it into one item here
                        // also means Compose can discard/recycle it like any other row once it's
                        // scrolled well past, instead of redrawing that big image/gradient/rounded
                        // clip on every single scroll frame regardless of position.
                        Box(Modifier.fillMaxWidth().height(heroHeight + HERO_TOP_SAFE_MARGIN).clipToBounds()) {
                            HeroBackdrop(
                                item = heroItem,
                                repository = repository,
                                modifier = Modifier
                                    .padding(top = HERO_TOP_SAFE_MARGIN, start = HERO_HORIZONTAL_MARGIN, end = HERO_HORIZONTAL_MARGIN)
                                    .fillMaxSize(),
                            )
                            Column(Modifier.fillMaxSize()) {
                                Spacer(Modifier.height(280.dp))
                                HeroInfo(
                                    item = heroItem,
                                    pageCount = state.trending.size,
                                    currentIndex = heroIndex,
                                    focusRequester = heroFocusRequester,
                                    onPageLeft = { pageHero(-1) },
                                    onPageRight = { pageHero(1) },
                                    onPlay = { heroItem?.let(onPlay) },
                                )
                            }
                        }
                    }
                    items(state.libraries, key = { it.id }) { library ->
                        val libraryItems = state.libraryItems[library.id].orEmpty()
                        if (libraryItems.isNotEmpty()) {
                            MediaRow(title = library.name.orEmpty()) {
                                itemsIndexed(libraryItems, key = { _, item -> item.id }) { index, mediaItem ->
                                    PosterCard(
                                        item = mediaItem,
                                        repository = repository,
                                        onClick = { onOpenDetails(mediaItem) },
                                        modifier = if (index == 0) leftEdgeModifier(sidebarFocusRequester) else Modifier,
                                    )
                                }
                            }
                        }
                    }
                    if (state.continueWatching.isNotEmpty()) {
                        item {
                            MediaRow(title = stringResource(R.string.home_continue_watching)) {
                                itemsIndexed(state.continueWatching, key = { _, item -> item.id }) { index, mediaItem ->
                                    SpotlightCard(
                                        item = mediaItem,
                                        repository = repository,
                                        onClick = { onOpenDetails(mediaItem) },
                                        modifier = if (index == 0) leftEdgeModifier(sidebarFocusRequester) else Modifier,
                                    )
                                }
                            }
                        }
                    }
                }

                if (showAccountMenu) {
                    AccountMenu(
                        onLogout = { showAccountMenu = false; onLogout() },
                        onChangeServer = if (hasMultipleServers) {
                            { showAccountMenu = false; onChangeServer() }
                        } else {
                            null
                        },
                        onInfo = { showAccountMenu = false; onShowAccountInfo() },
                    )
                }

                state.error?.let { error ->
                    Text(
                        "${stringResource(R.string.home_error)}\n$error",
                        color = TreeHouseTextSecondary,
                        modifier = Modifier.align(Alignment.Center).padding(48.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.AccountMenu(onLogout: () -> Unit, onChangeServer: (() -> Unit)?, onInfo: () -> Unit) {
    Column(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(24.dp)
            .background(TreeHouseSurface, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = onLogout) { Text(stringResource(R.string.user_menu_logout)) }
        if (onChangeServer != null) {
            Button(onClick = onChangeServer) { Text(stringResource(R.string.user_menu_change_server)) }
        }
        Button(onClick = onInfo) { Text(stringResource(R.string.user_menu_info)) }
    }
}

/**
 * A plain, hand-built nav rail - icon-only at rest, expanding to show labels once D-pad focus
 * actually lands somewhere inside it ([focusGroup] + [onFocusChanged] on the container reports
 * focus for the whole group, not just one item), rather than relying on a Leanback/tv-material
 * component to manage that transition itself. No background of its own - it floats directly over
 * whatever content is behind it.
 */
@Composable
private fun HomeSidebar(
    libraries: List<BaseItemDto>,
    onLibrary: (BaseItemDto) -> Unit,
    onSettings: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    var hasFocus by remember { mutableStateOf(false) }
    val width by animateDpAsState(if (hasFocus) SIDEBAR_WIDTH_EXPANDED else SIDEBAR_WIDTH_COLLAPSED, label = "sidebarWidth")

    Column(
        modifier = modifier
            .width(width)
            .focusRequester(focusRequester)
            .onFocusChanged { hasFocus = it.hasFocus }
            .focusGroup()
            .padding(vertical = 24.dp, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SidebarItem(R.drawable.ic_nav_home, stringResource(R.string.nav_home), hasFocus, onClick = {})
        libraries.forEach { library ->
            SidebarItem(R.drawable.ic_nav_library, library.name.orEmpty(), hasFocus, onClick = { onLibrary(library) })
        }
        Spacer(Modifier.weight(1f))
        SidebarItem(R.drawable.ic_profile, stringResource(R.string.nav_account), hasFocus, onClick = onSettings)
    }
}

@Composable
private fun SidebarItem(icon: Int, label: String, showLabel: Boolean, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    // clickable() already makes this focusable on its own - a separate .focusable() here stacked
    // two focus targets on top of each other, so the first D-pad press only moved focus onto the
    // inner one and a second press was needed to actually register as a click.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) TreeHouseAccent.copy(alpha = 0.3f) else Color.Transparent)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(id = icon),
            contentDescription = if (showLabel) null else label,
            tint = Color.Unspecified,
            // Explicit, uniform size regardless of each drawable's own declared intrinsic size -
            // ic_profile.xml in particular is authored bigger (for the profile picker's tiles) and
            // would otherwise render larger than the other sidebar icons.
            modifier = Modifier.size(24.dp),
        )
        if (showLabel) {
            Spacer(Modifier.width(12.dp))
            Text(label, color = TreeHouseTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun HeroBackdrop(item: BaseItemDto?, repository: JellyfinRepository, modifier: Modifier = Modifier) {
    // Height is fixed by the caller (see heroHeight in HomeScreen) - no fillMaxHeight() here, since
    // stacking it on top of that already-exact height would just re-apply the 0.62f fraction a
    // second time and shrink the box further.
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(HERO_CORNER_RADIUS))) {
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

/**
 * The "Top Shelf" hero: title/meta/overview for whichever [item] is current, plus a Play button
 * that doubles as the paging control - D-pad left/right on it cycles [onPageLeft]/[onPageRight]
 * through the trending movies/shows instead of moving focus elsewhere, mirroring how a real remote
 * scrolls a featured-content carousel. Down still moves focus into the rows below normally.
 */
@Composable
private fun HeroInfo(
    item: BaseItemDto?,
    pageCount: Int,
    currentIndex: Int,
    focusRequester: FocusRequester,
    onPageLeft: () -> Unit,
    onPageRight: () -> Unit,
    onPlay: () -> Unit,
) {
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = onPlay,
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (event.key) {
                            Key.DirectionLeft -> { onPageLeft(); true }
                            Key.DirectionRight -> { onPageRight(); true }
                            else -> false
                        }
                    },
            ) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_hero_play), contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.playback_play))
            }
            if (pageCount > 1) {
                Spacer(Modifier.width(20.dp))
                HeroDots(count = pageCount, currentIndex = currentIndex)
            }
        }
    }
}

@Composable
private fun HeroDots(count: Int, currentIndex: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            Box(
                Modifier
                    .size(8.dp)
                    .background(
                        color = if (i == currentIndex) TreeHouseAccent else TreeHouseTextSecondary.copy(alpha = 0.4f),
                        shape = CircleShape,
                    ),
            )
        }
    }
}

/**
 * Overrides D-pad Left from a row's first card to always re-enter the sidebar, instead of relying
 * on Compose's default geometry-based focus search - which picked the hero's Play button instead
 * of the sidebar from the very first row (Movies), since that button was still nearby in the
 * layout tree and won out on the distance heuristic, while it happened to resolve correctly one
 * row further down (Shows) purely by coincidence of vertical alignment.
 */
private fun leftEdgeModifier(sidebarFocusRequester: FocusRequester): Modifier =
    Modifier.focusProperties { left = sidebarFocusRequester }

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
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FocusableCard(modifier = modifier.width(140.dp), onClick = onClick) {
        GlideImage(
            model = repository.buildImageUrl(item.id, maxWidth = 280),
            contentDescription = item.name,
            modifier = Modifier.fillMaxWidth().aspectRatio(POSTER_ASPECT_RATIO),
            contentScale = ContentScale.Crop,
        )
    }
}

/**
 * 16:9 landscape thumbnail with a watch-progress bar - the Continue Watching row's card shape.
 * Backdrops/episode stills rarely carry a readable title baked into the art the way a movie
 * poster does, so - unlike [PosterCard] - this one needs its own title/subtitle underneath, or a
 * resumed episode is just an unlabeled still frame. For an episode, the subtitle leads with its
 * season/episode number since the episode's own title is often an unfamiliar one-liner with no
 * obvious connection to the show.
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun SpotlightCard(
    item: BaseItemDto,
    repository: JellyfinRepository,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isEpisode = item.type == BaseItemKind.EPISODE
    val imageType = if (isEpisode) ImageType.PRIMARY else ImageType.BACKDROP
    val positionTicks = item.userData?.playbackPositionTicks ?: 0L
    val totalTicks = item.runTimeTicks ?: 0L
    val percent = if (totalTicks > 0) (positionTicks.toFloat() / totalTicks).coerceIn(0f, 1f) else 0f

    val title = if (isEpisode) item.seriesName ?: item.name else item.name
    val season = item.parentIndexNumber
    val episodeNumber = item.indexNumber
    val subtitle = when {
        !isEpisode -> item.productionYear?.toString().orEmpty()
        season != null && episodeNumber != null -> stringResource(R.string.continue_watching_episode_format, season, episodeNumber)
        else -> item.name.orEmpty()
    }

    Column(modifier = Modifier.width(280.dp)) {
        // The focus-relevant modifier (e.g. leftEdgeModifier's override) has to land on
        // FocusableCard itself, not this wrapping Column - focusProperties only reaches a focus
        // target further down the same modifier chain, and the Column and the Card's actual focus
        // node are separate composables, not links in one chain.
        FocusableCard(onClick = onClick, modifier = modifier) {
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
        Spacer(Modifier.height(6.dp))
        Text(title.orEmpty(), color = TreeHouseTextPrimary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, color = TreeHouseTextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
