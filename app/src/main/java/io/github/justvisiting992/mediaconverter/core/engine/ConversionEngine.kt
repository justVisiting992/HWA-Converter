package io.github.justvisiting992.mediaconverter.core.engine

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** The outcome of one ffmpeg run, in the app's own terms. */
sealed interface EngineResult {
    data object Success : EngineResult

    data object Cancelled : EngineResult

    /** ffmpeg ran and failed; [logTail] is the end of its log, for the job card. */
    data class Failed(val logTail: String) : EngineResult
}

/**
 * The layer that actually runs ffmpeg (via the vendored FFmpegKit) and
 * probes inputs with ffprobe. Everything above this file speaks in presets
 * and jobs; this file is the only place that touches FFmpegKit.
 *
 * Files arrive as SAF content URIs (the system picker never gives real
 * paths), so both input and output go through FFmpegKit's SAF parameters —
 * pipe-backed paths ffmpeg can read and write.
 */
object ConversionEngine {

    /**
     * Duration of the media at [inputUri] in milliseconds, or null when
     * ffprobe cannot determine it (progress then shows as indeterminate —
     * never a made-up percentage). Blocking; call off the main thread.
     */
    fun probeDurationMs(context: Context, inputUri: Uri): Long? {
        val path = FFmpegKitConfig.getSafParameterForRead(context, inputUri)
        val session = runCatching { FFprobeKit.getMediaInformation(path) }.getOrNull()
            ?: return null
        val seconds = session.mediaInformation?.duration?.toDoubleOrNull() ?: return null
        return (seconds * 1000).toLong().takeIf { it > 0 }
    }

    /** The read path ffmpeg should use for a picked input file. */
    fun inputPath(context: Context, inputUri: Uri): String =
        FFmpegKitConfig.getSafParameterForRead(context, inputUri)

    /** The write path ffmpeg should use for an output entry created in MediaStore. */
    fun outputPath(context: Context, outputUri: Uri): String =
        FFmpegKitConfig.getSafParameterForWrite(context, outputUri)

    /**
     * Run one ffmpeg command to completion. [onProgress] receives a 0..1
     * fraction when [totalDurationMs] is known, or null when it is not —
     * statistics still arrive, they just cannot be turned into a fraction.
     * Cancelling the calling coroutine cancels the ffmpeg session.
     */
    suspend fun run(
        command: List<String>,
        totalDurationMs: Long?,
        onProgress: (Float?) -> Unit,
    ): EngineResult = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { cont ->
            val session = FFmpegKit.executeWithArgumentsAsync(
                command.toTypedArray(),
                { completed ->
                    if (!cont.isActive) return@executeWithArgumentsAsync
                    val rc = completed.returnCode
                    val result = when {
                        ReturnCode.isSuccess(rc) -> EngineResult.Success
                        ReturnCode.isCancel(rc) -> EngineResult.Cancelled
                        else -> EngineResult.Failed(
                            completed.allLogsAsString.trim().takeLast(LOG_TAIL_CHARS),
                        )
                    }
                    cont.resume(result)
                },
                null,
                { stats ->
                    val fraction = if (totalDurationMs != null && totalDurationMs > 0) {
                        (stats.time / totalDurationMs).toFloat().coerceIn(0f, 1f)
                    } else {
                        null
                    }
                    onProgress(fraction)
                },
            )
            cont.invokeOnCancellation { session.cancel() }
        }
    }

    private const val LOG_TAIL_CHARS = 1500
}
