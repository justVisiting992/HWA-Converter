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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.justvisiting992.mediaconverter.core.capability.DeviceCodecReport
import io.github.justvisiting992.mediaconverter.core.engine.ConversionPreset
import io.github.justvisiting992.mediaconverter.core.engine.PresetCommands
import io.github.justvisiting992.mediaconverter.core.queue.ConversionJob
import io.github.justvisiting992.mediaconverter.core.queue.ConversionQueueViewModel
import io.github.justvisiting992.mediaconverter.core.queue.JobStatus
import io.github.justvisiting992.mediaconverter.ui.components.HeroPanel
import io.github.justvisiting992.mediaconverter.ui.components.IconBadge
import io.github.justvisiting992.mediaconverter.ui.components.InfoChip
import io.github.justvisiting992.mediaconverter.ui.components.Thumbnails
import io.github.justvisiting992.mediaconverter.ui.components.estimateOutputBytes
import io.github.justvisiting992.mediaconverter.ui.components.formatDurationMs
import io.github.justvisiting992.mediaconverter.ui.components.formatEtaLeft
import io.github.justvisiting992.mediaconverter.ui.components.formatSizeBytes

/** Preset groups, in display order — the Convert tab's menu structure. */
private data class PresetCategory(
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val members: List<ConversionPreset>,
)

