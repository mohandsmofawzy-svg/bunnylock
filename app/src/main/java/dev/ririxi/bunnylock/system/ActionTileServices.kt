package dev.ririxi.bunnylock.system

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.ririxi.bunnylock.MainActivity
import dev.ririxi.bunnylock.R
import dev.ririxi.bunnylock.data.LockNotifier

abstract class ActionTileService : TileService() {
    abstract val action: String
    abstract val labelResource: Int

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        val isToggle = action == LockNotifier.ACTION_TOGGLE_LOCKS
        val active = isToggle && LockNotifier.locksActive(this)
        tile.label = getString(labelResource)
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isToggle) {
            tile.subtitle = getString(if (active) R.string.tile_locked else R.string.tile_unlocked)
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val intent =
            Intent(this, MainActivity::class.java)
                .setAction(action)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent =
                PendingIntent.getActivity(
                    this,
                    labelResource,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            startActivityAndCollapse(pendingIntent)
        } else {
            startActivityAndCollapse(intent)
        }
    }
}

class ToggleLocksTileService : ActionTileService() {
    override val action = LockNotifier.ACTION_TOGGLE_LOCKS
    override val labelResource = R.string.tile_toggle_label
}

class RestartRadioTileService : ActionTileService() {
    override val action = LockNotifier.ACTION_RESTART_RADIO
    override val labelResource = R.string.tile_restart_label
}

class RebootModemTileService : ActionTileService() {
    override val action = LockNotifier.ACTION_REBOOT_MODEM
    override val labelResource = R.string.tile_reboot_label
}
