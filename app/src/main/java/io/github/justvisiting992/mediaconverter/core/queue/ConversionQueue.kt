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
import io.github.justvisiting992.mediaconverter.core.engine.PresetCommand
import io.github.justvisiting992.mediaconverter.core.engine.PresetCommands
import kotlinx.coroutines.CancellationException
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

    /** [fraction] is null when the input duration is unknown — indeterminate, honestly. */
    data class Running(val fraction: Float?) : JobStatus

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
    val status: JobStatus,
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
class ConversionQueueViewModel : ViewModel() {

    private val _jobs = MutableStateFlow<List<ConversionJob>>(emptyList())
    val jobs: StateFlow<List<ConversionJob>> = _jobs

    private var nextId = 1L
    private var pumpJob: Job? = null
    private var runningDeferred: Deferred<EngineResult>? = null

    // The ViewModel outlives any single Activity instance, so engine and
    // MediaStore work runs against the application context, captured on
    // the first addJobs call.
    @Volatile
    private var appContext: Context? = null

    /**
     * Queue one job per picked file, all with the same [preset]. A preset
     * that cannot run (e.g. trim without times) becomes a Failed job with
     * the reason showing — visible, never silently dropped.
     */
    fun addJobs(
        context: Context,
        uris: List<Uri>,
        preset: ConversionPreset,
        report: DeviceCodecReport,
        trimStartSeconds: Double?,
        trimEndSeconds: Double?,
    ) {
        val app = context.applicationContext
        if (appContext == null) appContext = app
        viewModelScope.launch {
            val created = withContext(Dispatchers.IO) {
                uris.map { uri ->
                    createJob(app, uri, preset, report, trimStartSeconds, trimEndSeconds)
                }
            }
            _jobs.update { it + created }
            pump()
        }
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
    ): ConversionJob {
        val id = nextId++
        val inputName = queryDisplayName(context, inputUri) ?: "input"
        val base = inputName.substringBeforeLast('.', inputName)
        val outputName = "${base}_${preset.id}.${preset.outputExtension}"
        val isAudio = preset.outputExtension == "m4a"
        val outputUri = MediaStoreOutput.create(context, outputName, isAudio)
        if (outputUri == null) {
            return ConversionJob(
                id = id, inputUri = inputUri, inputName = inputName, preset = preset,
                outputUri = null, outputName = outputName,
                outputFolder = MediaStoreOutput.folderLabel(isAudio),
                command = emptyList(), note = "",
                status = JobStatus.Failed("Could not create the output file in MediaStore."),
            )
        }
        return when (
            val built = PresetCommands.build(
                preset = preset,
                inputPath = ConversionEngine.inputPath(context, inputUri),
                outputPath = ConversionEngine.outputPath(context, outputUri),
                report = report,
                trimStartSeconds = trimStartSeconds,
                trimEndSeconds = trimEndSeconds,
            )
        ) {
            is PresetCommand.Unavailable -> {
                MediaStoreOutput.discard(context, outputUri)
                ConversionJob(
                    id = id, inputUri = inputUri, inputName = inputName, preset = preset,
                    outputUri = null, outputName = outputName,
                    outputFolder = MediaStoreOutput.folderLabel(isAudio),
                    command = emptyList(), note = "",
                    status = JobStatus.Failed(built.reason),
                )
            }
            is PresetCommand.Ready -> ConversionJob(
                id = id, inputUri = inputUri, inputName = inputName, preset = preset,
                outputUri = outputUri, outputName = outputName,
                outputFolder = MediaStoreOutput.folderLabel(isAudio),
                command = built.command, note = built.note,
                status = JobStatus.Queued,
            )
        }
    }

    /** Start the runner if it is not already going. */
    private fun pump() {
        if (pumpJob?.isActive == true) return
        pumpJob = viewModelScope.launch {
            while (true) {
                val next = _jobs.value.firstOrNull { it.status is JobStatus.Queued }
                    ?: break
                runJob(next)
            }
        }
    }

    private suspend fun runJob(job: ConversionJob) {
        val context = appContext ?: return
        setStatus(job.id, JobStatus.Running(null))
        try {
            val durationMs = withContext(Dispatchers.IO) {
                ConversionEngine.probeDurationMs(context, job.inputUri)
            }
            val totalMs = totalDurationFor(job, durationMs)
            // The engine run lives in its own Deferred so cancel() can stop
            // this job without killing the pump loop behind it. Cancelling
            // it makes the engine cancel the ffmpeg session.
            val result = coroutineScope {
                val deferred = async {
                    ConversionEngine.run(job.command, totalMs) { fraction ->
                        setStatus(job.id, JobStatus.Running(fraction))
                    }
                }
                runningDeferred = deferred
                deferred.await()
            }
            when (result) {
                EngineResult.Success -> {
                    job.outputUri?.let { MediaStoreOutput.publish(context, it) }
                    setStatus(job.id, JobStatus.Done)
                }
                EngineResult.Cancelled -> {
                    discardOutput(job)
                    setStatus(job.id, JobStatus.Cancelled)
                }
                is EngineResult.Failed -> {
                    discardOutput(job)
                    setStatus(job.id, JobStatus.Failed(failureReason(result.logTail)))
                }
            }
        } catch (e: CancellationException) {
            discardOutput(job)
            setStatus(job.id, JobStatus.Cancelled)
        } finally {
            runningDeferred = null
        }
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
        val context = appContext ?: return
        viewModelScope.launch(Dispatchers.IO) { MediaStoreOutput.discard(context, uri) }
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

    fun folderLabel(isAudio: Boolean): String =
        if (isAudio) "Music/$FOLDER" else "Movies/$FOLDER"

    /** Create a pending output entry; null when MediaStore refuses. */
    fun create(context: Context, displayName: String, isAudio: Boolean): Uri? {
        val resolver = context.contentResolver
        val collection = when {
            isAudio -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(
                MediaStore.MediaColumns.MIME_TYPE,
                if (isAudio) "audio/mp4" else "video/mp4",
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    (if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES) +
                        "/" + FOLDER,
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
