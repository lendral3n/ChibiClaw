package com.chibiclaw.ai

import android.graphics.Bitmap
import android.util.Log
import com.chibiclaw.debug.DevLogger
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.tool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GemmaInference @Inject constructor(
    private val engineManager: GemmaEngineManager,
    private val devLogger: DevLogger
) {
    /**
     * Per-tier session state. LiteRT-LM only allows ONE Conversation per
     * Engine at a time, and with dual engines we now need two independent
     * session slots — one per tier. Each slot has its own owner-engine
     * reference (so we can detect a reload per-tier) and its own
     * "hasTools" flag (so a tools/no-tools rebuild on E2B doesn't trash
     * the E4B conversation).
     */
    private data class Session(
        var conversation: Conversation? = null,
        var ownerEngine: Engine? = null,
        var hasTools: Boolean = true
    )

    private val sessions = mutableMapOf<ModelTier, Session>(
        ModelTier.E4B to Session(),
        ModelTier.E2B to Session()
    )

    private fun sessionFor(tier: ModelTier): Session =
        sessions.getOrPut(tier) { Session() }

    /**
     * Called by GemmaEngineManager.unloadModel(tier) so we drop stale
     * references before the engine is closed under us. We do NOT try to
     * .close() the Conversation here because engine.close() already tears
     * it down, and touching it post-engine-close can crash native code.
     */
    fun onEngineUnloaded(tier: ModelTier) {
        val s = sessionFor(tier)
        if (s.conversation != null) {
            devLogger.i("INFERENCE", "[$tier] Engine unloading — dropping session reference")
        }
        s.conversation = null
        s.ownerEngine = null
        s.hasTools = true
    }

    /** Back-compat: unload both tiers. */
    fun onEngineUnloaded() {
        onEngineUnloaded(ModelTier.E4B)
        onEngineUnloaded(ModelTier.E2B)
    }

    /**
     * Explicitly resets the session for [tier] (closes the existing
     * Conversation) without touching the engine. Use this if the user
     * changes persona/skills and wants a fresh context, or when a native
     * error forces a rebuild.
     */
    fun resetSession(tier: ModelTier, reason: String) {
        val s = sessionFor(tier)
        s.conversation?.let { conv ->
            try {
                conv.close()
                devLogger.i("INFERENCE", "[$tier] Session closed ($reason)")
            } catch (e: Exception) {
                devLogger.w("INFERENCE", "[$tier] Session close failed: ${e.message}")
            }
        }
        s.conversation = null
        s.ownerEngine = null
    }

    fun resetSession(reason: String) {
        resetSession(ModelTier.E4B, reason)
        resetSession(ModelTier.E2B, reason)
    }

    /**
     * Closes every session whose ownerEngine === [engine], regardless of
     * which tier slot it's parked in. Fixes the "A session already exists"
     * FAILED_PRECONDITION when only one engine is loaded but the tier slots
     * have cross-piggybacked.
     *
     * Background: If the user only uploads one model but the router still
     * routes some commands to a different tier, the tier fallback in
     * [GemmaEngineManager.getEngine] silently reuses the single loaded
     * engine. Both sessions[E2B].conversation and sessions[E4B].conversation
     * can end up pinned to the same Engine instance. resetSession(tier) only
     * closes ONE of them — [closeAllSessionsOn] closes every Conversation
     * that's sitting on a given engine so createConversation() can succeed.
     */
    fun closeAllSessionsOn(engine: Engine, reason: String) {
        for ((tier, s) in sessions) {
            if (s.ownerEngine !== engine) continue
            val conv = s.conversation ?: continue
            try {
                conv.close()
                devLogger.i("INFERENCE", "[$tier] Session closed via closeAllSessionsOn ($reason)")
            } catch (e: Exception) {
                devLogger.w("INFERENCE", "[$tier] closeAllSessionsOn close failed: ${e.message}")
            }
            s.conversation = null
            s.ownerEngine = null
        }
    }

    /**
     * Lazily creates — or reuses — the session-long conversation for
     * [tier]. Must be called on the tier's engine dispatcher
     * ([GemmaEngineManager.dispatcherFor]).
     */
    private fun ensureConversation(
        tier: ModelTier,
        engine: Engine,
        systemPrompt: String,
        tools: ChibiClawTools?,
        useTools: Boolean
    ): Conversation {
        val s = sessionFor(tier)
        // Engine was recreated (reload / unload+load) — drop stale ref.
        if (s.ownerEngine !== engine) {
            if (s.conversation != null) {
                devLogger.i("INFERENCE", "[$tier] Engine ref changed — starting fresh session")
            }
            s.conversation = null
            s.ownerEngine = null
        }
        // Tools availability changed — we have to rebuild because tools are
        // pinned at ConversationConfig time.
        if (s.conversation != null && s.hasTools != useTools) {
            resetSession(tier, "tools mode changed to useTools=$useTools")
        }

        s.conversation?.let { return it }

        devLogger.i("INFERENCE", "[$tier] Creating new session (useTools=$useTools)")
        val sampler = SamplerConfig(temperature = 0.8, topK = 40, topP = 0.95)
        val config = if (useTools && tools != null) {
            ConversationConfig(
                systemInstruction = Contents.of(systemPrompt),
                samplerConfig = sampler,
                automaticToolCalling = true,
                tools = listOf(tool(tools))
            )
        } else {
            ConversationConfig(
                systemInstruction = Contents.of(systemPrompt),
                samplerConfig = sampler,
                automaticToolCalling = false
            )
        }
        // Belt-and-suspenders: before asking the native layer for a new
        // Conversation, make sure NO other Conversation is still clinging
        // to this engine via a different tier slot. See closeAllSessionsOn
        // docs for the full story.
        closeAllSessionsOn(engine, "ensure_pre_create_$tier")
        val conv = engine.createConversation(config)
        s.conversation = conv
        s.ownerEngine = engine
        s.hasTools = useTools
        devLogger.i("INFERENCE", "✓ [$tier] Session ready on ${Thread.currentThread().name}")
        return conv
    }

    fun sendMessage(
        userText: String,
        systemPrompt: String,
        tools: ChibiClawTools,
        tier: ModelTier = ModelTier.E4B
    ): Flow<String> {
        val engine = engineManager.getEngine(tier) ?: run {
            Log.e(TAG, "[$tier] Engine not ready")
            devLogger.e("INFERENCE", "[$tier] getEngine() returned null")
            return emptyFlow()
        }
        engineManager.resetIdleTimer()

        return flow {
            // Try the tool-enabled path first.
            //
            // KNOWN UPSTREAM ISSUE (LiteRT-LM 0.10.0):
            //   Gemma 3n sometimes emits its own native tool-call tokens
            //   (`<|tool_call|>...<|"|>`) instead of the JSON format LiteRT-LM's
            //   internal parser expects. When that happens the first token
            //   after the marker throws inside `JniMessageCallbackImpl` and
            //   the whole stream fails. We catch that below, drop the session
            //   (native state may be corrupted), and retry without tools —
            //   effectively degrading to plain-text generation for that turn.
            //
            // Additionally, any stray tool-call fragments that DO make it into
            // the stream as text are scrubbed via [sanitizeChunk] so the user
            // never sees `<|tool_call|>` garbage in the chat bubble.
            try {
                val conversation = ensureConversation(tier, engine, systemPrompt, tools, useTools = true)
                devLogger.i("INFERENCE", "[$tier] → sendMessageAsync (tools): \"${userText.take(60)}\"")
                conversation.sendMessageAsync(userText).collect { raw ->
                    val cleaned = sanitizeChunk(raw.toString())
                    if (cleaned.isNotEmpty()) emit(cleaned)
                }
                devLogger.i("INFERENCE", "[$tier] Stream complete")
                return@flow
            } catch (e: Exception) {
                if (isParserBug(e)) {
                    devLogger.w(
                        "INFERENCE",
                        "[$tier] LiteRT-LM 0.10.0 parser bug triggered — falling back to no-tools"
                    )
                } else {
                    devLogger.e("INFERENCE", "[$tier] Tools path failed: ${e::class.simpleName}: ${e.message}")
                }
                resetSession(tier, "tools path failed")
            }

            // Fallback: rebuild the session WITHOUT tools and retry once.
            devLogger.w("INFERENCE", "[$tier] Falling back to no-tools mode…")
            try {
                val conversation = ensureConversation(tier, engine, systemPrompt, tools = null, useTools = false)
                conversation.sendMessageAsync(userText).collect { raw ->
                    val cleaned = sanitizeChunk(raw.toString())
                    if (cleaned.isNotEmpty()) emit(cleaned)
                }
                devLogger.i("INFERENCE", "[$tier] No-tools stream complete")
            } catch (e: Exception) {
                resetSession(tier, "no-tools path failed")
                Log.e(TAG, "[$tier] All inference attempts failed: ${e.message}")
                devLogger.e("INFERENCE", "[$tier] All attempts failed: ${e::class.simpleName}: ${e.message}")
                emit("[ERROR] ${e.message}")
            }
        }.flowOn(engineManager.dispatcherFor(tier))
    }

    /**
     * Strips stray Gemma 3n tool-call markers from a text chunk before it
     * reaches the UI. LiteRT-LM 0.10.0's parser expects JSON but Gemma 3n
     * occasionally emits the native `<|tool_call|>…<|"|>` or a bare
     * `<start_of_turn>` token; if one of those leaks into the stream we
     * drop the fragment rather than show garbage to the user.
     *
     * We intentionally DO NOT try to re-interpret the stripped fragment as a
     * tool call here — the automatic tool caller inside LiteRT-LM has already
     * given up by that point. The parent [sendMessage] flow handles recovery
     * via the no-tools retry.
     */
    private fun sanitizeChunk(chunk: String): String {
        if (chunk.isEmpty()) return chunk
        var out = chunk
        // Full tool-call blocks that somehow slipped through. Try the strict
        // closed-form first, then the open-ended variant that just has a
        // <|tool_call|> prefix and no closing marker.
        out = TOOL_CALL_BLOCK_REGEX.replace(out, "")
        out = TOOL_CALL_OPEN_REGEX.replace(out, "")
        // Literal Gemma 3n control tokens that sometimes leak into the
        // streamed text. Strip every known variant + any other `<|xxx|>`
        // style control token (JSON payloads never look like that so this
        // is safe).
        out = out.replace("<|tool_call|>", "")
            .replace("<|/tool_call|>", "")
            .replace("<|end_tool_call|>", "")
            .replace("<|tool_response|>", "")
            .replace("<|/tool_response|>", "")
            .replace("<|\"|>", "")
            .replace("<|start_of_turn|>", "")
            .replace("<|end_of_turn|>", "")
            .replace("<start_of_turn>", "")
            .replace("<end_of_turn>", "")
        // Anything else shaped like <|...|> — final safety net.
        out = CONTROL_TOKEN_REGEX.replace(out, "")
        return out
    }

    /**
     * Detects the signature LiteRT-LM 0.10.0 parser failure so we can log it
     * cleanly and distinguish it from real crashes. The exception comes from
     * `JniMessageCallbackImpl` and its message typically mentions JSON /
     * tool-call parsing.
     */
    private fun isParserBug(e: Exception): Boolean {
        val msg = (e.message ?: "").lowercase()
        val stack = e.stackTrace.firstOrNull()?.className?.lowercase().orEmpty()
        return stack.contains("jnimessagecallback") ||
            msg.contains("tool_call") ||
            msg.contains("tool call") ||
            msg.contains("json")
    }

    /**
     * Real multimodal inference: packs [userText] and a JPEG-encoded
     * [screenshot] into one [Contents] and streams Gemma's response. Vision
     * is only on E4B — the tower isn't in E2B — so this method hard-pins
     * the tier to E4B regardless of routing decisions.
     *
     * We encode as JPEG q=85 rather than PNG because Gemma's image adapter
     * expects JPEG/PNG byte streams and JPEG is ~10x smaller, reducing JNI
     * copy overhead noticeably on 1080p screenshots.
     */
    fun sendMultimodal(
        userText: String,
        screenshot: Bitmap,
        systemPrompt: String,
        tools: ChibiClawTools? = null
    ): Flow<String> {
        val tier = ModelTier.E4B
        val engine = engineManager.getEngine(tier) ?: run {
            Log.e(TAG, "Engine not ready for vision")
            return emptyFlow()
        }
        engineManager.resetIdleTimer()

        return flow {
            val imageBytes = encodeBitmapAsJpeg(screenshot)
                ?: run {
                    emit("[ERROR] bitmap encoding failed")
                    return@flow
                }
            devLogger.i(
                "INFERENCE",
                "→ sendMultimodal: text=\"${userText.take(40)}\" image=${imageBytes.size / 1024}KB tools=${tools != null}"
            )

            val contents = Contents.of(
                Content.Text(userText),
                Content.ImageBytes(imageBytes)
            )

            // If caller didn't supply tools, go straight to vision-only mode —
            // we don't need the tool-calling machinery for "describe this image".
            val useToolsFirst = tools != null

            try {
                val conversation = ensureConversation(tier, engine, systemPrompt, tools, useTools = useToolsFirst)
                conversation.sendMessageAsync(contents).collect { raw ->
                    val cleaned = sanitizeChunk(raw.toString())
                    if (cleaned.isNotEmpty()) emit(cleaned)
                }
                devLogger.i("INFERENCE", "Multimodal stream complete")
            } catch (e: Exception) {
                if (isParserBug(e)) {
                    devLogger.w("INFERENCE", "Multimodal parser bug — retry no-tools")
                } else {
                    devLogger.e("INFERENCE", "Multimodal (tools) failed: ${e::class.simpleName}: ${e.message}")
                }
                resetSession(tier, "multimodal tools path failed")
                // Retry without tools — vision-only mode.
                try {
                    val conversation = ensureConversation(tier, engine, systemPrompt, tools = null, useTools = false)
                    conversation.sendMessageAsync(contents).collect { raw ->
                        val cleaned = sanitizeChunk(raw.toString())
                        if (cleaned.isNotEmpty()) emit(cleaned)
                    }
                    devLogger.i("INFERENCE", "Multimodal no-tools stream complete")
                } catch (e2: Exception) {
                    resetSession(tier, "multimodal no-tools failed")
                    Log.e(TAG, "Vision inference error: ${e2.message}")
                    emit("[ERROR] ${e2.message}")
                }
            }
        }.flowOn(engineManager.dispatcherFor(tier))
    }

    /**
     * Audio-in inference. [pcmOrWav] is a raw byte array — Gemma 3n E4B's
     * native audio tower accepts 16kHz mono PCM or a WAV-wrapped buffer.
     * Pinned to E4B because E2B doesn't include the audio tower.
     */
    fun sendAudio(
        pcmOrWav: ByteArray,
        userTextPrefix: String,
        systemPrompt: String,
        tools: ChibiClawTools
    ): Flow<String> {
        val tier = ModelTier.E4B
        val engine = engineManager.getEngine(tier) ?: run {
            Log.e(TAG, "Engine not ready for audio")
            return emptyFlow()
        }
        engineManager.resetIdleTimer()

        return flow {
            devLogger.i(
                "INFERENCE",
                "→ sendAudio: prefix=\"$userTextPrefix\" audio=${pcmOrWav.size / 1024}KB"
            )
            val contents = if (userTextPrefix.isBlank()) {
                Contents.of(Content.AudioBytes(pcmOrWav))
            } else {
                Contents.of(Content.Text(userTextPrefix), Content.AudioBytes(pcmOrWav))
            }

            try {
                val conversation = ensureConversation(tier, engine, systemPrompt, tools, useTools = true)
                conversation.sendMessageAsync(contents).collect { raw ->
                    val cleaned = sanitizeChunk(raw.toString())
                    if (cleaned.isNotEmpty()) emit(cleaned)
                }
                devLogger.i("INFERENCE", "Audio stream complete")
            } catch (e: Exception) {
                if (isParserBug(e)) {
                    devLogger.w("INFERENCE", "Audio parser bug — retry no-tools")
                } else {
                    devLogger.e("INFERENCE", "Audio (tools) failed: ${e::class.simpleName}: ${e.message}")
                }
                resetSession(tier, "audio tools path failed")
                try {
                    val conversation = ensureConversation(tier, engine, systemPrompt, tools = null, useTools = false)
                    conversation.sendMessageAsync(contents).collect { raw ->
                        val cleaned = sanitizeChunk(raw.toString())
                        if (cleaned.isNotEmpty()) emit(cleaned)
                    }
                } catch (e2: Exception) {
                    resetSession(tier, "audio no-tools failed")
                    Log.e(TAG, "Audio inference error: ${e2.message}")
                    emit("[ERROR] ${e2.message}")
                }
            }
        }.flowOn(engineManager.dispatcherFor(tier))
    }

    /**
     * One-shot transcription of [pcmOrWav] using a TEMPORARY conversation —
     * does not touch the persistent chat session so transcription output
     * does not pollute chat history. Returns the transcribed text (empty
     * string on failure). Call on [GemmaEngineManager.engineDispatcher].
     *
     * Gemma 3n E4B is multilingual (Indonesian included), so this works
     * offline without SpeechRecognizer.
     */
    suspend fun transcribeAudio(pcmOrWav: ByteArray, language: String = "Indonesian"): String {
        val tier = ModelTier.E4B
        val engine = engineManager.getEngine(tier) ?: run {
            devLogger.e("INFERENCE", "transcribeAudio: engine not ready")
            return ""
        }
        engineManager.resetIdleTimer()

        // CRITICAL: LiteRT-LM 0.10.0 only allows ONE Conversation per Engine at a
        // time. ANY previously-created Conversation on this engine — regardless
        // of which tier slot it's parked in — has to be closed before we can
        // create a fresh one here, or LiteRT-LM throws FAILED_PRECONDITION:
        //   "A session already exists. Only one session is supported at a time."
        // Voice input starts a fresh user turn anyway; the next sendMessage()
        // will lazily rebuild the persistent session via ensureConversation().
        closeAllSessionsOn(engine, "transcribe_audio_begin")

        val transcriptionPrompt =
            "You are a speech-to-text engine. Transcribe the user's audio into " +
                "plain text in $language. Output ONLY the transcription — no " +
                "greetings, no translation, no commentary, no quotes."
        val sampler = SamplerConfig(temperature = 0.1, topK = 1, topP = 1.0)
        val config = ConversationConfig(
            systemInstruction = Contents.of(transcriptionPrompt),
            samplerConfig = sampler,
            automaticToolCalling = false
        )

        var tmp: Conversation? = null
        return try {
            tmp = engine.createConversation(config)
            val contents = Contents.of(Content.AudioBytes(pcmOrWav))
            val sb = StringBuilder()
            tmp.sendMessageAsync(contents).collect { sb.append(it.toString()) }
            val text = sb.toString().trim().trim('"', '\'')
            devLogger.i("INFERENCE", "transcribeAudio → \"${text.take(80)}\"")
            text
        } catch (e: Exception) {
            devLogger.e("INFERENCE", "transcribeAudio failed: ${e::class.simpleName}: ${e.message}")
            ""
        } finally {
            try { tmp?.close() } catch (_: Exception) {}
        }
    }

    private fun encodeBitmapAsJpeg(bitmap: Bitmap, quality: Int = 85): ByteArray? =
        try {
            ByteArrayOutputStream(bitmap.width * bitmap.height / 4).use { os ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, os)
                os.toByteArray()
            }
        } catch (e: Exception) {
            Log.e(TAG, "encodeBitmapAsJpeg failed: ${e.message}")
            null
        }

    companion object {
        private const val TAG = "GemmaInference"

        // Matches a Gemma 3n native tool-call block like:
        //   <|tool_call|>{"name":"foo","args":{}}<|"|>
        // The body is non-greedy so a stray block in the middle of text is
        // surgically removed without eating surrounding prose.
        private val TOOL_CALL_BLOCK_REGEX =
            Regex("<\\|tool_call\\|>.*?<\\|\"\\|>", RegexOption.DOT_MATCHES_ALL)

        // Open-ended variant: `<|tool_call|>{...}` followed by end-of-stream
        // with no closing `<|"|>` token (Gemma occasionally truncates).
        private val TOOL_CALL_OPEN_REGEX =
            Regex("<\\|tool_call\\|>[^<]*", RegexOption.DOT_MATCHES_ALL)

        // Generic final safety net — any remaining `<|xxx|>` style control
        // token that's not real prose. Real JSON/tool arguments never contain
        // this exact pattern so it's safe to strip.
        private val CONTROL_TOKEN_REGEX = Regex("<\\|[^|>]*\\|>")
    }
}
