package com.chibiclaw.compliance

import com.chibiclaw.data.database.AuditActionType
import com.chibiclaw.data.database.AuditDao
import com.chibiclaw.data.database.AuditLogEntity
import com.chibiclaw.data.database.AuditResultStatus
import com.chibiclaw.data.prefs.SecurePreferences
import dagger.hilt.android.scopes.ServiceScoped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.days

/**
 * Audit logger infrastructure.
 *
 * Phase 0 expose API; usage di phase berikutnya saat tool / LLM call mulai jalan.
 * Tetap di-instantiate Phase 0 supaya setup wizard event terlog (privacy accepted,
 * consent granted/revoked, dll).
 *
 * Sensitive content (phone, email, card) di-redact via [redactSensitive].
 */
@Singleton
class AuditLogger @Inject constructor(
    private val auditDao: AuditDao,
    private val securePreferences: SecurePreferences,
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun log(
        actionType: AuditActionType,
        dataSummary: String,
        taskId: String? = null,
        toolName: String? = null,
        cloudDestination: String? = null,
        resultStatus: AuditResultStatus = AuditResultStatus.SUCCESS,
    ) {
        scope.launch {
            try {
                val now = Clock.System.now()
                val ttlDays = securePreferences.auditRetentionDays()
                val entry = AuditLogEntity(
                    id = UUID.randomUUID().toString(),
                    timestamp = now,
                    actionType = actionType,
                    taskId = taskId,
                    toolName = toolName,
                    dataSummary = redactSensitive(dataSummary),
                    cloudDestination = cloudDestination,
                    userConsentState = securePreferences.currentConsentSnapshot(),
                    resultStatus = resultStatus,
                    ttlUntil = now.plus(ttlDays.days),
                )
                auditDao.insert(entry)
                Timber.v("Audit: ${actionType.name} — ${entry.dataSummary.take(80)}")
            } catch (t: Throwable) {
                Timber.w(t, "Audit log insert failed (non-fatal)")
            }
        }
    }

    /**
     * Redact PII patterns (phone, email, card number) supaya audit log tidak bocor
     * sensitive content.
     */
    private fun redactSensitive(text: String): String {
        return text
            // Phone numbers (E.164 atau lokal Indonesia)
            .replace(Regex("\\+?\\d{10,}"), "[PHONE]")
            // Email
            .replace(Regex("[\\w.-]+@[\\w.-]+\\.[A-Za-z]{2,}"), "[EMAIL]")
            // Credit card (16 digit cluster)
            .replace(Regex("\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b"), "[CARD]")
            .take(200)
    }
}
