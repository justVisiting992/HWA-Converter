package io.github.justvisiting992.mediaconverter.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import io.github.justvisiting992.mediaconverter.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** A newer release on GitHub, with the APK that fits this phone. */
data class UpdateInfo(
    val version: String,
    val notes: String,
    val apkName: String,
    val apkUrl: String,
    val sizeBytes: Long,
)

sealed interface UpdateCheckResult {
    data class Available(val info: UpdateInfo) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult

    /** [noNetwork] is true when GitHub could not be reached at all:
     *  the phone is offline, or the user turned this app's network
     *  access off in the system settings. The controller treats
     *  that as the feature switching itself off, not as an error. */
    data class Failed(val message: String, val noNetwork: Boolean = false) :
        UpdateCheckResult
}

/**
 * The update checker. Its one job: ask the project's GitHub releases
 * for the latest version, compare it with the installed one, and, on
 * request, download that release's APK and hand it to the system
 * installer. This is the app's only network use, and it only ever
 * talks to GitHub. Conversions never leave the phone.
 */
object UpdateChecker {

    private const val LATEST_URL =
        "https://api.github.com/repos/justVisiting992/HWA-Converter/releases/latest"

    suspend fun check(currentVersion: String): UpdateCheckResult =
        withContext(Dispatchers.IO) {
            try {
                val connection = open(LATEST_URL)
                val body = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(body)
                val remote = json.optString("tag_name").removePrefix("v")
                if (remote.isEmpty() || !isNewer(remote, currentVersion)) {
                    return@withContext UpdateCheckResult.UpToDate
                }
                val assets = json.optJSONArray("assets")
                    ?: return@withContext UpdateCheckResult.Failed(
                        "That release has no files attached.",
                    )
                val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
                var byAbi: JSONObject? = null
                var universal: JSONObject? = null
                var firstApk: JSONObject? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name")
                    if (!name.endsWith(".apk")) continue
                    if (firstApk == null) firstApk = asset
                    if (abi.isNotEmpty() && name.contains(abi)) byAbi = asset
                    if (name.contains("universal")) universal = asset
                }
                val chosen = byAbi ?: universal ?: firstApk
                    ?: return@withContext UpdateCheckResult.Failed(
                        "That release has no APK attached.",
                    )
                UpdateCheckResult.Available(
                    UpdateInfo(
                        version = remote,
                        notes = json.optString("body")
                            .replace("\r\n", "\n")
                            .trim()
                            .take(280),
                        apkName = chosen.optString("name"),
                        apkUrl = chosen.optString("browser_download_url"),
                        sizeBytes = chosen.optLong("size"),
                    ),
                )
            } catch (e: java.io.IOException) {
                UpdateCheckResult.Failed(
                    "Could not reach GitHub. Check your connection and try again.",
                    noNetwork = true,
                )
            } catch (e: Exception) {
                UpdateCheckResult.Failed(
                    "Something went wrong while checking. Try again later.",
                )
            }
        }

    /** Download the APK into the app's cache, reporting 0f..1f. */
    suspend fun download(
        context: Context,
        info: UpdateInfo,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, info.apkName)
        if (target.exists() && info.sizeBytes > 0 &&
            target.length() == info.sizeBytes
        ) {
            onProgress(1f)
            return@withContext target
        }
        val connection = open(info.apkUrl)
        val total = if (info.sizeBytes > 0) {
            info.sizeBytes
        } else {
            connection.contentLengthLong
        }
        connection.inputStream.use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var copied = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    copied += read
                    if (total > 0) onProgress(copied.toFloat() / total)
                }
            }
        }
        target
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "HWA-Converter")
            setRequestProperty("Accept", "application/vnd.github+json")
        }

    /** "0.2.10" is newer than "0.2.9": compare number by number. */
    fun isNewer(remote: String, current: String): Boolean {
        fun parts(v: String) = v.split(".")
            .map { part -> part.filter { it.isDigit() }.toIntOrNull() ?: 0 }
        val r = parts(remote)
        val c = parts(current)
        for (i in 0 until maxOf(r.size, c.size)) {
            val rv = r.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (rv != cv) return rv > cv
        }
        return false
    }
}

/** The checker's small memory: the switch, the last check day, the
 *  version the user already said "later" to, and whether the app
 *  found itself without network access (the checker then disables
 *  itself until a check reaches GitHub again). */
object UpdatePrefs {

    private const val PREFS = "updates"
    private const val KEY_AUTO = "auto_check"
    private const val KEY_LAST_DAY = "last_check_day"
    private const val KEY_DISMISSED = "dismissed_version"
    private const val KEY_NETWORK_OFF = "network_off"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun autoCheck(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO, true)

