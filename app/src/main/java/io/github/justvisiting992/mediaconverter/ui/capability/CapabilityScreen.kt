package io.github.justvisiting992.mediaconverter.ui.capability

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.justvisiting992.mediaconverter.core.capability.DecoderCapability
import io.github.justvisiting992.mediaconverter.core.capability.DeviceCodecReport
import io.github.justvisiting992.mediaconverter.core.capability.EncoderCapability

/**
 * "Your hardware" — the app's promise made visible: exactly which encoders
 * this phone's media engine offers, probed live. Every preset in the app
 * is built from this report, so the user can see the evidence the app is
 * working from. The report is probed once in MainActivity and shared with
 * the Convert tab.
 */
@Composable
fun CapabilityScreen(report: DeviceCodecReport?, modifier: Modifier = Modifier) {
    if (report == null) {
        Column(
            modifier = modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
            Text(
                "Probing this device's media engine…",
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 100.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { DeviceHeader() }
        item { HardwareSummary(report) }
        item { HardwareDecodeSummary(report) }
        item {
            Text(
                "Video encoders",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        items(report.videoEncoders, key = { it.componentName + it.mimeType }) {
            EncoderCard(it)
        }
        item {
            Text(
                "Audio encoders",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        items(report.audioEncoders, key = { it.componentName + it.mimeType }) {
            EncoderCard(it)
        }
        item {
            Text(
                "Video decoders",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        items(report.videoDecoders, key = { "dec:" + it.componentName + it.mimeType }) {
            DecoderCard(it)
        }
    }
}

@Composable
private fun DeviceHeader() {
    Column {
        Text(
            "${Build.MANUFACTURER} ${Build.MODEL}",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MANUFACTURER + " " + Build.SOC_MODEL
        } else {
            Build.HARDWARE
        }
        Text(
            "$soc · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HardwareSummary(report: DeviceCodecReport) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Hardware encoding on this device",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            report.mediaVendor()?.let { vendor ->
                Text(
                    "Media engine: $vendor",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                )
            }
            SummaryRow("H.264", report.bestHardwareEncoder(DeviceCodecReport.MIME_AVC))
            SummaryRow("H.265 / HEVC", report.bestHardwareEncoder(DeviceCodecReport.MIME_HEVC))
            SummaryRow("VP9", report.bestHardwareEncoder(DeviceCodecReport.MIME_VP9))
            SummaryRow("AV1", report.bestHardwareEncoder(DeviceCodecReport.MIME_AV1))
        }
    }
}

@Composable
private fun HardwareDecodeSummary(report: DeviceCodecReport) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Hardware decoding on this device",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            DecoderSummaryRow("H.264", report.bestHardwareDecoder(DeviceCodecReport.MIME_AVC))
            DecoderSummaryRow("H.265 / HEVC", report.bestHardwareDecoder(DeviceCodecReport.MIME_HEVC))
            DecoderSummaryRow("VP9", report.bestHardwareDecoder(DeviceCodecReport.MIME_VP9))
            DecoderSummaryRow("AV1", report.bestHardwareDecoder(DeviceCodecReport.MIME_AV1))
        }
    }
}

@Composable
private fun DecoderSummaryRow(label: String, decoder: DecoderCapability?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = decoder?.componentName ?: "software only",
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = if (decoder != null) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
            },
        )
    }
}

@Composable
private fun SummaryRow(label: String, encoder: EncoderCapability?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = encoder?.componentName ?: "software only",
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = if (encoder != null) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
            },
        )
    }
}

@Composable
private fun DecoderCard(decoder: DecoderCapability) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    decoder.componentName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (decoder.isHardware) "HARDWARE" else "SOFTWARE",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (decoder.isHardware) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = FontWeight.Bold,
                )
            }
            val facts = buildList {
                add(decoder.mimeType)
                if (decoder.maxWidth != null && decoder.maxHeight != null) {
                    add("up to ${decoder.maxWidth}×${decoder.maxHeight}")
                }
            }
            Text(
                facts.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EncoderCard(encoder: EncoderCapability) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    encoder.componentName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (encoder.isHardware) "HARDWARE" else "SOFTWARE",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (encoder.isHardware) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = FontWeight.Bold,
                )
            }
            val facts = buildList {
                add(encoder.mimeType)
                if (encoder.maxWidth != null && encoder.maxHeight != null) {
                    add("up to ${encoder.maxWidth}×${encoder.maxHeight}")
                }
                encoder.maxBitrate?.let { add("≤ ${it / 1_000_000} Mbps") }
                if (encoder.profiles.isNotEmpty()) {
                    add(encoder.profiles.joinToString(", "))
                }
            }
            Text(
                facts.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
