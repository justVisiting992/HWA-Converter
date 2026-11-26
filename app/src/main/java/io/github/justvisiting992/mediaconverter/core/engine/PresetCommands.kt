package io.github.justvisiting992.mediaconverter.core.engine

import io.github.justvisiting992.mediaconverter.core.capability.DeviceCodecReport

/**
 * What the user wants done to a file — a goal, not an encoder. The engine
 * decides which encoder serves the goal, from the live [DeviceCodecReport].
 *
 * The rule this file exists to enforce: a preset is only offered when the
 * device can actually run it, and a hardware preset never silently falls
 * back to the CPU. If the hardware encoder is missing, [PresetCommands]
 * returns [PresetCommand.Unavailable] with the reason in plain words.
 */
enum class ConversionPreset(
    val id: String,
    val label: String,
    val description: String,
    val outputExtension: String,
) {
    H264_HARDWARE(
        id = "h264-hw",
        label = "H.264 · hardware",
        description = "Fast, small, plays everywhere. Uses this phone's media engine.",
        outputExtension = "mp4",
    ),
    HEVC_HARDWARE(
        id = "hevc-hw",
        label = "H.265 / HEVC · hardware",
        description = "About half the size of H.264 at the same quality. Uses this phone's media engine.",
        outputExtension = "mp4",
    ),
    H264_SOFTWARE(
        id = "h264-sw-max",
        label = "H.264 · max quality (software)",
        description = "Slow CPU encode with libx264, best quality per byte. For masters and HDR.",
        outputExtension = "mp4",
    ),
    AUDIO_AAC(
        id = "audio-aac",
        label = "Audio only · AAC",
        description = "Extract the audio track to AAC.",
        outputExtension = "m4a",
    ),
    TRIM_COPY(
        id = "trim-copy",
        label = "Trim · no re-encode",
        description = "Cut a section out without touching the picture. Cuts land on keyframes.",
        outputExtension = "mp4",
    ),
}

/** Which engine runs the job — shown to the user, never hidden. */
enum class EngineKind {
    HARDWARE,
    SOFTWARE,
    COPY,
}

/** The outcome of building a command for one preset. */
sealed interface PresetCommand {
    /** A runnable ffmpeg argument list (without the leading `ffmpeg`). */
    data class Ready(
        val command: List<String>,
        val engine: EngineKind,
        /** One line for the job log: which encoder, and why. */
        val note: String,
    ) : PresetCommand

    /** The preset cannot run on this device, or is missing an input it needs. */
    data class Unavailable(val reason: String) : PresetCommand
}

/**
 * Turns a preset + a live capability report into the exact ffmpeg command
 * the app will run. This is the layer in front of FFmpeg: it picks the
 * encoder, pins it by component name, and refuses — loudly — when the
 * hardware the preset needs is not there.
 *
 * FFmpeg facts this relies on (ffmpeg-kit-full-gpl 8.1.9, FFmpeg 8.1.2):
 * - The MediaCodec encoder wrapper (`h264_mediacodec`, `hevc_mediacodec`)
 *   is in every ffmpeg-kit variant, and its `codec_name` option selects the
 *   backend via MediaCodec createCodecByName (NULL by default =
 *   createEncoderByType, i.e. the platform's pick). See FFmpeg's own
 *   encoder docs, "MediaCodec" section.
 * - Hardware encoders take no CRF — bitrate only (`-b:v`). 8-bit input
 *   only, hence `-pix_fmt yuv420p`.
 * - The software path is libx264/libx265, which is why this app is GPL.
 */
object PresetCommands {

    /**
     * Bitrate for hardware encodes until the per-resolution bitrate table
     * lands (research: "no CRF on hardware — bitrate tables only"). 8 Mbps
     * is the v1 starting default for both H.264 and HEVC; the caller may
     * override it once quality tiers exist.
     */
    const val DEFAULT_VIDEO_BITRATE_BPS: Long = 8_000_000L

    /**
     * Presets this device can actually run, in display order. Hardware
     * presets appear only when the report has a hardware encoder for them;
     * software, audio and trim always work.
     */
    fun availablePresets(report: DeviceCodecReport): List<ConversionPreset> =
        ConversionPreset.entries.filter { preset ->
            when (preset) {
                ConversionPreset.H264_HARDWARE ->
                    report.hasHardwareEncoder(DeviceCodecReport.MIME_AVC)
                ConversionPreset.HEVC_HARDWARE ->
                    report.hasHardwareEncoder(DeviceCodecReport.MIME_HEVC)
                else -> true
            }
        }

