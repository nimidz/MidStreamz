package com.midstreamz.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val PixelDarkColorScheme = darkColorScheme(
    primary = PixelPrimary,
    onPrimary = PixelOnPrimary,
    primaryContainer = PixelPrimaryContainer,
    onPrimaryContainer = PixelOnPrimaryContainer,
    secondary = PixelSecondary,
    onSecondary = PixelOnSecondary,
    secondaryContainer = PixelSecondaryContainer,
    onSecondaryContainer = PixelOnSecondaryContainer,
    tertiary = PixelTertiary,
    onTertiary = PixelOnTertiary,
    tertiaryContainer = PixelTertiaryContainer,
    onTertiaryContainer = PixelOnTertiaryContainer,
    error = PixelError,
    onError = PixelOnError,
    errorContainer = PixelErrorContainer,
    onErrorContainer = PixelOnErrorContainer,
    background = PixelBgDark,
    onBackground = PixelTextPrimary,
    surface = PixelSurface,
    onSurface = PixelTextPrimary,
    surfaceVariant = PixelSurfaceContainerHigh,
    onSurfaceVariant = PixelTextSecondary,
    outline = PixelOutline,
    outlineVariant = PixelOutlineVariant
)

@Composable
fun MidStreamzTheme(
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context)
    } else {
        PixelDarkColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
