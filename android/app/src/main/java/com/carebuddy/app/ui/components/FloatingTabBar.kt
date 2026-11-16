package com.carebuddy.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carebuddy.app.ui.theme.CareBuddyTheme
import com.carebuddy.app.ui.theme.MotionTokens
import com.carebuddy.app.ui.theme.performHapticTick

data class TabItem(
    val route: String,
    val title: String,
    val icon: ImageVector
)

/**
 * Floating glass capsule bottom navigation bar.
 * Selected item gets a pill highlight animated with spring, haptic tick on change.
 */
@Composable
fun FloatingTabBar(
    items: List<TabItem>,
    currentRoute: String?,
    onItemSelected: (TabItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = CareBuddyTheme.colors
    val view = LocalView.current
    val capsuleShape = RoundedCornerShape(fullPillRadius)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        // Floating Glass Surface Capsule
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = capsuleShape,
            backgroundColor = colors.glassCard.copy(alpha = if (colors.isDark) 0.85f else 0.92f),
            elevation = 10.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    val isSelected = currentRoute == item.route

                    val iconTint by animateColorAsState(
                        targetValue = if (isSelected) colors.accent else colors.textTertiary,
                        animationSpec = MotionTokens.springDefault(),
                        label = "tab_icon_tint"
                    )

                    val pillBackground by animateColorAsState(
                        targetValue = if (isSelected) colors.accentSubtle else Color.Transparent,
                        animationSpec = MotionTokens.springDefault(),
                        label = "tab_pill_bg"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(capsuleShape)
                            .background(pillBackground)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    if (!isSelected) {
                                        view.performHapticTick()
                                        onItemSelected(item)
                                    }
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.title,
                                tint = iconTint,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                ),
                                color = iconTint
                            )
                        }
                    }
                }
            }
        }
    }
}
