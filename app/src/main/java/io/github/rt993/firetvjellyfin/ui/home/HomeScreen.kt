package io.github.rt993.firetvjellyfin.ui.home

import android.util.Log
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import io.github.rt993.firetvjellyfin.R
import io.github.rt993.firetvjellyfin.data.JellyfinRepository
import io.github.rt993.firetvjellyfin.ui.theme.AeroShardsBackground
import io.github.rt993.firetvjellyfin.ui.theme.FocusableCard
import io.github.rt993.firetvjellyfin.ui.theme.focusParallax
import io.github.rt993.firetvjellyfin.ui.theme.kenBurnsDrift
import io.github.rt993.firetvjellyfin.ui.theme.TreeHouseAccent
import io.github.rt993.firetvjellyfin.ui.theme.TreeHouseSurface
import io.github.rt993.firetvjellyfin.ui.theme.TreeHouseTextPrimary
import io.github.rt993.firetvjellyfin.ui.theme.TreeHouseTextSecondary
import io.github.rt993.firetvjellyfin.ui.theme.TreeHouseTheme
import io.github.rt993.firetvjellyfin.util.formatRuntimeTicks
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlin.math.abs
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType

private const val POSTER_ASPECT_RATIO = 2f / 3f
private const val SPOTLIGHT_ASPECT_RATIO = 16f / 9f
private val SIDEBAR_WIDTH_COLLAPSED = 64.dp
private val SIDEBAR_WIDTH_EXPANDED = 220.dp

// How the sidebar's items curve away from whichever one currently has D-pad focus - the item at
// distance 0 sits at full size/brightness and flush against the rail; farther items shrink, dim,
// and recede, like they're riding the rim of a wheel bowing back past the focused one. Distance is
// measured in list positions (Home, each library, then Account, in traversal order), not pixels -
// it curves the same way a focus move does, one step at a time.
private const val WHEEL_CURVE_STEPS = 3
private const val WHEEL_MIN_SCALE = 0.86f
private const val WHEEL_MIN_ALPHA = 0.55f
private val WHEEL_MAX_RECEDE = 14.dp

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

private const val HERO_PANEL_LIMIT = 6

