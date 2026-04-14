package com.chibiclaw.memory.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.chibiclaw.memory.local.entity.AppPattern

@Dao
interface AppPatternDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(pattern: AppPattern)

    @Update
    suspend fun update(pattern: AppPattern)

    @Query("SELECT * FROM app_patterns WHERE packageName = :packageName")
    suspend fun getByPackage(packageName: String): AppPattern?

    @Query("UPDATE app_patterns SET usageCount = usageCount + 1, lastUsed = :now WHERE packageName = :packageName")
    suspend fun incrementUsage(packageName: String, now: Long = System.currentTimeMillis())
}
