package dev.ririxi.bunnylock.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.ServiceCompat
import dev.ririxi.bunnylock.R
import dev.ririxi.bunnylock.modem.ModemController
import dev.ririxi.bunnylock.modem.RootState
import dev.ririxi.bunnylock.shizuku.RootOrShizuku
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

class LockActionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var actionJob: Job? = null
    private var controller: ModemController? = null
    private var latestStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        latestStartId = startId
        val action = intent?.action
        if (action != LockNotifier.ACTION_LOCK_BAND && action != LockNotifier.ACTION_UNLOCK_ALL) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (actionJob?.isActive == true) return START_NOT_STICKY
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification(action),
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
            )
        } catch (_: Exception) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        actionJob = scope.launch { runAction(action) }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        actionJob?.cancel()
        scope.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private suspend fun runAction(action: String) {
        try {
            val ready = CompletableDeferred<Unit>()
            lateinit var modem: ModemController
            modem =
                ModemController(
                    applicationContext,
                    scope,
                    RootOrShizuku(applicationContext) { modem.helperLost("The Shizuku service stopped.") },
                    onConnected = { ready.complete(Unit) },
                    forceMtkRilForSelectedSim = action == LockNotifier.ACTION_LOCK_BAND,
                )
            controller = modem

            if (action == LockNotifier.ACTION_LOCK_BAND && !modem.selectDefaultDataSim()) {
                throw IllegalStateException("The active data SIM could not be identified safely.")
            }

            val watcher =
                scope.launch {
                    modem.state.collect { state ->
                        val denied = state.root as? RootState.Denied ?: return@collect
                        ready.completeExceptionally(IllegalStateException(denied.reason))
                    }
                }
            try {
                modem.connect()
                withTimeout(CONNECT_TIMEOUT_SECONDS.seconds) { ready.await() }
            } finally {
                watcher.cancel()
            }

            if (action == LockNotifier.ACTION_LOCK_BAND) {
                modem.reapplyBandLocks()
                Toast.makeText(applicationContext, "Locked", Toast.LENGTH_SHORT).show()
            } else {
                modem.unlockEverything()
                LockNotifier.cancel(applicationContext)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            // A failure is deliberately silent: the success toast must only follow a completed modem operation.
        } finally {
            withContext(NonCancellable) {
                controller?.let { runCatching { it.shutdown() } }
                controller = null
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf(latestStartId)
            }
        }
    }

    private fun notification(action: String): Notification {
        val channel =
            NotificationChannel(CHANNEL_ID, getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val actionText =
            getString(
                if (action == LockNotifier.ACTION_LOCK_BAND) R.string.notification_action_locking else R.string.notification_action_unlocking,
            )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_lock)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(actionText)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "lock-actions"
        private const val NOTIFICATION_ID = 3
        private const val CONNECT_TIMEOUT_SECONDS = 120L
    }
}