// A stack of poster cards receding/fanning to one side behind whichever one has D-pad focus -
// D-pad left/right moves focus between cards the same way it moves through any other row, each
// one reporting its own distance from focus so the whole stack reflows together (same idea as the
// sidebar's wheel curve, just horizontal). There's no real 3D scene or GPU blur here - Compose's
// graphicsLayer has no translateZ/perspective-camera equivalent, so "depth" is faked with the same
// arithmetic a CSS perspective transform would use (apparent size shrinks as
// DEPTH_PERSPECTIVE_PX / (DEPTH_PERSPECTIVE_PX + DEPTH_STEP_PX * steps-back)), and "blur" is a
// plain dark overlay instead of a real blur, which needs RenderEffect (API 31+) well above this
// app's target hardware.
private const val DEPTH_VISIBLE_STEPS = 3
// Fraction of the card's own width, not a flat dp value - a flat offset either buries the stack
// behind the front card (too small relative to a wide card) or blows it apart (too large for a
// narrow one), since the card's own size already scales with the hero's height.
private const val DEPTH_SPREAD_FRACTION = 0.32f
private const val DEPTH_TILT_DEGREES = 14f
private const val DEPTH_PERSPECTIVE_PX = 1500f
private const val DEPTH_STEP_PX = 190f
private const val DEPTH_DIM_STEP = 0.22f
private const val DEPTH_CAMERA_DISTANCE = 10f

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
 * .BrowseSupportFragment]-based Home screen entirely. A depth-carousel of trending movies/shows
 * sits up top - D-pad focus brings a card to the front of the stack, see
 * [HeroDepthCarousel] - one poster row per library, then a Continue Watching row last.
 * [HomeSidebar] is a plain hand-built nav rail (not androidx.tv.material3's NavigationDrawer/
 * ModalNavigationDrawer - both turned out to have real, hard-to-verify quirks around measuring
 * collapsed-vs-expanded width that cost two rounds of bugs): no background of its own, icon-only
 * until D-pad focus actually lands on it, then it expands to show labels and curves its items
 * around whichever one is focused.
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
    onScanLibrary: () -> Unit,
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

    // Falls back to Continue Watching items if there's no trending data yet, so the gallery isn't
    // simply empty on a freshly-seeded server with little watch history.
    val heroItems = state.trending.ifEmpty { state.continueWatching }

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
        Box(Modifier.fillMaxSize()) {
            // A field of flat glass shards drifting slowly across the screen at a few depths,
            // instead of a flat color fill - see AeroShardsBackground.kt.
            AeroShardsBackground(modifier = Modifier.fillMaxSize())
            Row(Modifier.fillMaxSize()) {
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
                        // Bundled into one LazyColumn item (not a static sibling of the list) so
                        // Compose can discard/recycle it like any other row once scrolled well
                        // past, instead of redrawing it on every single scroll frame regardless of
                        // position - same reasoning the old fixed backdrop was built around.
                        HeroDepthCarousel(
                            items = heroItems,
                            repository = repository,
                            height = heroHeight,
                            firstFocusRequester = heroFocusRequester,
                            onPlay = onPlay,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(heroHeight)
                                .padding(top = HERO_TOP_SAFE_MARGIN, start = HERO_HORIZONTAL_MARGIN, end = HERO_HORIZONTAL_MARGIN),
                        )
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
                        onScanLibrary = { showAccountMenu = false; onScanLibrary() },
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
}

@Composable
private fun BoxScope.AccountMenu(
    onLogout: () -> Unit,
    onChangeServer: (() -> Unit)?,
    onScanLibrary: () -> Unit,
    onInfo: () -> Unit,
) {
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
        Button(onClick = onScanLibrary) { Text(stringResource(R.string.user_menu_scan_library)) }
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

    // Which item currently holds focus, by position in traversal order (Home, each library,
    // Account) - every item below reads its own distance from this shared index to curve its own
    // scale/opacity/offset, so the whole rail reflows together as focus moves through it.
    var focusedIndex by remember { mutableStateOf(0) }
    val accountIndex = libraries.size + 1

    Column(
        modifier = modifier
            .width(width)
            .focusRequester(focusRequester)
            .onFocusChanged { hasFocus = it.hasFocus }
            .focusGroup()
            .padding(vertical = 24.dp, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SidebarItem(
            icon = R.drawable.ic_nav_home,
            label = stringResource(R.string.nav_home),
            showLabel = hasFocus,
            distance = -focusedIndex,
            onFocused = { focusedIndex = 0 },
            onClick = {},
        )
        libraries.forEachIndexed { i, library ->
            val index = i + 1
            SidebarItem(
                icon = R.drawable.ic_nav_library,
                label = library.name.orEmpty(),
                showLabel = hasFocus,
                distance = index - focusedIndex,
                onFocused = { focusedIndex = index },
                onClick = { onLibrary(library) },
            )
        }
        Spacer(Modifier.weight(1f))
        SidebarItem(
            icon = R.drawable.ic_profile,
            label = stringResource(R.string.nav_account),
            showLabel = hasFocus,
            distance = accountIndex - focusedIndex,
            onFocused = { focusedIndex = accountIndex },
            onClick = onSettings,
        )
    }
}

@Composable
private fun SidebarItem(icon: Int, label: String, showLabel: Boolean, distance: Int, onFocused: () -> Unit, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val proximity = 1f - (abs(distance).coerceAtMost(WHEEL_CURVE_STEPS) / WHEEL_CURVE_STEPS.toFloat())
    val scale by animateFloatAsState(WHEEL_MIN_SCALE + (1f - WHEEL_MIN_SCALE) * proximity, label = "wheelScale")
    val curveAlpha by animateFloatAsState(WHEEL_MIN_ALPHA + (1f - WHEEL_MIN_ALPHA) * proximity, label = "wheelAlpha")
    val recede by animateDpAsState(WHEEL_MAX_RECEDE * (1f - proximity), label = "wheelRecede")

    // clickable() already makes this focusable on its own - a separate .focusable() here stacked
    // two focus targets on top of each other, so the first D-pad press only moved focus onto the
    // inner one and a second press was needed to actually register as a click.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .offset(x = -recede)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = curveAlpha
            }
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) TreeHouseAccent.copy(alpha = 0.3f) else Color.Transparent)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused()
            }
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

/**
 * A depth-carousel of trending movies/shows: every card is centered on the same spot and pushed
 * apart purely with [Modifier.offset] (see [HeroDepthCard]), so D-pad left/right moves focus
 * between them exactly like any other row - the one with focus sits at the front, full size and
 * bright; the next couple fan back and to the right behind it, shrinking, tilting and dimming;
 * anything before it fades out fast rather than piling up in front. A dedicated info column next
 * to the stack always shows whichever item currently has focus - there's no room for synopsis
 * text on a narrow poster card the way the old full-width accordion panel had.
 */
