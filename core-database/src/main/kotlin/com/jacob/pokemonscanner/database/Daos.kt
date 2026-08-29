package com.jacob.pokemonscanner.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PokemonRecordDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: PokemonRecordEntity): Long

    @Update suspend fun update(record: PokemonRecordEntity)
    @Delete suspend fun delete(record: PokemonRecordEntity)

    @Query("DELETE FROM pokemon_records WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT * FROM pokemon_records ORDER BY scannedAt DESC")
    fun observeAll(): Flow<List<PokemonRecordEntity>>

    @Query("SELECT * FROM pokemon_records WHERE id = :id")
    fun observeById(id: Long): Flow<PokemonRecordEntity?>

    @Query("""
        SELECT * FROM pokemon_records
        WHERE speciesName LIKE '%' || :query || '%' OR nickname LIKE '%' || :query || '%'
        ORDER BY scannedAt DESC
    """)
    fun search(query: String): Flow<List<PokemonRecordEntity>>

    @Query("""
        SELECT * FROM pokemon_records
        WHERE ivPercentage >= :minimumIv
          AND cp BETWEEN :minimumCp AND :maximumCp
          AND (:shinyOnly = 0 OR isShiny = 1)
          AND (:favoriteOnly = 0 OR isFavorite = 1)
        ORDER BY ivPercentage DESC, cp DESC
    """)
    fun filter(
        minimumIv: Double,
        minimumCp: Int,
        maximumCp: Int,
        shinyOnly: Boolean,
        favoriteOnly: Boolean,
    ): Flow<List<PokemonRecordEntity>>

    @Query("SELECT * FROM pokemon_records WHERE scanSessionId = :sessionId ORDER BY scannedAt")
    fun bySession(sessionId: Long): Flow<List<PokemonRecordEntity>>

    @Query("SELECT * FROM pokemon_records WHERE recordFingerprint = :fingerprint ORDER BY scannedAt DESC")
    suspend fun likelyDuplicates(fingerprint: String): List<PokemonRecordEntity>
}

@Dao
interface ScanSessionDao {
    @Insert suspend fun insert(session: ScanSessionEntity): Long
    @Update suspend fun update(session: ScanSessionEntity)
    @Query("SELECT * FROM scan_sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<ScanSessionEntity>>
}

@Dao
interface SpeciesDataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<SpeciesDataEntity>)

    @Query("SELECT * FROM species_data WHERE lower(speciesName) = lower(:name) ORDER BY version DESC")
    suspend fun byName(name: String): List<SpeciesDataEntity>

    @Query("SELECT * FROM species_data ORDER BY pokedexNumber, form")
    suspend fun all(): List<SpeciesDataEntity>
}
