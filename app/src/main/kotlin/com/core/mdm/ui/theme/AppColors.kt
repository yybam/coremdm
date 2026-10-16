package com.core.mdm.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Palette mirrors the website's tokens (mdm site/style.css) so the app and the web console
 * look like one product. `card`/`cardBorder` are translucent "glass" over the glow background;
 * `panel` is the opaque surface for dialogs, snackbars and anything that floats above content.
 */
data class AppColors(
    val cyan: Color,
    val cyanDim: Color,
    val navy: Color,
    val navyLight: Color,
    val card: Color,
    val cardBorder: Color,
    val panel: Color,
    val green: Color,
    val red: Color,
    val yellow: Color,
    val purple: Color,
    val orange: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val accent2: Color,
    val gradStart: Color,
    val gradEnd: Color,
    val isDark: Boolean,
)

fun darkAppColors() = AppColors(
    cyan          = Color(0xFF7FD0F5),   // site link / console --cyan
    cyanDim       = Color(0x4D2E9AE6),   // accent @ 30% — tinted buttons & borders
    navy          = Color(0xFF070A14),   // --bg
    navyLight     = Color(0xB30A101E),   // .nav glass bar
    card          = Color(0x12FFFFFF),   // --glass (~7% white)
    cardBorder    = Color(0x1FFFFFFF),   // --line (12% white)
    panel         = Color(0xFF131B2E),   // --panel
    green         = Color(0xFF4ADE9B),   // --ok
    red           = Color(0xFFFF6B7D),   // --bad
    yellow        = Color(0xFFFFC15E),   // --warn
    purple        = Color(0xFF7FE0D0),   // site icon stroke (teal)
    orange        = Color(0xFFFFA36B),
    textPrimary   = Color(0xFFEEF1F8),   // --text
    textSecondary = Color(0x9EE2E8F5),   // --muted
    accent        = Color(0xFF2E9AE6),
    accent2       = Color(0xFF2FD39A),
    gradStart     = Color(0xFF1F7FD6),
    gradEnd       = Color(0xFF1FB58F),
    isDark        = true,
)

fun lightAppColors() = AppColors(
    cyan          = Color(0xFF1F7FD6),
    cyanDim       = Color(0x331F7FD6),
    navy          = Color(0xFFF1F5FA),
    navyLight     = Color(0xCCFFFFFF),
    card          = Color(0xB3FFFFFF),
    cardBorder    = Color(0x1A0D1B2A),
    panel         = Color(0xFFFFFFFF),
    green         = Color(0xFF16A36A),
    red           = Color(0xFFE5485C),
    yellow        = Color(0xFFC98A12),
    purple        = Color(0xFF14A38B),
    orange        = Color(0xFFE0692E),
    textPrimary   = Color(0xFF0D1B2A),
    textSecondary = Color(0xFF5A6A80),
    accent        = Color(0xFF1F7FD6),
    accent2       = Color(0xFF1FB58F),
    gradStart     = Color(0xFF1F7FD6),
    gradEnd       = Color(0xFF1FB58F),
    isDark        = false,
)

val LocalAppColors = compositionLocalOf { darkAppColors() }
