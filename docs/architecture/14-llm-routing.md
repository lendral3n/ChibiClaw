# 14 — LLM Routing & Adapters

Adapter pattern untuk multi-backend LLM. Cascade: Gemma local → Gemini API free → Claude web reverse-engineered → ChatGPT web reverse-engineered. LLM yang putuskan kapan escalate via tool `escalate_to_cloud`.

---

## Adapter Interface

```kotlin
interface InferenceAdapter {
    val id: String                              // unique id ("gemma_local", "gemini_free", ...)
    val capability: AdapterCapability
    
    suspend fun isAvailable(): Boolean
    suspend fun complete(prompt: AgentPrompt): InferenceResult
    fun stream(prompt: AgentPrompt): Flow<InferenceChunk>
    suspend fun shutdown()
}

data class AdapterCapability(
    val displayName: String,
    val contextWindow: Int,                     // tokens
    val supportsToolCalling: Boolean,
    val supportsStreaming: Boolean,
    val supportsVision: Boolean,
    val supportsConstrainedDecoding: Boolean,
    val isLocal: Boolean,
    val estimatedTpsDecode: Float,              // tokens/sec
    val estimatedTpsPrefill: Float,
    val requiresAuth: Boolean,
)

data class AgentPrompt(
    val systemPrompt: String,
    val taskGoal: String,
    val taskChannel: TaskChannel,
    val taskHistory: List<String>,
    val recentTasks: List<String>,
    val worldSnapshot: String,
    val relevantMemory: List<String>,
    val emotionSignal: String?,
    val toolCatalog: List<String>,
    val personaTraits: String,
    val iteration: Int,
    val maxIteration: Int,
    val responseFormat: ResponseFormat = ResponseFormat.JSON_STRUCTURED,
)

sealed class InferenceResult {
    data class Success(val raw: String, val tokensUsed: Int, val latencyMs: Long) : InferenceResult()
    data class Error(val errorClass: AdapterErrorClass, val message: String) : InferenceResult()
}

enum class AdapterErrorClass {
    AUTH_EXPIRED, RATE_LIMITED, NETWORK, TIMEOUT, CONTEXT_OVERFLOW, MODEL_ERROR, UNKNOWN
}
```

---

## Adapter Implementations

### GemmaAdapter (Default)

LiteRT-LM 0.11+ Kotlin API. Gemma 4 4B Q4 di on-device.

```kotlin
@Singleton
class GemmaAdapter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auditLogger: AuditLogger,
) : InferenceAdapter {
    
    override val id = "gemma_local"
    override val capability = AdapterCapability(
        displayName = "Gemma 4 4B (Local)",
        contextWindow = 128_000,
        supportsToolCalling = true,
        supportsStreaming = true,
        supportsVision = false,             // Phase 1: text-only. Phase 5 ganti MiniCPM-V atau Gemma 4 multimodal
        supportsConstrainedDecoding = true, // LiteRT-LM 0.11 punya llguidance
        isLocal = true,
        estimatedTpsDecode = 25f,           // Snapdragon 8 Elite Gen 5 NPU
        estimatedTpsPrefill = 1500f,
        requiresAuth = false,
    )
    
    private var session: LlmInferenceSession? = null
    
    override suspend fun isAvailable(): Boolean {
        return session != null || tryInit()
    }
    
    private suspend fun tryInit(): Boolean {
        return runCatching {
            val modelPath = downloadOrLoadModel()
            val options = LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(4096)
                .setBackend(LlmInference.Backend.NPU)
                .build()
            val inference = LlmInference.create(context, options)
            session = inference.startSession(
                LlmInferenceSession.Options.builder()
                    .setTemperature(0.7f)
                    .setTopK(40)
                    .setSystemPromptToken(SYSTEM_PROMPT_TOKEN)
                    .build()
            )
            true
        }.getOrDefault(false)
    }
    
    override suspend fun complete(prompt: AgentPrompt): InferenceResult {
        val sess = session ?: return InferenceResult.Error(
            AdapterErrorClass.MODEL_ERROR, "Session not initialized"
        )
        
        val promptText = PromptBuilder.toGemmaFormat(prompt)
        val start = System.currentTimeMillis()
        
        return runCatching {
            sess.addQueryChunk(promptText)
            val response = sess.generateResponse()
            val latency = System.currentTimeMillis() - start
            val tokens = sess.sizeInTokens(response)
            
            auditLogger.logLlmCall("gemma_local", tokens, latency)
            InferenceResult.Success(response, tokens, latency)
        }.getOrElse { e ->
            InferenceResult.Error(AdapterErrorClass.MODEL_ERROR, e.message ?: "unknown")
        }
    }
    
    override fun stream(prompt: AgentPrompt): Flow<InferenceChunk> = flow {
        val sess = session ?: return@flow
        val promptText = PromptBuilder.toGemmaFormat(prompt)
        sess.addQueryChunk(promptText)
        sess.generateResponseStreaming { partial ->
            launch { emit(InferenceChunk(partial.text, partial.isLast)) }
        }
    }
    
    override suspend fun shutdown() {
        session?.close()
        session = null
    }
    
    private suspend fun downloadOrLoadModel(): String {
        // Phase 1: model bundled di assets or downloaded ke /data/files
        // Phase 4+: support multiple model variants user-pilih
        return File(context.filesDir, "gemma-4-4b-q4.task").absolutePath
    }
}
```

