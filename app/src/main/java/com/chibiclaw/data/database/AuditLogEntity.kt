package com.chibiclaw.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

/**
 * AuditLog entry. Setiap action yang affect user data atau eksternal state masuk sini.
 *
 * - data_summary: ringkasan singkat dengan PII redacted (phone, email, card di-masked).
 * - cloud_destination: null kalau local, else adapter id (gemini_free / claude_web / gpt_web).
 * - ttl_until: auto-cleanup TTL, default 90 hari dari created_at (configurable via SecurePreferences).
 */
@Entity(
    tableName = "audit_log",
    indices = [
        Index("timestamp"),
        Index("action_type"),
        Index("ttl_until"),
    ],
)
data class AuditLogEntity(
    @PrimaryKey val id: String,
    val timestamp: Instant,
    @ColumnInfo(name = "action_type") val actionType: AuditActionType,
    @ColumnInfo(name = "task_id") val taskId: String? = null,
    @ColumnInfo(name = "tool_name") val toolName: String? = null,
    @ColumnInfo(name = "data_summary") val dataSummary: String,
    @ColumnInfo(name = "cloud_destination") val cloudDestination: String? = null,
    @ColumnInfo(name = "user_consent_state") val userConsentState: String,
    @ColumnInfo(name = "result_status") val resultStatus: AuditResultStatus,
    @ColumnInfo(name = "ttl_until") val ttlUntil: Instant,
)

enum class AuditActionType {
    // Phase 0
    SERVICE_STARTED, SERVICE_STOPPED,
    SETUP_COMPLETED, PRIVACY_ACCEPTED, CONSENT_GRANTED, CONSENT_REVOKED,
    PERMISSION_REQUESTED, PERMISSION_DENIED,
    OVERLAY_SHOWN, OVERLAY_HIDDEN,
    EXPORT_DATA, ERASE_DATA,

    // Phase 1
    LLM_CALL_LOCAL,
    TOOL_EXECUTED,
    MEMORY_READ, MEMORY_WRITE, MEMORY_DELETE,

    // Phase 2
    MIC_ACTIVATED, MIC_DEACTIVATED,
    STT_RESULT, TTS_PLAYBACK,

    // Phase 3
    DATA_READ_CONTACT, DATA_READ_SMS, DATA_READ_NOTIFICATION,
    DATA_WRITE_MESSAGING, DATA_WRITE_FILE,
    SHIZUKU_EXEC,

    // Phase 4
    LLM_CALL_CLOUD,
    CLOUD_AUTH_SETUP, CLOUD_AUTH_REVOKED,

    // Phase 5
    DATA_READ_LOCATION, DATA_READ_CALENDAR,
    SCREEN_CAPTURED,

    // Phase 6
    STANDING_INSTRUCTION_FIRED, STANDING_INSTRUCTION_CREATED, STANDING_INSTRUCTION_DELETED,

    // General
    UNKNOWN,
}

enum class AuditResultStatus {
    SUCCESS, FAILED, BLOCKED_BY_GATE, USER_DENIED, TIMEOUT,
}
