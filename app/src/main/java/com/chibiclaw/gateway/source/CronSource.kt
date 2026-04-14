package com.chibiclaw.gateway.source

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.chibiclaw.memory.local.dao.CronTaskDao
import com.chibiclaw.memory.local.entity.CronTaskEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CronSource owns the user's scheduled commands. Each task is persisted in
 * [CronTaskDao] (so it survives reboots) and mirrored into WorkManager as a
 * unique PeriodicWorkRequest whose name is the task's id.
 *
 * The UI talks to this class via [observeAll] / [upsertTask] / [deleteTask];
 * the persistence + WorkManager sync is handled in one place to keep the two
 * representations from drifting.
 */
@Singleton
class CronSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cronTaskDao: CronTaskDao
) {
    private val workManager = WorkManager.getInstance(context)

    fun observeAll(): Flow<List<CronTaskEntity>> = cronTaskDao.observeAll()

    suspend fun getAll(): List<CronTaskEntity> = cronTaskDao.getAll()

    /**
     * Creates or updates a task. If [task.enabled] is false the WorkManager
     * entry is cancelled but the row is kept so the user can re-enable.
     */
    suspend fun upsertTask(task: CronTaskEntity) {
        cronTaskDao.upsert(task)
        if (task.enabled) {
            scheduleWork(task)
        } else {
            workManager.cancelUniqueWork(task.id)
        }
    }

    suspend fun deleteTask(task: CronTaskEntity) {
        workManager.cancelUniqueWork(task.id)
        cronTaskDao.delete(task)
    }

    /** Re-enqueues every persisted task with WorkManager. Call once at boot. */
    suspend fun restoreAll() {
        val tasks = cronTaskDao.getAll()
        tasks.filter { it.enabled }.forEach { scheduleWork(it) }
        Log.d(TAG, "Restored ${tasks.size} cron task(s) from DB")
    }

    suspend fun markRun(id: String) = cronTaskDao.markRun(id)

    private fun scheduleWork(task: CronTaskEntity) {
        enqueue(task.id, task.command, task.intervalMinutes)
    }

    private fun enqueue(id: String, command: String, intervalMinutes: Long) {
        // WorkManager's minimum periodic interval is 15 minutes — clamp so
        // users can't accidentally create a spin loop.
        val clamped = intervalMinutes.coerceAtLeast(15L)
        val workRequest = PeriodicWorkRequestBuilder<CronWorker>(clamped, TimeUnit.MINUTES)
            .setInputData(
                androidx.work.Data.Builder()
                    .putString(CronWorker.KEY_COMMAND, command)
                    .putString(CronWorker.KEY_TASK_ID, id)
                    .build()
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.NOT_REQUIRED)
                    .build()
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            id,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
        Log.d(TAG, "Scheduled cron task: $id every ${clamped}min")
    }

    fun cancelTask(id: String) {
        workManager.cancelUniqueWork(id)
        Log.d(TAG, "Cancelled cron task: $id")
    }

    fun cancelAll() {
        workManager.cancelAllWork()
        Log.d(TAG, "All cron tasks cancelled")
    }

    companion object {
        private const val TAG = "CronSource"
    }
}

/**
 * WorkManager Worker that submits a cron command to CommandGateway.
 * Uses Hilt injection via HiltWorker (requires hilt-work dependency).
 */
class CronWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        val command = inputData.getString(KEY_COMMAND) ?: return Result.failure()
        val taskId = inputData.getString(KEY_TASK_ID).orEmpty()
        Log.d(TAG, "CronWorker executing: $command (id=$taskId)")
        // Broadcast intent so ChibiService can pick it up
        val intent = android.content.Intent("com.chibiclaw.CRON_COMMAND").apply {
            putExtra("command", command)
            putExtra("task_id", taskId)
            setPackage(applicationContext.packageName)
        }
        applicationContext.sendBroadcast(intent)
        return Result.success()
    }

    companion object {
        const val KEY_COMMAND = "cron_command"
        const val KEY_TASK_ID = "cron_task_id"
        private const val TAG = "CronWorker"
    }
}
