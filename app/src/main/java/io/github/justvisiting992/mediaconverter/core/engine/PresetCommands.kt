package io.github.justvisiting992.mediaconverter.core.engine

import androidx.annotation.StringRes
import io.github.justvisiting992.mediaconverter.BuildConfig
import io.github.justvisiting992.mediaconverter.R
import io.github.justvisiting992.mediaconverter.core.capability.DeviceCodecReport
import io.github.justvisiting992.mediaconverter.core.locale.Strings

/**
 * What the user wants done to a file, a goal, not an encoder. The engine
 * decides which encoder serves the goal, from the live [DeviceCodecReport].
 *
 * The rule this file exists to enforce: a preset is only offered when the
 * device can actually run it, and a hardware preset never silently falls
 * back to the CPU. If the hardware encoder is missing, [PresetCommands]
 * returns [PresetCommand.Unavailable] with the reason in plain words.
 */
/** Which MediaStore collection an output belongs in. */
enum class MediaKind { VIDEO, AUDIO, IMAGE }

enum class ConversionPreset(
    val id: String,
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int,
    val outputExtension: String,
    val kind: MediaKind,
    val mimeType: String,
) {
    H264_HARDWARE(
        id = "h264-hw",
        labelRes = R.string.preset_h264_hw_label,
        descriptionRes = R.string.preset_h264_hw_desc,
        outputExtension = "mp4",
        kind = MediaKind.VIDEO,
        mimeType = "video/mp4",
    ),
    HEVC_HARDWARE(
        id = "hevc-hw",
        labelRes = R.string.preset_hevc_hw_label,
        descriptionRes = R.string.preset_hevc_hw_desc,
        outputExtension = "mp4",
        kind = MediaKind.VIDEO,
        mimeType = "video/mp4",
    ),
    VP9_HARDWARE(
        id = "vp9-hw",
        labelRes = R.string.preset_vp9_hw_label,
        descriptionRes = R.string.preset_vp9_hw_desc,
        outputExtension = "webm",
        kind = MediaKind.VIDEO,
        mimeType = "video/webm",
    ),
    AV1_HARDWARE(
        id = "av1-hw",
        labelRes = R.string.preset_av1_hw_label,
        descriptionRes = R.string.preset_av1_hw_desc,
        outputExtension = "mp4",
        kind = MediaKind.VIDEO,
        mimeType = "video/mp4",
    ),
    H264_SOFTWARE(
        id = "h264-sw-max",
        labelRes = R.string.preset_h264_sw_label,
        descriptionRes = R.string.preset_h264_sw_desc,
        outputExtension = "mp4",
        kind = MediaKind.VIDEO,
        mimeType = "video/mp4",
    ),
    HEVC_SOFTWARE(
        id = "hevc-sw-max",
        labelRes = R.string.preset_hevc_sw_label,
        descriptionRes = R.string.preset_hevc_sw_desc,
        outputExtension = "mp4",
        kind = MediaKind.VIDEO,
        mimeType = "video/mp4",
    ),
    VP9_SOFTWARE(
        id = "vp9-sw",
        labelRes = R.string.preset_vp9_sw_label,
        descriptionRes = R.string.preset_vp9_sw_desc,
        outputExtension = "webm",
        kind = MediaKind.VIDEO,
        mimeType = "video/webm",
    ),
    AV1_SOFTWARE(
        id = "av1-sw",
        labelRes = R.string.preset_av1_sw_label,
        descriptionRes = R.string.preset_av1_sw_desc,
        outputExtension = "mp4",
        kind = MediaKind.VIDEO,
        mimeType = "video/mp4",
    ),
    AUDIO_AAC(
        id = "audio-aac",
        labelRes = R.string.preset_audio_aac_label,
        descriptionRes = R.string.preset_audio_aac_desc,
        outputExtension = "m4a",
        kind = MediaKind.AUDIO,
        mimeType = "audio/mp4",
    ),
    AUDIO_MP3(
        id = "audio-mp3",
        labelRes = R.string.preset_audio_mp3_label,
        descriptionRes = R.string.preset_audio_mp3_desc,
        outputExtension = "mp3",
        kind = MediaKind.AUDIO,
        mimeType = "audio/mpeg",
    ),
    AUDIO_OPUS(
        id = "audio-opus",
        labelRes = R.string.preset_audio_opus_label,
        descriptionRes = R.string.preset_audio_opus_desc,
        outputExtension = "opus",
        kind = MediaKind.AUDIO,
        mimeType = "audio/opus",
    ),
    AUDIO_FLAC(
        id = "audio-flac",
        labelRes = R.string.preset_audio_flac_label,
        descriptionRes = R.string.preset_audio_flac_desc,
        outputExtension = "flac",
        kind = MediaKind.AUDIO,
        mimeType = "audio/flac",
    ),
    AUDIO_WAV(
        id = "audio-wav",
        labelRes = R.string.preset_audio_wav_label,
        descriptionRes = R.string.preset_audio_wav_desc,
        outputExtension = "wav",
        kind = MediaKind.AUDIO,
        mimeType = "audio/wav",
    ),
    REMUX_MP4(
        id = "remux-mp4",
        labelRes = R.string.preset_remux_mp4_label,
        descriptionRes = R.string.preset_remux_mp4_desc,
        outputExtension = "mp4",
        kind = MediaKind.VIDEO,
        mimeType = "video/mp4",
    ),
    REMUX_MKV(
        id = "remux-mkv",
        labelRes = R.string.preset_remux_mkv_label,
        descriptionRes = R.string.preset_remux_mkv_desc,
        outputExtension = "mkv",
        kind = MediaKind.VIDEO,
        mimeType = "video/x-matroska",
    ),
    TRIM_COPY(
        id = "trim-copy",
        labelRes = R.string.preset_trim_label,
        descriptionRes = R.string.preset_trim_desc,
        outputExtension = "mp4",
        kind = MediaKind.VIDEO,
        mimeType = "video/mp4",
    ),
    GIF(
        id = "gif",
        labelRes = R.string.preset_gif_label,
        descriptionRes = R.string.preset_gif_desc,
        outputExtension = "gif",
        kind = MediaKind.IMAGE,
        mimeType = "image/gif",
    ),
    CUSTOM(
        id = "custom",
        labelRes = R.string.preset_custom_label,
        descriptionRes = R.string.preset_custom_desc,
        outputExtension = "mp4",
        kind = MediaKind.VIDEO,
        mimeType = "video/mp4",
    ),
}

