package com.chibiclaw.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import kotlinx.datetime.Instant

/**
 * TaskDependency — directed edge: `taskId` blocked-on `dependsOnTaskId`.
 *
 * Status:
 *  - PENDING: dependency masih active (depends task belum complete).
 *  - RESOLVED: dependency completed sukses.
 *  - FAILED: dependency failed → parent task biasanya juga gagal.
 *
 * Foreign key CASCADE: kalau task dihapus, edge ikut hilang.
 */
@Entity(
    tableName = "task_dependency",
    primaryKeys = ["task_id", "depends_on_task_id"],
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["task_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["depends_on_task_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("task_id"), Index("depends_on_task_id"), Index("status")],
)
data class TaskDependencyEntity(
    @ColumnInfo(name = "task_id") val taskId: String,
    @ColumnInfo(name = "depends_on_task_id") val dependsOnTaskId: String,
    val status: DependencyStatus = DependencyStatus.PENDING,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "resolved_at") val resolvedAt: Instant? = null,
)

enum class DependencyStatus { PENDING, RESOLVED, FAILED }
