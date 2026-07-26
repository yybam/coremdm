package com.core.mdm.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppColors(
    val cyan: Color,
    val cyanDim: Color,
    val navy: Color,
    val navyLight: Color,
    val card: Color,
    val cardBorder: Color,
    val green: Color,
    val red: Color,
    val yellow: Color,
    val purple: Color,
    val orange: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val isDark: Boolean,
)

fun darkAppColors() = AppColors(
    cyan          = Color(0xFF4DD2F0),
    cyanDim       = Color(0xFF1E6880),
    navy          = Color(0xFF080814),
    navyLight     = Color(0xFF0F1028),
    card          = Color(0xFF141432),
    cardBorder    = Color(0xFF1E1E42),
    green         = Color(0xFF22C55E),
    red           = Color(0xFFEF4444),
    yellow        = Color(0xFFF59E0B),
    purple        = Color(0xFF8B5CF6),
    orange        = Color(0xFFF97316),
    textPrimary   = Color(0xFFD8E8FF),
    textSecondary = Color(0xFF667799),
    isDark        = true,
)

fun lightAppColors() = AppColors(
    cyan          = Color(0xFF0288D1),
    cyanDim       = Color(0xFF0367A6),
    navy          = Color(0xFFF0F4F8),
    navyLight     = Color(0xFFFFFFFF),
    card          = Color(0xFFFFFFFF),
    cardBorder    = Color(0xFFD0D8E8),
    green         = Color(0xFF16A34A),
    red           = Color(0xFFDC2626),
    yellow        = Color(0xFFD97706),
    purple        = Color(0xFF7C3AED),
    orange        = Color(0xFFEA580C),
    textPrimary   = Color(0xFF0D1B2A),
    textSecondary = Color(0xFF5A6A80),
    isDark        = false,
)

val LocalAppColors = compositionLocalOf { darkAppColors() }
