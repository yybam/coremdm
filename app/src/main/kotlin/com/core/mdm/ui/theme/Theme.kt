package com.core.mdm.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val MdmDarkColorScheme = darkColorScheme(
    primary            = MdmCyan,
    onPrimary          = Color(0xFF001F2A),
    primaryContainer   = MdmCyanDim,
    onPrimaryContainer = MdmCyan,
    secondary          = MdmPurple,
    onSecondary        = Color(0xFF1A0040),
    background         = MdmNavy,
    onBackground       = MdmTextPrimary,
    surface            = MdmNavyLight,
    onSurface          = MdmTextPrimary,
    surfaceVariant     = MdmCard,
    onSurfaceVariant   = MdmTextSecondary,
    outline            = MdmCardBorder,
    error              = MdmRed,
    onError            = Color(0xFF410002),
)

private val MdmLightColorScheme = lightColorScheme(
    primary            = Color(0xFF0288D1),
    onPrimary          = Color.White,
    primaryContainer   = Color(0xFFE1F5FE),
    onPrimaryContainer = Color(0xFF004C6E),
    secondary          = Color(0xFF7C3AED),
    onSecondary        = Color.White,
    background         = Color(0xFFF0F4F8),
    onBackground       = Color(0xFF0D1B2A),
    surface            = Color(0xFFFFFFFF),
    onSurface          = Color(0xFF0D1B2A),
    surfaceVariant     = Color(0xFFFFFFFF),
    onSurfaceVariant   = Color(0xFF5A6A80),
    outline            = Color(0xFFD0D8E8),
    error              = Color(0xFFDC2626),
    onError            = Color.White,
)

@Composable
fun CoreMdmTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.DARK   -> true
        ThemeMode.LIGHT  -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val appColors    = if (isDark) darkAppColors() else lightAppColors()
    val colorScheme  = if (isDark) MdmDarkColorScheme else MdmLightColorScheme

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content     = content
        )
    }
}