**Phase 1 deliverable:** Gemma 4 4B loaded, basic completion works, tool calling parsing via constrained JSON.

### GeminiFreeAdapter (Phase 4)

Google AI Studio API free tier — Gemini 2.5 Flash, 1500 req/day free.

```kotlin
@Singleton
class GeminiFreeAdapter @Inject constructor(
    private val httpClient: OkHttpClient,
    private val securePrefs: SecurePreferences,
    private val quotaTracker: AdapterQuotaTracker,
    private val auditLogger: AuditLogger,
) : InferenceAdapter {
    
    override val id = "gemini_free"
    override val capability = AdapterCapability(
        displayName = "Gemini 2.5 Flash (Free Tier)",
        contextWindow = 1_000_000,
        supportsToolCalling = true,
        supportsStreaming = true,
        supportsVision = true,
        supportsConstrainedDecoding = true,
        isLocal = false,
        estimatedTpsDecode = 150f,
        estimatedTpsPrefill = 5000f,
        requiresAuth = true,
    )
    
    override suspend fun isAvailable(): Boolean {
        val apiKey = securePrefs.getGeminiApiKey()
        val quotaOk = quotaTracker.hasQuota("gemini_free")
        return !apiKey.isNullOrBlank() && quotaOk
    }
    
    override suspend fun complete(prompt: AgentPrompt): InferenceResult {
        val apiKey = securePrefs.getGeminiApiKey() 
            ?: return InferenceResult.Error(AdapterErrorClass.AUTH_EXPIRED, "API key missing")
        
        if (!quotaTracker.tryConsume("gemini_free")) {
            return InferenceResult.Error(AdapterErrorClass.RATE_LIMITED, "Daily quota exhausted")
        }
        
        val payload = PromptBuilder.toGeminiFormat(prompt)
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        
        val start = System.currentTimeMillis()
        return runCatching {
            val response = httpClient.newCall(request).execute()
            val body = response.body!!.string()
            val latency = System.currentTimeMillis() - start
            
            when {
                response.isSuccessful -> {
                    val parsed = parseGeminiResponse(body)
                    auditLogger.logLlmCall("gemini_free", parsed.tokensUsed, latency)
                    InferenceResult.Success(parsed.text, parsed.tokensUsed, latency)
                }
                response.code == 429 -> {
                    quotaTracker.markExhausted("gemini_free")
                    InferenceResult.Error(AdapterErrorClass.RATE_LIMITED, "API rate limit")
                }
                response.code == 401 -> InferenceResult.Error(AdapterErrorClass.AUTH_EXPIRED, "Invalid API key")
                else -> InferenceResult.Error(AdapterErrorClass.UNKNOWN, "HTTP ${response.code}: $body")
            }
        }.getOrElse { e ->
            InferenceResult.Error(AdapterErrorClass.NETWORK, e.message ?: "Network error")
        }
    }
    
    override fun stream(prompt: AgentPrompt): Flow<InferenceChunk> = flow {
        // Server-Sent Events implementation
    }
}
```

