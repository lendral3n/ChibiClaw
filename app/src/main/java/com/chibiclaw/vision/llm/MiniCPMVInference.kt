package com.chibiclaw.vision.llm

import android.content.Context
import android.graphics.Bitmap
import com.chibiclaw.vision.screenshot.ImageProcessor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MiniCPMVInference @Inject constructor(
    @ApplicationContext private val context: Context,
    private val processor: ImageProcessor,
) {

    @Volatile private var sessionRef: Any? = null
    @Volatile private var initFailed: Boolean = false

    private fun modelFile(): File = File(context.filesDir, "models/minicpm-v-4-6-q4.gguf")
    private fun mmprojFile(): File = File(context.filesDir, "models/mmproj-f16.gguf")

    fun isAvailable(): Boolean {
        if (initFailed) return false
        if (sessionRef != null) return true
        return modelFile().exists() && mmprojFile().exists()
    }

    suspend fun infer(prompt: String, image: Bitmap, maxTokens: Int = 512): String? {
        if (initFailed) return null
        if (sessionRef == null) {
            val ok = withContext(Dispatchers.IO) { tryInit() }
            if (!ok) {
                initFailed = true
                return null
            }
        }
        val sess = sessionRef ?: return null
        val resized = processor.resize(image, ImageProcessor.MAX_DIM_DEFAULT)
        val bytes = processor.toJpegBytes(resized)
        return withContext(Dispatchers.IO) { invokeInference(sess, prompt, bytes, maxTokens) }
    }

    private fun tryInit(): Boolean {
        val model = modelFile()
        val mmproj = mmprojFile()
        if (!model.exists() || !mmproj.exists()) return false
        return try {
            val cls = Class.forName("com.chibiclaw.nativellm.LlamaCppMm")
            val openMethod = cls.getMethod("open", String::class.java, String::class.java, Int::class.javaPrimitiveType)
            sessionRef = openMethod.invoke(null, model.absolutePath, mmproj.absolutePath, 4096)
            true
        } catch (t: Throwable) {
            Timber.w(t, "MiniCPM-V init failed")
            false
        }
    }

    private fun invokeInference(sess: Any, prompt: String, imageBytes: ByteArray, maxTokens: Int): String? {
        return try {
            val method = sess.javaClass.getMethod("generate", String::class.java, ByteArray::class.java, Int::class.javaPrimitiveType)
            method.invoke(sess, prompt, imageBytes, maxTokens) as? String
        } catch (t: Throwable) {
            Timber.w(t, "MiniCPM-V generate failed")
            null
        }
    }

    fun shutdown() {
        runCatching { sessionRef?.javaClass?.getMethod("close")?.invoke(sessionRef) }
        sessionRef = null
    }
}
