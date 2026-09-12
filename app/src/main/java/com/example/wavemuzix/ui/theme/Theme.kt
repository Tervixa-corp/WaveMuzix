package com.example.wavemuzix.ui.theme

import android.app.Activity
import android.os.Build
import android.util.DisplayMetrics
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = AccentPrimary,
    secondary = AccentSecondary,
    tertiary = Pink80,
    background = DeepBlack,
    surface = SoftBlack,
    onPrimary = TextPrimary,
    onSecondary = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariant
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun WaveMuzixTheme(
    accentColor: Color = AccentPrimary,
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme.copy(
        primary = accentColor
    )
    val view = LocalView.current
    
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false // false = light icons
        }
    }

    // Force the app to use a small font and display scale, ignoring system settings
    val context = LocalContext.current
    val stableDensity = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        // Use the device's hardware stable density instead of the user-adjusted one
        DisplayMetrics.DENSITY_DEVICE_STABLE / 160f
    } else {
        // Fallback for older versions: use standard density
        context.resources.displayMetrics.densityDpi / 160f
    }

    val forcedDensity = Density(
        density = stableDensity * 0.85f, // Force "Small" display size (85% of standard)
        fontScale = 0.85f // Force "Small" font size
    )

    CompositionLocalProvider(LocalDensity provides forcedDensity) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
