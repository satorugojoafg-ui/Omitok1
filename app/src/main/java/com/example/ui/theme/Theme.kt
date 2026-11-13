package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.i18n.AppLanguage

private val OmitokDarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = ObsidianBlack,
    primaryContainer = CyanMuted.copy(alpha = 0.22f),
    onPrimaryContainer = NeonCyan,
    secondary = ElectricMagenta,
    onSecondary = TextPrimary,
    secondaryContainer = MagentaDark.copy(alpha = 0.30f),
    onSecondaryContainer = ElectricMagenta,
    tertiary = CyberViolet,
    onTertiary = TextPrimary,
    background = ObsidianBlack,
    onBackground = TextPrimary,
    surface = DeepSpace,
    onSurface = TextPrimary,
    surfaceVariant = CardSlate,
    onSurfaceVariant = TextSecondary,
    outline = SubtleBorder,
    outlineVariant = SubtleBorder.copy(alpha = 0.5f),
    error = ErrorCoral,
    onError = ObsidianBlack,
    surfaceContainerLowest = ObsidianBlack,
    surfaceContainerLow = DeepSpace,
    surfaceContainer = CardSlate,
    surfaceContainerHigh = ColorTokens.SurfaceHigh,
)

private object ColorTokens {
    val SurfaceHigh = androidx.compose.ui.graphics.Color(0xFF1E263D)
}

val OmitokShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun OmitokTheme(
    language: AppLanguage = AppLanguage.FA,
    content: @Composable () -> Unit
) {
    val layoutDirection = if (language == AppLanguage.FA) {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        MaterialTheme(
            colorScheme = OmitokDarkColorScheme,
            typography = AppTypography,
            shapes = OmitokShapes,
            content = content
        )
    }
}
