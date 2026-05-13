package com.chibiclaw.ai.llm

import com.chibiclaw.ai.llm.adapters.GemmaAdapter
import com.chibiclaw.ai.llm.adapters.StubAdapter
import com.chibiclaw.data.database.TaskEntity
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Router yang pilih InferenceAdapter per Task. Cascade:
 *
 *   1. Gemma local (default)
 *   2. Stub (dev fallback kalau Gemma belum tersedia)
 *
 * Phase 4 akan tambah Gemini free, Claude web, GPT web di chain.
 *
 * Per-task adapter pinning: LLM bisa emit tool `escalate_to_cloud` untuk pin
 * task ke adapter target (Phase 4+).
 */
@Singleton
class InferenceRouter @Inject constructor(
    private val gemma: GemmaAdapter,
    private val stub: StubAdapter,
) {
    private val taskPinning = ConcurrentHashMap<String, String>()

    suspend fun selectAdapter(task: TaskEntity): InferenceAdapter {
        val pinnedId = taskPinning[task.id]
        if (pinnedId != null) {
            val pinned = adapterById(pinnedId)
            if (pinned != null && pinned.isAvailable()) return pinned
        }
        // Default cascade
        return if (gemma.isAvailable()) {
            gemma
        } else {
            Timber.w("Gemma not available, fallback to Stub (dev mode)")
            stub
        }
    }

    suspend fun escalate(target: AdapterTarget, taskId: String): InferenceAdapter? {
        val adapter = when (target) {
            AdapterTarget.GEMMA -> gemma.takeIf { it.isAvailable() }
            AdapterTarget.STUB -> stub
            AdapterTarget.GEMINI, AdapterTarget.CLAUDE, AdapterTarget.GPT -> null  // Phase 4
        }
        if (adapter != null) {
            taskPinning[taskId] = adapter.id
        }
        return adapter
    }

    fun unpin(taskId: String) {
        taskPinning.remove(taskId)
    }

    fun pinnedAdapterFor(taskId: String): String? = taskPinning[taskId]

    private fun adapterById(id: String): InferenceAdapter? = when (id) {
        gemma.id -> gemma
        stub.id -> stub
        else -> null
    }
}
