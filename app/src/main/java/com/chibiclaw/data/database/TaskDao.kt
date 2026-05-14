package com.chibiclaw.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

@Dao
interface TaskDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity)

    @Update
    suspend fun update(task: TaskEntity)

    @Query("UPDATE task SET status = :status, started_at = :startedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: TaskStatus, startedAt: Instant? = null)

    @Query("UPDATE task SET status = :status, completed_at = :completedAt, result_summary = :summary, emotion_tag = :emotion WHERE id = :id")
    suspend fun markCompleted(id: String, status: TaskStatus, completedAt: Instant, summary: String?, emotion: String?)

    @Query("UPDATE task SET status = :status, completed_at = :completedAt, error_message = :error WHERE id = :id")
    suspend fun markFailed(id: String, status: TaskStatus, completedAt: Instant, error: String?)

    @Query("UPDATE task SET iteration_count = :iter WHERE id = :id")
    suspend fun setIterationCount(id: String, iter: Int)

    @Query("SELECT * FROM task WHERE id = :id")
    suspend fun get(id: String): TaskEntity?

    @Query("SELECT * FROM task WHERE id = :id")
    fun observe(id: String): Flow<TaskEntity?>

    @Query("SELECT * FROM task WHERE status IN (:statuses) ORDER BY priority DESC, created_at ASC LIMIT :limit")
    suspend fun listByStatus(statuses: List<TaskStatus>, limit: Int = 100): List<TaskEntity>

    @Query("SELECT * FROM task WHERE channel = :channel AND status = :status ORDER BY created_at DESC LIMIT :limit")
    suspend fun listByChannelStatus(channel: TaskChannel, status: TaskStatus, limit: Int = 50): List<TaskEntity>

    @Query("SELECT * FROM task ORDER BY created_at DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<TaskEntity>>

    @Query("SELECT * FROM task ORDER BY created_at DESC LIMIT :limit")
    suspend fun recentSnapshot(limit: Int = 200): List<TaskEntity>

    @Query("SELECT * FROM task WHERE status = 'PENDING' ORDER BY priority DESC, created_at ASC LIMIT :limit")
    suspend fun runnable(limit: Int = 5): List<TaskEntity>

    @Query("SELECT * FROM task WHERE status IN ('PLANNING', 'RUNNING', 'BLOCKED') LIMIT :limit")
    suspend fun listIncomplete(limit: Int = 50): List<TaskEntity>

    @Query("DELETE FROM task WHERE status IN ('COMPLETED', 'FAILED', 'CANCELLED') AND ttl_until IS NOT NULL AND ttl_until < :now")
    suspend fun deleteExpired(now: Instant): Int
}

@Dao
interface AgentStepDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(step: AgentStepEntity)

    @Query("SELECT * FROM agent_step WHERE task_id = :taskId ORDER BY step_index ASC")
    suspend fun listByTask(taskId: String): List<AgentStepEntity>

    @Query("SELECT * FROM agent_step WHERE task_id = :taskId ORDER BY step_index ASC")
    fun observeByTask(taskId: String): Flow<List<AgentStepEntity>>

    @Query("SELECT COUNT(*) FROM agent_step WHERE task_id = :taskId")
    suspend fun countByTask(taskId: String): Int
}

@Dao
interface MemoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: MemoryRecordEntity)

    @Query("SELECT * FROM memory_record WHERE id = :id")
    suspend fun get(id: String): MemoryRecordEntity?

    @Query("SELECT * FROM memory_record WHERE key = :key LIMIT 1")
    suspend fun getByKey(key: String): MemoryRecordEntity?

    @Query("SELECT * FROM memory_record WHERE category = :category ORDER BY last_accessed_at DESC LIMIT :limit")
    suspend fun listByCategory(category: MemoryCategory, limit: Int = 100): List<MemoryRecordEntity>

    @Query("SELECT * FROM memory_record ORDER BY last_accessed_at DESC LIMIT :limit")
    suspend fun listAll(limit: Int = 1000): List<MemoryRecordEntity>

    @Query("SELECT COUNT(*) FROM memory_record")
    suspend fun count(): Int

    @Query("UPDATE memory_record SET last_accessed_at = :now, access_count = :count WHERE id = :id")
    suspend fun touch(id: String, now: Instant, count: Int)

    @Query("UPDATE memory_record SET confidence = :confidence WHERE id = :id")
    suspend fun updateConfidence(id: String, confidence: Float)

    @Query("DELETE FROM memory_record WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM memory_record WHERE key = :key")
    suspend fun deleteByKey(key: String)

    @Query("DELETE FROM memory_record WHERE ttl_until IS NOT NULL AND ttl_until < :now")
    suspend fun deleteExpired(now: Instant): Int

    @Query("SELECT * FROM memory_record ORDER BY last_accessed_at ASC LIMIT :limit")
    suspend fun listOldestAccessed(limit: Int): List<MemoryRecordEntity>

    @Query("SELECT * FROM memory_record WHERE last_accessed_at < :threshold")
    suspend fun listStaleSince(threshold: Instant): List<MemoryRecordEntity>

    @Query("DELETE FROM memory_record WHERE confidence < :minConfidence AND last_accessed_at < :threshold")
    suspend fun deleteLowConfidenceStale(minConfidence: Float, threshold: Instant): Int

    @Query("SELECT category, COUNT(*) as cnt FROM memory_record GROUP BY category")
    suspend fun countByCategory(): List<MemoryCategoryCount>
}

data class MemoryCategoryCount(
    val category: MemoryCategory,
    val cnt: Int,
)
