package com.jacob.pokemonscanner.domain

import kotlin.math.floor
import kotlin.math.sqrt

data class SpeciesStats(val attack: Int, val defense: Int, val stamina: Int)
data class CpMultiplier(val level: Double, val multiplier: Double)

data class LevelMatch(
    val levels: List<Double>,
    val confidence: Float,
    val validationError: String? = null,
)

class LevelCalculator(private val multipliers: List<CpMultiplier>) {
    fun findLevels(
        stats: SpeciesStats,
        cp: Int,
        attackIv: Int,
        defenseIv: Int,
        staminaIv: Int,
        currentHp: Int? = null,
    ): LevelMatch {
        require(cp >= 10)
        require(attackIv in 0..15 && defenseIv in 0..15 && staminaIv in 0..15)

        val levels = multipliers.filter { row ->
            calculateCp(stats, attackIv, defenseIv, staminaIv, row.multiplier) == cp &&
                (currentHp == null || calculateHp(stats, staminaIv, row.multiplier) == currentHp)
        }.map { it.level }

        return when (levels.size) {
            0 -> LevelMatch(emptyList(), 0f, "No level matches CP, IVs, species stats, and HP")
            1 -> LevelMatch(levels, 1f)
            else -> LevelMatch(levels, 1f / levels.size)
        }
    }

    fun calculateCp(stats: SpeciesStats, attackIv: Int, defenseIv: Int, staminaIv: Int, cpm: Double): Int {
        val raw = (stats.attack + attackIv) *
            sqrt((stats.defense + defenseIv).toDouble()) *
            sqrt((stats.stamina + staminaIv).toDouble()) * cpm * cpm / 10.0
        return maxOf(10, floor(raw).toInt())
    }

    fun calculateHp(stats: SpeciesStats, staminaIv: Int, cpm: Double): Int =
        maxOf(10, floor((stats.stamina + staminaIv) * cpm).toInt())
}
