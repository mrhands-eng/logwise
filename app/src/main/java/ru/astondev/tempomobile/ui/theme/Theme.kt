package ru.astondev.tempomobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import ru.astondev.tempomobile.domain.AppPalette
import ru.astondev.tempomobile.domain.ThemeMode

private data class PaletteColors(
    val light: ColorScheme,
    val dark: ColorScheme,
)

private fun forest(): PaletteColors {
    val green = Color(0xFF1B5E4B)
    val ink = Color(0xFF14201C)
    return PaletteColors(
        light = lightColorScheme(
            primary = green,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFC8E6D8),
            onPrimaryContainer = ink,
            secondary = Color(0xFF2E8B6E),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFD4F0E4),
            onSecondaryContainer = ink,
            tertiary = Color(0xFF4A6741),
            onTertiary = Color.White,
            background = Color(0xFFF3F0E8),
            onBackground = ink,
            surface = Color.White,
            onSurface = ink,
            surfaceVariant = Color(0xFFE6E2D6),
            onSurfaceVariant = Color(0xFF3D4A44),
            outline = Color(0xFF6B7A72),
            error = Color(0xFFB3261E),
            onError = Color.White,
            errorContainer = Color(0xFFF9DEDC),
            onErrorContainer = Color(0xFF410E0B),
        ),
        dark = darkColorScheme(
            primary = Color(0xFF7DCFB0),
            onPrimary = Color(0xFF003828),
            primaryContainer = Color(0xFF0F4636),
            onPrimaryContainer = Color(0xFFB8F0D8),
            secondary = Color(0xFF8FD4BA),
            onSecondary = Color(0xFF003828),
            secondaryContainer = Color(0xFF1A4034),
            onSecondaryContainer = Color(0xFFB8F0D8),
            tertiary = Color(0xFFA8C89A),
            onTertiary = Color(0xFF1A2E14),
            background = Color(0xFF101614),
            onBackground = Color(0xFFE6EDE9),
            surface = Color(0xFF18201C),
            onSurface = Color(0xFFE6EDE9),
            surfaceVariant = Color(0xFF2A3430),
            onSurfaceVariant = Color(0xFFB8C4BE),
            outline = Color(0xFF84908A),
            error = Color(0xFFF2B8B5),
            onError = Color(0xFF601410),
            errorContainer = Color(0xFF8C1D18),
            onErrorContainer = Color(0xFFF9DEDC),
        ),
    )
}

private fun ocean(): PaletteColors {
    val blue = Color(0xFF0B5F8A)
    val ink = Color(0xFF0E1A24)
    return PaletteColors(
        light = lightColorScheme(
            primary = blue,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFC9E7F7),
            onPrimaryContainer = ink,
            secondary = Color(0xFF1A7FA8),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFD4EEF8),
            onSecondaryContainer = ink,
            tertiary = Color(0xFF3D6B8A),
            onTertiary = Color.White,
            background = Color(0xFFEEF5F9),
            onBackground = ink,
            surface = Color.White,
            onSurface = ink,
            surfaceVariant = Color(0xFFD7E6EF),
            onSurfaceVariant = Color(0xFF3A4E5C),
            outline = Color(0xFF6A7E8C),
            error = Color(0xFFB3261E),
            onError = Color.White,
            errorContainer = Color(0xFFF9DEDC),
            onErrorContainer = Color(0xFF410E0B),
        ),
        dark = darkColorScheme(
            primary = Color(0xFF7CC8EF),
            onPrimary = Color(0xFF00344D),
            primaryContainer = Color(0xFF0A3F5C),
            onPrimaryContainer = Color(0xFFC9E7F7),
            secondary = Color(0xFF8FD0E8),
            onSecondary = Color(0xFF00344D),
            secondaryContainer = Color(0xFF1A3A4C),
            onSecondaryContainer = Color(0xFFC9E7F7),
            tertiary = Color(0xFFA0C4D8),
            onTertiary = Color(0xFF0E2A38),
            background = Color(0xFF0C141A),
            onBackground = Color(0xFFE4EEF4),
            surface = Color(0xFF152029),
            onSurface = Color(0xFFE4EEF4),
            surfaceVariant = Color(0xFF253440),
            onSurfaceVariant = Color(0xFFB4C4D0),
            outline = Color(0xFF8494A0),
            error = Color(0xFFF2B8B5),
            onError = Color(0xFF601410),
            errorContainer = Color(0xFF8C1D18),
            onErrorContainer = Color(0xFFF9DEDC),
        ),
    )
}

