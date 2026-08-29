package com.jacob.pokemonscanner.domain

import com.jacob.pokemonscanner.model.CleanupItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CleanupPolicyTest {
    @Test fun protectedItemsCanNeverBeDiscardedEvenIfSelected() {
        val protected = setOf(
            CleanupItem.MAX_POTION,
            CleanupItem.MAX_REVIVE,
            CleanupItem.GOLDEN_RAZZ_BERRY,
            CleanupItem.SILVER_PINAP_BERRY,
            CleanupItem.OTHER,
        )
        protected.forEach { item ->
            val result = CleanupPolicy.validate(item, setOf(item), 12, 12)
            assertTrue("$item must be protected", result is CleanupValidation.Rejected)
        }
    }

    @Test fun razzAndGoldenRazzUseExactDistinctNames() {
        assertEquals(CleanupItem.RAZZ_BERRY, CleanupItem.fromExactRecognizedName("Razz Berry"))
        assertEquals(CleanupItem.GOLDEN_RAZZ_BERRY, CleanupItem.fromExactRecognizedName("Golden Razz Berry"))
        assertTrue(CleanupItem.fromExactRecognizedName("12 Razz Berry") == null)
    }

    @Test fun onlyFullVerifiedQuantityIsAllowed() {
        val selected = setOf(CleanupItem.POTION)
        assertEquals(CleanupValidation.Allowed(27), CleanupPolicy.validate(CleanupItem.POTION, selected, 27, 27))
        assertTrue(CleanupPolicy.validate(CleanupItem.POTION, selected, 1, 27) is CleanupValidation.Rejected)
        assertTrue(CleanupPolicy.validate(CleanupItem.POTION, emptySet(), 27, 27) is CleanupValidation.Rejected)
    }

    @Test fun requestedDefaultsAreSelectedAndProtectedItemsAreNot() {
        assertEquals(
            setOf(
                CleanupItem.POTION,
                CleanupItem.SUPER_POTION,
                CleanupItem.HYPER_POTION,
                CleanupItem.REVIVE,
                CleanupItem.RAZZ_BERRY,
                CleanupItem.NANAB_BERRY,
            ),
            CleanupItem.defaultSelection,
        )
        assertTrue(CleanupItem.defaultSelection.none { !it.mayEverBeDiscarded })
    }
}
