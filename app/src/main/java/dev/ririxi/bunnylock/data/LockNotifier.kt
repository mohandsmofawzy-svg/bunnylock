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
    const val ACTION_UNLOCK_ALL = "dev.ririxi.bunnylock.action.UNLOCK_ALL"
    const val ACTION_TOGGLE_LOCKS = "dev.ririxi.bunnylock.action.TOGGLE_LOCKS"
    const val ACTION_RESTART_RADIO = "dev.ririxi.bunnylock.action.RESTART_RADIO"
    const val ACTION_REBOOT_MODEM = "dev.ririxi.bunnylock.action.REBOOT_MODEM"
    const val ACTION_TOGGLE_GAME_MODE = "dev.ririxi.bunnylock.action.TOGGLE_GAME_MODE"
    private const val CHANNEL_ID = "active_locks_silent_v2"
    private const val NOTIFICATION_ID = 1
    private const val PREFS = "bunnylock"
    private const val KEY_LINES = "notificationLines"
    private const val KEY_LOCK_ACTIVE = "notificationLockActive"

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
        val toggleLocks =
            PendingIntent.getBroadcast(
                context,
                1,
                Intent(context, NotificationActionReceiver::class.java).setAction(ACTION_TOGGLE_LOCKS),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val restartRadio = actionIntent(context, ACTION_RESTART_RADIO, 2)
        val rebootModem = actionIntent(context, ACTION_REBOOT_MODEM, 3)
        val toggleGameMode = actionIntent(context, ACTION_TOGGLE_GAME_MODE, 5)
        val restore =
            PendingIntent.getBroadcast(
                context,
                4,
                Intent(context, NotificationRestoreReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val notification =
            Notification
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_lock)
                .setContentTitle(context.getString(R.string.system_notify_title))
                .setContentText(lines.joinToString(context.getString(R.string.system_notify_line_separator)))
                .setStyle(Notification.BigTextStyle().bigText(lines.joinToString("\n")))
                .setWhen(lockedSinceMillis)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openApp)
                .setDeleteIntent(restore)
               // .addAction(Notification.Action.Builder(null, context.getString(R.string.system_notify_toggle_locks), toggleLocks).build())
                .addAction(Notification.Action.Builder(null, context.getString(R.string.system_notify_toggle_game_mode), toggleGameMode).build())
                .addAction(Notification.Action.Builder(null, context.getString(R.string.system_notify_restart_radio), restartRadio).build())
                .addAction(Notification.Action.Builder(null, context.getString(R.string.system_notify_reboot_modem), rebootModem).build())
                .build()
        manager(context).notify(NOTIFICATION_ID, notification)
    }

    fun restore(context: Context) {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LINES, null) ?: return
        val lines = JSONArray(saved).let { values -> List(values.length()) { values.getString(it) } }
        if (lines.isEmpty()) return
        val lockedSince = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("lockedSince", System.currentTimeMillis())
        show(context, lines, lockedSince)
    }

    fun cancel(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_LINES)
            .putBoolean(KEY_LOCK_ACTIVE, false)
            .apply()
        refreshTiles(context)
        manager(context).cancel(NOTIFICATION_ID)
    }

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
}
