package org.mountaineers.traillog.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Brand fallbacks when dynamic color is unavailable (API < 31)
private val BrandGreen = Color(0xFF1B5E20)
private val BrandOrange = Color(0xFFEF6C00)
private val BrandOnPrimary = Color.White

private val LightColors = lightColorScheme(
    primary = BrandGreen,
    onPrimary = BrandOnPrimary,
    secondary = BrandOrange,
    tertiary = Color(0xFF1565C0)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF81C784),
    onPrimary = Color(0xFF003300),
    secondary = Color(0xFFFFB74D),
    tertiary = Color(0xFF90CAF9)
)

@Composable
fun TrailLogTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