**Setup user:** di setup wizard, user diarahkan ke aistudio.google.com → create API key → paste ke `securePrefs.geminiApiKey`. Stored encrypted.

### ClaudeWebAdapter (Phase 4)

Reverse-engineered Claude.ai web session. Login via WebView headless once-off, ekstrak session cookie + token. Subsequent call hit `claude.ai/api/...` endpoints.

```kotlin
@Singleton
class ClaudeWebAdapter @Inject constructor(
    private val httpClient: OkHttpClient,
    private val securePrefs: SecurePreferences,
    private val sessionRotator: ClaudeSessionRotator,
    private val auditLogger: AuditLogger,
) : InferenceAdapter {
    
    override val id = "claude_web"
    override val capability = AdapterCapability(
        displayName = "Claude.ai (Web Session)",
        contextWindow = 200_000,
        supportsToolCalling = false,            // reverse-engineered web doesn't expose native tool API; mock via prompt
        supportsStreaming = true,
        supportsVision = true,
        supportsConstrainedDecoding = false,
        isLocal = false,
        estimatedTpsDecode = 80f,
        estimatedTpsPrefill = 3000f,
        requiresAuth = true,
    )
    
    override suspend fun isAvailable(): Boolean {
        val session = securePrefs.getClaudeSession()
        return session?.isValid() == true
    }
    
    override suspend fun complete(prompt: AgentPrompt): InferenceResult {
        val session = securePrefs.getClaudeSession() 
            ?: return InferenceResult.Error(AdapterErrorClass.AUTH_EXPIRED, "No Claude session")
        
        val payload = PromptBuilder.toClaudeWebFormat(prompt)
        val request = Request.Builder()
            .url("https://claude.ai/api/organizations/${session.orgId}/chat_conversations/${session.activeConvId}/completion")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .header("Cookie", session.cookies.joinToString("; "))
            .header("anthropic-client-sha", session.clientSha)
            .header("anthropic-client-version", session.clientVersion)
            .header("anthropic-device-id", session.deviceId)
            .header("User-Agent", session.userAgent)
            // ... other anti-bot headers
            .build()
        
        val start = System.currentTimeMillis()
        return runCatching {
            val response = httpClient.newCall(request).execute()
            // Parse SSE stream
            val text = parseClaudeStreamingResponse(response.body!!.byteStream())
            val latency = System.currentTimeMillis() - start
            
            if (text.isBlank()) {
                sessionRotator.flag(session, "empty response")
                return@runCatching InferenceResult.Error(AdapterErrorClass.UNKNOWN, "Empty response")
            }
            
            auditLogger.logLlmCall("claude_web", estimateTokens(text), latency)
            InferenceResult.Success(text, estimateTokens(text), latency)
        }.getOrElse { e ->
            if (e.message?.contains("401") == true) {
                sessionRotator.markInvalid(session)
                InferenceResult.Error(AdapterErrorClass.AUTH_EXPIRED, "Session invalid, re-login required")
            } else {
                InferenceResult.Error(AdapterErrorClass.NETWORK, e.message ?: "")
            }
        }
    }
}
```

**Session struktur:**

```kotlin
@Serializable
data class ClaudeWebSession(
    val orgId: String,
    val userId: String,
    val activeConvId: String,                 // pre-created conversation di setup
    val cookies: List<String>,
    val clientSha: String,                    // dari WebView fingerprint
    val clientVersion: String,
    val deviceId: String,                     // randomized once
    val userAgent: String,
    val createdAt: Instant,
    val lastValidatedAt: Instant,
    val maxAge: Duration = Duration.ofDays(14),
) {
    fun isValid(): Boolean = Instant.now() < createdAt + maxAge && !flaggedInvalid
}
```

