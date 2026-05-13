package com.chibiclaw.voice.emotion

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TextEmotionClassifier — roberta-base-go_emotions ONNX INT8 (~125MB),
 * multi-label classification untuk 28 emotion.
 *
 * Output: Map<emotion_label, probability_0_to_1>. LLM dapat top-3 emotion
 * dengan prob > threshold sebagai context.
 *
 * Status Phase 2: reflection-based init + lookup fallback rule-based (untuk
 * dev) kalau model belum ada.
 */
@Singleton
class TextEmotionClassifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mutex = Mutex()
    @Volatile private var ortSession: Any? = null
    @Volatile private var initFailed: Boolean = false

    suspend fun init() = mutex.withLock {
        if (ortSession != null || initFailed) return@withLock
        initFailed = !tryInitOnnx()
        if (initFailed) {
            Timber.w("TextEmotionClassifier: ONNX init gagal, pakai keyword-based fallback")
        }
    }

    suspend fun classify(text: String): Map<String, Float> {
        if (ortSession == null && !initFailed) init()
        return if (ortSession != null) {
            runCatching { runOnnx(text) }.getOrElse { e ->
                Timber.w(e, "Text emotion inference failed")
                fallbackKeyword(text)
            }
        } else {
            fallbackKeyword(text)
        }
    }

    private fun tryInitOnnx(): Boolean {
        val modelFile = File(context.filesDir, "models/roberta_goemotions_q8.onnx")
        val modelBytes = if (modelFile.exists()) {
            modelFile.readBytes()
        } else {
            runCatching {
                context.assets.open("models/roberta_goemotions_q8.onnx").use { it.readBytes() }
            }.getOrElse {
                Timber.w("roberta_goemotions_q8.onnx tidak ditemukan")
                return false
            }
        }
        return try {
            val ortEnvClass = Class.forName("ai.onnxruntime.OrtEnvironment")
            val env = ortEnvClass.getMethod("getEnvironment").invoke(null)
            ortSession = ortEnvClass
                .getMethod("createSession", ByteArray::class.java)
                .invoke(env, modelBytes)
            Timber.i("TextEmotionClassifier ONNX ready (${modelBytes.size / 1024 / 1024}MB)")
            true
        } catch (t: Throwable) {
            Timber.w(t, "TextEmotionClassifier ONNX init failed")
            false
        }
    }

    /**
     * Sub-milestone: tokenize text (BPE), run inference, sigmoid output.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun runOnnx(text: String): Map<String, Float> {
        throw NotImplementedError(
            "RoBERTa GoEmotions ONNX inference binding belum di-implement. " +
                "Sub-milestone: tokenize, build input_ids + attention_mask tensors, " +
                "run session, sigmoid logits → label probs."
        )
    }

    /**
     * Keyword fallback. Bukan accurate, tapi cukup untuk dev mode signal.
     */
    private fun fallbackKeyword(text: String): Map<String, Float> {
        val t = text.lowercase()
        val hits = mutableMapOf<String, Float>()
        // Indonesia keywords sederhana
        if (t.contains(Regex("\\bseneng\\b|\\bsenang\\b|\\bgembira\\b|\\bhebat\\b|\\bbahagia\\b|\\bmantap\\b|haha"))) hits["joy"] = 0.7f
        if (t.contains(Regex("\\bsedih\\b|\\bcape\\b|\\bcapek\\b|\\bkecewa\\b|hiks"))) hits["sadness"] = 0.65f
        if (t.contains(Regex("\\bmarah\\b|kesel|kesal|annoying|geram"))) hits["anger"] = 0.6f
        if (t.contains(Regex("\\bkaget\\b|wah!|surprise"))) hits["surprise"] = 0.55f
        if (t.contains(Regex("\\btakut\\b|cemas|khawatir"))) hits["fear"] = 0.5f
        if (t.contains(Regex("makasih|thanks|terima kasih"))) hits["gratitude"] = 0.6f
        if (hits.isEmpty()) hits["neutral"] = 0.7f
        return hits
    }
}
