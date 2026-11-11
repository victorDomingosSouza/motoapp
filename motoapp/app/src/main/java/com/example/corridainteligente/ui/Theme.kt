package com.example.corridainteligente.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val AccentGreen = Color(0xFF39CE7B)
val PanelColor = Color(0xFF1A1C1C)
val TileColor = Color(0xFF121414)

private val DarkColors = darkColorScheme(
    primary = AccentGreen,
    onPrimary = Color(0xFF082414),
    primaryContainer = Color(0xFF193626),
    onPrimaryContainer = AccentGreen,
    secondary = Color(0xFFB5C8BD),
    secondaryContainer = Color(0xFF193626),
    onSecondaryContainer = AccentGreen,
    background = Color(0xFF101212),
    onBackground = Color(0xFFF2F4F3),
    surface = PanelColor,
    surfaceContainer = PanelColor,
    surfaceContainerHigh = Color(0xFF202423),
    surfaceContainerHighest = Color(0xFF252A28),
    surfaceContainerLow = Color(0xFF151817),
    surfaceContainerLowest = Color(0xFF0B0D0C),
    onSurface = Color(0xFFF2F4F3),
    surfaceVariant = Color(0xFF232727),
    onSurfaceVariant = Color(0xFFA8B0AC),
    outline = Color(0xFF65716A),
    outlineVariant = Color(0xFF303634),
    error = Color(0xFFFF8585),
)

@Composable
fun CorridaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(24.dp),
        ),
        content = content,
    )
}
