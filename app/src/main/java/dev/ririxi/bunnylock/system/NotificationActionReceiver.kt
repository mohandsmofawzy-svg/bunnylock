package dev.ririxi.bunnylock.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.ririxi.bunnylock.data.LockNotifier
import dev.ririxi.bunnylock.data.ProfileStore
import dev.ririxi.bunnylock.modem.CellLockRequest
import dev.ririxi.bunnylock.modem.LockRat
import dev.ririxi.bunnylock.modem.ModemController
import dev.ririxi.bunnylock.shizuku.RootOrShizuku
import dev.ririxi.bunnylock.ui.LockProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in ACTIONS) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            var modem: ModemController? = null
            try {
                val connected = CompletableDeferred<Unit>()
                modem = ModemController(
                    appContext,
                    scope,
                    RootOrShizuku(appContext) {},
                    onConnected = { connected.complete(Unit) },
                )
                modem.connect()
                withTimeout(CONNECT_TIMEOUT_MILLIS) { connected.await() }
                performAction(appContext, modem, action)
            } catch (_: Exception) {
                // Notification actions have no foreground UI for reporting an error.
            } finally {
                modem?.close()
                scope.cancel()
                pendingResult.finish()
            }
        }
    }

    private suspend fun performAction(context: Context, modem: ModemController, action: String) {
        when (action) {
            LockNotifier.ACTION_TOGGLE_LOCKS -> {
                val state = modem.state.value
                if (state.anyBandLock || state.anyCellLock || state.anyPlmnLock) {
                    modem.unlockEverything()
                } else {
                    ProfileStore.load(context).firstOrNull()?.let { applyProfile(modem, it) }
                }
                modem.refreshNow()
                val updated = modem.state.value
                if (!updated.anyBandLock && !updated.anyCellLock && !updated.anyPlmnLock) LockNotifier.cancel(context)
            }

            LockNotifier.ACTION_RESTART_RADIO -> modem.radioCycle()
            LockNotifier.ACTION_REBOOT_MODEM -> modem.rebootModem()
        }
    }

    private suspend fun applyProfile(modem: ModemController, profile: LockProfile) {
        profile.bands.forEach { (group, bands) ->
            if (bands.isEmpty()) modem.unlockBands(group) else modem.lockBands(group, bands)
        }
        val form = profile.cell
        if (form == null) {
            modem.unlockCells()
            return
        }
        modem.lockCells(
            CellLockRequest(
                rat = if (modem.state.value.supports5g) form.rat else LockRat.LTE,
                earfcn = form.lteEarfcn.toLongOrNull(),
                ltePcis = form.ltePcis.toInts(),
                nrArfcn = form.nrArfcn.toLongOrNull(),
                nrPcis = form.nrPcis.toInts(),
                nrBand = form.nrBand ?: form.nrCandidates.firstOrNull(),
                nrScs = form.nrScs,
            ),
        )
    }

    private fun String.toInts() = split(',', ' ', ';').mapNotNull { it.trim().toIntOrNull() }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 90_000L
        val ACTIONS = setOf(LockNotifier.ACTION_TOGGLE_LOCKS, LockNotifier.ACTION_RESTART_RADIO, LockNotifier.ACTION_REBOOT_MODEM)
    }
}