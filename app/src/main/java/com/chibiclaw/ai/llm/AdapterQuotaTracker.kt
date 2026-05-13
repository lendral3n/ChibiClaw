package com.chibiclaw.ai.llm

import com.chibiclaw.data.database.ModelConfigDao
import com.chibiclaw.data.database.ModelConfigEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracker quota per adapter — daily reset, exhaustion flag, success/error count.
 *
 * Aturan reset:
 *  - Tiap `tryConsume(adapterId)` cek apakah `lastResetAt` lebih dari 24 jam lalu
 *    (atau beda kalender harian di local TZ). Kalau ya → reset usedToday=0,
 *    exhaustedAt=null.
 *
 * Aturan exhaustion:
 *  - Saat call return RATE_LIMITED → `markExhausted(adapterId)`. Subsequent
 *    `hasQuota()` return false sampai reset harian.
 *  - Saat usedToday >= dailyQuota → otomatis exhausted juga.
 */
@Singleton
class AdapterQuotaTracker @Inject constructor(
    private val dao: ModelConfigDao,
) {
    private val mutex = Mutex()

    /** Ensure entity exists; create default kalau belum. */
    suspend fun ensureConfig(
        adapterId: String,
        displayName: String,
        dailyQuota: Int,
    ): ModelConfigEntity = mutex.withLock {
        dao.get(adapterId) ?: ModelConfigEntity(
            adapterId = adapterId,
            displayName = displayName,
            dailyQuota = dailyQuota,
        ).also { dao.upsert(it) }
    }

    /**
     * Cek apakah adapter masih punya quota call. Trigger daily reset kalau perlu.
     * Return true → OK call. False → skip / cascade ke adapter lain.
     */
    suspend fun hasQuota(adapterId: String): Boolean = mutex.withLock {
        val cfg = dao.get(adapterId) ?: return@withLock false
        if (!cfg.enabled) return@withLock false

        val resetCfg = maybeReset(cfg)
        if (resetCfg.dailyQuota < 0) return@withLock true                      // unlimited
        if (resetCfg.exhaustedAt != null) return@withLock false                // explicit exhaust
        resetCfg.usedToday < resetCfg.dailyQuota
    }

    /** Increment counter setelah successful call. */
    suspend fun recordSuccess(adapterId: String) = mutex.withLock {
        dao.incrementUsage(adapterId, Clock.System.now())
    }

    /** Mark adapter exhausted (RATE_LIMITED dari provider). */
    suspend fun markExhausted(adapterId: String) = mutex.withLock {
        val now = Clock.System.now()
        dao.markExhausted(adapterId, now)
        dao.incrementError(adapterId)
        Timber.w("Adapter $adapterId marked exhausted @ $now")
    }

    suspend fun recordError(adapterId: String) = mutex.withLock {
        dao.incrementError(adapterId)
    }

    suspend fun snapshot(adapterId: String): ModelConfigEntity? = dao.get(adapterId)

    suspend fun listAll(): List<ModelConfigEntity> = dao.listAll()

    private suspend fun maybeReset(cfg: ModelConfigEntity): ModelConfigEntity {
        val now = Clock.System.now()
        val tz = TimeZone.currentSystemDefault()
        val resetDate = cfg.lastResetAt.toLocalDateTime(tz).date
        val today = now.toLocalDateTime(tz).date
        if (today > resetDate) {
            dao.resetDaily(cfg.adapterId, now)
            return cfg.copy(usedToday = 0, lastResetAt = now, exhaustedAt = null)
        }
        return cfg
    }
}
