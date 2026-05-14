package com.chibiclaw.agent

import com.chibiclaw.data.database.TaskChannel
import com.chibiclaw.data.database.TaskEntity
import com.chibiclaw.data.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TaskManager — facade di atas TaskRepository. Concurrency control + slot
 * tracking + listener untuk AgentRuntime dispatch.
 *
 * Phase 8: max 3 task paralel. Priority + cron-fairness:
 *   - `runnable()` di TaskDao sudah ORDER BY priority DESC, created_at ASC
 *   - High-priority task akan dapat slot duluan saat free
 *   - Tidak ada preemption sintetik — slot dilepas saat task complete
 *
 * Slot bookkeeping: ConcurrentHashMap thread-safe untuk reader; mutex hanya
 * untuk transactional pickup (nextRunnable atomic dengan slot reserve).
 */
@Singleton
class TaskManager @Inject constructor(
    private val repo: TaskRepository,
) {
    private val activeSlots = ConcurrentHashMap<String, Unit>()
    private val mutex = Mutex()

    private val maxParallel: Int = MAX_PARALLEL_TASKS

    suspend fun enqueue(
        goal: String,
        channel: TaskChannel,
        priority: Int = 3,
        parentId: String? = null,
        triggerSource: String? = null,
        maxIteration: Int = 15,
    ): TaskEntity {
        val task = repo.create(goal, channel, priority, parentId, triggerSource, maxIteration)
        Timber.i("Enqueued task ${task.id} channel=$channel goal=\"${goal.take(60)}\"")
        return task
    }

    suspend fun get(id: String): TaskEntity? = repo.get(id)

    fun observe(id: String) = repo.observe(id)

    fun observeRecent(limit: Int = 50) = repo.observeRecent(limit)

    /** Ambil task selanjutnya yang siap di-run. Mark sebagai slot occupied. */
    suspend fun nextRunnable(): TaskEntity? = mutex.withLock {
        if (activeSlots.size >= maxParallel) return null
        val pending = repo.runnable(limit = maxParallel - activeSlots.size)
        val next = pending.firstOrNull() ?: return null
        activeSlots[next.id] = Unit
        return next
    }

    /** Lepas slot setelah task selesai (apapun statusnya). */
    fun releaseSlot(taskId: String) {
        activeSlots.remove(taskId)
    }

    fun isActive(taskId: String): Boolean = activeSlots.containsKey(taskId)

    fun activeCount(): Int = activeSlots.size

    fun activeIds(): Set<String> = activeSlots.keys.toSet()

    fun maxParallel(): Int = maxParallel

    suspend fun resumeIncomplete(): List<TaskEntity> {
        // Phase 8: kalau ada incomplete task post-crash, mark failed (recovery
        // sederhana). Polish berikutnya: resume tergantung umur + last step
        // (akan butuh persistent agentstep ledger lookup).
        val incomplete = repo.listIncomplete()
        incomplete.forEach { task ->
            Timber.w("Resume: marking stale task ${task.id} as failed")
            repo.markFailed(task.id, "Service restart — task recovery fail-on-restart")
        }
        return incomplete
    }

    companion object {
        const val MAX_PARALLEL_TASKS = 3
    }
}
