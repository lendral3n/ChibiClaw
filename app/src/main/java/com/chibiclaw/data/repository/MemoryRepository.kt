package com.chibiclaw.data.repository

import com.chibiclaw.data.database.MemoryCategory
import com.chibiclaw.data.database.MemoryCategoryCount
import com.chibiclaw.data.database.MemoryDao
import com.chibiclaw.data.database.MemoryRecordEntity
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryRepository @Inject constructor(
    private val dao: MemoryDao,
) {
    suspend fun upsert(record: MemoryRecordEntity) = dao.upsert(record)

    suspend fun get(id: String): MemoryRecordEntity? = dao.get(id)

    suspend fun getByKey(key: String): MemoryRecordEntity? = dao.getByKey(key)

    suspend fun listByCategory(category: MemoryCategory): List<MemoryRecordEntity> =
        dao.listByCategory(category)

    suspend fun listAll(limit: Int = 1000): List<MemoryRecordEntity> = dao.listAll(limit)

    suspend fun count(): Int = dao.count()

    suspend fun touch(id: String, accessCount: Int) {
        dao.touch(id, Clock.System.now(), accessCount)
    }

    suspend fun updateConfidence(id: String, confidence: Float) {
        dao.updateConfidence(id, confidence.coerceIn(0f, 1f))
    }

    suspend fun setPinned(id: String, pinned: Boolean) {
        dao.setPinned(id, pinned)
    }

    suspend fun deleteById(id: String) = dao.deleteById(id)

    suspend fun deleteByKey(key: String) = dao.deleteByKey(key)

    suspend fun cleanupExpired(now: Instant = Clock.System.now()): Int = dao.deleteExpired(now)

    suspend fun listOldestAccessed(limit: Int): List<MemoryRecordEntity> =
        dao.listOldestAccessed(limit)

    suspend fun listStaleSince(threshold: Instant): List<MemoryRecordEntity> =
        dao.listStaleSince(threshold)

    suspend fun deleteLowConfidenceStale(minConfidence: Float, threshold: Instant): Int =
        dao.deleteLowConfidenceStale(minConfidence, threshold)

    suspend fun countByCategory(): List<MemoryCategoryCount> = dao.countByCategory()
}
