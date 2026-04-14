package com.chibiclaw.ai

import com.chibiclaw.memory.MemoryManager
import com.chibiclaw.skills.SkillRegistry
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContextAssembler @Inject constructor(
    private val promptTemplate: PromptTemplate,
    private val skillRegistry: SkillRegistry,
    private val memoryManager: MemoryManager
) {
    suspend fun buildSystemPrompt(command: String): String {
        val skillContext = skillRegistry.toContextString()
        val recentHistory = memoryManager.getRecentCommands(5)
        val memoryContext = if (recentHistory.isNotEmpty()) {
            recentHistory.joinToString("\n") { "- ${it.command} → ${it.result}" }
        } else ""
        return promptTemplate.getSystemPrompt(skillContext, memoryContext)
    }
}
