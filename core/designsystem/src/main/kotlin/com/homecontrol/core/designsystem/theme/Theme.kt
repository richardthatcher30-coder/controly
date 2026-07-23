package com.homecontrol.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SeaGreen = Color(0xFF7BDCB5)
private val DeepTeal = Color(0xFF0E3B36)
private val Amber = Color(0xFFE8B15C)
private val DarkSurface = Color(0xFF101418)
private val DarkSurfaceVariant = Color(0xFF1B2126)
private val LightSurface = Color(0xFFFAFDFB)

private val HomeControlDarkColorScheme = darkColorScheme(
    primary = SeaGreen,
    onPrimary = DeepTeal,
    secondary = Amber,
    onSecondary = Color(0xFF3A2A05),
    background = DarkSurface,
    onBackground = Color(0xFFE2E7E9),
    surface = DarkSurfaceVariant,
    onSurface = Color(0xFFE2E7E9),
    surfaceVariant = Color(0xFF262E33),
    onSurfaceVariant = Color(0xFFB8C2C6),
)

private val HomeControlLightColorScheme = lightColorScheme(
    primary = DeepTeal,
    onPrimary = Color.White,
    secondary = Amber,
    onSecondary = Color(0xFF3A2A05),
    background = LightSurface,
    onBackground = Color(0xFF161C1A),
    surface = Color.White,
    onSurface = Color(0xFF161C1A),
)

private val HomeControlTypography = Typography(
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
)

private val HomeControlShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
)

/**
 * The single Material 3 theme for HomeControl. Dark is the primary,
 * designed-for palette per the product spec; light is derived for
 * completeness rather than separately hand-tuned.
 */
@Composable
fun HomeControlTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) HomeControlDarkColorScheme else HomeControlLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = HomeControlTypography,
        shapes = HomeControlShapes,
        content = content,
    )
}
