package com.codeisland.buddylite.ui.theme

import androidx.compose.ui.graphics.Color

object BuddyColors {
    // Surfaces
    val Background = Color(0xFF040406)
    val Surface = Color(0xFF0D0D12)
    val SurfaceVariant = Color(0xFF1A1A24)
    val SurfaceElevated = Color(0xFF16161F)

    // Text
    val OnSurface = Color.White
    val OnSurfaceDim = Color.White.copy(alpha = 0.58f)
    val OnSurfaceFaint = Color.White.copy(alpha = 0.42f)
    val OnSurfaceDisabled = Color.White.copy(alpha = 0.24f)

    // Status
    val StatusIdle = Color(0xFF8C96A8)
    val StatusActive = Color(0xFF4DD964)
    val StatusAttention = Color(0xFFFF8C00)
    val StatusQuestion = Color(0xFF409CFF)

    // Accent buttons
    val AccentGreen = Color(0xFF59E68B)
    val AccentBlue = Color(0xFF409CFF)
    val AccentOrange = Color(0xFFFF8C00)
    val AccentRed = Color(0xFFFF453A)

    // Dividers + strokes
    val Divider = Color.White.copy(alpha = 0.10f)
    val DividerStrong = Color.White.copy(alpha = 0.16f)
    val CardBorder = Color.White.copy(alpha = 0.08f)

    // Chips
    val ChipBackground = Color.White.copy(alpha = 0.07f)
    fun chipBackground(accent: Color) = accent.copy(alpha = 0.14f)
}
