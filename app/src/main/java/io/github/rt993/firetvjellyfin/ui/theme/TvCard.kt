package io.github.rt993.firetvjellyfin.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Glow

private const val CARD_CORNER_DP = 12
private const val FOCUSED_SCALE = 1.14f
private const val FOCUSED_GLOW_DP = 24

/**
 * A [Card] with the "Scale & Glow"/halo-border focus treatment shared by every poster/spotlight
 * tile across Home and Details - scale up, a colored glow, and a crisp 2px border when focused, so
 * a focused tile never blends into busy background art. The glow is tinted with the app's accent
 * color rather than plain white for a more distinctive, "branded" focus state.
 */
@Composable
fun FocusableCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(CARD_CORNER_DP.dp)
    Card(
        onClick = onClick,
        modifier = modifier.onFocusChanged { if (it.isFocused) onFocused() },
        shape = CardDefaults.shape(shape = shape, focusedShape = shape),
        scale = CardDefaults.scale(focusedScale = FOCUSED_SCALE),
        border = CardDefaults.border(
            focusedBorder = Border(border = BorderStroke(2.dp, Color.White), shape = shape),
        ),
        glow = CardDefaults.glow(focusedGlow = Glow(elevationColor = TreeHouseAccent, elevation = FOCUSED_GLOW_DP.dp)),
    ) {
        content()
    }
}
