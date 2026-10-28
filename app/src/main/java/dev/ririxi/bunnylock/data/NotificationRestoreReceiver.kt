package dev.ririxi.bunnylock.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** يعيد إشعار الأقفال المحفوظ بعد حدث استعادة النظام. */
class NotificationRestoreReceiver : BroadcastReceiver() {
    /** يعيد إنشاء الإشعار من بيانات التفضيلات؛ لا يرسل أوامر إلى المودم. */
    override fun onReceive(context: Context, intent: Intent) {
        LockNotifier.restore(context)
    }
}