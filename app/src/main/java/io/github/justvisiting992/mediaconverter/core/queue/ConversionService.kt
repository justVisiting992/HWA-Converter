package io.github.justvisiting992.mediaconverter.core.queue

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.justvisiting992.mediaconverter.MainActivity
import io.github.justvisiting992.mediaconverter.core.locale.AppLocale
import io.github.justvisiting992.mediaconverter.R
import io.github.justvisiting992.mediaconverter.ui.components.formatEtaLeft
import io.github.justvisiting992.mediaconverter.ui.components.formatSizeBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keeps conversions alive while the app is in the background and shows
 * their progress as a permanent notification: percent, speed, and a
 * cancel action. Android kills background processes doing long work,
 * a foreground service is the platform's contract for "this is still
 * working for the user, keep it running", and the notification is the
 * user's window into it.
 *
 * The service owns no conversion state: it renders the shared queue
 * ([ConversionRuntime]) and stops itself when the queue drains.
 */
class ConversionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    AppLocale.wrap(this@ConversionService)
                        .getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = AppLocale.wrap(this@ConversionService)
                        .getString(R.string.notif_channel_desc)
                },
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            ConversionRuntime.get(this).cancelCurrent()
            return START_NOT_STICKY
        }
        // Foreground within the system's grace window, with a
        // placeholder; the collector below replaces it immediately
        // with the real progress.
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(
            title = AppLocale.wrap(this).getString(R.string.notif_converting),
            text = AppLocale.wrap(this).getString(R.string.notif_starting),
            percent = null,
        ),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
        if (observeJob?.isActive != true) {
            observeJob = scope.launch { observeQueue() }
        }
        // Safety net: if no job ever materializes (the files could not
        // be queued at all), do not linger as a foreground service.
        scope.launch {
            delay(15_000)
            val jobs = ConversionRuntime.get(this@ConversionService).jobs.value
            if (jobs.none {
                    it.status is JobStatus.Running || it.status is JobStatus.Queued
                }
            ) {
                ServiceCompat.stopForeground(
                    this@ConversionService, ServiceCompat.STOP_FOREGROUND_REMOVE,
                )
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /**
     * Mirror the queue into the notification. Updates are throttled:
     * the notification is re-posted when the whole percent changes or
     * every couple of seconds, whichever comes first, ffmpeg reports
     * statistics several times a second and the user gains nothing
     * from the notification redrawing at that rate.
     */
    private suspend fun observeQueue() {
        val queue = ConversionRuntime.get(this)
        var lastPercent = -2
        var lastPostAt = 0L
        // Jobs are created asynchronously (probing takes a moment), so
        // at startup the queue can legitimately read empty for a beat.
        // Standing down on that first empty read killed the service,
        // and its notification, before the first job even landed.
        // Only stand down once work has been seen and then drained, or
        // when every job already reached a finished state.
        var seenActive = false
        queue.jobs.collect { jobs ->
            val running = jobs.firstOrNull { it.status is JobStatus.Running }
            val queuedCount = jobs.count { it.status is JobStatus.Queued }
            val active = running != null || queuedCount > 0
            if (active) seenActive = true
            if (!active) {
                if (seenActive || jobs.isNotEmpty()) {
                    ServiceCompat.stopForeground(
                        this, ServiceCompat.STOP_FOREGROUND_REMOVE,
                    )
                    stopSelf()
                }
                return@collect
            }
            val status = running?.status as? JobStatus.Running
            val percent = status?.fraction?.let { (it * 100).toInt() }
            val now = System.currentTimeMillis()
            if (percent != lastPercent || now - lastPostAt >= 2_000) {
                lastPercent = percent ?: -1
                lastPostAt = now
                val ctx = AppLocale.wrap(this@ConversionService)
                val parts = buildList {
                    if (percent != null) add("$percent%")
                    status?.speed?.let { add("%.1f×".format(it)) }
                    formatEtaLeft(
                        ctx,
                        running?.inputInfo?.durationMs,
                        status?.fraction,
                        status?.speed,
                    )?.let { add(it) }
                    status?.sizeBytes?.let { add(formatSizeBytes(it)) }
                    if (queuedCount > 0) {
                        add(ctx.getString(R.string.notif_more_queued, queuedCount))
                    }
                }
                val waiting = running == null && queue.awaitingStart.value
                val text = when {
                    waiting -> ctx.getString(R.string.notif_waiting, queuedCount)
                    running == null -> ctx.getString(R.string.notif_starting)
                    percent != null -> parts.joinToString(" · ")
                    else -> (listOf(ctx.getString(R.string.notif_converting)) + parts)
                        .joinToString(" · ")
                }
                notify(
                    buildNotification(
                        title = if (waiting) {
                            ctx.getString(R.string.notif_ready_title)
                        } else {
                            ctx.getString(
                                R.string.notif_converting_file,
                                running?.inputName
                                    ?: ctx.getString(R.string.notif_files),
                            )
                        },
                        text = text,
                        percent = percent,
                    ),
                )
            }
        }
    }

    private fun notify(notification: Notification) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(
        title: String,
        text: String,
        percent: Int?,
    ): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val cancel = PendingIntent.getService(
            this,
            1,
            Intent(this, ConversionService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_convert)
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(100, percent ?: 0, percent == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                AppLocale.wrap(this).getString(R.string.notif_cancel),
                cancel,
            )
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "conversions"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_CANCEL =
            "io.github.justvisiting992.mediaconverter.CANCEL_CONVERSION"

        /** Start (or refresh) the foreground service. Safe to call repeatedly. */
        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, ConversionService::class.java),
                )
            }
        }
    }
}