**Risk catatan:**
- Endpoint dan header signature **sering rotate** Anthropic. Update strategy: hook untuk catch baru fingerprint dari WebView interaktif.
- ToS violation. Account bisa di-ban. Mitigasi: rate limit 1 call/30s, batch jangan terlalu cepat.

### GPTWebAdapter (Phase 4)

Reverse-engineered ChatGPT web. Mirip Claude tapi endpoint berbeda.

```kotlin
@Singleton
class GPTWebAdapter @Inject constructor(
    /* sama seperti ClaudeWebAdapter dengan endpoint chatgpt.com */
) : InferenceAdapter {
    override val id = "gpt_web"
    override val capability = AdapterCapability(
        displayName = "ChatGPT (Web Session)",
        contextWindow = 128_000,
        // ...
    )
    // ... implementation mirip ClaudeWebAdapter
}
```

Reference library yang bisa di-port:
- `revChatGPT` (Python)
- `pythongpt` (community)
- Kotlin port belum ada matang — implementasi Phase 4 perlu spike

---

## Inference Router

```kotlin
@Singleton
class InferenceRouter @Inject constructor(
    private val gemma: GemmaAdapter,
    private val gemini: GeminiFreeAdapter,
    private val claude: ClaudeWebAdapter,
    private val gpt: GPTWebAdapter,
) {
    private val taskPinning = ConcurrentHashMap<String, String>()
    
    suspend fun selectAdapter(task: TaskEntity): InferenceAdapter {
        val pinned = taskPinning[task.id]
        return when (pinned) {
            "gemini_free" -> gemini.takeIf { it.isAvailable() } ?: gemma
            "claude_web" -> claude.takeIf { it.isAvailable() } ?: gemma
            "gpt_web" -> gpt.takeIf { it.isAvailable() } ?: gemma
            else -> gemma
        }
    }
    
    suspend fun escalate(target: AdapterTarget, task: TaskEntity): InferenceAdapter? {
        val ordered = listOf(
            target.toAdapter(),
            // fallback chain
            gemini,
            claude,
            gpt,
        ).distinctBy { it.id }
        
        for (adapter in ordered) {
            if (adapter.isAvailable()) {
                taskPinning[task.id] = adapter.id
                return adapter
            }
        }
        return null
    }
    
    fun unpin(taskId: String) {
        taskPinning.remove(taskId)
    }
}

enum class AdapterTarget {
    GEMMA, GEMINI, CLAUDE, GPT;
    
    fun toAdapter(/*deps*/): InferenceAdapter = TODO()
}
```

---

## Quota Tracking

```kotlin
@Singleton
class AdapterQuotaTracker @Inject constructor(
    private val configRepo: ModelConfigRepository,
) {
    
    data class Quota(
        val adapterId: String,
        val maxPerDay: Int,
        val usedToday: Int,
        val resetAt: Instant,
    )
    
    private val quotas = mapOf(
        "gemini_free" to QuotaPolicy(maxPerDay = 1500, periodHours = 24),
        "claude_web" to QuotaPolicy(maxPerHour = 30),     // unofficial limit
        "gpt_web" to QuotaPolicy(maxPerHour = 30),
        // gemma_local: unlimited
    )
    
    suspend fun hasQuota(adapterId: String): Boolean {
        val policy = quotas[adapterId] ?: return true
        val state = loadQuotaState(adapterId)
        return state.used < policy.max
    }
    
    suspend fun tryConsume(adapterId: String): Boolean {
        val state = loadQuotaState(adapterId)
        val policy = quotas[adapterId] ?: return true
        if (state.used >= policy.max) return false
        saveQuotaState(adapterId, state.copy(used = state.used + 1))
        return true
    }
    
    suspend fun markExhausted(adapterId: String) {
        // Set quota to max — won't retry until reset
        val policy = quotas[adapterId] ?: return
        saveQuotaState(adapterId, QuotaState(adapterId, policy.max, computeNextReset(policy)))
    }
}
```

---

## Prompt Format Adapter

