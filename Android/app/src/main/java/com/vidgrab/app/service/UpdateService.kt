package com.vidgrab.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.vidgrab.app.MainActivity
import com.vidgrab.app.R
import com.vidgrab.app.core.Engine
import com.vidgrab.app.core.UpdateManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Download and verify first, then acquire the engine lock for the atomic replacement. */
class UpdateService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)
    private var lastNotificationAt = 0L

    override fun onCreate() { super.onCreate(); createChannel(this) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Duplicate manual taps and launch checks must share the existing worker.
        if (!running.compareAndSet(false, true)) return START_NOT_STICKY
        try {
            startForeground(NOTIF_ID, buildNotification("Checking engine update…", 0f),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } catch (_: Exception) {
            running.set(false)
            UpdateManager.finishUpdate()
            UpdateManager.markFailed()
            stopSelf()
            return START_NOT_STICKY
        }
        scope.launch {
            var dest: File? = null
            try {
                check(Engine.await()) { "Engine is not ready" }
                UpdateManager.setProgress(1f, -1, "Checking engine update…")
                val release = runInterruptible { UpdateManager.fetchLatestRelease() }
                    ?: error("Could not read the release")
                val installed = Engine.engineVersion(applicationContext) ?: error("Engine version is unknown")
                if (!UpdateManager.isNewer(release.tag, installed)) {
                    UpdateManager.markDone()
                    return@launch
                }
                val checksum = runInterruptible { UpdateManager.expectedChecksum(release) }
                    ?: error("Release checksum is unavailable")
                val target = File.createTempFile("engine-update-", ".bin", cacheDir)
                dest = target
                val started = android.os.SystemClock.elapsedRealtime()
                val downloaded = runInterruptible {
                    UpdateManager.downloadAsset(release, checksum, target) { read, total ->
                        val progress = read.toFloat() / total * 84f
                        val elapsed = (android.os.SystemClock.elapsedRealtime() - started).coerceAtLeast(1)
                        val eta = ((total - read) * elapsed / read.coerceAtLeast(1) / 1000)
                        val text = "Downloading engine · ${read * 100 / total}%"
                        UpdateManager.setProgress(2f + progress, eta, text)
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (now - lastNotificationAt >= 500) {
                            lastNotificationAt = now
                            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                                .notify(NOTIF_ID, buildNotification(text, 2f + progress))
                        }
                    }
                }
                check(downloaded) { "Engine download could not be verified" }
                ensureActive()
                UpdateManager.setProgress(88f, -1, "Waiting to install engine…")
                Engine.withExclusiveEngine {
                    ensureActive()
                    UpdateManager.setProgress(95f, 0, "Installing verified engine…")
                    check(Engine.installBinary(applicationContext, target, release.tag)) { "Engine install failed" }
                }
                UpdateManager.markDone()
            } catch (e: CancellationException) {
                UpdateManager.markFailed()
                throw e
            } catch (_: Exception) {
                UpdateManager.markFailed()
            } finally {
                dest?.delete()
                running.set(false)
                UpdateManager.finishUpdate()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(text: String, progress: Float): Notification {
        val contentIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(getString(R.string.notif_updating))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .setProgress(100, progress.toInt(), progress <= 1f)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        UpdateManager.markFailed()
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (running.get()) UpdateManager.markFailed()
        scope.cancel()
        UpdateManager.finishUpdate()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "vidgrab_updates"
        const val NOTIF_ID = 102
        fun createChannel(ctx: Context) {
            val channel = NotificationChannel(CHANNEL_ID, ctx.getString(R.string.notif_channel_updates), NotificationManager.IMPORTANCE_LOW).apply {
                description = ctx.getString(R.string.notif_channel_updates_desc)
                setShowBadge(false)
            }
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }
}
