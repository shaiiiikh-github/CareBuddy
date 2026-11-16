package com.carebuddy.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = LightCareBuddyColors.accent,
    onPrimary = Color.White,
    secondary = LightCareBuddyColors.success,
    background = LightCareBuddyColors.background,
    surface = LightCareBuddyColors.glassCard,
    onBackground = LightCareBuddyColors.textPrimary,
    onSurface = LightCareBuddyColors.textPrimary,
    outline = LightCareBuddyColors.separator,
    error = LightCareBuddyColors.danger
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkCareBuddyColors.accent,
    onPrimary = Color.White,
    secondary = DarkCareBuddyColors.success,
    background = DarkCareBuddyColors.background,
    surface = DarkCareBuddyColors.glassCard,
    onBackground = DarkCareBuddyColors.textPrimary,
    onSurface = DarkCareBuddyColors.textPrimary,
    outline = DarkCareBuddyColors.separator,
    error = DarkCareBuddyColors.danger
)

object CareBuddyTheme {
    val colors: CareBuddyColors
        @Composable
        @ReadOnlyComposable
        get() = LocalCareBuddyColors.current
}

@Composable
fun CareBuddyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val buddyColors = if (darkTheme) DarkCareBuddyColors else LightCareBuddyColors
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalCareBuddyColors provides buddyColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
