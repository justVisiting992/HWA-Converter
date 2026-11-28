package io.github.justvisiting992.mediaconverter.ui.theme

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Light/dark choice, independent of which color template is active. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * The user's appearance choices, persisted in SharedPreferences, two
 * small values don't justify a database or a DataStore dependency.
 */
object ThemePrefs {
    private const val FILE = "appearance"
    private const val KEY_TEMPLATE = "color_template"
    private const val KEY_MODE = "theme_mode"

    fun load(context: Context): Pair<ThemeTemplate, ThemeMode> {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val template = ThemeTemplate.fromId(prefs.getString(KEY_TEMPLATE, null))
        val mode = runCatching {
            ThemeMode.valueOf(prefs.getString(KEY_MODE, null) ?: "SYSTEM")
        }.getOrDefault(ThemeMode.SYSTEM)
        return template to mode
    }

    fun save(context: Context, template: ThemeTemplate, mode: ThemeMode) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY_TEMPLATE, template.id)
            .putString(KEY_MODE, mode.name)
            .apply()
    }
}

/**
 * Whether the PHONE is currently in dark mode, read from the
 * application context's configuration. The activity's own
 * configuration, what isSystemInDarkTheme() reads, can be
 * overridden per app: HyperOS's per-app dark mode does exactly that,
 * holding an app in night mode while the phone itself is light.
 * The application context reports the system-wide state, which is
 * what the System mode promises to follow.
 */
internal fun systemUsesDarkTheme(context: Context): Boolean {
    val uiMode = context.applicationContext.resources.configuration.uiMode
    return (uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
}

/**
 * The app's type scale: Material 3's metrics with two deliberate
 * changes. One, a single [fontFamily] runs through every style, so
 * Persian can bring Vazirmatn with it (Fonts.kt). Two, a legibility
 * pass the user asked for (2026-11-28): the text people actually
 * read sits one weight step stronger than the M3 defaults, titles
 * Medium to SemiBold, body copy Regular to Medium, button labels
 * SemiBold. Captions and small print keep their default weights, so
 * nothing turns heavy. Sizes and line heights are untouched.
 */
fun appTypography(fontFamily: FontFamily): Typography = Typography(
    displayLarge = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp),
    displayMedium = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 45.sp, lineHeight = 52.sp),
    displaySmall = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 36.sp, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    bodyMedium = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
    bodySmall = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelLarge = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
)

@Composable
fun MediaConverterTheme(
    template: ThemeTemplate = ThemeTemplate.DYNAMIC,
    mode: ThemeMode = ThemeMode.SYSTEM,
    fontFamily: FontFamily = FontFamily.Default,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val darkTheme = when (mode) {
        ThemeMode.SYSTEM -> systemUsesDarkTheme(context)
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = template.scheme(darkTheme) ?: when {
        // DYNAMIC: Material You from the wallpaper (API 31+). Below
        // that, fall back to Ocean, the app's original palette.
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> ThemeTemplate.OCEAN.scheme(dark = true)!!
        else -> ThemeTemplate.OCEAN.scheme(dark = false)!!
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = appTypography(fontFamily),
        content = content,
    )
}
