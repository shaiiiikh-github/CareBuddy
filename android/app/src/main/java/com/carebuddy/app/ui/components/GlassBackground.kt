package com.carebuddy.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.carebuddy.app.ui.theme.CareBuddyTheme

/**
 * Renders the iOS 26 Liquid Glass backdrop:
 * A calm, low-contrast solid background augmented with 2-3 soft, diffuse ambient color blobs.
 * Completely subtle in light mode, deep obsidian in dark mode.
 */
@Composable
fun GlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = CareBuddyTheme.colors
    val isDark = colors.isDark

    val blob1Color = if (isDark) colors.accent.copy(alpha = 0.08f) else colors.accent.copy(alpha = 0.045f)
    val blob2Color = if (isDark) colors.success.copy(alpha = 0.06f) else Color(0xFFFF9500).copy(alpha = 0.035f)
    val blob3Color = if (isDark) Color(0xFF6B3CE9).copy(alpha = 0.05f) else colors.accent.copy(alpha = 0.03f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Blob 1: Top-right ambient glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(blob1Color, Color.Transparent),
                    center = Offset(width * 0.85f, height * 0.15f),
                    radius = width * 0.8f
                ),
                radius = width * 0.8f,
                center = Offset(width * 0.85f, height * 0.15f)
            )

            // Blob 2: Mid-left ambient glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(blob2Color, Color.Transparent),
                    center = Offset(width * 0.1f, height * 0.55f),
                    radius = width * 0.75f
                ),
                radius = width * 0.75f,
                center = Offset(width * 0.1f, height * 0.55f)
            )

            // Blob 3: Bottom ambient glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(blob3Color, Color.Transparent),
                    center = Offset(width * 0.6f, height * 0.9f),
                    radius = width * 0.7f
                ),
                radius = width * 0.7f,
                center = Offset(width * 0.6f, height * 0.9f)
            )
        }

        content()
    }
}
