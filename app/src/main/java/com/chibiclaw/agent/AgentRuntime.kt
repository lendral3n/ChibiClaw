package com.chibiclaw.agent

import com.chibiclaw.agent.tools.ToolDispatcher
import com.chibiclaw.agent.tools.ToolResult
import com.chibiclaw.ai.llm.InferenceResult
import com.chibiclaw.ai.llm.InferenceRouter
import com.chibiclaw.ai.llm.LlmOutcome
import com.chibiclaw.ai.llm.ResponseParser
import com.chibiclaw.compliance.AuditLogger
import com.chibiclaw.data.database.AuditActionType
import com.chibiclaw.data.database.NextIntent
import com.chibiclaw.data.database.TaskChannel
import com.chibiclaw.data.database.TaskEntity
import com.chibiclaw.data.database.TaskStatus
import com.chibiclaw.data.repository.TaskRepository
import com.chibiclaw.voice.ResponseComposer
import com.chibiclaw.voice.tts.ElevenLabsTts
import com.chibiclaw.voice.tts.TtsResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AgentRuntime — orchestrator yang menjalankan agent loop per task.
 *
 * Phase 1: single-slot sequential. Tick scheduler poll TaskManager untuk
 * next runnable task, eksekusi loop sampai done/blocked/failed.
 *
 * Phase 8: multi-slot paralel + resource scheduler.
 *
 * Lihat docs/architecture/12-agent-loop.md.
 */
