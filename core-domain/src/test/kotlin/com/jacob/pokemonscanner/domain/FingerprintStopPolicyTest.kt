package com.jacob.pokemonscanner.domain

import com.jacob.pokemonscanner.model.StopReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FingerprintStopPolicyTest {
    @Test fun stopsAfterSamePokemonThreeTimes() {
        val policy = FingerprintStopPolicy()
        assertNull(policy.record("a"))
        assertNull(policy.record("a"))
        assertEquals(StopReason.REPEATED_POKEMON, policy.record("a"))
    }

    @Test fun stopsOnRepeatingSequence() {
        val policy = FingerprintStopPolicy()
        listOf("a", "b", "c", "a", "b").forEach { assertNull(policy.record(it)) }
        assertEquals(StopReason.REPEATING_SEQUENCE, policy.record("c"))
    }
}