```kotlin
object PromptBuilder {
    
    fun toGemmaFormat(prompt: AgentPrompt): String {
        // LiteRT-LM Gemma 4 instruct format
        return buildString {
            append("<start_of_turn>system\n")
            append(prompt.systemPrompt)
            append("\n\n## Persona\n")
            append(prompt.personaTraits)
            append("\n\n## World State\n")
            append(prompt.worldSnapshot)
            append("\n\n## Relevant Memory\n")
            prompt.relevantMemory.forEach { append("- $it\n") }
            append("\n\n## Available Tools\n")
            prompt.toolCatalog.forEach { append("$it\n\n") }
            append("\n\n## Response Format\n")
            append(JSON_SCHEMA_SPEC)
            append("<end_of_turn>\n")
            
            append("<start_of_turn>user\n")
            append("## Task Goal\n")
            append(prompt.taskGoal)
            append("\n\n## Channel: ${prompt.taskChannel}")
            append("\n\n## Task History\n")
            prompt.taskHistory.forEach { append("$it\n") }
            append("\n\n## Recent Tasks (for context)\n")
            prompt.recentTasks.forEach { append("- $it\n") }
            if (prompt.emotionSignal != null) {
                append("\n\n## Emotion Signal\n${prompt.emotionSignal}\n")
            }
            append("\n\nIteration ${prompt.iteration}/${prompt.maxIteration}. Respond in JSON.\n")
            append("<end_of_turn>\n")
            
            append("<start_of_turn>model\n")
        }
    }
    
    fun toGeminiFormat(prompt: AgentPrompt): String {
        // Gemini REST API JSON payload
        return Json.encodeToString(GeminiPayload(
            contents = listOf(
                GeminiContent(role = "user", parts = listOf(GeminiPart(buildPromptText(prompt))))
            ),
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(prompt.systemPrompt))),
            generationConfig = GeminiGenerationConfig(
                temperature = 0.7,
                maxOutputTokens = 4096,
                responseMimeType = "application/json",
                responseSchema = JSON_SCHEMA_GEMINI,
            ),
        ))
    }
    
    fun toClaudeWebFormat(prompt: AgentPrompt): String {
        // claude.ai web API payload
        return Json.encodeToString(ClaudeWebPayload(
            prompt = buildPromptText(prompt),
            model = "claude-sonnet-4-6",
            // ...
        ))
    }
    
    // toGPTWebFormat: similar
}
```

---

## Response Format Konvensi

Setiap adapter return raw string. AgentRuntime parse pakai `parseLlmResponse()`. Hierarchy:

1. **Constrained JSON** kalau adapter support (Gemma 0.11 llguidance, Gemini responseSchema) — output guaranteed valid JSON
2. **JSON block fenced** kalau adapter return mixed text + ```json``` block
3. **Tag-based plain text** sebagai fallback (THOUGHT/TOOL_CALLS/NEXT)

Format target:

```json
{
  "thought": "User mau kirim WA ke Budi. Aku perlu buka WA dulu.",
  "tool_calls": [
    {"tool": "intent_open", "args": {"package": "com.whatsapp"}}
  ],
  "next": "continue",
  "summary": null,
  "question": null,
  "emotion": "neutral"
}
```

`next` values: `continue` (more iterations needed), `done`, `await_user`, `escalate`.

---

## Setup Wizard untuk Cloud Adapter

### Gemini API Key (Phase 4)

1. UI step: "Set up Gemini (free tier, 1500 req/day)"
2. Tampilkan tombol "Buka Google AI Studio"
3. Buka https://aistudio.google.com/apikey di Custom Tab
4. User create key, copy
5. Paste di input field
6. Test call (1 token completion) → kalau OK, save encrypted

### Claude.ai WebView Headless (Phase 4)

1. UI step: "Login Claude.ai dengan akun kamu"
2. Tampilkan **WebView modal** dengan URL `https://claude.ai/login`
3. User login interactive (Google sign-in atau email/password)
4. JavaScript bridge listen `URL_CHANGED` event
5. Saat URL = `https://claude.ai/chats`, extract:
   - All cookies (DocumentTrack JS query)
   - localStorage items (org_id, user_id)
   - Generate clientSha (hash dari user-agent + timestamp)
