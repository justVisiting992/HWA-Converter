package io.github.justvisiting992.mediaconverter.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.justvisiting992.mediaconverter.core.update.UpdateController
import io.github.justvisiting992.mediaconverter.ui.components.IconBadge
import io.github.justvisiting992.mediaconverter.ui.components.InfoChip
import io.github.justvisiting992.mediaconverter.ui.components.formatSizeBytes

/**
 * The update card: a floating card over a dimmed screen, in the
 * style of the feature tour's cards. It appears when the checker
 * finds a newer release on GitHub. The only ways out are its two
 * buttons: Download & Install, or Download Later (which remembers
 * the version and does not ask again for it).
 */
@Composable
fun UpdateDialog(controller: UpdateController) {
    val info by controller.dialogInfo.collectAsState()
    val current = info ?: return
    val progress by controller.progress.collectAsState()
    val scheme = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.linearGradient(
                        listOf(scheme.primaryContainer, scheme.tertiaryContainer),
                    ),
                )
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconBadge(
                    icon = Icons.Filled.Download,
                    container = scheme.primary,
                    content = scheme.onPrimary,
                    size = 44.dp,
                )
                Column {
                    Text(
                        "Update available",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onPrimaryContainer,
                    )
                    Text(
                        "A new version is on GitHub.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onPrimaryContainer,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoChip("Version ${current.version}")
                if (current.sizeBytes > 0) {
                    InfoChip(formatSizeBytes(current.sizeBytes))
                }
            }
            if (current.notes.isNotBlank()) {
                Text(
                    current.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onPrimaryContainer,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (progress != null) {
                val fraction = progress ?: 0f
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Downloading… ${(fraction * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onPrimaryContainer,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { controller.postpone() },
                    enabled = progress == null,
                ) {
                    Text("Download Later")
                }
                GradientButton(
                    text = if (progress != null) "Downloading…" else "Download & Install",
                    enabled = progress == null,
                    onClick = { controller.downloadAndInstall() },
                )
            }
        }
    }
}

/** A pill button filled with the app's primary to tertiary
 *  gradient, like the hero panels. */
@Composable
private fun GradientButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(
                Brush.linearGradient(
                    listOf(scheme.primary, scheme.tertiary),
                ),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onPrimary,
        )
    }
}
