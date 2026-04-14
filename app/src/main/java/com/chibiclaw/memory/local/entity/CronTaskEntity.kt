package com.chibiclaw.memory.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persistent record of a user-defined scheduled command. Tied 1-to-1 with a
 * WorkManager PeriodicWorkRequest — the [id] is reused as the unique-work
 * name so we can enqueue/cancel by id without a secondary index.
 *
 * We keep this separate from [CommandHistory] because cron tasks are
 * *definitions* (what to run and when), not execution records.
 */
@Entity(tableName = "cron_tasks")
data class CronTaskEntity(
    @PrimaryKey val id: String,
    val command: String,
    val intervalMinutes: Long,
    val enabled: Boolean = true,
    val lastRun: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)
