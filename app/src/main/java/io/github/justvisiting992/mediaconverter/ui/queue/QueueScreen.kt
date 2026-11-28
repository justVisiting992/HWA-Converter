package io.github.justvisiting992.mediaconverter.ui.queue

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.justvisiting992.mediaconverter.R
import io.github.justvisiting992.mediaconverter.core.engine.PresetCommands
import io.github.justvisiting992.mediaconverter.core.queue.ConversionJob
import io.github.justvisiting992.mediaconverter.core.queue.ConversionQueue
import io.github.justvisiting992.mediaconverter.core.queue.ConversionQueueViewModel
import io.github.justvisiting992.mediaconverter.core.queue.JobStatus
import io.github.justvisiting992.mediaconverter.ui.components.IconBadge
import io.github.justvisiting992.mediaconverter.ui.components.InfoChip
import io.github.justvisiting992.mediaconverter.ui.components.OnGradientTextShadow
import io.github.justvisiting992.mediaconverter.ui.components.Thumbnails
import io.github.justvisiting992.mediaconverter.ui.components.estimateOutputBytes
import io.github.justvisiting992.mediaconverter.ui.components.formatDurationMs
import io.github.justvisiting992.mediaconverter.ui.components.formatEtaLeft
import io.github.justvisiting992.mediaconverter.ui.components.formatSizeBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    // Finished jobs whose original file is still on the device: the
    // only ones the Delete originals button may touch.
    val deletable = jobs.filter {
        it.status is JobStatus.Done && !it.originalDeleted
    }
    var confirmDelete by remember { mutableStateOf(false) }
    var originalsBytes by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(deletable.map { it.id }) {
        originalsBytes = withContext(Dispatchers.IO) {
            var total = 0L
            var any = false
            for (job in deletable) {
                queueViewModel.originalSizeBytes(job.inputUri)?.let {
                    total += it
                    any = true
                }
            }
            if (any) total else null
        }
    }
    // MediaStore files can demand the system's own approval before
    // they may be deleted; the answer lands here and is recorded.
    var pendingApprovalIds by remember {
        mutableStateOf<List<Long>>(emptyList())
    }
    val approvalLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val approved = result.resultCode == Activity.RESULT_OK
        pendingApprovalIds.forEach {
            queueViewModel.markOriginal(it, approved)
        }
        pendingApprovalIds = emptyList()
    }
    val scope = rememberCoroutineScope()
    fun runDelete() {
        val targets = deletable
        scope.launch {
            val needsApproval = mutableListOf<ConversionJob>()
            for (job in targets) {
                if (queueViewModel.deleteOriginal(job) ==
                    ConversionQueue.DeleteOutcome.NEEDS_APPROVAL
                ) {
                    needsApproval += job
                }
            }
            val media = needsApproval.filter {
                it.inputUri.authority == MediaStore.AUTHORITY
            }
            (needsApproval - media.toSet()).forEach {
                queueViewModel.markOriginal(it.id, deleted = false)
            }
            if (media.isNotEmpty()) {
                if (Build.VERSION.SDK_INT >= 30) {
                    pendingApprovalIds = media.map { it.id }
                    runCatching {
                        val request = MediaStore.createDeleteRequest(
                            context.contentResolver,
                            media.map { it.inputUri },
                        )
                        approvalLauncher.launch(
                            IntentSenderRequest.Builder(request.intentSender)
                                .build(),
                        )
                    }.onFailure {
                        media.forEach {
                            queueViewModel.markOriginal(it.id, deleted = false)
                        }
                        pendingApprovalIds = emptyList()
                    }
                } else {
                    media.forEach {
                        queueViewModel.markOriginal(it.id, deleted = false)
                    }
                }
            }
        }
    }
    Box(modifier = modifier) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp, top = 16.dp,
            bottom = if (hasFinished || deletable.isNotEmpty()) {
                224.dp
            } else {
                120.dp
            },
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (awaitingStart) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { queueViewModel.start(context) }) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(stringResource(R.string.queue_start))
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
                        stringResource(R.string.queue_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.queue_empty_body),
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
    if (hasFinished || deletable.isNotEmpty()) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 84.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.End,
        ) {
            if (hasFinished) {
                ExtendedFloatingActionButton(
                    onClick = { queueViewModel.clearFinished() },
                    icon = {
                        Icon(Icons.Filled.ClearAll, contentDescription = null)
                    },
                    text = {
                        Text(stringResource(R.string.queue_clear_finished))
                    },
                    containerColor =
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (deletable.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { confirmDelete = true },
                    icon = {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                    },
                    text = {
                        Text(
                            stringResource(
                                if (deletable.size == 1) {
                                    R.string.queue_delete_original_one
                                } else {
                                    R.string.queue_delete_originals
                                },
                            ) + " (${deletable.size})",
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor =
                        MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = {
                Text(
                    stringResource(
                        if (deletable.size == 1) {
                            R.string.delete_original_title_one
                        } else {
                            R.string.delete_originals_title
                        },
                    ),
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.delete_originals_body))
                    Text(
                        listOfNotNull(
                            "${deletable.size}",
                            originalsBytes?.let { formatSizeBytes(it) },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        runDelete()
                    },
                ) {
                    Text(
                        stringResource(R.string.delete_originals_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.job_cancel))
                }
            },
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
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_debug_log_title)))
        }
    }
    val info = job.inputInfo
    var thumb by remember(job.id) { mutableStateOf(Thumbnails.cached(job.inputUri)) }
    if (info?.videoCodec != null && thumb == null) {
        LaunchedEffect(job.id) { thumb = Thumbnails.load(context, job.inputUri) }
    }
    val scheme = MaterialTheme.colorScheme
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Column(
            Modifier
                .background(
                    Brush.linearGradient(
                        listOf(scheme.primaryContainer, scheme.secondaryContainer),
                    ),
                )
                .padding(14.dp),
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
                        style = MaterialTheme.typography.bodyMedium
                            .copy(shadow = OnGradientTextShadow),
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(job.preset.labelRes),
                        style = MaterialTheme.typography.bodySmall
                            .copy(shadow = OnGradientTextShadow),
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
                    style = MaterialTheme.typography.bodySmall
                        .copy(shadow = OnGradientTextShadow),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (job.command.isNotEmpty()) {
                var commandExpanded by remember { mutableStateOf(false) }
                Text(
                    stringResource(R.string.job_command_prefix) +
                        PresetCommands.displayCommand(job.command),
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
                            if (waitingToStart) {
                                stringResource(R.string.job_waiting)
                            } else {
                                stringResource(R.string.job_queued)
                            },
                            style = MaterialTheme.typography.bodyMedium
                                .copy(shadow = OnGradientTextShadow),
                        )
                        estimateOutputBytes(job.command, info?.durationMs)?.let {
                            Text(
                                stringResource(
                                    R.string.job_est_output,
                                    formatSizeBytes(it),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.job_cancel))
                    }
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
                                    context,
                                    info?.durationMs, status.fraction, status.speed,
                                )
                                Text(
                                    listOfNotNull(
                                        status.speed?.let {
                                            stringResource(
                                                R.string.job_speed,
                                                "%.1f".format(it),
                                            )
                                        },
                                        eta,
                                        status.sizeBytes?.let { formatSizeBytes(it) },
                                    ).joinToString(" · ")
                                        .ifEmpty { stringResource(R.string.job_converting) },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(
                            listOfNotNull(
                                stringResource(R.string.job_converting),
                                status.speed?.let {
                                    stringResource(R.string.job_speed, "%.1f".format(it))
                                },
                                status.sizeBytes?.let { formatSizeBytes(it) },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium
                                .copy(shadow = OnGradientTextShadow),
                        )
                    }
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.job_cancel))
                    }
                }
                is JobStatus.Done -> Column {
                    Text(
                        stringResource(
                            R.string.job_saved_to,
                            job.outputFolder,
                            job.outputName,
                        ),
                        style = MaterialTheme.typography.bodyMedium
                            .copy(shadow = OnGradientTextShadow),
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
                                Spacer(Modifier.size(4.dp))
                                Text(
                                    stringResource(R.string.job_open),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                            TextButton(onClick = {
                                runCatching {
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = outMime
                                        putExtra(Intent.EXTRA_STREAM, outUri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(send, context.getString(R.string.share_file_title)),
                                    )
                                }
                            }) {
                                Icon(
                                    Icons.Filled.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.size(4.dp))
                                Text(
                                    stringResource(R.string.job_share),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }
                    if (job.originalDeleted) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint =
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.size(4.dp))
                            Text(
                                stringResource(R.string.job_original_deleted),
                                style = MaterialTheme.typography.bodySmall,
                                color =
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else if (job.originalDeleteFailed) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.size(4.dp))
                            Text(
                                stringResource(
                                    R.string.job_original_delete_failed,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                is JobStatus.Failed -> Text(
                    status.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                is JobStatus.Cancelled -> Text(
                    stringResource(R.string.job_cancelled),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (job.debugLog != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (job.logUri == null) {
                            stringResource(R.string.job_debug_log_save)
                        } else {
                            stringResource(R.string.job_debug_log_saved)
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
                                    Intent.createChooser(intent, context.getString(R.string.share_debug_log_title)),
                                )
                            } else {
                                pendingLogShare = true
                                onSaveLog()
                            }
                        },
                    ) {
                        Text(stringResource(R.string.job_share_log))
                    }
                }
            }
        }
    }
}
