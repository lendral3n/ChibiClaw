package com.chibiclaw.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

/**
 * MemoryRecord — persistent knowledge per kategori (USER_PROFILE / CONTACT /
 * HABIT / FACT / PREFERENCE). Vector + structured JSON.
 *
 * Embedding (FloatArray 384-dim → ByteArray little-endian) di-compute via
 * EmbeddingProvider (multilingual-e5-small ONNX INT8).
 */
@Entity(
    tableName = "memory_record",
    indices = [
        Index("category"),
        Index("key", unique = true),
        Index("last_accessed_at"),
    ],
)
data class MemoryRecordEntity(
    @PrimaryKey val id: String,
    val category: MemoryCategory,
    val key: String,
    @ColumnInfo(name = "value_json") val valueJson: String,
    @ColumnInfo(name = "embedding_blob", typeAffinity = androidx.room.ColumnInfo.BLOB)
    val embeddingBlob: ByteArray,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "last_accessed_at") val lastAccessedAt: Instant,
    @ColumnInfo(name = "access_count") val accessCount: Int = 0,
    val confidence: Float = 1.0f,
    @ColumnInfo(name = "ttl_until") val ttlUntil: Instant? = null,
    @ColumnInfo(name = "source_task_id") val sourceTaskId: String? = null,
    /** Phase 7 polish: pinned record immune to confidence decay & auto-forget. */
    @ColumnInfo(name = "pinned", defaultValue = "0") val pinned: Boolean = false,
) {
    // Custom equals/hashCode untuk ByteArray comparison
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MemoryRecordEntity) return false
        return id == other.id
    }
    override fun hashCode(): Int = id.hashCode()
}

enum class MemoryCategory {
    USER_PROFILE,
    CONTACT,
    HABIT,
    FACT,
    PREFERENCE,
}
