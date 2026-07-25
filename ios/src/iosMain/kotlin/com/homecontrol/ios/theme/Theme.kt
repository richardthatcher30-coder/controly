package com.homecontrol.ios.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Standalone iOS copy of Android's `core:designsystem` `HomeControlTheme` —
 * that module is Android-only (uses AndroidX Compose, not Compose
 * Multiplatform) and unreachable from `:ios`, so the same color/typography/
 * shape values are hand-copied here rather than shared, to keep the two
 * apps looking consistent until `core:designsystem` is itself ported to KMP.
 * Dark is the primary, designed-for palette per the original file's own
 * comment; light is hand-tuned alongside it, not just derived.
 */
private val DarkColors = darkColorScheme(
    primary = Color(0xFF89D5C7),
    onPrimary = Color(0xFF00372F),
    primaryContainer = Color(0xFF005046),
    onPrimaryContainer = Color(0xFFA6F2E4),
    secondary = Color(0xFFB0CCC7),
    onSecondary = Color(0xFF1B3532),
    secondaryContainer = Color(0xFF324B48),
    onSecondaryContainer = Color(0xFFCCE8E3),
    tertiary = Color(0xFFFFB951),
    onTertiary = Color(0xFF452B00),
    tertiaryContainer = Color(0xFF633F00),
    onTertiaryContainer = Color(0xFFFFDDA6),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E1513),
    onBackground = Color(0xFFDDE4E1),
    surface = Color(0xFF0E1513),
    onSurface = Color(0xFFDDE4E1),
    surfaceVariant = Color(0xFF3F4946),
    onSurfaceVariant = Color(0xFFBEC9C5),
    outline = Color(0xFF889390),
    outlineVariant = Color(0xFF3F4946),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0D6B5F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA6F2E4),
    onPrimaryContainer = Color(0xFF00201B),
    secondary = Color(0xFF4A6360),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E3),
    onSecondaryContainer = Color(0xFF051F1C),
    tertiary = Color(0xFF8A5A00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDA6),
    onTertiaryContainer = Color(0xFF2B1700),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFAFDFB),
    onBackground = Color(0xFF161D1B),
    surface = Color(0xFFFAFDFB),
    onSurface = Color(0xFF161D1B),
    surfaceVariant = Color(0xFFDAE5E1),
    onSurfaceVariant = Color(0xFF3F4946),
    outline = Color(0xFF6F7976),
    outlineVariant = Color(0xFFBEC9C5),
)

private val ControlyTypography = Typography().let { base ->
    base.copy(
        headlineMedium = base.headlineMedium.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
            lineHeight = 34.sp,
            letterSpacing = (-0.3).sp,
        ),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp),
        bodyLarge = base.bodyLarge.copy(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    )
}

private val ControlyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun ControlyTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = ControlyTypography,
        shapes = ControlyShapes,
        content = content,
    )
}
