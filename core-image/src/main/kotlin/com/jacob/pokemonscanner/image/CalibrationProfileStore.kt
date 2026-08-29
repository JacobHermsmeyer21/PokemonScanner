package com.jacob.pokemonscanner.image

import com.jacob.pokemonscanner.model.AutomationAction
import com.jacob.pokemonscanner.model.GameScreen
import com.jacob.pokemonscanner.model.NormalizedRect
import java.io.File
import java.util.Properties

data class ImportedCalibrationProfile(
    val id: String,
    val widthPixels: Int,
    val heightPixels: Int,
    val densityDpi: Int,
    val language: String,
    val gameVersion: String,
    val targets: Map<Pair<GameScreen, AutomationAction>, NormalizedRect>,
)

/**
 * File-backed import point for device calibration. A profile directory contains profile.properties,
 * targets.properties, and optional PNG templates. Unknown or malformed targets are rejected.
 */
class CalibrationProfileStore(private val root: File) {
    fun listProfiles(): List<ImportedCalibrationProfile> = root.listFiles()
        .orEmpty()
        .filter(File::isDirectory)
        .mapNotNull(::load)

    fun load(directory: File): ImportedCalibrationProfile? = runCatching {
        val profile = Properties().apply {
            directory.resolve("profile.properties").inputStream().use(::load)
        }
        val targetsFile = directory.resolve("targets.properties")
        val targets = if (targetsFile.isFile) Properties().apply {
            targetsFile.inputStream().use(::load)
        }.entries.mapNotNull { (rawKey, rawValue) ->
            val parts = rawKey.toString().split('.')
            if (parts.size != 2) return@mapNotNull null
            val screen = runCatching { GameScreen.valueOf(parts[0]) }.getOrNull() ?: return@mapNotNull null
            val action = runCatching { AutomationAction.valueOf(parts[1]) }.getOrNull() ?: return@mapNotNull null
            val coordinates = rawValue.toString().split(',').mapNotNull(String::toFloatOrNull)
            if (coordinates.size != 4) return@mapNotNull null
            (screen to action) to NormalizedRect(coordinates[0], coordinates[1], coordinates[2], coordinates[3])
        }.toMap() else emptyMap()
        ImportedCalibrationProfile(
            id = directory.name,
            widthPixels = profile.getProperty("widthPixels").toInt(),
            heightPixels = profile.getProperty("heightPixels").toInt(),
            densityDpi = profile.getProperty("densityDpi").toInt(),
            language = profile.getProperty("language", "en"),
            gameVersion = profile.getProperty("gameVersion", "unknown"),
            targets = targets,
        )
    }.getOrNull()
}
