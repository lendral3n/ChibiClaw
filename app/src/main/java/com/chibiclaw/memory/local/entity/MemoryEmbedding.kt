package com.chibiclaw.memory.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Phase 3.5 — one row per embedded memory chunk.
 *
 * The vector itself is stored as a raw little-endian float32 byte
 * blob. ONNX Runtime writes back FloatBuffers so we flatten them
 * here; reading is done in the DAO → VectorMemoryStore path. We
 * deliberately sidestep Room's TypeConverter machinery because a
 * 384-dim float32 is already a perfectly nice ByteArray and the
 * converter overhead would be non-trivial per query.
 *
 * `sourceType` lets us attribute a chunk back to conversation /
 * command history / contact / clipboard so we can filter at query
 * time (e.g. "only search conversation memory for this prompt").
 */
@Entity(
    tableName = "memory_embedding",
    indices = [
        Index(value = ["sourceType"]),
        Index(value = ["sourceId"])
    ]
)
data class MemoryEmbedding(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceType: String,        // "conversation" | "command" | "contact" | "clipboard" | "note"
    val sourceId: Long,            // primary key of the row this chunk came from (0 if synthetic)
    val text: String,              // the original chunk text (short)
    val vector: ByteArray,         // float32[dim] flattened little-endian
    val dim: Int,                  // usually 384 for all-MiniLM-L6-v2
    val timestamp: Long = System.currentTimeMillis()
) {
    // Room's autogen data-class equality chokes on ByteArray — override by hand.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MemoryEmbedding) return false
        return id == other.id &&
               sourceType == other.sourceType &&
               sourceId == other.sourceId &&
               text == other.text &&
               dim == other.dim &&
               timestamp == other.timestamp &&
               vector.contentEquals(other.vector)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + sourceType.hashCode()
        result = 31 * result + sourceId.hashCode()
        result = 31 * result + text.hashCode()
        result = 31 * result + vector.contentHashCode()
        result = 31 * result + dim
        result = 31 * result + timestamp.hashCode()
        return result
    }
}
