package dev.ririxi.bunnylock.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.ririxi.bunnylock.modem.ModemController

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            LockNotifier.ACTION_TOGGLE_LOCKS -> {
                // تنفيذ أكشن فتح/قفل الشبكة هنا أو استدعاء الوظيفة المناسبة
            }
            LockNotifier.ACTION_TOGGLE_GAME_MODE -> {
                val currentMode = ModemController.isGameModeEnabled(context)
                ModemController.setGameModeEnabled(context, !currentMode)
            }
            LockNotifier.ACTION_RESTART_RADIO -> {
                // تنفيذ إعادة تشغيل الراديو هنا
            }
            LockNotifier.ACTION_REBOOT_MODEM -> {
                // تنفيذ إعادة تشغيل المودم هنا
            }
        }
    }
}