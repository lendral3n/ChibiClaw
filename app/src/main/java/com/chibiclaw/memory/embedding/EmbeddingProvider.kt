package com.chibiclaw.memory.embedding

import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * EmbeddingProvider — generate 384-dim FloatArray dari teks pakai
 * multilingual-e5-small ONNX INT8.
 *
 * Phase 1 sub-milestone: implementasi sub-ideal (hash-based dummy) supaya
 * MemoryStore + ContextBuilder bisa berfungsi sambil menunggu:
 *  1. Model file `e5_small_q8.onnx` tersedia di assets/models/
 *  2. Tokenizer binding (HuggingFace tokenizer Kotlin atau bundled BPE)
 *  3. ONNX Runtime init + inference path
 *
 * Setelah 3 hal di atas siap, replace [encode] internal dengan actual ONNX call.
 * Lihat docs/architecture/16-memory-system.md section EmbeddingProvider.
 */
@Singleton
class EmbeddingProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mutex = Mutex()
    @Volatile private var initialized = false

    suspend fun init() = mutex.withLock {
        if (initialized) return@withLock
        // Phase 1 placeholder: kalau model file exists, init ONNX session.
        // Sementara langsung mark initialized untuk hash-based fallback.
        initialized = true
        Timber.i("EmbeddingProvider initialized (Phase 1 fallback hash-based)")
    }

    suspend fun encode(text: String): FloatArray = mutex.withLock {
        if (!initialized) init()
        // TODO Phase 1 sub-milestone: real ONNX inference.
        // Fallback: deterministic hash-based pseudo-embedding (bukan semantic,
        // tapi MemoryStore tetap bisa dedupe + similarity sederhana).
        hashEmbedding("passage: $text")
    }

    suspend fun encodeQuery(query: String): FloatArray = mutex.withLock {
        if (!initialized) init()
        hashEmbedding("query: $query")
    }

    /**
     * Pseudo-embedding via SHA-256 + sliding hash → 384-dim normalized float.
     *
     * Bukan semantic, tapi unique per teks. MemoryStore similarity akan
     * miss real semantic match sampai ONNX inference live — itu trade-off
     * Phase 1 conscious.
     */
    private fun hashEmbedding(text: String): FloatArray {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(text.toByteArray(Charsets.UTF_8))
        // SHA-256 = 32 bytes. Expand to 384 dim by hashing rounds.
        val result = FloatArray(EMBED_DIM)
        var seed = bytes
        var offset = 0
        while (offset < EMBED_DIM) {
            for (b in seed) {
                if (offset >= EMBED_DIM) break
                // Map byte (-128..127) → float (-1..1)
                result[offset] = b.toFloat() / 128f
                offset++
            }
            // Re-hash for next round
            seed = md.digest(seed)
        }
        return normalize(result)
    }

    private fun normalize(vec: FloatArray): FloatArray {
        var sum = 0.0
        for (v in vec) sum += v * v
        val norm = sqrt(sum).toFloat().coerceAtLeast(1e-8f)
        return FloatArray(vec.size) { vec[it] / norm }
    }

    companion object {
        const val EMBED_DIM = 384
    }
}

/**
 * Convert FloatArray ↔ ByteArray (little-endian) untuk persist di Room BLOB.
 */
fun FloatArray.toEmbeddingBlob(): ByteArray {
    val buf = ByteBuffer.allocate(this.size * 4).order(ByteOrder.LITTLE_ENDIAN)
    this.forEach { buf.putFloat(it) }
    return buf.array()
}

fun ByteArray.toEmbeddingArray(): FloatArray {
    val buf = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
    return FloatArray(this.size / 4) { buf.float }
}

/**
 * Cosine similarity (assume both vectors already L2-normalized → dot product).
 */
fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    require(a.size == b.size)
    var dot = 0f
    for (i in a.indices) dot += a[i] * b[i]
    return dot
}
