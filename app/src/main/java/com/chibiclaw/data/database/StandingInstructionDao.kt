package com.chibiclaw.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

@Dao
interface StandingInstructionDao {

    @Query("SELECT * FROM standing_instruction WHERE id = :id LIMIT 1")
    suspend fun get(id: String): StandingInstructionEntity?

    @Query("SELECT * FROM standing_instruction WHERE enabled = 1 ORDER BY priority DESC, created_at ASC")
    suspend fun listEnabled(): List<StandingInstructionEntity>

    @Query("SELECT * FROM standing_instruction ORDER BY created_at DESC")
    fun observeAll(): Flow<List<StandingInstructionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: StandingInstructionEntity)

    @Update
    suspend fun update(entity: StandingInstructionEntity)

    @Delete
    suspend fun delete(entity: StandingInstructionEntity)

    @Query("UPDATE standing_instruction SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("""
        UPDATE standing_instruction
        SET fires_today = fires_today + 1,
            total_fires = total_fires + 1,
            last_fired_at = :firedAt
        WHERE id = :id
    """)
    suspend fun recordFire(id: String, firedAt: Instant)

    @Query("UPDATE standing_instruction SET fires_today = 0, fires_today_reset_at = :resetAt WHERE id = :id")
    suspend fun resetDailyCounter(id: String, resetAt: Instant)
}
