package com.chibiclaw.ai

import com.chibiclaw.memory.MemoryManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContextAssembler @Inject constructor(
    private val promptTemplate: PromptTemplate,
    private val memoryManager: MemoryManager
) {
    // Skill context dihapus dari system prompt — model sekarang memanggil
    // lookup_skill() sendiri saat runtime (agentic skill discovery).
    // Memory context dibatasi agar tidak membengkakkan prompt.
    private val MAX_MEMORY_CHARS = 500

    suspend fun buildSystemPrompt(command: String): String {
        val recentHistory = memoryManager.getRecentCommands(5)
        val memoryContext = if (recentHistory.isNotEmpty()) {
            recentHistory
                .joinToString("\n") { "- ${it.command} → ${it.result}" }
                .take(MAX_MEMORY_CHARS)
        } else ""
        return promptTemplate.getSystemPrompt(memoryContext = memoryContext)
    }
}
