package com.chibiclaw.agent

import com.chibiclaw.agent.tools.ToolRegistry
import com.chibiclaw.ai.llm.AgentPrompt
import com.chibiclaw.data.database.TaskEntity
import com.chibiclaw.data.repository.TaskRepository
import com.chibiclaw.memory.MemoryStore
import com.chibiclaw.world.WorldObserver
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Build AgentPrompt untuk LLM call. Input: task + repo + world + memory + tools.
 *
 * Hybrid memory strategy (ADR-009): auto-include top-3 high-confidence + LLM
 * bisa explicit `memory_recall` untuk yang lebih jauh.
 */
@Singleton
class ContextBuilder @Inject constructor(
    private val taskRepository: TaskRepository,
    private val worldObserver: WorldObserver,
    private val memoryStore: MemoryStore,
    private val toolRegistry: ToolRegistry,
) {

    private val personaTraits = """
        - Persona: Fuu (kawaii, lembut, profesional, to-the-point)
        - Bahasa: Indonesia kasual default, mix English saat code-switch
        - Respon: padat, max 2-3 kalimat
        - Selalu konfirmasi tindakan HIGH severity
    """.trimIndent()

    suspend fun build(task: TaskEntity): AgentPrompt {
        // Task history (current task's agent steps)
        val taskHistory = taskRepository.listSteps(task.id).takeLast(N_STEP_HISTORY).map {
            buildString {
                append("[step ${it.stepIndex}] ${it.thought.take(160)}")
                if (it.toolCallJson != null) append(" | tool=${it.toolCallJson.take(80)}")
                if (it.toolResultJson != null) append(" | result=${it.toolResultJson.take(120)}")
            }
        }

        // Recent tasks for cross-task context (other tasks)
        val recentTasks = taskRepository.observeRecent(N_RECENT_TASKS).let { /* observable */ }
            // Phase 1: skip observable, use simple repo query if needed.
            // Untuk simplicity, kosong dulu — Phase 7 memory mature akan inject pattern.
        val recentTasksList = emptyList<String>()

        // World snapshot
        val worldText = worldObserver.current().toPromptText()

        // Relevant memory (hybrid: auto-include high-confidence top-K)
        val memoryHits = memoryStore.recall(
            query = task.goal,
            topK = AUTO_INCLUDE_MEMORY_K,
            minSimilarity = AUTO_INCLUDE_MIN_SIM,
        )
        val memoryLines = memoryHits.map { hit ->
            "${hit.record.category.name}/${hit.record.key} (conf ${"%.2f".format(hit.record.confidence)}, sim ${"%.2f".format(hit.similarity)}): ${hit.record.valueJson.take(120)}"
        }

        // Tools
        val tools = toolRegistry.availableSpecs()

        return AgentPrompt(
            systemPrompt = "",        // diisi di PromptBuilder (Gemma format embed system)
            taskGoal = task.goal,
            taskChannel = task.channel,
            taskHistory = taskHistory,
            recentTasks = recentTasksList,
            worldSnapshot = worldText,
            relevantMemory = memoryLines,
            emotionSignal = null,     // Phase 2: emotion detector fill ini
            toolCatalog = tools,
            personaTraits = personaTraits,
            iteration = task.iterationCount,
            maxIteration = task.maxIteration,
        )
    }

    companion object {
        private const val N_STEP_HISTORY = 10
        private const val N_RECENT_TASKS = 20
        private const val AUTO_INCLUDE_MEMORY_K = 3
        private const val AUTO_INCLUDE_MIN_SIM = 0.5f
    }
}
