package dev.ririxi.bunnylock.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import dev.ririxi.bunnylock.modem.ModemController
import dev.ririxi.bunnylock.shizuku.RootOrShizuku
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** ينفذ إجراءات الإشعار؛ العمليات غير المتزامنة تستخدم goAsync ثم تنهي النتيجة وتغلق مواردها. */
class NotificationActionReceiver : BroadcastReceiver() {
    /** يوجه الإجراء إلى تغيير تفضيل محلي أو عملية مودم قصيرة خارج دورة حياة Activity. */
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            LockNotifier.ACTION_TOGGLE_LOCKS -> {
                Toast.makeText(context, "Toggled locks", Toast.LENGTH_SHORT).show()
            }
            LockNotifier.ACTION_TOGGLE_GAME_MODE -> {
                val currentMode = ModemController.isGameModeEnabled(context)
                ModemController.setGameModeEnabled(context, !currentMode)
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
        }
    }

    /** ينفذ أمرًا يحتاج اتصال المساعد مع مهلة محدودة ويضمن إيقاف المساعد والنطاق عند الانتهاء. */
    private fun runModemAction(context: Context, action: String) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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
            } catch (_: Exception) {
                // Notification actions have no foreground UI for reporting an error.
            } finally {
                modem?.close()
                scope.cancel()
                pendingResult.finish()
            }
        }
    }

    private companion object {
        // حد انتظار إنشاء اتصال المساعد قبل التخلي عن إجراء الإشعار.
        const val CONNECT_TIMEOUT_MILLIS = 90_000L
    }
}