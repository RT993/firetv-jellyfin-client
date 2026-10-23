package io.github.rt993.firetvjellyfin.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.sin

private const val RING_COUNT = 4
private const val BASE_RADIUS_FRACTION = 0.16f
private const val RADIUS_STEP_FRACTION = 0.09f
private const val BREATHE_EXPAND_FRACTION = 0.012f
private const val BREATHE_LOOP_MS = 3400
private const val RING_STAGGER = 0.62f
private const val STROKE_DP = 2.2f
private const val GLOW_LAYERS = 4

/**
 * A handful of concentric rings, breathing in and out of brightness (not in lockstep - each one
 * offset so the glow reads as drifting through the stack rather than pulsing all at once), colored
 * in a gradient from [color] at the center to [colorTwo] at the rim - the same idea as a WebGL
 * "magic rings" shader, built the same cheap way as this app's other glows: a handful of stroked
 * circles with decreasing alpha and [BlendMode.Plus] standing in for a real blurred halo, which
 * needs RenderEffect (API 31+) well above this app's target hardware.
 */
@Composable
fun MagicRingsBackground(
    modifier: Modifier = Modifier,
    color: Color = TreeHouseAccent,
    colorTwo: Color = Color(0xFF6366F1),
    centerYFraction: Float = 0.5f,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "magicRings")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(animation = tween(BREATHE_LOOP_MS, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "ringsPhase",
    )
    val density = LocalDensity.current.density

    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(TreeHouseBackground)
        val center = Offset(size.width / 2f, size.height * centerYFraction)
        val unit = minOf(size.width, size.height)
        val strokePx = STROKE_DP * density

        for (index in 0 until RING_COUNT) {
            val mix = if (RING_COUNT > 1) index / (RING_COUNT - 1).toFloat() else 0f
            val ringColor = lerp(color, colorTwo, mix)
            val breathe = (sin(time - index * RING_STAGGER) + 1f) / 2f
            val radius = unit * (BASE_RADIUS_FRACTION + index * RADIUS_STEP_FRACTION + breathe * BREATHE_EXPAND_FRACTION)
            val brightness = 0.25f + 0.55f * breathe

            // Soft halo: the same ring redrawn a few times, each wider and dimmer, additively
            // blended - the cheap stand-in for a real blurred glow this app uses everywhere else.
            for (layer in GLOW_LAYERS downTo 1) {
                val glowWidth = strokePx * (1f + layer * 2.4f)
                val glowAlpha = (brightness * 0.22f / layer).coerceIn(0f, 1f)
                drawCircle(
                    color = ringColor,
                    radius = radius,
                    center = center,
                    style = Stroke(width = glowWidth),
                    alpha = glowAlpha,
                    blendMode = BlendMode.Plus,
                )
            }
            // A crisp, bright core line on top of its own glow.
            drawCircle(
                color = ringColor,
                radius = radius,
                center = center,
                style = Stroke(width = strokePx),
                alpha = brightness.coerceIn(0f, 1f),
                blendMode = BlendMode.Plus,
            )
        }
    }
}
