package io.github.justvisiting992.mediaconverter.ui.convert

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.justvisiting992.mediaconverter.core.capability.DeviceCodecReport
import io.github.justvisiting992.mediaconverter.core.engine.ConversionPreset
import io.github.justvisiting992.mediaconverter.core.engine.PresetCommands
import io.github.justvisiting992.mediaconverter.core.queue.ConversionJob
import io.github.justvisiting992.mediaconverter.core.queue.ConversionQueueViewModel
import io.github.justvisiting992.mediaconverter.core.queue.JobStatus

/** Preset groups, in display order — the Convert tab's menu structure. */
private data class PresetCategory(val title: String, val members: List<ConversionPreset>)

private fun categoriesFor(available: List<ConversionPreset>): List<PresetCategory> {
    fun group(title: String, vararg members: ConversionPreset) =
        PresetCategory(title, members.filter { it in available })
    return listOf(
        group(
            "Hardware encode",
            ConversionPreset.H264_HARDWARE, ConversionPreset.HEVC_HARDWARE,
            ConversionPreset.VP9_HARDWARE, ConversionPreset.AV1_HARDWARE,
        ),
        group(
            "Software encode",
            ConversionPreset.H264_SOFTWARE, ConversionPreset.HEVC_SOFTWARE,
            ConversionPreset.VP9_SOFTWARE, ConversionPreset.AV1_SOFTWARE,
        ),
        group(
            "Audio only",
            ConversionPreset.AUDIO_AAC, ConversionPreset.AUDIO_MP3,
            ConversionPreset.AUDIO_OPUS, ConversionPreset.AUDIO_FLAC,
            ConversionPreset.AUDIO_WAV,
        ),
        group(
            "No re-encode",
            ConversionPreset.REMUX_MP4, ConversionPreset.REMUX_MKV,
            ConversionPreset.TRIM_COPY,
        ),
        group(
            "Create",
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

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (report == null) {
            item { Text("Probing this device's media engine…") }
        } else {
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
            item {
                Button(
                    onClick = { picker.launch(arrayOf("video/*", "audio/*")) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Add files")
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
    Card(Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
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

@Composable
private fun JobCard(job: ConversionJob, onCancel: () -> Unit) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                job.inputName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                job.preset.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (job.note.isNotEmpty()) {
                Text(
                    job.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (val status = job.status) {
                is JobStatus.Queued -> Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Queued", style = MaterialTheme.typography.bodyMedium)
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
                        LinearProgressIndicator(
                            progress = { animatedFraction },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "Converting… ${(status.fraction * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(
                            "Converting…",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
                is JobStatus.Done -> Text(
                    "Saved to ${job.outputFolder}/${job.outputName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
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
