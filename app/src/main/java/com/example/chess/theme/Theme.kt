package com.example.chess.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Bright, friendly colors used across the app. */
object Kid {
    val Purple = Color(0xFF6C5CE7)
    val PurpleDark = Color(0xFF4B3CC4)
    val Green = Color(0xFF00B894)
    val GreenDark = Color(0xFF00876C)
    val Orange = Color(0xFFFF9F43)
    val OrangeDark = Color(0xFFE07B12)
    val Pink = Color(0xFFFD79A8)
    val Blue = Color(0xFF0984E3)
    val Red = Color(0xFFE55353)
    val Gold = Color(0xFFFFC93C)
    val GoldDark = Color(0xFFE09A00)
    val Ink = Color(0xFF2D3047)
    val InkSoft = Color(0xFF5E6180)
    val Cream = Color(0xFFFFF4E0)
    val Sky = Color(0xFFE6F3FF)
    val Card = Color(0xFFFFFFFF)

    val background = Brush.verticalGradient(listOf(Sky, Cream))
}

private val ColorScheme = lightColorScheme(
    primary = Kid.Purple,
    onPrimary = Color.White,
    secondary = Kid.Green,
    onSecondary = Color.White,
    tertiary = Kid.Orange,
    onTertiary = Color.White,
    background = Kid.Cream,
    onBackground = Kid.Ink,
    surface = Kid.Card,
    onSurface = Kid.Ink,
    surfaceVariant = Color(0xFFF1ECFF),
    onSurfaceVariant = Kid.InkSoft,
    error = Kid.Red,
)

private val Rounded = FontFamily.SansSerif

private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Black, fontSize = 40.sp, lineHeight = 46.sp),
    headlineMedium = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, lineHeight = 22.sp),
    labelMedium = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 18.sp),
)

@Composable
fun ChessTheme(content: @Composable () -> Unit) {
    // Always light: the board colors and characters are designed for a bright look.
    MaterialTheme(colorScheme = ColorScheme, typography = AppTypography, content = content)
}
