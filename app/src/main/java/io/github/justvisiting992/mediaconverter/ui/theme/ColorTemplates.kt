package io.github.justvisiting992.mediaconverter.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The app's color scheme templates, the way modern Android apps offer
 * them: Material You (dynamic, from the wallpaper) plus a set of fixed
 * palettes, each with a light and a dark scheme. Surfaces and
 * backgrounds stay at the Material baseline neutrals so every template
 * reads as the same app in a different color, not a different app.
 */
enum class ThemeTemplate(val id: String, val label: String) {
    DYNAMIC("dynamic", "Material You"),
    OCEAN("ocean", "Ocean"),
    FOREST("forest", "Forest"),
    SUNSET("sunset", "Sunset"),
    BERRY("berry", "Berry"),
    LAVENDER("lavender", "Lavender"),
    GRAPHITE("graphite", "Graphite");

    /** The fixed scheme for this template, or null for DYNAMIC. */
    fun scheme(dark: Boolean): ColorScheme? = when (this) {
        DYNAMIC -> null
        OCEAN -> if (dark) OceanDark else OceanLight
        FOREST -> if (dark) ForestDark else ForestLight
        SUNSET -> if (dark) SunsetDark else SunsetLight
        BERRY -> if (dark) BerryDark else BerryLight
        LAVENDER -> if (dark) LavenderDark else LavenderLight
        GRAPHITE -> if (dark) GraphiteDark else GraphiteLight
    }

    /** Primary/secondary/tertiary of the light scheme, for picker dots. */
    fun swatches(): List<Color> {
        val s = scheme(dark = false) ?: return emptyList()
        return listOf(s.primary, s.secondary, s.tertiary)
    }

    companion object {
        fun fromId(id: String?): ThemeTemplate =
            entries.firstOrNull { it.id == id } ?: DYNAMIC
    }
}

private val OceanLight = lightColorScheme(
    primary = Color(0xFF0B5563), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBEE9F2), onPrimaryContainer = Color(0xFF001F24),
    secondary = Color(0xFF4A6267), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCDE7EC), onSecondaryContainer = Color(0xFF051F23),
    tertiary = Color(0xFF475D92), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD9E2FF), onTertiaryContainer = Color(0xFF0B1B3C),
)
private val OceanDark = darkColorScheme(
    primary = Color(0xFF9FD3DE), onPrimary = Color(0xFF00363E),
    primaryContainer = Color(0xFF004B56), onPrimaryContainer = Color(0xFFBEE9F2),
    secondary = Color(0xFFB0CBD1), onSecondary = Color(0xFF1B3439),
    secondaryContainer = Color(0xFF334B50), onSecondaryContainer = Color(0xFFCDE7EC),
    tertiary = Color(0xFFB3C5F8), onTertiary = Color(0xFF1C2F57),
    tertiaryContainer = Color(0xFF334670), onTertiaryContainer = Color(0xFFD9E2FF),
)

private val ForestLight = lightColorScheme(
    primary = Color(0xFF2F5D33), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC9EBCB), onPrimaryContainer = Color(0xFF052109),
    secondary = Color(0xFF54634F), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD8E5D0), onSecondaryContainer = Color(0xFF121F10),
    tertiary = Color(0xFF38656B), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBCEBF0), onTertiaryContainer = Color(0xFF002023),
)
private val ForestDark = darkColorScheme(
    primary = Color(0xFF9ED49B), onPrimary = Color(0xFF0C3812),
    primaryContainer = Color(0xFF17501E), onPrimaryContainer = Color(0xFFC9EBCB),
    secondary = Color(0xFFBCCBB2), onSecondary = Color(0xFF263423),
    secondaryContainer = Color(0xFF3C4B39), onSecondaryContainer = Color(0xFFD8E5D0),
    tertiary = Color(0xFFA0CFD6), onTertiary = Color(0xFF00373D),
    tertiaryContainer = Color(0xFF1F4D53), onTertiaryContainer = Color(0xFFBCEBF0),
)

