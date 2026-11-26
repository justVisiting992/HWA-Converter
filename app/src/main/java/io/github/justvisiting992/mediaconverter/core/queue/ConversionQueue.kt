package io.github.justvisiting992.mediaconverter.core.queue

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.justvisiting992.mediaconverter.core.capability.DeviceCodecReport
import io.github.justvisiting992.mediaconverter.core.engine.ConversionEngine
import io.github.justvisiting992.mediaconverter.core.engine.ConversionPreset
import io.github.justvisiting992.mediaconverter.core.engine.EngineResult
import io.github.justvisiting992.mediaconverter.core.engine.InputInfo
import io.github.justvisiting992.mediaconverter.core.engine.LogCollector
import io.github.justvisiting992.mediaconverter.core.engine.MediaKind
import io.github.justvisiting992.mediaconverter.core.engine.PresetCommand
import io.github.justvisiting992.mediaconverter.core.engine.PresetCommands
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where one conversion stands. The UI renders this, nothing else. */
sealed interface JobStatus {
    data object Queued : JobStatus

    /**
     * [fraction] is null when the input duration is unknown —
     * indeterminate, honestly. [speed] is realtime multiples (2.5 =
     * two and a half times realtime), null before ffmpeg reports any.
     */
    data class Running(
        val fraction: Float?,
        val speed: Double? = null,
        /** Output bytes written so far, as ffmpeg reports them. */
        val sizeBytes: Long? = null,
    ) : JobStatus

    data object Done : JobStatus

    data class Failed(val reason: String) : JobStatus

    data object Cancelled : JobStatus
}

/** One conversion the user asked for, from pick to outcome. */
data class ConversionJob(
    val id: Long,
    val inputUri: Uri,
    val inputName: String,
    val preset: ConversionPreset,
    /** MediaStore entry the output is written to; null when creation failed up front. */
    val outputUri: Uri?,
    val outputName: String,
    /** Human place the output lands, e.g. "Movies/HWA Converter". */
    val outputFolder: String,
    /** The exact ffmpeg arguments (kept for the job log / command view). */
    val command: List<String>,
    /** One line from the preset layer: which encoder runs, and why. */
    val note: String,
    /** What ffprobe found in the input before the command was built. */
    val inputInfo: InputInfo? = null,
    /** True when the command decodes on the media engine (retry ladder). */
    val usedHardwareDecode: Boolean = false,
    /** The manual options for a CUSTOM job; null for preset jobs. */
    val manual: PresetCommands.ManualOptions? = null,
    val status: JobStatus,
    /** The debug log file written when the job finished; null until then. */
    val logUri: Uri? = null,
)

/**
 * The conversion queue. Jobs run strictly one at a time — hardware codec
 * instances are scarce, and two encodes at once would fight over the same
 * media engine — in the order they were added.
 *
 * Outputs are MediaStore entries created before the encode starts (ffmpeg
 * writes through the SAF write path) and published only on success; a
 * failed or cancelled job's entry is deleted, so a half-written file never
 * shows up in the user's gallery.
 */
class ConversionQueue(private val appContext: Context) {

    // The queue owns its scope: conversions keep running while the app
    // is in the background (the foreground service keeps the process
    // alive), independent of any screen's lifecycle.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _jobs = MutableStateFlow<List<ConversionJob>>(emptyList())
    val jobs: StateFlow<List<ConversionJob>> = _jobs

    private var nextId = 1L
    private var pumpJob: Job? = null

    @Volatile
    private var runningDeferred: Deferred<EngineResult>? = null

    // The latest capability report, kept so a failed job's command can be
    // rebuilt (the decode retry ladder) without re-probing the device.
    @Volatile
    private var codecReport: DeviceCodecReport? = null

    /**
     * Queue one job per picked file, all with the same [preset]. A preset
     * that cannot run (e.g. trim without times) becomes a Failed job with
     * the reason showing — visible, never silently dropped.
     */
    fun addJobs(
        uris: List<Uri>,
        preset: ConversionPreset,
        report: DeviceCodecReport,
        trimStartSeconds: Double?,
        trimEndSeconds: Double?,
        manual: PresetCommands.ManualOptions? = null,
    ) {
        codecReport = report
        scope.launch {
            val created = withContext(Dispatchers.IO) {
                uris.map { uri ->
                    createJob(
                        appContext, uri, preset, report,
                        trimStartSeconds, trimEndSeconds, manual,
                    )
                }
            }
            _jobs.update { it + created }
            pump()
        }
    }