    /**
     * Build the ffmpeg command for [preset], or say why it cannot run.
     * [trimStartSeconds] / [trimEndSeconds] are only read by [ConversionPreset.TRIM_COPY].
     */
    fun build(
        preset: ConversionPreset,
        inputPath: String,
        outputPath: String,
        report: DeviceCodecReport,
        videoBitrateBps: Long = DEFAULT_VIDEO_BITRATE_BPS,
        trimStartSeconds: Double? = null,
        trimEndSeconds: Double? = null,
    ): PresetCommand = when (preset) {
        ConversionPreset.H264_HARDWARE -> hardwareVideo(
            mimeType = DeviceCodecReport.MIME_AVC,
            ffmpegEncoder = "h264_mediacodec",
            codecLabel = "H.264",
            inputPath = inputPath,
            outputPath = outputPath,
            report = report,
            videoBitrateBps = videoBitrateBps,
            extraVideoArgs = emptyList(),
        )
        ConversionPreset.HEVC_HARDWARE -> hardwareVideo(
            mimeType = DeviceCodecReport.MIME_HEVC,
            ffmpegEncoder = "hevc_mediacodec",
            codecLabel = "HEVC",
            inputPath = inputPath,
            outputPath = outputPath,
            report = report,
            videoBitrateBps = videoBitrateBps,
            // hvc1 tag: the MP4 brand Apple players expect for HEVC.
            extraVideoArgs = listOf("-tag:v", "hvc1"),
        )
        ConversionPreset.H264_SOFTWARE -> PresetCommand.Ready(
            command = listOf(
                "-y", "-i", inputPath,
                "-c:v", "libx264", "-preset", "slow", "-crf", "18",
                "-pix_fmt", "yuv420p",
                "-c:a", "aac", "-b:a", "128k",
                "-movflags", "+faststart",
                outputPath,
            ),
            engine = EngineKind.SOFTWARE,
            note = "Software encode (libx264, CRF 18, slow preset). " +
                "Slow, best quality per byte — no hardware involved.",
        )
        ConversionPreset.AUDIO_AAC -> PresetCommand.Ready(
            command = listOf(
                "-y", "-i", inputPath,
                "-vn", "-c:a", "aac", "-b:a", "192k",
                outputPath,
            ),
            engine = EngineKind.SOFTWARE,
            note = "Audio extraction to AAC 192k. No video encoder involved.",
        )
        ConversionPreset.TRIM_COPY -> {
            if (trimStartSeconds == null || trimEndSeconds == null ||
                trimEndSeconds <= trimStartSeconds
            ) {
                PresetCommand.Unavailable(
                    "Pick a start and end time first — trim needs both.",
                )
            } else {
                PresetCommand.Ready(
                    // -ss before -i: fast seek. With -c copy the cut lands
                    // on the nearest keyframe, so the edges are approximate.
                    command = listOf(
                        "-y", "-ss", trimStartSeconds.toString(),
                        "-to", trimEndSeconds.toString(),
                        "-i", inputPath,
                        "-c", "copy",
                        outputPath,
                    ),
                    engine = EngineKind.COPY,
                    note = "Stream copy, no re-encode. Cuts land on keyframes, " +
                        "so the edges may shift a few frames.",
                )
            }
        }
    }

    private fun hardwareVideo(
        mimeType: String,
        ffmpegEncoder: String,
        codecLabel: String,
        inputPath: String,
        outputPath: String,
        report: DeviceCodecReport,
        videoBitrateBps: Long,
        extraVideoArgs: List<String>,
    ): PresetCommand {
        val encoder = report.bestHardwareEncoder(mimeType)
            ?: return PresetCommand.Unavailable(
                "This phone has no hardware $codecLabel encoder, so the " +
                    "hardware preset cannot run. The software preset can " +
                    "still do this conversion on the CPU.",
            )
        return PresetCommand.Ready(
            command = listOf(
                "-y", "-i", inputPath,
                "-c:v", ffmpegEncoder,
                // Pin the exact component the probe found — never let the
                // platform silently pick a software encoder instead.
                "-codec_name", encoder.componentName,
                "-b:v", videoBitrateBps.toString(),
                "-pix_fmt", "yuv420p",
            ) + extraVideoArgs + listOf(
                "-c:a", "aac", "-b:a", "128k",
                "-movflags", "+faststart",
                outputPath,
            ),
            engine = EngineKind.HARDWARE,
            note = "Hardware encode ($codecLabel) on ${encoder.componentName}.",
        )
    }

    /** One shell-readable line, for the command-mode view and the job log. */
    fun displayCommand(command: List<String>): String =
        command.joinToString(" ") { arg ->
            if (arg.any { it == ' ' || it == '\'' || it == '"' }) "'$arg'" else arg
        }
}
