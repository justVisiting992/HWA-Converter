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

/** What ffprobe learned about one input file. Every field is nullable: an
 * unprobeable field stays unknown rather than becoming a made-up value. */
data class InputInfo(
    val durationMs: Long?,
    val format: String?,
    val sizeBytes: Long?,
    val overallBitrateBps: Long?,
    val videoCodec: String?,
    val width: Long?,
    val height: Long?,
    val videoBitrateBps: Long?,
    val audioCodec: String?,
)

/**
 * Collects one ffmpeg run's log lines for the debug log file. A long
 * encode logs thousands of progress lines, so the collector keeps the
 * first [HEAD_LINES] lines (input description, stream mapping, encoder
 * init — the diagnostic part) and the last [TAIL_LINES], and says in the
 * output how many lines were elided between them.
 */
class LogCollector {
    private val lines = mutableListOf<String>()
    private var elided = 0

    @Synchronized
    fun append(line: String) {
        if (lines.size < HEAD_LINES + TAIL_LINES) {
            lines.add(line)
        } else {
            lines.removeAt(HEAD_LINES)
            lines.add(line)
            elided++
        }
    }

    @Synchronized
    fun render(): String {
        if (elided == 0) return lines.joinToString("\n")
        val head = lines.subList(0, HEAD_LINES).joinToString("\n")
        val tail = lines.subList(HEAD_LINES, lines.size).joinToString("\n")
        return head + "\n... [$elided log lines omitted] ...\n" + tail
    }

    private companion object {
        const val HEAD_LINES = 300
        const val TAIL_LINES = 1500
    }
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
     * Probe the media at [inputUri]: duration, container, size, bitrates
     * and the first video/audio stream's codec and dimensions. Returns
     * null only when ffprobe produces nothing at all; individual fields
     * are null when ffprobe does not report them. Blocking; call off the
     * main thread.
     */
    fun probeInput(context: Context, inputUri: Uri): InputInfo? {
        val path = FFmpegKitConfig.getSafParameterForRead(context, inputUri)
        val session = runCatching { FFprobeKit.getMediaInformation(path) }.getOrNull()
            ?: return null
        val info = session.mediaInformation ?: return null
        val streams = info.streams.orEmpty()
        val video = streams.firstOrNull { it.type == "video" }
        val audio = streams.firstOrNull { it.type == "audio" }
        return InputInfo(
            durationMs = info.duration?.toDoubleOrNull()
                ?.let { (it * 1000).toLong() }?.takeIf { it > 0 },
            format = info.format,
            sizeBytes = info.size?.toLongOrNull(),
            overallBitrateBps = info.bitrate?.toLongOrNull(),
            videoCodec = video?.codec,
            width = video?.width,
            height = video?.height,
            videoBitrateBps = video?.bitrate?.toLongOrNull(),
            audioCodec = audio?.codec,
        )
    }

    /** The read path ffmpeg should use for a picked input file. */
    fun inputPath(context: Context, inputUri: Uri): String =
        FFmpegKitConfig.getSafParameterForRead(context, inputUri)

    /** The write path ffmpeg should use for an output entry created in MediaStore. */
    fun outputPath(context: Context, outputUri: Uri): String =
        FFmpegKitConfig.getSafParameterForWrite(context, outputUri)

    /**
     * Run one ffmpeg command to completion. [onProgress] receives a 0..1
     * fraction when [totalDurationMs] is known (null when it is not —
     * statistics still arrive, they just cannot be turned into a
     * fraction), the current speed as a multiple of realtime, and the
     * output size written so far in bytes.
     * Every ffmpeg log line is also passed to [onLog] for the debug log.
     * Cancelling the calling coroutine cancels the ffmpeg session.
     */
    suspend fun run(
        command: List<String>,
        totalDurationMs: Long?,
        onProgress: (fraction: Float?, speed: Double?, sizeBytes: Long?) -> Unit,
        onLog: (String) -> Unit = {},
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
                { log -> onLog(log.message) },
                { stats ->
                    val fraction = if (totalDurationMs != null && totalDurationMs > 0) {
                        (stats.time / totalDurationMs).toFloat().coerceIn(0f, 1f)
                    } else {
                        null
                    }
                    onProgress(
                        fraction,
                        stats.speed.takeIf { it > 0 },
                        stats.size.takeIf { it > 0 },
                    )
                },
            )
            cont.invokeOnCancellation { session.cancel() }
        }
    }

    private const val LOG_TAIL_CHARS = 1500
}
