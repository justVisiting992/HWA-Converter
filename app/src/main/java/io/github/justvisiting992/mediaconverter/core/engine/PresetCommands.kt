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
        /** True when the command decodes the input on the media engine. */
        val usedHardwareDecode: Boolean = false,
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
 * The command is also input-aware. Decoding is half the pipeline: a
 * hardware encoder fed by a software decoder still pegs the CPU (a
 * 1080p AV1 WebM decodes through libdav1d at ~70% CPU while the media
 * engine idles between frames — measured on-device, 2026-11-26). So
 * when the probe found a hardware decoder for the input's codec, the
 * command selects FFmpeg's matching `*_mediacodec` decoder. If that
 * decoder rejects the file it fails within seconds, and the queue
 * retries once with software decode and says so — see ConversionQueue.
 *
 * FFmpeg facts this relies on (ffmpeg-kit-full-gpl 8.1.9, FFmpeg 8.1.2):
 * - The MediaCodec wrappers (`h264_mediacodec`, `hevc_mediacodec`,
 *   `vp9_mediacodec`, `av1_mediacodec` for decode; the first two for
 *   encode) are in every ffmpeg-kit variant. The encoder's `codec_name`
 *   option selects the backend via MediaCodec createCodecByName (NULL by
 *   default = createEncoderByType, i.e. the platform's pick). Decoders
 *   have no equivalent pin — the wrapper resolves the platform decoder
 *   for the codec, which on a probed device is the hardware component.
 * - Hardware encoders take no CRF — bitrate only (`-b:v`). 8-bit input
 *   only, hence `-pix_fmt yuv420p`.
 * - The software path is libx264/libx265, which is why this app is GPL.
 */
object PresetCommands {

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
     *
     * [input] is the probed description of the source file; when present
     * it drives the decoder choice and the bitrate (see [hardwareBitrate]).
     * [softwareDecode] forces CPU decode — the queue sets it when it
     * retries a job whose hardware decoder failed fast. [trimStartSeconds]
     * / [trimEndSeconds] are only read by [ConversionPreset.TRIM_COPY].
     */
    fun build(
        preset: ConversionPreset,
        inputPath: String,
        outputPath: String,
        report: DeviceCodecReport,
        input: InputInfo? = null,
        softwareDecode: Boolean = false,
        videoBitrateBps: Long? = null,
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
            input = input,
            softwareDecode = softwareDecode,
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
            input = input,
            softwareDecode = softwareDecode,
            videoBitrateBps = videoBitrateBps,
            // hvc1 tag: the MP4 brand Apple players expect for HEVC.
            extraVideoArgs = listOf("-tag:v", "hvc1"),
        )
        ConversionPreset.H264_SOFTWARE -> {
            val decode = decodeChoice(report, input, softwareDecode)
            PresetCommand.Ready(
                command = listOf("-y") + decode.args + listOf(
                    "-i", inputPath,
                    "-c:v", "libx264", "-preset", "slow", "-crf", "18",
                    "-pix_fmt", "yuv420p",
                    "-c:a", "aac", "-b:a", "128k",
                    "-movflags", "+faststart",
                    outputPath,
                ),
                engine = EngineKind.SOFTWARE,
                note = "Software encode (libx264, CRF 18, slow preset). " +
                    "Slow, best quality per byte. Decode: ${decode.label}.",
                usedHardwareDecode = decode.hardware,
            )
        }
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
        input: InputInfo?,
        softwareDecode: Boolean,
        videoBitrateBps: Long?,
        extraVideoArgs: List<String>,
    ): PresetCommand {
        val encoder = report.bestHardwareEncoder(mimeType)
            ?: return PresetCommand.Unavailable(
                "This phone has no hardware $codecLabel encoder, so the " +
                    "hardware preset cannot run. The software preset can " +
                    "still do this conversion on the CPU.",
            )
        val decode = decodeChoice(report, input, softwareDecode)
        val bitrate = videoBitrateBps
            ?: hardwareBitrate(mimeType, input, encoder.maxBitrate)
        return PresetCommand.Ready(
            command = listOf("-y") + decode.args + listOf(
                "-i", inputPath,
                "-c:v", ffmpegEncoder,
                // Pin the exact component the probe found — never let the
                // platform silently pick a software encoder instead.
                "-codec_name", encoder.componentName,
                "-b:v", bitrate.toString(),
                "-pix_fmt", "yuv420p",
            ) + extraVideoArgs + listOf(
                "-c:a", "aac", "-b:a", "128k",
                "-movflags", "+faststart",
                outputPath,
            ),
            engine = EngineKind.HARDWARE,
            note = "Hardware encode ($codecLabel) on ${encoder.componentName} " +
                "at ${"%.1f".format(bitrate / 1e6)} Mbps. Decode: ${decode.label}.",
            usedHardwareDecode = decode.hardware,
        )
    }

    /** A chosen input decoder: ffmpeg input args plus how to describe it. */
    private data class DecodeChoice(
        /** Input-side args placed before `-i`; empty for software decode. */
        val args: List<String>,
        val hardware: Boolean,
        /** Short label for the job note, e.g. "hardware (av1_mediacodec)". */
        val label: String,
    )

    /**
     * Pick the decoder for the input's video codec. Hardware when the
     * probe found a hardware decoder for it — decode output is
     * bit-identical either way, so this costs no quality. Falls back to
     * CPU decode, and says so, when there is no hardware decoder, the
     * codec has no MediaCodec wrapper, the input is unknown, or the
     * caller forced software (the retry ladder).
     */
    private fun decodeChoice(
        report: DeviceCodecReport,
        input: InputInfo?,
        softwareDecode: Boolean,
    ): DecodeChoice {
        val codec = input?.videoCodec?.lowercase()
        val (wrapper, mime) = when (codec) {
            "av1" -> "av1_mediacodec" to DeviceCodecReport.MIME_AV1
            "vp9" -> "vp9_mediacodec" to DeviceCodecReport.MIME_VP9
            "h264", "avc" -> "h264_mediacodec" to DeviceCodecReport.MIME_AVC
            "hevc", "h265" -> "hevc_mediacodec" to DeviceCodecReport.MIME_HEVC
            else -> null to null
        }
        if (softwareDecode) {
            return DecodeChoice(
                args = emptyList(),
                hardware = false,
                label = "CPU (retried after the hardware decoder failed)",
            )
        }
        if (wrapper == null || mime == null) {
            return DecodeChoice(
                args = emptyList(),
                hardware = false,
                label = "CPU (no hardware decoder path for ${codec ?: "this input"})",
            )
        }
        val decoder = report.bestHardwareDecoder(mime)
            ?: return DecodeChoice(
                args = emptyList(),
                hardware = false,
                label = "CPU (this phone has no hardware ${codec?.uppercase()} decoder)",
            )
        return DecodeChoice(
            args = listOf("-c:v", wrapper),
            hardware = true,
            label = "hardware ($wrapper on ${decoder.componentName})",
        )
    }

    /**
     * The bitrate for a hardware encode: a per-resolution table value,
     * then capped against the source. Hardware encoders have no CRF, so
     * the table is the quality knob — but a flat table value against an
     * ultra-low-bitrate source (a 0.25 Mbps AV1 WebM) inflates the
     * output several-fold for nothing. The cap is 2x the source video
     * bitrate: a newer source codec (AV1) needs roughly 1.3–1.5x its
     * bitrate re-encoded as HEVC/H.264 at equal quality, plus headroom
     * for the hardware encoder being less efficient than a software one.
     * When the video stream doesn't report its own bitrate (WebM often
     * doesn't), the file's overall bitrate stands in — it slightly
     * overstates the video share, which only loosens the cap a little.
     * The result is also clamped to what the encoder component accepts.
     */
    private fun hardwareBitrate(
        mimeType: String,
        input: InputInfo?,
        encoderMaxBitrate: Int?,
    ): Long {
        val height = input?.height?.toInt() ?: 1080
        val hevc = mimeType == DeviceCodecReport.MIME_HEVC
        var target = when {
            height >= 2160 -> if (hevc) 16_000_000L else 20_000_000L
            height >= 1080 -> if (hevc) 4_500_000L else 8_000_000L
            height >= 720 -> if (hevc) 2_500_000L else 4_000_000L
            height >= 480 -> if (hevc) 1_200_000L else 2_000_000L
            else -> if (hevc) 800_000L else 1_200_000L
        }
        val source = input?.videoBitrateBps ?: input?.overallBitrateBps
        if (source != null && source > 0) {
            val cap = maxOf(source * 2, 400_000L)
            if (cap < target) target = cap
        }
        if (encoderMaxBitrate != null && encoderMaxBitrate > 0 &&
            target > encoderMaxBitrate
        ) {
            target = encoderMaxBitrate.toLong()
        }
        return target
    }

    /** One shell-readable line, for the command-mode view and the job log. */
    fun displayCommand(command: List<String>): String =
        command.joinToString(" ") { arg ->
            if (arg.any { it == ' ' || it == '\'' || it == '"' }) "'$arg'" else arg
        }
}
