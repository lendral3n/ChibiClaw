package com.chibiclaw.memory.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.chibiclaw.memory.local.entity.CommandHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface CommandHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: CommandHistory): Long

    @Query("SELECT * FROM command_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 20): List<CommandHistory>

    @Query("SELECT * FROM command_history ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<CommandHistory>>

    @Query("DELETE FROM command_history WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}
