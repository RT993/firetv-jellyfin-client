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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** One diagonal light band: how far along the sweep it starts (phase), how fast it drifts
 * relative to the others (speed), its tilt off horizontal, and how thick/bright it reads. */
private data class Streak(val phase: Float, val speed: Float, val tiltDegrees: Float, val color: Color, val thickness: Float)

private val STREAKS = listOf(
    Streak(phase = 0.00f, speed = 1.00f, tiltDegrees = -18f, color = Color(0x5500A4DC), thickness = 0.16f),
    Streak(phase = 0.35f, speed = 0.72f, tiltDegrees = -18f, color = Color(0x402D7FC7), thickness = 0.22f),
    Streak(phase = 0.65f, speed = 1.35f, tiltDegrees = -18f, color = Color(0x330060A0), thickness = 0.12f),
)

// One full loop of the slowest streak - deliberately long, so the sweep reads as a slow ambient
// drift (PS5 dashboard-style) rather than something distractingly fast behind Home's content.
private const val LOOP_MS = 24_000

/**
 * A slow-drifting field of diagonal blue light bands behind Home's content - built from a handful
 * of tilted [Brush.linearGradient] fills (BlendMode.Plus for an additive glow) rather than any
 * real-time blur, which needs RenderEffect (API 31+) this app's minSdk 23 doesn't have and would
 * be far too costly on the target Fire Stick hardware anyway. The animated drift value is only
 * ever read inside the [Canvas] draw lambda, so each frame invalidates just this composable's
 * drawing - never a recomposition of the (static, potentially large) content drawn on top of it.
 */
@Composable
fun DynamicBlueBackground(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "dynamicBackgroundDrift")
    val drift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = LOOP_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "drift",
    )
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(TreeHouseBackground)
        STREAKS.forEach { streak ->
            // Ranges roughly -0.3..1.3 so each band fully enters and exits off-screen before its
            // next loop, instead of popping in/out at the very edge.
            val progress = ((drift * streak.speed + streak.phase) % 1f + 1f) % 1f
            val centerXFraction = progress * 1.6f - 0.3f
            drawStreak(centerXFraction, streak.tiltDegrees, streak.color, streak.thickness)
        }
    }
}

private fun DrawScope.drawStreak(centerXFraction: Float, tiltDegrees: Float, color: Color, thickness: Float) {
    val w = size.width
    val h = size.height
    val angleRad = Math.toRadians(tiltDegrees.toDouble())
    val dirX = cos(angleRad).toFloat()
    val dirY = sin(angleRad).toFloat()
    val diagonal = hypot(w, h)
    val center = Offset(w * centerXFraction, h / 2f)
    // Extends well past both screen edges so the gradient's own endpoints (always fully
    // transparent) never land inside the visible area.
    val halfLength = diagonal
    val start = Offset(center.x - dirX * halfLength, center.y - dirY * halfLength)
    val end = Offset(center.x + dirX * halfLength, center.y + dirY * halfLength)
    val brush = Brush.linearGradient(
        colorStops = arrayOf(
            0f to Color.Transparent,
            0.5f - thickness to Color.Transparent,
            0.5f to color,
            0.5f + thickness to Color.Transparent,
            1f to Color.Transparent,
        ),
        start = start,
        end = end,
    )
    drawRect(brush = brush, blendMode = BlendMode.Plus)
}
