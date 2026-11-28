package io.github.justvisiting992.mediaconverter.core.locale

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/**
 * The app's language machinery. The user picks a language in Look &amp;
 * Feel (or leaves "System language", the default); the choice is a
 * BCP-47 tag in SharedPreferences. [wrap] builds a Context whose
 * resources resolve in that language, and the UI provides that context
 * through LocalContext, so every stringResource follows it at once.
 * The non-UI layers (queue, service, update checker) resolve through
 * [Strings], which is bound to a wrapped context.
 *
 * Each language is named in its own language in the selector, the way
 * Android's own language lists do it. Persian's flag is the lion and
 * sun image (R.drawable.flag_fa): Unicode has no lion-and-sun flag
 * emoji, so [Language.flagRes] replaces [Language.flag] for it.
 */
data class Language(
    /** BCP-47 tag, also the SharedPreferences value. "" means English base. */
    val tag: String,
    /** The language's name in itself. Never translated. */
    val nativeName: String,
    /** Flag emoji shown in the selector; empty when [flagRes] is used. */
    val flag: String,
    /** Drawable for the flag when no flag emoji exists (Persian). */
    val flagRes: Int = 0,
)

object AppLocale {

    /** The selector's first entry: follow the phone. Stored as "". */
    const val SYSTEM = ""

    val LANGUAGES: List<Language> = listOf(
        Language("en", "English", "🇬🇧"),
        Language("de", "Deutsch", "🇩🇪"),
        Language("fr", "Français", "🇫🇷"),
        Language("es", "Español", "🇪🇸"),
        Language("it", "Italiano", "🇮🇹"),
        Language("pt", "Português", "🇵🇹"),
        Language("nl", "Nederlands", "🇳🇱"),
        Language("pl", "Polski", "🇵🇱"),
        Language("uk", "Українська", "🇺🇦"),
        Language("ru", "Русский", "🇷🇺"),
        Language("ro", "Română", "🇷🇴"),
        Language("cs", "Čeština", "🇨🇿"),
        Language("hu", "Magyar", "🇭🇺"),
        Language("el", "Ελληνικά", "🇬🇷"),
        Language("sv", "Svenska", "🇸🇪"),
        Language("da", "Dansk", "🇩🇰"),
        Language("nb", "Norsk", "🇳🇴"),
        Language("fi", "Suomi", "🇫🇮"),
        Language("sk", "Slovenčina", "🇸🇰"),
        Language("hr", "Hrvatski", "🇭🇷"),
        Language("bg", "Български", "🇧🇬"),
        Language("sr", "Српски", "🇷🇸"),
        Language("lt", "Lietuvių", "🇱🇹"),
        Language("sl", "Slovenščina", "🇸🇮"),
        Language("lv", "Latviešu", "🇱🇻"),
        Language("et", "Eesti", "🇪🇪"),
        Language("ga", "Gaeilge", "🇮🇪"),
        Language("ca", "Català", "🇦🇩"),
        Language("sq", "Shqip", "🇦🇱"),
        Language("is", "Íslenska", "🇮🇸"),
        Language("pt-BR", "Português (Brasil)", "🇧🇷"),
        Language("ja", "日本語", "🇯🇵"),
        Language("ko", "한국어", "🇰🇷"),
        Language("zh-CN", "简体中文", "🇨🇳"),
        Language("zh-TW", "繁體中文", "🇹🇼"),
        Language(
            "fa", "پارسی", "",
            flagRes = io.github.justvisiting992.mediaconverter.R.drawable.flag_fa,
        ),
    )

    private const val PREFS = "locale"
    private const val KEY_TAG = "language_tag"

    /** The stored choice: a tag from [LANGUAGES], or [SYSTEM]. */
    fun selectedTag(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TAG, SYSTEM) ?: SYSTEM

    fun setTag(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_TAG, tag).apply()
    }

    /** The locale the app should run in for [tag] (or the stored one). */
    fun localeFor(context: Context, tag: String = selectedTag(context)): Locale =
        if (tag == SYSTEM) {
            val system = context.resources.configuration.locales
            if (system.isEmpty) Locale.ENGLISH else system[0]
        } else {
            Locale.forLanguageTag(tag)
        }

    /** A context whose resources (and only resources) speak [tag]. */
    fun wrap(context: Context, tag: String = selectedTag(context)): Context {
        val locale = localeFor(context, tag)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLocales(LocaleList(locale))
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }

    /** True when the effective language reads right to left. */
    fun isRtl(context: Context, tag: String = selectedTag(context)): Boolean =
        localeFor(context, tag).let {
            android.text.TextUtils.getLayoutDirectionFromLocale(it) ==
                android.view.View.LAYOUT_DIRECTION_RTL
        }

    /** True when the effective language is Persian (drives Vazirmatn). */
    fun isPersian(context: Context, tag: String = selectedTag(context)): Boolean =
        localeFor(context, tag).language == "fa"
}

/**
 * String resolution for the layers that have no composable: the
 * conversion queue, the foreground service, the update checker.
 * Bound to a language-wrapped context at app start and on every
 * language change; before the first bind it falls back to the plain
 * context, which is the system language, a safe default.
 */
object Strings {
    @Volatile
    private var bound: Context? = null

    fun bind(context: Context) {
        bound = AppLocale.wrap(context.applicationContext)
    }

    fun get(resId: Int, vararg args: Any): String {
        val ctx = bound ?: return ""
        return if (args.isEmpty()) ctx.getString(resId)
        else ctx.getString(resId, *args)
    }
}
