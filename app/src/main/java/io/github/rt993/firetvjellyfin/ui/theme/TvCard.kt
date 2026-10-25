package io.github.rt993.firetvjellyfin.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Glow

private const val CARD_CORNER_DP = 12
private const val FOCUSED_SCALE = 1.14f
private const val FOCUSED_GLOW_DP = 24

// A fixed tilt, not cursor-tracked - there's no pointer on a D-pad remote, so instead of tilting
// toward wherever a cursor sits, a focused card just pops into this one consistent "turned toward
// you" angle, the same read a cursor-tracked tilt gives on a single card, without needing motion
// input this device doesn't have. Y-axis only, not X: tipping the top edge back (rotationX) made
// the card's elevation shadow - cast from its flat, untilted outline, not reprojected in 3D - stop
// lining up with the now-foreshortened top edge, leaving a sliver of square shadow corner visible
// above the rounded card. A pure Y-axis turn doesn't foreshorten the top edge at all, so it never
// exposed the mismatch.
private const val TILT_ROTATION_Y = 10f
private const val TILT_CAMERA_DISTANCE = 14f
private const val TILT_ANIMATION_MS = 220

private const val PARALLAX_SCALE = 1.06f
private const val PARALLAX_TRANSLATION_DP = -4f
private const val PARALLAX_ANIMATION_MS = 220

/**
 * A [Card] with the "Scale & Glow"/halo-border focus treatment shared by every poster/spotlight
 * tile across Home and Details - scale up, a colored glow, a crisp 2px border, and a slight 3D
 * tilt when focused, so a focused tile never blends into busy background art. The glow is tinted
 * with the app's accent color rather than plain white for a more distinctive, "branded" focus
 * state.
 *
 * [shape] defaults to the rounded-rect every poster/spotlight card uses - pass e.g. [androidx
 * .compose.foundation.shape.CircleShape] for a round tile (the profile picker) instead.
 *
 * [content] receives the card's own focus state, so a caller can drive a subtle inner-image
 * parallax (see HomeScreen's PosterCard/SpotlightCard) layered under this card-level tilt, instead
 * of every image inside every focusable tile reimplementing its own focus listener.
 */
@Composable
fun FocusableCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(CARD_CORNER_DP.dp),
    content: @Composable (isFocused: Boolean) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val density = LocalDensity.current.density
    val tiltAnimationSpec = tween<Float>(TILT_ANIMATION_MS)
    val rotationY by animateFloatAsState(
        targetValue = if (isFocused) TILT_ROTATION_Y else 0f,
        animationSpec = tiltAnimationSpec,
        label = "tiltRotationY",
    )

    Card(
        onClick = onClick,
        modifier = modifier.graphicsLayer {
            this.rotationY = rotationY
            cameraDistance = TILT_CAMERA_DISTANCE * density
        },
        shape = CardDefaults.shape(shape = shape, focusedShape = shape),
        scale = CardDefaults.scale(focusedScale = FOCUSED_SCALE),
        border = CardDefaults.border(
            focusedBorder = Border(border = BorderStroke(2.dp, Color.White), shape = shape),
        ),
        glow = CardDefaults.glow(focusedGlow = Glow(elevationColor = TreeHouseAccent, elevation = FOCUSED_GLOW_DP.dp)),
        interactionSource = interactionSource,
    ) {
        content(isFocused)
    }
}

/**
 * A subtle focus-driven scale/lift for content *inside* a [FocusableCard], layered under the
 * card's own tilt/scale/glow - reads as the poster art itself drifting toward the viewer on
 * focus, rather than just the card frame around it growing.
 */
@Composable
fun Modifier.focusParallax(isFocused: Boolean): Modifier {
    val animationSpec = tween<Float>(PARALLAX_ANIMATION_MS)
    val scale by animateFloatAsState(
        targetValue = if (isFocused) PARALLAX_SCALE else 1f,
        animationSpec = animationSpec,
        label = "parallaxScale",
    )
    val translationDp by animateFloatAsState(
        targetValue = if (isFocused) PARALLAX_TRANSLATION_DP else 0f,
        animationSpec = animationSpec,
        label = "parallaxTranslation",
    )
    val density = LocalDensity.current.density
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
        translationY = translationDp * density
    }
}
