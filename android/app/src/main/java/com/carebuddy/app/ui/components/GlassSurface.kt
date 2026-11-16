package com.carebuddy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.carebuddy.app.ui.theme.CareBuddyTheme
import com.carebuddy.app.ui.theme.pressScale

/**
 * Core iOS 26 "Liquid Glass" Surface Component.
 * Translucent fill + 1dp vertical gradient hairline border + soft ambient elevation.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    backgroundColor: Color = CareBuddyTheme.colors.glassCard,
    borderBrush: Brush = Brush.verticalGradient(
        listOf(
            CareBuddyTheme.colors.glassBorderTop,
            CareBuddyTheme.colors.glassBorderBottom
        )
    ),
    borderWidth: Dp = 1.dp,
    elevation: Dp = if (CareBuddyTheme.colors.isDark) 0.dp else 4.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val clickModifier = if (onClick != null) {
        Modifier.pressScale(onClick = onClick)
    } else Modifier

    val shadowModifier = if (elevation > 0.dp) {
        Modifier.shadow(
            elevation = elevation,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = 0.05f),
            spotColor = Color.Black.copy(alpha = 0.08f)
        )
    } else Modifier

    Box(
        modifier = modifier
            .then(shadowModifier)
            .clip(shape)
            .border(borderWidth, borderBrush, shape)
            .background(backgroundColor, shape)
            .then(clickModifier)
    ) {
        content()
    }
}

/**
 * Standard GlassCard with 28dp continuous-corner styling and default content padding.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    backgroundColor: Color = CareBuddyTheme.colors.glassCard,
    contentPadding: Dp = 20.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    GlassSurface(
        modifier = modifier,
        shape = shape,
        backgroundColor = backgroundColor,
        onClick = onClick
    ) {
        Box(modifier = Modifier.padding(contentPadding)) {
            content()
        }
    }
}
