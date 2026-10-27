package dev.ririxi.bunnylock.ui // أو حسب الـ Package الأساسية للمشروع عندك

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.ririxi.bunnylock.modem.ModemController

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TOGGLE_GAME_MODE -> {
                val currentMode = ModemController.isGameModeEnabled(context)
                ModemController.setGameModeEnabled(context, !currentMode)
            }
            // أية أアクションز تانية خلفية تحب تضيفها مستقبلاً
        }
    }

    companion object {
        const val ACTION_TOGGLE_GAME_MODE = "dev.ririxi.bunnylock.ACTION_TOGGLE_GAME_MODE"
    }
}