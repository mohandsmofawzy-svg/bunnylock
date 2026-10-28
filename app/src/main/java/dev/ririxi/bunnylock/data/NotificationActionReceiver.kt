package dev.ririxi.bunnylock.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import dev.ririxi.bunnylock.R
import dev.ririxi.bunnylock.modem.ModemController
import dev.ririxi.bunnylock.shizuku.RootOrShizuku
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** ينفذ إجراءات الإشعار؛ العمليات غير المتزامنة تستخدم goAsync ثم تنهي النتيجة وتغلق مواردها. */
class NotificationActionReceiver : BroadcastReceiver() {
    /** يوجه الإجراء إلى تغيير تفضيل محلي أو عملية مودم قصيرة خارج دورة حياة Activity. */
    override fun onReceive(context: Context, intent: Intent) {
        if (context.getSharedPreferences(DynamicPlmnService.PREFERENCES, Context.MODE_PRIVATE)
                .getBoolean("appExitRequested", false)
        ) return
        when (intent.action) {
            LockNotifier.ACTION_TOGGLE_LOCKS -> {
                Toast.makeText(context, "Toggled locks", Toast.LENGTH_SHORT).show()
            }
            LockNotifier.ACTION_TOGGLE_GAME_MODE -> {
                val currentMode = ModemController.isGameModeEnabled(context)
                ModemController.setGameModeEnabled(context, !currentMode)
                try {
                    GameModeService.sync(context, enabled = !currentMode)
                } catch (e: RuntimeException) {
                    Log.e(TAG, "Could not update Game Mode monitoring", e)
                    Toast.makeText(context, context.getString(R.string.game_mode_notification_start_failed), Toast.LENGTH_LONG).show()
                    return
                }
                val statusText = if (!currentMode) "Game Mode Enabled" else "Game Mode Disabled"
                Toast.makeText(context, statusText, Toast.LENGTH_SHORT).show()
            }
            LockNotifier.ACTION_RESTART_RADIO -> {
                Toast.makeText(context, "Restarting Radio...", Toast.LENGTH_SHORT).show()
                runModemAction(context, LockNotifier.ACTION_RESTART_RADIO)
            }
            LockNotifier.ACTION_REBOOT_MODEM -> {
                Toast.makeText(context, "Rebooting Modem...", Toast.LENGTH_SHORT).show()
                runModemAction(context, LockNotifier.ACTION_REBOOT_MODEM)
            }
            LockNotifier.ACTION_FIX -> {
                try {
                    NotificationFixService.start(context)
                } catch (e: RuntimeException) {
                    LockNotifier.updateFixStatus(
                        context,
                        context.getString(R.string.system_fix_failed, e.message ?: context.getString(R.string.system_fix_start_error)),
                    )
                }
            }
        }
    }

    /** ينفذ أمرًا يحتاج اتصال المساعد مع مهلة محدودة ويضمن إيقاف المساعد والنطاق عند الانتهاء. */
    private fun runModemAction(context: Context, action: String) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val pendingFinished = AtomicBoolean(false)
        fun finishPendingResult() {
            if (pendingFinished.compareAndSet(false, true)) pendingResult.finish()
        }
        val job =
            scope.launch {
                var modem: ModemController? = null
                try {
                    val connected = CompletableDeferred<Unit>()
                    modem = ModemController(
                        appContext,
                        scope,
                        RootOrShizuku(appContext) {},
                        onConnected = { connected.complete(Unit) },
                    )
                    modem.connect()
                    withTimeout(CONNECT_TIMEOUT_MILLIS) { connected.await() }
                    when (action) {
                        LockNotifier.ACTION_RESTART_RADIO -> modem.radioCycle()
                        LockNotifier.ACTION_REBOOT_MODEM -> modem.rebootModem()
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e(TAG, "Could not complete notification modem action", e)
                } finally {
                    try {
                        modem?.close()
                    } finally {
                        scope.cancel()
                        finishPendingResult()
                    }
                }
            }
        synchronized(actionLock) {
            if (stoppingForExit) {
                job.cancel(CancellationException("Application is exiting"))
            } else {
                activeModemActions += job
            }
        }
        job.invokeOnCompletion {
            activeModemActions -= job
            scope.cancel()
            finishPendingResult()
        }
    }

    companion object {
        // حد انتظار إنشاء اتصال المساعد قبل التخلي عن إجراء الإشعار.
        private const val CONNECT_TIMEOUT_MILLIS = 90_000L
        private const val TAG = "BunnyLockAction"
        private val actionLock = Any()
        private val activeModemActions = ConcurrentHashMap.newKeySet<Job>()
        @Volatile
        private var stoppingForExit = false

        fun stopForExit(): List<Job> {
            val jobs =
                synchronized(actionLock) {
                    stoppingForExit = true
                    activeModemActions.toList()
                }
            jobs.forEach { it.cancel(CancellationException("Application is exiting")) }
            return jobs
        }

        fun resumeAfterExit() {
            synchronized(actionLock) { stoppingForExit = false }
        }
    }
}