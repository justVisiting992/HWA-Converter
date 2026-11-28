package io.github.justvisiting992.mediaconverter.ui.convert

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import android.webkit.MimeTypeMap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.justvisiting992.mediaconverter.R
import io.github.justvisiting992.mediaconverter.core.capability.DeviceCodecReport
import io.github.justvisiting992.mediaconverter.core.engine.ConversionPreset
import io.github.justvisiting992.mediaconverter.ui.tour.TourKeys
import io.github.justvisiting992.mediaconverter.ui.tour.tourTarget
import io.github.justvisiting992.mediaconverter.core.engine.PresetCommands
import io.github.justvisiting992.mediaconverter.core.queue.ConversionJob
import io.github.justvisiting992.mediaconverter.core.queue.ConversionQueueViewModel
import io.github.justvisiting992.mediaconverter.core.queue.JobStatus
import io.github.justvisiting992.mediaconverter.ui.components.HeroPanel
import io.github.justvisiting992.mediaconverter.ui.components.IconBadge
import io.github.justvisiting992.mediaconverter.ui.components.InfoChip
import io.github.justvisiting992.mediaconverter.ui.components.OnGradientTextShadow
import io.github.justvisiting992.mediaconverter.ui.components.Thumbnails
import io.github.justvisiting992.mediaconverter.ui.components.estimateOutputBytes
import io.github.justvisiting992.mediaconverter.ui.components.formatDurationMs
import io.github.justvisiting992.mediaconverter.ui.components.formatEtaLeft
import io.github.justvisiting992.mediaconverter.ui.components.formatSizeBytes

/** Preset groups, in display order, the Convert tab's menu structure. */
private data class PresetCategory(
    @StringRes val titleRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val members: List<ConversionPreset>,
)

private fun categoriesFor(available: List<ConversionPreset>): List<PresetCategory> {
    fun group(
        @StringRes titleRes: Int,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        vararg members: ConversionPreset,
    ) = PresetCategory(titleRes, icon, members.filter { it in available })
    return listOf(
        group(
            R.string.category_hardware, Icons.Filled.Bolt,
            ConversionPreset.H264_HARDWARE, ConversionPreset.HEVC_HARDWARE,
            ConversionPreset.VP9_HARDWARE, ConversionPreset.AV1_HARDWARE,
        ),
        group(
            R.string.category_software, Icons.Filled.Code,
            ConversionPreset.H264_SOFTWARE, ConversionPreset.HEVC_SOFTWARE,
            ConversionPreset.VP9_SOFTWARE, ConversionPreset.AV1_SOFTWARE,
        ),
        group(
            R.string.category_audio, Icons.Filled.MusicNote,
            ConversionPreset.AUDIO_AAC, ConversionPreset.AUDIO_MP3,
            ConversionPreset.AUDIO_OPUS, ConversionPreset.AUDIO_FLAC,
            ConversionPreset.AUDIO_WAV,
        ),
        group(
            R.string.category_no_reencode, Icons.Filled.ContentCopy,
            ConversionPreset.REMUX_MP4, ConversionPreset.REMUX_MKV,
            ConversionPreset.TRIM_COPY,
        ),
        group(
            R.string.category_create, Icons.Filled.AutoAwesome,
            ConversionPreset.GIF, ConversionPreset.CUSTOM,
        ),
    ).filter { it.members.isNotEmpty() }
}

