package com.jacob.pokemonscanner.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [PokemonRecordEntity::class, ScanSessionEntity::class, SpeciesDataEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(DatabaseConverters::class)
abstract class ScannerDatabase : RoomDatabase() {
    abstract fun pokemonRecordDao(): PokemonRecordDao
    abstract fun scanSessionDao(): ScanSessionDao
    abstract fun speciesDataDao(): SpeciesDataDao
}
