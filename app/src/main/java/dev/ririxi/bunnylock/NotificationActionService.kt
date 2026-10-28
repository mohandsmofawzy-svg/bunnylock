package dev.ririxi.bunnylock

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.telephony.SubscriptionManager
import android.util.Log
import android.widget.Toast
import dev.ririxi.bunnylock.data.LockNotifier
import dev.ririxi.bunnylock.data.RootException
import dev.ririxi.bunnylock.modem.Backend
import dev.ririxi.bunnylock.modem.ModemController
import dev.ririxi.bunnylock.modem.RootState
import dev.ririxi.bunnylock.shizuku.RootOrShizuku
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

class NotificationActionService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var actionJob: Job? = null
    private var latestStartId = 0

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        latestStartId = startId
        val commandIntent = intent
        val action = commandIntent?.action
        if (commandIntent == null || action == null ||
            action != LockNotifier.ACTION_LOCK_BAND && action != LockNotifier.ACTION_UNLOCK_ALL
        ) {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        if (actionJob?.isActive == true) return START_NOT_STICKY

        startForegroundActionNotification()
        actionJob =
            serviceScope.launch {
                try {
                    performAction(action, commandIntent)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Notification action failed: $action", e)
                    showFailure(e.message ?: "Couldn't complete the modem action.")
                } finally {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelfResult(latestStartId)
                }
            }
        return START_NOT_STICKY
    }

    private suspend fun performAction(
        action: String,
        intent: Intent,
    ) {
        val target =
            if (action == LockNotifier.ACTION_LOCK_BAND) {
                defaultDataSim()
            } else {
                SimTarget(
                    intent.getIntExtra(LockNotifier.EXTRA_SIM_SLOT, -1),
                    intent.getIntExtra(LockNotifier.EXTRA_SUBSCRIPTION_ID, -1),
                ).also {
                    check(it.slot >= 0 && it.subscriptionId >= 0) { "The SIM associated with these locks is unknown." }
                }
            }

        lateinit var modem: ModemController
        val connected = CompletableDeferred<Unit>()
        modem =
            ModemController(
                applicationContext,
                serviceScope,
                RootOrShizuku(
                    applicationContext,
                    { reason -> modem.helperLost(reason) },
                    allowShizukuPermissionRequest = false,
                ),
                onConnected = { connected.complete(Unit) },
                requireSubscriptionBoundMediatek = action == LockNotifier.ACTION_LOCK_BAND,
            )
        try {
            modem.loadSims()
            check(modem.selectSimForAction(target.slot, target.subscriptionId)) {
                "The saved SIM subscription is no longer active or can't be identified safely."
            }
            modem.connect()
            awaitConnection(modem, connected)

            if (action == LockNotifier.ACTION_UNLOCK_ALL) {
                modem.unlockEverything()
                LockNotifier.cancel(this)
                markAppForRefresh()
                return
            }

            check(defaultDataSim() == target) { "The default data SIM changed before the band lock could be applied." }
            modem.reapplyBandLocks(target.subscriptionId)
            markAppForRefresh()
            Toast.makeText(applicationContext, "Locked", Toast.LENGTH_SHORT).show()
        } finally {
            withContext(NonCancellable) { modem.shutdown() }
        }
    }

    private suspend fun awaitConnection(
        modem: ModemController,
        connected: CompletableDeferred<Unit>,
    ) = withTimeout(CONNECTION_TIMEOUT_SECONDS.seconds) {
        coroutineScope {
            val denied =
                async {
                    modem.state.first { it.root is RootState.Denied }.root as RootState.Denied
                }
            val failure =
                select<String?> {
                    connected.onAwait { null }
                    denied.onAwait { it.reason }
                }
            denied.cancel()
            if (failure != null) throw RootException(failure)
        }
    }

    @SuppressLint("MissingPermission")
    private fun defaultDataSim(): SimTarget {
        check(checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            "Phone permission is needed to identify the default data SIM."
        }
        val manager = getSystemService(SubscriptionManager::class.java)
        val defaultSubId = SubscriptionManager.getDefaultDataSubscriptionId()
        check(defaultSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            "Android hasn't identified a default data subscription."
        }
        val active =
            manager.activeSubscriptionInfoList
                ?: error("Android couldn't list active SIM subscriptions.")
        val info =
            active.singleOrNull { it.subscriptionId == defaultSubId }
                ?: error("The default data subscription doesn't map to exactly one active SIM.")
        check(info.simSlotIndex >= 0) { "Android didn't report the SIM slot for mobile data." }
        return SimTarget(info.simSlotIndex, info.subscriptionId)
    }

    private fun startForegroundActionNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(ACTION_CHANNEL_ID, "Modem actions", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            },
        )
        val notification =
            Notification.Builder(this, ACTION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_lock)
                .setContentTitle("bunnylock")
                .setContentText("Applying notification action...")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(ACTION_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(ACTION_NOTIFICATION_ID, notification)
        }
    }

    private fun showFailure(message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(
            FAILURE_NOTIFICATION_ID,
            Notification.Builder(this, ACTION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_lock)
                .setContentTitle("bunnylock action failed")
                .setContentText(message)
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun markAppForRefresh() {
        getSharedPreferences("bunnylock", MODE_PRIVATE)
            .edit()
            .putBoolean(LockNotifier.PREF_REFRESH_AFTER_ACTION, true)
            .apply()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private data class SimTarget(
        val slot: Int,
        val subscriptionId: Int,
    )

    private companion object {
        const val TAG = "BunnylockNotification"
        const val ACTION_CHANNEL_ID = "notification_actions"
        const val ACTION_NOTIFICATION_ID = 2
        const val FAILURE_NOTIFICATION_ID = 3
        const val CONNECTION_TIMEOUT_SECONDS = 180L
    }
}