private fun categoriesFor(available: List<ConversionPreset>): List<PresetCategory> {
    fun group(
        title: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        vararg members: ConversionPreset,
    ) = PresetCategory(title, icon, members.filter { it in available })
    return listOf(
        group(
            "Hardware encode", Icons.Filled.Bolt,
            ConversionPreset.H264_HARDWARE, ConversionPreset.HEVC_HARDWARE,
            ConversionPreset.VP9_HARDWARE, ConversionPreset.AV1_HARDWARE,
        ),
        group(
            "Software encode", Icons.Filled.Code,
            ConversionPreset.H264_SOFTWARE, ConversionPreset.HEVC_SOFTWARE,
            ConversionPreset.VP9_SOFTWARE, ConversionPreset.AV1_SOFTWARE,
        ),
        group(
            "Audio only", Icons.Filled.MusicNote,
            ConversionPreset.AUDIO_AAC, ConversionPreset.AUDIO_MP3,
            ConversionPreset.AUDIO_OPUS, ConversionPreset.AUDIO_FLAC,
            ConversionPreset.AUDIO_WAV,
        ),
        group(
            "No re-encode", Icons.Filled.ContentCopy,
            ConversionPreset.REMUX_MP4, ConversionPreset.REMUX_MKV,
            ConversionPreset.TRIM_COPY,
        ),
        group(
            "Create", Icons.Filled.AutoAwesome,
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
) {
    val jobs by queueViewModel.jobs.collectAsState()
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

    val presets = remember(report) {
        report?.let { PresetCommands.availablePresets(it) } ?: emptyList()
    }
    val categories = remember(presets) { categoriesFor(presets) }
    var selectedPresetId by remember { mutableStateOf<String?>(null) }
    val selected = presets.firstOrNull { it.id == selectedPresetId } ?: presets.firstOrNull()
    var expandedTitle by remember(categories) {
        mutableStateOf(
            categories.firstOrNull { cat -> cat.members.any { it == selected } }?.title,
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
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 100.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (report == null) {
            item { Text("Probing this device's media engine…") }
        } else {
            item { ConvertHero(report) }
            item {
                Text(
                    "Convert with",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            items(categories, key = { it.title }) { category ->
                CategoryCard(
                    category = category,
                    selected = selected,
                    expanded = expandedTitle == category.title,
                    onToggle = {
                        expandedTitle =
                            if (expandedTitle == category.title) null else category.title
                    },
                    onSelect = { preset ->
                        selectedPresetId = preset.id
                    },
                )
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
                            label = { Text("Start (seconds)") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                            ),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = trimEnd,
                            onValueChange = { trimEnd = it },
                            label = { Text("End (seconds)") },
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

        if (jobs.isNotEmpty()) {
            item {
                Text(
                    "Queue",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(jobs, key = { it.id }) { job ->
                JobCard(job, onCancel = { queueViewModel.cancel(job.id) })
            }
        } else if (report != null) {
            item {
                Text(
                    "Nothing queued yet. Pick a conversion, add files, and " +
                        "the jobs run here — one at a time, on the hardware " +
                        "when the setting says so.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (report != null) {
        ExtendedFloatingActionButton(
            onClick = { picker.launch(arrayOf("video/*", "audio/*")) },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Add files") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 104.dp),
        )
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
    val (badgeContainer, badgeContent) = when (category.title) {
        "Hardware encode" -> scheme.primaryContainer to scheme.onPrimaryContainer
        "Software encode" -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        "Audio only" -> scheme.secondaryContainer to scheme.onSecondaryContainer
        "Create" -> scheme.primaryContainer to scheme.onPrimaryContainer
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
                        category.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (chosen != null) {
                        Text(
                            chosen.label,
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box2(selected)
        Column(Modifier.padding(start = 10.dp)) {
            Text(
                preset.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            AnimatedVisibility(
                visible = selected,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Text(
                    preset.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun Box2(selected: Boolean) {
    if (selected) {
        Icon(
            Icons.Filled.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 2.dp),
        )
    } else {
        // Same footprint as the check icon, so rows stay aligned.
        Spacer(Modifier.size(24.dp))
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
                "Manual settings",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )

            PanelLabel("Video")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                videoOptions.forEach { codec ->
                    FilterChip(
                        selected = video == codec,
                        onClick = { onVideo(codec) },
                        label = { Text(codec.label) },
                    )
                }
            }

            AnimatedVisibility(
                visible = video != PresetCommands.ManualVideoCodec.NONE,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PanelLabel("Container")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    label = { Text("Video bitrate (kbps) — blank = auto") },
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
                    label = { Text("Quality (CRF) — blank = default, lower = better") },
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
                    PanelLabel("Max height")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf<Int?>(null, 1080, 720, 480, 360).forEach { height ->
                            FilterChip(
                                selected = maxHeight == height,
                                onClick = { onMaxHeight(height) },
                                label = { Text(height?.let { "${it}p" } ?: "Original") },
                            )
                        }
                    }
                }
            }

            PanelLabel("Audio")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PresetCommands.ManualAudioCodec.entries.forEach { codec ->
                    FilterChip(
                        selected = audio == codec,
                        onClick = { onAudio(codec) },
                        label = { Text(codec.label) },
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
                    label = { Text("Audio bitrate (kbps)") },
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
                            "Hardware decode",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "Decode on the media engine when this phone has " +
                                "a decoder for the file's codec.",
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JobCard(job: ConversionJob, onCancel: () -> Unit) {
    val context = LocalContext.current
    val info = job.inputInfo
    var thumb by remember(job.id) { mutableStateOf(Thumbnails.cached(job.inputUri)) }
    if (info?.videoCodec != null && thumb == null) {
        LaunchedEffect(job.id) { thumb = Thumbnails.load(context, job.inputUri) }
    }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                if (info?.videoCodec != null) {
                    val bitmap = thumb
                    if (bitmap != null) {
                        Image(
                            bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainerHigh,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Movie,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        job.inputName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        job.preset.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (info != null) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            info.videoCodec?.let { InfoChip(it.uppercase()) }
                            if (info.width != null && info.height != null) {
                                InfoChip("${info.width}×${info.height}")
                            }
                            info.durationMs?.let { InfoChip(formatDurationMs(it)) }
                            info.sizeBytes?.let { InfoChip(formatSizeBytes(it)) }
                            info.audioCodec?.let { InfoChip(it.uppercase()) }
                        }
                    }
                }
            }
            if (job.note.isNotEmpty()) {
                Text(
                    job.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (job.command.isNotEmpty()) {
                var commandExpanded by remember { mutableStateOf(false) }
                Text(
                    "ffmpeg " + PresetCommands.displayCommand(job.command),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (commandExpanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { commandExpanded = !commandExpanded },
                )
            }
            when (val status = job.status) {
                is JobStatus.Queued -> Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Queued", style = MaterialTheme.typography.bodyMedium)
                        estimateOutputBytes(job.command, info?.durationMs)?.let {
                            Text(
                                "≈ ${formatSizeBytes(it)} output",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
                is JobStatus.Running -> Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (status.fraction != null) {
                        val animatedFraction by animateFloatAsState(
                            targetValue = status.fraction,
                            label = "jobProgress",
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                "${(status.fraction * 100).toInt()}%",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Column(Modifier.weight(1f)) {
                                LinearProgressIndicator(
                                    progress = { animatedFraction },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                val eta = formatEtaLeft(
                                    info?.durationMs, status.fraction, status.speed,
                                )
                                Text(
                                    listOfNotNull(
                                        status.speed?.let { "%.1f× speed".format(it) },
                                        eta,
                                    ).joinToString(" · ").ifEmpty { "Converting…" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(
                            "Converting…" +
                                (status.speed?.let { " · %.1f× speed".format(it) } ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
                is JobStatus.Done -> Column {
                    Text(
                        "Saved to ${job.outputFolder}/${job.outputName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    val outUri = job.outputUri
                    if (outUri != null) {
                        val outMime = MimeTypeMap.getSingleton()
                            .getMimeTypeFromExtension(
                                job.outputName.substringAfterLast('.').lowercase(),
                            ) ?: "video/*"
                        Row {
                            TextButton(onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(outUri, outMime)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        },
                                    )
                                }
                            }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(" Open", style = MaterialTheme.typography.labelLarge)
                            }
                            TextButton(onClick = {
                                runCatching {
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = outMime
                                        putExtra(Intent.EXTRA_STREAM, outUri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(send, "Share converted file"),
                                    )
                                }
                            }) {
                                Icon(
                                    Icons.Filled.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(" Share", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
                is JobStatus.Failed -> Text(
                    status.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                is JobStatus.Cancelled -> Text(
                    "Cancelled",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (job.logUri != null) {
                TextButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, job.logUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(
                            Intent.createChooser(intent, "Share debug log"),
                        )
                    },
                ) {
                    Text("Share log")
                }
            }
        }
    }
}

/**
 * The Convert tab's hero: the app's whole pitch in one panel — whose
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
                    report.mediaVendor()?.let { "$it media engine" } ?: "Media engine",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onPrimaryContainer,
                )
                Text(
                    "Hardware acceleration is on for this phone — " +
                        "conversions run on the media engine, not the CPU.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
        }
        val chips = buildList {
            if (report.bestHardwareEncoder(DeviceCodecReport.MIME_AVC) != null) {
                add("H.264 HW encode")
            }
            if (report.bestHardwareEncoder(DeviceCodecReport.MIME_HEVC) != null) {
                add("HEVC HW encode")
            }
            if (report.bestHardwareEncoder(DeviceCodecReport.MIME_VP9) != null) {
                add("VP9 HW encode")
            }
            if (report.bestHardwareEncoder(DeviceCodecReport.MIME_AV1) != null) {
                add("AV1 HW encode")
            }
            if (report.bestHardwareDecoder(DeviceCodecReport.MIME_AV1) != null) {
                add("AV1 HW decode")
            }
            if (report.bestHardwareDecoder(DeviceCodecReport.MIME_HEVC) != null) {
                add("HEVC HW decode")
            }
        }
        if (chips.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 14.dp),
            ) {
                chips.forEach { InfoChip(it) }
            }
        }
    }
}
