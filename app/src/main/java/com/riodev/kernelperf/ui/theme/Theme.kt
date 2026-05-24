package com.riodev.kernelperf.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Cyan400 = Color(0xFF26C6DA)
val Cyan900 = Color(0xFF006064)
val DarkBg = Color(0xFF0A0E14)
val DarkSurface = Color(0xFF111820)
val DarkCard = Color(0xFF161E28)
val DarkCardElevated = Color(0xFF1C2530)
val TextPrimary = Color(0xFFE8EDF2)
val TextSecondary = Color(0xFF8A9BB0)
val GreenAccent = Color(0xFF00E676)
val OrangeAccent = Color(0xFFFF6D00)
val RedAccent = Color(0xFFFF1744)

private val DarkColorScheme = darkColorScheme(
    primary = Cyan400,
    background = DarkBg,
    surface = DarkSurface,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    surfaceVariant = DarkCard,
    onSurfaceVariant = TextSecondary,
    error = RedAccent
)

@Composable
fun KernelPerfTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColorScheme, content = content)
}
