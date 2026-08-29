package com.jacob.pokemonscanner.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "pokemon_records",
    indices = [
        Index("speciesName"), Index("nickname"), Index("cp"), Index("ivPercentage"),
        Index("scanSessionId"), Index("recordFingerprint"), Index("scannedAt"),
    ],
)
data class PokemonRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val speciesName: String,
    val rawOcrName: String?,
    val nickname: String?,
    val form: String?,
    val cp: Int,
    val level: Double?,
    val possibleLevels: List<Double>,
    val attackIv: Int,
    val defenseIv: Int,
    val staminaIv: Int,
    val totalIv: Int,
    val ivPercentage: Double,
    val currentHp: Int?,
    val isShiny: Boolean?,
    val isFavorite: Boolean?,
    val scannedAt: Instant,
    val scanSessionId: Long?,
    val detailScreenshotPath: String?,
    val appraisalScreenshotPath: String?,
    val screenshotHash: String?,
    val recordFingerprint: String,
    val nameConfidence: Float,
    val cpConfidence: Float,
    val ivConfidence: Float,
    val levelConfidence: Float,
    val manuallyEdited: Boolean = false,
    val verified: Boolean = false,
    val notes: String = "",
    val detectorVersion: String = "1",
    val speciesDataVersion: String? = null,
)

@Entity(tableName = "scan_sessions")
data class ScanSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Instant,
    val endedAt: Instant? = null,
    val mode: String,
    val totalScanned: Int = 0,
    val duplicatesSkipped: Int = 0,
    val failures: Int = 0,
    val stopReason: String? = null,
    val deviceProfile: String,
    val appVersion: String,
)

@Entity(
    tableName = "species_data",
    primaryKeys = ["speciesName", "form", "version"],
    indices = [Index("pokedexNumber")],
)
data class SpeciesDataEntity(
    val speciesName: String,
    val form: String,
    val pokedexNumber: Int,
    val baseAttack: Int,
    val baseDefense: Int,
    val baseStamina: Int,
    val aliases: List<String>,
    val version: String,
)
