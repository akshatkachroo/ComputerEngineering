package com.scribesync.scribesync.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = TealAccent,
    onPrimary = NavyDeep,
    primaryContainer = TealAccentDim,
    onPrimaryContainer = Color(0xFFDFFAF6),
    secondary = AmberAccent,
    onSecondary = NavyDeep,
    secondaryContainer = Color(0xFF3A3320),
    onSecondaryContainer = Color(0xFFF5E3BE),
    tertiary = NavyOutline,
    error = ErrorRed,
    onError = NavyDeep,
    errorContainer = ErrorRedDim,
    onErrorContainer = Color(0xFFFBE1DE),
    background = NavyDeep,
    onBackground = Color(0xFFE4E8EF),
    surface = NavySurface,
    onSurface = Color(0xFFE4E8EF),
    surfaceVariant = NavySurfaceVariant,
    onSurfaceVariant = Color(0xFFC2C9D6),
    outline = NavyOutline,
    outlineVariant = Color(0xFF3A4457)
)

private val LightColorScheme = lightColorScheme(
    primary = TealAccentLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFF2EE),
    onPrimaryContainer = Color(0xFF06342F),
    secondary = Color(0xFF8A6A1F),
    secondaryContainer = Color(0xFFF3E3BE),
    tertiary = CharcoalText,
    error = Color(0xFFB3261E),
    background = BackgroundLight,
    onBackground = CharcoalText,
    surface = SurfaceLight,
    onSurface = CharcoalText,
    outline = Color(0xFF6B7280)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * Dark-first, brand-colored theme. Dynamic (Material You) color is intentionally
 * not used here — the navy/teal identity is the point, not the device wallpaper.
 */
@Composable
fun ScribeSyncTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
