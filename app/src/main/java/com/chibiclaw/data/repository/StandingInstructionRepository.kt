package com.chibiclaw.data.repository

import com.chibiclaw.agent.initiative.trigger.ComplexTrigger
import com.chibiclaw.data.database.StandingInstructionDao
import com.chibiclaw.data.database.StandingInstructionEntity
import com.chibiclaw.data.database.TaskChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * StandingInstructionRepository — CRUD + serialize/deserialize ComplexTrigger.
 *
 * Storage trigger di-encode JSON sealed-class polymorphic via kotlinx.serialization.
 */
@Singleton
class StandingInstructionRepository @Inject constructor(
    private val dao: StandingInstructionDao,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    suspend fun get(id: String): StandingInstructionEntity? = dao.get(id)

    suspend fun listEnabled(): List<StandingInstructionEntity> = dao.listEnabled()

    fun observeAll(): Flow<List<StandingInstructionEntity>> = dao.observeAll()

    suspend fun upsert(
        id: String? = null,
        name: String,
        description: String,
        trigger: ComplexTrigger,
        taskTemplate: String,
        enabled: Boolean = true,
        priority: Int = 3,
        cooldownMs: Long = 0L,
        maxFiresPerDay: Int = -1,
        preAuthorizedTools: List<String> = emptyList(),
        channel: TaskChannel = TaskChannel.STANDING,
    ): String {
        val now = Clock.System.now()
        val existing = id?.let { dao.get(it) }
        val entity = (existing ?: StandingInstructionEntity(
            id = id ?: UUID.randomUUID().toString(),
            name = name,
            description = description,
            triggerJson = "",
            taskTemplate = taskTemplate,
            createdAt = now,
            updatedAt = now,
        )).copy(
            name = name,
            description = description,
            triggerJson = json.encodeToString(ComplexTrigger.serializer(), trigger),
            taskTemplate = taskTemplate,
            enabled = enabled,
            priority = priority,
            cooldownMs = cooldownMs,
            maxFiresPerDay = maxFiresPerDay,
            preAuthorizedToolsCsv = preAuthorizedTools.joinToString(","),
            channel = channel,
            updatedAt = now,
        )
        dao.upsert(entity)
        Timber.i("StandingInstruction saved: ${entity.id} '${entity.name}' enabled=${entity.enabled}")
        return entity.id
    }

    suspend fun delete(entity: StandingInstructionEntity) = dao.delete(entity)

    suspend fun setEnabled(id: String, enabled: Boolean) = dao.setEnabled(id, enabled)

    /** Parse trigger JSON. Return null kalau corrupt. */
    fun parseTrigger(entity: StandingInstructionEntity): ComplexTrigger? = runCatching {
        json.decodeFromString(ComplexTrigger.serializer(), entity.triggerJson)
    }.onFailure { Timber.w(it, "Trigger parse failed for ${entity.id}") }.getOrNull()

    /** Cek cooldown + daily limit. Caller-side filter sebelum fire. */
    fun canFire(entity: StandingInstructionEntity, nowMs: Long): Boolean {
        if (!entity.enabled) return false
        // Cooldown
        val lastFireMs = entity.lastFiredAt?.toEpochMilliseconds() ?: 0L
        if (entity.cooldownMs > 0 && (nowMs - lastFireMs) < entity.cooldownMs) return false
        // Daily limit
        if (entity.maxFiresPerDay > 0) {
            val tz = TimeZone.currentSystemDefault()
            val resetDate = entity.firesTodayResetAt?.toLocalDateTime(tz)?.date
            val today = Clock.System.now().toLocalDateTime(tz).date
            val effectiveFires = if (resetDate == null || today > resetDate) 0 else entity.firesToday
            if (effectiveFires >= entity.maxFiresPerDay) return false
        }
        return true
    }

    /** Record fire — increment counter + reset daily kalau beda hari. */
    suspend fun recordFire(entity: StandingInstructionEntity) {
        val now = Clock.System.now()
        val tz = TimeZone.currentSystemDefault()
        val today = now.toLocalDateTime(tz).date
        val resetDate = entity.firesTodayResetAt?.toLocalDateTime(tz)?.date
        if (resetDate == null || today > resetDate) {
            dao.resetDailyCounter(entity.id, now)
        }
        dao.recordFire(entity.id, now)
    }

    fun lastFireMs(entity: StandingInstructionEntity): Long =
        entity.lastFiredAt?.toEpochMilliseconds() ?: 0L
}
