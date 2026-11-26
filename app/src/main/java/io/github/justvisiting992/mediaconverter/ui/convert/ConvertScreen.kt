package io.github.justvisiting992.mediaconverter.ui.convert

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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

/**
 * The Convert tab: pick files, pick a preset (only the ones this device
 * can actually run are shown), and watch the queue work through them one
 * at a time. Outputs land in Movies/HWA Converter (Music/ for audio).
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
    var selectedPresetId by remember { mutableStateOf<String?>(null) }
    val selected = presets.firstOrNull { it.id == selectedPresetId } ?: presets.firstOrNull()

    var trimStart by remember { mutableStateOf("") }
    var trimEnd by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        val preset = selected
        if (uris.isNotEmpty() && report != null && preset != null) {
            queueViewModel.addJobs(
                context = context,
                uris = uris,
                preset = preset,
                report = report,
                trimStartSeconds = trimStart.toDoubleOrNull(),
                trimEndSeconds = trimEnd.toDoubleOrNull(),
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
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { preset ->
                        FilterChip(
                            selected = preset == selected,
                            onClick = { selectedPresetId = preset.id },
                            label = { Text(preset.label) },
                        )
                    }
                }
            }
            item {
                Text(
                    selected?.description ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected == ConversionPreset.TRIM_COPY) {
                item {
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
                    "Nothing queued yet. Pick a preset, add files, and the " +
                        "conversion runs here — one at a time, on the " +
                        "hardware when the preset says so.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun JobCard(job: ConversionJob, onCancel: () -> Unit) {
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
                        LinearProgressIndicator(
                            progress = { status.fraction },
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
        }
    }
}
