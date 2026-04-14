package com.chibiclaw.gateway.source

import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.model.time.ExecutionTime
import com.cronutils.parser.CronParser
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Optional
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 7.1 — wrapper around `cron-utils` that validates and
 * evaluates standard 5-field Unix cron strings ("0 9 * * 1" = 9am
 * every Monday).
 *
 * WorkManager still handles the actual firing via its minimum-15-min
 * periodic work request, but when we need "once at 09:00 sharp on
 * Monday" (e.g. from a skill), we use this parser to compute the
 * nextExecution() timestamp and register a ONE-shot alarm via
 * AlarmManager. The interval-minute path in [CronSource] still works
 * for recurring housekeeping.
 */
@Singleton
class CronExpressionParser @Inject constructor() {

    private val parser: CronParser by lazy {
        CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX))
    }

    /** True if [expression] is a valid 5-field Unix cron. */
    fun isValid(expression: String): Boolean = try {
        parser.parse(expression).validate()
        true
    } catch (_: Exception) {
        false
    }

    /**
     * Returns the next execution time after [from] (default = now)
     * in millis, or `null` if the expression is invalid or has no
     * future trigger.
     */
    fun nextExecution(expression: String, from: ZonedDateTime = ZonedDateTime.now()): Long? {
        return try {
            val cron = parser.parse(expression)
            cron.validate()
            val exec = ExecutionTime.forCron(cron)
            val next: Optional<ZonedDateTime> = exec.nextExecution(from)
            if (next.isPresent) next.get().toInstant().toEpochMilli() else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Returns the delay (in milliseconds) until the next execution
     * relative to *now*, or `null` if the expression is invalid or
     * there is no future trigger.
     */
    fun delayUntilNext(expression: String): Long? {
        val now = System.currentTimeMillis()
        val next = nextExecution(expression, ZonedDateTime.now(ZoneId.systemDefault())) ?: return null
        return (next - now).coerceAtLeast(0)
    }
}
