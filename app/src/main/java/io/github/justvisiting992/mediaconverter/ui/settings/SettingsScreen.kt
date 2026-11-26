package io.github.justvisiting992.mediaconverter.ui.settings

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.justvisiting992.mediaconverter.ui.theme.ThemeMode
import io.github.justvisiting992.mediaconverter.ui.theme.ThemeTemplate

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
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 100.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "Theme",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        item {
            Text(
                "Color scheme",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
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
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun TemplateRow(
    entry: ThemeTemplate,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // Material You has no fixed palette — show the live scheme's colors.
    val live = MaterialTheme.colorScheme
    val dots = entry.swatches().ifEmpty {
        listOf(live.primary, live.secondary, live.tertiary)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.Transparent
                },
                shape = MaterialTheme.shapes.medium,
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
