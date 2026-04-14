package com.chibiclaw.memory

import com.chibiclaw.memory.local.dao.*
import com.chibiclaw.memory.local.entity.*
import com.chibiclaw.memory.vector.VectorMemoryStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryManager @Inject constructor(
    private val commandHistoryDao: CommandHistoryDao,
    private val conversationDao: ConversationDao,
    private val appPatternDao: AppPatternDao,
    private val contactContextDao: ContactContextDao,
    private val whitelistDao: WhitelistDao,
    private val vectorMemoryStore: VectorMemoryStore
) {

    suspend fun saveCommand(
        command: String,
        result: String,
        state: String,
        severity: String,
        tier: Int = -1
    ) {
        val id = commandHistoryDao.insert(
            CommandHistory(
                command = command,
                result = result,
                state = state,
                severity = severity,
                executionTier = tier
            )
        )
        // Phase 3.5 — fan out into the vector store so semantic search
        // (e.g. "yang kemarin tentang jadwal rapat") can find it later.
        if (state == "DONE") {
            try {
                vectorMemoryStore.index("command", id, "$command → $result")
            } catch (_: Exception) { /* best-effort */ }
        }
    }

    suspend fun getRecentCommands(limit: Int = 10): List<CommandHistory> =
        commandHistoryDao.getRecent(limit)

    suspend fun saveConversationTurn(sessionId: String, role: String, content: String) {
        val id = conversationDao.insert(
            ConversationContext(sessionId = sessionId, role = role, content = content)
        )
        try {
            vectorMemoryStore.index("conversation", id, content)
        } catch (_: Exception) { }
    }

    suspend fun getSession(sessionId: String): List<ConversationContext> =
        conversationDao.getBySession(sessionId)

    /**
     * Hybrid search: vector store first (semantic, handles synonyms
     * and paraphrases), Room LIKE as a fallback when the embedder
     * isn't loaded or returns nothing.
     */
    suspend fun searchMemory(query: String): List<ConversationContext> {
        if (vectorMemoryStore.isAvailable()) {
            val hits = try {
                vectorMemoryStore.search(query, topK = 5, sourceType = "conversation")
            } catch (_: Exception) { emptyList() }
            if (hits.isNotEmpty()) {
                return hits.map { hit ->
                    ConversationContext(
                        id = hit.embedding.sourceId,
                        sessionId = "vector",
                        role = "vector",
                        content = hit.embedding.text,
                        timestamp = hit.embedding.timestamp
                    )
                }
            }
        }
        return conversationDao.search(query)
    }

    suspend fun recordAppUsage(packageName: String, tier: Int, success: Boolean) {
        val existing = appPatternDao.getByPackage(packageName)
        if (existing == null) {
            appPatternDao.insert(AppPattern(packageName = packageName, avgTier = tier, usageCount = 1))
        } else {
            val newRate = if (success) (existing.successRate * 0.9f) + 0.1f
                          else existing.successRate * 0.9f
            appPatternDao.update(existing.copy(
                successRate = newRate,
                avgTier = tier,
                usageCount = existing.usageCount + 1,
                lastUsed = System.currentTimeMillis()
            ))
        }
    }

    suspend fun getWhitelist(packageName: String) = whitelistDao.getByPackage(packageName)

    suspend fun addToWhitelist(packageName: String, maxTier: Int = 3, policy: String = "auto") {
        whitelistDao.insert(AppWhitelist(packageName = packageName, allowedTier = maxTier, policy = policy))
    }

    suspend fun queryAll(query: String): String {
        val history = commandHistoryDao.getRecent(5)
        val memory = searchMemory(query).take(3)
        val contacts = contactContextDao.search(query)

        val sb = StringBuilder()
        if (history.isNotEmpty()) {
            sb.append("Recent commands:\n")
            history.forEach { sb.append("- ${it.command} → ${it.result}\n") }
        }
        if (memory.isNotEmpty()) {
            sb.append("Memory:\n")
            memory.forEach { sb.append("- [${it.role}] ${it.content}\n") }
        }
        if (contacts.isNotEmpty()) {
            sb.append("Contacts:\n")
            contacts.forEach { sb.append("- ${it.name} via ${it.preferredApp}\n") }
        }
        return sb.toString().ifEmpty { "No relevant memory found." }
    }
}
