package com.sally.launcher.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Skema statis (dipakai bila warna dinamis mati atau Android < 12). Seed: hijau zamrud.
internal val LightColors = lightColorScheme(
    primary = Color(0xFF1F6B4C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA8F2C8),
    onPrimaryContainer = Color(0xFF002114),
    secondary = Color(0xFF4C6355),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCEE9D6),
    onSecondaryContainer = Color(0xFF092016),
    tertiary = Color(0xFF3B6470),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBFE9F8),
    onTertiaryContainer = Color(0xFF001F28),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF5FBF6),
    onBackground = Color(0xFF171D19),
    surface = Color(0xFFF5FBF6),
    onSurface = Color(0xFF171D19),
    surfaceVariant = Color(0xFFDBE5DC),
    onSurfaceVariant = Color(0xFF404943),
    surfaceContainer = Color(0xFFE9F0EA),
    surfaceContainerHigh = Color(0xFFE3EAE4),
    outline = Color(0xFF707972),
    outlineVariant = Color(0xFFBFC9C1),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFF8CD5AD),
    onPrimary = Color(0xFF00391F),
    primaryContainer = Color(0xFF005233),
    onPrimaryContainer = Color(0xFFA8F2C8),
    secondary = Color(0xFFB3CCBB),
    onSecondary = Color(0xFF1E352A),
    secondaryContainer = Color(0xFF354B3F),
    onSecondaryContainer = Color(0xFFCEE9D6),
    tertiary = Color(0xFFA3CDDC),
    onTertiary = Color(0xFF043542),
    tertiaryContainer = Color(0xFF214C59),
    onTertiaryContainer = Color(0xFFBFE9F8),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1511),
    onBackground = Color(0xFFDEE4DD),
    surface = Color(0xFF0F1511),
    onSurface = Color(0xFFDEE4DD),
    surfaceVariant = Color(0xFF404943),
    onSurfaceVariant = Color(0xFFBFC9C1),
    surfaceContainer = Color(0xFF1B211D),
    surfaceContainerHigh = Color(0xFF252B27),
    outline = Color(0xFF8A938C),
    outlineVariant = Color(0xFF404943),
)
