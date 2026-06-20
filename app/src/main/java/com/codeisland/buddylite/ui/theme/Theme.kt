package com.codeisland.buddylite.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkBuddyScheme = darkColorScheme(
    primary = BuddyColors.AccentBlue,
    secondary = BuddyColors.AccentGreen,
    tertiary = BuddyColors.AccentOrange,
    background = BuddyColors.Background,
    surface = BuddyColors.Surface,
    surfaceVariant = BuddyColors.SurfaceVariant,
    onPrimary = BuddyColors.OnSurface,
    onSecondary = BuddyColors.OnSurface,
    onTertiary = BuddyColors.OnSurface,
    onBackground = BuddyColors.OnSurface,
    onSurface = BuddyColors.OnSurface,
    onSurfaceVariant = BuddyColors.OnSurfaceDim,
    outline = BuddyColors.DividerStrong,
    outlineVariant = BuddyColors.Divider,
    error = BuddyColors.AccentRed
)

@Composable
fun BuddyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkBuddyScheme,
        typography = BuddyType,
        content = content
    )
}
