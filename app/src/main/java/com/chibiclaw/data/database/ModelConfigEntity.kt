package com.chibiclaw.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

/**
 * Per-adapter config + quota tracking.
 *
 * adapterId: identik dengan InferenceAdapter.id (mis. "gemini_free", "claude_web").
 * dailyQuota: limit harian; -1 = unlimited.
 * usedToday: increment per call; reset di {@link AdapterQuotaTracker} kalau
 *   beda hari dari lastResetAt.
 * exhaustedAt: kalau provider return RATE_LIMITED, isi timestamp supaya kita
 *   skip adapter ini sementara (cooldown sampai reset harian).
 * sessionJson: untuk web adapter — JSON-serialized session blob (cookies +
 *   clientSha + dll). Plaintext di Room (DB encrypted by SQLCipher).
 *   Gemini free TIDAK pakai field ini; pakai SecurePreferences `gemini_api_key`.
 * sessionExpiresAt: prediksi expiry session (Phase 4 = 14 hari default).
 */
@Entity(tableName = "model_config")
data class ModelConfigEntity(
    @PrimaryKey val adapterId: String,
    val displayName: String,
    val enabled: Boolean = false,
    val dailyQuota: Int = -1,
    val usedToday: Int = 0,
    val lastResetAt: Instant = Instant.fromEpochMilliseconds(0L),
    val exhaustedAt: Instant? = null,
    val sessionJson: String? = null,
    val sessionExpiresAt: Instant? = null,
    val lastUsedAt: Instant? = null,
    val totalCalls: Long = 0L,
    val totalErrors: Long = 0L,
)