@Composable
private fun HeroDepthCarousel(
    items: List<BaseItemDto>,
    repository: JellyfinRepository,
    height: Dp,
    firstFocusRequester: FocusRequester,
    onPlay: (BaseItemDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    val panelItems = remember(items) { items.take(HERO_PANEL_LIMIT) }
    if (panelItems.isEmpty()) return

    var focusedIndex by remember { mutableStateOf(0) }
    val focusedItem = panelItems[focusedIndex.coerceIn(0, panelItems.lastIndex)]
    val cardWidth = height * POSTER_ASPECT_RATIO

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.fillMaxHeight().width(cardWidth * (1f + DEPTH_VISIBLE_STEPS * DEPTH_SPREAD_FRACTION + 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            panelItems.forEachIndexed { index, item ->
                HeroDepthCard(
                    item = item,
                    repository = repository,
                    cardWidth = cardWidth,
                    distance = index - focusedIndex,
                    onFocused = { focusedIndex = index },
                    onClick = { onPlay(item) },
                    modifier = Modifier
                        .height(height)
                        .then(if (index == 0) Modifier.focusRequester(firstFocusRequester) else Modifier),
                )
            }
        }
        Spacer(Modifier.width(32.dp))
        HeroDepthInfo(item = focusedItem, modifier = Modifier.weight(1f))
    }
}

/**
 * One card in the stack: a poster image, anchored at the same center spot as every other card in
 * the stack and pushed to its own position purely with [Modifier.offset] (a real layout-level
 * shift, not just a visual one) - so its placed bounds, not just its drawn pixels, land where the
 * stack visually puts it, which is what keeps D-pad left/right landing on the geometrically correct
 * neighbor despite every card starting from the same centered spot.
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun HeroDepthCard(
    item: BaseItemDto,
    repository: JellyfinRepository,
    cardWidth: Dp,
    distance: Int,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isFront = distance == 0
    val shown = abs(distance) <= DEPTH_VISIBLE_STEPS
    val stepsBack = distance.coerceAtLeast(0).toFloat()
    val density = LocalDensity.current.density

    val scale by animateFloatAsState(
        DEPTH_PERSPECTIVE_PX / (DEPTH_PERSPECTIVE_PX + DEPTH_STEP_PX * stepsBack),
        label = "depthScale",
    )
    val tiltDegrees by animateFloatAsState(DEPTH_TILT_DEGREES * distance.toFloat().coerceIn(0f, 1f), label = "depthTilt")
    val spreadOffset by animateDpAsState(cardWidth * DEPTH_SPREAD_FRACTION * distance, label = "depthSpread")
    val cardAlpha by animateFloatAsState(
        if (!shown) 0f else if (distance < 0) (1f + distance).coerceAtLeast(0f) else 1f,
        label = "depthAlpha",
    )
    val dimAlpha by animateFloatAsState((stepsBack * DEPTH_DIM_STEP).coerceIn(0f, 0.86f), label = "depthDim")

    Box(
        modifier = modifier
            .aspectRatio(POSTER_ASPECT_RATIO)
            .zIndex(2000f - distance * 20f)
            .offset(x = spreadOffset)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationY = tiltDegrees
                alpha = cardAlpha
                cameraDistance = DEPTH_CAMERA_DISTANCE * density
            }
            .clip(RoundedCornerShape(HERO_CORNER_RADIUS))
            .onFocusChanged { if (it.isFocused) onFocused() }
            .clickable(onClick = onClick, enabled = shown),
    ) {
        GlideImage(
            model = repository.buildImageUrl(item.id, imageType = ImageType.PRIMARY, maxWidth = 440),
            contentDescription = item.name,
            // The drift is only worth animating on the one card actually at the front right now -
            // every other card is already being scaled/dimmed/tilted back for the stack itself.
            modifier = if (isFront) Modifier.fillMaxSize().kenBurnsDrift() else Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        if (dimAlpha > 0f) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dimAlpha)))
        }
    }
}

/**
 * The stack's companion text column - title, rating/year/runtime/rating meta line, overview, and a
 * "press OK to play" hint for whichever card currently sits at the front. The card itself (not a
 * separate button) is what's actually focusable/clickable - this column is purely informational.
 */
@Composable
private fun HeroDepthInfo(item: BaseItemDto, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            item.name.orEmpty(),
            color = TreeHouseTextPrimary,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(buildMetaLine(item), color = TreeHouseTextSecondary, style = MaterialTheme.typography.bodySmall)
        item.overview?.takeIf { it.isNotBlank() }?.let { overview ->
            Spacer(Modifier.height(10.dp))
            Text(
                overview,
                color = TreeHouseTextSecondary,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.widthIn(max = 480.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_hero_play), contentDescription = null, tint = TreeHouseTextPrimary)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.playback_play), color = TreeHouseTextPrimary, style = MaterialTheme.typography.labelLarge)
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
    FocusableCard(modifier = modifier.width(140.dp), onClick = onClick) { isFocused ->
        GlideImage(
            model = repository.buildImageUrl(item.id, maxWidth = 280),
            contentDescription = item.name,
            modifier = Modifier.fillMaxWidth().aspectRatio(POSTER_ASPECT_RATIO).focusParallax(isFocused),
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
        FocusableCard(onClick = onClick, modifier = modifier) { isFocused ->
            Box {
                GlideImage(
                    model = repository.buildImageUrl(item.id, imageType = imageType, maxWidth = 560),
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxWidth().aspectRatio(SPOTLIGHT_ASPECT_RATIO).focusParallax(isFocused),
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
