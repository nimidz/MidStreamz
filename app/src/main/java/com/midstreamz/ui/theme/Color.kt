package com.midstreamz.ui.theme

import androidx.compose.ui.graphics.Color

// ==========================================
// GOOGLE PIXEL OS (MATERIAL YOU / M3) PALETTE
// ==========================================

// Surface hierarchy (Deep, clean, layered tones)
val PixelBgDark = Color(0xFF0F141A)              // AMOLED deep base
val PixelSurface = Color(0xFF141920)             // Base surface
val PixelSurfaceContainerLowest = Color(0xFF0C1015)
val PixelSurfaceContainerLow = Color(0xFF181D25)
val PixelSurfaceContainer = Color(0xFF1D222B)    // Standard Card
val PixelSurfaceContainerHigh = Color(0xFF262C36)// Elevated Card / Input
val PixelSurfaceContainerHighest = Color(0xFF303642) // Highlights

// Outline
val PixelOutline = Color(0xFF434954)
val PixelOutlineVariant = Color(0xFF2E343E)

// Google Pixel Tonal Accents
val PixelPrimary = Color(0xFFA8C7FA)            // Pixel Blue 200 (Active pill, highlights)
val PixelOnPrimary = Color(0xFF083063)
val PixelPrimaryContainer = Color(0xFF234476)   // Chip/Card selected container
val PixelOnPrimaryContainer = Color(0xFFD3E3FD)

val PixelSecondary = Color(0xFF7BD99B)          // Pixel Sage/Mint Green (Success, online)
val PixelOnSecondary = Color(0xFF06391A)
val PixelSecondaryContainer = Color(0xFF1F512F)
val PixelOnSecondaryContainer = Color(0xFFA6F2BD)

val PixelTertiary = Color(0xFFFFB787)           // Pixel Warm Peach/Amber (Super Chat, Alerts)
val PixelOnTertiary = Color(0xFF4D2700)
val PixelTertiaryContainer = Color(0xFF6E3900)
val PixelOnTertiaryContainer = Color(0xFFFFDCC5)

val PixelError = Color(0xFFF2B8B5)              // Pixel Soft Rose/Red
val PixelOnError = Color(0xFF601410)
val PixelErrorContainer = Color(0xFF8C1D18)
val PixelOnErrorContainer = Color(0xFFF9DEDC)

// Typography (Crisp, High Contrast)
val PixelTextPrimary = Color(0xFFE2E7EE)        // 90% White
val PixelTextSecondary = Color(0xFFC2C7D0)      // 70% Neutral
val PixelTextMuted = Color(0xFF8C929D)          // 50% Slate

// Backward Compatibility Aliases for MidStreamz
val BgDark = PixelBgDark
val SurfaceDark = PixelSurfaceContainer
val SurfaceLightDark = PixelSurfaceContainerHigh
val BorderDark = PixelOutlineVariant

val PrimaryBlue = Color(0xFF4A89E8)             // Pixel Vibrant Blue
val PrimaryHover = Color(0xFF3B72C7)

val EmeraldSuccess = Color(0xFF34D399)          // Smooth Emerald
val AmberSuperChat = Color(0xFFF59E0B)
val RoseError = Color(0xFFF87171)

val TextPrimary = PixelTextPrimary
val TextSecondary = PixelTextSecondary
val TextMuted = PixelTextMuted