/** Which engine runs the job, shown to the user, never hidden. */
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
 * encoder, pins it by component name, and refuses, loudly, when the
 * hardware the preset needs is not there.
 *
 * The command is also input-aware. Decoding is half the pipeline: a
 * hardware encoder fed by a software decoder still pegs the CPU (a
 * 1080p AV1 WebM decodes through libdav1d at ~70% CPU while the media
 * engine idles between frames, measured on-device, 2026-11-26). So
 * when the probe found a hardware decoder for the input's codec, the
 * command selects FFmpeg's matching `*_mediacodec` decoder. If that
 * decoder rejects the file it fails within seconds, and the queue
 * retries once with software decode and says so, see ConversionQueue.
 *
 * FFmpeg facts this relies on (ffmpeg-kit-full-gpl 8.1.9, FFmpeg 8.1.2):
 * - The MediaCodec wrappers (`h264_mediacodec`, `hevc_mediacodec`,
 *   `vp9_mediacodec`, `av1_mediacodec` for decode; the first two for
 *   encode) are in every ffmpeg-kit variant. The encoder's `codec_name`
 *   option selects the backend via MediaCodec createCodecByName (NULL by
 *   default = createEncoderByType, i.e. the platform's pick). Decoders
 *   have no equivalent pin, the wrapper resolves the platform decoder
 *   for the codec, which on a probed device is the hardware component.
 * - Hardware encoders take no CRF, bitrate only (`-b:v`). 8-bit input
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
                ConversionPreset.VP9_HARDWARE ->
                    // FFmpeg's VP9/AV1 MediaCodec encoder wrappers arrived
                    // in FFmpeg 6.1; the legacy edition's FFmpeg 6.0 does
                    // not have them, so these presets are never offered
                    // there, whatever the probe finds.
                    !BuildConfig.LEGACY_ENGINE &&
                        report.hasHardwareEncoder(DeviceCodecReport.MIME_VP9)
                ConversionPreset.AV1_HARDWARE ->
                    !BuildConfig.LEGACY_ENGINE &&
                        report.hasHardwareEncoder(DeviceCodecReport.MIME_AV1)
                else -> true
            }
        }

    /**
     * Build the ffmpeg command for [preset], or say why it cannot run.
     *
     * [input] is the probed description of the source file; when present
     * it drives the decoder choice and the bitrate (see [hardwareBitrate]).
     * [softwareDecode] forces CPU decode, the queue sets it when it
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
        ConversionPreset.VP9_HARDWARE -> hardwareVideo(
            mimeType = DeviceCodecReport.MIME_VP9,
            ffmpegEncoder = "vp9_mediacodec",
            codecLabel = "VP9",
            inputPath = inputPath,
            outputPath = outputPath,
            report = report,
            input = input,
            softwareDecode = softwareDecode,
            videoBitrateBps = videoBitrateBps,
            extraVideoArgs = emptyList(),
            audioArgs = listOf("-c:a", "libopus", "-b:a", "128k"),
            containerArgs = emptyList(),
        )
        ConversionPreset.AV1_HARDWARE -> hardwareVideo(
            mimeType = DeviceCodecReport.MIME_AV1,
            ffmpegEncoder = "av1_mediacodec",
            codecLabel = "AV1",
            inputPath = inputPath,
            outputPath = outputPath,
            report = report,
            input = input,
            softwareDecode = softwareDecode,
            videoBitrateBps = videoBitrateBps,
            extraVideoArgs = emptyList(),
        )
        ConversionPreset.H264_SOFTWARE -> {
            val decode = decodeChoice(report, input, softwareDecode)
            PresetCommand.Ready(
                command = listOf("-y") + decode.args + listOf(
                    "-i", inputPath,
                    "-threads", "0",
                    "-c:v", "libx264", "-preset", "slow", "-crf", "18",
                    "-pix_fmt", "yuv420p",
                    "-c:a", "aac", "-b:a", "128k",
                    "-movflags", "+faststart",
                    outputPath,
                ),
                engine = EngineKind.SOFTWARE,
                note = Strings.get(R.string.note_sw_x264, decode.label),
                usedHardwareDecode = decode.hardware,
            )
        }
        ConversionPreset.HEVC_SOFTWARE -> {
            val decode = decodeChoice(report, input, softwareDecode)
            PresetCommand.Ready(
                command = listOf("-y") + decode.args + listOf(
                    "-i", inputPath,
                    "-threads", "0",
                    "-c:v", "libx265", "-preset", "medium", "-crf", "22",
                    "-pix_fmt", "yuv420p", "-tag:v", "hvc1",
                    "-c:a", "aac", "-b:a", "128k",
                    "-movflags", "+faststart",
                    outputPath,
                ),
                engine = EngineKind.SOFTWARE,
                note = Strings.get(R.string.note_sw_x265, decode.label),
                usedHardwareDecode = decode.hardware,
            )
        }
        ConversionPreset.VP9_SOFTWARE -> {
            val decode = decodeChoice(report, input, softwareDecode)
            PresetCommand.Ready(
                command = listOf("-y") + decode.args + listOf(
                    "-i", inputPath,
                    "-threads", "0",
                    // Constant-quality VP9: -b:v 0 with a CRF target.
                    "-c:v", "libvpx-vp9", "-b:v", "0", "-crf", "30",
                    "-deadline", "good", "-cpu-used", "2", "-row-mt", "1",
                    "-pix_fmt", "yuv420p",
                    "-c:a", "libopus", "-b:a", "128k",
                    outputPath,
                ),
                engine = EngineKind.SOFTWARE,
                note = Strings.get(R.string.note_sw_vp9, decode.label),
                usedHardwareDecode = decode.hardware,
            )
        }
        ConversionPreset.AV1_SOFTWARE -> {
            val decode = decodeChoice(report, input, softwareDecode)
            PresetCommand.Ready(
                command = listOf("-y") + decode.args + listOf(
                    "-i", inputPath,
                    "-threads", "0",
                    // libaom is the AV1 encoder in this FFmpeg build (no
                    // SVT-AV1); cpu-used 5 is the usable speed/quality
                    // point, and it is still slow, hence the label.
                    "-c:v", "libaom-av1", "-crf", "32", "-b:v", "0",
                    "-cpu-used", "5", "-row-mt", "1",
                    "-pix_fmt", "yuv420p",
                    "-c:a", "aac", "-b:a", "128k",
                    "-movflags", "+faststart",
                    outputPath,
                ),
                engine = EngineKind.SOFTWARE,
                note = Strings.get(R.string.note_sw_av1, decode.label),
                usedHardwareDecode = decode.hardware,
            )
        }
        ConversionPreset.CUSTOM -> PresetCommand.Unavailable(
            Strings.get(R.string.refusal_custom_path),
        )
        ConversionPreset.AUDIO_AAC -> PresetCommand.Ready(
            command = listOf(
                "-y", "-i", inputPath,
                "-vn", "-c:a", "aac", "-b:a", "192k",
                outputPath,
            ),
            engine = EngineKind.SOFTWARE,
            note = Strings.get(R.string.note_audio_aac),
        )
        ConversionPreset.AUDIO_MP3 -> PresetCommand.Ready(
            command = listOf(
                "-y", "-i", inputPath,
                "-vn", "-c:a", "libmp3lame", "-b:a", "192k",
                outputPath,
            ),
            engine = EngineKind.SOFTWARE,
            note = Strings.get(R.string.note_audio_mp3),
        )
        ConversionPreset.AUDIO_OPUS -> PresetCommand.Ready(
            command = listOf(
                "-y", "-i", inputPath,
                "-vn", "-c:a", "libopus", "-b:a", "128k",
                outputPath,
            ),
            engine = EngineKind.SOFTWARE,
            note = Strings.get(R.string.note_audio_opus),
        )
        ConversionPreset.AUDIO_FLAC -> PresetCommand.Ready(
            command = listOf(
                "-y", "-i", inputPath,
                "-vn", "-c:a", "flac",
                outputPath,
            ),
            engine = EngineKind.SOFTWARE,
            note = Strings.get(R.string.note_audio_flac),
        )
        ConversionPreset.AUDIO_WAV -> PresetCommand.Ready(
            command = listOf(
                "-y", "-i", inputPath,
                "-vn", "-c:a", "pcm_s16le",
                outputPath,
            ),
            engine = EngineKind.SOFTWARE,
            note = Strings.get(R.string.note_audio_wav),
        )
        ConversionPreset.REMUX_MP4 -> PresetCommand.Ready(
            command = listOf(
                "-y", "-i", inputPath,
                "-c", "copy",
                "-movflags", "+faststart",
                outputPath,
            ),
            engine = EngineKind.COPY,
            note = Strings.get(R.string.note_remux_mp4),
        )
        ConversionPreset.REMUX_MKV -> PresetCommand.Ready(
            command = listOf(
                "-y", "-i", inputPath,
                "-c", "copy",
                outputPath,
            ),
            engine = EngineKind.COPY,
            note = Strings.get(R.string.note_remux_mkv),
        )
        ConversionPreset.GIF -> {
            val decode = decodeChoice(report, input, softwareDecode)
            PresetCommand.Ready(
                command = listOf("-y") + decode.args + listOf(
                    "-i", inputPath,
                    "-threads", "0",
                    "-an",
                    "-vf", "fps=12,scale=480:-2:flags=lanczos",
                    "-loop", "0",
                    outputPath,
                ),
                engine = EngineKind.SOFTWARE,
                note = Strings.get(R.string.note_gif, decode.label),
                usedHardwareDecode = decode.hardware,
            )
        }
        ConversionPreset.TRIM_COPY -> {
            if (trimStartSeconds == null || trimEndSeconds == null ||
                trimEndSeconds <= trimStartSeconds
            ) {
                PresetCommand.Unavailable(
                    Strings.get(R.string.refusal_trim_times),
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
                    note = Strings.get(R.string.note_trim),
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
        audioArgs: List<String> = listOf("-c:a", "aac", "-b:a", "128k"),
        containerArgs: List<String> = listOf("-movflags", "+faststart"),
    ): PresetCommand {
        val encoder = report.bestHardwareEncoder(mimeType)
            ?: return PresetCommand.Unavailable(
                Strings.get(R.string.refusal_no_hw_encoder, codecLabel),
            )
        val decode = decodeChoice(report, input, softwareDecode)
        val bitrate = videoBitrateBps
            ?: hardwareBitrate(mimeType, input, encoder.maxBitrate)
        return PresetCommand.Ready(
            command = listOf("-y") + decode.args + listOf(
                "-i", inputPath,
                "-threads", "0",
                "-c:v", ffmpegEncoder,
                // Pin the exact component the probe found, never let the
                // platform silently pick a software encoder instead.
                "-codec_name", encoder.componentName,
                "-b:v", bitrate.toString(),
                "-pix_fmt", "yuv420p",
            ) + extraVideoArgs + audioArgs + containerArgs + listOf(outputPath),
            engine = EngineKind.HARDWARE,
            note = Strings.get(
                R.string.note_hw_encode,
                codecLabel,
                encoder.componentName,
                "%.1f".format(bitrate / 1e6),
                decode.label,
            ),
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
     * probe found a hardware decoder for it, decode output is
     * bit-identical either way, so this costs no quality. Falls back to
     * CPU decode, and says so, when there is no hardware decoder, the
     * codec has no MediaCodec wrapper, the input is unknown, or the
     * caller forced software (the retry ladder).
     */
    private fun decodeChoice(
        report: DeviceCodecReport,
        input: InputInfo?,
        softwareDecode: Boolean,
        softwareLabel: String? = null,
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
                label = softwareLabel
                    ?: Strings.get(R.string.decode_cpu_retry),
            )
        }
        if (wrapper == null || mime == null) {
            return DecodeChoice(
                args = emptyList(),
                hardware = false,
                label = Strings.get(
                    R.string.decode_cpu_no_path,
                    codec ?: Strings.get(R.string.decode_this_input),
                ),
            )
        }
        val decoder = report.bestHardwareDecoder(mime)
            ?: return DecodeChoice(
                args = emptyList(),
                hardware = false,
                label = Strings.get(
                    R.string.decode_cpu_no_decoder,
                    codec?.uppercase() ?: "",
                ),
            )
        return DecodeChoice(
            args = listOf("-c:v", wrapper),
            hardware = true,
            label = Strings.get(R.string.decode_hw, wrapper, decoder.componentName),
        )
    }

    /**
     * The bitrate for a hardware encode: a per-resolution table value,
     * then capped against the source. Hardware encoders have no CRF, so
     * the table is the quality knob, but a flat table value against an
     * ultra-low-bitrate source (a 0.25 Mbps AV1 WebM) inflates the
     * output several-fold for nothing. The cap is 2x the source video
     * bitrate: a newer source codec (AV1) needs roughly 1.3–1.5x its
     * bitrate re-encoded as HEVC/H.264 at equal quality, plus headroom
     * for the hardware encoder being less efficient than a software one.
     * When the video stream doesn't report its own bitrate (WebM often
     * doesn't), the file's overall bitrate stands in, it slightly
     * overstates the video share, which only loosens the cap a little.
     * The result is also clamped to what the encoder component accepts.
     */
    private fun hardwareBitrate(
        mimeType: String,
        input: InputInfo?,
        encoderMaxBitrate: Int?,
    ): Long {
        val height = input?.height?.toInt() ?: 1080
        val hevc = mimeType == DeviceCodecReport.MIME_HEVC ||
            mimeType == DeviceCodecReport.MIME_VP9 ||
            mimeType == DeviceCodecReport.MIME_AV1
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

    // ------------------------------------------------------------------
    // Manual mode: the user picks container, codecs, quality and size.
    // Same honesty rules as the presets, hardware choices are checked
    // against the probe, impossible combinations are refused in words.
    // ------------------------------------------------------------------

    /** Containers a manual conversion can target. */
    enum class ManualContainer(val label: String, val extension: String, val mimeType: String) {
        MP4("MP4", "mp4", "video/mp4"),
        MKV("MKV", "mkv", "video/x-matroska"),
        WEBM("WebM", "webm", "video/webm"),
    }

    enum class ManualVideoCodec(@StringRes val labelRes: Int) {
        COPY(R.string.mc_copy),
        H264_HW(R.string.mc_h264_hw),
        HEVC_HW(R.string.mc_hevc_hw),
        VP9_HW(R.string.mc_vp9_hw),
        AV1_HW(R.string.mc_av1_hw),
        H264_SW(R.string.mc_h264_sw),
        HEVC_SW(R.string.mc_hevc_sw),
        VP9_SW(R.string.mc_vp9_sw),
        AV1_SW(R.string.mc_av1_sw),
        NONE(R.string.mc_none),
    }

    enum class ManualAudioCodec(
        @StringRes val labelRes: Int,
        /** Output file shape when the job is audio-only. */
        val extension: String,
        val mimeType: String,
    ) {
        COPY(R.string.ma_copy, "", ""),
        AAC(R.string.ma_aac, "m4a", "audio/mp4"),
        MP3(R.string.ma_mp3, "mp3", "audio/mpeg"),
        OPUS(R.string.ma_opus, "opus", "audio/opus"),
        NONE(R.string.ma_none, "", ""),
    }

    /** Everything the manual panel lets the user choose. */
    data class ManualOptions(
        val container: ManualContainer = ManualContainer.MP4,
        val video: ManualVideoCodec = ManualVideoCodec.HEVC_HW,
        val audio: ManualAudioCodec = ManualAudioCodec.AAC,
        /** Hardware video bitrate; null = the table value for this input. */
        val videoBitrateKbps: Int? = null,
        /** Software quality (CRF); null = the codec's preset default. */
        val crf: Int? = null,
        val audioBitrateKbps: Int = 128,
        /** Cap the output height (downscale only); null = original size. */
        val maxHeight: Int? = null,
        /** True = decode on the media engine when a decoder exists. */
        val hardwareDecode: Boolean = true,
    )

    /** The output file a manual job produces: extension, MIME, collection. */
    fun manualOutputSpec(options: ManualOptions): Triple<String, String, MediaKind> =
        if (options.video == ManualVideoCodec.NONE) {
            Triple(options.audio.extension, options.audio.mimeType, MediaKind.AUDIO)
        } else {
            Triple(options.container.extension, options.container.mimeType, MediaKind.VIDEO)
        }

    /**
     * Build the ffmpeg command for a manual conversion. Refuses, in
     * words, when the combination cannot work: a missing hardware
     * encoder, audio-copy with no video (the output format would be
     * unknowable), or a WebM container with codecs WebM cannot hold.
     */
    fun buildManual(
        options: ManualOptions,
        inputPath: String,
        outputPath: String,
        report: DeviceCodecReport,
        input: InputInfo? = null,
    ): PresetCommand {
        if (options.video == ManualVideoCodec.NONE) {
            if (options.audio == ManualAudioCodec.COPY ||
                options.audio == ManualAudioCodec.NONE
            ) {
                return PresetCommand.Unavailable(
                    Strings.get(R.string.refusal_audio_only_codec),
                )
            }
            val encoder = when (options.audio) {
                ManualAudioCodec.AAC -> listOf("-c:a", "aac", "-b:a", "${options.audioBitrateKbps}k")
                ManualAudioCodec.MP3 -> listOf("-c:a", "libmp3lame", "-b:a", "${options.audioBitrateKbps}k")
                ManualAudioCodec.OPUS -> listOf("-c:a", "libopus", "-b:a", "${options.audioBitrateKbps}k")
                else -> return PresetCommand.Unavailable(
                    Strings.get(R.string.refusal_pick_audio),
                )
            }
            return PresetCommand.Ready(
                command = listOf("-y", "-i", inputPath, "-vn") + encoder + listOf(outputPath),
                engine = EngineKind.SOFTWARE,
                note = Strings.get(
                    R.string.note_custom_audio,
                    Strings.get(options.audio.labelRes),
                    options.audioBitrateKbps,
                ),
            )
        }
        if (options.container == ManualContainer.WEBM &&
            options.video != ManualVideoCodec.VP9_SW &&
            options.video != ManualVideoCodec.AV1_SW &&
            options.video != ManualVideoCodec.VP9_HW &&
            options.video != ManualVideoCodec.AV1_HW &&
            options.video != ManualVideoCodec.COPY
        ) {
            return PresetCommand.Unavailable(
                Strings.get(R.string.refusal_webm_video),
            )
        }
        if (options.container == ManualContainer.WEBM &&
            (options.audio == ManualAudioCodec.AAC || options.audio == ManualAudioCodec.MP3)
        ) {
            return PresetCommand.Unavailable(
                Strings.get(R.string.refusal_webm_audio),
            )
        }

        val decode = decodeChoice(
            report, input,
            softwareDecode = !options.hardwareDecode,
            softwareLabel = Strings.get(R.string.decode_cpu_choice),
        )
        val args = mutableListOf("-y")
        args += decode.args
        args += listOf("-i", inputPath, "-threads", "0")
        var engine = EngineKind.SOFTWARE

        // Video side.
        when (options.video) {
            ManualVideoCodec.COPY -> {
                args += listOf("-c:v", "copy")
                engine = EngineKind.COPY
            }
            ManualVideoCodec.H264_HW, ManualVideoCodec.HEVC_HW,
            ManualVideoCodec.VP9_HW, ManualVideoCodec.AV1_HW -> {
                if (BuildConfig.LEGACY_ENGINE &&
                    (options.video == ManualVideoCodec.VP9_HW ||
                        options.video == ManualVideoCodec.AV1_HW)
                ) {
                    return PresetCommand.Unavailable(
                        Strings.get(R.string.refusal_legacy_vp9_av1),
                    )
                }
                val (mime, wrapper, shortName) = when (options.video) {
                    ManualVideoCodec.HEVC_HW ->
                        Triple(DeviceCodecReport.MIME_HEVC, "hevc_mediacodec", "HEVC")
                    ManualVideoCodec.VP9_HW ->
                        Triple(DeviceCodecReport.MIME_VP9, "vp9_mediacodec", "VP9")
                    ManualVideoCodec.AV1_HW ->
                        Triple(DeviceCodecReport.MIME_AV1, "av1_mediacodec", "AV1")
                    else ->
                        Triple(DeviceCodecReport.MIME_AVC, "h264_mediacodec", "H.264")
                }
                val encoder = report.bestHardwareEncoder(mime)
                    ?: return PresetCommand.Unavailable(
                        Strings.get(R.string.refusal_no_hw_encoder_manual, shortName),
                    )
                val bitrate = options.videoBitrateKbps?.let { it * 1000L }
                    ?: hardwareBitrate(mime, input, encoder.maxBitrate)
                args += listOf(
                    "-c:v", wrapper,
                    "-codec_name", encoder.componentName,
                    "-b:v", bitrate.toString(),
                    "-pix_fmt", "yuv420p",
                )
                if (options.video == ManualVideoCodec.HEVC_HW) {
                    args += listOf("-tag:v", "hvc1")
                }
                engine = EngineKind.HARDWARE
            }
            ManualVideoCodec.H264_SW -> args += listOf(
                "-c:v", "libx264", "-preset", "slow",
                "-crf", (options.crf ?: 18).toString(), "-pix_fmt", "yuv420p",
            )
            ManualVideoCodec.HEVC_SW -> args += listOf(
                "-c:v", "libx265", "-preset", "medium",
                "-crf", (options.crf ?: 22).toString(),
                "-pix_fmt", "yuv420p", "-tag:v", "hvc1",
            )
            ManualVideoCodec.VP9_SW -> args += listOf(
                "-c:v", "libvpx-vp9", "-b:v", "0",
                "-crf", (options.crf ?: 30).toString(),
                "-deadline", "good", "-cpu-used", "2", "-row-mt", "1",
                "-pix_fmt", "yuv420p",
            )
            ManualVideoCodec.AV1_SW -> args += listOf(
                "-c:v", "libaom-av1",
                "-crf", (options.crf ?: 32).toString(), "-b:v", "0",
                "-cpu-used", "5", "-row-mt", "1", "-pix_fmt", "yuv420p",
            )
            ManualVideoCodec.NONE -> Unit // handled above
        }

        // Downscale cap (re-encodes only; a copied stream keeps its size).
        if (options.maxHeight != null && options.video != ManualVideoCodec.COPY) {
            args += listOf("-vf", "scale=-2:min(ih\\,${options.maxHeight})")
        }

        // Audio side.
        when (options.audio) {
            ManualAudioCodec.COPY -> args += listOf("-c:a", "copy")
            ManualAudioCodec.AAC -> args += listOf(
                "-c:a", "aac", "-b:a", "${options.audioBitrateKbps}k",
            )
            ManualAudioCodec.MP3 -> args += listOf(
                "-c:a", "libmp3lame", "-b:a", "${options.audioBitrateKbps}k",
            )
            ManualAudioCodec.OPUS -> args += listOf(
                "-c:a", "libopus", "-b:a", "${options.audioBitrateKbps}k",
            )
            ManualAudioCodec.NONE -> args += listOf("-an")
        }

        if (options.container == ManualContainer.MP4) {
            args += listOf("-movflags", "+faststart")
        }
        args += outputPath
        return PresetCommand.Ready(
            command = args,
            engine = engine,
            note = Strings.get(
                R.string.note_custom_full,
                options.container.label,
                Strings.get(options.video.labelRes),
                Strings.get(options.audio.labelRes),
                decode.label,
            ),
            usedHardwareDecode = decode.hardware,
        )
    }

    /** One shell-readable line, for the command-mode view and the job log. */
    fun displayCommand(command: List<String>): String =
        command.joinToString(" ") { arg ->
            if (arg.any { it == ' ' || it == '\'' || it == '"' }) "'$arg'" else arg
        }
}
