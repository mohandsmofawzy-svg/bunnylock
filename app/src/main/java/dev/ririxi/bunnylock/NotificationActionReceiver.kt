package dev.ririxi.bunnylock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import dev.ririxi.bunnylock.data.LockNotifier

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != LockNotifier.ACTION_LOCK_BAND && intent.action != LockNotifier.ACTION_UNLOCK_ALL) return
        val serviceIntent =
            Intent(context, NotificationActionService::class.java)
                .setAction(intent.action)
                .putExtra(LockNotifier.EXTRA_SIM_SLOT, intent.getIntExtra(LockNotifier.EXTRA_SIM_SLOT, -1))
                .putExtra(
                    LockNotifier.EXTRA_SUBSCRIPTION_ID,
                    intent.getIntExtra(LockNotifier.EXTRA_SUBSCRIPTION_ID, -1),
                )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(context, serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
