package com.chibiclaw.memory.vector

import com.chibiclaw.memory.local.dao.MemoryEmbeddingDao
import com.chibiclaw.memory.local.entity.MemoryEmbedding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 3.5 — persistent vector store over Room.
 *
 * Room isn't a real vector DB, so we fetch every embedding of the
 * requested source type into memory and brute-force cosine similarity
 * on the CPU. At 384 dims × 5 000 rows this is ≈ 2 M float muls, which
 * a Snapdragon 8 Elite cruises through in <10 ms. If we ever grow past
 * 20 000 rows we can add an HNSW index via a JNI lib, but that's
 * premature — the bottleneck today is I/O, not math.
 *
 * All vectors are stored as little-endian float32 byte blobs so we
 * can avoid Room TypeConverter overhead on the hot path.
 */
@Singleton
class VectorMemoryStore @Inject constructor(
    private val dao: MemoryEmbeddingDao,
    private val embedder: MiniLMEmbedder
) {

    data class Hit(val embedding: MemoryEmbedding, val score: Float)

    /** True when the underlying ONNX model loaded successfully. */
    fun isAvailable(): Boolean = embedder.isReady()

    /**
     * Embed [text] and upsert a row tagged with [sourceType]/[sourceId].
     * Silently becomes a no-op if the embedder isn't available (no
     * model file on device) — we never block ingestion on vector load.
     */
    suspend fun index(sourceType: String, sourceId: Long, text: String) {
        if (text.isBlank()) return
        if (!embedder.isReady()) return
        val vec = embedder.encodeOne(text) ?: return
        val row = MemoryEmbedding(
            sourceType = sourceType,
            sourceId = sourceId,
            text = text.take(512),
            vector = floatsToBytes(vec),
            dim = vec.size
        )
        try { dao.insert(row) } catch (_: Exception) { /* best-effort */ }
    }

    /** Bulk variant — avoids re-initialising the ONNX session per row. */
    suspend fun indexBatch(entries: List<Triple<String, Long, String>>) {
        if (entries.isEmpty() || !embedder.isReady()) return
        val vecs = embedder.encode(entries.map { it.third })
        if (vecs.size != entries.size) return
        val rows = entries.zip(vecs).mapNotNull { (entry, vec) ->
            val (type, id, text) = entry
            if (text.isBlank() || vec.isEmpty()) null
            else MemoryEmbedding(
                sourceType = type,
                sourceId = id,
                text = text.take(512),
                vector = floatsToBytes(vec),
                dim = vec.size
            )
        }
        try { dao.insertAll(rows) } catch (_: Exception) { }
    }

    /**
     * Cosine-similarity search. Returns up to [topK] best hits,
     * optionally filtered to a single source type. Threshold is
     * applied post-ranking so we don't accidentally return a ton of
     * irrelevant but just-barely-positive vectors.
     */
    suspend fun search(
        query: String,
        topK: Int = 5,
        sourceType: String? = null,
        threshold: Float = 0.35f
    ): List<Hit> = withContext(Dispatchers.Default) {
        if (query.isBlank() || !embedder.isReady()) return@withContext emptyList()
        val q = embedder.encodeOne(query) ?: return@withContext emptyList()
        val pool = if (sourceType != null) dao.byType(sourceType, 2000) else dao.recent(2000)
        val ranked = pool.asSequence()
            .map { row -> Hit(row, cosine(q, bytesToFloats(row.vector))) }
            .filter { it.score >= threshold }
            .sortedByDescending { it.score }
            .take(topK)
            .toList()
        ranked
    }

    suspend fun deleteSource(type: String, id: Long) = dao.deleteSource(type, id)
    suspend fun purgeOlderThan(beforeMs: Long) = dao.purgeOlderThan(beforeMs)
    suspend fun totalRows(): Int = dao.count()
    suspend fun clear() = dao.clear()

    // ---- math + serialisation ----

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        // Vectors are already L2-normalised in MiniLMEmbedder.l2Normalize
        // so cosine collapses to a dot product. Keep the full formula as
        // a fallback for rows written before normalisation existed.
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in a.indices) {
            val x = a[i].toDouble()
            val y = b[i].toDouble()
            dot += x * y
            na += x * x
            nb += y * y
        }
        if (na <= 1e-9 || nb <= 1e-9) return 0f
        return (dot / (Math.sqrt(na) * Math.sqrt(nb))).toFloat()
    }

    private fun floatsToBytes(v: FloatArray): ByteArray {
        val buf = ByteBuffer.allocate(v.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (f in v) buf.putFloat(f)
        return buf.array()
    }

    private fun bytesToFloats(bytes: ByteArray): FloatArray {
        if (bytes.size % 4 != 0) return FloatArray(0)
        val out = FloatArray(bytes.size / 4)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in out.indices) out[i] = buf.float
        return out
    }
}
