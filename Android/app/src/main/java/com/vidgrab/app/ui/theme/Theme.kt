package com.vidgrab.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidgrab.app.core.Prefs

@Immutable
data class AuroraColors(
    val bg: Color, val surface: Color, val surfaceHigh: Color,
    val text: Color, val textDim: Color, val textFaint: Color,
    val glass: Color, val glassBorder: Color,
    val violet: Color, val cyan: Color, val magenta: Color,
    val success: Color, val danger: Color, val isDark: Boolean
) {
    val primary get() = violet
    val gradient get() = Brush.linearGradient(listOf(violet, cyan))
    val gradientWarm get() = Brush.linearGradient(listOf(magenta, violet))
    val gradientSoft get() = Brush.linearGradient(listOf(violet.copy(alpha = .16f), cyan.copy(alpha = .04f)))
}
private val Dark = AuroraColors(
    Color(0xFF0D0E14), Color(0xFF181921), Color(0xFF232532),
    Color(0xFFF6F5FC), Color(0xFFA9ABBF), Color(0xFF9093AB),
    Color(0xFF181921), Color(0xFF30323F),
    Color(0xFFAF9BFF), Color(0xFF8FBCFF), Color(0xFFF3B2DB),
    Color(0xFF82DDB6), Color(0xFFFFA0A7), true
)
private val Light = AuroraColors(
    Color(0xFFF4F3FA), Color.White, Color(0xFFECEAF5),
    Color(0xFF1C1B2B), Color(0xFF67667D), Color(0xFF77758B),
    Color.White, Color(0xFFDEDBEB),
    Color(0xFF7050DB), Color(0xFF5179CB), Color(0xFFB25491),
    Color(0xFF16835B), Color(0xFFBF3B51), false
)
val LocalAurora = staticCompositionLocalOf { Dark }
private val Typography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 35.sp, letterSpacing = (-1).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 25.sp, lineHeight = 30.sp, letterSpacing = (-.8).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 25.sp, letterSpacing = (-.5).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = (-.2).sp),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = .5.sp)
)
@Composable
fun VidGrabTheme(mode: Prefs.ThemeMode = Prefs.ThemeMode.System, content: @Composable () -> Unit) {
    val dark = when (mode) { Prefs.ThemeMode.Dark -> true; Prefs.ThemeMode.Light -> false; else -> isSystemInDarkTheme() }
    val a = if (dark) Dark else Light
    val scheme = if (dark) darkColorScheme(
        primary = a.primary, onPrimary = Color(0xFF211544), primaryContainer = a.surfaceHigh, onPrimaryContainer = a.text,
        secondary = a.cyan, onSecondary = Color(0xFF211544), secondaryContainer = a.surfaceHigh, onSecondaryContainer = a.primary,
        background = a.bg, onBackground = a.text, surface = a.surface, onSurface = a.text,
        surfaceVariant = a.surfaceHigh, onSurfaceVariant = a.textDim, outline = a.glassBorder, error = a.danger
    ) else lightColorScheme(
        primary = a.primary, onPrimary = Color.White, primaryContainer = Color(0xFFE8E0FF), onPrimaryContainer = a.text,
        secondary = a.cyan, secondaryContainer = Color(0xFFE8E0FF), onSecondaryContainer = a.primary,
        background = a.bg, onBackground = a.text, surface = a.surface, onSurface = a.text,
        surfaceVariant = a.surfaceHigh, onSurfaceVariant = a.textDim, outline = a.glassBorder, error = a.danger
    )
    CompositionLocalProvider(LocalAurora provides a) {
        MaterialTheme(colorScheme = scheme, typography = Typography,
            shapes = Shapes(extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(30.dp)), content = content)
    }
}
