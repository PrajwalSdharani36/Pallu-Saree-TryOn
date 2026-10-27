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
 * Mirror (kiosk) palette. Dark by default: a dim trial room, like the inside of a jewellery box.
 * A light "ivory silk" version can be chosen on the mirror; every kiosk colour below follows it.
 */
@Immutable
data class MirrorPalette(
    val light: Boolean,
    val mirror: Color,
    val raised: Color,
    val raised2: Color,
    val deep: Color,
    val onMirror: Color,
    val muted: Color,
    val faint: Color,
    val line: Color,
    val accent: Color,       // buttons, selected chips, highlights
    val accentBright: Color, // rings and glows
    val onAccent: Color,
    val heart: Color,
    val peacock: Color,
    val borderGround: Color,
    val borderBand: Color,
    val borderGold: Color,
    val strong: Color,       // the brightest text: countdown digits, the current step
)

val DarkMirror = MirrorPalette(
    light = false,
    mirror = Color(0xFF170F14), raised = Color(0xFF241820), raised2 = Color(0xFF31212B), deep = Color(0xFF0E090C),
    onMirror = Color(0xFFF3E8E0), muted = Color(0xFFBBA8B3), faint = Color(0xFF9C8A95), line = Color(0xFF5A4450),
    accent = Color(0xFFD9AE55), accentBright = Color(0xFFF1D48A), onAccent = Color(0xFF1E1218),
    heart = Color(0xFFE0566B), peacock = Color(0xFF1B6E5A),
    borderGround = Color(0xFF5C0F1F), borderBand = Color(0xFF8E1B2E), borderGold = Color(0xFFD9AE55),
    strong = Color(0xFFFFF6E6),
)

val LightMirror = MirrorPalette(
    light = true,
    mirror = Color(0xFFFBF6F0), raised = Color(0xFFFFFCF8), raised2 = Color(0xFFF1E7DD), deep = Color(0xFFF3EAE0),
    onMirror = Color(0xFF2E1D25), muted = Color(0xFF6B5862), faint = Color(0xFF75626C), line = Color(0xFFDCCDBF),
    accent = Color(0xFF8E1B2E), accentBright = Color(0xFFB8892E), onAccent = Color(0xFFFFF8F2),
    heart = Color(0xFFC23B55), peacock = Color(0xFF2F6B5E),
    borderGround = Color(0xFFF3E6D6), borderBand = Color(0xFFA33A4C), borderGold = Color(0xFFC69C45),
    strong = Color(0xFF2E1D25),
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
val TempleRed = Color(0xFF5C0F1F)
val Danger = Color(0xFFA3122A)

/** Display face: Fraunces with its "soft" rounded serifs, so big headings read gently, not sharp. */
val Rozha = FontFamily(Font(R.font.fraunces_soft_semibold, FontWeight.Normal), Font(R.font.fraunces_soft_semibold, FontWeight.SemiBold))
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

private fun mirrorColors(p: MirrorPalette) = if (p.light) {
    lightColorScheme(
        primary = p.accent, onPrimary = p.onAccent,
        primaryContainer = Color(0xFFFFF4EE), onPrimaryContainer = p.onMirror,
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
        primaryContainer = Color(0xFF3A1F2A), onPrimaryContainer = p.onMirror,
        secondary = Color(0xFF5FC2C6), onSecondary = Color(0xFF032426),
        tertiary = p.heart, onTertiary = Color.White,
        background = p.mirror, onBackground = p.onMirror,
        surface = p.raised, onSurface = p.onMirror,
        surfaceVariant = p.raised2, onSurfaceVariant = p.muted,
        outline = p.line, outlineVariant = Color(0xFF3A2A33),
        error = Color(0xFFFF7A8C),
    )
}

/** Staff screens: light silk. */
@Composable
fun PalluTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = StoreColors, typography = PalluType, content = content)
}

/** Kiosk screens: a dim trial room so the try-on photos glow. */
@Composable
fun MirrorTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = mirrorColors(mirrorPalette), typography = PalluType, content = content)
}
