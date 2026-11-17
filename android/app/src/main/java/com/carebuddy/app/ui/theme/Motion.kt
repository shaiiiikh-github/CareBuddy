package com.carebuddy.app.ui.theme

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView

object MotionTokens {
    /**
     * Standard iOS snappy spring with 0.8 damping ratio and 400f stiffness.
     */
    fun <T> springDefault() = spring<T>(
        dampingRatio = 0.8f,
        stiffness = 400f
    )

    fun <T> springBouncy() = spring<T>(
        dampingRatio = 0.65f,
        stiffness = 380f
    )

    fun <T> springGentle() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
}

/**
 * Applies a subtle iOS tactile 0.97 scale press effect when the composable is touched.
 */
fun Modifier.pressScale(
    targetScale: Float = 0.97f,
    onClick: (() -> Unit)? = null
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) targetScale else 1f,
        animationSpec = MotionTokens.springDefault(),
        label = "press_scale"
    )
    val view = LocalView.current

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = {
                view.performHapticTick()
                onClick()
            }
        )
    } else Modifier

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(clickableModifier)
}

fun View.performHapticTick() {
    try {
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    } catch (_: Exception) {}
}

fun View.performHapticSuccess() {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    } catch (_: Exception) {}
}

fun View.performHapticWarning() {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            performHapticFeedback(HapticFeedbackConstants.REJECT)
        } else {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    } catch (_: Exception) {}
}
