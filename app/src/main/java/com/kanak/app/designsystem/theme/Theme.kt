package com.kanak.app.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = AmoledBackground,
    primaryContainer = EmeraldSoftBg,
    onPrimaryContainer = EmeraldPrimary,
    secondary = EmeraldSecondary,
    onSecondary = AmoledBackground,
    background = AmoledBackground,
    onBackground = AmoledTextPrimary,
    surface = AmoledPrimarySurface,
    onSurface = AmoledTextPrimary,
    surfaceVariant = AmoledSecondarySurface,
    onSurfaceVariant = AmoledTextSecondary,
    outline = AmoledCardBorder,
    outlineVariant = AmoledDivider,
    error = StatusError,
    onError = AmoledTextPrimary
)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldSecondary,
    onPrimary = LightSurface,
    primaryContainer = Color(0xFFE6F7F2),
    onPrimaryContainer = EmeraldSecondary,
    secondary = EmeraldPrimary,
    onSecondary = LightSurface,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    outlineVariant = Color(0xFFE2E8F0),
    error = StatusError,
    onError = LightSurface
)

@Composable
fun KanakAppTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = KanakTypography,
        content = content
    )
}
