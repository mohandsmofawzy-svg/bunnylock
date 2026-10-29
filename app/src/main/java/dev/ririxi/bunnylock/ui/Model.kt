package dev.ririxi.bunnylock.ui

import dev.ririxi.bunnylock.core.Bands
import dev.ririxi.bunnylock.data.Release
import dev.ririxi.bunnylock.modem.BandGroupState
import dev.ririxi.bunnylock.modem.Group
import dev.ririxi.bunnylock.modem.LockRat
import dev.ririxi.bunnylock.modem.ModemState
<<<<<<< HEAD
import dev.ririxi.bunnylock.watchdog.WatchdogState
=======
>>>>>>> 1f1c3fc2ddb001caec522b01498a9ab0a4e36bb1

val Group.favoritesKey: String
    get() =
        when (this) {
            Group.LTE -> "lte"
            Group.NR, Group.NR_SA, Group.NR_NSA -> "nr"
            Group.WCDMA -> "wcdma"
            Group.GSM -> "gsm"
        }

data class CellForm(
    val rat: LockRat = LockRat.LTE,
    val lteEarfcn: String = "",
    val ltePcis: String = "",
    val nrArfcn: String = "",
    val nrPcis: String = "",
    /** Subcarrier spacing in kHz. */
    val nrScs: Int = 30,
    val nrBand: Int? = null,
    /** The user chose [nrScs] by hand, so typing a channel no longer replaces it with the band's usual spacing. */
    val nrScsPicked: Boolean = false,
) {
    val nrCandidates: List<Int> get() = nrArfcn.toIntOrNull()?.let { Bands.nrBandsOf(it) } ?: emptyList()
}

data class UiState(
    val modem: ModemState = ModemState(),
    val busy: Boolean = false,
    val bandSelections: Map<Group, Set<Int>> = emptyMap(),
    val form: CellForm = CellForm(),
    val plmnMcc: String = "",
    val plmnMnc: String = "",
<<<<<<< HEAD
    val dynamicColor: Boolean = true,
=======
    val language: String = "en",
>>>>>>> 1f1c3fc2ddb001caec522b01498a9ab0a4e36bb1
    val checkUpdates: Boolean = true,
    val toolOutput: String = "",
    val update: Release? = null,
    val favoriteBands: Map<String, Set<Int>> = emptyMap(),
<<<<<<< HEAD
    val watchdog: WatchdogState = WatchdogState(),
=======
>>>>>>> 1f1c3fc2ddb001caec522b01498a9ab0a4e36bb1
) {
    fun favoritesOf(group: Group): Set<Int> = favoriteBands[group.favoritesKey].orEmpty()

    val lockRat get() = if (modem.supports5g) form.rat else LockRat.LTE

    fun selectedBands(groupState: BandGroupState): Set<Int> = bandSelections[groupState.group] ?: groupState.allowed
}
