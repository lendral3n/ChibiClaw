package com.chibiclaw.data.database.converters

import androidx.room.TypeConverter
import kotlinx.datetime.Instant

/**
 * Room type converter untuk kotlinx-datetime Instant ↔ Long (epoch millis).
 */
class InstantConverter {
    @TypeConverter
    fun fromInstant(instant: Instant?): Long? = instant?.toEpochMilliseconds()

    @TypeConverter
    fun toInstant(epochMillis: Long?): Instant? = epochMillis?.let { Instant.fromEpochMilliseconds(it) }
}
