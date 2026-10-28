package dev.ririxi.bunnylock.data

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.service.quicksettings.TileService
import dev.ririxi.bunnylock.MainActivity
import dev.ririxi.bunnylock.R
import dev.ririxi.bunnylock.system.RebootModemTileService
import dev.ririxi.bunnylock.system.RestartRadioTileService
import dev.ririxi.bunnylock.system.ToggleLocksTileService
import org.json.JSONArray

object LockNotifier {
    /** إجراء فتح كل الأقفال الذي يمكن أن تستقبله Activity. */
    const val ACTION_UNLOCK_ALL = "dev.ririxi.bunnylock.action.UNLOCK_ALL"
    /** إجراء تبديل الأقفال من أدوات النظام؛ قد يبقى غير معروض إذا عُطل زر الإشعار. */
    const val ACTION_TOGGLE_LOCKS = "dev.ririxi.bunnylock.action.TOGGLE_LOCKS"
    /** إجراء إعادة تشغيل الراديو من الإشعار أو Quick Settings. */
    const val ACTION_RESTART_RADIO = "dev.ririxi.bunnylock.action.RESTART_RADIO"
    /** إجراء إعادة تشغيل المودم من الإشعار أو Quick Settings. */
    const val ACTION_REBOOT_MODEM = "dev.ririxi.bunnylock.action.REBOOT_MODEM"
    /** إجراء إصلاح الشبكة من الإشعار دون إعادة تشغيل الراديو أو المودم. */
    const val ACTION_FIX = "dev.ririxi.bunnylock.action.FIX"
    /** إجراء تبديل Game Mode المحفوظ دون إرسال إعداد مباشر إلى المودم. */
    const val ACTION_TOGGLE_GAME_MODE = "dev.ririxi.bunnylock.action.TOGGLE_GAME_MODE"
    // قناة هادئة وإشعار ثابت لتجنب التنبيه المتكرر ما دامت الأقفال فعالة.
    private const val CHANNEL_ID = "active_locks_silent_v2"
    private const val NOTIFICATION_ID = 1
    private const val PREFS = "bunnylock"
    private const val KEY_LINES = "notificationLines"
    private const val KEY_LOCK_ACTIVE = "notificationLockActive"
    const val PREFERENCES_NAME = PREFS

    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)

    private fun ensureChannel(context: Context) {
        val channel =
            NotificationChannel(CHANNEL_ID, context.getString(R.string.system_notify_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.system_notify_channel_description)
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
        manager(context).createNotificationChannel(channel)
    }

    /** يحدث إشعار الأقفال ومربعات Quick Settings؛ يفشل العرض بأمان إذا لم يمنح المستخدم إذن الإشعارات. */
    fun show(
        context: Context,
        lines: List<String>,
        lockedSinceMillis: Long,
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_LINES, JSONArray(lines).toString())
            .putBoolean(KEY_LOCK_ACTIVE, true)
            .apply()
        refreshTiles(context)
        val permissionMissing =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (permissionMissing) return
        ensureChannel(context)
        val openApp =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val restartRadio = actionIntent(context, ACTION_RESTART_RADIO, 2)
        val fix = fixActionIntent(context)
        val toggleGameMode = actionIntent(context, ACTION_TOGGLE_GAME_MODE, 5)
        val restore =
            PendingIntent.getBroadcast(
                context,
                4,
                Intent(context, NotificationRestoreReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val notification = buildNotification(context, lines, lockedSinceMillis, null, openApp, restore, toggleGameMode, restartRadio, fix)
        manager(context).notify(NOTIFICATION_ID, notification)
    }

    /** يعرض حالة Smart Fix داخل الإشعار الحالي مع الحفاظ على أفعاله ومحتواه الأصلي. */
    fun updateFixStatus(context: Context, status: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(context)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val savedLines = prefs.getString(KEY_LINES, null)
        val lines =
            savedLines?.let { encoded ->
                val values = JSONArray(encoded)
                List(values.length()) { values.getString(it) }
            } ?: emptyList()
        val lockedSince = prefs.getLong("lockedSince", System.currentTimeMillis())
        manager(context).notify(NOTIFICATION_ID, notificationForFix(context, lines, lockedSince, status))
    }

    /** يعيد الإشعار نفسه كي تستخدمه خدمة الإصلاح الأمامية دون إنشاء إشعار متعارض. */
    fun notificationForFix(context: Context, status: String): Notification {
        ensureChannel(context)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val savedLines = prefs.getString(KEY_LINES, null)
        val lines =
            savedLines?.let { encoded ->
                val values = JSONArray(encoded)
                List(values.length()) { values.getString(it) }
            } ?: emptyList()
        return notificationForFix(context, lines, prefs.getLong("lockedSince", System.currentTimeMillis()), status)
    }

    private fun notificationForFix(
        context: Context,
        lines: List<String>,
        lockedSinceMillis: Long,
        status: String,
    ): Notification {
        val openApp =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val restore =
            PendingIntent.getBroadcast(
                context,
                4,
                Intent(context, NotificationRestoreReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val toggleGameMode = actionIntent(context, ACTION_TOGGLE_GAME_MODE, 5)
        val restartRadio = actionIntent(context, ACTION_RESTART_RADIO, 2)
        val fix = fixActionIntent(context)
        return buildNotification(context, lines, lockedSinceMillis, status, openApp, restore, toggleGameMode, restartRadio, fix)
    }

    private fun buildNotification(
        context: Context,
        lines: List<String>,
        lockedSinceMillis: Long,
        fixStatus: String?,
        openApp: PendingIntent,
        restore: PendingIntent,
        toggleGameMode: PendingIntent,
        restartRadio: PendingIntent,
        fix: PendingIntent,
    ): Notification {
        val content = lines.joinToString(context.getString(R.string.system_notify_line_separator))
        val expandedText = listOfNotNull(fixStatus, lines.takeIf { it.isNotEmpty() }?.joinToString("\n")).joinToString("\n")
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_lock)
            .setContentTitle(context.getString(R.string.system_notify_title))
            .setContentText(fixStatus ?: content)
            .setStyle(Notification.BigTextStyle().bigText(expandedText.ifBlank { content }))
            .setWhen(lockedSinceMillis)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp)
            .setDeleteIntent(restore)
            .addAction(Notification.Action.Builder(null, context.getString(R.string.system_notify_toggle_game_mode), toggleGameMode).build())
            .addAction(Notification.Action.Builder(null, context.getString(R.string.system_notify_restart_radio), restartRadio).build())
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_menu_manage,
                    context.getString(R.string.system_notify_fix),
                    fix,
                ).build(),
            )
            .build()
    }

    /** يعيد بناء إشعار الأقفال من بيانات التفضيلات بعد استعادة النظام. */
    fun restore(context: Context) {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LINES, null) ?: return
        val lines = JSONArray(saved).let { values -> List(values.length()) { values.getString(it) } }
        if (lines.isEmpty()) return
        val lockedSince = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("lockedSince", System.currentTimeMillis())
        show(context, lines, lockedSince)
    }

    /** يمسح حالة الإشعار المحفوظة ويطلب تحديث البلاطات بعد زوال الأقفال. */
    fun cancel(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_LINES)
            .putBoolean(KEY_LOCK_ACTIVE, false)
            .apply()
        refreshTiles(context)
        manager(context).cancel(NOTIFICATION_ID)
    }

    /** يقرأ حالة الأقفال المحفوظة لتحديد حالة البلاطات دون استعلام المودم. */
    fun locksActive(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_LOCK_ACTIVE, false)

    private fun refreshTiles(context: Context) {
        listOf(ToggleLocksTileService::class.java, RestartRadioTileService::class.java, RebootModemTileService::class.java)
            .forEach { service -> TileService.requestListeningState(context, ComponentName(context, service)) }
    }

    private fun actionIntent(context: Context, action: String, requestCode: Int) =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, NotificationActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun fixActionIntent(context: Context) =
        PendingIntent.getBroadcast(
            context,
            6,
            Intent(context, NotificationActionReceiver::class.java).setAction(ACTION_FIX),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun bandTargetPreferenceKey(groupName: String) = "fixBandSelection.$groupName"
}
