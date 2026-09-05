package io.github.rt993.firetvjellyfin.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * A soft, borderless halo of [color] bleeding out from behind whatever this wraps (typically a
 * poster) - the "ambient glow" look of a per-title accent color, without a real Gaussian blur
 * (Compose's blur() modifier needs API 31+ to actually render, and this app's target hardware -
 * see DeviceProfileFactory - runs Android 9). A radial gradient sized larger than [content] and
 * centered behind it fakes the same softness on every API level this app supports - so size
 * [modifier] noticeably bigger than content for the bleed to actually show.
 */
@Composable
fun AmbientGlow(color: Color?, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) {
        if (color != null) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(Brush.radialGradient(listOf(color.copy(alpha = 0.55f), Color.Transparent))),
            )
        }
        content()
    }
}