    /** Cancel whatever is running right now (the notification's action). */
    fun cancelCurrent() {
        _jobs.value.firstOrNull { it.status is JobStatus.Running }
            ?.let { cancel(it.id) }
    }

    fun cancel(jobId: Long) {
        val job = _jobs.value.firstOrNull { it.id == jobId } ?: return
        when (job.status) {
            is JobStatus.Queued -> {
                discardOutput(job)
                setStatus(jobId, JobStatus.Cancelled)
            }
            is JobStatus.Running -> runningDeferred?.cancel()
            else -> Unit
        }
    }

    private fun createJob(
        context: Context,
        inputUri: Uri,
        preset: ConversionPreset,
        report: DeviceCodecReport,
        trimStartSeconds: Double?,
        trimEndSeconds: Double?,
        manual: PresetCommands.ManualOptions?,
    ): ConversionJob {
        val id = nextId++
        val inputName = queryDisplayName(context, inputUri) ?: "input"
        val base = inputName.substringBeforeLast('.', inputName)
        // A custom job's file shape comes from the manual options, not
        // the placeholder values on the CUSTOM preset entry.
        val custom = if (preset == ConversionPreset.CUSTOM) manual else null
        val (outExt, outMime, outKind) = if (custom != null) {
            PresetCommands.manualOutputSpec(custom)
        } else {
            Triple(preset.outputExtension, preset.mimeType, preset.kind)
        }
        val outputName = "${base}_${preset.id}.$outExt"
        // Probe first: the command is built from what the file actually
        // is (codec, resolution, bitrate), not from guesses.
        val inputInfo = ConversionEngine.probeInput(context, inputUri)
        val outputUri = MediaStoreOutput.create(
            context, outputName, outKind, outMime,
        )
        if (outputUri == null) {
            return ConversionJob(
                id = id, inputUri = inputUri, inputName = inputName, preset = preset,
                outputUri = null, outputName = outputName,
                outputFolder = MediaStoreOutput.folderLabel(outKind),
                command = emptyList(), note = "", inputInfo = inputInfo,
                status = JobStatus.Failed("Could not create the output file in MediaStore."),
            )
        }
        return when (
            val built = if (custom != null) {
                PresetCommands.buildManual(
                    options = custom,
                    inputPath = ConversionEngine.inputPath(context, inputUri),
                    outputPath = ConversionEngine.outputPath(context, outputUri),
                    report = report,
                    input = inputInfo,
                )
            } else {
                PresetCommands.build(
                    preset = preset,
                    inputPath = ConversionEngine.inputPath(context, inputUri),
                    outputPath = ConversionEngine.outputPath(context, outputUri),
                    report = report,
                    input = inputInfo,
                    trimStartSeconds = trimStartSeconds,
                    trimEndSeconds = trimEndSeconds,
                )
            }
        ) {
            is PresetCommand.Unavailable -> {
                MediaStoreOutput.discard(context, outputUri)
                ConversionJob(
                    id = id, inputUri = inputUri, inputName = inputName, preset = preset,
                    outputUri = null, outputName = outputName,
                    outputFolder = MediaStoreOutput.folderLabel(outKind),
                    command = emptyList(), note = "", inputInfo = inputInfo,
                    status = JobStatus.Failed(built.reason),
                )
            }
            is PresetCommand.Ready -> ConversionJob(
                id = id, inputUri = inputUri, inputName = inputName, preset = preset,
                outputUri = outputUri, outputName = outputName,
                outputFolder = MediaStoreOutput.folderLabel(outKind),
                command = built.command, note = built.note,
                inputInfo = inputInfo,
                usedHardwareDecode = built.usedHardwareDecode,
                manual = custom,
                status = JobStatus.Queued,
            )
        }
    }

    /** Start the runner if it is not already going. */
    private fun pump() {
        if (pumpJob?.isActive == true) return
        pumpJob = scope.launch {
            while (true) {
                val next = _jobs.value.firstOrNull { it.status is JobStatus.Queued }
                    ?: break
                runJob(next)
            }
        }
    }

