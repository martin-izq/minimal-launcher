package com.martin.minimallauncher.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

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
)

private val LightColors = lightColorScheme(
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF111111),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFEFEFEF),
    onSurfaceVariant = Color(0xFF555555),
    primary = Color(0xFF111111),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF777777),
    outline = Color(0xFFDDDDDD),
)

private val MinimalTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Light, fontSize = 64.sp, letterSpacing = (-1).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 24.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 22.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 18.sp, letterSpacing = 0.2.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 13.sp, letterSpacing = 0.5.sp),
)

@Composable
fun MinimalLauncherTheme(
    amoledDark: Boolean = true,
    useSystem: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = if (useSystem) isSystemInDarkTheme() else amoledDark
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = MinimalTypography,
        content = content,
    )
}