private fun slate(): PaletteColors {
    val slate = Color(0xFF3D4A5C)
    val ink = Color(0xFF1A1F27)
    return PaletteColors(
        light = lightColorScheme(
            primary = slate,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFD8DEE8),
            onPrimaryContainer = ink,
            secondary = Color(0xFF5A6A7E),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFE2E8F0),
            onSecondaryContainer = ink,
            tertiary = Color(0xFF6B5A4A),
            onTertiary = Color.White,
            background = Color(0xFFF2F3F5),
            onBackground = ink,
            surface = Color.White,
            onSurface = ink,
            surfaceVariant = Color(0xFFE2E5EA),
            onSurfaceVariant = Color(0xFF4A5563),
            outline = Color(0xFF6E7885),
            error = Color(0xFFB3261E),
            onError = Color.White,
            errorContainer = Color(0xFFF9DEDC),
            onErrorContainer = Color(0xFF410E0B),
        ),
        dark = darkColorScheme(
            primary = Color(0xFFB0BCCE),
            onPrimary = Color(0xFF233041),
            primaryContainer = Color(0xFF2C3848),
            onPrimaryContainer = Color(0xFFD8DEE8),
            secondary = Color(0xFFA8B4C4),
            onSecondary = Color(0xFF233041),
            secondaryContainer = Color(0xFF343E4C),
            onSecondaryContainer = Color(0xFFD8DEE8),
            tertiary = Color(0xFFC4B4A4),
            onTertiary = Color(0xFF2E241A),
            background = Color(0xFF11151A),
            onBackground = Color(0xFFE8EBEE),
            surface = Color(0xFF1A2028),
            onSurface = Color(0xFFE8EBEE),
            surfaceVariant = Color(0xFF2C343E),
            onSurfaceVariant = Color(0xFFB8C0CA),
            outline = Color(0xFF8A929C),
            error = Color(0xFFF2B8B5),
            onError = Color(0xFF601410),
            errorContainer = Color(0xFF8C1D18),
            onErrorContainer = Color(0xFFF9DEDC),
        ),
    )
}

private fun ember(): PaletteColors {
    val ember = Color(0xFFA8431E)
    val ink = Color(0xFF24140E)
    return PaletteColors(
        light = lightColorScheme(
            primary = ember,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFF3D5C6),
            onPrimaryContainer = ink,
            secondary = Color(0xFFC45E2F),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFF8E0D4),
            onSecondaryContainer = ink,
            tertiary = Color(0xFF8A6A3A),
            onTertiary = Color.White,
            background = Color(0xFFF7F1EC),
            onBackground = ink,
            surface = Color.White,
            onSurface = ink,
            surfaceVariant = Color(0xFFE9DED6),
            onSurfaceVariant = Color(0xFF5A463C),
            outline = Color(0xFF8A7468),
            error = Color(0xFFB3261E),
            onError = Color.White,
            errorContainer = Color(0xFFF9DEDC),
            onErrorContainer = Color(0xFF410E0B),
        ),
        dark = darkColorScheme(
            primary = Color(0xFFE8A07A),
            onPrimary = Color(0xFF4A1E0C),
            primaryContainer = Color(0xFF6B2E14),
            onPrimaryContainer = Color(0xFFF3D5C6),
            secondary = Color(0xFFE8B08C),
            onSecondary = Color(0xFF4A1E0C),
            secondaryContainer = Color(0xFF5A3020),
            onSecondaryContainer = Color(0xFFF3D5C6),
            tertiary = Color(0xFFD4C09A),
            onTertiary = Color(0xFF3A2E14),
            background = Color(0xFF16100D),
            onBackground = Color(0xFFF0E6E0),
            surface = Color(0xFF221812),
            onSurface = Color(0xFFF0E6E0),
            surfaceVariant = Color(0xFF3A2E26),
            onSurfaceVariant = Color(0xFFC8B8AE),
            outline = Color(0xFF9A8A80),
            error = Color(0xFFF2B8B5),
            onError = Color(0xFF601410),
            errorContainer = Color(0xFF8C1D18),
            onErrorContainer = Color(0xFFF9DEDC),
        ),
    )
}

private fun colorsFor(palette: AppPalette): PaletteColors = when (palette) {
    AppPalette.Forest -> forest()
    AppPalette.Ocean -> ocean()
    AppPalette.Slate -> slate()
    AppPalette.Ember -> ember()
}

@Composable
fun TempoTheme(
    themeMode: ThemeMode = ThemeMode.System,
    palette: AppPalette = AppPalette.Forest,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val colors = colorsFor(palette)
    MaterialTheme(
        colorScheme = if (dark) colors.dark else colors.light,
        typography = TempoTypography,
        content = content,
    )
}
