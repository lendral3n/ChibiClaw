package com.chibiclaw.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

/**
 * Task entity — first-class agent unit.
 *
 * Lihat docs/architecture/11-data-model.md untuk full schema.
 */
@Entity(
    tableName = "task",
    indices = [
        Index("status"),
        Index("channel"),
        Index("created_at"),
        Index("priority"),
    ],
)
data class TaskEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "parent_id") val parentId: String? = null,
    val channel: TaskChannel,
    val goal: String,
    val status: TaskStatus,
    val priority: Int = 3,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "started_at") val startedAt: Instant? = null,
    @ColumnInfo(name = "completed_at") val completedAt: Instant? = null,
    val deadline: Instant? = null,
    @ColumnInfo(name = "trigger_source") val triggerSource: String? = null,
    @ColumnInfo(name = "iteration_count") val iterationCount: Int = 0,
    @ColumnInfo(name = "max_iteration") val maxIteration: Int = 15,
    @ColumnInfo(name = "result_summary") val resultSummary: String? = null,
    @ColumnInfo(name = "error_message") val errorMessage: String? = null,
    @ColumnInfo(name = "emotion_tag") val emotionTag: String? = null,
    @ColumnInfo(name = "ttl_until") val ttlUntil: Instant? = null,
)

enum class TaskChannel { CHAT, AUTONOMOUS, STANDING }

enum class TaskStatus {
    PENDING,
    PLANNING,
    RUNNING,
    BLOCKED,
    AWAITING_USER,
    COMPLETED,
    FAILED,
    CANCELLED;

    val isTerminal: Boolean get() = this in setOf(COMPLETED, FAILED, CANCELLED)
    val isActive: Boolean get() = this in setOf(PENDING, PLANNING, RUNNING)
}

/**
 * Per-iteration step. Audit-grade detail untuk debug.
 */
@Entity(
    tableName = "agent_step",
    foreignKeys = [ForeignKey(
        entity = TaskEntity::class,
        parentColumns = ["id"],
        childColumns = ["task_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("task_id"), Index("timestamp")],
)
data class AgentStepEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "task_id") val taskId: String,
    @ColumnInfo(name = "step_index") val stepIndex: Int,
    val timestamp: Instant,
    val thought: String,
    @ColumnInfo(name = "tool_call_json") val toolCallJson: String? = null,
    @ColumnInfo(name = "tool_result_json") val toolResultJson: String? = null,
    @ColumnInfo(name = "next_intent") val nextIntent: NextIntent,
    @ColumnInfo(name = "adapter_used") val adapterUsed: String,
    @ColumnInfo(name = "tokens_used") val tokensUsed: Int? = null,
    @ColumnInfo(name = "latency_ms") val latencyMs: Long? = null,
)

enum class NextIntent { CONTINUE, DONE, AWAIT_USER, ERROR, ESCALATE, REASONING }
