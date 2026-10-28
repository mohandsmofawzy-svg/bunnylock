package dev.ririxi.bunnylock.watchdog

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import dev.ririxi.bunnylock.MainActivity
import dev.ririxi.bunnylock.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class WatchdogService : Service() {
    private val engine by lazy { WatchdogEngine(this) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var notifyJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == ACTION_STOP) {
            stopMonitor()
            return START_NOT_STICKY
        }
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification(Watchdog.state.value),
                if (Build.VERSION.SDK_INT >= 34) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    0
                },
            )
        } catch (e: Exception) {
            Watchdog.mutable.value =
                Watchdog.mutable.value.copy(
                    running = false,
                    starting = false,
                    error = e.message ?: "Couldn't show the watchdog notification.",
                )
            stopSelf()
            return START_NOT_STICKY
        }
        engine.start()
        if (notifyJob?.isActive != true) {
            notifyJob =
                scope.launch {
                    Watchdog.state.collectLatest { snapshot ->
                        if (snapshot.running) {
                            runCatching { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(snapshot)) }
                        }
                    }
                }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        notifyJob?.cancel()
        engine.stop()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun stopMonitor() {
        notifyJob?.cancel()
        engine.stop()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notification(state: WatchdogState): Notification {
        ensureChannel()
        val open =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val stop =
            PendingIntent.getService(
                this,
                1,
                Intent(this, WatchdogService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val text = state.lastSample?.let { "${it.verdict.label}. ${it.transport}." } ?: "Observe-only connectivity log."
        return Notification
            .Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_lock)
            .setContentTitle("Network watchdog")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
    }

    private fun ensureChannel() {
        val channel =
            NotificationChannel(CHANNEL_ID, "Network watchdog", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows while observe-only network monitoring is running."
                setShowBadge(false)
            }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_STOP = "dev.ririxi.bunnylock.watchdog.STOP"
        private const val CHANNEL_ID = "watchdog"
        private const val NOTIFICATION_ID = 2
    }
}
