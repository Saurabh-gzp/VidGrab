package com.vidgrab.app.service

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
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.vidgrab.app.MainActivity
import com.vidgrab.app.R
import com.vidgrab.app.core.DlStatus
import com.vidgrab.app.core.DownloadRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Foreground download engine — app swipe/close hone par bhi downloads chalte rahenge.
 * START_STICKY: process mar bhi jaye to system restart karta hai, queue persist se resume.
 *
 * v2.2.2 NOTIFICATION LIFECYCLE FIX (user feedback):
 *  - "Download ke baad bhi notification dikh raha hai" — service kabhi stop hi nahi
 *    hoti thi, foreground notif hamesha rehti thi. Ab jab queue + active dono khaali
 *    hain → stopForeground(REMOVE) + stopSelf() → notif GAYAB.
 *  - "Cancel button nahi hai" — progress notification par Cancel action add kiya;
 *    tap karte hi current download cancel (process kill + item remove).
 *  - Complete/Failed par ek dismissible result notification (auto-dismiss on tap).
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null
    private val workerStarted = AtomicBoolean(false)
    private var lastNotifUpdate = 0L
    @Volatile private var latestStartId = 0

    override fun onCreate() {
        super.onCreate()
        createChannel(this)
        createResultsChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        // Notification Cancel button → current download cancel karo
        if (intent?.action == ACTION_CANCEL) {
            val id = intent.getStringExtra(EXTRA_ID)
            if (id != null) DownloadRepository.cancel(id)
            if (!isRunning) { stopSelfResult(startId); return START_NOT_STICKY }
            return START_STICKY
        }
        isRunning = true
        startAsForeground()
        // START_STICKY restart (intent == null) par bhi worker wapas chalu
        ensureWorker()
        return START_STICKY
    }

    private fun ensureWorker() {
        if (!workerStarted.compareAndSet(false, true)) return
        scope.launch {
            // Notif progress updater
            val poller = launch {
                while (isActive) {
                    delay(700)
                    updateProgressNotification()
                }
            }

            // Await the real restoration barrier instead of guessing a four-second delay.
            DownloadRepository.awaitReady()
            var idleStartId = latestStartId
            while (isActive) {
                idleStartId = latestStartId
                val item = withTimeoutOrNull(1500) { DownloadRepository.queue.receive() }
                if (item == null) {
                    if (!hasPendingWork()) break
                    continue
                }
                acquireWakeLock()
                try { DownloadRepository.executeOne(item) }
                catch (e: CancellationException) { throw e }
                finally { releaseWakeLock() }
            }
            poller.cancel()
            withContext(Dispatchers.Main.immediate) {
                workerStarted.set(false)
                if (hasPendingWork()) ensureWorker() else finishUp(idleStartId)
            }
        }
    }

    /** Kuch download hona baaki hai? (queue + active processing dono check) */
    private fun hasPendingWork(): Boolean {
        return DownloadRepository.active.value.any { it.status.isActiveStatus() }
    }

    private fun startAsForeground() {
        val notif = buildNotification(getString(R.string.notif_downloading), "Starting download…", 0f, false, null)
        startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private fun buildNotification(
        title: String,
        text: String,
        progress: Float,
        indeterminate: Boolean,
        cancelId: String?
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val b = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .setProgress(100, progress.toInt(), indeterminate)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        // v2.2.2: Cancel action — tap → ACTION_CANCEL → repository.cancel(id)
        if (cancelId != null) {
            val cancelIntent = Intent(this, DownloadService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_ID, cancelId)
            }
            val cancelPi = PendingIntent.getService(
                this, cancelId.hashCode(), cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            b.addAction(R.drawable.ic_notif_close, getString(R.string.notif_action_cancel), cancelPi)
        }
        return b.build()
    }

    private fun updateProgressNotification() {
        val now = System.currentTimeMillis()
        if (now - lastNotifUpdate < 500) return
        lastNotifUpdate = now
        val current = DownloadRepository.active.value.firstOrNull {
            it.status.isActiveStatus()
        } ?: return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Processing phase (queue wait / extraction) vs actual download — dono ka alag title
        val preparing = current.status == DlStatus.QUEUED || current.status == DlStatus.FETCHING
        // MERGING par bhi indeterminate bar — merge/safe save mein bhi progress dikhta rahe
        val indeterminate = preparing || current.status == DlStatus.MERGING
        val title = getString(if (preparing) R.string.notif_preparing else R.string.notif_downloading)
        val text = when (current.status) {
            DlStatus.MERGING -> "Finalizing ${current.title.take(28)}"
            DlStatus.QUEUED, DlStatus.FETCHING -> "Processing ${current.title.take(28)}…"
            else -> {
                val speed = UrlUtilsKt.speedText(current.speedBps)
                "${current.title.take(24)} • ${current.progress.toInt()}%$speed"
            }
        }
        nm.notify(NOTIF_ID, buildNotification(title, text, current.progress, indeterminate, current.id))
    }

    /**
     * v2.2.2: Kaam khatam — foreground notif REMOVE + service stop.
     * Yahi user ke "download ke baad bhi notification rehta hai" ka fix hai.
     */
    private fun finishUp(completedStartId: Int = latestStartId) {
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) { }
        try { getSystemService(Context.NOTIFICATION_SERVICE).let { it as NotificationManager }.cancel(NOTIF_ID) } catch (_: Exception) { }
        workerStarted.set(false)
        // A new start request must keep its worker alive.
        if (!stopSelfResult(completedStartId)) {
            startAsForeground()
            ensureWorker()
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VidGrab:download")
        wakeLock?.acquire(6 * 60 * 60 * 1000L)
    }

    private fun releaseWakeLock() {
        try { wakeLock?.takeIf { it.isHeld }?.release() } catch (_: Exception) { }
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        // Stop native work too; executeOne requeues unfinished work for the next launch.
        scope.cancel()
        finishUp()
    }

    override fun onDestroy() {
        isRunning = false
        releaseWakeLock()
        // Restart par naya worker spawn ho sake — flag reset zaroori
        workerStarted.set(false)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "vidgrab_downloads"
        const val RESULTS_CHANNEL_ID = "vidgrab_results"
        const val NOTIF_ID = 101
        const val ACTION_CANCEL = "com.vidgrab.app.action.CANCEL_DOWNLOAD"
        const val EXTRA_ID = "download_id"

        fun createChannel(ctx: Context) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(
                CHANNEL_ID, ctx.getString(R.string.notif_channel_downloads),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = ctx.getString(R.string.notif_channel_downloads_desc)
                setShowBadge(false)
            }
            nm.createNotificationChannel(ch)
        }

        fun createResultsChannel(ctx: Context) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(
                RESULTS_CHANNEL_ID, ctx.getString(R.string.notif_channel_results),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = ctx.getString(R.string.notif_channel_results_desc)
            }
            nm.createNotificationChannel(ch)
        }

        /**
         * v2.2.2: Download complete/fail hone par chhoti dismissible notification.
         * Progress wali notif service ke finishUp() se remove ho jati hai —
         * result notif par tap → app khulta hai, swipe → gayab.
         */
        fun notifyResult(ctx: Context, success: Boolean, title: String, detail: String) {
            try {
                createChannel(ctx)
                createResultsChannel(ctx)
                val contentIntent = PendingIntent.getActivity(
                    ctx, 0, Intent(ctx, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val n = NotificationCompat.Builder(ctx, RESULTS_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_download)
                    .setContentTitle(ctx.getString(if (success) R.string.notif_download_done else R.string.notif_download_failed))
                    .setContentText("$title • $detail".take(120))
                    .setStyle(NotificationCompat.BigTextStyle().bigText("$title\n$detail"))
                    .setAutoCancel(true)
                    .setOngoing(false)
                    .setContentIntent(contentIntent)
                    .build()
                val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify((title.hashCode() and 0x7FFFFFFF) % 100000 + 1000, n)
            } catch (_: Exception) { }
        }

        @Volatile
        var isRunning = false
            private set

        fun start(ctx: Context) {
            val i = Intent(ctx, DownloadService::class.java)
            ctx.startForegroundService(i)
        }
    }
}

/** DlStatus helper — active (notification-worthy) statuses */
private fun DlStatus.isActiveStatus(): Boolean =
    this == DlStatus.QUEUED || this == DlStatus.FETCHING ||
        this == DlStatus.DOWNLOADING || this == DlStatus.MERGING

/** Chhota helper — UrlUtils import circular na ho isliye yahan */
object UrlUtilsKt {
    fun speedText(bps: Long): String =
        if (bps <= 0) "" else {
            val mb = bps / (1024.0 * 1024.0)
            val kb = bps / 1024.0
            " • " + if (mb >= 1) String.format(java.util.Locale.US, "%.1f MB/s", mb)
            else String.format(java.util.Locale.US, "%.0f KB/s", kb)
        }
}
