package io.github.rt993.firetvjellyfin.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity

private const val KEN_BURNS_LOOP_MS = 18_000
private const val KEN_BURNS_SCALE_TO = 1.12f
private const val KEN_BURNS_PAN_DP = 16f

/**
 * A slow, continuous scale+pan drift for one large background image - a hero panel, a Details
 * backdrop - the same cheap, GPU-composited "breathing" trick other TV apps use for a still image,
 * not a real camera move. Deliberately not meant for small poster/card art: at that size the drift
 * would read as jitter, and it would be one more animated layer on every tiny tile in a grid.
 */
@Composable
fun Modifier.kenBurnsDrift(): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "kenBurnsDrift")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = KEN_BURNS_LOOP_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "kenBurnsProgress",
    )
    val density = LocalDensity.current.density
    return this.graphicsLayer {
        val scale = 1f + (KEN_BURNS_SCALE_TO - 1f) * progress
        scaleX = scale
        scaleY = scale
        translationX = -KEN_BURNS_PAN_DP * density * progress
        translationY = -KEN_BURNS_PAN_DP * density * 0.5f * progress
    }
}
