package com.jacob.pokemonscanner.domain

import com.jacob.pokemonscanner.model.PokemonScan
import com.jacob.pokemonscanner.model.StopReason
import java.security.MessageDigest

object FingerprintBuilder {
    fun build(scan: PokemonScan): String {
        val iv = scan.ivs.value
        val canonical = listOf(
            scan.speciesName.value.orEmpty().trim().lowercase(),
            scan.nickname.value.orEmpty().trim().lowercase(),
            scan.form.value.orEmpty().trim().lowercase(),
            scan.cp.value?.toString().orEmpty(),
            iv?.attack?.toString().orEmpty(),
            iv?.defense?.toString().orEmpty(),
            iv?.stamina?.toString().orEmpty(),
            scan.possibleLevels.joinToString(","),
            scan.favorite.name,
            scan.screenshotHash.orEmpty(),
        ).joinToString("|")
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}

class FingerprintStopPolicy(
    private val samePokemonLimit: Int = 3,
    private val minimumSequenceLength: Int = 3,
    private val maximumHistory: Int = 100,
) {
    private val history = ArrayDeque<String>()

    fun record(fingerprint: String): StopReason? {
        history.addLast(fingerprint)
        while (history.size > maximumHistory) history.removeFirst()

        if (history.takeLast(samePokemonLimit).size == samePokemonLimit &&
            history.takeLast(samePokemonLimit).distinct().size == 1
        ) return StopReason.REPEATED_POKEMON

        val maxWindow = history.size / 2
        for (window in minimumSequenceLength..maxWindow) {
            val last = history.takeLast(window)
            val previous = history.dropLast(window).takeLast(window)
            if (last == previous) return StopReason.REPEATING_SEQUENCE
        }
        return null
    }

    fun clear() = history.clear()
}