    fun setAutoCheck(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO, enabled).apply()
    }

    fun lastCheckDay(context: Context): Long =
        prefs(context).getLong(KEY_LAST_DAY, 0)

    fun setLastCheckDay(context: Context, day: Long) {
        prefs(context).edit().putLong(KEY_LAST_DAY, day).apply()
    }

    fun dismissedVersion(context: Context): String? =
        prefs(context).getString(KEY_DISMISSED, null)

    fun setDismissedVersion(context: Context, version: String) {
        prefs(context).edit().putString(KEY_DISMISSED, version).apply()
    }

    fun networkOff(context: Context): Boolean =
        prefs(context).getBoolean(KEY_NETWORK_OFF, false)

    fun setNetworkOff(context: Context, off: Boolean) {
        prefs(context).edit().putBoolean(KEY_NETWORK_OFF, off).apply()
    }
}

/**
 * Holds the update state the UI reads: whether the floating dialog
 * is up and what it shows, the About row's status line, and the
 * download progress. One instance lives in MainActivity.
 */
class UpdateController(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _dialogInfo = MutableStateFlow<UpdateInfo?>(null)
    val dialogInfo: StateFlow<UpdateInfo?> = _dialogInfo

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    private val _progress = MutableStateFlow<Float?>(null)
    val progress: StateFlow<Float?> = _progress

    private val _autoCheck =
        MutableStateFlow(UpdatePrefs.autoCheck(appContext))
    val autoCheck: StateFlow<Boolean> = _autoCheck

    /** True when the app found it has no network access (the phone
     *  is offline for this app, or the user turned the app's
     *  network access off in the system settings). The automatic
     *  checker disables itself then, and revives the moment a check
     *  reaches GitHub again. The switch in About shows off while
     *  this is true. */
    private val _networkOff =
        MutableStateFlow(UpdatePrefs.networkOff(appContext))
    val networkOff: StateFlow<Boolean> = _networkOff

    private var checking = false

    private fun noteReached() {
        if (_networkOff.value) {
            _networkOff.value = false
            UpdatePrefs.setNetworkOff(appContext, false)
        }
    }

    private fun noteUnreachable() {
        if (!_networkOff.value) {
            _networkOff.value = true
            UpdatePrefs.setNetworkOff(appContext, true)
        }
    }

    /** The quiet launch check: at most once a day, only if the
     *  switch is on and the app has network access, and a version
     *  the user already postponed does not pop up again. */
    fun maybeAutoCheck() {
        if (!_autoCheck.value || _networkOff.value || checking) return
        val today = System.currentTimeMillis() / 86_400_000
        if (UpdatePrefs.lastCheckDay(appContext) >= today) return
        checking = true
        scope.launch {
            val result = UpdateChecker.check(BuildConfig.VERSION_NAME)
            checking = false
            when (result) {
                is UpdateCheckResult.Available -> {
                    noteReached()
                    UpdatePrefs.setLastCheckDay(appContext, today)
                    if (UpdatePrefs.dismissedVersion(appContext) !=
                        result.info.version
                    ) {
                        _dialogInfo.value = result.info
                    }
                }
                UpdateCheckResult.UpToDate -> {
                    noteReached()
                    UpdatePrefs.setLastCheckDay(appContext, today)
                }
                is UpdateCheckResult.Failed -> {
                    // No network access: the checker disables
                    // itself instead of retrying every launch.
                    if (result.noNetwork) noteUnreachable()
                }
            }
        }
    }

    /** The About row's check: always runs, always reports back. A
     *  successful reach also revives the automatic checker. */
    fun checkManually() {
        if (checking) return
        checking = true
        _status.value = "Checking GitHub…"
        scope.launch {
            when (val result = UpdateChecker.check(BuildConfig.VERSION_NAME)) {
                is UpdateCheckResult.Available -> {
                    noteReached()
                    _status.value = "Version ${result.info.version} is available."
                    _dialogInfo.value = result.info
                }
                UpdateCheckResult.UpToDate -> {
                    noteReached()
                    _status.value = "You are up to date."
                }
                is UpdateCheckResult.Failed -> {
                    if (result.noNetwork) noteUnreachable()
                    _status.value = result.message
                }
            }
            checking = false
        }
    }

    fun setAutoCheck(enabled: Boolean) {
        _autoCheck.value = enabled
        UpdatePrefs.setAutoCheck(appContext, enabled)
    }

    /** "Download Later": remember this version and close the card. */
    fun postpone() {
        _dialogInfo.value?.let {
            UpdatePrefs.setDismissedVersion(appContext, it.version)
        }
        _dialogInfo.value = null
        _progress.value = null
    }

    fun downloadAndInstall() {
        val info = _dialogInfo.value ?: return
        if (_progress.value != null) return
        _progress.value = 0f
        scope.launch {
            try {
                val file = UpdateChecker.download(appContext, info) { fraction ->
                    _progress.value = fraction
                }
                _progress.value = null
                install(file)
            } catch (e: Exception) {
                _progress.value = null
                _status.value =
                    "The download failed. Check your connection and try again."
                _dialogInfo.value = null
            }
        }
    }

    private fun install(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !appContext.packageManager.canRequestPackageInstalls()
        ) {
            _status.value =
                "Allow installs from this app, then tap Download & Install again."
            _dialogInfo.value = null
            val settings = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${appContext.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(settings)
            return
        }
        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
        _dialogInfo.value = null
    }
}
