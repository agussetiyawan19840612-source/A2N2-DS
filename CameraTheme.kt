package com.naturalai.camerapro.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val ObsidianBlack = Color(0xFF050505)
val SurfacePanel = Color(0xFF101216)
val BorderHairline = Color(0xFF23272F)
val AmberAccent = Color(0xFFF59E0B)
val CyanAccent = Color(0xFF06B6D4)
val CrimsonClip = Color(0xFFEF4444)
val EmeraldOk = Color(0xFF10B981)

private val ProCameraColorScheme = darkColorScheme(
    primary = AmberAccent,
    onPrimary = Color.Black,
    secondary = CyanAccent,
    onSecondary = Color.Black,
    background = ObsidianBlack,
    onBackground = Color(0xFFF4F4F5),
    surface = SurfacePanel,
    onSurface = Color(0xFFF4F4F5),
    surfaceVariant = Color(0xFF181B22),
    onSurfaceVariant = Color(0xFFA1A1AA),
    outline = BorderHairline,
    error = CrimsonClip
)

@Composable
fun NaturalCameraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ProCameraColorScheme,
        content = content
    )
}
