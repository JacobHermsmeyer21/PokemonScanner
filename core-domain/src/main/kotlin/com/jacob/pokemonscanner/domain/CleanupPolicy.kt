package com.jacob.pokemonscanner.domain

import com.jacob.pokemonscanner.model.CleanupItem

sealed interface CleanupValidation {
    data class Allowed(val quantity: Int) : CleanupValidation
    data class Rejected(val reason: String) : CleanupValidation
}

object CleanupPolicy {
    fun validate(
        item: CleanupItem?,
        selectedItems: Set<CleanupItem>,
        selectedQuantity: Int?,
        availableQuantity: Int?,
    ): CleanupValidation {
        if (item == null) return CleanupValidation.Rejected("Item name was not verified")
        if (!item.mayEverBeDiscarded) return CleanupValidation.Rejected("${item.displayName} is protected")
        if (item !in selectedItems) return CleanupValidation.Rejected("${item.displayName} is not selected for cleanup")
        if (selectedQuantity == null || availableQuantity == null) {
            return CleanupValidation.Rejected("The selected and available quantities were not both verified")
        }
        if (selectedQuantity <= 0 || selectedQuantity != availableQuantity) {
            return CleanupValidation.Rejected("Discard confirmation is not set to the full verified quantity")
        }
        return CleanupValidation.Allowed(selectedQuantity)
    }
}
