package io.github.justvisiting992.mediaconverter.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Apps
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import io.github.justvisiting992.mediaconverter.R
import io.github.justvisiting992.mediaconverter.core.locale.AppLocale
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
    languageTag: String,
    onLanguageChange: (String) -> Unit,
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
                    Icons.Filled.Language,
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.onPrimaryContainer,
                    size = 32.dp,
                )
                Text(
                    stringResource(R.string.settings_language),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        item {
            LanguageCard(
                selectedTag = languageTag,
                onSelect = onLanguageChange,
            )
        }
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
                Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium)
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
                    label = { Text(stringResource(R.string.theme_system)) },
                )
                FilterChip(
                    selected = mode == ThemeMode.LIGHT,
                    onClick = { onModeChange(ThemeMode.LIGHT) },
                    label = { Text(stringResource(R.string.theme_light)) },
                )
                FilterChip(
                    selected = mode == ThemeMode.DARK,
                    onClick = { onModeChange(ThemeMode.DARK) },
                    label = { Text(stringResource(R.string.theme_dark)) },
                )
            }
        }
        if (mode == ThemeMode.SYSTEM) {
            item {
                Text(
                    if (systemUsesDarkTheme(context)) {
                        stringResource(R.string.theme_following_dark)
                    } else {
                        stringResource(R.string.theme_following_light)
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
                Text(stringResource(R.string.settings_color_scheme), style = MaterialTheme.typography.titleMedium)
            }
        }
        item {
            // Two cards per row, each taking half the width by weight
            // and holding a near-square aspect, so the eight themes
            // form a 4x2 grid that adapts to any screen size.
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ThemeTemplate.entries.chunked(2).forEach { rowEntries ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowEntries.forEach { entry ->
                            Box(Modifier.weight(1f)) {
                                TemplateCard(
                                    entry = entry,
                                    selected = entry == template,
                                    onClick = { onTemplateChange(entry) },
                                )
                            }
                        }
                        repeat(2 - rowEntries.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        item {
            Text(
                stringResource(R.string.settings_color_note),
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
                Text(stringResource(R.string.settings_app_icon), style = MaterialTheme.typography.titleMedium)
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
                stringResource(R.string.settings_icon_note),
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
private fun TemplateCard(
    entry: ThemeTemplate,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // Material You has no fixed palette, show the live scheme's colors.
    val live = MaterialTheme.colorScheme
    val dots = entry.swatches().ifEmpty {
        listOf(live.primary, live.secondary, live.tertiary)
    }
    val shape = RoundedCornerShape(20.dp)
    Card(
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.3f)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        val painted = Modifier
            .fillMaxSize()
            .then(
                if (selected) {
                    Modifier.background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.secondaryContainer,
                                MaterialTheme.colorScheme.primaryContainer,
                            ),
                        ),
                    )
                } else {
                    Modifier.background(
                        MaterialTheme.colorScheme.surfaceContainer,
                    )
                },
            )
            .padding(14.dp)
        Column(
            modifier = painted,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                entry.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) {
                    FontWeight.SemiBold
                } else {
                    FontWeight.Medium
                },
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

/**
 * The language selector: a card that opens like the Convert tab's
 * category cards. "System language" is the first row and the default;
 * every other row is one language, named in itself, with its flag at
 * the left. The chosen row wears the same gradient highlight as a
 * chosen preset. Picking a language applies at once, no restart: the
 * caller swaps the app's localized context.
 */
@Composable
private fun LanguageCard(
    selectedTag: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "languageChevron",
    )
    val selected = AppLocale.LANGUAGES.firstOrNull { it.tag == selectedTag }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        selected?.nativeName
                            ?: stringResource(R.string.language_system),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.rotate(chevronRotation),
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 8.dp),
                ) {
                    LanguageRow(
                        flag = "🌐",
                        flagRes = 0,
                        name = stringResource(R.string.language_system),
                        selected = selectedTag == AppLocale.SYSTEM,
                        onClick = { onSelect(AppLocale.SYSTEM) },
                    )
                    AppLocale.LANGUAGES.forEach { language ->
                        LanguageRow(
                            flag = language.flag,
                            flagRes = language.flagRes,
                            name = language.nativeName,
                            selected = selectedTag == language.tag,
                            onClick = { onSelect(language.tag) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageRow(
    flag: String,
    flagRes: Int,
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(shape)
            .then(
                if (selected) {
                    Modifier.background(
                        Brush.linearGradient(
                            listOf(
                                scheme.secondaryContainer,
                                scheme.primaryContainer,
                            ),
                        ),
                    )
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (flagRes != 0) {
            // Persian: the lion and sun flag image. Unicode has no
            // lion-and-sun flag emoji, so this one row shows a small
            // picture where the others show an emoji.
            Image(
                painter = painterResource(flagRes),
                contentDescription = null,
                modifier = Modifier
                    .size(width = 30.dp, height = 18.dp)
                    .clip(RoundedCornerShape(3.dp)),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
        } else {
            Text(flag, fontSize = 20.sp)
        }
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
