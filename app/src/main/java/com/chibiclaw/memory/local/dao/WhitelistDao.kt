package com.chibiclaw.memory.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.chibiclaw.memory.local.entity.AppWhitelist
import kotlinx.coroutines.flow.Flow

@Dao
interface WhitelistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: AppWhitelist)

    @Query("SELECT * FROM app_whitelist WHERE packageName = :packageName")
    suspend fun getByPackage(packageName: String): AppWhitelist?

    @Query("SELECT * FROM app_whitelist ORDER BY packageName ASC")
    fun observeAll(): Flow<List<AppWhitelist>>

    @Query("DELETE FROM app_whitelist WHERE packageName = :packageName")
    suspend fun delete(packageName: String)
}
