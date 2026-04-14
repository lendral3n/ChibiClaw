package com.chibiclaw.memory.vector

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.LongBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Phase 3.5 — sentence embedder backed by ONNX Runtime Mobile and the
 * quantized `all-MiniLM-L6-v2` model (~23 MB, 384 dims, mean-pooled).
 *
 * The model and tokenizer vocab are shipped as assets:
 *
 *   assets/
 *     models/
 *       minilm_l6_v2_quant.onnx
 *       minilm_vocab.txt
 *
 * On first use we copy the .onnx out to the app's cacheDir (ORT needs
 * a real file path on-disk) and build an OrtSession lazily. The
 * session is reused across calls — OrtSession is thread-safe for
 * inference but we mutex around encode() anyway to keep the tokenizer
 * buffers deterministic under concurrent calls.
 *
 * If the model file is missing (user hasn't downloaded yet) the
 * embedder reports isReady()=false and callers fall back to the
 * plain LIKE search already in [MemoryManager.searchMemory].
 */
@Singleton
class MiniLMEmbedder @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val mutex = Mutex()
    @Volatile private var env: OrtEnvironment? = null
    @Volatile private var session: OrtSession? = null
    @Volatile private var tokenizer: BertTokenizer? = null
    @Volatile private var inputNames: List<String> = emptyList()
    @Volatile private var loadAttempted = false
    @Volatile private var ready = false

    val dim: Int = 384

    fun isReady(): Boolean = ready

    /**
     * Encode a list of strings into normalised 384-dim embeddings.
     * Returns an empty list if the model isn't available — callers
     * must handle that as the "vector search disabled" case.
     */
    suspend fun encode(texts: List<String>): List<FloatArray> = withContext(Dispatchers.Default) {
        if (texts.isEmpty()) return@withContext emptyList()
        ensureLoaded()
        val s = session ?: return@withContext emptyList()
        val tok = tokenizer ?: return@withContext emptyList()
        val envLocal = env ?: return@withContext emptyList()

        mutex.withLock {
            texts.map { text ->
                encodeOne(envLocal, s, tok, text)
            }
        }
    }

    suspend fun encodeOne(text: String): FloatArray? {
        val list = encode(listOf(text))
        return list.firstOrNull()
    }

    private fun encodeOne(
        env: OrtEnvironment,
        session: OrtSession,
        tok: BertTokenizer,
        text: String
    ): FloatArray {
        val enc = tok.encode(text, MAX_TOKENS)
        val shape = longArrayOf(1, enc.inputIds.size.toLong())
        val ids = OnnxTensor.createTensor(env, LongBuffer.wrap(enc.inputIds), shape)
        val mask = OnnxTensor.createTensor(env, LongBuffer.wrap(enc.attentionMask), shape)
        val typeIds = OnnxTensor.createTensor(env, LongBuffer.wrap(enc.tokenTypeIds), shape)

        // Different export pipelines use slightly different input names
        // ("input_ids" vs "input"). We resolved the real names at load-
        // time so we can feed whichever combination the model wants.
        val feed = linkedMapOf<String, OnnxTensor>()
        inputNames.forEach { name ->
            when (name) {
                "input_ids", "inputs", "input" -> feed[name] = ids
                "attention_mask" -> feed[name] = mask
                "token_type_ids" -> feed[name] = typeIds
            }
        }
        // Fallback if the model only accepts ids + mask
        if (feed.isEmpty()) {
            feed["input_ids"] = ids
            feed["attention_mask"] = mask
        }

        val result = session.run(feed)
        try {
            val raw = result[0].value
            // Output shape: either [1, seq_len, 384] (token embeddings,
            // need mean-pooling) or [1, 384] (already pooled).
            val pooled = when (raw) {
                is Array<*> -> meanPoolFromTokens(raw, enc.attentionMask)
                else -> floatArrayOf()
            }
            return l2Normalize(pooled)
        } finally {
            result.close()
            ids.close()
            mask.close()
            typeIds.close()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun meanPoolFromTokens(raw: Array<*>, mask: LongArray): FloatArray {
        // raw is Array<Array<FloatArray>>: [batch][seq][dim]
        val batch = raw as? Array<Array<FloatArray>> ?: return FloatArray(0)
        if (batch.isEmpty()) return FloatArray(0)
        val first = batch[0]
        if (first.isEmpty()) return FloatArray(0)
        val d = first[0].size
        if (d != dim) return meanPoolDirect(first) // shape is already pooled
        val out = FloatArray(d)
        var count = 0f
        for ((idx, token) in first.withIndex()) {
            val m = mask.getOrNull(idx) ?: 0L
            if (m == 0L) continue
            for (i in 0 until d) out[i] += token[i]
            count += 1f
        }
        if (count > 0f) for (i in 0 until d) out[i] /= count
        return out
    }

    @Suppress("UNCHECKED_CAST")
    private fun meanPoolDirect(any: Any?): FloatArray {
        // Model that returns already-pooled embeddings [1, 384]
        val batch = any as? Array<FloatArray> ?: return FloatArray(0)
        return batch.firstOrNull() ?: FloatArray(0)
    }

    private fun l2Normalize(v: FloatArray): FloatArray {
        if (v.isEmpty()) return v
        var sum = 0.0
        for (f in v) sum += f * f
        val norm = sqrt(sum).toFloat()
        if (norm <= 1e-9f) return v
        val out = FloatArray(v.size)
        for (i in v.indices) out[i] = v[i] / norm
        return out
    }

    @Synchronized
    private fun ensureLoaded() {
        if (ready || loadAttempted) return
        loadAttempted = true
        try {
            val modelFile = copyAssetIfNeeded(MODEL_ASSET, MODEL_FILE)
            val vocabLines = runCatching { context.assets.open(VOCAB_ASSET).bufferedReader().readLines() }.getOrNull()
                ?: run {
                    // Model is present but vocab missing — still unusable.
                    return
                }
            val envLocal = OrtEnvironment.getEnvironment()
            val opts = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            val sessionLocal = envLocal.createSession(modelFile.absolutePath, opts)
            env = envLocal
            session = sessionLocal
            inputNames = sessionLocal.inputNames.toList()
            tokenizer = BertTokenizer(vocabLines)
            ready = true
        } catch (_: Throwable) {
            // Missing model / bad file / native load failure — stay
            // unready; vector search will silently degrade to LIKE.
            ready = false
        }
    }

    private fun copyAssetIfNeeded(assetPath: String, outName: String): File {
        val out = File(context.cacheDir, outName)
        if (out.exists() && out.length() > 1024) return out
        context.assets.open(assetPath).use { input ->
            FileOutputStream(out).use { fo ->
                input.copyTo(fo)
            }
        }
        return out
    }

    companion object {
        private const val MODEL_ASSET = "models/minilm_l6_v2_quant.onnx"
        private const val VOCAB_ASSET = "models/minilm_vocab.txt"
        private const val MODEL_FILE = "minilm_l6_v2_quant.onnx"
        private const val MAX_TOKENS = 128
    }
}
