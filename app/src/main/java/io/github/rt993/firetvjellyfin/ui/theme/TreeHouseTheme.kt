package io.github.rt993.firetvjellyfin.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

// Mirrors app/src/main/res/values/colors.xml - kept in sync by hand since Compose color tokens
// and Android color resources aren't the same type.
val TreeHouseBackground = Color(0xFF101418)
val TreeHouseSurface = Color(0xFF202832)
val TreeHouseAccent = Color(0xFF00A4DC)
val TreeHouseTextPrimary = Color(0xFFFFFFFF)
val TreeHouseTextSecondary = Color(0xFFB0B8C0)

@Composable
fun TreeHouseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = TreeHouseAccent,
            onPrimary = TreeHouseTextPrimary,
            background = TreeHouseBackground,
            onBackground = TreeHouseTextPrimary,
            surface = TreeHouseSurface,
            onSurface = TreeHouseTextPrimary,
            surfaceVariant = TreeHouseSurface,
            onSurfaceVariant = TreeHouseTextSecondary,
        ),
        content = content,
    )
}
