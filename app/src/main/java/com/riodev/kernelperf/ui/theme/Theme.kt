package com.riodev.kernelperf.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Cyan = Color(0xFF00BCD4)
val CyanDark = Color(0xFF006064)
val BgDark = Color(0xFF0D1117)
val Card = Color(0xFF161B22)
val CardBorder = Color(0xFF21262D)
val TextPri = Color(0xFFE6EDF3)
val TextSec = Color(0xFF8B949E)
val Green = Color(0xFF3FB950)
val Orange = Color(0xFFF78166)
val Red = Color(0xFFFF7B72)
val Yellow = Color(0xFFD29922)

private val scheme = darkColorScheme(
    primary = Cyan, background = BgDark, surface = Card,
    onBackground = TextPri, onSurface = TextPri,
    surfaceVariant = Card, onSurfaceVariant = TextSec, error = Red
)

@Composable
fun Theme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
