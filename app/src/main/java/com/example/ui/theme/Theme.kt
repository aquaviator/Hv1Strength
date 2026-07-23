package com.example.ui.theme

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

val HumanDarkColorScheme = darkColorScheme(
    primary = HumanElectricBlue,
    onPrimary = Color.White,
    primaryContainer = HumanElectricBlueContainer,
    onPrimaryContainer = Color.White,
    secondary = HumanElectricBluePressed,
    onSecondary = Color.White,
    tertiary = HumanElectricBlueMuted,
    onTertiary = Color.White,
    background = HumanDarkBackground,
    onBackground = HumanDarkOnBackground,
    surface = HumanDarkSurface,
    onSurface = HumanDarkOnSurface,
    surfaceVariant = HumanDarkSurfaceElevated,
    onSurfaceVariant = HumanDarkOnSurfaceVariant,
    outline = HumanDarkOutline,
    error = HumanError,
    onError = Color.White,
    errorContainer = HumanErrorContainer,
    onErrorContainer = Color.White
)

val HumanLightColorScheme = lightColorScheme(
    primary = HumanElectricBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0EBFF),
    onPrimaryContainer = HumanElectricBlueMuted,
    secondary = HumanElectricBluePressed,
    onSecondary = Color.White,
    tertiary = Color(0xFFD6E4FF),
    onTertiary = HumanElectricBlueMuted,
    background = HumanLightBackground,
    onBackground = HumanLightOnBackground,
    surface = HumanLightSurface,
    onSurface = HumanLightOnSurface,
    surfaceVariant = HumanLightSurfaceElevated,
    onSurfaceVariant = HumanLightOnSurfaceVariant,
    outline = HumanLightOutline,
    error = HumanError,
    onError = Color.White
)

@Composable
fun HumanV1Theme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> HumanDarkColorScheme
        else -> HumanLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

/**
 * Legacy theme entry point forwarding directly to HumanV1Theme.
 */
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    HumanV1Theme(
        darkTheme = darkTheme,
        dynamicColor = dynamicColor,
        content = content
    )
}
