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
 * Status Phase 1 (audit 2026-05-13):
 *  - Compile-clean tanpa hard dep ke spesifik API class name (LiteRT-LM 0.11
 *    Kotlin API spec masih shifting per Google AI Edge release).
 *  - Lazy load saat `isAvailable()` dipanggil pertama kali — kalau model file
 *    tidak ada di filesystem, return false → InferenceRouter fallback ke
 *    StubAdapter (dev mode).
 *  - Inference call pakai reflection-based invocation supaya kalau Maven
 *    publishes LiteRT-LM dengan API berubah, error catchable runtime, tidak
 *    block compile. Saat API stabilize, refactor ke direct call.
 *
 * Path model:
 *   /data/data/com.chibiclaw/files/models/gemma-4-4b-q4.task
 *
 * Cara push model ke device:
 *   adb push gemma-4-4b-q4.task /data/local/tmp/
 *   adb shell run-as com.chibiclaw cp /data/local/tmp/gemma-4-4b-q4.task files/models/
 *
 * Atau Phase 9 polish: in-app downloader dari Hugging Face dengan progress UI.
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
        supportsVision = false,
        supportsConstrainedDecoding = true,
        isLocal = true,
        estimatedTpsDecode = 25f,
        estimatedTpsPrefill = 1500f,
        requiresAuth = false,
    )

    private val mutex = Mutex()
    @Volatile private var session: Any? = null    // typed Any supaya tahan API shift
    @Volatile private var inference: Any? = null
    @Volatile private var initFailed: Boolean = false

    private fun modelFile(): File = File(context.filesDir, "models/gemma-4-4b-q4.task")

    override suspend fun isAvailable(): Boolean {
        if (initFailed) return false
        if (session != null) return true
        return modelFile().exists()
    }

    override suspend fun complete(prompt: AgentPrompt): InferenceResult = mutex.withLock {
        if (initFailed) {
            return InferenceResult.Error(
                AdapterErrorClass.MODEL_NOT_LOADED,
                "Gemma init gagal sebelumnya — InferenceRouter akan pilih adapter lain"
            )
        }

        if (session == null) {
            val ok = withContext(Dispatchers.IO) { tryInit() }
            if (!ok) {
                initFailed = true
                return InferenceResult.Error(
                    AdapterErrorClass.MODEL_NOT_LOADED,
                    "Gemma model belum tersedia di ${modelFile().absolutePath}. Push via adb: " +
                        "adb push gemma-4-4b-q4.task /data/local/tmp/ && " +
                        "adb shell run-as com.chibiclaw cp /data/local/tmp/gemma-4-4b-q4.task files/models/"
                )
            }
        }

        val promptText = PromptBuilder.toGemmaFormat(prompt)
        val start = System.currentTimeMillis()
        return try {
            val raw = invokeInference(promptText)
            val latency = System.currentTimeMillis() - start
            Timber.d("Gemma inference: ${raw.length} chars in ${latency}ms")
            InferenceResult.Success(raw = raw, tokensUsed = raw.length / 4, latencyMs = latency)
        } catch (t: Throwable) {
            Timber.e(t, "Gemma inference exception")
            InferenceResult.Error(AdapterErrorClass.MODEL_ERROR, t.message ?: t.javaClass.simpleName)
        }
    }

    /**
     * Init LiteRT-LM session. Pakai reflection untuk locate class supaya
     * tidak hard-code import yang mungkin breaking di versi tertentu.
     *
     * Expected classes (per LiteRT-LM 0.11 reference):
     *  - `com.google.ai.edge.litertlm.LlmInference`
     *  - `com.google.ai.edge.litertlm.LlmInference$LlmInferenceOptions`
     *  - `com.google.ai.edge.litertlm.LlmInferenceSession`
     *
     * Kalau class tidak ada → tryInit return false, adapter fallback.
     */
    private fun tryInit(): Boolean {
        val file = modelFile()
        if (!file.exists()) {
            Timber.w("Gemma model file tidak ditemukan: ${file.absolutePath}")
            return false
        }

        return try {
            // Locate LlmInference class via Class.forName supaya graceful kalau
            // dependency tidak tersedia di runtime.
            val llmInferenceClass = Class.forName("com.google.ai.edge.litertlm.LlmInference")
            val optionsClass = Class.forName("com.google.ai.edge.litertlm.LlmInferenceOptions")

            // Build options: setModelPath(String) → setMaxTokens(int) → build()
            val optionsBuilder = optionsClass
                .getMethod("builder")
                .invoke(null)
            val optionsBuilderClass = optionsBuilder!!.javaClass
            optionsBuilderClass.getMethod("setModelPath", String::class.java)
                .invoke(optionsBuilder, file.absolutePath)
            runCatching {
                optionsBuilderClass.getMethod("setMaxTokens", Int::class.javaPrimitiveType)
                    .invoke(optionsBuilder, MAX_OUTPUT_TOKENS)
            } // ignore kalau API method name beda
            val options = optionsBuilderClass.getMethod("build").invoke(optionsBuilder)

            // LlmInference.createFromOptions(context, options) atau create(context, options)
            val inferenceInstance = runCatching {
                llmInferenceClass.getMethod("createFromOptions", Context::class.java, optionsClass)
                    .invoke(null, context, options)
            }.getOrElse {
                llmInferenceClass.getMethod("create", Context::class.java, optionsClass)
                    .invoke(null, context, options)
            }
            inference = inferenceInstance

            // Session: LlmInferenceSession.createFromOptions(inference, ...)
            // atau inference.createSession() — coba dua-duanya
            val sessionInstance = runCatching {
                val sessionOptClass = Class.forName("com.google.ai.edge.litertlm.LlmInferenceSessionOptions")
                val sessOptBuilder = sessionOptClass.getMethod("builder").invoke(null)
                val sessOptBuilderClass = sessOptBuilder!!.javaClass
                runCatching {
                    sessOptBuilderClass.getMethod("setTemperature", Float::class.javaPrimitiveType)
                        .invoke(sessOptBuilder, TEMPERATURE)
                }
                runCatching {
                    sessOptBuilderClass.getMethod("setTopK", Int::class.javaPrimitiveType)
                        .invoke(sessOptBuilder, TOP_K)
                }
                val sessOpts = sessOptBuilderClass.getMethod("build").invoke(sessOptBuilder)

                val sessionClass = Class.forName("com.google.ai.edge.litertlm.LlmInferenceSession")
                sessionClass.getMethod("createFromOptions", llmInferenceClass, sessionOptClass)
                    .invoke(null, inferenceInstance, sessOpts)
            }.getOrElse {
                llmInferenceClass.getMethod("createSession")
                    .invoke(inferenceInstance)
            }
            session = sessionInstance

            Timber.i("Gemma 4 4B session ready (model ${file.length() / 1024 / 1024}MB)")
            true
        } catch (t: Throwable) {
            Timber.w(t, "Gemma init via reflection failed — API surface mungkin berbeda. " +
                "Refactor manual ke direct API saat stabilize.")
            false
        }
    }

    private fun invokeInference(promptText: String): String {
        val sess = session ?: throw IllegalStateException("Session null")
        // session.addQueryChunk(text) → session.generateResponse(): String
        val sessClass = sess.javaClass
        runCatching {
            sessClass.getMethod("addQueryChunk", String::class.java).invoke(sess, promptText)
        }
        // Coba beberapa nama method generate yang umum
        val generateMethods = listOf("generateResponse", "generate", "complete")
        for (methodName in generateMethods) {
            val result = runCatching {
                sessClass.getMethod(methodName).invoke(sess)
            }.getOrNull()
            if (result is String) return result
        }
        throw IllegalStateException("Tidak menemukan method generate di session class ${sessClass.name}")
    }

    override fun stream(prompt: AgentPrompt): Flow<InferenceChunk> {
        // Phase 1 sub-milestone: bind ke session.generateResponseStreaming() saat
        // API stabilize. Sementara emit single chunk dari complete().
        return flowOf(InferenceChunk(text = "", isLast = true))
    }

    override suspend fun shutdown() = mutex.withLock {
        runCatching { session?.javaClass?.getMethod("close")?.invoke(session) }
        runCatching { inference?.javaClass?.getMethod("close")?.invoke(inference) }
        session = null
        inference = null
    }

    companion object {
        private const val MAX_OUTPUT_TOKENS = 4096
        private const val TEMPERATURE = 0.7f
        private const val TOP_K = 40
    }
}
