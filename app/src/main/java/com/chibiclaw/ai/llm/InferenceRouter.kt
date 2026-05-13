package com.chibiclaw.ai.llm

import com.chibiclaw.ai.llm.adapters.ClaudeWebAdapter
import com.chibiclaw.ai.llm.adapters.GPTWebAdapter
import com.chibiclaw.ai.llm.adapters.GeminiFreeAdapter
import com.chibiclaw.ai.llm.adapters.GemmaAdapter
import com.chibiclaw.ai.llm.adapters.StubAdapter
import com.chibiclaw.data.database.TaskEntity
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Router yang pilih InferenceAdapter per Task. Cascade default:
 *
 *   1. Per-task pin (kalau ada, dari escalate_to_cloud)
 *   2. Gemma local (kalau model tersedia + available)
 *   3. Gemini free (kalau API key + quota)
 *   4. Claude web (kalau session valid)
 *   5. GPT web (kalau session valid)
 *   6. Stub (dev fallback / no cloud configured)
 *
 * Per-task adapter pinning: LLM emit `escalate_to_cloud` → pin task ke target.
 */
@Singleton
class InferenceRouter @Inject constructor(
    private val gemma: GemmaAdapter,
    private val gemini: GeminiFreeAdapter,
    private val claudeWeb: ClaudeWebAdapter,
    private val gptWeb: GPTWebAdapter,
    private val stub: StubAdapter,
) {
    private val taskPinning = ConcurrentHashMap<String, String>()

    suspend fun selectAdapter(task: TaskEntity): InferenceAdapter {
        val pinnedId = taskPinning[task.id]
        if (pinnedId != null) {
            val pinned = adapterById(pinnedId)
            if (pinned != null && pinned.isAvailable()) {
                Timber.d("Adapter pinned: ${pinned.id} for task ${task.id}")
                return pinned
            }
            // Pinned tapi tidak available → drop pin, fallback cascade.
            taskPinning.remove(task.id)
            Timber.w("Pinned adapter $pinnedId unavailable; cascading")
        }
        return cascadeAvailable()
    }

    suspend fun escalate(target: AdapterTarget, taskId: String): InferenceAdapter? {
        val adapter = resolveTarget(target) ?: return null
        if (!adapter.isAvailable()) {
            Timber.w("Escalate ke ${adapter.id} gagal — adapter belum available")
            return null
        }
        taskPinning[taskId] = adapter.id
        Timber.i("Task $taskId pinned ke ${adapter.id}")
        return adapter
    }

    fun unpin(taskId: String) {
        taskPinning.remove(taskId)
    }

    fun pinnedAdapterFor(taskId: String): String? = taskPinning[taskId]

    /** Snapshot semua adapter untuk Settings UI. */
    fun allAdapters(): List<InferenceAdapter> = listOf(gemma, gemini, claudeWeb, gptWeb, stub)

    private suspend fun cascadeAvailable(): InferenceAdapter {
        // Priority: local first (zero cost), then cloud free, then cloud reverse.
        if (gemma.isAvailable()) return gemma
        if (gemini.isAvailable()) return gemini
        if (claudeWeb.isAvailable()) return claudeWeb
        if (gptWeb.isAvailable()) return gptWeb
        Timber.w("No live adapter; fallback Stub (dev mode)")
        return stub
    }

    private fun resolveTarget(target: AdapterTarget): InferenceAdapter? = when (target) {
        AdapterTarget.GEMMA -> gemma
        AdapterTarget.GEMINI -> gemini
        AdapterTarget.CLAUDE -> claudeWeb
        AdapterTarget.GPT -> gptWeb
        AdapterTarget.STUB -> stub
    }

    private fun adapterById(id: String): InferenceAdapter? = when (id) {
        gemma.id -> gemma
        gemini.id -> gemini
        claudeWeb.id -> claudeWeb
        gptWeb.id -> gptWeb
        stub.id -> stub
        else -> null
    }
}