    private suspend fun runJob(job: ConversionJob) {
        val context = appContext
        setStatus(job.id, JobStatus.Running(null))
        val collector = LogCollector()
        val startedAt = System.nanoTime()
        val inputInfo = job.inputInfo
        try {
            val totalMs = totalDurationFor(job, inputInfo?.durationMs)

            // One engine run in its own Deferred, so cancel() can stop
            // this job without killing the pump loop behind it.
            // Cancelling it makes the engine cancel the ffmpeg session.
            suspend fun execute(command: List<String>): EngineResult = coroutineScope {
                val deferred = async {
                    ConversionEngine.run(
                        command, totalMs,
                        onProgress = { fraction, speed, size ->
                            setStatus(job.id, JobStatus.Running(fraction, speed, size))
                        },
                        onLog = { line -> collector.append(line) },
                    )
                }
                runningDeferred = deferred
                deferred.await()
            }

            var current = job
            var result = execute(current.command)
            // Decode retry ladder: a hardware decoder that rejects a
            // file fails at startup, within seconds — a failure minutes
            // in is something else and is reported, not retried. The
            // retry rebuilds the same command with CPU decode, updates
            // the job so the card and the log show what actually ran,
            // and the ffmpeg -y flag overwrites the partial output.
            if (result is EngineResult.Failed && current.usedHardwareDecode &&
                (System.nanoTime() - startedAt) / 1e9 < 20.0
            ) {
                val report = codecReport
                val outUri = current.outputUri
                val rebuilt = if (report != null && outUri != null) {
                    if (current.manual != null) {
                        PresetCommands.buildManual(
                            options = current.manual.copy(hardwareDecode = false),
                            inputPath = ConversionEngine.inputPath(context, current.inputUri),
                            outputPath = ConversionEngine.outputPath(context, outUri),
                            report = report,
                            input = current.inputInfo,
                        )
                    } else {
                        PresetCommands.build(
                            preset = current.preset,
                            inputPath = ConversionEngine.inputPath(context, current.inputUri),
                            outputPath = ConversionEngine.outputPath(context, outUri),
                            report = report,
                            input = current.inputInfo,
                            softwareDecode = true,
                        )
                    }
                } else {
                    null
                }
                if (rebuilt is PresetCommand.Ready) {
                    collector.append(
                        "--- hardware decode failed fast; retrying with CPU decode ---",
                    )
                    current = current.copy(
                        command = rebuilt.command,
                        note = rebuilt.note,
                        usedHardwareDecode = false,
                    )
                    _jobs.update { jobs ->
                        jobs.map { if (it.id == job.id) current else it }
                    }
                    result = execute(current.command)
                }
            }
            val elapsedSec = (System.nanoTime() - startedAt) / 1e9
            val finishedJob = current
            when (result) {
                EngineResult.Success -> {
                    finishedJob.outputUri?.let { MediaStoreOutput.publish(context, it) }
                    finishWithLog(finishedJob, inputInfo, collector, "Done", elapsedSec)
                    setStatus(job.id, JobStatus.Done)
                }
                EngineResult.Cancelled -> {
                    discardOutput(finishedJob)
                    finishWithLog(finishedJob, inputInfo, collector, "Cancelled", elapsedSec)
                    setStatus(job.id, JobStatus.Cancelled)
                }
                is EngineResult.Failed -> {
                    discardOutput(finishedJob)
                    val reason = failureReason(result.logTail)
                    finishWithLog(finishedJob, inputInfo, collector, "Failed: $reason", elapsedSec)
                    setStatus(job.id, JobStatus.Failed(reason))
                }
            }
        } catch (e: CancellationException) {
            discardOutput(job)
            val elapsedSec = (System.nanoTime() - startedAt) / 1e9
            val latest = _jobs.value.firstOrNull { it.id == job.id } ?: job
            finishWithLog(latest, inputInfo, collector, "Cancelled", elapsedSec)
            setStatus(job.id, JobStatus.Cancelled)
        } finally {
            runningDeferred = null
        }
    }

    /** Write the finished job's debug log and point the job at it. */
    private suspend fun finishWithLog(
        job: ConversionJob,
        info: InputInfo?,
        collector: LogCollector,
        resultLine: String,
        elapsedSec: Double,
    ) {
        val context = appContext
        val text = buildDebugLog(job, info, collector, resultLine, elapsedSec)
        val uri = withContext(Dispatchers.IO) { DebugLogStore.write(context, job, text) }
        if (uri != null) {
            _jobs.update { jobs ->
                jobs.map { if (it.id == job.id) it.copy(logUri = uri) else it }
            }
        }
    }

