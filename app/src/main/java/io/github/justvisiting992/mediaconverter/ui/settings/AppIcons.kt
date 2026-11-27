package io.github.justvisiting992.mediaconverter.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import io.github.justvisiting992.mediaconverter.R

/**
 * The selectable launcher icons. Each entry maps to an activity-alias
 * in the manifest (`.icon.<aliasSuffix>`) that carries the LAUNCHER
 * entry with that icon; exactly one alias is enabled at a time.
 * Artwork by the user, who owns its license.
 */
enum class AppIcon(
    val label: String,
    val aliasSuffix: String,
    /** The icon's foreground PNG, for the Settings preview. This must
     *  be the raster foreground, never R.mipmap.ic_launcher itself:
     *  that resource resolves to the adaptive-icon XML on API 26+,
     *  and painterResource throws on adaptive icons ("Only
     *  VectorDrawables and rasterized asset types are supported") —
     *  which is what crashed the Settings tab on open. */
    val previewRes: Int,
) {
    DEFAULT("Default", "IconDefault", R.mipmap.ic_launcher_foreground),
    DUSK("Dusk", "IconDusk", R.mipmap.ic_launcher_dusk_foreground),
    EMERALD("Emerald Script", "IconEmerald", R.mipmap.ic_launcher_emerald_foreground),
    SUNSET("Sunset", "IconSunset", R.mipmap.ic_launcher_sunset_foreground),
    VIOLET("Violet Script", "IconViolet", R.mipmap.ic_launcher_violet_foreground),
    GUNMETAL("Gunmetal", "IconGunmetal", R.mipmap.ic_launcher_gunmetal_foreground),
    MIDNIGHT("Midnight", "IconMidnight", R.mipmap.ic_launcher_midnight_foreground),
    FOREST("Forest", "IconForest", R.mipmap.ic_launcher_forest_foreground),
    SLATE("Slate", "IconSlate", R.mipmap.ic_launcher_slate_foreground),
    BLACK_GOLD("Black & Gold", "IconBlackGold", R.mipmap.ic_launcher_blackgold_foreground),
}

/** Persists and applies the chosen launcher icon. */
object IconPrefs {

    private const val PREFS = "app_icon"
    private const val KEY = "icon"

    fun load(context: Context): AppIcon {
        val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
        return AppIcon.entries.firstOrNull { it.name == name } ?: AppIcon.DEFAULT
    }

    /** Switch the launcher entry to [icon]'s alias and remember it. */
    fun apply(context: Context, icon: AppIcon) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, icon.name).apply()
        val pm = context.packageManager
        val pkg = context.packageName
        for (entry in AppIcon.entries) {
            val component = ComponentName(pkg, "$pkg.icon.${entry.aliasSuffix}")
            val state = if (entry == icon) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            runCatching {
                pm.setComponentEnabledSetting(
                    component, state, PackageManager.DONT_KILL_APP,
                )
            }
        }
    }
}
