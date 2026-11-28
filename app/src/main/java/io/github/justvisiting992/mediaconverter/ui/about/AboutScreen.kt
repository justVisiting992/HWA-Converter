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
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.justvisiting992.mediaconverter.BuildConfig
import io.github.justvisiting992.mediaconverter.R
import io.github.justvisiting992.mediaconverter.core.update.UpdateController
import io.github.justvisiting992.mediaconverter.ui.components.OnGradientTextShadow
import io.github.justvisiting992.mediaconverter.ui.components.HeroPanel
import io.github.justvisiting992.mediaconverter.ui.components.IconBadge
import io.github.justvisiting992.mediaconverter.ui.components.InfoChip

private const val REPO_URL = "https://github.com/justVisiting992/HWA-Converter"
private const val RELEASES_URL = "$REPO_URL/releases"

/**
 * The About tab: what the app is, in one paragraph, the project's
 * home on GitHub, and the update checker. The GitHub and Releases
 * rows open in the browser; the update rows work right here, and
 * they are the app's only network use.
 */
@Composable
fun AboutScreen(
    modifier: Modifier = Modifier,
    updateController: UpdateController,
    onReplayTour: () -> Unit = {},
) {
    val context = LocalContext.current
    fun open(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
    val updateStatus by updateController.status.collectAsState()
    val autoCheck by updateController.autoCheck.collectAsState()
    val networkOff by updateController.networkOff.collectAsState()
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            HeroPanel(Modifier.fillMaxWidth()) {
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
                            style = MaterialTheme.typography.titleLarge
                                .copy(shadow = OnGradientTextShadow),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            stringResource(R.string.about_tagline),
                            style = MaterialTheme.typography.bodyMedium
                                .copy(shadow = OnGradientTextShadow),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Row(
                    modifier = Modifier.padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    InfoChip(stringResource(R.string.about_version, BuildConfig.VERSION_NAME))
                    InfoChip("GPL-3.0")
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
                    stringResource(R.string.about_body),
                    style = MaterialTheme.typography.bodyLarge,
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
                        title = stringResource(R.string.about_github),
                        subtitle = stringResource(R.string.about_github_sub),
                        onClick = { open(REPO_URL) },
                    )
                    LinkRow(
                        icon = Icons.Filled.Download,
                        title = stringResource(R.string.about_releases),
                        subtitle = stringResource(R.string.about_releases_sub),
                        onClick = { open(RELEASES_URL) },
                    )
                    ActionRow(
                        icon = Icons.Filled.SystemUpdate,
                        title = stringResource(R.string.about_check_updates),
                        subtitle = updateStatus
                            ?: stringResource(R.string.about_check_updates_sub),
                        onClick = { updateController.checkManually() },
                    )
                    SwitchRow(
                        icon = Icons.Filled.Autorenew,
                        title = stringResource(R.string.about_auto_check),
                        subtitle = if (networkOff) {
                            stringResource(R.string.about_auto_off)
                        } else {
                            stringResource(R.string.about_auto_daily)
                        },
                        checked = autoCheck && !networkOff,
                        enabled = !networkOff,
                        onCheckedChange = { updateController.setAutoCheck(it) },
                    )
                    Text(
                        stringResource(R.string.about_internet_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            start = 16.dp, end = 16.dp, bottom = 14.dp,
                        ),
                    )
                    ActionRow(
                        icon = Icons.Filled.PlayArrow,
                        title = stringResource(R.string.about_replay_tour),
                        subtitle = stringResource(R.string.about_replay_tour_sub),
                        onClick = onReplayTour,
                    )
                }
            }
        }
        item {
            Text(
                stringResource(R.string.about_footer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

/** A row like LinkRow, but for actions inside the app: no
 *  open-in-new icon at the end. */
@Composable
private fun ActionRow(
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
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A row with a switch at the end instead of a click action. */
@Composable
private fun SwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
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
                style = MaterialTheme.typography.bodyMedium,
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
