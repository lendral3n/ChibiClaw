package com.chibiclaw.memory.context

import com.chibiclaw.memory.MemoryManager
import com.chibiclaw.executor.tier2.ContactsExecutor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 3.1 — EntityResolver.
 *
 * Maps natural references Gemma finds in user input ("si bos", "anakku",
 * "the last guy who sent the invoice") to concrete identifiers — phone
 * number, email, package name, URL, etc. The resolver tries several
 * lookup paths in order:
 *
 *  1. [ContactsExecutor] — a direct search in the system contacts
 *     database (fast, the ground truth).
 *  2. [MemoryManager]'s recent conversation context — maybe we just
 *     talked about "Budi" two messages ago and his number is still in
 *     memory.
 *  3. Heuristic fallback — if the query looks like a phone number or
 *     an email or a URL, return it as-is and let the executor decide.
 *
 * Returns a [Resolved] record carrying the canonical value plus a
 * short trace explaining why the resolution chose what it chose, which
 * Gemma can quote if the user asks "how did you know?".
 */
@Singleton
class EntityResolver @Inject constructor(
    private val contactsExecutor: ContactsExecutor,
    private val memoryManager: MemoryManager
) {

    data class Resolved(
        val query: String,
        val canonical: String,
        val kind: Kind,
        val source: String
    )

    enum class Kind { PHONE, EMAIL, URL, PACKAGE, CONTACT, UNKNOWN }

    suspend fun resolve(query: String): Resolved {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return Resolved(query, "", Kind.UNKNOWN, "empty")
        }

        // 1. Pure-syntactic shortcut — no need to hit the DB for a number.
        classifySyntactic(trimmed)?.let { return it }

        // 2. Contacts DB search.
        val contactResult = try {
            contactsExecutor.search(trimmed)
        } catch (_: Exception) { "" }
        if (contactResult.isNotEmpty() && !contactResult.startsWith("No ")) {
            // ContactsExecutor returns "Name: 08123...". Parse the phone.
            val phone = contactResult.substringAfter(":", "").trim()
            if (phone.isNotEmpty()) {
                return Resolved(query, phone, Kind.PHONE, "contacts:$contactResult")
            }
            return Resolved(query, contactResult, Kind.CONTACT, "contacts")
        }

        // 3. Conversation memory fallback.
        val memHits = try {
            memoryManager.searchMemory(trimmed).take(1)
        } catch (_: Exception) { emptyList() }
        memHits.firstOrNull()?.let {
            return Resolved(query, it.content, Kind.UNKNOWN, "memory")
        }

        return Resolved(query, trimmed, Kind.UNKNOWN, "passthrough")
    }

    private fun classifySyntactic(q: String): Resolved? {
        val phone = Regex("^[+]?[0-9][0-9\\s()-]{6,}$")
        val email = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        val url = Regex("^https?://[\\w.-]+.*$")
        val pkg = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$")
        return when {
            phone.matches(q.replace("\\s".toRegex(), "")) ->
                Resolved(q, q, Kind.PHONE, "regex:phone")
            email.matches(q) -> Resolved(q, q, Kind.EMAIL, "regex:email")
            url.matches(q) -> Resolved(q, q, Kind.URL, "regex:url")
            pkg.matches(q) -> Resolved(q, q, Kind.PACKAGE, "regex:pkg")
            else -> null
        }
    }
}
