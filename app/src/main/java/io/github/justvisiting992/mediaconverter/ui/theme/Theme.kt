package io.github.justvisiting992.mediaconverter.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** Light/dark choice, independent of which color template is active. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * The user's appearance choices, persisted in SharedPreferences — two
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

@Composable
fun MediaConverterTheme(
    template: ThemeTemplate = ThemeTemplate.DYNAMIC,
    mode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colorScheme = template.scheme(darkTheme) ?: when {
        // DYNAMIC: Material You from the wallpaper (API 31+). Below
        // that, fall back to Ocean — the app's original palette.
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> ThemeTemplate.OCEAN.scheme(dark = true)!!
        else -> ThemeTemplate.OCEAN.scheme(dark = false)!!
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
