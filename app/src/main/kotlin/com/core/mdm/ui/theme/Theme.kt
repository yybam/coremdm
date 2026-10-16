package com.core.mdm.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val MdmDarkColorScheme = darkColorScheme(
    primary            = Color(0xFF2E9AE6),
    onPrimary          = Color.White,
    primaryContainer   = MdmCyanDim,
    onPrimaryContainer = MdmCyan,
    secondary          = Color(0xFF2FD39A),
    onSecondary        = Color(0xFF062015),
    background         = MdmNavy,
    onBackground       = MdmTextPrimary,
    surface            = MdmCard,
    onSurface          = MdmTextPrimary,
    surfaceVariant     = MdmCard,
    onSurfaceVariant   = MdmTextSecondary,
    outline            = MdmCardBorder,
    error              = MdmRed,
    onError            = Color(0xFF2A0008),
)

private val MdmLightColorScheme = lightColorScheme(
    primary            = Color(0xFF1F7FD6),
    onPrimary          = Color.White,
    primaryContainer   = Color(0xFFDCEEFB),
    onPrimaryContainer = Color(0xFF0B3E6A),
    secondary          = Color(0xFF1FB58F),
    onSecondary        = Color.White,
    background         = Color(0xFFF1F5FA),
    onBackground       = Color(0xFF0D1B2A),
    surface            = Color(0xFFFFFFFF),
    onSurface          = Color(0xFF0D1B2A),
    surfaceVariant     = Color(0xFFFFFFFF),
    onSurfaceVariant   = Color(0xFF5A6A80),
    outline            = Color(0xFFD0D8E8),
    error              = Color(0xFFE5485C),
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
