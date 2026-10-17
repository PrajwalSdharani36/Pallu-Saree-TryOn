package com.pallu.tryon.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pallu.tryon.R

// Kanjivaram palette
val Silk = Color(0xFFF5F2F7)        // pale lilac-grey ground
val Paper = Color(0xFFFFFFFF)
val Ink = Color(0xFF24121A)         // aubergine ink
val Muted = Color(0xFF6E5A66)
val Arakku = Color(0xFF8E1B2E)      // lac red
val ArakkuDeep = Color(0xFF6B1222)
val Peacock = Color(0xFF0E5E63)     // mayil teal
val Zari = Color(0xFFB8892E)        // woven gold
val ZariLight = Color(0xFFD9AE55)
val Line = Color(0xFFE3DCE6)
// Mirror (kiosk) palette: a dim trial room, like the inside of a jewellery box
val Mirror = Color(0xFF170F14)      // night aubergine
val MirrorRaised = Color(0xFF241820) // velvet
val MirrorRaised2 = Color(0xFF31212B)
val MirrorDeep = Color(0xFF0E090C)
val OnMirror = Color(0xFFF6ECE4)    // warm ivory
val OnMirrorMuted = Color(0xFFBBA8B3)
val OnMirrorFaint = Color(0xFF9C8A95)
val MirrorLine = Color(0xFF5A4450)
val GoldBright = Color(0xFFF1D48A)
val OnGold = Color(0xFF1E1218)
val Kumkum = Color(0xFFE0566B)      // shortlist heart
val PeacockDeep = Color(0xFF1B6E5A)
val TempleRed = Color(0xFF5C0F1F)
val Danger = Color(0xFFA3122A)

val Rozha = FontFamily(Font(R.font.rozha_one, FontWeight.Normal))
val Mukta = FontFamily(
    Font(R.font.mukta_regular, FontWeight.Normal),
    Font(R.font.mukta_medium, FontWeight.Medium),
    Font(R.font.mukta_bold, FontWeight.Bold),
)

private val PalluType = Typography(
    displayLarge = TextStyle(fontFamily = Rozha, fontSize = 52.sp, lineHeight = 58.sp),
    displayMedium = TextStyle(fontFamily = Rozha, fontSize = 40.sp, lineHeight = 46.sp),
    headlineLarge = TextStyle(fontFamily = Rozha, fontSize = 32.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = Rozha, fontSize = 26.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontFamily = Rozha, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = Mukta, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Mukta, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 23.sp),
    titleSmall = TextStyle(fontFamily = Mukta, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Mukta, fontSize = 17.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontFamily = Mukta, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = Mukta, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Mukta, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Mukta, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = Mukta, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)

private val StoreColors = lightColorScheme(
    primary = Arakku,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF6DDE1),
    onPrimaryContainer = ArakkuDeep,
    secondary = Peacock,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3ECEC),
    onSecondaryContainer = Color(0xFF053437),
    tertiary = Zari,
    background = Silk,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Silk,
    onSurfaceVariant = Muted,
    outline = Line,
    outlineVariant = Line,
    error = Danger,
)

private val MirrorColors = darkColorScheme(
    primary = ZariLight,
    onPrimary = OnGold,
    primaryContainer = Color(0xFF3A1F2A),
    onPrimaryContainer = OnMirror,
    secondary = Color(0xFF5FC2C6),
    onSecondary = Color(0xFF032426),
    tertiary = Kumkum,
    onTertiary = Color.White,
    background = Mirror,
    onBackground = OnMirror,
    surface = MirrorRaised,
    onSurface = OnMirror,
    surfaceVariant = MirrorRaised2,
    onSurfaceVariant = OnMirrorMuted,
    outline = MirrorLine,
    outlineVariant = Color(0xFF3A2A33),
    error = Color(0xFFFF7A8C),
)

/** Staff screens: light silk. */
@Composable
fun PalluTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = StoreColors, typography = PalluType, content = content)
}

/** Kiosk screens: a dim trial room so the try-on photos glow. */
@Composable
fun MirrorTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MirrorColors, typography = PalluType, content = content)
}
