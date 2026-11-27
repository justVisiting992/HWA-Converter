package io.github.justvisiting992.mediaconverter.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.justvisiting992.mediaconverter.BuildConfig
import io.github.justvisiting992.mediaconverter.ui.components.HeroPanel
import io.github.justvisiting992.mediaconverter.ui.components.IconBadge
import io.github.justvisiting992.mediaconverter.ui.components.InfoChip

private const val REPO_URL = "https://github.com/justVisiting992/HWA-Converter"
private const val RELEASES_URL = "$REPO_URL/releases"

/**
 * The About tab: what the app is, in one paragraph, and the project's
 * home on GitHub. The app itself has no INTERNET permission, so the
 * links open in the browser rather than loading anything in-app.
 */
@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    fun open(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            HeroPanel {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IconBadge(
                        icon = Icons.Filled.SwapHoriz,
                        container = MaterialTheme.colorScheme.primary,
                        content = MaterialTheme.colorScheme.onPrimary,
                        size = 44.dp,
                    )
                    Column {
                        Text(
                            "HWA Converter",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            "Hardware-first media conversion",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Row(
                    modifier = Modifier.padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    InfoChip("Version ${BuildConfig.VERSION_NAME}")
                    InfoChip("GPL-3.0")
                    InfoChip("No network permission")
                }
            }
        }
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Most converter apps encode on the CPU and never touch " +
                        "the media engine sitting in your phone. HWA Converter " +
                        "works the other way around: it probes the hardware " +
                        "first — MediaTek, Qualcomm, Exynos, Kirin — and " +
                        "encodes and decodes on the media engine whenever " +
                        "the chip offers it, with the software path kept for " +
                        "maximum quality and for whatever the hardware " +
                        "cannot do. Nothing is faked and nothing falls back " +
                        "silently: every job shows the exact command that " +
                        "ran, and the Hardware tab shows what your device " +
                        "can actually do. The app is free and open source, " +
                        "and it asks for no network permission — your files " +
                        "never leave the phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(18.dp),
                )
            }
        }
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    LinkRow(
                        icon = Icons.Filled.Code,
                        title = "GitHub",
                        subtitle = "Source code, issues and the project page",
                        onClick = { open(REPO_URL) },
                    )
                    LinkRow(
                        icon = Icons.Filled.Download,
                        title = "Releases",
                        subtitle = "Download the latest APKs",
                        onClick = { open(RELEASES_URL) },
                    )
                }
            }
        }
        item {
            Text(
                "Built on FFmpeg through FFmpegKit (GPL). Conversion engine: " +
                    "your phone's media hardware, whenever it offers one.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun LinkRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBadge(
            icon = icon,
            container = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
