package dev.ririxi.bunnylock.modem

enum class GameModeOperatingState {
    STABLE,
    RECOVERY,
}

enum class GameModeSignalQuality {
    POOR,
    HEALTHY,
    UNKNOWN,
}

data class GameModeSignalSample(
    val rsrpDbm: Int?,
    val sinrDb: Int?,
    val timestampElapsedRealtimeMillis: Long?,
)

data class GameModeObservation(
    val state: GameModeOperatingState,
    val requestRecovery: Boolean,
    val returnedToStable: Boolean,
    val signalQuality: GameModeSignalQuality = GameModeSignalQuality.UNKNOWN,
    val reason: String? = null,
    val enteredRecovery: Boolean = false,
)

/** Applies signal hysteresis only to recent, distinct Android radio measurements. */
class GameModePolicy(
    recoveryAttempted: Boolean = false,
) {
    private var poorSamples = 0
    private var stableSamples = 0
    private var recoveryAttempted = recoveryAttempted
    private var lastSampleTimestampMillis: Long? = null

    var state = GameModeOperatingState.STABLE
        private set

    fun observe(
        sample: GameModeSignalSample,
        nowElapsedRealtimeMillis: Long,
        recoveryAllowed: Boolean = true,
    ): GameModeObservation {
        val timestamp = sample.timestampElapsedRealtimeMillis
        if (timestamp == null) return unknown("measurement timestamp unavailable")
        if (timestamp <= 0L || timestamp > nowElapsedRealtimeMillis) return unknown("measurement timestamp invalid")
        val age = nowElapsedRealtimeMillis - timestamp
        if (age > MAX_SIGNAL_AGE_MILLIS) return unknown("measurement is stale")
        val previousTimestamp = lastSampleTimestampMillis
        if (previousTimestamp != null && timestamp <= previousTimestamp) return unknown("measurement is duplicate or out of order")

        val sampleInterval = previousTimestamp?.let { timestamp - it }
        if (sampleInterval != null && sampleInterval > MAX_SAMPLE_GAP_MILLIS) {
            resetSampleCounters()
        }
        lastSampleTimestampMillis = timestamp
        if (sampleInterval != null && sampleInterval < MIN_SAMPLE_INTERVAL_MILLIS) {
            resetSampleCounters()
            return unknown("measurement interval is too short")
        }

        val rsrpDbm = sample.rsrpDbm?.takeIf { it in VALID_RSRP_RANGE }
        val sinrDb = sample.sinrDb?.takeIf { it in VALID_SINR_RANGE }
        val quality = classifySignal(rsrpDbm, sinrDb)
        if (quality == GameModeSignalQuality.UNKNOWN) {
            resetSampleCounters()
            return GameModeObservation(state, requestRecovery = false, returnedToStable = false, signalQuality = quality, reason = "measurements are incomplete, invalid, or inconclusive")
        }

        if (quality == GameModeSignalQuality.POOR) {
            stableSamples = 0
            poorSamples = (poorSamples + 1).coerceAtMost(POOR_SIGNAL_SAMPLES_FOR_RECOVERY)
            if (poorSamples < POOR_SIGNAL_SAMPLES_FOR_RECOVERY) {
                return GameModeObservation(state, requestRecovery = false, returnedToStable = false, signalQuality = quality)
            }

            val enteredRecovery = state != GameModeOperatingState.RECOVERY
            state = GameModeOperatingState.RECOVERY
            val requestRecovery = recoveryAllowed && !recoveryAttempted
            if (requestRecovery) recoveryAttempted = true
            return GameModeObservation(state, requestRecovery, returnedToStable = false, signalQuality = quality, enteredRecovery = enteredRecovery)
        }

        poorSamples = 0
        if (state != GameModeOperatingState.RECOVERY) {
            stableSamples = 0
            return GameModeObservation(state, requestRecovery = false, returnedToStable = false, signalQuality = quality)
        }
        stableSamples = (stableSamples + 1).coerceAtMost(STABLE_SIGNAL_SAMPLES_TO_EXIT_RECOVERY)
        if (stableSamples < STABLE_SIGNAL_SAMPLES_TO_EXIT_RECOVERY) {
            return GameModeObservation(state, requestRecovery = false, returnedToStable = false, signalQuality = quality)
        }

        state = GameModeOperatingState.STABLE
        stableSamples = 0
        recoveryAttempted = false
        return GameModeObservation(state, requestRecovery = false, returnedToStable = true, signalQuality = quality)
    }

    /** Legacy calls without a measurement timestamp are deliberately inconclusive. */
    fun observe(
        rsrpDbm: Int?,
        sinrDb: Int?,
        recoveryAllowed: Boolean = true,
    ): GameModeObservation = observe(GameModeSignalSample(rsrpDbm, sinrDb, null), 0L, recoveryAllowed)

    fun reset() {
        resetSampleCounters()
        lastSampleTimestampMillis = null
        recoveryAttempted = false
        state = GameModeOperatingState.STABLE
    }

    private fun unknown(reason: String): GameModeObservation {
        resetSampleCounters()
        return GameModeObservation(state, requestRecovery = false, returnedToStable = false, reason = reason)
    }

    private fun resetSampleCounters() {
        poorSamples = 0
        stableSamples = 0
    }

    companion object {
        const val POOR_SIGNAL_RSRP_DBM = -110
        const val POOR_SIGNAL_SINR_DB = -5
        const val POOR_SIGNAL_SAMPLES_FOR_RECOVERY = 3
        const val STABLE_SIGNAL_SAMPLES_TO_EXIT_RECOVERY = 4
        const val STABLE_MODE_CHECK_INTERVAL_MILLIS = 15_000L
        const val RECOVERY_MODE_CHECK_INTERVAL_MILLIS = 5_000L
        const val MAX_SIGNAL_AGE_MILLIS = STABLE_MODE_CHECK_INTERVAL_MILLIS * 2
        private const val MAX_SAMPLE_GAP_MILLIS = MAX_SIGNAL_AGE_MILLIS
        private const val MIN_SAMPLE_INTERVAL_MILLIS = 1_000L
        private val VALID_RSRP_RANGE = -160..0
        private val VALID_SINR_RANGE = -30..50

        fun isRecoveryCooldownActive(
            nowMillis: Long,
            lastAttemptMillis: Long,
            cooldownMillis: Long,
        ): Boolean =
            lastAttemptMillis > 0L &&
                (nowMillis < lastAttemptMillis || nowMillis - lastAttemptMillis < cooldownMillis)

        fun isPoorSignal(
            rsrpDbm: Int?,
            sinrDb: Int?,
        ): Boolean {
            val rsrp = rsrpDbm?.takeIf { it in VALID_RSRP_RANGE }
            val sinr = sinrDb?.takeIf { it in VALID_SINR_RANGE }
            return classifySignal(rsrp, sinr) == GameModeSignalQuality.POOR
        }

        private fun classifySignal(
            rsrpDbm: Int?,
            sinrDb: Int?,
        ): GameModeSignalQuality =
            when {
                rsrpDbm == null || sinrDb == null -> GameModeSignalQuality.UNKNOWN
                sinrDb <= POOR_SIGNAL_SINR_DB -> GameModeSignalQuality.POOR
                rsrpDbm <= POOR_SIGNAL_RSRP_DBM -> GameModeSignalQuality.UNKNOWN
                else -> GameModeSignalQuality.HEALTHY
            }

    }
}
