package com.carebuddy.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * CareBuddy iOS 26 "Liquid Glass" Design System Color Tokens.
 * Strict semantic naming; no raw hex in UI screens.
 */
data class CareBuddyColors(
    val isDark: Boolean,
    // Backgrounds
    val background: Color,
    val backgroundSecondary: Color,
    // Glass Surfaces
    val glassSurface: Color,
    val glassSurfaceSubtle: Color,
    val glassCard: Color,
    val glassBorderTop: Color,
    val glassBorderBottom: Color,
    val glassHighlight: Color,
    // Typography & Labels
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val separator: Color,
    // Semantic Accent Tints
    val accent: Color,
    val accentSubtle: Color,
    val success: Color,
    val successSubtle: Color,
    val warning: Color,
    val warningSubtle: Color,
    val danger: Color,
    val dangerSubtle: Color,
    val tintGray: Color,
    val switchTrackOff: Color
)

val LightCareBuddyColors = CareBuddyColors(
    isDark = false,
    background = Color(0xFFF2F2F7),
    backgroundSecondary = Color(0xFFE5E5EA),
    glassSurface = Color.White.copy(alpha = 0.68f),
    glassSurfaceSubtle = Color.White.copy(alpha = 0.45f),
    glassCard = Color.White.copy(alpha = 0.72f),
    glassBorderTop = Color.White.copy(alpha = 0.65f),
    glassBorderBottom = Color.White.copy(alpha = 0.20f),
    glassHighlight = Color.White.copy(alpha = 0.85f),
    textPrimary = Color(0xFF1C1C1E),
    textSecondary = Color(0x993C3C43), // 60%
    textTertiary = Color(0x4D3C3C43),  // 30%
    separator = Color(0x1F000000),     // 12%
    accent = Color(0xFF007AFF),
    accentSubtle = Color(0x1A007AFF),
    success = Color(0xFF34C759),
    successSubtle = Color(0x1A34C759),
    warning = Color(0xFFFF9500),
    warningSubtle = Color(0x1AFF9500),
    danger = Color(0xFFFF3B30),
    dangerSubtle = Color(0x1AFF3B30),
    tintGray = Color(0xFF8E8E93),
    switchTrackOff = Color(0xFFE9E9EA)
)

val DarkCareBuddyColors = CareBuddyColors(
    isDark = true,
    background = Color(0xFF0B0B10),
    backgroundSecondary = Color(0xFF16161E),
    glassSurface = Color.White.copy(alpha = 0.10f),
    glassSurfaceSubtle = Color.White.copy(alpha = 0.06f),
    glassCard = Color.White.copy(alpha = 0.12f),
    glassBorderTop = Color.White.copy(alpha = 0.24f),
    glassBorderBottom = Color.White.copy(alpha = 0.06f),
    glassHighlight = Color.White.copy(alpha = 0.15f),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0x99FFFFFF), // 60%
    textTertiary = Color(0x4DFFFFFF),  // 30%
    separator = Color(0x26FFFFFF),     // 15%
    accent = Color(0xFF0A84FF),
    accentSubtle = Color(0x260A84FF),
    success = Color(0xFF30D158),
    successSubtle = Color(0x2630D158),
    warning = Color(0xFFFF9F0A),
    warningSubtle = Color(0x26FF9F0A),
    danger = Color(0xFFFF453A),
    dangerSubtle = Color(0x26FF453A),
    tintGray = Color(0xFF8E8E93),
    switchTrackOff = Color(0xFF39393D)
)

val LocalCareBuddyColors = staticCompositionLocalOf { LightCareBuddyColors }

// Backward compatibility bridge for old code while transitioning screens
val SlateDark = Color(0xFF0B0B10)
val SlateCard = Color(0xFF1A1A24)
val SlateCardBorder = Color(0x26FFFFFF)
val TealAccent = Color(0xFF0A84FF)
val TealLight = Color(0xFF0A84FF)
val TealGlow = Color(0xFF30D158)
val AmberWarning = Color(0xFFFF9F0A)
val RedEmergency = Color(0xFFFF453A)
val RedEmergencyDark = Color(0xFF801A18)
val GreenSuccess = Color(0xFF30D158)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0x99FFFFFF)
val TextMuted = Color(0x66FFFFFF)
