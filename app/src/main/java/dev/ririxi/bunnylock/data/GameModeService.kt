package dev.ririxi.bunnylock.data

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
import android.util.Log
import androidx.core.content.ContextCompat
import dev.ririxi.bunnylock.MainActivity
import dev.ririxi.bunnylock.R
import dev.ririxi.bunnylock.modem.Backend
import dev.ririxi.bunnylock.modem.GameModeOperatingState
import dev.ririxi.bunnylock.modem.GameModePolicy
import dev.ririxi.bunnylock.modem.ModemController
import dev.ririxi.bunnylock.modem.RootState
import dev.ririxi.bunnylock.shizuku.RootOrShizuku
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Keeps Game Mode monitoring alive while the app is backgrounded; it never changes bands or PLMN. */
class GameModeService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null
    private var modem: ModemController? = null

    override fun onCreate() {
        super.onCreate()
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isEnabled()) {
            startRequested = false
            stopSelf(startId)
            return START_NOT_STICKY
        }
        try {
            startAsForeground()
        } catch (e: RuntimeException) {
            Log.e(TAG, "Could not start Game Mode foreground monitoring", e)
            startRequested = false
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (monitorJob?.isActive != true) {
            monitorJob = serviceScope.launch { monitorLoop() }
        }
        return START_STICKY
    }

    private fun startAsForeground() {
        createChannel()
        val notification = buildNotification(getString(R.string.game_mode_notification_waiting))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel() {
        val channel =
            NotificationChannel(CHANNEL_ID, getString(R.string.game_mode_notification_channel), NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.game_mode_notification_channel_description)
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(
        message: String,
        ongoing: Boolean = true,
    ): Notification {
        val openApp =
            PendingIntent.getActivity(
                this,
                70,
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_lock)
            .setContentTitle(getString(R.string.game_mode_notification_title))
            .setContentText(message)
            .setStyle(Notification.BigTextStyle().bigText(message))
            .setContentIntent(openApp)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .build()
    }

    private suspend fun monitorLoop() {
        val controller = ModemController(this, serviceScope, RootOrShizuku(this) {}).also { modem = it }
        var previousState = GameModeOperatingState.STABLE
        var lastMonitorError: String? = null
        var lastRecoveryLog: String? = null
        var hasReportedState = false
        try {
            while (serviceScope.isActive && isEnabled() && uiMonitoring) delay(UI_IDLE_CHECK_INTERVAL_MILLIS)
            if (!isEnabled()) return

            controller.connect()
            withTimeout(CONNECT_TIMEOUT_MILLIS) {
                while (controller.state.value.root == RootState.Checking) delay(CONNECT_CHECK_INTERVAL_MILLIS)
            }
            val connected = controller.state.value
            if (connected.root != RootState.Granted || !controller.helperRunning) {
                val reason = (connected.root as? RootState.Denied)?.reason
                throw IllegalStateException(reason ?: getString(R.string.system_fix_helper_unavailable))
            }
            updateNotification(getString(R.string.game_mode_notification_monitoring))

            while (serviceScope.isActive && isEnabled()) {
                if (uiMonitoring) {
                    delay(UI_IDLE_CHECK_INTERVAL_MILLIS)
                    continue
                }
                if (controller.state.value.root != RootState.Granted || !controller.helperRunning) {
                    throw IllegalStateException(getString(R.string.game_mode_notification_helper_lost))
                }
                try {
                    controller.refreshConnection()
                    val signal = controller.bestObservedSignal
                    val operatingState = controller.gameModeOperatingState
                    val hasSignal = signal?.rsrp != null || signal?.sinr != null
                    if (hasSignal && (operatingState != previousState || lastMonitorError != null || !hasReportedState)) {
                        previousState = operatingState
                        hasReportedState = true
                        updateNotification(
                            getString(
                                if (previousState == GameModeOperatingState.RECOVERY && controller.state.value.backend != Backend.QUALCOMM) {
                                    R.string.game_mode_notification_recovery_monitoring_only
                                } else if (previousState == GameModeOperatingState.RECOVERY) {
                                    R.string.game_mode_notification_recovery
                                } else {
                                    R.string.game_mode_notification_stable
                                },
                            ),
                        )
                    } else if (!hasSignal && lastMonitorError != null) {
                        updateNotification(getString(R.string.game_mode_notification_monitoring))
                    }
                    val recoveryLog =
                        controller.state.value.log.firstOrNull {
                            it.contains("Automatic re-attach") || it.contains("Automatic signal recovery failed")
                        }
                    if (recoveryLog != null && recoveryLog != lastRecoveryLog) {
                        lastRecoveryLog = recoveryLog
                        val notification =
                            when {
                                recoveryLog.contains("was accepted by the modem") ->
                                    getString(R.string.game_mode_notification_recovery_requested)
                                recoveryLog.contains("skipped because") ->
                                    getString(R.string.game_mode_notification_recovery_skipped)
                                recoveryLog.contains("failed") ->
                                    getString(R.string.game_mode_notification_recovery_failed)
                                else -> recoveryLog.substringAfter("  ")
                            }
                        updateNotification(notification)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val message = e.message ?: e.javaClass.simpleName
                    if (message != lastMonitorError) {
                        Log.e(TAG, "Game Mode connection monitoring failed", e)
                        updateNotification(getString(R.string.game_mode_notification_unavailable, message))
                        lastMonitorError = message
                    }
                    delay(GameModePolicy.STABLE_MODE_CHECK_INTERVAL_MILLIS)
                    continue
                }
                lastMonitorError = null
                delay(
                    if (controller.gameModeOperatingState == GameModeOperatingState.RECOVERY) {
                        GameModePolicy.RECOVERY_MODE_CHECK_INTERVAL_MILLIS
                    } else {
                        GameModePolicy.STABLE_MODE_CHECK_INTERVAL_MILLIS
                    },
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Game Mode monitoring stopped", e)
            val message = getString(R.string.game_mode_notification_unavailable, e.message ?: e.javaClass.simpleName)
            stopForeground(STOP_FOREGROUND_REMOVE)
            updateNotification(message, ongoing = false)
            stopSelf()
        } finally {
            controller.close()
            modem = null
        }
    }

    private fun updateNotification(
        message: String,
        ongoing: Boolean = true,
    ) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(message, ongoing))
    }

    private fun isEnabled() = getSharedPreferences(PREFERENCES, MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    override fun onDestroy() {
        monitorJob?.cancel()
        monitorJob = null
        modem?.close()
        modem = null
        serviceScope.cancel()
        running = false
        startRequested = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val PREFERENCES = "bunnylock"
        private const val KEY_ENABLED = "gameModeEnabled"
        private const val CHANNEL_ID = "game_mode_monitoring"
        private const val NOTIFICATION_ID = 25
        private const val CONNECT_TIMEOUT_MILLIS = 120_000L
        private const val CONNECT_CHECK_INTERVAL_MILLIS = 200L
        private const val UI_IDLE_CHECK_INTERVAL_MILLIS = 1_000L
        private const val TAG = "BunnyLockGameMode"

        @Volatile
        private var running = false

        @Volatile
        private var startRequested = false

        @Volatile
        private var uiMonitoring = false

        fun setUiMonitoring(active: Boolean) {
            uiMonitoring = active
        }

        fun sync(context: Context, enabled: Boolean) {
            val appContext = context.applicationContext
            val serviceIntent = Intent(appContext, GameModeService::class.java)
            if (!enabled) {
                startRequested = false
                appContext.stopService(serviceIntent)
                appContext.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
                return
            }
            if (running || startRequested) return
            startRequested = true
            try {
                ContextCompat.startForegroundService(appContext, serviceIntent)
            } catch (e: RuntimeException) {
                startRequested = false
                throw e
            }
        }
    }
}
