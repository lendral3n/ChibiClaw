package com.chibiclaw.memory

import com.chibiclaw.data.database.MemoryCategory
import com.chibiclaw.data.database.MemoryRecordEntity
import com.chibiclaw.data.repository.MemoryRepository
import com.chibiclaw.memory.embedding.EmbeddingProvider
import com.chibiclaw.memory.embedding.cosineSimilarity
import com.chibiclaw.memory.embedding.toEmbeddingArray
import com.chibiclaw.memory.embedding.toEmbeddingBlob
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.days

/**
 * MemoryStore — facade untuk persistent vector memory.
 *
 * Hybrid storage:
 *  - Vector (embedding 384-dim) untuk semantic search
 *  - JSON value untuk structured fact per category
 *
 * Lihat docs/architecture/16-memory-system.md.
 */
@Singleton
class MemoryStore @Inject constructor(
    private val repo: MemoryRepository,
    private val embedder: EmbeddingProvider,
) {

    suspend fun remember(
        category: MemoryCategory,
        key: String,
        valueJson: String,
        confidence: Float = 1.0f,
        ttlDays: Int? = null,
        sourceTaskId: String? = null,
    ) {
        val textForEmbedding = "${category.name.lowercase()}: $key — ${valueJson.take(200)}"
        val embedding = embedder.encode(textForEmbedding)
        val now = Clock.System.now()
        val ttl = ttlDays?.let { now.plus(it.days) }

        val record = MemoryRecordEntity(
            id = UUID.randomUUID().toString(),
            category = category,
            key = key,
            valueJson = valueJson,
            embeddingBlob = embedding.toEmbeddingBlob(),
            createdAt = now,
            lastAccessedAt = now,
            accessCount = 0,
            confidence = confidence.coerceIn(0f, 1f),
            ttlUntil = ttl,
            sourceTaskId = sourceTaskId,
        )
        repo.upsert(record)
        Timber.d("Memory remembered: $category/$key")
    }

    suspend fun recall(
        query: String,
        category: MemoryCategory? = null,
        topK: Int = 5,
        minSimilarity: Float = 0.3f,
    ): List<MemoryHit> {
        val queryEmbedding = embedder.encodeQuery(query)

        val candidates = if (category != null) {
            repo.listByCategory(category)
        } else {
            repo.listAll()
        }

        val hits = candidates.map { record ->
            val recEmbed = record.embeddingBlob.toEmbeddingArray()
            MemoryHit(record, cosineSimilarity(queryEmbedding, recEmbed))
        }.filter { it.similarity >= minSimilarity }
            .sortedByDescending { it.similarity }
            .take(topK)

        // Touch (update access tracking) asynchronously — bukan blocking
        hits.forEach { hit ->
            repo.touch(hit.record.id, hit.record.accessCount + 1)
        }

        return hits
    }

    suspend fun listByCategory(category: MemoryCategory): List<MemoryRecordEntity> =
        repo.listByCategory(category)

    suspend fun forget(idOrKey: String) {
        if (idOrKey.matches(UUID_REGEX)) {
            repo.deleteById(idOrKey)
        } else {
            repo.deleteByKey(idOrKey)
        }
    }

    suspend fun cleanup(now: Instant = Clock.System.now()) {
        repo.cleanupExpired(now)
        // LRU evict kalau >5000
        val total = repo.count()
        if (total > MAX_RECORDS) {
            val toDelete = repo.listOldestAccessed(total - SOFT_LIMIT)
            toDelete.forEach { repo.deleteById(it.id) }
            Timber.i("Memory LRU evict: ${toDelete.size} records removed")
        }
    }

    companion object {
        private const val MAX_RECORDS = 5000
        private const val SOFT_LIMIT = 4500
        private val UUID_REGEX = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    }
}

data class MemoryHit(
    val record: MemoryRecordEntity,
    val similarity: Float,
)