    /**
     * The debug log's header: everything needed to diagnose a conversion
     * without the phone in hand — device, app, the probed input, the
     * exact command, the outcome — followed by the ffmpeg log itself.
     */
    private fun buildDebugLog(
        job: ConversionJob,
        info: InputInfo?,
        collector: LogCollector,
        resultLine: String,
        elapsedSec: Double,
    ): String {
        val sb = StringBuilder()
        sb.appendLine("HWA Converter debug log")
        sb.appendLine(
            "Time: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()),
        )
        val context = appContext
        val pkg = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        val code = pkg?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                it.longVersionCode
            } else {
                @Suppress("DEPRECATION") it.versionCode.toLong()
            }
        }
        sb.appendLine("App: ${pkg?.versionName ?: "?"} (build ${code ?: "?"})")
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL
        } else {
            "unknown"
        }
        sb.appendLine(
            "Device: ${Build.MANUFACTURER} ${Build.MODEL} · SoC $soc · " +
                "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        )
        sb.appendLine("Input: ${job.inputName}")
        if (info != null) {
            sb.appendLine(
                "Input probe: format=${info.format ?: "?"} · " +
                    "duration=${info.durationMs?.let { "%.1fs".format(it / 1000.0) } ?: "?"} · " +
                    "size=${info.sizeBytes?.let { "%.1f MB".format(it / 1e6) } ?: "?"} · " +
                    "overall=${info.overallBitrateBps?.let { "%.2f Mbps".format(it / 1e6) } ?: "?"}",
            )
            sb.appendLine(
                "Video stream: ${info.videoCodec ?: "?"} " +
                    "${info.width ?: "?"}x${info.height ?: "?"} @ " +
                    (info.videoBitrateBps?.let { "%.2f Mbps".format(it / 1e6) } ?: "?"),
            )
            sb.appendLine("Audio stream: ${info.audioCodec ?: "none"}")
        } else {
            sb.appendLine("Input probe: unavailable")
        }
        sb.appendLine("Preset: ${job.preset.id} (${job.preset.label})")
        sb.appendLine("Note: ${job.note}")
        sb.appendLine("Command: ffmpeg " + PresetCommands.displayCommand(job.command))
        sb.appendLine("Result: $resultLine")
        sb.appendLine("Elapsed: ${"%.1f".format(elapsedSec)}s")
        sb.appendLine()
        sb.appendLine("--- ffmpeg log ---")
        sb.append(collector.render())
        return sb.toString()
    }

    /**
     * The progress denominator. For trim it is the length of the cut, not
     * the whole input; for everything else the input duration.
     */
    private fun totalDurationFor(job: ConversionJob, inputDurationMs: Long?): Long? {
        if (job.preset == ConversionPreset.TRIM_COPY) {
            val args = job.command
            val ss = args.getOrNull(args.indexOf("-ss") + 1)?.toDoubleOrNull()
            val to = args.getOrNull(args.indexOf("-to") + 1)?.toDoubleOrNull()
            if (ss != null && to != null && to > ss) return ((to - ss) * 1000).toLong()
        }
        return inputDurationMs
    }

    private fun failureReason(logTail: String): String {
        val lastLine = logTail.lineSequence().map { it.trim() }.lastOrNull { it.isNotEmpty() }
        return if (lastLine != null) {
            "Conversion failed. ffmpeg said: $lastLine"
        } else {
            "Conversion failed (ffmpeg gave no message)."
        }
    }

    private fun setStatus(jobId: Long, status: JobStatus) {
        _jobs.update { jobs ->
            jobs.map { if (it.id == jobId) it.copy(status = status) else it }
        }
    }

    private fun discardOutput(job: ConversionJob) {
        val uri = job.outputUri ?: return
        val context = appContext
        scope.launch(Dispatchers.IO) { MediaStoreOutput.discard(context, uri) }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? =
        runCatching {
            context.contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index) else null
                } else {
                    null
                }
            }
        }.getOrNull() ?: uri.lastPathSegment
}

/** MediaStore entries for conversion outputs: create pending, publish or discard. */
object MediaStoreOutput {

    private const val FOLDER = "HWA Converter"

