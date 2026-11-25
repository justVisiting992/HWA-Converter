package io.github.justvisiting992.mediaconverter.core.capability

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build

/**
 * One encoder component as the device reports it, reduced to what the
 * conversion engine needs to make decisions.
 */
data class EncoderCapability(
    /** Codec2 / OMX component name, e.g. "c2.mtk.avc.encoder". */
    val componentName: String,
    /** MIME type, e.g. "video/avc". */
    val mimeType: String,
    /** True when the component runs on the SoC's media engine, not the CPU. */
    val isHardware: Boolean,
    /** Largest frame the encoder accepts, or null for non-video codecs. */
    val maxWidth: Int?,
    val maxHeight: Int?,
    /** Upper bound of the supported bitrate range, bits/sec. */
    val maxBitrate: Int?,
    /** Human-readable profile names the encoder advertises, best first. */
    val profiles: List<String>,
)

/** Everything this device can hardware-encode, probed once at runtime. */
data class DeviceCodecReport(
    val videoEncoders: List<EncoderCapability>,
    val audioEncoders: List<EncoderCapability>,
) {
    /** The preferred hardware encoder component for a MIME type, if any. */
    fun bestHardwareEncoder(mimeType: String): EncoderCapability? =
        videoEncoders.firstOrNull { it.mimeType == mimeType && it.isHardware }
            ?: audioEncoders.firstOrNull { it.mimeType == mimeType && it.isHardware }

    fun hasHardwareEncoder(mimeType: String): Boolean = bestHardwareEncoder(mimeType) != null

    companion object {
        const val MIME_AVC = "video/avc"
        const val MIME_HEVC = "video/hevc"
        const val MIME_VP9 = "video/x-vnd.on2.vp9"
        const val MIME_AV1 = "video/av01"
    }
}

/**
 * Runtime capability detection, modelled on the patterns that survived in
 * LiTr and Media3 Transformer: ask the platform what the encoder actually
 * advertises, never assume from the chipset name. The Dimensity 9400+ can
 * encode HEVC at 4K, but the platform only *guarantees* far less — so this
 * probe, not a spec sheet, is what our presets are built from.
 */
object HardwareCodecProbe {

    fun probe(): DeviceCodecReport {
        val video = mutableListOf<EncoderCapability>()
        val audio = mutableListOf<EncoderCapability>()
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        for (info in codecList.codecInfos) {
            if (!info.isEncoder) continue
            for (mime in info.supportedTypes) {
                val isVideo = mime.startsWith("video/")
                val isAudio = mime.startsWith("audio/")
                if (!isVideo && !isAudio) continue
                val caps = runCatching { info.getCapabilitiesForType(mime) }.getOrNull() ?: continue
                val videoCaps = if (isVideo) caps.videoCapabilities else null
                val entry = EncoderCapability(
                    componentName = info.name,
                    mimeType = mime,
                    isHardware = isHardware(info),
                    maxWidth = videoCaps?.supportedWidths?.upper,
                    maxHeight = videoCaps?.let {
                        it.getSupportedHeightsFor(it.supportedWidths.upper).upper
                    },
                    // EncoderCapabilities has no bitrate range of its own; the
                    // range lives on the video/audio capabilities.
                    maxBitrate = videoCaps?.bitrateRange?.upper
                        ?: caps.audioCapabilities?.bitrateRange?.upper,
                    profiles = rankProfiles(mime, caps.profileLevels.map { it.profile }),
                )
                (if (isVideo) video else audio).add(entry)
            }
        }
        // Hardware first, then by capability, so index 0 is the best component.
        val order = compareByDescending<EncoderCapability> { it.isHardware }
            .thenByDescending { (it.maxWidth ?: 0) * (it.maxHeight ?: 0) }
        return DeviceCodecReport(
            videoEncoders = video.sortedWith(order),
            audioEncoders = audio.sortedWith(order),
        )
    }