/**
 * The Convert tab: pick files, pick a conversion from the categorized
 * menu (only what this device can actually run is shown), and watch the
 * queue work through the jobs one at a time. The Custom entry opens the
 * manual panel: container, codecs, quality, size, decode path.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConvertScreen(
    report: DeviceCodecReport?,
    modifier: Modifier = Modifier,
    queueViewModel: ConversionQueueViewModel = viewModel(),
    onStartPressed: () -> Unit = {},
) {
    val context = LocalContext.current

    // Connect the screen to the process-wide queue (the same one the
    // foreground service renders into the progress notification).
    LaunchedEffect(Unit) { queueViewModel.attach(context) }

    // Android 13+ needs a runtime grant before the conversion
    // notification can appear. Asked for once, when the first files
    // are picked; conversions run either way.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    // The Start gate, read through derived state: the queue's job
    // list changes on every progress tick while a conversion runs,
    // and this screen should only react when the waiting count or
    // the gate itself changes, not several times a second.
    val jobs by queueViewModel.jobs.collectAsState()
    val awaitingStart by queueViewModel.awaitingStart.collectAsState()
    val waitingCount by remember {
        derivedStateOf { jobs.count { it.status is JobStatus.Queued } }
    }

    val presets = remember(report) {
        report?.let { PresetCommands.availablePresets(it) } ?: emptyList()
    }
    val categories = remember(presets) { categoriesFor(presets) }
    var selectedPresetId by remember { mutableStateOf<String?>(null) }
    val selected = presets.firstOrNull { it.id == selectedPresetId } ?: presets.firstOrNull()
    var expandedTitle by remember(categories) {
        mutableStateOf(
            categories.firstOrNull { cat -> cat.members.any { it == selected } }?.titleRes,
        )
    }

    var trimStart by remember { mutableStateOf("") }
    var trimEnd by remember { mutableStateOf("") }

    // Manual (Custom) panel state.
    var manualContainer by remember { mutableStateOf(PresetCommands.ManualContainer.MP4) }
    var manualVideo by remember { mutableStateOf(PresetCommands.ManualVideoCodec.HEVC_HW) }
    var manualAudio by remember { mutableStateOf(PresetCommands.ManualAudioCodec.AAC) }
    var manualBitrate by remember { mutableStateOf("") }
    var manualCrf by remember { mutableStateOf("") }
    var manualAudioBitrate by remember { mutableStateOf("128") }
    var manualMaxHeight by remember { mutableStateOf<Int?>(null) }
    var manualHwDecode by remember { mutableStateOf(true) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        val preset = selected
        if (uris.isNotEmpty() && report != null && preset != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            val manual = if (preset == ConversionPreset.CUSTOM) {
                PresetCommands.ManualOptions(
                    container = manualContainer,
                    video = manualVideo,
                    audio = manualAudio,
                    videoBitrateKbps = manualBitrate.toIntOrNull(),
                    crf = manualCrf.toIntOrNull(),
                    audioBitrateKbps = manualAudioBitrate.toIntOrNull() ?: 128,
                    maxHeight = manualMaxHeight,
                    hardwareDecode = manualHwDecode,
                )
            } else {
                null
            }
            queueViewModel.addJobs(
                context = context,
                uris = uris,
                preset = preset,
                report = report,
                trimStartSeconds = trimStart.toDoubleOrNull(),
                trimEndSeconds = trimEnd.toDoubleOrNull(),
                manual = manual,
            )
            // No tab jump: the jobs wait for Start right here, and
            // the Start button below is how the user says go.
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (report == null) {
            item { Text(stringResource(R.string.convert_probing)) }
        } else {
            item { ConvertHero(report) }
            item {
                Text(
                    stringResource(R.string.convert_with),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            items(categories, key = { it.titleRes }) { category ->
                // The first category card doubles as the tour's
                // "presets" anchor.
                val card: @Composable () -> Unit = {
                    CategoryCard(
                        category = category,
                        selected = selected,
                        expanded = expandedTitle == category.titleRes,
                        onToggle = {
                            expandedTitle =
                                if (expandedTitle == category.titleRes) null
                                else category.titleRes
                        },
                        onSelect = { preset ->
                            selectedPresetId = preset.id
                        },
                    )
                }
                if (category == categories.first()) {
                    Box(Modifier.tourTarget(TourKeys.PRESETS)) { card() }
                } else {
                    card()
                }
            }
            item {
                AnimatedVisibility(
                    visible = selected == ConversionPreset.CUSTOM,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    ManualPanel(
                        report = report,
                        container = manualContainer,
                        onContainer = { manualContainer = it },
                        video = manualVideo,
                        onVideo = { manualVideo = it },
                        audio = manualAudio,
                        onAudio = { manualAudio = it },
                        bitrate = manualBitrate,
                        onBitrate = { manualBitrate = it },
                        crf = manualCrf,
                        onCrf = { manualCrf = it },
                        audioBitrate = manualAudioBitrate,
                        onAudioBitrate = { manualAudioBitrate = it },
                        maxHeight = manualMaxHeight,
                        onMaxHeight = { manualMaxHeight = it },
                        hwDecode = manualHwDecode,
                        onHwDecode = { manualHwDecode = it },
                    )
                }
            }
            item {
                AnimatedVisibility(
                    visible = selected == ConversionPreset.TRIM_COPY,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedTextField(
                            value = trimStart,
                            onValueChange = { trimStart = it },
                            label = { Text(stringResource(R.string.trim_start)) },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                            ),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = trimEnd,
                            onValueChange = { trimEnd = it },
                            label = { Text(stringResource(R.string.trim_end)) },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                            ),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        if (report != null) {
            item {
                Text(
                    stringResource(R.string.convert_helper),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
    if (report != null) {
        // The action stack, floating above the navigation pill:
        // Add files on top; Start below it, present only while
        // picked files are waiting. Nothing converts until Start.
        // The stack is measured from the same navigation-bar inset
        // the pill floats above, not from the screen's edge: the
        // pill is 60 dp tall with a 12 dp margin, so 84 dp leaves
        // a 12 dp gap on every phone. A flat offset from the edge
        // put the buttons under the pill on Samsung, whose nav
        // inset is much larger than Xiaomi's gesture bar.
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 84.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.End,
        ) {
            ExtendedFloatingActionButton(
                onClick = { picker.launch(arrayOf("video/*", "audio/*")) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add_files)) },
                modifier = Modifier.tourTarget(TourKeys.ADD_FILES),
            )
            if (awaitingStart) {
                ExtendedFloatingActionButton(
                    onClick = {
                        queueViewModel.start(context)
                        onStartPressed()
                    },
                    icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                    text = {
                        Text(
                            if (waitingCount == 1) {
                                stringResource(R.string.start_conversion)
                            } else {
                                stringResource(
                                    R.string.start_conversions_n,
                                    waitingCount,
                                )
                            },
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.tourTarget(TourKeys.START),
                )
            }
        }
    }
    }
}

/** One expandable menu section of presets. */
@Composable
private fun CategoryCard(
    category: PresetCategory,
    selected: ConversionPreset?,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSelect: (ConversionPreset) -> Unit,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "chevron",
    )
    val chosen = category.members.firstOrNull { it == selected }
    val scheme = MaterialTheme.colorScheme
    val (badgeContainer, badgeContent) = when (category.titleRes) {
        R.string.category_hardware -> scheme.primaryContainer to scheme.onPrimaryContainer
        R.string.category_software -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        R.string.category_audio -> scheme.secondaryContainer to scheme.onSecondaryContainer
        R.string.category_no_reencode -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        R.string.category_create -> scheme.primaryContainer to scheme.onPrimaryContainer
        else -> scheme.surfaceContainerHigh to scheme.onSurfaceVariant
    }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconBadge(category.icon, badgeContainer, badgeContent)
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(category.titleRes),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (chosen != null) {
                        Text(
                            stringResource(chosen.labelRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
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
                Column(Modifier.padding(bottom = 8.dp)) {
                    category.members.forEach { preset ->
                        PresetRow(
                            preset = preset,
                            selected = preset == selected,
                            onClick = { onSelect(preset) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetRow(
    preset: ConversionPreset,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    // The chosen preset wears a full rounded highlight instead of a
    // check mark: a soft gradient in the hero panel's style, but in
    // the theme's secondary family, so the two never read as the
    // same element. Both colors come from the theme, so every color
    // scheme recolors the highlight with it.
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
        verticalAlignment = Alignment.Top,
    ) {
        Column {
            Text(
                stringResource(preset.labelRes),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            AnimatedVisibility(
                visible = selected,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Text(
                    stringResource(preset.descriptionRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** The Custom panel: every manual choice, grouped and labelled. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ManualPanel(
    report: DeviceCodecReport,
    container: PresetCommands.ManualContainer,
    onContainer: (PresetCommands.ManualContainer) -> Unit,
    video: PresetCommands.ManualVideoCodec,
    onVideo: (PresetCommands.ManualVideoCodec) -> Unit,
    audio: PresetCommands.ManualAudioCodec,
    onAudio: (PresetCommands.ManualAudioCodec) -> Unit,
    bitrate: String,
    onBitrate: (String) -> Unit,
    crf: String,
    onCrf: (String) -> Unit,
    audioBitrate: String,
    onAudioBitrate: (String) -> Unit,
    maxHeight: Int?,
    onMaxHeight: (Int?) -> Unit,
    hwDecode: Boolean,
    onHwDecode: (Boolean) -> Unit,
) {
    val videoOptions = PresetCommands.ManualVideoCodec.entries.filter { codec ->
        when (codec) {
            PresetCommands.ManualVideoCodec.H264_HW ->
                report.hasHardwareEncoder(DeviceCodecReport.MIME_AVC)
            PresetCommands.ManualVideoCodec.HEVC_HW ->
                report.hasHardwareEncoder(DeviceCodecReport.MIME_HEVC)
            PresetCommands.ManualVideoCodec.VP9_HW ->
                report.hasHardwareEncoder(DeviceCodecReport.MIME_VP9)
            PresetCommands.ManualVideoCodec.AV1_HW ->
                report.hasHardwareEncoder(DeviceCodecReport.MIME_AV1)
            else -> true
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.manual_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )

            PanelLabel(stringResource(R.string.manual_video))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                videoOptions.forEach { codec ->
                    FilterChip(
                        selected = video == codec,
                        onClick = { onVideo(codec) },
                        label = { Text(stringResource(codec.labelRes)) },
                    )
                }
            }

            AnimatedVisibility(
                visible = video != PresetCommands.ManualVideoCodec.NONE,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PanelLabel(stringResource(R.string.manual_container))
                    FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                        PresetCommands.ManualContainer.entries.forEach { entry ->
                            FilterChip(
                                selected = container == entry,
                                onClick = { onContainer(entry) },
                                label = { Text(entry.label) },
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = video == PresetCommands.ManualVideoCodec.H264_HW ||
                    video == PresetCommands.ManualVideoCodec.HEVC_HW,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                OutlinedTextField(
                    value = bitrate,
                    onValueChange = onBitrate,
                    label = { Text(stringResource(R.string.manual_bitrate_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            AnimatedVisibility(
                visible = video == PresetCommands.ManualVideoCodec.H264_SW ||
                    video == PresetCommands.ManualVideoCodec.HEVC_SW ||
                    video == PresetCommands.ManualVideoCodec.VP9_SW ||
                    video == PresetCommands.ManualVideoCodec.AV1_SW,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                OutlinedTextField(
                    value = crf,
                    onValueChange = onCrf,
                    label = { Text(stringResource(R.string.manual_crf_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            AnimatedVisibility(
                visible = video != PresetCommands.ManualVideoCodec.COPY &&
                    video != PresetCommands.ManualVideoCodec.NONE,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PanelLabel(stringResource(R.string.manual_max_height))
                    FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                        listOf<Int?>(null, 1080, 720, 480, 360).forEach { height ->
                            FilterChip(
                                selected = maxHeight == height,
                                onClick = { onMaxHeight(height) },
                                label = {
                                    Text(
                                        height?.let { "${it}p" }
                                            ?: stringResource(R.string.manual_original),
                                    )
                                },
                            )
                        }
                    }
                }
            }

            PanelLabel(stringResource(R.string.manual_audio))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PresetCommands.ManualAudioCodec.entries.forEach { codec ->
                    FilterChip(
                        selected = audio == codec,
                        onClick = { onAudio(codec) },
                        label = { Text(stringResource(codec.labelRes)) },
                    )
                }
            }
            AnimatedVisibility(
                visible = audio == PresetCommands.ManualAudioCodec.AAC ||
                    audio == PresetCommands.ManualAudioCodec.MP3 ||
                    audio == PresetCommands.ManualAudioCodec.OPUS,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                OutlinedTextField(
                    value = audioBitrate,
                    onValueChange = onAudioBitrate,
                    label = { Text(stringResource(R.string.manual_audio_bitrate)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            AnimatedVisibility(
                visible = video != PresetCommands.ManualVideoCodec.COPY &&
                    video != PresetCommands.ManualVideoCodec.NONE,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.manual_hw_decode),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            stringResource(R.string.manual_hw_decode_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = hwDecode, onCheckedChange = onHwDecode)
                }
            }
        }
    }
}

@Composable
private fun PanelLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * The Convert tab's hero: the app's whole pitch in one panel, whose
 * media engine this is, and which hardware paths it unlocks. Built
 * from the same probe report the presets are built from.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConvertHero(report: DeviceCodecReport) {
    val scheme = MaterialTheme.colorScheme
    HeroPanel(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconBadge(
                Icons.Filled.Bolt,
                scheme.primary,
                scheme.onPrimary,
                size = 48.dp,
            )
            Column {
                Text(
                    report.mediaVendor()?.let {
                        stringResource(R.string.convert_hero_engine, it)
                    } ?: stringResource(R.string.convert_hero_engine_fallback),
                    style = MaterialTheme.typography.titleLarge
                        .copy(shadow = OnGradientTextShadow),
                    fontWeight = FontWeight.Bold,
                    color = scheme.onPrimaryContainer,
                )
                Text(
                    stringResource(R.string.convert_hero_body),
                    style = MaterialTheme.typography.bodyMedium
                        .copy(shadow = OnGradientTextShadow),
                    color = scheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
        }
        val chips = buildList {
            if (report.bestHardwareEncoder(DeviceCodecReport.MIME_AVC) != null) {
                add(stringResource(R.string.chip_h264_hw_encode))
            }
            if (report.bestHardwareEncoder(DeviceCodecReport.MIME_HEVC) != null) {
                add(stringResource(R.string.chip_hevc_hw_encode))
            }
            if (report.bestHardwareEncoder(DeviceCodecReport.MIME_VP9) != null) {
                add(stringResource(R.string.chip_vp9_hw_encode))
            }
            if (report.bestHardwareEncoder(DeviceCodecReport.MIME_AV1) != null) {
                add(stringResource(R.string.chip_av1_hw_encode))
            }
            if (report.bestHardwareDecoder(DeviceCodecReport.MIME_AV1) != null) {
                add(stringResource(R.string.chip_av1_hw_decode))
            }
            if (report.bestHardwareDecoder(DeviceCodecReport.MIME_HEVC) != null) {
                add(stringResource(R.string.chip_hevc_hw_decode))
            }
        }
        if (chips.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 14.dp),
            ) {
                chips.forEach { InfoChip(it) }
            }
        }
    }
}
