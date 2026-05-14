package com.chibiclaw.agent.initiative.trigger

import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.parser.CronParser as UpstreamCronParser
import timber.log.Timber
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CronParser wrapper di atas cron-utils.
 *
 * Format: UNIX cron 5-field (minute hour dayOfMonth month dayOfWeek).
 * Contoh:
 *   "0 7 * * *"          → 07:00 setiap hari
 *   "0 18-22 * * MON-FRI" → tiap jam 18-22 weekday
 *   "0 17,18,19 * * *"   → 17:00, 18:00, 19:00 setiap hari
 *
 * shouldFire(cron, lastFireMs): true kalau ada execution time antara
 * lastFireMs dan now. Penting: simpan lastFireMs di standing instruction
 * supaya tidak miss tick.
 */
@Singleton
class CronParser @Inject constructor() {

    private val parser by lazy {
        val definition = CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX)
        UpstreamCronParser(definition)
    }

    fun isValid(expression: String): Boolean = runCatching {
        parser.parse(expression).validate()
    }.isSuccess

    /**
     * True kalau cron punya execution time di range (lastFireMs, nowMs].
     * Pakai system default time zone.
     */
    fun shouldFire(expression: String, lastFireMs: Long, nowMs: Long): Boolean {
        return runCatching {
            val cron = parser.parse(expression).also { it.validate() }
            val zone = ZoneId.systemDefault()
            val executionTime = com.cronutils.model.time.ExecutionTime.forCron(cron)
            val now = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMs), zone)
            val lastFire = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(lastFireMs), zone)
            // lastExecution() di optional — kalau ada dan di antara lastFire .. now → fire
            val lastExec = executionTime.lastExecution(now)
            if (!lastExec.isPresent) return@runCatching false
            val lastExecTime = lastExec.get()
            lastExecTime.isAfter(lastFire) && !lastExecTime.isAfter(now)
        }.onFailure {
            Timber.w(it, "Cron parse failed for '$expression'")
        }.getOrDefault(false)
    }

    /** Next fire time after now, milliseconds. Null kalau cron tidak ada upcoming. */
    fun nextFire(expression: String, fromMs: Long = System.currentTimeMillis()): Long? {
        return runCatching {
            val cron = parser.parse(expression)
            val executionTime = com.cronutils.model.time.ExecutionTime.forCron(cron)
            val zone = ZoneId.systemDefault()
            val from = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(fromMs), zone)
            executionTime.nextExecution(from).map { it.toInstant().toEpochMilli() }.orElse(null)
        }.getOrNull()
    }
}
