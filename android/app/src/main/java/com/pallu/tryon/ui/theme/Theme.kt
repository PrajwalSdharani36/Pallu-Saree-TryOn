package com.pallu.tryon.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
val ZariGold = Color(0xFFD9AE55)    // fixed woven gold (staff screens, borders)
val Line = Color(0xFFE3DCE6)

/**
 * Mirror (kiosk) palette: "Silk Glass". The room takes the colour of the saree on screen (a
 * blurred photo behind everything, see Ambient), with frosted glass panels on top. Dark by
 * default; a light "day" version can be chosen on the mirror and every kiosk colour follows it.
 */
@Immutable
data class MirrorPalette(
    val light: Boolean,
    val mirror: Color,       // ground under the blurred saree
    val scrim: Color,        // laid over the blurred saree so text always reads
    val glass: Color,        // frosted panels
    val glassStrong: Color,  // panels that sit on top of a photo
    val glassLine: Color,
    val raised: Color,       // opaque sheets (keypad, "Try more sarees")
    val raised2: Color,
    val deep: Color,
    val onMirror: Color,
    val muted: Color,
    val faint: Color,
    val line: Color,
    val accent: Color,       // highlights, selected things, rings
    val accentBright: Color,
    val onAccent: Color,
    val primary: Color,      // the one main button on each screen
    val onPrimary: Color,
    val heart: Color,
    val peacock: Color,
    val strong: Color,       // the brightest text: countdown digits, the current step
)

val DarkMirror = MirrorPalette(
    light = false,
    mirror = Color(0xFF120D12), scrim = Color(0x8C120D12),
    glass = Color(0x1FFFFFFF), glassStrong = Color(0xB8150F15), glassLine = Color(0x33FFFFFF),
    raised = Color(0xFF221820), raised2 = Color(0xFF30242C), deep = Color(0xFF0B080B),
    onMirror = Color(0xFFFBF6F1), muted = Color(0xFFE0D6DA), faint = Color(0xFFC2B6BC), line = Color(0x40FFFFFF),
    accent = Color(0xFFF0D08A), accentBright = Color(0xFFFFE2A8), onAccent = Color(0xFF1A1216),
    primary = Color(0xFFFFFFFF), onPrimary = Color(0xFF1A1216),
    heart = Color(0xFFD93A55), peacock = Color(0xFF2E8B72),
    strong = Color(0xFFFFFFFF),
)

val LightMirror = MirrorPalette(
    light = true,
    mirror = Color(0xFFFAF5EF), scrim = Color(0xBFFAF5EF),
    glass = Color(0xA6FFFFFF), glassStrong = Color(0xE6FFFDFA), glassLine = Color(0x262A1A20),
    raised = Color(0xFFFFFDF9), raised2 = Color(0xFFF2E9E0), deep = Color(0xFFEFE5DA),
    onMirror = Color(0xFF2A1A20), muted = Color(0xFF5B4750), faint = Color(0xFF6E5B64), line = Color(0x332A1A20),
    accent = Color(0xFF7A1E2C), accentBright = Color(0xFFB8892E), onAccent = Color(0xFFFFFFFF),
    primary = Color(0xFF7A1E2C), onPrimary = Color(0xFFFFFFFF),
    heart = Color(0xFFC23B55), peacock = Color(0xFF2F6B5E),
    strong = Color(0xFF2A1A20),
)

/** The mirror's current palette; the kiosk's light/dark switch sets it (saved per device). */
var mirrorPalette by mutableStateOf(DarkMirror)

// Read through the palette so a switch repaints every kiosk screen, drawings included.
val Mirror: Color get() = mirrorPalette.mirror
val MirrorRaised: Color get() = mirrorPalette.raised
val MirrorRaised2: Color get() = mirrorPalette.raised2
val MirrorDeep: Color get() = mirrorPalette.deep
val OnMirror: Color get() = mirrorPalette.onMirror
val OnMirrorMuted: Color get() = mirrorPalette.muted
val OnMirrorFaint: Color get() = mirrorPalette.faint
val MirrorLine: Color get() = mirrorPalette.line
val ZariLight: Color get() = mirrorPalette.accent
val GoldBright: Color get() = mirrorPalette.accentBright
val OnGold: Color get() = mirrorPalette.onAccent
val Kumkum: Color get() = mirrorPalette.heart
val PeacockDeep: Color get() = mirrorPalette.peacock
val MirrorStrong: Color get() = mirrorPalette.strong
val Glass: Color get() = mirrorPalette.glass
val GlassStrong: Color get() = mirrorPalette.glassStrong
val GlassLine: Color get() = mirrorPalette.glassLine
val Primary: Color get() = mirrorPalette.primary
val OnPrimary: Color get() = mirrorPalette.onPrimary
val TempleRed = Color(0xFF5C0F1F)
val Danger = Color(0xFFA3122A)

