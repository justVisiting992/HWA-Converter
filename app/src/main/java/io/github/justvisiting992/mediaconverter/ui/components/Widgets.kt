package io.github.justvisiting992.mediaconverter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Small shared building blocks in the app's current design language —
 * the one borrowed from the friendliest modern Android apps: a big
 * gradient hero panel for the headline fact, tinted squircle icon
 * badges that color-code sections, and small tonal chips for facts.
 * All static shapes and brushes: no blur, no per-frame work.
 */

/** A section's icon in a tinted squircle — the color-coding device. */
@Composable
fun IconBadge(
    icon: ImageVector,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}

/** A small tonal pill carrying one fact ("H.264 HW", "1920×1080"). */
@Composable
fun InfoChip(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The hero panel: a large rounded gradient surface for the screen's
 * headline fact. The gradient runs between the theme's two container
 * tones, so every color template gets its own hero and the template's
 * on-container colors stay readable on it.
 */
@Composable
fun HeroPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(scheme.primaryContainer, scheme.tertiaryContainer),
                ),
            )
            .padding(20.dp),
        content = content,
    )
}

/** "24:41" or "1:02:03". */
fun formatDurationMs(durationMs: Long): String {
    val totalSec = durationMs / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) {
        "%d:%02d:%02d".format(h, m, s)
    } else {
        "%d:%02d".format(m, s)
    }
}

/** "67.1 MB", "812 KB". */
fun formatSizeBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1e9)
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1e6)
    bytes >= 1_000 -> "%.0f KB".format(bytes / 1e3)
    else -> "$bytes B"
}

/**
 * Time left for a running job, from how far it is and how fast it is
 * going: "≈ 45 s left", "≈ 3 min left", "≈ 1 h 5 min left". Null when
 * any input is missing — an estimate is never invented.
 */
fun formatEtaLeft(durationMs: Long?, fraction: Float?, speed: Double?): String? {
    if (durationMs == null || fraction == null || speed == null) return null
    if (fraction <= 0f || speed <= 0) return null
    val remainingSec = (durationMs / 1000.0) * (1f - fraction) / speed
    if (remainingSec < 1) return "≈ done"
    val total = remainingSec.toLong()
    return when {
        total < 60 -> "≈ $total s left"
        total < 3600 -> "≈ ${total / 60} min left"
        else -> "≈ ${total / 3600} h ${(total % 3600) / 60} min left"
    }
}

/**
 * Expected output size for a queued job, computed from the bitrates
 * pinned in its own command (-b:v / -b:a) times the input duration.
 * Null when the command pins no bitrate (CRF quality modes, stream
 * copy) — those sizes genuinely cannot be known up front.
 */
fun estimateOutputBytes(command: List<String>, durationMs: Long?): Long? {
    if (durationMs == null || durationMs <= 0) return null
    fun bitrateAfter(flag: String): Long? {
        val i = command.indexOf(flag)
        if (i < 0 || i + 1 >= command.size) return null
        val raw = command[i + 1]
        return if (raw.endsWith("k")) {
            raw.dropLast(1).toLongOrNull()?.times(1000)
        } else {
            raw.toLongOrNull()
        }
    }
    val totalBps = listOfNotNull(bitrateAfter("-b:v"), bitrateAfter("-b:a")).sum()
    if (totalBps <= 0) return null
    return totalBps * durationMs / 8000
}