private val SunsetLight = lightColorScheme(
    primary = Color(0xFF9A4A00), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDCC3), onPrimaryContainer = Color(0xFF2F1400),
    secondary = Color(0xFF755845), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDBCA), onSecondaryContainer = Color(0xFF2B160A),
    tertiary = Color(0xFF5F6135), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE3E6AC), onTertiaryContainer = Color(0xFF1B1D03),
)
private val SunsetDark = darkColorScheme(
    primary = Color(0xFFFFB77C), onPrimary = Color(0xFF4D2600),
    primaryContainer = Color(0xFF743500), onPrimaryContainer = Color(0xFFFFDCC3),
    secondary = Color(0xFFE5BFA9), onSecondary = Color(0xFF422B1B),
    secondaryContainer = Color(0xFF5B4130), onSecondaryContainer = Color(0xFFFFDBCA),
    tertiary = Color(0xFFC7CA92), onTertiary = Color(0xFF30330F),
    tertiaryContainer = Color(0xFF47491F), onTertiaryContainer = Color(0xFFE3E6AC),
)

private val BerryLight = lightColorScheme(
    primary = Color(0xFF9E2B4E), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9E1), onPrimaryContainer = Color(0xFF3F0016),
    secondary = Color(0xFF74565E), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDAE2), onSecondaryContainer = Color(0xFF2B151B),
    tertiary = Color(0xFF7A4A2B), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCC6), onTertiaryContainer = Color(0xFF2E1500),
)
private val BerryDark = darkColorScheme(
    primary = Color(0xFFFFB1C4), onPrimary = Color(0xFF5E0F28),
    primaryContainer = Color(0xFF7E2943), onPrimaryContainer = Color(0xFFFFD9E1),
    secondary = Color(0xFFE2BDC5), onSecondary = Color(0xFF422931),
    secondaryContainer = Color(0xFF5B3F47), onSecondaryContainer = Color(0xFFFFDAE2),
    tertiary = Color(0xFFF0BD96), onTertiary = Color(0xFF4A280C),
    tertiaryContainer = Color(0xFF663E22), onTertiaryContainer = Color(0xFFFFDCC6),
)

private val LavenderLight = lightColorScheme(
    primary = Color(0xFF5B4B9E), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE4DFFF), onPrimaryContainer = Color(0xFF1B0B4A),
    secondary = Color(0xFF5F5A76), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE4E0F7), onSecondaryContainer = Color(0xFF1B192B),
    tertiary = Color(0xFF7A5267), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8E9), onTertiaryContainer = Color(0xFF30101F),
)
private val LavenderDark = darkColorScheme(
    primary = Color(0xFFC6BFFF), onPrimary = Color(0xFF2E2170),
    primaryContainer = Color(0xFF443687), onPrimaryContainer = Color(0xFFE4DFFF),
    secondary = Color(0xFFC8C2DC), onSecondary = Color(0xFF302C44),
    secondaryContainer = Color(0xFF47435B), onSecondaryContainer = Color(0xFFE4E0F7),
    tertiary = Color(0xFFECB8D0), onTertiary = Color(0xFF492334),
    tertiaryContainer = Color(0xFF623A4B), onTertiaryContainer = Color(0xFFFFD8E9),
)

private val GraphiteLight = lightColorScheme(
    primary = Color(0xFF44525E), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E4EF), onPrimaryContainer = Color(0xFF0B1D29),
    secondary = Color(0xFF55616A), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDAE4EC), onSecondaryContainer = Color(0xFF131D24),
    tertiary = Color(0xFF5A5E7A), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE0E0F9), onTertiaryContainer = Color(0xFF181A30),
)
private val GraphiteDark = darkColorScheme(
    primary = Color(0xFFAFC9DA), onPrimary = Color(0xFF14303F),
    primaryContainer = Color(0xFF2C4757), onPrimaryContainer = Color(0xFFCBE6F7),
    secondary = Color(0xFFBDC9D1), onSecondary = Color(0xFF28323A),
    secondaryContainer = Color(0xFF3F4C55), onSecondaryContainer = Color(0xFFDAE4EC),
    tertiary = Color(0xFFC3C4DD), onTertiary = Color(0xFF2C2E45),
    tertiaryContainer = Color(0xFF43455E), onTertiaryContainer = Color(0xFFE0E0F9),
)