/** Display face: Fraunces with its "soft" rounded serifs, so big headings read gently, not sharp. */
val Rozha = FontFamily(Font(R.font.fraunces_soft_semibold, FontWeight.Normal), Font(R.font.fraunces_soft_semibold, FontWeight.SemiBold))
/** Body face: Manrope, open and very legible at a distance. */
val Sans = FontFamily(
    Font(R.font.manrope_400, FontWeight.Normal),
    Font(R.font.manrope_500, FontWeight.Medium),
    Font(R.font.manrope_600, FontWeight.SemiBold),
    Font(R.font.manrope_700, FontWeight.Bold),
)

private val PalluType = Typography(
    displayLarge = TextStyle(fontFamily = Rozha, fontSize = 52.sp, lineHeight = 58.sp),
    displayMedium = TextStyle(fontFamily = Rozha, fontSize = 40.sp, lineHeight = 46.sp),
    headlineLarge = TextStyle(fontFamily = Rozha, fontSize = 32.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = Rozha, fontSize = 27.sp, lineHeight = 33.sp),
    headlineSmall = TextStyle(fontFamily = Rozha, fontSize = 23.sp, lineHeight = 29.sp),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 21.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontSize = 17.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontSize = 15.sp, lineHeight = 23.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
)

/** The mirror is read from a step or two away, often by customers who don't use apps much: bigger everything. */
private val MirrorType = Typography(
    displayLarge = TextStyle(fontFamily = Rozha, fontSize = 64.sp, lineHeight = 70.sp),
    displayMedium = TextStyle(fontFamily = Rozha, fontSize = 48.sp, lineHeight = 54.sp),
    headlineLarge = TextStyle(fontFamily = Rozha, fontSize = 38.sp, lineHeight = 44.sp),
    headlineMedium = TextStyle(fontFamily = Rozha, fontSize = 32.sp, lineHeight = 38.sp),
    headlineSmall = TextStyle(fontFamily = Rozha, fontSize = 27.sp, lineHeight = 33.sp),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 26.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 23.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontSize = 20.sp, lineHeight = 30.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontSize = 18.sp, lineHeight = 27.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontSize = 16.sp, lineHeight = 23.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 22.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
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

private fun mirrorColors(p: MirrorPalette) = if (p.light) {
    lightColorScheme(
        primary = p.accent, onPrimary = p.onAccent,
        primaryContainer = Color(0xFFF7E6E8), onPrimaryContainer = p.onMirror,
        secondary = p.peacock, onSecondary = Color.White,
        tertiary = p.heart, onTertiary = Color.White,
        background = p.mirror, onBackground = p.onMirror,
        surface = p.raised, onSurface = p.onMirror,
        surfaceVariant = p.raised2, onSurfaceVariant = p.muted,
        outline = p.line, outlineVariant = Color(0xFFEADDD0),
        error = Danger,
    )
} else {
    darkColorScheme(
        primary = p.accent, onPrimary = p.onAccent,
        primaryContainer = Color(0xFF3A2A2E), onPrimaryContainer = p.onMirror,
        secondary = Color(0xFF5FC2C6), onSecondary = Color(0xFF032426),
        tertiary = p.heart, onTertiary = Color.White,
        background = p.mirror, onBackground = p.onMirror,
        surface = p.raised, onSurface = p.onMirror,
        surfaceVariant = p.raised2, onSurfaceVariant = p.muted,
        outline = p.line, outlineVariant = Color(0x26FFFFFF),
        error = Color(0xFFFF8A9A),
    )
}

/** Staff screens: light silk. */
@Composable
fun PalluTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = StoreColors, typography = PalluType, content = content)
}

/** Kiosk screens: Silk Glass, with large type for reading from a step away. */
@Composable
fun MirrorTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = mirrorColors(mirrorPalette), typography = MirrorType, content = content)
}
