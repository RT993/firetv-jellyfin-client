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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private data class Sparkle(val angle: Float, val radiusFraction: Float, val phase: Float, val speed: Float, val size: Float)

private const val SPARKLE_COUNT = 26
private const val LOOP_MS = 7000
// Fixed seed, not a new Random() per composition - the dots should sit in the same relative
// arrangement every time a tile recomposes, only animating smoothly, not reshuffling.
private val SPARKLES = Random(42).let { random ->
    List(SPARKLE_COUNT) {
        Sparkle(
            angle = random.nextFloat() * (Math.PI * 2).toFloat(),
            radiusFraction = 0.25f + random.nextFloat() * 0.65f,
            phase = random.nextFloat() * (Math.PI * 2).toFloat(),
            speed = 0.4f + random.nextFloat() * 0.8f,
            size = 1.2f + random.nextFloat() * 2.2f,
        )
    }
}

/**
 * A small, self-contained "glowing orb" fill for a profile tile - a breathing radial glow plus a
 * couple dozen drifting, twinkling sparkle dots, evoking a crystal ball without the literal 15-40k
 * particle WebGL version that'd be a real problem on this app's target Fire Stick hardware. Meant
 * to sit behind [androidx.tv.material3.Card]'s own circular clip (see [FocusableCard]'s `shape`
 * param) - this composable doesn't clip itself, it just fills whatever bounds it's given.
 */
@Composable
fun ProfileGlowOrb(modifier: Modifier = Modifier, color: Color = TreeHouseAccent) {
    val infiniteTransition = rememberInfiniteTransition(label = "profileGlowOrb")
    val t by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(LOOP_MS, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "orbTime",
    )
    val breathe by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(2200, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "orbBreathe",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = min(size.width, size.height) / 2f

        // No flat fill here - transparent everywhere but the glow/sparkles/ring themselves, so
        // whatever sits behind the tile (the profile picker's rings) shows through it instead of
        // being covered by a solid circle.
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to color.copy(alpha = 0.55f + 0.15f * breathe),
                    0.6f to color.copy(alpha = 0.22f),
                    1f to color.copy(alpha = 0.04f),
                ),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
        drawSparkles(center, radius, color, t)
        drawCircle(
            color = color.copy(alpha = 0.35f + 0.25f * breathe),
            radius = radius * (0.96f + 0.02f * breathe),
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f + breathe),
        )
    }
}

private fun DrawScope.drawSparkles(center: Offset, radius: Float, color: Color, t: Float) {
    val tau = (Math.PI * 2).toFloat()
    SPARKLES.forEach { sparkle ->
        val angle = sparkle.angle + t * tau * sparkle.speed
        val dotRadius = radius * sparkle.radiusFraction
        val position = Offset(center.x + cos(angle) * dotRadius, center.y + sin(angle) * dotRadius)
        val twinkle = 0.35f + 0.65f * (0.5f + 0.5f * sin(t * tau * 1.6f + sparkle.phase))
        drawCircle(color = Color.White, radius = sparkle.size, center = position, alpha = twinkle * 0.8f)
        drawCircle(color = color, radius = sparkle.size * 2.2f, center = position, alpha = twinkle * 0.25f)
    }
}
