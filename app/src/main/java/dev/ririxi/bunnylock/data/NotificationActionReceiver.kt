package dev.ririxi.bunnylock.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import dev.ririxi.bunnylock.modem.ModemController

class NotificationActionReceiver : BroadcastReceiver() {
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
                // لو الـ ModemController شغال من خلال خدمة أو كائن متاح، تقدر تستدعي دالتك هنا
                // أو تنفذ الأكشن بالطريقة المناسبة لمشروعك
            }
            LockNotifier.ACTION_REBOOT_MODEM -> {
                Toast.makeText(context, "Rebooting Modem...", Toast.LENGTH_SHORT).show()
                // استدعاء إعادة تشغيل المودم هنا
            }
        }
    }
}