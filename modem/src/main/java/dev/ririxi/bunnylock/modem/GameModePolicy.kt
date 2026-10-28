package dev.ririxi.bunnylock.modem

enum class GameModeOperatingState {
    STABLE,
    RECOVERY,
}

data class GameModeObservation(
    val state: GameModeOperatingState,
    val requestRecovery: Boolean,
    val returnedToStable: Boolean,
)

/** Applies signal hysteresis without treating missing measurements as evidence of recovery. */
class GameModePolicy(
    recoveryAttempted: Boolean = false,
) {
    private var poorSamples = 0
    private var stableSamples = 0
    private var recoveryAttempted = recoveryAttempted

    var state = GameModeOperatingState.STABLE
        private set

    fun observe(
        rsrpDbm: Int?,
        sinrDb: Int?,
        recoveryAllowed: Boolean = true,
    ): GameModeObservation {
        if (rsrpDbm == null && sinrDb == null) {
            poorSamples = 0
            stableSamples = 0
            return GameModeObservation(state, requestRecovery = false, returnedToStable = false)
        }

        if (isPoorSignal(rsrpDbm, sinrDb)) {
            poorSamples = (poorSamples + 1).coerceAtMost(POOR_SIGNAL_SAMPLES_FOR_RECOVERY)
            stableSamples = 0
            if (poorSamples < POOR_SIGNAL_SAMPLES_FOR_RECOVERY) {
                return GameModeObservation(state, requestRecovery = false, returnedToStable = false)
            }

            state = GameModeOperatingState.RECOVERY
            val requestRecovery = recoveryAllowed && !recoveryAttempted
            if (requestRecovery) recoveryAttempted = true
            return GameModeObservation(state, requestRecovery, returnedToStable = false)
        }

        poorSamples = 0
        if (state != GameModeOperatingState.RECOVERY) {
            stableSamples = 0
            return GameModeObservation(state, requestRecovery = false, returnedToStable = false)
        }

        stableSamples = (stableSamples + 1).coerceAtMost(STABLE_SIGNAL_SAMPLES_TO_EXIT_RECOVERY)
        if (stableSamples < STABLE_SIGNAL_SAMPLES_TO_EXIT_RECOVERY) {
            return GameModeObservation(state, requestRecovery = false, returnedToStable = false)
        }

        state = GameModeOperatingState.STABLE
        stableSamples = 0
        recoveryAttempted = false
        return GameModeObservation(state, requestRecovery = false, returnedToStable = true)
    }

    fun reset() {
        poorSamples = 0
        stableSamples = 0
        recoveryAttempted = false
        state = GameModeOperatingState.STABLE
    }

    companion object {
        const val POOR_SIGNAL_RSRP_DBM = -110
        const val POOR_SIGNAL_SINR_DB = -5
        const val POOR_SIGNAL_SAMPLES_FOR_RECOVERY = 3
        const val STABLE_SIGNAL_SAMPLES_TO_EXIT_RECOVERY = 4
        const val STABLE_MODE_CHECK_INTERVAL_MILLIS = 15_000L
        const val RECOVERY_MODE_CHECK_INTERVAL_MILLIS = 5_000L

        fun isPoorSignal(
            rsrpDbm: Int?,
            sinrDb: Int?,
        ): Boolean = rsrpDbm != null && rsrpDbm <= POOR_SIGNAL_RSRP_DBM && (sinrDb == null || sinrDb <= POOR_SIGNAL_SINR_DB)
    }
}
