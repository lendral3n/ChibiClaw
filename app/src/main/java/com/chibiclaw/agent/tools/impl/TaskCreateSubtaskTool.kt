package com.chibiclaw.agent.tools.impl

import com.chibiclaw.agent.TaskManager
import com.chibiclaw.agent.tools.ErrorClass
import com.chibiclaw.agent.tools.Tool
import com.chibiclaw.agent.tools.ToolCall
import com.chibiclaw.agent.tools.ToolCapability
import com.chibiclaw.agent.tools.ToolCost
import com.chibiclaw.agent.tools.ToolResult
import com.chibiclaw.agent.tools.ToolSafety
import com.chibiclaw.agent.tools.ToolSeverity
import com.chibiclaw.agent.tools.ToolSpec
import com.chibiclaw.data.database.DependencyStatus
import com.chibiclaw.data.database.TaskChannel
import com.chibiclaw.data.database.TaskDao
import com.chibiclaw.data.database.TaskDependencyDao
import com.chibiclaw.data.database.TaskDependencyEntity
import com.chibiclaw.data.database.TaskStatus
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import javax.inject.Inject

/**
 * task_create_subtask — decompose task kompleks ke 1+ subtask.
 *
 * Tool args (parent task ditandai via call.args["__taskId"] dari dispatcher):
 *   goal: string (subtask goal)
 *   priority: int (optional 1-5, default inherit parent atau 3)
 *   blocks_parent: bool (optional, default true) — kalau true, parent task
 *     BLOCKED sampai subtask resolve.
 *
 * Anti-loop:
 *  - LLM dianjurkan emit subtask hanya saat decomposisi natural (mis. summary
 *    multiple item).
 *  - Phase 9 polish: depth limit 3.
 */
class TaskCreateSubtaskTool @Inject constructor(
    private val taskManager: TaskManager,
    private val dependencyDao: TaskDependencyDao,
    private val taskDao: TaskDao,
) : Tool {

    override val spec = ToolSpec(
        name = "task_create_subtask",
        description = """
            Buat subtask dari task aktif. Pakai untuk decomposisi paralel
            (mis. "summary 3 artikel" → 3 subtask, masing-masing satu artikel).
            Parent task otomatis blocked sampai subtask resolve (kecuali args
            blocks_parent=false).
        """.trimIndent(),
        parameters = mapOf(
            "goal" to "string (subtask goal)",
            "priority" to "int (optional, 1-5, default 3)",
            "blocks_parent" to "bool (optional, default true)",
        ),
        capability = ToolCapability(
            latencyMsRange = 30..120,
            worksOn = listOf("agent-internal"),
            cost = ToolCost.LOW,
        ),
        safety = ToolSafety(
            severity = ToolSeverity.LOW,
            reason = "Local task creation; no external side-effect",
        ),
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        val parentTaskId = call.args["__taskId"]?.jsonPrimitive?.content
            ?: return ToolResult.Error(
                callId = call.callId,
                errorClass = ErrorClass.INVALID_ARGS,
                message = "Internal: __taskId tidak ter-stamp oleh dispatcher",
            )
        val goal = call.args["goal"]?.jsonPrimitive?.content
            ?: return ToolResult.Error(
                callId = call.callId,
                errorClass = ErrorClass.INVALID_ARGS,
                message = "goal arg required",
            )
        val priority = call.args["priority"]?.jsonPrimitive?.content?.toIntOrNull()?.coerceIn(1, 5) ?: 3
        val blocksParent = call.args["blocks_parent"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true

        val parent = taskManager.get(parentTaskId) ?: return ToolResult.Error(
            callId = call.callId,
            errorClass = ErrorClass.NOT_AVAILABLE,
            message = "Parent task tidak ditemukan",
        )

        // Phase 9: subtask depth limit (anti-loop / runaway recursion).
        val depth = taskManager.depthOf(parent.id)
        if (depth >= MAX_SUBTASK_DEPTH) {
            return ToolResult.Error(
                callId = call.callId,
                errorClass = ErrorClass.NOT_AVAILABLE,
                message = "Subtask depth limit ($MAX_SUBTASK_DEPTH) tercapai — decomposisi sudah cukup dalam",
                recoveryHint = "Selesaikan subtask existing dulu sebelum buat baru",
            )
        }

        val subtask = taskManager.enqueue(
            goal = goal,
            channel = parent.channel,
            priority = priority,
            parentId = parent.id,
            triggerSource = "subtask:${parent.id}",
            maxIteration = parent.maxIteration,
        )

        if (blocksParent) {
            dependencyDao.insert(
                TaskDependencyEntity(
                    taskId = parent.id,
                    dependsOnTaskId = subtask.id,
                    status = DependencyStatus.PENDING,
                    createdAt = Clock.System.now(),
                ),
            )
            // Phase 9: mark parent BLOCKED supaya AgentRuntime pause iteration.
            taskDao.updateStatus(parent.id, TaskStatus.BLOCKED, startedAt = parent.startedAt)
        }

        Timber.i("Subtask created: parent=$parentTaskId → child=${subtask.id} blocks=$blocksParent depth=${depth + 1}")
        return ToolResult.Success(
            callId = call.callId,
            data = JsonObject(mapOf(
                "subtask_id" to JsonPrimitive(subtask.id),
                "parent_id" to JsonPrimitive(parent.id),
                "blocks_parent" to JsonPrimitive(blocksParent),
                "channel" to JsonPrimitive(parent.channel.name),
                "priority" to JsonPrimitive(priority),
                "depth" to JsonPrimitive(depth + 1),
            )),
        )
    }

    companion object {
        private const val MAX_SUBTASK_DEPTH = 3
    }
}
