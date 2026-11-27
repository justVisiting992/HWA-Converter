package io.github.justvisiting992.mediaconverter.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.Color
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
     *  VectorDrawables and rasterized asset types are supported"),
     *  which is what crashed the Settings tab on open. */
    val previewRes: Int,
    /** This icon's own two gradient tones, sampled once from the
     *  artwork (its light tone and its deep tone, the white lettering
     *  excluded). The app frames the icon with a gradient of these
     *  two: the Settings preview border here, the ring layer in the
     *  adaptive icon XML. The frame matches the icon itself and
     *  covers the artwork's rough hand-cropped rim. Plain constants:
     *  no computation at runtime. */
    val ringLight: Color,
    val ringDeep: Color,
) {
    DEFAULT("Default", "IconDefault", R.mipmap.ic_launcher_foreground, Color(0xFFE2D493), Color(0xFF2A5F7A)),
    DUSK("Dusk", "IconDusk", R.mipmap.ic_launcher_dusk_foreground, Color(0xFF88796B), Color(0xFF182744)),
    EMERALD("Emerald Script", "IconEmerald", R.mipmap.ic_launcher_emerald_foreground, Color(0xFFCED689), Color(0xFF234245)),
    SUNSET("Sunset", "IconSunset", R.mipmap.ic_launcher_sunset_foreground, Color(0xFFEE9856), Color(0xFF582E54)),
    VIOLET("Violet Script", "IconViolet", R.mipmap.ic_launcher_violet_foreground, Color(0xFFBB6768), Color(0xFF332458)),
    GUNMETAL("Gunmetal", "IconGunmetal", R.mipmap.ic_launcher_gunmetal_foreground, Color(0xFF838A95), Color(0xFF404652)),
    MIDNIGHT("Midnight", "IconMidnight", R.mipmap.ic_launcher_midnight_foreground, Color(0xFF79706C), Color(0xFF1E2331)),
    FOREST("Forest", "IconForest", R.mipmap.ic_launcher_forest_foreground, Color(0xFF577968), Color(0xFF1C3D30)),
    BLACK_GOLD("Black & Gold", "IconBlackGold", R.mipmap.ic_launcher_blackgold_foreground, Color(0xFFE5CEA9), Color(0xFF4B2F1F)),
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
