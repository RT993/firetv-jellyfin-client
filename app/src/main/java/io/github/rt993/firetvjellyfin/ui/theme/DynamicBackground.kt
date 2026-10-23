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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

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

private data class BeamDust(val xJitter: Float, val phase: Float, val speed: Float, val size: Float)

private const val DUST_COUNT = 18
// Fixed seed, not a fresh Random() per composition - dust should keep the same relative starting
// positions across recompositions and only animate, not reshuffle.
private val BEAM_DUST = Random(7).let { random ->
    List(DUST_COUNT) {
        BeamDust(
            xJitter = (random.nextFloat() - 0.5f) * 0.06f,
            phase = random.nextFloat(),
            speed = 0.6f + random.nextFloat() * 0.6f,
            size = 1.5f + random.nextFloat() * 2f,
        )
    }
}

private const val LASER_LOOP_MS = 5200

/**
 * A vertical beam of light landing on the screen and spilling outward where it hits - built the
 * same way as [DynamicBlueBackground] (plain gradient fills, BlendMode.Plus for glow, no real
 * blur/shader pass) rather than a literal WebGL port, for the same target-hardware reasons. Meant
 * to sit behind the profile picker's content, with [landingYFraction] aimed at wherever that
 * content actually sits, so the splash reads as light hitting it rather than an unrelated effect.
 */
@Composable
fun LaserBeamBackground(
    modifier: Modifier = Modifier,
    color: Color = TreeHouseAccent,
    beamXFraction: Float = 0.5f,
    landingYFraction: Float = 0.46f,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "laserBeamBackground")
    val t by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(LASER_LOOP_MS, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "laserTime",
    )
    val breathe by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(2600, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "laserBreathe",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(TreeHouseBackground)
        val beamX = size.width * beamXFraction
        val landingY = size.height * landingYFraction
        val beamHalfWidth = size.width * 0.035f

        // The beam itself: a narrow vertical core fading out toward its edges, from the top of the
        // screen down to where it lands.
        val beamBrush = Brush.linearGradient(
            colorStops = arrayOf(
                0f to Color.Transparent,
                0.5f to color.copy(alpha = 0.9f),
                1f to Color.Transparent,
            ),
            start = Offset(beamX - beamHalfWidth, 0f),
            end = Offset(beamX + beamHalfWidth, 0f),
        )
        drawRect(brush = beamBrush, topLeft = Offset.Zero, size = Size(size.width, landingY), blendMode = BlendMode.Plus)

        // Where it lands: a wide, flattened glow spilling sideways across the surface (squashed
        // vertically via scale() - Brush.radialGradient itself is always circular), pulsing gently
        // rather than holding one fixed brightness.
        val spillRadius = size.width * (0.42f + 0.04f * breathe)
        val landing = Offset(beamX, landingY)
        scale(scaleX = 1f, scaleY = 0.3f, pivot = landing) {
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to color.copy(alpha = 0.5f + 0.15f * breathe),
                        0.5f to color.copy(alpha = 0.18f),
                        1f to Color.Transparent,
                    ),
                    center = landing,
                    radius = spillRadius,
                ),
                radius = spillRadius,
                center = landing,
                blendMode = BlendMode.Plus,
            )
        }

        // A tight, bright core flare right at the landing point itself.
        drawCircle(
            color = Color.White,
            radius = size.width * (0.012f + 0.004f * breathe),
            center = landing,
            alpha = 0.85f,
            blendMode = BlendMode.Plus,
        )

        // Dust falling down the beam into the landing point, fading in and out at either end
        // rather than popping in place.
        BEAM_DUST.forEach { dust ->
            val fall = (t * dust.speed + dust.phase) % 1f
            val y = landingY * fall
            val edgeFade = sin((fall * Math.PI).toFloat()).coerceIn(0f, 1f)
            val x = beamX + size.width * dust.xJitter
            drawCircle(color = Color.White, radius = dust.size, center = Offset(x, y), alpha = edgeFade * 0.7f)
        }
    }
}
