package dev.ririxi.bunnylock.modem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameModePolicyTest {
    @Test
    fun missingMeasurementsAreUnknownAndDoNotChangeOperatingState() {
        val policy = GameModePolicy()

        val observation = policy.observe(GameModeSignalSample(null, null, null), 10_000L)

        assertEquals(GameModeSignalQuality.UNKNOWN, observation.signalQuality)
        assertEquals(GameModeOperatingState.STABLE, observation.state)
        assertFalse(observation.requestRecovery)
        assertFalse(observation.returnedToStable)
    }

    @Test
    fun rsrpWithoutSinrCannotConfirmPoorOrHealthySignal() {
        val policy = GameModePolicy()

        val poorRsrp = policy.observe(GameModeSignalSample(-120, null, 10_000L), 10_000L)
        val goodRsrp = policy.observe(GameModeSignalSample(-90, null, 25_000L), 25_000L)

        assertEquals(GameModeSignalQuality.UNKNOWN, poorRsrp.signalQuality)
        assertEquals(GameModeSignalQuality.UNKNOWN, goodRsrp.signalQuality)
        assertEquals(GameModeOperatingState.STABLE, policy.state)
        assertFalse(GameModePolicy.isPoorSignal(-120, null))
        assertFalse(GameModePolicy.isPoorSignal(null, -7))
    }

    @Test
    fun poorSinrCountsEvenWhenRsrpIsAboveItsPoorThreshold() {
        val policy = GameModePolicy()

        assertTrue(GameModePolicy.isPoorSignal(-100, -7))
        repeat(GameModePolicy.POOR_SIGNAL_SAMPLES_FOR_RECOVERY - 1) { index ->
            val observation = policy.observe(GameModeSignalSample(-100, -7, 10_000L + index * 5_000L), 10_000L + index * 5_000L)
            assertEquals(GameModeSignalQuality.POOR, observation.signalQuality)
            assertFalse(observation.requestRecovery)
        }
        val finalTime = 10_000L + (GameModePolicy.POOR_SIGNAL_SAMPLES_FOR_RECOVERY - 1) * 5_000L
        val recovery = policy.observe(GameModeSignalSample(-100, -7, finalTime), finalTime)

        assertEquals(GameModeOperatingState.RECOVERY, recovery.state)
        assertTrue(recovery.requestRecovery)
        assertTrue(recovery.enteredRecovery)
    }

    @Test
    fun poorSinrWithoutRsrpIsUnknown() {
        val policy = GameModePolicy()

        val observation = policy.observe(GameModeSignalSample(null, -7, 10_000L), 10_000L)

        assertEquals(GameModeSignalQuality.UNKNOWN, observation.signalQuality)
        assertEquals(GameModeOperatingState.STABLE, policy.state)
        assertFalse(observation.requestRecovery)
    }

    @Test
    fun repeatedTimestampDoesNotCountAsAnotherPoorSample() {
        val policy = GameModePolicy()

        policy.observe(GameModeSignalSample(-112, -7, 10_000L), 10_000L)
        val duplicate = policy.observe(GameModeSignalSample(-112, -7, 10_000L), 15_000L)
        val next = policy.observe(GameModeSignalSample(-112, -7, 20_000L), 20_000L)

        assertEquals(GameModeSignalQuality.UNKNOWN, duplicate.signalQuality)
        assertTrue(duplicate.reason.orEmpty().contains("duplicate"))
        assertFalse(duplicate.requestRecovery)
        assertFalse(next.requestRecovery)
        assertEquals(GameModeOperatingState.STABLE, policy.state)
    }

    @Test
    fun tooFrequentMeasurementResetsConsecutivePoorSamples() {
        val policy = GameModePolicy()

        policy.observe(GameModeSignalSample(-112, -7, 10_000L), 10_000L)
        val tooSoon = policy.observe(GameModeSignalSample(-112, -7, 10_500L), 10_500L)
        val next = policy.observe(GameModeSignalSample(-112, -7, 15_000L), 15_000L)

        assertEquals(GameModeSignalQuality.UNKNOWN, tooSoon.signalQuality)
        assertTrue(tooSoon.reason.orEmpty().contains("too short"))
        assertFalse(next.requestRecovery)
        assertEquals(GameModeOperatingState.STABLE, policy.state)
    }

    @Test
    fun staleFutureAndInvalidMeasurementsAreUnknown() {
        val policy = GameModePolicy()
        val now = 100_000L

        val stale = policy.observe(GameModeSignalSample(-112, -7, 1_000L), now)
        val future = policy.observe(GameModeSignalSample(-112, -7, now + 1L), now)
        val invalid = policy.observe(GameModeSignalSample(-999, 100, now), now)

        assertEquals(GameModeSignalQuality.UNKNOWN, stale.signalQuality)
        assertTrue(stale.reason.orEmpty().contains("stale"))
        assertEquals(GameModeSignalQuality.UNKNOWN, future.signalQuality)
        assertEquals(GameModeSignalQuality.UNKNOWN, invalid.signalQuality)
        assertEquals(GameModeOperatingState.STABLE, policy.state)
    }

    @Test
    fun transientPoorSignalDoesNotEnterRecovery() {
        val policy = GameModePolicy()

        assertFalse(policy.observe(GameModeSignalSample(-112, -7, 10_000L), 10_000L).requestRecovery)
        assertFalse(policy.observe(GameModeSignalSample(-112, -7, 15_000L), 15_000L).requestRecovery)
        assertEquals(GameModeOperatingState.STABLE, policy.state)
        assertEquals(
            GameModeSignalQuality.UNKNOWN,
            policy.observe(GameModeSignalSample(-112, 2, 20_000L), 20_000L).signalQuality,
        )
        assertFalse(policy.observe(GameModeSignalSample(-112, -7, 25_000L), 25_000L).requestRecovery)
    }

    @Test
    fun unknownMeasurementsNeverCountAsHealthyRecoverySamples() {
        val policy = GameModePolicy()
        enterRecovery(policy)

        repeat(3) { index ->
            val now = 30_000L + index * 5_000L
            val unknown = policy.observe(GameModeSignalSample(null, null, null), now)
            assertEquals(GameModeOperatingState.RECOVERY, unknown.state)
            assertFalse(unknown.returnedToStable)
        }
        val firstHealthy = policy.observe(GameModeSignalSample(-100, 2, 50_000L), 50_000L)

        assertEquals(GameModeOperatingState.RECOVERY, firstHealthy.state)
        assertFalse(firstHealthy.returnedToStable)
    }

    @Test
    fun recoveryRequiresFourFreshHealthySamplesToReturnToStable() {
        val policy = GameModePolicy()
        enterRecovery(policy)

        repeat(GameModePolicy.STABLE_SIGNAL_SAMPLES_TO_EXIT_RECOVERY - 1) { index ->
            val now = 30_000L + index * 5_000L
            assertEquals(GameModeOperatingState.RECOVERY, policy.observe(GameModeSignalSample(-100, 2, now), now).state)
        }
        val finalTime = 30_000L + (GameModePolicy.STABLE_SIGNAL_SAMPLES_TO_EXIT_RECOVERY - 1) * 5_000L
        val stable = policy.observe(GameModeSignalSample(-100, 2, finalTime), finalTime)

        assertEquals(GameModeOperatingState.STABLE, stable.state)
        assertTrue(stable.returnedToStable)
    }

    @Test
    fun recoveryCanRequestOnceWhenActionsBecomeAllowed() {
        val policy = GameModePolicy()

        repeat(GameModePolicy.POOR_SIGNAL_SAMPLES_FOR_RECOVERY) { index ->
            val now = 10_000L + index * 5_000L
            val observation = policy.observe(GameModeSignalSample(-112, -7, now), now, recoveryAllowed = false)
            assertFalse(observation.requestRecovery)
        }
        assertEquals(GameModeOperatingState.RECOVERY, policy.state)

        val nextTime = 25_000L
        assertTrue(policy.observe(GameModeSignalSample(-112, -7, nextTime), nextTime, recoveryAllowed = true).requestRecovery)
        assertFalse(policy.observe(GameModeSignalSample(-112, -7, 30_000L), 30_000L).requestRecovery)
    }

    @Test
    fun poorSamplesSeparatedByTooLargeAGapMustBeConfirmedAgain() {
        val policy = GameModePolicy()

        policy.observe(GameModeSignalSample(-112, -7, 10_000L), 10_000L)
        policy.observe(GameModeSignalSample(-112, -7, 15_000L), 15_000L)
        val afterGap = policy.observe(GameModeSignalSample(-112, -7, 50_000L), 50_000L)

        assertFalse(afterGap.requestRecovery)
        assertEquals(GameModeOperatingState.STABLE, policy.state)
    }

    @Test
    fun recoveryCooldownIsBoundedAndTreatsClockRollbackConservatively() {
        assertTrue(GameModePolicy.isRecoveryCooldownActive(120_000L, 100_000L, 45_000L))
        assertFalse(GameModePolicy.isRecoveryCooldownActive(145_000L, 100_000L, 45_000L))
        assertTrue(GameModePolicy.isRecoveryCooldownActive(99_000L, 100_000L, 45_000L))
        assertFalse(GameModePolicy.isRecoveryCooldownActive(120_000L, 0L, 45_000L))
    }

    @Test
    fun resetClearsStateCountersAndDuplicateTracking() {
        val policy = GameModePolicy()
        enterRecovery(policy)

        policy.reset()
        val firstAfterReset = policy.observe(GameModeSignalSample(-112, -7, 10_000L), 10_000L)

        assertEquals(GameModeOperatingState.STABLE, firstAfterReset.state)
        assertFalse(firstAfterReset.requestRecovery)
        assertFalse(firstAfterReset.returnedToStable)
    }

    private fun enterRecovery(policy: GameModePolicy) {
        repeat(GameModePolicy.POOR_SIGNAL_SAMPLES_FOR_RECOVERY) { index ->
            val now = 10_000L + index * 5_000L
            policy.observe(GameModeSignalSample(-112, -7, now), now)
        }
        assertEquals(GameModeOperatingState.RECOVERY, policy.state)
    }
}
