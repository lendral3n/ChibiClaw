package com.chibiclaw.memory.embedding

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * EmbeddingProvider — multilingual-e5-small ONNX INT8 (384-dim).
 *
 * Status Phase 1 (audit 2026-05-13):
 *  - Concrete ONNX Runtime skeleton ada via reflection (graceful kalau model
 *    + tokenizer belum tersedia di assets).
 *  - Fallback hash-based pseudo-embedding kalau ONNX init gagal → MemoryStore
 *    tetap berfungsi, tapi similarity bukan semantic real.
 *
 * Path model expected:
 *   - assets/models/e5_small_q8.onnx (~50 MB)
 *   - tokenizer.json + library binding (Phase 1 sub-milestone)
 *
 * Phase 1 sub-milestone TODO:
 *  1. Push e5_small_q8.onnx ke assets (atau download in-app)
 *  2. Tokenizer binding: ai.djl.huggingface.tokenizers (~12 MB) atau bundled BPE
 *  3. Implement [encodeOnnx] real (sekarang masih NotImplementedError)
 */
@Singleton
class EmbeddingProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mutex = Mutex()
    @Volatile private var initialized: Boolean = false
    @Volatile private var initFailed: Boolean = false
    @Volatile private var ortSession: Any? = null

    suspend fun init() = mutex.withLock {
        if (initialized || initFailed) return@withLock
        initialized = tryInitOnnx()
        initFailed = !initialized
        if (initFailed) {
            Timber.w("EmbeddingProvider: ONNX init gagal, fallback ke hash-based pseudo-embedding")
        }
    }

    suspend fun encode(text: String): FloatArray {
        if (!initialized && !initFailed) init()
        return if (initialized) {
            runCatching { encodeOnnx("passage: $text") }
                .getOrElse { encodeFallback("passage: $text") }
        } else {
            encodeFallback("passage: $text")
        }
    }

    suspend fun encodeQuery(query: String): FloatArray {
        if (!initialized && !initFailed) init()
        return if (initialized) {
            runCatching { encodeOnnx("query: $query") }
                .getOrElse { encodeFallback("query: $query") }
        } else {
            encodeFallback("query: $query")
        }
    }

    /**
     * ONNX init via reflection — toleran terhadap absence onnxruntime di runtime.
     */
    private fun tryInitOnnx(): Boolean {
        return try {
            val modelFile = File(context.filesDir, "models/e5_small_q8.onnx")
            val modelBytes = if (modelFile.exists()) {
                modelFile.readBytes()
            } else {
                runCatching {
                    context.assets.open("models/e5_small_q8.onnx").use { it.readBytes() }
                }.getOrElse {
                    Timber.w("e5_small_q8.onnx tidak ditemukan di filesDir atau assets")
                    return false
                }
            }

            val ortEnvClass = Class.forName("ai.onnxruntime.OrtEnvironment")
            val env = ortEnvClass.getMethod("getEnvironment").invoke(null)
            val session = ortEnvClass.getMethod("createSession", ByteArray::class.java)
                .invoke(env, modelBytes)
            ortSession = session

            Timber.i("EmbeddingProvider ONNX session ready (model ${modelBytes.size / 1024 / 1024}MB)")
            true
        } catch (t: Throwable) {
            Timber.w(t, "EmbeddingProvider ONNX init failed")
            false
        }
    }

    /**
     * Real ONNX inference. Phase 1 sub-milestone implementation.
     *
     * Pipeline:
     *   tokenize(prefixedText) → input_ids[seq_len], attention_mask[seq_len]
     *   ort.run({input_ids, attention_mask}) → last_hidden_state[1, seq_len, 384]
     *   mean_pool over attention_mask → [384]
     *   L2 normalize → [384]
     *
     * Tokenizer dependency belum di-enable. Phase 1 audit fallback ke hash-based.
     */
    private fun encodeOnnx(prefixedText: String): FloatArray {
        throw NotImplementedError(
            "ONNX encode butuh tokenizer binding. Phase 1 sub-milestone: bind " +
                "ai.djl.huggingface.tokenizers atau bundled BPE. Fallback hash-based."
        )
    }

    /**
     * Hash-based pseudo-embedding via SHA-256. **Bukan semantic** — deterministic
     * per teks, tapi tidak capture meaning. MemoryStore similarity search akan
     * miss real semantic match sampai ONNX live.
     *
     * Tetap L2-normalized supaya cosine sim formula konsisten dengan future ONNX.
     */
    private fun encodeFallback(text: String): FloatArray {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        var seed = md.digest(text.toByteArray(Charsets.UTF_8))
        val result = FloatArray(EMBED_DIM)
        var offset = 0
        while (offset < EMBED_DIM) {
            for (b in seed) {
                if (offset >= EMBED_DIM) break
                result[offset] = b.toFloat() / 128f
                offset++
            }
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
