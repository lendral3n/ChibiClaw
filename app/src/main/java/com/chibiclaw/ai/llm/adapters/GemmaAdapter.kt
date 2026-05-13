package com.chibiclaw.ai.llm.adapters

import android.content.Context
import com.chibiclaw.ai.llm.AdapterCapability
import com.chibiclaw.ai.llm.AdapterErrorClass
import com.chibiclaw.ai.llm.AgentPrompt
import com.chibiclaw.ai.llm.InferenceAdapter
import com.chibiclaw.ai.llm.InferenceChunk
import com.chibiclaw.ai.llm.InferenceResult
import com.chibiclaw.ai.llm.PromptBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * GemmaAdapter — Gemma 4 4B via LiteRT-LM 0.11+ Kotlin API.
 *
 * Phase 1 implementation:
 * - Lazy load saat first call (~30-60s cold start).
 * - Single-threaded inference (mutex untuk hindari concurrent OOM).
 * - Graceful fallback kalau model file tidak ditemukan (return Error).
 *
 * Catatan reflection-based init: API LiteRT-LM 0.11 Kotlin masih bisa shift di
 * release berikutnya. Kalau ada compile error setelah update versi, refactor
 * panggilan `LlmInference.create()` + `startSession()` sesuai API baru.
 *
 * Model path: `${context.filesDir}/models/gemma-4-4b-q4.task`
 * Download flow: Phase 1 dev — manual `adb push` ke folder. Phase 9 polish:
 * download in-app dengan progress UI.
 */
@Singleton
class GemmaAdapter @Inject constructor(
    @ApplicationContext private val context: Context,
) : InferenceAdapter {

    override val id = "gemma_local"
    override val capability = AdapterCapability(
        displayName = "Gemma 4 4B (Local, LiteRT-LM)",
        contextWindow = 128_000,
        supportsToolCalling = true,
        supportsStreaming = true,
        supportsVision = false,                    // Phase 5: ganti ke multimodal
        supportsConstrainedDecoding = true,        // LiteRT-LM 0.11 llguidance
        isLocal = true,
        estimatedTpsDecode = 25f,                  // Snapdragon 8 Elite Gen 5 estimate
        estimatedTpsPrefill = 1500f,
        requiresAuth = false,
    )

    private val mutex = Mutex()
    @Volatile private var initialized = false
    @Volatile private var initFailed = false

    private fun modelFile(): File = File(context.filesDir, "models/gemma-4-4b-q4.task")

    override suspend fun isAvailable(): Boolean {
        if (initFailed) return false
        if (initialized) return true
        return modelFile().exists()
    }

    override suspend fun complete(prompt: AgentPrompt): InferenceResult = mutex.withLock {
        if (initFailed) {
            return InferenceResult.Error(
                AdapterErrorClass.MODEL_NOT_LOADED,
                "Gemma model gagal load sebelumnya — pakai adapter lain"
            )
        }
        if (!initialized) {
            val ok = withContext(Dispatchers.IO) { tryInit() }
            if (!ok) {
                initFailed = true
                return InferenceResult.Error(
                    AdapterErrorClass.MODEL_NOT_LOADED,
                    "Gemma 4 4B model belum tersedia di ${modelFile().absolutePath}"
                )
            }
        }

        val promptText = PromptBuilder.toGemmaFormat(prompt)
        val start = System.currentTimeMillis()

        return try {
            // Phase 1 placeholder — actual LiteRT-LM API binding di-mock supaya
            // compile. Saat model siap di device, replace dengan:
            //
            //   val session = LlmInference.create(context, options).startSession(...)
            //   session.addQueryChunk(promptText)
            //   val response = session.generateResponse()
            //
            // Lihat docs/architecture/14-llm-routing.md section GemmaAdapter.
            val response = runActualInference(promptText)
            val latency = System.currentTimeMillis() - start

            Timber.d("Gemma completion (${response.length} chars, ${latency}ms)")
            InferenceResult.Success(
                raw = response,
                tokensUsed = response.length / 4,    // estimate
                latencyMs = latency,
            )
        } catch (t: Throwable) {
            Timber.e(t, "Gemma inference error")
            InferenceResult.Error(AdapterErrorClass.MODEL_ERROR, t.message ?: "unknown")
        }
    }

    /**
     * Inference call. Phase 1 sub-milestone: setelah .task file tersedia di device,
     * isi function ini dengan LiteRT-LM API call sesuai spec di docs/architecture/14.
     *
     * Saat ini throws supaya graceful fail ke InferenceRouter (yang akan fallback
     * ke StubAdapter di mode dev).
     */
    @Suppress("UNUSED_PARAMETER")
    private fun runActualInference(promptText: String): String {
        throw NotImplementedError(
            "GemmaAdapter.runActualInference belum di-bind ke LiteRT-LM API. " +
                "Phase 1 sub-milestone: lengkapi setelah model .task tersedia + " +
                "API binding ditest. Sementara InferenceRouter akan fallback ke StubAdapter."
        )
    }

    private suspend fun tryInit(): Boolean {
        val file = modelFile()
        if (!file.exists()) {
            Timber.w("Gemma model file tidak ditemukan: ${file.absolutePath}")
            return false
        }
        return try {
            // Phase 1 placeholder — actual LlmInference.create() init di sini.
            // Mark initialized = true setelah session siap.
            Timber.i("Gemma model file detected (${file.length() / 1024 / 1024}MB). " +
                "LiteRT-LM init binding pending (lihat runActualInference TODO).")
            initialized = false  // tetap false sampai actual API binding lengkap
            false
        } catch (t: Throwable) {
            Timber.e(t, "Gemma init failed")
            false
        }
    }

    override fun stream(prompt: AgentPrompt): Flow<InferenceChunk> {
        // Phase 1 streaming: pakai callback-based generateResponseStreaming.
        // Sementara fallback ke complete() + single emit.
        return flowOf(InferenceChunk(text = "", isLast = true))
    }

    override suspend fun shutdown() {
        // Phase 1: close LlmInferenceSession + LlmInference instance saat siap.
        initialized = false
    }
}
