package com.chibiclaw.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

@Dao
interface ModelConfigDao {

    @Query("SELECT * FROM model_config WHERE adapterId = :adapterId LIMIT 1")
    suspend fun get(adapterId: String): ModelConfigEntity?

    @Query("SELECT * FROM model_config")
    suspend fun listAll(): List<ModelConfigEntity>

    @Query("SELECT * FROM model_config")
    fun observeAll(): Flow<List<ModelConfigEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ModelConfigEntity)

    @Update
    suspend fun update(entity: ModelConfigEntity)

    @Query("UPDATE model_config SET usedToday = usedToday + 1, totalCalls = totalCalls + 1, lastUsedAt = :ts WHERE adapterId = :adapterId")
    suspend fun incrementUsage(adapterId: String, ts: Instant)

    @Query("UPDATE model_config SET totalErrors = totalErrors + 1 WHERE adapterId = :adapterId")
    suspend fun incrementError(adapterId: String)

    @Query("UPDATE model_config SET usedToday = 0, lastResetAt = :ts, exhaustedAt = NULL WHERE adapterId = :adapterId")
    suspend fun resetDaily(adapterId: String, ts: Instant)

    @Query("UPDATE model_config SET exhaustedAt = :ts WHERE adapterId = :adapterId")
    suspend fun markExhausted(adapterId: String, ts: Instant)

    @Query("UPDATE model_config SET sessionJson = :json, sessionExpiresAt = :expiresAt, enabled = 1 WHERE adapterId = :adapterId")
    suspend fun updateSession(adapterId: String, json: String, expiresAt: Instant?)

    @Query("UPDATE model_config SET enabled = :enabled WHERE adapterId = :adapterId")
    suspend fun setEnabled(adapterId: String, enabled: Boolean)
}