6. Test API call ke `/api/organizations/{org}/chat_conversations` → kalau 200, save
7. Pre-create 1 conversation untuk reuse (avoid create-new tiap call)

### ChatGPT Login (Phase 4)

Mirip Claude flow tapi URL `https://chat.openai.com/auth/login`.

---

## Risk Mitigation untuk Reverse Web

1. **Header rotation**: setiap call randomize User-Agent dari list common browsers. Update list quarterly.
2. **Rate limiting**: max 1 call / 30 detik per adapter. Throttle queue.
3. **Session re-validation**: setiap 24 jam, send ping ke endpoint mundane (GET /api/organizations) untuk verify session aktif. Kalau 401 → notify user.
4. **Fallback graceful**: kalau auth fail → ToolResult.Error(AUTH_EXPIRED) → LLM emit await_user("Login ulang Claude.ai?") atau switch ke Gemini free.
5. **Audit log lengkap**: setiap reverse-engineered call di-log dengan detail (sebelum/sesudah) untuk debug saat endpoint rotate.

---

## Tool `escalate_to_cloud` Behavior

Saat LLM emit:
```json
{
  "tool_calls": [
    {"tool": "escalate_to_cloud", "args": {"reason": "task butuh long reasoning", "target": "claude"}}
  ]
}
```

ToolDispatcher route ke `EscalateToolHandler`:

```kotlin
class EscalateToolHandler @Inject constructor(
    private val router: InferenceRouter,
    private val agentRuntime: AgentRuntime,
) : ToolHandler<EscalateArgs> {
    
    override suspend fun execute(call: ToolCall, task: TaskEntity): ToolResult {
        val args = call.parseArgs<EscalateArgs>()
        
        // High severity → confirm user kalau cloud mode = opt-in
        if (!isCloudPreAuthorized(task)) {
            val approved = showConfirmation(
                "Fuu mau lanjut pakai ${args.target} (cloud). Lanjut?"
            )
            if (!approved) return ToolResult.UserDenied
        }
        
        val adapter = router.escalate(args.target, task)
        return if (adapter != null) {
            ToolResult.Success(call.callId, mapOf(
                "switched_to" to JsonPrimitive(adapter.id),
                "next_iteration_uses" to JsonPrimitive(adapter.capability.displayName)
            ))
        } else {
            ToolResult.Error(call.callId, ErrorClass.NOT_AVAILABLE, "No cloud adapter available")
        }
    }
}
```

LLM observe success → next iteration uses new adapter (pin via router state).

---

## Concurrency & Thread Safety

- Each adapter has internal mutex untuk single-threaded inference (Gemma) atau pool (cloud).
- `InferenceRouter.taskPinning` ConcurrentHashMap (multiple task slot concurrent).
- `AdapterQuotaTracker` synchronized di Room transaction.

---

## Monitoring & Debug

Dev console (built into ChibiClaw dev mode) tampilkan:
- Current adapter per task (pinning state)
- Quota per adapter (used / max / reset time)
- Last 50 LLM call (adapter, latency, tokens, status)
- Session validity (cookies, expiry)

Akses via Settings → Dev → LLM Routing.

---

## Configuration

`settings/llm_routing.json` (di SecurePreferences):

```json
{
  "default_adapter": "gemma_local",
  "cloud_mode": "opt_in",
  "auto_escalate_on_failure": false,
  "escalation_pre_authorized": false,
  "adapter_priority_order": ["gemma_local", "gemini_free", "claude_web", "gpt_web"],
  "adapter_quotas": {
    "gemini_free": {"max_per_day": 1500},
    "claude_web": {"max_per_hour": 30},
    "gpt_web": {"max_per_hour": 30}
  }
}
```

User bisa edit di Settings → AI Engine.

---

## Next Files

- Voice pipeline: [15-voice-pipeline.md](15-voice-pipeline.md)
- Memory: [16-memory-system.md](16-memory-system.md)
- Phase 4 detail: [25-phase-4-cloud-escalation.md](25-phase-4-cloud-escalation.md)
