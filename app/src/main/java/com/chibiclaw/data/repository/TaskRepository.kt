package com.chibiclaw.data.repository

import com.chibiclaw.data.database.AgentStepDao
import com.chibiclaw.data.database.AgentStepEntity
import com.chibiclaw.data.database.DependencyStatus
import com.chibiclaw.data.database.NextIntent
import com.chibiclaw.data.database.TaskChannel
import com.chibiclaw.data.database.TaskDao
import com.chibiclaw.data.database.TaskDependencyDao
import com.chibiclaw.data.database.TaskEntity
import com.chibiclaw.data.database.TaskStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepository @Inject constructor(
    private val taskDao: TaskDao,
    private val agentStepDao: AgentStepDao,
    private val dependencyDao: TaskDependencyDao,
) {

    suspend fun create(
        goal: String,
        channel: TaskChannel,
        priority: Int = 3,
        parentId: String? = null,
        triggerSource: String? = null,
        maxIteration: Int = 15,
    ): TaskEntity {
        val task = TaskEntity(
            id = UUID.randomUUID().toString(),
            parentId = parentId,
            channel = channel,
            goal = goal,
            status = TaskStatus.PENDING,
            priority = priority,
            createdAt = Clock.System.now(),
            triggerSource = triggerSource,
            maxIteration = maxIteration,
        )
        taskDao.insert(task)
        return task
    }

    suspend fun get(id: String): TaskEntity? = taskDao.get(id)

    fun observe(id: String): Flow<TaskEntity?> = taskDao.observe(id)

    fun observeRecent(limit: Int = 50): Flow<List<TaskEntity>> = taskDao.observeRecent(limit)

    suspend fun recentSnapshot(limit: Int = 200): List<TaskEntity> = taskDao.recentSnapshot(limit)

    suspend fun runnable(limit: Int = 5): List<TaskEntity> = taskDao.runnable(limit)

    suspend fun listIncomplete(): List<TaskEntity> = taskDao.listIncomplete()

    suspend fun markPlanning(id: String) {
        taskDao.updateStatus(id, TaskStatus.PLANNING, startedAt = Clock.System.now())
    }

    suspend fun markRunning(id: String) {
        taskDao.updateStatus(id, TaskStatus.RUNNING)
    }

    suspend fun markCompleted(id: String, summary: String, emotion: String?) {
        val now = Clock.System.now()
        taskDao.markCompleted(id, TaskStatus.COMPLETED, now, summary, emotion)
        // Phase 9: auto-mark dependencies RESOLVED + unblock parent kalau no pending deps.
        dependencyDao.resolveDependentsOf(id, DependencyStatus.RESOLVED, now)
        unblockParentsIfClear(id)
    }

    suspend fun markFailed(id: String, error: String) {
        val now = Clock.System.now()
        taskDao.markFailed(id, TaskStatus.FAILED, now, error)
        // Phase 9: subtask fail → propagate FAILED ke deps; parent biarkan
        // putuskan sendiri lewat agent loop iteration berikutnya.
        dependencyDao.resolveDependentsOf(id, DependencyStatus.FAILED, now)
        unblockParentsIfClear(id)
    }

    /** Phase 9: walk dependents — kalau sudah tidak ada PENDING dep, parent kembali PENDING. */
    private suspend fun unblockParentsIfClear(resolvedTaskId: String) {
        val edges = dependencyDao.listDependents(resolvedTaskId)
        edges.forEach { edge ->
            val parentTaskId = edge.taskId
            val pending = dependencyDao.countPending(parentTaskId)
            if (pending == 0) {
                val parent = taskDao.get(parentTaskId) ?: return@forEach
                if (parent.status == TaskStatus.BLOCKED) {
                    taskDao.updateStatus(parentTaskId, TaskStatus.PENDING, startedAt = null)
                }
            }
        }
    }

    suspend fun markAwaitingUser(id: String, question: String) {
        taskDao.markFailed(id, TaskStatus.AWAITING_USER, Clock.System.now(), error = "awaiting_user: $question")
    }

    suspend fun markCancelled(id: String) {
        taskDao.markFailed(id, TaskStatus.CANCELLED, Clock.System.now(), error = "user_cancelled")
    }

    suspend fun setIteration(id: String, iter: Int) {
        taskDao.setIterationCount(id, iter)
    }

    suspend fun appendStep(
        taskId: String,
        thought: String,
        toolCallJson: String?,
        toolResultJson: String?,
        nextIntent: NextIntent,
        adapterUsed: String,
        tokensUsed: Int? = null,
        latencyMs: Long? = null,
    ): AgentStepEntity {
        val countBefore = agentStepDao.countByTask(taskId)
        val step = AgentStepEntity(
            id = UUID.randomUUID().toString(),
            taskId = taskId,
            stepIndex = countBefore,
            timestamp = Clock.System.now(),
            thought = thought,
            toolCallJson = toolCallJson,
            toolResultJson = toolResultJson,
            nextIntent = nextIntent,
            adapterUsed = adapterUsed,
            tokensUsed = tokensUsed,
            latencyMs = latencyMs,
        )
        agentStepDao.insert(step)
        return step
    }

    suspend fun listSteps(taskId: String): List<AgentStepEntity> = agentStepDao.listByTask(taskId)

    fun observeSteps(taskId: String): Flow<List<AgentStepEntity>> = agentStepDao.observeByTask(taskId)

    suspend fun cleanupExpired(now: Instant = Clock.System.now()): Int =
        taskDao.deleteExpired(now)
}
