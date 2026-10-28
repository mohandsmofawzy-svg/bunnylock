package dev.ririxi.bunnylock.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** يعيد تشغيل المراقبة بعد الإقلاع فقط إذا ظل اختيار المستخدم محفوظًا على التشغيل. */
class DynamicPlmnBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = context.getSharedPreferences(DynamicPlmnService.PREFERENCES, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(DynamicPlmnService.KEY_ENABLED, false)) return
        try {
            DynamicPlmnService.start(context)
        } catch (e: RuntimeException) {
            DynamicPlmnService.recordStartFailure(context, e)
            Log.e(TAG, "Could not restore Dynamic PLMN after boot", e)
        }
    }

    private companion object {
        const val TAG = "BunnyLockDynamic"
    }
}
