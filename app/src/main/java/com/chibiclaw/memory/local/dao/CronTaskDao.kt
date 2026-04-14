package com.chibiclaw.memory.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.chibiclaw.memory.local.entity.CronTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CronTaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: CronTaskEntity)

    @Update
    suspend fun update(task: CronTaskEntity)

    @Delete
    suspend fun delete(task: CronTaskEntity)

    @Query("SELECT * FROM cron_tasks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<CronTaskEntity>>

    @Query("SELECT * FROM cron_tasks")
    suspend fun getAll(): List<CronTaskEntity>

    @Query("SELECT * FROM cron_tasks WHERE id = :id")
    suspend fun getById(id: String): CronTaskEntity?

    @Query("UPDATE cron_tasks SET lastRun = :now WHERE id = :id")
    suspend fun markRun(id: String, now: Long = System.currentTimeMillis())
}
