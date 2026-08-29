package com.jacob.pokemonscanner.database

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): ScannerDatabase =
        Room.databaseBuilder(context, ScannerDatabase::class.java, "pokemon-scanner.db")
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()

    @Provides fun pokemonDao(db: ScannerDatabase): PokemonRecordDao = db.pokemonRecordDao()
    @Provides fun sessionDao(db: ScannerDatabase): ScanSessionDao = db.scanSessionDao()
    @Provides fun speciesDao(db: ScannerDatabase): SpeciesDataDao = db.speciesDataDao()
}