    private fun isHardware(info: MediaCodecInfo): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            info.isHardwareAccelerated
        } else {
            // Pre-API 29 fallback: Google's OMX.google.* / c2.android.* are the
            // software reference codecs; vendor components are hardware.
            val name = info.name.lowercase()
            !name.startsWith("omx.google.") && !name.startsWith("c2.android.") &&
                !name.contains(".sw.")
        }

    /**
     * Profile preference order per codec, best first (after LiTr's ranking).
     * Only profiles the device advertises survive the filter, in our order.
     */
    private fun rankProfiles(mimeType: String, advertised: List<Int>): List<String> {
        val (names, preference) = when (mimeType) {
            DeviceCodecReport.MIME_AVC -> AVC_PROFILES to AVC_PREFERENCE
            DeviceCodecReport.MIME_HEVC -> HEVC_PROFILES to HEVC_PREFERENCE
            DeviceCodecReport.MIME_VP9 -> VP9_PROFILES to VP9_PREFERENCE
            DeviceCodecReport.MIME_AV1 -> AV1_PROFILES to AV1_PREFERENCE
            else -> return emptyList()
        }
        return preference.filter { it in advertised }.map { names[it] ?: "profile $it" }
    }

    private val AVC_PROFILES = mapOf(
        MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline to "Baseline",
        MediaCodecInfo.CodecProfileLevel.AVCProfileMain to "Main",
        MediaCodecInfo.CodecProfileLevel.AVCProfileExtended to "Extended",
        MediaCodecInfo.CodecProfileLevel.AVCProfileHigh to "High",
        MediaCodecInfo.CodecProfileLevel.AVCProfileHigh10 to "High 10",
        MediaCodecInfo.CodecProfileLevel.AVCProfileHigh422 to "High 4:2:2",
        MediaCodecInfo.CodecProfileLevel.AVCProfileHigh444 to "High 4:4:4",
        MediaCodecInfo.CodecProfileLevel.AVCProfileConstrainedBaseline to "Constrained Baseline",
        MediaCodecInfo.CodecProfileLevel.AVCProfileConstrainedHigh to "Constrained High",
    )
    private val AVC_PREFERENCE = listOf(
        MediaCodecInfo.CodecProfileLevel.AVCProfileHigh444,
        MediaCodecInfo.CodecProfileLevel.AVCProfileHigh422,
        MediaCodecInfo.CodecProfileLevel.AVCProfileHigh10,
        MediaCodecInfo.CodecProfileLevel.AVCProfileHigh,
        MediaCodecInfo.CodecProfileLevel.AVCProfileConstrainedHigh,
        MediaCodecInfo.CodecProfileLevel.AVCProfileMain,
        MediaCodecInfo.CodecProfileLevel.AVCProfileConstrainedBaseline,
        MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline,
        MediaCodecInfo.CodecProfileLevel.AVCProfileExtended,
    )

    private val HEVC_PROFILES = mapOf(
        MediaCodecInfo.CodecProfileLevel.HEVCProfileMain to "Main",
        MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10 to "Main 10",
        MediaCodecInfo.CodecProfileLevel.HEVCProfileMainStill to "Main Still",
        MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10 to "Main 10 HDR10",
        MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus to "Main 10 HDR10+",
    )
    private val HEVC_PREFERENCE = listOf(
        MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus,
        MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10,
        MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10,
        MediaCodecInfo.CodecProfileLevel.HEVCProfileMain,
        MediaCodecInfo.CodecProfileLevel.HEVCProfileMainStill,
    )

    private val VP9_PROFILES = mapOf(
        MediaCodecInfo.CodecProfileLevel.VP9Profile0 to "Profile 0",
        MediaCodecInfo.CodecProfileLevel.VP9Profile1 to "Profile 1",
        MediaCodecInfo.CodecProfileLevel.VP9Profile2 to "Profile 2",
        MediaCodecInfo.CodecProfileLevel.VP9Profile3 to "Profile 3",
        MediaCodecInfo.CodecProfileLevel.VP9Profile2HDR to "Profile 2 HDR",
        MediaCodecInfo.CodecProfileLevel.VP9Profile3HDR to "Profile 3 HDR",
        MediaCodecInfo.CodecProfileLevel.VP9Profile2HDR10Plus to "Profile 2 HDR10+",
        MediaCodecInfo.CodecProfileLevel.VP9Profile3HDR10Plus to "Profile 3 HDR10+",
    )
    private val VP9_PREFERENCE = listOf(
        MediaCodecInfo.CodecProfileLevel.VP9Profile3HDR10Plus,
        MediaCodecInfo.CodecProfileLevel.VP9Profile2HDR10Plus,
        MediaCodecInfo.CodecProfileLevel.VP9Profile3HDR,
        MediaCodecInfo.CodecProfileLevel.VP9Profile2HDR,
        MediaCodecInfo.CodecProfileLevel.VP9Profile3,
        MediaCodecInfo.CodecProfileLevel.VP9Profile2,
        MediaCodecInfo.CodecProfileLevel.VP9Profile1,
        MediaCodecInfo.CodecProfileLevel.VP9Profile0,
    )

    private val AV1_PROFILES = mapOf(
        MediaCodecInfo.CodecProfileLevel.AV1ProfileMain8 to "Main 8-bit",
        MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10 to "Main 10-bit",
        MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10 to "Main 10 HDR10",
        MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10Plus to "Main 10 HDR10+",
    )
    private val AV1_PREFERENCE = listOf(
        MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10Plus,
        MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10,
        MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10,
        MediaCodecInfo.CodecProfileLevel.AV1ProfileMain8,
    )
}
