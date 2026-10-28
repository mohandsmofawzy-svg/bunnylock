package dev.ririxi.bunnylock.modem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameModePolicyTest {
    @Test
    fun transientPoorSignalDoesNotEnterRecovery() {
        val policy = GameModePolicy()

        assertFalse(policy.observe(-112, -7).requestRecovery)
        assertFalse(policy.observe(-112, -7).requestRecovery)
        assertEquals(GameModeOperatingState.STABLE, policy.state)
        assertEquals(GameModeOperatingState.STABLE, policy.observe(-102, 4).state)
        assertFalse(policy.observe(-112, -7).requestRecovery)
    }

    @Test
    fun persistentPoorSignalEntersRecoveryAndRequestsOnlyOneAttempt() {
        val policy = GameModePolicy()

        repeat(2) { assertFalse(policy.observe(-112, -7).requestRecovery) }
        val recovery = policy.observe(-112, -7)
        assertEquals(GameModeOperatingState.RECOVERY, recovery.state)
        assertTrue(recovery.requestRecovery)
        repeat(5) { assertFalse(policy.observe(-112, -7).requestRecovery) }
    }

    @Test
    fun recoveryRequiresFourGoodSamplesToReturnToStable() {
        val policy = GameModePolicy()
        repeat(GameModePolicy.POOR_SIGNAL_SAMPLES_FOR_RECOVERY) { policy.observe(-112, -7) }

        repeat(GameModePolicy.STABLE_SIGNAL_SAMPLES_TO_EXIT_RECOVERY - 1) {
            assertEquals(GameModeOperatingState.RECOVERY, policy.observe(-100, 2).state)
        }
        val stable = policy.observe(-100, 2)

        assertEquals(GameModeOperatingState.STABLE, stable.state)
        assertTrue(stable.returnedToStable)
    }

    @Test
    fun missingSignalDoesNotCountAsRecovery() {
        val policy = GameModePolicy()
        repeat(GameModePolicy.POOR_SIGNAL_SAMPLES_FOR_RECOVERY) { policy.observe(-112, -7) }

        val observation = policy.observe(null, null)

        assertEquals(GameModeOperatingState.RECOVERY, observation.state)
        assertFalse(observation.returnedToStable)
        assertFalse(observation.requestRecovery)
    }

    @Test
    fun poorSignalDoesNotRequestRecoveryWhenActionsAreNotAllowed() {
        val policy = GameModePolicy()

        repeat(GameModePolicy.POOR_SIGNAL_SAMPLES_FOR_RECOVERY) {
            assertFalse(policy.observe(-112, -7, recoveryAllowed = false).requestRecovery)
        }
        assertEquals(GameModeOperatingState.RECOVERY, policy.state)
        assertTrue(policy.observe(-112, -7, recoveryAllowed = true).requestRecovery)
    }

    @Test
    fun recoveredEpisodeCanTriggerOneLaterAttempt() {
        val policy = GameModePolicy()
        repeat(GameModePolicy.POOR_SIGNAL_SAMPLES_FOR_RECOVERY) { policy.observe(-112, -7) }
        repeat(5) { assertFalse(policy.observe(-112, -7).requestRecovery) }
        repeat(GameModePolicy.STABLE_SIGNAL_SAMPLES_TO_EXIT_RECOVERY) { policy.observe(-100, 2) }

        repeat(GameModePolicy.POOR_SIGNAL_SAMPLES_FOR_RECOVERY - 1) {
            assertFalse(policy.observe(-112, -7).requestRecovery)
        }
        assertTrue(policy.observe(-112, -7).requestRecovery)
    }
}
