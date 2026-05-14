package com.chibiclaw.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.datetime.Instant

@Dao
interface TaskDependencyDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(edge: TaskDependencyEntity)

    @Query("SELECT * FROM task_dependency WHERE task_id = :taskId")
    suspend fun listDependenciesOf(taskId: String): List<TaskDependencyEntity>

    @Query("SELECT * FROM task_dependency WHERE depends_on_task_id = :taskId")
    suspend fun listDependents(taskId: String): List<TaskDependencyEntity>

    @Query("""
        UPDATE task_dependency
        SET status = :status, resolved_at = :ts
        WHERE depends_on_task_id = :resolvedTaskId AND status = 'PENDING'
    """)
    suspend fun resolveDependentsOf(resolvedTaskId: String, status: DependencyStatus, ts: Instant)

    @Query("""
        SELECT COUNT(*) FROM task_dependency
        WHERE task_id = :taskId AND status = 'PENDING'
    """)
    suspend fun countPending(taskId: String): Int
}
