package com.carebuddy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carebuddy.app.ui.theme.CareBuddyTheme
import com.carebuddy.app.ui.theme.pressScale

enum class ButtonStyle {
    Filled,
    Tinted,
    Plain,
    Danger
}

/**
 * iOS-styled full capsule button with spring press feedback.
 */
@Composable
fun CapsuleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.Filled,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    minHeight: Dp = 50.dp
) {
    val colors = CareBuddyTheme.colors

    val containerColor = when (style) {
        ButtonStyle.Filled -> if (enabled) colors.accent else colors.switchTrackOff
        ButtonStyle.Tinted -> if (enabled) colors.accentSubtle else colors.switchTrackOff
        ButtonStyle.Plain -> Color.Transparent
        ButtonStyle.Danger -> if (enabled) colors.danger else colors.switchTrackOff
    }

    val contentColor = when (style) {
        ButtonStyle.Filled -> Color.White
        ButtonStyle.Tinted -> if (enabled) colors.accent else colors.textTertiary
        ButtonStyle.Plain -> if (enabled) colors.accent else colors.textTertiary
        ButtonStyle.Danger -> Color.White
    }

    val shape = RoundedCornerShape(fullPillRadius)

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = minHeight)
            .clip(shape)
            .background(containerColor)
            .pressScale(targetScale = 0.96f, onClick = if (enabled) onClick else null)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = contentColor
            )
        }
    }
}
