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
 * Wav2SmallEmotion — audeering/wav2small ONNX INT8 (~120KB) untuk audio emotion.
 *
 * Output: VAD vector continuous (valence, arousal, dominance) di range [-1, 1].
 *
 * Status Phase 2: reflection-based ONNX init (graceful kalau model belum
 * ada). Sub-milestone: bind real inference setelah model file di-push.
 *
 * Path: `assets/models/wav2small.onnx` atau `filesDir/models/wav2small.onnx`
 */
@Singleton
class Wav2SmallEmotion @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mutex = Mutex()
    @Volatile private var ortSession: Any? = null
    @Volatile private var initFailed: Boolean = false

    suspend fun init() = mutex.withLock {
        if (ortSession != null || initFailed) return@withLock
        initFailed = !tryInitOnnx()
        if (initFailed) {
            Timber.w("Wav2SmallEmotion: ONNX init gagal, audio emotion akan return null")
        }
    }

    /**
     * Analyze 3-second audio window → VAD vector. Return null kalau model belum
     * tersedia (LLM tetap dapat text emotion signal sebagai fallback).
     */
    suspend fun analyze(pcm: ShortArray): VadVector? {
        if (ortSession == null && !initFailed) init()
        if (ortSession == null) return null

        return try {
            runOnnx(pcm)
        } catch (t: Throwable) {
            Timber.w(t, "Wav2Small inference failed")
            null
        }
    }

    private fun tryInitOnnx(): Boolean {
        val modelFile = File(context.filesDir, "models/wav2small.onnx")
        val modelBytes = if (modelFile.exists()) {
            modelFile.readBytes()
        } else {
            runCatching {
                context.assets.open("models/wav2small.onnx").use { it.readBytes() }
            }.getOrElse {
                Timber.w("wav2small.onnx tidak ditemukan di filesDir atau assets")
                return false
            }
        }
        return try {
            val ortEnvClass = Class.forName("ai.onnxruntime.OrtEnvironment")
            val env = ortEnvClass.getMethod("getEnvironment").invoke(null)
            ortSession = ortEnvClass
                .getMethod("createSession", ByteArray::class.java)
                .invoke(env, modelBytes)
            Timber.i("Wav2Small ONNX ready (${modelBytes.size / 1024}KB)")
            true
        } catch (t: Throwable) {
            Timber.w(t, "Wav2Small ONNX init failed")
            false
        }
    }

    /**
     * Sub-milestone: bind real input tensor (float32 [1, samples] 16kHz mono),
     * run session, ekstrak output VAD vector.
     */
    private fun runOnnx(@Suppress("UNUSED_PARAMETER") pcm: ShortArray): VadVector {
        throw NotImplementedError(
            "Wav2Small ONNX inference binding belum di-implement. " +
                "Phase 2 sub-milestone: convert PCM short[] → float32[], create OnnxTensor, " +
                "run session, extract output VAD."
        )
    }
}
