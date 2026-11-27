package io.github.justvisiting992.mediaconverter.ui.queue

import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.justvisiting992.mediaconverter.core.engine.PresetCommands
import io.github.justvisiting992.mediaconverter.core.queue.ConversionJob
import io.github.justvisiting992.mediaconverter.core.queue.ConversionQueueViewModel
import io.github.justvisiting992.mediaconverter.core.queue.JobStatus
import io.github.justvisiting992.mediaconverter.ui.components.IconBadge
import io.github.justvisiting992.mediaconverter.ui.components.InfoChip
import io.github.justvisiting992.mediaconverter.ui.components.Thumbnails
import io.github.justvisiting992.mediaconverter.ui.components.estimateOutputBytes
import io.github.justvisiting992.mediaconverter.ui.components.formatDurationMs
import io.github.justvisiting992.mediaconverter.ui.components.formatEtaLeft
import io.github.justvisiting992.mediaconverter.ui.components.formatSizeBytes

/**
 * The Queue tab: every conversion, in its own place, queued, running
 * and finished jobs as cards, with a way to clear the finished ones so
 * the list does not grow forever.
 */
@Composable
fun QueueScreen(
    modifier: Modifier = Modifier,
    queueViewModel: ConversionQueueViewModel = viewModel(),
) {
    val jobs by queueViewModel.jobs.collectAsState()
    val awaitingStart by queueViewModel.awaitingStart.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(Unit) { queueViewModel.attach(context) }

    val hasFinished = jobs.any {
        it.status is JobStatus.Done ||
            it.status is JobStatus.Failed ||
            it.status is JobStatus.Cancelled
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Queue",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (awaitingStart) {
                    TextButton(onClick = { queueViewModel.start(context) }) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(" Start")
                    }
                }
                if (hasFinished) {
                    TextButton(onClick = { queueViewModel.clearFinished() }) {
                        Text("Clear finished")
                    }
                }
            }
        }
        if (jobs.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IconBadge(
                        icon = Icons.Filled.Queue,
                        container = MaterialTheme.colorScheme.surfaceContainerHigh,
                        content = MaterialTheme.colorScheme.onSurfaceVariant,
                        size = 52.dp,
                    )
                    Text(
                        "No conversions yet",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Add files from the Convert tab, tap Start, and " +
                            "they will run here, one at a time.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(jobs, key = { it.id }) { job ->
            JobCard(
                job,
                waitingToStart = awaitingStart,
                onCancel = { queueViewModel.cancel(job.id) },
                onSaveLog = { queueViewModel.saveLog(job.id) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JobCard(
    job: ConversionJob,
    waitingToStart: Boolean = false,
    onCancel: () -> Unit,
    onSaveLog: () -> Unit = {},
) {
    val context = LocalContext.current
    // Sharing a log that is not saved yet saves it first, then shares
    // when the saved URI lands on the job.
    var pendingLogShare by remember(job.id) { mutableStateOf(false) }
    val savedLogUri = job.logUri
    LaunchedEffect(savedLogUri) {
        if (pendingLogShare && savedLogUri != null) {
            pendingLogShare = false
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, savedLogUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share debug log"))
        }
    }
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
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
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
                        Text(
                            if (waitingToStart) "Waiting for Start" else "Queued",
                            style = MaterialTheme.typography.bodyMedium,
                        )
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
                            animationSpec = tween(400),
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
                                        status.sizeBytes?.let { formatSizeBytes(it) },
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
                                (status.speed?.let { " · %.1f× speed".format(it) } ?: "") +
                                (status.sizeBytes?.let { " · ${formatSizeBytes(it)}" } ?: ""),
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
            if (job.debugLog != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (job.logUri == null) {
                            "Debug log. Tap the log to save it to " +
                                "Download/HWA Converter"
                        } else {
                            "Debug log. Saved to Download/HWA Converter"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                            )
                            .clickable(enabled = job.logUri == null) { onSaveLog() }
                            .heightIn(max = 168.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(10.dp),
                    ) {
                        Text(
                            job.debugLog,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                        )
                    }
                    TextButton(
                        onClick = {
                            if (job.logUri != null) {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_STREAM, job.logUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(
                                    Intent.createChooser(intent, "Share debug log"),
                                )
                            } else {
                                pendingLogShare = true
                                onSaveLog()
                            }
                        },
                    ) {
                        Text("Share log")
                    }
                }
            }
        }
    }
}
