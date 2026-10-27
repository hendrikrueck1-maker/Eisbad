package de.eisbad.app

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Farbthemen, passend zur PWA (style.css). */
data class ThemeOption(val id: String, val label: String)

val THEME_OPTIONS = listOf(
    ThemeOption("schiefer", "Schiefer"),
    ThemeOption("wald", "Wald"),
    ThemeOption("beere", "Beere"),
    ThemeOption("hell", "Hell"),
)

private fun darkTheme(
    bg: Long, card: Long, fg: Long, muted: Long, accent: Long, onAccent: Long,
): ColorScheme = darkColorScheme(
    primary = Color(accent),
    onPrimary = Color(onAccent),
    secondaryContainer = Color(accent),
    onSecondaryContainer = Color(onAccent),
    background = Color(bg),
    onBackground = Color(fg),
    surface = Color(bg),
    onSurface = Color(fg),
    surfaceVariant = Color(card),
    onSurfaceVariant = Color(fg),
    surfaceContainerHighest = Color(card),
    outline = Color(muted),
)

fun colorSchemeFor(id: String): ColorScheme = when (id) {
    "wald" -> darkTheme(0xFF101A16, 0xFF1A2923, 0xFFEAF3EC, 0xFF96AE9F, 0xFF7FD1A0, 0xFF08170F)
    "beere" -> darkTheme(0xFF17121F, 0xFF241C31, 0xFFF3EEF9, 0xFFAE9FC0, 0xFFD58CFF, 0xFF1D0A2B)
    "hell" -> lightColorScheme(
        primary = Color(0xFF0F7A6E),
        onPrimary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFF0F7A6E),
        onSecondaryContainer = Color(0xFFFFFFFF),
        background = Color(0xFFF5F2EC),
        onBackground = Color(0xFF1E2528),
        surface = Color(0xFFF5F2EC),
        onSurface = Color(0xFF1E2528),
        surfaceVariant = Color(0xFFFFFFFF),
        onSurfaceVariant = Color(0xFF1E2528),
        surfaceContainerHighest = Color(0xFFFFFFFF),
        outline = Color(0xFF5D6A70),
    )
    else -> darkTheme(0xFF15171A, 0xFF21252B, 0xFFF2EFE8, 0xFFA3A6A2, 0xFFF0B35A, 0xFF1B1406)
}
