package com.martin.minimallauncher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.martin.minimallauncher.R

private val DarkColors = darkColorScheme(
    background = Color.Black,
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF0A0A0A),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF141414),
    onSurfaceVariant = Color(0xFFBDBDBD),
    primary = Color(0xFFEDEDED),
    onPrimary = Color.Black,
    secondary = Color(0xFF8A8A8A),
    outline = Color(0xFF333333),
    // Tonal surfaces — keep dialogs, sheets and menus on our near-black aesthetic.
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0C0C0D),
    surfaceContainer = Color(0xFF121214),
    surfaceContainerHigh = Color(0xFF17171A),
    surfaceContainerHighest = Color(0xFF1E1E22),
)

private val LightColors = lightColorScheme(
    // Warm-biased neutrals (a chosen grey, not a default), matching the amber accent.
    background = Color(0xFFF7F6F3),
    onBackground = Color(0xFF1A1917),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1917),
    surfaceVariant = Color(0xFFEFEDE7),
    onSurfaceVariant = Color(0xFF5C5A54),
    primary = Color(0xFF1A1917),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF78766F),
    outline = Color(0xFFE3E1DA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F2ED),
    surfaceContainer = Color(0xFFEEECE6),
    surfaceContainerHigh = Color(0xFFE9E6DF),
    surfaceContainerHighest = Color(0xFFE3E0D8),
)

/**
 * Manrope (variable font). Requires app/src/main/res/font/manrope.ttf — download the Manrope
 * variable font from Google Fonts. Falls back to the system font until the file is present.
 */
private val Manrope = FontFamily(
    Font(R.font.manrope, weight = FontWeight.Light, variationSettings = FontVariation.Settings(FontVariation.weight(300))),
    Font(R.font.manrope, weight = FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.manrope, weight = FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.manrope, weight = FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
)

private val MinimalTypography = Typography(
    displayLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Light, fontSize = 64.sp, letterSpacing = (-1.5).sp),
    headlineMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Light, fontSize = 24.sp, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 22.sp, letterSpacing = (-0.3).sp),
    titleSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 15.sp, letterSpacing = (-0.1).sp),
    bodyLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 18.sp, letterSpacing = 0.1.sp),
    bodyMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 15.sp, letterSpacing = 0.1.sp),
    labelLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 0.4.sp),
    titleMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 16.sp),
    labelMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 12.sp),
)

/** Warm "focus light" — the brand's amber, used for the active focus state regardless of accent. */
val FocusWarm = Color(0xFFD8A24A)
val OnFocusWarm = Color(0xFF1A1206)

/** Optional accent colors; index 0 = default (monochrome). Tints primary (badges, highlights…). */
val AccentColors: List<Color?> = listOf(
    null,               // default: monochrome primary
    Color(0xFF3B82F6),  // blue
    Color(0xFF10B981),  // green
    Color(0xFFF59E0B),  // amber
    Color(0xFF8B5CF6),  // violet
    Color(0xFFEF4444),  // red
)

@Composable
fun MinimalLauncherTheme(
    amoledDark: Boolean = true,
    accent: Int = 0,
    content: @Composable () -> Unit,
) {
    val base = if (amoledDark) DarkColors else LightColors
    val accentColor = AccentColors.getOrNull(accent)
    val scheme = if (accentColor != null) base.copy(primary = accentColor, onPrimary = Color.White) else base
    MaterialTheme(
        colorScheme = scheme,
        typography = MinimalTypography,
        content = content,
    )
}
