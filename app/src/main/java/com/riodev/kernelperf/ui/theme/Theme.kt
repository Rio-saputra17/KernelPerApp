package com.riodev.kernelperf.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ─── Color Palette ────────────────────────────────────────────────────────────
val Cyan400 = Color(0xFF26C6DA)
val Cyan600 = Color(0xFF00ACC1)
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
    onPrimary = Color(0xFF003038),
    primaryContainer = Color(0xFF004F5B),
    onPrimaryContainer = Cyan400,
    secondary = Color(0xFF4FC3F7),
    onSecondary = Color(0xFF003549),
    background = DarkBg,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkCard,
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFF2A3A4A),
    error = RedAccent
)

@Composable
fun KernelPerfTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography(),
        content = content
    )
}
