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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class ShardSpec(
    val baseX: Float,
    val baseY: Float,
    val depth: Float,
    val sizeFraction: Float,
    val rotation: Float,
    val driftAngle: Float,
    val localPoints: List<Offset>,
)

private const val GRID_COLS = 6
private const val GRID_ROWS = 3
private const val SHARD_COUNT = GRID_COLS * GRID_ROWS
// 0..1 screen-widths of drift per second at the nearest depth - farther shards move slower, the
// same cheap parallax cue used for depth everywhere else in this app (scale+speed, no real 3D).
private const val DRIFT_SPEED_FRACTION = 0.014f
private const val WRAP_MARGIN = 0.2f
private const val WRAP_SPAN = 1f + 2 * WRAP_MARGIN

// Fixed seed, not a fresh Random() per composition - shards keep the same relative shapes/starting
// positions across recompositions and only drift, never reshuffle. One shard per cell of a grid
// (jittered within it), not pure random x/y - with only 18 shards, plain uniform random placement
// risked a particular seed clumping most of them into one half of the screen and leaving the other
// empty; a grid guarantees even coverage regardless of what the seed happens to roll.
private val SHARDS: List<ShardSpec> = Random(99).let { random ->
    val cellWidth = WRAP_SPAN / GRID_COLS
    val cellHeight = WRAP_SPAN / GRID_ROWS
    List(SHARD_COUNT) { cell ->
        val col = cell % GRID_COLS
        val row = cell / GRID_COLS
        val depth = random.nextFloat()
        val stretch = 2.0f + random.nextFloat() * 2.4f
        val baseAngle = random.nextFloat() * (2 * Math.PI).toFloat()
        val points = (0 until 4).map { corner ->
            val angle = baseAngle + corner * (Math.PI / 2).toFloat() + (random.nextFloat() - 0.5f) * 0.7f
            val radius = 0.5f + random.nextFloat() * 0.5f
            Offset(cos(angle) * radius * stretch, sin(angle) * radius)
        }
        ShardSpec(
            baseX = -WRAP_MARGIN + (col + 0.5f + (random.nextFloat() - 0.5f) * 0.8f) * cellWidth,
            baseY = -WRAP_MARGIN + (row + 0.5f + (random.nextFloat() - 0.5f) * 0.8f) * cellHeight,
            depth = depth,
            sizeFraction = 0.028f + (1f - depth) * 0.05f + random.nextFloat() * 0.018f,
            rotation = random.nextFloat() * (2 * Math.PI).toFloat(),
            driftAngle = (18f + random.nextFloat() * 24f) * (Math.PI / 180f).toFloat(),
            localPoints = points,
        )
    }
}

private fun rotated(p: Offset, angle: Float): Offset {
    val c = cos(angle)
    val s = sin(angle)
    return Offset(p.x * c - p.y * s, p.x * s + p.y * c)
}

private fun wrap(value: Float): Float {
    val shifted = (value + WRAP_MARGIN) % WRAP_SPAN
    return (if (shifted < 0f) shifted + WRAP_SPAN else shifted) - WRAP_MARGIN
}

/**
 * A field of flat, irregular glass-shard shapes drifting slowly across the screen at a few
 * different depths - not a literal port of React Bits' AeroShards, a raw WebGPU ray-traced glass
 * renderer with bloom/depth-of-field/soft shadows (~1300 shards) that has no equivalent on this
 * Android/Compose stack, and would be wildly beyond this app's target Fire Stick hardware even if
 * it did. Each shard is just a filled, low-alpha polygon with a brighter [accentColor] stroke
 * along its own edges standing in for the rim light a real shard would catch - the same cheap
 * additive-glow trick used everywhere else in this app, no real 3D, no shaders, no blur.
 */
@Composable
fun AeroShardsBackground(
    modifier: Modifier = Modifier,
    shardColor: Color = Color(0xFF000000),
    accentColor: Color = Color(0xFF3B82F6),
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
        val unit = minOf(size.width, size.height)

        SHARDS.forEach { shard ->
            val speed = DRIFT_SPEED_FRACTION * (0.3f + (1f - shard.depth) * 0.7f)
            val dx = cos(shard.driftAngle) * speed * time
            val dy = sin(shard.driftAngle) * speed * time
            val centerX = wrap(shard.baseX + dx) * size.width
            val centerY = wrap(shard.baseY + dy) * size.height
            val center = Offset(centerX, centerY)
            val shardSize = unit * shard.sizeFraction * (0.5f + (1f - shard.depth) * 0.5f)
            val alpha = 0.1f + (1f - shard.depth) * 0.16f

            val path = Path()
            shard.localPoints.forEachIndexed { index, point ->
                val rotatedPoint = rotated(point, shard.rotation)
                val screenPoint = center + Offset(rotatedPoint.x, rotatedPoint.y) * shardSize
                if (index == 0) path.moveTo(screenPoint.x, screenPoint.y) else path.lineTo(screenPoint.x, screenPoint.y)
            }
            path.close()

            drawPath(
                path = path,
                brush = Brush.linearGradient(
                    listOf(shardColor.copy(alpha = alpha * 0.7f), shardColor.copy(alpha = alpha * 0.25f)),
                ),
            )
            drawPath(
                path = path,
                color = accentColor,
                alpha = (alpha * 2.2f).coerceIn(0f, 0.55f),
                style = Stroke(width = 1.1f * density),
            )
        }
    }
}
