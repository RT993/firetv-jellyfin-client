package io.github.rt993.firetvjellyfin.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity

private const val RING_COUNT = 3
private const val BASE_RADIUS_FRACTION = 0.6f
private const val RADIUS_STEP_FRACTION = 0.1f
private const val EXPANSION_FRACTION = 0.1f
private const val CYCLE_SECONDS = 3.45f
private const val STAGGER_SECONDS = 0.5f
private const val STROKE_DP = 2.2f
private const val GLOW_LAYERS = 4

private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** A wave of brightness that rises fast, holds, then fades slowly - one ring's full life cycle. */
private fun pulseLife(t: Float): Float = minOf(smoothstep(0f, 0.7f, t), 1f - smoothstep(0.5f, 3.25f, t))

/**
 * A handful of big rings, lighting up one after another in a wave (not a literal WebGL port - see
 * React Bits' MagicRings, a raw GLSL shader with no DOM/JS equivalent here): each ring's own
 * brightness pulses on its own staggered delay and drifts outward while it glows, then fades, on
 * a loop. Colored in a gradient from [color] at the center to [colorTwo] at the rim. Built the same
 * cheap way as every other glow in this app - a handful of stroked circles with decreasing alpha
 * and [BlendMode.Plus] standing in for a real blurred halo (no shaders, no GPU blur, which needs
 * RenderEffect - API 31+ - well above this app's target hardware). The driving clock runs
 * unbounded via [withFrameNanos] rather than a repeating tween, since each ring's cycle needs its
 * own phase offset against one continuously advancing time value, not a value that jumps back to
 * zero on every loop.
 */
@Composable
fun MagicRingsBackground(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFA855F7),
    colorTwo: Color = Color(0xFF6366F1),
    centerYFraction: Float = 0.5f,
) {
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var lastNanos = -1L
        while (true) {
            withFrameNanos { nanos ->
                if (lastNanos >= 0) time += (nanos - lastNanos) / 1_000_000_000f
                lastNanos = nanos
            }
        }
    }
    val density = LocalDensity.current.density

    Canvas(modifier = modifier.fillMaxSize()) {
        // No flat fill here - the screen behind this already has its own windowBackground (the
        // same dark navy), so painting it a second time here just added a redundant opaque
        // rectangle. Transparent everywhere but the rings themselves.
        val center = Offset(size.width / 2f, size.height * centerYFraction)
        val unit = minOf(size.width, size.height)
        val strokePx = STROKE_DP * density

        for (index in 0 until RING_COUNT) {
            val mix = if (RING_COUNT > 1) index / (RING_COUNT - 1).toFloat() else 0f
            val ringColor = lerp(color, colorTwo, mix)

            val local = time - index * STAGGER_SECONDS
            val t = ((local % CYCLE_SECONDS) + CYCLE_SECONDS) % CYCLE_SECONDS
            val brightness = pulseLife(t).coerceIn(0f, 1f)
            val drift = (1f - (1f - t / CYCLE_SECONDS).let { it * it }) * EXPANSION_FRACTION
            val radius = unit * (BASE_RADIUS_FRACTION + index * RADIUS_STEP_FRACTION + drift)

            if (brightness <= 0.002f) continue

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
                alpha = brightness,
                blendMode = BlendMode.Plus,
            )
        }
    }
}
