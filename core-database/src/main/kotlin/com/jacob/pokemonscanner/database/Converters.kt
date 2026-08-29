package com.jacob.pokemonscanner.database

import androidx.room.TypeConverter
import java.time.Instant

class DatabaseConverters {
    @TypeConverter fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()
    @TypeConverter fun longToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)
    @TypeConverter fun doublesToString(value: List<Double>): String = value.joinToString(",")
    @TypeConverter fun stringToDoubles(value: String): List<Double> =
        value.takeIf(String::isNotBlank)?.split(',')?.mapNotNull(String::toDoubleOrNull).orEmpty()
    @TypeConverter fun stringsToString(value: List<String>): String = value.joinToString("\u001F")
    @TypeConverter fun stringToStrings(value: String): List<String> =
        value.takeIf(String::isNotBlank)?.split('\u001F').orEmpty()
}
