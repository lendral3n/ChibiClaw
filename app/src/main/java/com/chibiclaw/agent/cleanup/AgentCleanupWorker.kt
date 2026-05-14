package com.chibiclaw.agent.cleanup

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chibiclaw.data.repository.TaskRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.datetime.Clock
import timber.log.Timber
import kotlin.time.Duration.Companion.days

/**
 * AgentCleanupWorker — daily housekeeping:
 *  - Delete tasks COMPLETED/FAILED/CANCELLED dengan ttlUntil expired.
 *  - AgentStep ikut hilang via CASCADE foreign key.
 *
 * AuditLog cleanup sudah di-handle terpisah oleh AuditLogger.cleanupOlderThan
 * (Phase 0). Phase 8 worker fokus task tree.
 */
@HiltWorker
class AgentCleanupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val taskRepository: TaskRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val now = Clock.System.now()
        val deletedCount = taskRepository.cleanupExpired(now)
        Timber.i("AgentCleanupWorker done: $deletedCount task expired/TTL deleted")
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "agent_cleanup"
        const val DEFAULT_TTL_DAYS = 30
    }
}
