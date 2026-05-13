package com.chibiclaw.ai.llm.session

import kotlinx.serialization.Serializable

/**
 * Session blob hasil reverse-engineered web login. Stored di SecurePreferences
 * sebagai JSON string (di-encrypt by AES-256 EncryptedSharedPreferences).
 *
 * `maxAgeDays` default 14 — sebagian besar provider rotate cookie ~2 minggu.
 * Validator akan ping endpoint mundane setiap 24 jam supaya tahu kapan re-login.
 */

@Serializable
data class ClaudeWebSession(
    val orgId: String,
    val userId: String,
    val activeConvId: String? = null,
    val cookies: List<String>,
    val clientSha: String,
    val clientVersion: String,
    val deviceId: String,
    val userAgent: String,
    val createdAtMs: Long,
    val lastValidatedAtMs: Long,
    val maxAgeDays: Int = 14,
)

@Serializable
data class GPTWebSession(
    val userId: String,
    val accessToken: String,
    val cookies: List<String>,
    val conversationId: String? = null,
    val userAgent: String,
    val createdAtMs: Long,
    val lastValidatedAtMs: Long,
    val maxAgeDays: Int = 14,
)