    fun folderLabel(kind: MediaKind): String = when (kind) {
        MediaKind.AUDIO -> "Music/$FOLDER"
        MediaKind.IMAGE -> "Pictures/$FOLDER"
        MediaKind.VIDEO -> "Movies/$FOLDER"
    }

    /** Create a pending output entry; null when MediaStore refuses. */
    fun create(
        context: Context,
        displayName: String,
        kind: MediaKind,
        mimeType: String,
    ): Uri? {
        val resolver = context.contentResolver
        val volume = MediaStore.VOLUME_EXTERNAL_PRIMARY
        val collection = when (kind) {
            MediaKind.AUDIO -> MediaStore.Audio.Media.getContentUri(volume)
            MediaKind.IMAGE -> MediaStore.Images.Media.getContentUri(volume)
            MediaKind.VIDEO -> MediaStore.Video.Media.getContentUri(volume)
        }
        val directory = when (kind) {
            MediaKind.AUDIO -> Environment.DIRECTORY_MUSIC
            MediaKind.IMAGE -> Environment.DIRECTORY_PICTURES
            MediaKind.VIDEO -> Environment.DIRECTORY_MOVIES
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    "$directory/$FOLDER",
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        return runCatching { resolver.insert(collection, values) }.getOrNull()
    }

    /** Make a finished output visible to other apps. */
    fun publish(context: Context, uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            runCatching { context.contentResolver.update(uri, values, null, null) }
        }
    }

    /** Remove an output entry whose conversion did not complete. */
    fun discard(context: Context, uri: Uri) {
        runCatching { context.contentResolver.delete(uri, null, null) }
    }
}

/** Debug log files in Downloads/HWA Converter, one per finished job. */
object DebugLogStore {

    private const val FOLDER = "HWA Converter"

    /**
     * Write [text] as the job's debug log file and return its URI, or
     * null when the write is not possible. Below API 29 there is no
     * MediaStore Downloads collection, so no log file is written there.
     */
    fun write(context: Context, job: ConversionJob, text: String): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val resolver = context.contentResolver
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val base = job.inputName.substringBeforeLast('.', job.inputName)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "hwa-log_${base}_$stamp.txt")
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER,
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection =
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = runCatching { resolver.insert(collection, values) }.getOrNull()
            ?: return null
        val written = runCatching {
            resolver.openOutputStream(uri)?.use {
                it.write(text.toByteArray(Charsets.UTF_8))
            }
            true
        }.getOrDefault(false)
        if (!written) {
            runCatching { resolver.delete(uri, null, null) }
            return null
        }
        val publish = ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }
        runCatching { resolver.update(uri, publish, null, null) }
        return uri
    }
}

/**
 * The one queue for the whole process. The UI's ViewModel and the
 * foreground service both talk to this instance, so the notification
 * and the job cards always describe the same conversions.
 */
object ConversionRuntime {

    @Volatile
    private var instance: ConversionQueue? = null

    fun get(context: Context): ConversionQueue =
        instance ?: synchronized(this) {
            instance ?: ConversionQueue(context.applicationContext)
                .also { instance = it }
        }
}

/**
 * The UI's handle on the shared queue. It mirrors the runtime queue's
 * jobs into its own flow so the screens can collect one stable flow;
 * the conversions themselves live in [ConversionQueue], not here, so
 * they survive the screen being destroyed and recreated.
 */
class ConversionQueueViewModel : ViewModel() {

    private val _jobs = MutableStateFlow<List<ConversionJob>>(emptyList())
    val jobs: StateFlow<List<ConversionJob>> = _jobs

    private var queue: ConversionQueue? = null

    /** Connect to the shared queue (idempotent). */
    fun attach(context: Context) {
        if (queue != null) return
        val shared = ConversionRuntime.get(context)
        queue = shared
        viewModelScope.launch {
            shared.jobs.collect { _jobs.value = it }
        }
    }

    fun addJobs(
        context: Context,
        uris: List<Uri>,
        preset: ConversionPreset,
        report: DeviceCodecReport,
        trimStartSeconds: Double?,
        trimEndSeconds: Double?,
        manual: PresetCommands.ManualOptions? = null,
    ) {
        attach(context)
        queue?.addJobs(
            uris, preset, report, trimStartSeconds, trimEndSeconds, manual,
        )
        ConversionService.start(context)
    }

    fun cancel(jobId: Long) {
        queue?.cancel(jobId)
    }
}
