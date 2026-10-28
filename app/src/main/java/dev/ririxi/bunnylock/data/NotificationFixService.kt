package dev.ririxi.bunnylock.data

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import dev.ririxi.bunnylock.R
import dev.ririxi.bunnylock.modem.BandGroupState
import dev.ririxi.bunnylock.modem.Group
import dev.ririxi.bunnylock.modem.ModemController
import dev.ririxi.bunnylock.modem.RootState
import dev.ririxi.bunnylock.modem.SimInfo
import dev.ririxi.bunnylock.shizuku.RootOrShizuku
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** يبقي إصلاح الإشعار منفردًا ومرئيًا أثناء عمليات المودم الطويلة. */
class NotificationFixService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var fixJob: Job? = null
    private var latestStartId = 0
    private var modem: ModemController? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        if (intent?.action != LockNotifier.ACTION_FIX) {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        if (fixJob?.isActive == true) return START_NOT_STICKY
        try {
            val notification = LockNotifier.notificationForFix(this, getString(R.string.system_fix_fixing))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: RuntimeException) {
            Log.e(TAG, "Could not start foreground notification fix", e)
            LockNotifier.updateFixStatus(this, getString(R.string.system_fix_failed, e.message ?: getString(R.string.system_fix_start_error)))
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        LockNotifier.updateFixStatus(this, getString(R.string.system_fix_fixing))
        fixJob = serviceScope.launch {
            try {
                performFix()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Notification Fix failed", e)
                LockNotifier.updateFixStatus(
                    this@NotificationFixService,
                    getString(R.string.system_fix_failed, e.message ?: getString(R.string.system_fix_unknown_error)),
                )
            } finally {
                modem?.close()
                modem = null
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelfResult(latestStartId)
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun performFix() {
        LockNotifier.updateFixStatus(this, getString(R.string.system_fix_connecting))
        val controller = ModemController(this, serviceScope, RootOrShizuku(this) {}).also { modem = it }
        controller.connect()
        withTimeout(CONNECT_TIMEOUT_MILLIS) {
            while (controller.state.value.root == RootState.Checking) delay(200)
        }
        val root = controller.state.value.root
        if (root != RootState.Granted || !controller.helperRunning) {
            throw IllegalStateException((root as? RootState.Denied)?.reason ?: getString(R.string.system_fix_helper_unavailable))
        }

        controller.loadSims()
        val sims = controller.state.value.sims
        val phonePermission = checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val activeDataSubId =
            if (phonePermission) {
                SubscriptionManager.getDefaultDataSubscriptionId()
            } else {
                SubscriptionManager.INVALID_SUBSCRIPTION_ID
            }
        val selectedSim: SimInfo? =
            if (activeDataSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                sims.firstOrNull { it.subId == activeDataSubId }
                    ?: throw IllegalStateException(getString(R.string.system_fix_active_sim_unavailable))
            } else {
                sims.firstOrNull { it.slot + 1 == controller.state.value.sim }
            }
        if (selectedSim != null && controller.state.value.sim != selectedSim.slot + 1) {
            controller.setSim(selectedSim.slot + 1)
        }
        controller.refreshNow()

        var plmnSuccess = false
        var plmnMessage: String
        LockNotifier.updateFixStatus(this, getString(R.string.system_fix_detecting_home))
        if (!phonePermission || activeDataSubId == SubscriptionManager.INVALID_SUBSCRIPTION_ID || selectedSim == null) {
            plmnMessage = getString(R.string.system_fix_home_unavailable)
        } else {
            try {
                val telephony = getSystemService(TelephonyManager::class.java).createForSubscriptionId(activeDataSubId)
                val homePlmn = telephony.simOperator?.takeIf { it.matches(Regex("\\d{5,6}")) }
                    ?: throw IllegalStateException(getString(R.string.system_fix_home_unavailable))
                val current = controller.state.value
                val currentLock = current.plmnLock?.filter(Char::isDigit)
                if (current.plmnLockKnown && currentLock == homePlmn) {
                    plmnSuccess = true
                    plmnMessage = getString(R.string.system_fix_plmn_confirmed, formatPlmn(homePlmn))
                } else {
                    LockNotifier.updateFixStatus(this, getString(R.string.system_fix_applying_plmn))
                    controller.lockPlmn(homePlmn.take(3), homePlmn.drop(3))
                    controller.refreshNow()
                    val verified = controller.state.value
                    plmnSuccess = verified.plmnLockKnown && verified.plmnLock?.filter(Char::isDigit) == homePlmn
                    plmnMessage =
                        if (plmnSuccess) {
                            getString(R.string.system_fix_plmn_confirmed, formatPlmn(homePlmn))
                        } else {
                            getString(R.string.system_fix_plmn_unverified)
                        }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                plmnMessage = getString(R.string.system_fix_plmn_failed, e.message ?: getString(R.string.system_fix_unknown_error))
            }
        }

        LockNotifier.updateFixStatus(this, getString(R.string.system_fix_applying_bands))
        val bandResults = applyCurrentBandLocks(controller)
        val bandSuccess = bandResults.isNotEmpty() && bandResults.all { it.second }
        val outcome =
            when {
                plmnSuccess && bandSuccess -> getString(R.string.system_fix_completed, plmnMessage)
                plmnSuccess -> getString(R.string.system_fix_partial, plmnMessage, bandSummary(bandResults))
                bandSuccess -> getString(R.string.system_fix_partial, plmnMessage, bandSummary(bandResults))
                else -> getString(R.string.system_fix_failed, "$plmnMessage; ${bandSummary(bandResults)}")
            }
        LockNotifier.updateFixStatus(this, outcome)
    }

    private suspend fun applyCurrentBandLocks(controller: ModemController): List<Pair<Group, Boolean>> {
        val prefs = getSharedPreferences(LockNotifier.PREFERENCES_NAME, Context.MODE_PRIVATE)
        val groups = controller.state.value.groups
        val targets =
            groups.mapNotNull { state ->
                val saved = prefs.getString(LockNotifier.bandTargetPreferenceKey(state.group.name), null)
                val selected =
                    if (saved != null) {
                        saved.split(',').mapNotNull(String::toIntOrNull).toSet()
                    } else {
                        state.current?.takeIf { state.locked }
                    }
                selected?.let { state to it.intersect(state.supported.toSet()) }
            }
        if (targets.isEmpty()) return emptyList()

        return targets.map { (state, bands) ->
            if (bands.isEmpty()) return@map state.group to false
            try {
                controller.lockBands(state.group, bands)
                controller.refreshNow()
                val verified: BandGroupState? = controller.state.value.groups.firstOrNull { it.group == state.group }
                state.group to (verified?.current == bands)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not restore ${state.group} band lock", e)
                state.group to false
            }
        }
    }

    private fun bandSummary(results: List<Pair<Group, Boolean>>): String =
        when {
            results.isEmpty() -> getString(R.string.system_fix_no_band_locks)
            results.all { it.second } -> getString(R.string.system_fix_bands_confirmed, results.size)
            else -> getString(
                R.string.system_fix_bands_partial,
                results.count { it.second },
                results.size,
            )
        }

    private fun formatPlmn(numeric: String) = "${numeric.take(3)}-${numeric.drop(3)}"

    override fun onDestroy() {
        fixJob?.cancel()
        modem?.close()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CONNECT_TIMEOUT_MILLIS = 120_000L
        private const val TAG = "BunnyLockFix"
        const val PREFERENCES_NAME = "bunnylock"

        fun start(context: Context) {
            val intent = Intent(context.applicationContext, NotificationFixService::class.java)
                .setAction(LockNotifier.ACTION_FIX)
            ContextCompat.startForegroundService(context.applicationContext, intent)
        }
    }
}