@Singleton
class AgentRuntime @Inject constructor(
    private val taskManager: TaskManager,
    private val taskRepository: TaskRepository,
    private val inferenceRouter: InferenceRouter,
    private val contextBuilder: ContextBuilder,
    private val toolDispatcher: ToolDispatcher,
    private val auditLogger: AuditLogger,
    private val responseComposer: ResponseComposer,
    private val elevenLabsTts: ElevenLabsTts,
) {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Volatile private var tickJob: kotlinx.coroutines.Job? = null

    fun start() {
        if (tickJob?.isActive == true) return
        Timber.i("AgentRuntime starting tick loop")
        tickJob = scope.launch {
            // Recovery: incomplete tasks dari crash sebelumnya
            taskManager.resumeIncomplete()

            while (isActive) {
                runOneTick()
                delay(TICK_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        tickJob?.cancel()
        tickJob = null
        Timber.i("AgentRuntime stopped")
    }

    private suspend fun runOneTick() {
        // Phase 8: fill all free slots in one tick (was 1-at-a-time pre-Phase 8).
        while (true) {
            val next = taskManager.nextRunnable() ?: return
            scope.launch {
                try {
                    executeTask(next.id)
                } finally {
                    taskManager.releaseSlot(next.id)
                }
            }
        }
    }

    /**
     * Execute task — agent loop iterative.
     */
    suspend fun executeTask(taskId: String) {
        var task = taskRepository.get(taskId) ?: return
        if (task.status != TaskStatus.PENDING) {
            Timber.w("executeTask called but task ${taskId} not PENDING (status=${task.status})")
            return
        }

        taskRepository.markPlanning(task.id)
        Timber.i("Task ${task.id} started: \"${task.goal.take(80)}\"")

        var iteration = task.iterationCount
        loop@ while (iteration < task.maxIteration) {
            task = taskRepository.get(taskId) ?: break
            if (task.status.isTerminal) break

            // Build context
            val prompt = contextBuilder.build(task)

            // Select adapter (with task pinning)
            val adapter = inferenceRouter.selectAdapter(task)
            val start = System.currentTimeMillis()
            val result = adapter.complete(prompt)
            val latency = System.currentTimeMillis() - start

            auditLogger.log(
                actionType = AuditActionType.LLM_CALL_LOCAL,
                dataSummary = "Task=${task.id} adapter=${adapter.id} iter=$iteration latency=${latency}ms",
                taskId = task.id,
            )

            val raw = when (result) {
                is InferenceResult.Success -> result.raw
                is InferenceResult.Error -> {
                    Timber.w("LLM error: ${result.errorClass} ${result.message}")
                    taskRepository.markFailed(task.id, "LLM error: ${result.message}")
                    return
                }
            }

            val outcome = ResponseParser.parse(raw)
            handleOutcome(task, outcome, adapter.id, iteration, latency, (result as InferenceResult.Success).tokensUsed)

            iteration += 1
            taskRepository.setIteration(task.id, iteration)

            // Check terminal state setelah handle
            val refreshed = taskRepository.get(taskId)
            if (refreshed == null || refreshed.status.isTerminal) {
                break@loop
            }
            if (refreshed.status == TaskStatus.AWAITING_USER) {
                break@loop
            }
        }

        val final = taskRepository.get(taskId)
        if (final != null && !final.status.isTerminal && final.status != TaskStatus.AWAITING_USER) {
            taskRepository.markFailed(taskId, "Max iteration ($iteration) reached")
            Timber.w("Task $taskId reached max iteration")
        }

        // Voice response — speak hasil ke user (channel CHAT only).
        val refreshed = taskRepository.get(taskId)
        if (refreshed != null && refreshed.channel == TaskChannel.CHAT) {
            speakResponse(refreshed)
        }
    }

    private suspend fun speakResponse(task: TaskEntity) {
        val composed = responseComposer.compose(task)
        if (composed.isSilent || !elevenLabsTts.hasApiKey()) return
        when (val result = elevenLabsTts.speak(composed.text, composed.emotionTag)) {
            is TtsResult.Success -> Timber.d("TTS played ${result.bytesStreamed / 1024}KB")
            is TtsResult.Error -> Timber.w("TTS error: ${result.message}")
        }
    }

    private suspend fun handleOutcome(
        task: TaskEntity,
        outcome: LlmOutcome,
        adapterId: String,
        iteration: Int,
        latency: Long,
        tokensUsed: Int,
    ) {
        when (outcome) {
            is LlmOutcome.Done -> {
                taskRepository.appendStep(
                    taskId = task.id,
                    thought = "[done] ${outcome.summary}",
                    toolCallJson = null,
                    toolResultJson = null,
                    nextIntent = NextIntent.DONE,
                    adapterUsed = adapterId,
                    tokensUsed = tokensUsed,
                    latencyMs = latency,
                )
                taskRepository.markCompleted(task.id, outcome.summary, outcome.emotionTag)
            }

            is LlmOutcome.AwaitUser -> {
                taskRepository.appendStep(
                    taskId = task.id,
                    thought = "[await_user] ${outcome.question}",
                    toolCallJson = null,
                    toolResultJson = null,
                    nextIntent = NextIntent.AWAIT_USER,
                    adapterUsed = adapterId,
                    tokensUsed = tokensUsed,
                    latencyMs = latency,
                )
                taskRepository.markAwaitingUser(task.id, outcome.question)
            }

            is LlmOutcome.ToolCalls -> {
                if (task.status == TaskStatus.PLANNING) {
                    taskRepository.markRunning(task.id)
                }
                for (call in outcome.calls) {
                    val toolResult = toolDispatcher.execute(call, task)
                    val callJson = json.encodeToString(com.chibiclaw.agent.tools.ToolCall.serializer(), call)
                    val resultJson = when (toolResult) {
                        is ToolResult.Success -> "success(${toolResult.data})"
                        is ToolResult.Error -> "error(${toolResult.errorClass}: ${toolResult.message})"
                        is ToolResult.Timeout -> "timeout(${toolResult.elapsedMs}ms)"
                        is ToolResult.UserDenied -> "user_denied"
                    }
                    taskRepository.appendStep(
                        taskId = task.id,
                        thought = outcome.reasoning,
                        toolCallJson = callJson,
                        toolResultJson = resultJson,
                        nextIntent = NextIntent.CONTINUE,
                        adapterUsed = adapterId,
                        tokensUsed = tokensUsed,
                        latencyMs = latency,
                    )
                    if (toolResult is ToolResult.Error && toolResult.fatal) {
                        taskRepository.markFailed(task.id, "Fatal tool error: ${toolResult.message}")
                        return
                    }
                }
            }

            is LlmOutcome.Reasoning -> {
                taskRepository.appendStep(
                    taskId = task.id,
                    thought = "[reasoning] ${outcome.reasoning}",
                    toolCallJson = null,
                    toolResultJson = null,
                    nextIntent = NextIntent.REASONING,
                    adapterUsed = adapterId,
                    tokensUsed = tokensUsed,
                    latencyMs = latency,
                )
            }

            is LlmOutcome.Escalate -> {
                taskRepository.appendStep(
                    taskId = task.id,
                    thought = "[escalate] target=${outcome.target} reason=${outcome.reason}",
                    toolCallJson = null,
                    toolResultJson = null,
                    nextIntent = NextIntent.ESCALATE,
                    adapterUsed = adapterId,
                    tokensUsed = tokensUsed,
                    latencyMs = latency,
                )
                // Phase 4 akan implement actual cloud pinning. Phase 1: no-op.
                Timber.d("Escalate (Phase 4 TBD): target=${outcome.target}")
            }
        }
    }

    companion object {
        private const val TICK_INTERVAL_MS = 1_000L
    }
}
