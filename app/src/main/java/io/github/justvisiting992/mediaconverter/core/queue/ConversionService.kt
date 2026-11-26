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
import io.github.justvisiting992.mediaconverter.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps conversions alive while the app is in the background and shows
 * their progress as a permanent notification: percent, speed, and a
 * cancel action. Android kills background processes doing long work —
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
                    "Conversions",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description =
                        "Progress of running conversions while the app is in the background."
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
            buildNotification(title = "Converting…", text = "Starting…", percent = null),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
        if (observeJob?.isActive != true) {
            observeJob = scope.launch { observeQueue() }
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
     * every couple of seconds, whichever comes first — ffmpeg reports
     * statistics several times a second and the user gains nothing
     * from the notification redrawing at that rate.
     */
    private suspend fun observeQueue() {
        val queue = ConversionRuntime.get(this)
        var lastPercent = -2
        var lastPostAt = 0L
        queue.jobs.collect { jobs ->
            val running = jobs.firstOrNull { it.status is JobStatus.Running }
            val queuedCount = jobs.count { it.status is JobStatus.Queued }
            if (running == null && queuedCount == 0) {
                // Nothing in flight and nothing waiting: the queue has
                // drained (or the only jobs were cancelled). Stand down.
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@collect
            }
            val status = running?.status as? JobStatus.Running
            val percent = status?.fraction?.let { (it * 100).toInt() }
            val now = System.currentTimeMillis()
            if (percent != lastPercent || now - lastPostAt >= 2_000) {
                lastPercent = percent ?: -1
                lastPostAt = now
                val speedText = status?.speed?.let { " · %.1f×".format(it) } ?: ""
                val queueText = if (queuedCount > 0) " · $queuedCount more queued" else ""
                val text = when {
                    running == null -> "Starting…"
                    percent != null -> "$percent%$speedText$queueText"
                    else -> "Converting…$speedText$queueText"
                }
                notify(
                    buildNotification(
                        title = "Converting ${running?.inputName ?: "files"}",
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
                "Cancel",
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
