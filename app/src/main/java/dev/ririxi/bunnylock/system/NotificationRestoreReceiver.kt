package dev.ririxi.bunnylock.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.ririxi.bunnylock.data.LockNotifier

class NotificationRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        LockNotifier.restore(context)
    }
}
