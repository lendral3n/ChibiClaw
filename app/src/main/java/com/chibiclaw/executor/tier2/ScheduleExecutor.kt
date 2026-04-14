package com.chibiclaw.executor.tier2

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 11 — Scheduled task automation via WorkManager.
 */
@Singleton
class ScheduleExecutor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workManager = WorkManager.getInstance(context)
    private val prefs: SharedPreferences = context.getSharedPreferences("chibi_schedules", Context.MODE_PRIVATE)

    fun perform(operation: String, command: String, scheduleTime: String, repeatInterval: String, taskId: String): String {
        val op = operation.trim().lowercase()
        return try {
            when (op) {
                "create", "add", "jadwal", "schedule" -> createTask(command, scheduleTime, repeatInterval)
                "list", "daftar" -> listTasks()
                "cancel", "delete", "hapus", "batal" -> cancelTask(taskId)
                "cancel_all", "clear" -> cancelAll()
                "status" -> getTaskStatus(taskId)
                else -> "schedule_error: unknown operation '$op'"
            }
        } catch (e: Exception) {
            "schedule_error: ${e.message}"
        }
    }

    private fun createTask(command: String, scheduleTime: String, repeatInterval: String): String {
        if (command.isBlank()) return "schedule_error: command required"

        val data = Data.Builder()
            .putString("command", command)
            .build()

        if (repeatInterval.isNotBlank()) {
            // Periodic task
            val intervalMs = parseInterval(repeatInterval) ?: return "schedule_error: invalid interval '$repeatInterval'"
            val intervalMinutes = intervalMs / 60000
            if (intervalMinutes < 15) return "schedule_error: minimum interval is 15 minutes"

            val workRequest = PeriodicWorkRequestBuilder<ScheduledCommandWorker>(
                intervalMinutes, TimeUnit.MINUTES
            ).setInputData(data)
                .addTag("chibi_schedule")
                .build()

            val uniqueName = "chibi_${command.hashCode()}"
            workManager.enqueueUniquePeriodicWork(
                uniqueName,
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )

            saveTask(workRequest.id.toString(), command, "every $repeatInterval")
            return "schedule_created: id=${workRequest.id} command='$command' interval=$repeatInterval"
        } else {
            // One-time delayed task
            val delayMs = parseDelay(scheduleTime) ?: return "schedule_error: invalid time '$scheduleTime'"
            val workRequest = OneTimeWorkRequestBuilder<ScheduledCommandWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .addTag("chibi_schedule")
                .build()

            workManager.enqueue(workRequest)
            saveTask(workRequest.id.toString(), command, "in $scheduleTime")
            return "schedule_created: id=${workRequest.id} command='$command' delay=$scheduleTime"
        }
    }

    private fun listTasks(): String {
        val tasks = prefs.all
        if (tasks.isEmpty()) return "[schedules] no tasks"
        val sb = StringBuilder("[schedules] ${tasks.size} task(s)\n")
        tasks.forEach { (id, value) ->
            sb.append("• id=$id | $value\n")
        }
        return sb.toString()
    }

    private fun cancelTask(taskId: String): String {
        if (taskId.isBlank()) return "schedule_error: taskId required"
        return try {
            workManager.cancelWorkById(UUID.fromString(taskId))
            prefs.edit().remove(taskId).apply()
            "schedule_cancelled: $taskId"
        } catch (e: Exception) {
            "schedule_error: ${e.message}"
        }
    }

    private fun cancelAll(): String {
        workManager.cancelAllWorkByTag("chibi_schedule")
        prefs.edit().clear().apply()
        return "schedule_cancelled_all"
    }

    private fun getTaskStatus(taskId: String): String {
        if (taskId.isBlank()) return "schedule_error: taskId required"
        return try {
            val info = workManager.getWorkInfoById(UUID.fromString(taskId)).get()
            "schedule_status: id=$taskId state=${info?.state ?: "NOT_FOUND"}"
        } catch (e: Exception) {
            "schedule_error: ${e.message}"
        }
    }

    private fun saveTask(id: String, command: String, schedule: String) {
        prefs.edit().putString(id, "$command ($schedule)").apply()
    }

    private fun parseInterval(interval: String): Long? {
        val regex = Regex("(\\d+)\\s*(m|min|h|hour|d|day|s|sec)")
        val match = regex.find(interval.lowercase()) ?: return interval.toLongOrNull()?.times(60000)
        val num = match.groupValues[1].toLongOrNull() ?: return null
        return when (match.groupValues[2]) {
            "s", "sec" -> num * 1000
            "m", "min" -> num * 60 * 1000
            "h", "hour" -> num * 3600 * 1000
            "d", "day" -> num * 86400 * 1000
            else -> null
        }
    }

    private fun parseDelay(time: String): Long? = parseInterval(time)

    companion object {
        private const val TAG = "ScheduleExecutor"
    }
}

/**
 * Worker that executes a scheduled ChibiClaw command.
 * In a real implementation, this would inject ChibiOrchestrator and run the command.
 * For now, it logs the command and sends a notification.
 */
class ScheduledCommandWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {
    override fun doWork(): Result {
        val command = inputData.getString("command") ?: return Result.failure()
        Log.d("ScheduledWorker", "Executing scheduled command: $command")
        // TODO: Inject ChibiOrchestrator and execute command
        // For now, just log it
        return Result.success()
    }
}
