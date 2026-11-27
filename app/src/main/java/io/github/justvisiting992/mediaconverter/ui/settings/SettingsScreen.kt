package io.github.justvisiting992.mediaconverter.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Apps
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.justvisiting992.mediaconverter.ui.components.IconBadge
import io.github.justvisiting992.mediaconverter.ui.theme.ThemeMode
import io.github.justvisiting992.mediaconverter.ui.theme.ThemeTemplate
import io.github.justvisiting992.mediaconverter.ui.theme.systemUsesDarkTheme

/**
 * Appearance settings: light/dark mode and the color scheme template.
 * Choices apply immediately and are persisted by the caller.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    template: ThemeTemplate,
    mode: ThemeMode,
    onTemplateChange: (ThemeTemplate) -> Unit,
    onModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var appIcon by remember { mutableStateOf(IconPrefs.load(context)) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                IconBadge(
                    Icons.Filled.Contrast,
                    MaterialTheme.colorScheme.tertiaryContainer,
                    MaterialTheme.colorScheme.onTertiaryContainer,
                    size = 32.dp,
                )
                Text("Theme", style = MaterialTheme.typography.titleMedium)
            }
        }
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = mode == ThemeMode.SYSTEM,
                    onClick = { onModeChange(ThemeMode.SYSTEM) },
                    label = { Text("System") },
                )
                FilterChip(
                    selected = mode == ThemeMode.LIGHT,
                    onClick = { onModeChange(ThemeMode.LIGHT) },
                    label = { Text("Light") },
                )
                FilterChip(
                    selected = mode == ThemeMode.DARK,
                    onClick = { onModeChange(ThemeMode.DARK) },
                    label = { Text("Dark") },
                )
            }
        }
        if (mode == ThemeMode.SYSTEM) {
            item {
                Text(
                    if (systemUsesDarkTheme(context)) {
                        "Following your phone. It is in dark mode right now."
                    } else {
                        "Following your phone. It is in light mode right now."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                IconBadge(
                    Icons.Filled.Palette,
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.onPrimaryContainer,
                    size = 32.dp,
                )
                Text("Color scheme", style = MaterialTheme.typography.titleMedium)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeTemplate.entries.forEach { entry ->
                    TemplateRow(
                        entry = entry,
                        selected = entry == template,
                        onClick = { onTemplateChange(entry) },
                    )
                }
            }
        }
        item {
            Text(
                "Material You follows your wallpaper. The other schemes " +
                    "are fixed palettes, each with a light and a dark variant.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                IconBadge(
                    Icons.Filled.Apps,
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.onSecondaryContainer,
                    size = 32.dp,
                )
                Text("App icon", style = MaterialTheme.typography.titleMedium)
            }
        }
        item {
            // A fixed 3-column grid: every cell takes an equal third
            // of the full width, so the nine icons always form a
            // symmetrical 3x3 block on any screen size.
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppIcon.entries.chunked(3).forEach { rowEntries ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowEntries.forEach { entry ->
                            Box(Modifier.weight(1f)) {
                                IconChoice(
                                    entry = entry,
                                    selected = entry == appIcon,
                                    onClick = {
                                        appIcon = entry
                                        IconPrefs.apply(context, entry)
                                    },
                                )
                            }
                        }
                        repeat(3 - rowEntries.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        item {
            Text(
                "The home-screen icon may take a few seconds to change. " +
                    "Some launchers refresh it only after a restart.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun IconChoice(entry: AppIcon, selected: Boolean, onClick: () -> Unit) {
    // The artwork's own corner radius is 23 percent of its size. A
    // fixed dp radius cannot match it at every cell size: the ring
    // then cuts across the artwork at the corners. A percent shape
    // scales with the cell, like the launcher ring does.
    val shape = RoundedCornerShape(percent = 23)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Image(
            painter = painterResource(entry.previewRes),
            contentDescription = entry.label,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                // A narrow frame in the icon's own gradient sits
                // on the artwork's rim and covers its rough
                // hand-cropped edge, so the border is one clean
                // line that matches the icon itself. The selected
                // icon's frame is simply thicker.
                .border(
                    width = if (selected) 3.5.dp else 2.dp,
                    // The icons carry their light tone at the top
                    // right and their deep tone at the bottom left;
                    // run the ring's gradient the same way.
                    brush = Brush.linearGradient(
                        listOf(entry.ringDeep, entry.ringLight),
                        start = Offset(0f, Float.POSITIVE_INFINITY),
                        end = Offset(Float.POSITIVE_INFINITY, 0f),
                    ),
                    shape = shape,
                )
                .clickable(onClick = onClick),
        )
        Text(
            entry.label,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun TemplateRow(
    entry: ThemeTemplate,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // Material You has no fixed palette, show the live scheme's colors.
    val live = MaterialTheme.colorScheme
    val dots = entry.swatches().ifEmpty {
        listOf(live.primary, live.secondary, live.tertiary)
    }
    Card(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.Transparent
                },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                entry.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                dots.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(color),
                    )
                }
            }
        }
    }
}
