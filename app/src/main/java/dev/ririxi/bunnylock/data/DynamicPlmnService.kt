package dev.ririxi.bunnylock.data

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import dev.ririxi.bunnylock.MainActivity
import dev.ririxi.bunnylock.R
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
import java.util.concurrent.TimeUnit

data class DynamicPlmnStatus(
    val enabled: Boolean = false,
    val serviceRunning: Boolean = false,
    val state: String = DynamicPlmnService.STATE_STOPPED,
    val lastCheckAt: Long = 0L,
    val simName: String = "",
    val homePlmn: String = "",
    val result: String = "",
    val error: String = "",
)

/** خدمة مرئية للمستخدم؛ إبقاء Activity أو ViewModel مفتوحًا ليس شرطًا لدورة الفحص. */
class DynamicPlmnService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var monitorJob: Job? = null
    private var modem: ModemController? = null

    override fun onCreate() {
        super.onCreate()
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, false).apply()
            stopMonitoring()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val prefs = getSharedPreferences(PREFERENCES, MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_ENABLED, false)) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_RETRY) {
            monitorJob?.cancel()
            monitorJob = null
        }
        try {
            startAsForeground()
        } catch (e: RuntimeException) {
            recordStartFailure(this, e)
            Log.e(TAG, "Could not enter foreground", e)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        running = true
        writeStatus(this, state = STATE_STARTING, error = "")
        if (monitorJob?.isActive != true) {
            monitorJob = serviceScope.launch { monitorLoop() }
        }
        return START_STICKY
    }

    private fun startAsForeground() {
        createChannel()
        val notification = buildNotification(getString(R.string.dynamic_plmn_notification_starting))
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
            NotificationChannel(CHANNEL_ID, getString(R.string.dynamic_plmn_notification_channel), NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.dynamic_plmn_notification_channel_description)
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(message: String): Notification {
        val openApp =
            PendingIntent.getActivity(
                this,
                40,
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val stop =
            PendingIntent.getService(
                this,
                41,
                Intent(this, DynamicPlmnService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_lock)
            .setContentTitle(getString(R.string.dynamic_plmn_title))
            .setContentText(message)
            .setStyle(Notification.BigTextStyle().bigText(message))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                Notification.Action.Builder(null, getString(R.string.dynamic_plmn_stop), stop).build(),
            )
            .build()
    }

    private suspend fun monitorLoop() {
        var consecutiveFailures = 0
        while (serviceScope.isActive && isEnabled()) {
            try {
                checkAndRepair()
                consecutiveFailures = 0
                delay(checkIntervalMillis())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                consecutiveFailures++
                val wait = retryDelay(consecutiveFailures)
                val message = e.message?.takeIf(String::isNotBlank) ?: getString(R.string.dynamic_plmn_error_generic)
                writeStatus(
                    this,
                    state = STATE_ERROR,
                    result = message,
                    error = message,
                    lastCheckAt = System.currentTimeMillis(),
                )
                updateNotification(getString(R.string.dynamic_plmn_notification_error, message, wait / 1000))
                delay(wait)
            }
        }
        if (!isEnabled()) stopSelf()
    }

    private suspend fun checkAndRepair() {
        writeStatus(this, state = STATE_CHECKING, error = "")
        updateNotification(getString(R.string.dynamic_plmn_notification_checking))

        val controller = modem ?: ModemController(this, serviceScope, RootOrShizuku(this) {}).also { modem = it }
        if (!controller.helperRunning) {
            controller.connect()
            withTimeout(CONNECT_TIMEOUT_MILLIS) {
                while (controller.state.value.root == RootState.Checking) delay(250)
            }
        }
        val connectedState = controller.state.value
        if (connectedState.root != RootState.Granted || !controller.helperRunning) {
            val reason = (connectedState.root as? RootState.Denied)?.reason
            throw IllegalStateException(reason ?: getString(R.string.dynamic_plmn_helper_unavailable))
        }
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            throw IllegalStateException(getString(R.string.dynamic_plmn_permission_required))
        }

        val activeDataSubId = SubscriptionManager.getDefaultDataSubscriptionId()
        if (activeDataSubId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            throw IllegalStateException(getString(R.string.dynamic_plmn_no_active_sim))
        }
        controller.loadSims()
        val sim = controller.state.value.sims.firstOrNull { it.subId == activeDataSubId }
            ?: throw IllegalStateException(getString(R.string.dynamic_plmn_no_active_sim))
        if (controller.state.value.sim != sim.slot + 1) controller.setSim(sim.slot + 1)

        // تُسلسل القراءة والأمر مع عمليات القفل الأخرى عبر قفل ModemController المشترك.
        controller.refreshNow()
        val observed = controller.state.value
        if (observed.root != RootState.Granted || !controller.helperRunning) {
            throw IllegalStateException(getString(R.string.dynamic_plmn_helper_unavailable))
        }
        val telephony = getSystemService(TelephonyManager::class.java).createForSubscriptionId(activeDataSubId)
        val homePlmn = telephony.simOperator?.takeIf { it.matches(Regex("\\d{5,6}")) }
            ?: observed.homePlmn?.takeIf { it.matches(Regex("\\d{5,6}")) }
            ?: throw IllegalStateException(getString(R.string.dynamic_plmn_home_unknown))
        val target = "$activeDataSubId:$homePlmn"
        val attemptPrefs = getSharedPreferences(PREFERENCES, MODE_PRIVATE)
        val savedTarget = attemptPrefs.getString(KEY_LOCK_ATTEMPT_TARGET, null)
        if (savedTarget != target) {
            attemptPrefs.edit()
                .putString(KEY_LOCK_ATTEMPT_TARGET, target)
                .putInt(KEY_LOCK_ATTEMPTS, 0)
                .apply()
        }
        var lockAttempts = attemptPrefs.getInt(KEY_LOCK_ATTEMPTS, 0)
        val simName = sim.name
        writeStatus(
            this,
            state = STATE_CHECKING,
            simName = simName,
            homePlmn = formatPlmn(homePlmn),
            lastCheckAt = System.currentTimeMillis(),
        )

        if (!observed.plmnLockKnown) {
            throw IllegalStateException(getString(R.string.dynamic_plmn_lock_state_unknown))
        }
        val lockDigits = observed.plmnLock?.filter(Char::isDigit)
        if (lockDigits == homePlmn) {
            attemptPrefs.edit().putInt(KEY_LOCK_ATTEMPTS, 0).apply()
            val result = getString(R.string.dynamic_plmn_confirmed, formatPlmn(homePlmn))
            writeStatus(this, state = STATE_RUNNING, result = result, error = "", lastCheckAt = System.currentTimeMillis())
            updateNotification(result)
            return
        }
        if (observed.plmnLock != null) {
            throw IllegalStateException(getString(R.string.dynamic_plmn_other_lock, observed.plmnLock))
        }
        if (lockAttempts >= MAX_LOCK_ATTEMPTS) {
            throw IllegalStateException(getString(R.string.dynamic_plmn_attempt_limit))
        }

        writeStatus(this, state = STATE_RETRYING, result = getString(R.string.dynamic_plmn_applying), error = "")
        lockAttempts++
        attemptPrefs.edit().putInt(KEY_LOCK_ATTEMPTS, lockAttempts).apply()
        controller.lockPlmn(homePlmn.take(3), homePlmn.drop(3))
        controller.refreshNow()
        val verified = controller.state.value
        val result =
            when {
                verified.plmnLockKnown && verified.plmnLock?.filter(Char::isDigit) == homePlmn ->
                    getString(R.string.dynamic_plmn_confirmed, formatPlmn(homePlmn))
                !verified.plmnLockKnown -> getString(R.string.dynamic_plmn_lock_state_unknown)
                else -> getString(R.string.dynamic_plmn_unconfirmed)
            }
        if (verified.plmnLockKnown && verified.plmnLock?.filter(Char::isDigit) == homePlmn) {
            attemptPrefs.edit().putInt(KEY_LOCK_ATTEMPTS, 0).apply()
            writeStatus(this, state = STATE_RUNNING, result = result, error = "", lastCheckAt = System.currentTimeMillis())
        } else {
            throw IllegalStateException(result)
        }
        updateNotification(result)
    }

    private fun formatPlmn(numeric: String): String = "${numeric.take(3)}-${numeric.drop(3)}"

    private fun retryDelay(failures: Int): Long =
        when (failures) {
            1 -> CHECK_INTERVAL_MILLIS
            2 -> TimeUnit.MINUTES.toMillis(2)
            3 -> TimeUnit.MINUTES.toMillis(5)
            else -> TimeUnit.MINUTES.toMillis(15)
        }

    private fun updateNotification(message: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(message))
    }

    private fun isEnabled() = getSharedPreferences(PREFERENCES, MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    private fun checkIntervalMillis(): Long =
        getSharedPreferences(PREFERENCES, MODE_PRIVATE)
            .getLong(KEY_INTERVAL_MILLIS, CHECK_INTERVAL_MILLIS)
            .coerceAtLeast(CHECK_INTERVAL_MILLIS)

    private fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
        modem?.close()
        modem = null
        running = false
        writeStatus(this, state = STATE_STOPPED, error = "")
    }

    override fun onDestroy() {
        stopMonitoring()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val PREFERENCES = "bunnylock"
        const val KEY_ENABLED = "dynamicPlmnEnabled"
        private const val KEY_INTERVAL_MILLIS = "dynamicPlmnIntervalMillis"
        const val STATE_STOPPED = "STOPPED"
        const val STATE_STOPPING = "STOPPING"
        const val STATE_STARTING = "STARTING"
        const val STATE_RUNNING = "RUNNING"
        const val STATE_CHECKING = "CHECKING"
        const val STATE_RETRYING = "RETRYING"
        const val STATE_ERROR = "ERROR"
        private const val ACTION_START = "dev.ririxi.bunnylock.action.START_DYNAMIC_PLMN"
        private const val ACTION_STOP = "dev.ririxi.bunnylock.action.STOP_DYNAMIC_PLMN"
        private const val ACTION_RETRY = "dev.ririxi.bunnylock.action.RETRY_DYNAMIC_PLMN"
        private const val KEY_STATE = "dynamicPlmnState"
        private const val KEY_LAST_CHECK = "dynamicPlmnLastCheck"
        private const val KEY_SIM_NAME = "dynamicPlmnSimName"
        private const val KEY_HOME_PLMN = "dynamicPlmnHome"
        private const val KEY_RESULT = "dynamicPlmnResult"
        private const val KEY_ERROR = "dynamicPlmnError"
        private const val KEY_LOCK_ATTEMPT_TARGET = "dynamicPlmnLockAttemptTarget"
        private const val KEY_LOCK_ATTEMPTS = "dynamicPlmnLockAttempts"
        private const val CHANNEL_ID = "dynamic_plmn_service"
        private const val NOTIFICATION_ID = 24
        private const val CHECK_INTERVAL_MILLIS = 60_000L
        private const val MAX_LOCK_ATTEMPTS = 5
        private const val CONNECT_TIMEOUT_MILLIS = 120_000L
        private const val TAG = "BunnyLockDynamic"

        @Volatile
        private var running = false

        fun isServiceRunning(): Boolean = running

        fun status(context: Context): DynamicPlmnStatus {
            val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            val runningNow = isServiceRunning()
            return DynamicPlmnStatus(
                enabled = prefs.getBoolean(KEY_ENABLED, false),
                serviceRunning = runningNow,
                state =
                    if (runningNow && !prefs.getBoolean(KEY_ENABLED, false)) {
                        STATE_STOPPING
                    } else if (runningNow) {
                        prefs.getString(KEY_STATE, STATE_STARTING) ?: STATE_STARTING
                    } else if (prefs.getBoolean(KEY_ENABLED, false)) {
                        STATE_ERROR
                    } else {
                        STATE_STOPPED
                    },
                lastCheckAt = prefs.getLong(KEY_LAST_CHECK, 0L),
                simName = prefs.getString(KEY_SIM_NAME, "").orEmpty(),
                homePlmn = prefs.getString(KEY_HOME_PLMN, "").orEmpty(),
                result = prefs.getString(KEY_RESULT, "").orEmpty(),
                error = prefs.getString(KEY_ERROR, "").orEmpty(),
            )
        }

        fun start(context: Context) {
            val appContext = context.applicationContext
            appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_ENABLED, true)
                .putLong(KEY_INTERVAL_MILLIS, CHECK_INTERVAL_MILLIS)
                .putString(KEY_STATE, STATE_STARTING)
                .putString(KEY_ERROR, "")
                .apply()
            val intent = Intent(appContext, DynamicPlmnService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(appContext, intent)
        }

        fun stop(context: Context) {
            val appContext = context.applicationContext
            appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, false).apply()
            if (isServiceRunning()) {
                appContext.startService(Intent(appContext, DynamicPlmnService::class.java).setAction(ACTION_STOP))
            } else {
                writeStatus(appContext, state = STATE_STOPPED, error = "")
            }
        }

        fun retry(context: Context) {
            val appContext = context.applicationContext
            appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_ENABLED, true)
                .putLong(KEY_INTERVAL_MILLIS, CHECK_INTERVAL_MILLIS)
                .apply()
            val intent = Intent(appContext, DynamicPlmnService::class.java).setAction(ACTION_RETRY)
            if (isServiceRunning()) {
                appContext.startService(intent)
            } else {
                ContextCompat.startForegroundService(appContext, intent)
            }
        }

        fun recordStartFailure(context: Context, error: Exception) {
            val message = error.message?.takeIf(String::isNotBlank) ?: context.getString(R.string.dynamic_plmn_start_failed)
            writeStatus(context, state = STATE_ERROR, result = message, error = message)
        }

        private fun writeStatus(
            context: Context,
            state: String,
            simName: String? = null,
            homePlmn: String? = null,
            result: String? = null,
            error: String? = null,
            lastCheckAt: Long? = null,
        ) {
            val edit = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putString(KEY_STATE, state)
            simName?.let { edit.putString(KEY_SIM_NAME, it) }
            homePlmn?.let { edit.putString(KEY_HOME_PLMN, it) }
            result?.let { edit.putString(KEY_RESULT, it) }
            error?.let { edit.putString(KEY_ERROR, it) }
            lastCheckAt?.let { edit.putLong(KEY_LAST_CHECK, it) }
            edit.apply()
        }
    }
}
