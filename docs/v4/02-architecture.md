# 02 — Architecture

**Companion to**: [01-design-paper.md](01-design-paper.md)
**Audience**: Engineering
**Last updated**: 2026-05-10

---

## 1. System Overview

### 1.1 Layer cake

```
┌──────────────────────────────────────────────────────────────────┐
│  USER (voice / text)                                              │
└──────────────────────────────────────────────────────────────────┘
                            ↑↓
┌──────────────────────────────────────────────────────────────────┐
│  PRESENTATION LAYER                                               │
│  • ChatScreen (text fallback)                                     │
│  • VoiceOverlay (visualization saat Fuu listening/speaking)       │
│  • DashboardScreen (status, history, settings)                    │
│  • OnboardingFlow (first-time setup)                              │
└──────────────────────────────────────────────────────────────────┘
                            ↑↓
┌──────────────────────────────────────────────────────────────────┐
│  VOICE LAYER (NEW)                                                │
│  • WakeWordEngine (porcupine / openWakeWord)                      │
│  • VoiceActivityDetector (Silero VAD)                             │
│  • SpeechToText (Whisper.cpp / Whisper API)                       │
│  • TextToSpeech (Piper / OpenAI TTS / ElevenLabs)                 │
│  • InterruptManager (handle barge-in saat Fuu sedang bicara)      │
└──────────────────────────────────────────────────────────────────┘
                            ↑↓
┌──────────────────────────────────────────────────────────────────┐
│  GATEWAY LAYER (existing, sedikit modif)                          │
│  • CommandGateway                                                 │
│  • CommandQueue                                                   │
│  • Sources: VoiceSource, TextSource, AIDLSource, CronSource       │
└──────────────────────────────────────────────────────────────────┘
                            ↑↓
┌──────────────────────────────────────────────────────────────────┐
│  AGENT LAYER (existing, refactored)                               │
│  • ChibiAgent — main orchestrator                                 │
│  • PromptBuilder — system prompt + context assembly               │
│  • CommandHistoryStore — log commands                             │
│  • MemoryStore (NEW) — semantic memory + entity store             │
│  • ToolSafety + AutoControlGate — safety layer (kept from v3)     │
└──────────────────────────────────────────────────────────────────┘
                            ↑↓
┌──────────────────────────────────────────────────────────────────┐
│  INFERENCE LAYER (NEW abstraction)                                │
│  • InferenceAdapter (interface)                                   │
│    ├── GemmaLocalAdapter (Gemma 4 via LiteRT-LM)                  │
│    ├── OpenAIAdapter (GPT-4o, GPT-4o-mini)                        │
│    ├── AnthropicAdapter (Claude Sonnet 4.6, Opus 4.7)             │
│    └── GoogleAIAdapter (Gemini Pro, Gemini Flash)                 │
│  • InferenceRouter — pilih adapter berdasar mode + fallback        │
│  • CostMeter — track cloud token usage                            │
│  • PrivacyManager — coordinate offline/cloud toggle               │
└──────────────────────────────────────────────────────────────────┘
                            ↑↓
┌──────────────────────────────────────────────────────────────────┐
│  TOOL LAYER (existing, consolidated)                              │
│  • ChibiClawTools (10 primitives, dari 27 di v3)                  │
│    ├── tap(target_or_coords)                                      │
│    ├── type(text)                                                 │
│    ├── key(name)                                                  │
│    ├── screenshot()                                               │
│    ├── intent(action, uri, package)                               │
│    ├── query(provider, query)                                     │
│    ├── system(target, state)                                      │
│    ├── messaging(kind, recipient, body)                           │
│    ├── shizuku(kind, payload)                                     │
│    └── meta (askUser, report)                                     │
└──────────────────────────────────────────────────────────────────┘
                            ↑↓
┌──────────────────────────────────────────────────────────────────┐
│  EXECUTION LAYER (existing, vision-first override)                │
│  • ActionDispatcher (consolidated)                                │
│  • ExecutionStrategy router:                                      │
│    if currentApp in BLACKLIST → VisionFirstStrategy               │
│    else → AccessibilityFirstStrategy                              │
└──────────────────────────────────────────────────────────────────┘
                            ↑↓
┌──────────────────────────────────────────────────────────────────┐
│  EXECUTOR LAYER (existing, kept)                                  │
│  • Tier 0: UtilityExecutor                                        │
│  • Tier 1: IntentExecutor, AppLauncher                            │
│  • Tier 2: 15 executors (System, Messaging, Camera, dll)          │
│  • Tier 3: AccessibilityExecutor, GestureDispatcher, Vision*      │
│  • Tier 4: AppManager, NotificationController, Shizuku           │
└──────────────────────────────────────────────────────────────────┘
                            ↑↓
┌──────────────────────────────────────────────────────────────────┐
│  ANDROID PLATFORM                                                 │
│  • AccessibilityService                                           │
│  • Intent System                                                  │
│  • ContentProvider                                                │
│  • MediaProjection                                                │
│  • Shizuku (optional ADB-level)                                   │
└──────────────────────────────────────────────────────────────────┘
```

---

## 2. Component Detail

### 2.1 InferenceAdapter Pattern

**Goal**: Abstrak inference engine sehingga ChibiAgent tidak peduli pakai Gemma local atau Claude cloud.

#### Interface

```kotlin
// agent/inference/InferenceAdapter.kt
interface InferenceAdapter {
    val id: String
    val displayName: String
    val capabilities: ModelCapabilities
    val privacyTier: PrivacyTier  // OFFLINE | CLOUD_DOMESTIC | CLOUD_INTL

    suspend fun isReady(): Boolean
    suspend fun warm(): Result<Unit>  // pre-load / handshake
    
    fun sendMessage(
        userText: String,
        systemPrompt: String,
        tools: ToolSet,
        screenshot: Bitmap? = null,  // null = no vision
        history: List<ChatMessage> = emptyList()
    ): Flow<InferenceChunk>
    
    fun estimateCost(promptTokens: Int, completionTokens: Int): Cost
    suspend fun close()
}

data class ModelCapabilities(
    val supportsVision: Boolean,
    val supportsToolCalling: Boolean,
    val supportsStreaming: Boolean,
    val maxContextTokens: Int,
    val typicalLatencyMs: Int
)

enum class PrivacyTier {
    OFFLINE,            // Tidak ada data leave device
    CLOUD_DOMESTIC,     // Indonesia-based cloud (jarang)
    CLOUD_INTL          // US/EU based cloud (Anthropic, OpenAI, Google)
}

sealed class InferenceChunk {
    data class TextDelta(val text: String) : InferenceChunk()
    data class ToolCall(val name: String, val args: Map<String, Any>) : InferenceChunk()
    data class ToolResult(val name: String, val result: String) : InferenceChunk()
    data class Done(val totalTokens: Int) : InferenceChunk()
    data class Error(val cause: Throwable) : InferenceChunk()
}

data class Cost(
    val provider: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val totalUsd: Double
)
```

#### Implementations (skeleton)

```kotlin
// agent/inference/GemmaLocalAdapter.kt
@Singleton
class GemmaLocalAdapter @Inject constructor(
    private val gemmaInference: GemmaInference,    // existing v3
    private val gemmaEngineManager: GemmaEngineManager
) : InferenceAdapter {
    override val id = "gemma-local"
    override val displayName = "Gemma 4 (Offline)"
    override val capabilities = ModelCapabilities(
        supportsVision = true,
        supportsToolCalling = true,
        supportsStreaming = true,
        maxContextTokens = 32_000,
        typicalLatencyMs = 2500
    )
    override val privacyTier = PrivacyTier.OFFLINE
    
    override suspend fun isReady() = gemmaEngineManager.isReady()
    
    override fun sendMessage(...) = gemmaInference.sendMessage(...)
        .map { chunk -> mapToInferenceChunk(chunk) }
    
    override fun estimateCost(p: Int, c: Int) = Cost("gemma", p, c, 0.0)  // free
}
```

```kotlin
// agent/inference/AnthropicAdapter.kt
@Singleton
class AnthropicAdapter @Inject constructor(
    private val httpClient: OkHttpClient,
    private val secureStorage: SecureStorage
) : InferenceAdapter {
    override val id = "anthropic-claude"
    override val displayName = "Claude Sonnet 4.6"
    override val capabilities = ModelCapabilities(
        supportsVision = true,
        supportsToolCalling = true,
        supportsStreaming = true,
        maxContextTokens = 200_000,
        typicalLatencyMs = 800
    )
    override val privacyTier = PrivacyTier.CLOUD_INTL
    
    private val apiKey: String? get() = secureStorage.getString("anthropic_api_key")
    
    override suspend fun isReady() = !apiKey.isNullOrBlank() && pingApi()
    
    override fun sendMessage(...): Flow<InferenceChunk> = flow {
        val body = buildAnthropicMessagesRequest(...)
        val response = httpClient.streamRequest("https://api.anthropic.com/v1/messages", body)
        response.collect { sse ->
            when (sse.eventType) {
                "content_block_delta" -> emit(InferenceChunk.TextDelta(sse.text))
                "content_block_start" if sse.contentType == "tool_use" ->
                    emit(InferenceChunk.ToolCall(sse.name, sse.args))
                "message_stop" -> emit(InferenceChunk.Done(sse.totalTokens))
            }
        }
    }
    
    // Pricing: claude-sonnet-4-6: $3/M input, $15/M output
    override fun estimateCost(p: Int, c: Int) = Cost(
        provider = "anthropic-claude-sonnet-4-6",
        inputTokens = p,
        outputTokens = c,
        totalUsd = (p * 3.0 + c * 15.0) / 1_000_000
    )
}
```

#### Router

```kotlin
// agent/inference/InferenceRouter.kt
@Singleton
class InferenceRouter @Inject constructor(
    private val adapters: Map<String, @JvmSuppressWildcards InferenceAdapter>,
    private val privacyManager: PrivacyManager,
    private val devLogger: DevLogger
) {
    suspend fun selectAdapter(taskHint: TaskHint? = null): InferenceAdapter {
        val mode = privacyManager.currentMode  // OFFLINE | CLOUD | HYBRID
        
        val candidate = when (mode) {
            PrivacyMode.OFFLINE -> adapters["gemma-local"]
            PrivacyMode.CLOUD -> adapters[privacyManager.preferredCloudAdapter]
            PrivacyMode.HYBRID -> {
                // Hybrid: cloud kalau ready, fallback offline
                val cloud = adapters[privacyManager.preferredCloudAdapter]
                if (cloud?.isReady() == true) cloud else adapters["gemma-local"]
            }
        }
        
        return candidate?.takeIf { it.isReady() }
            ?: adapters["gemma-local"]!!  // ultimate fallback
    }
}

data class TaskHint(
    val isVisionHeavy: Boolean = false,
    val isCriticalTask: Boolean = false  // hint untuk pilih model lebih kuat
)
```

### 2.2 VoiceLayer Architecture

#### State machine

```
                    ┌─────────────┐
                    │   IDLE      │ ← initial state, low CPU
                    │  (listen    │
                    │  for wake)  │
                    └──────┬──────┘
                           │ wake word detected
                           ↓
                    ┌─────────────┐
                    │  LISTENING  │
                    │  (record    │
                    │  + STT)     │
                    └──────┬──────┘
                           │ silence > 1s OR explicit stop
                           ↓
                    ┌─────────────┐
                    │  PROCESSING │
                    │  (Gemma /   │
                    │  cloud      │
                    │  inference) │
                    └──────┬──────┘
                           │ response ready
                           ↓
                    ┌─────────────┐
                    │  SPEAKING   │ ← user can interrupt
                    │  (TTS       │
                    │  output)    │
                    └──────┬──────┘
                           │ TTS done OR interrupted
                           ↓
                    ┌─────────────┐
                    │  IDLE       │ (loop)
                    └─────────────┘
```

#### Implementation

```kotlin
// voice/VoiceLayer.kt
@Singleton
class VoiceLayer @Inject constructor(
    private val wakeWordEngine: WakeWordEngine,
    private val vad: VoiceActivityDetector,
    private val stt: SpeechToTextProvider,
    private val tts: TextToSpeechProvider,
    private val interruptManager: InterruptManager,
    private val commandGateway: CommandGateway,
    private val agent: ChibiAgent
) {
    private val _state = MutableStateFlow(VoiceState.IDLE)
    val state: StateFlow<VoiceState> = _state.asStateFlow()
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    fun start() {
        scope.launch {
            wakeWordEngine.startListening()
            wakeWordEngine.detections.collect { 
                onWakeWordDetected()
            }
        }
        
        scope.launch {
            agent.voiceResponses.collect { responseText ->
                _state.value = VoiceState.SPEAKING
                tts.speak(responseText, onInterrupt = ::onUserInterrupt)
                _state.value = VoiceState.IDLE
            }
        }
    }
    
    private suspend fun onWakeWordDetected() {
        _state.value = VoiceState.LISTENING
        playChime("listening")
        
        val transcript = stt.transcribeUntilSilence(maxSeconds = 15)
        if (transcript.isBlank()) {
            _state.value = VoiceState.IDLE
            return
        }
        
        _state.value = VoiceState.PROCESSING
        commandGateway.submitDirect(transcript, CommandSource.VOICE)
        // Response handled via agent.voiceResponses flow
    }
    
    private fun onUserInterrupt() {
        tts.stop()
        agent.cancelCurrentResponse()
        _state.value = VoiceState.LISTENING  // listen for new command
    }
}

enum class VoiceState { IDLE, LISTENING, PROCESSING, SPEAKING, ERROR }
```

#### Wake word detection (background)

ChibiClaw v4 perlu listen wake word continuously. Strategy:
- Foreground service tetap (sudah ada di v3 untuk ChibiService)
- Wake word engine berjalan di low-priority dispatcher
- VAD pre-filter sebelum wake word: kalau VAD detect silence >5s, suspend wake word (save battery)
- Resume wake word saat VAD detect activity

Battery impact target: <5%/jam idle (wake word listening continuously).

### 2.3 VisionFirstExecutor Strategy

#### Decision flow

```
ActionDispatcher.dispatch(action) {
    // existing AutoControlGate check
    val gate = autoControlGate.check(action)
    if (gate is Decision.Deny) return "BLOCKED: ${gate.message}"
    
    // NEW: strategy selection
    return when (action) {
        is UiInteractAction -> {
            val currentApp = perceptionRouter.getCurrentForegroundApp()
            val strategy = if (currentApp in VISION_BLACKLIST) {
                visionFirstStrategy
            } else {
                accessibilityFirstStrategy
            }
            strategy.execute(action)
        }
        // ... other action types unchanged
    }
}

interface ExecutionStrategy {
    suspend fun execute(action: UiInteractAction): String
}

class AccessibilityFirstStrategy @Inject constructor(
    private val accessibilityExecutor: AccessibilityExecutor,
    private val visionFallback: VisionFirstStrategy
) : ExecutionStrategy {
    override suspend fun execute(action: UiInteractAction): String {
        val result = accessibilityExecutor.perform(action)
        return when {
            result.startsWith("ui_error: node_not_found") -> {
                // accessibility failed, try vision
                visionFallback.execute(action)
            }
            else -> result
        }
    }
}

class VisionFirstStrategy @Inject constructor(
    private val perceptionRouter: PerceptionRouter,
    private val visionInference: InferenceAdapter,  // dedicated vision inference
    private val gestureDispatcher: GestureDispatcher
) : ExecutionStrategy {
    override suspend fun execute(action: UiInteractAction): String {
        val screenshot = perceptionRouter.captureScreenshot()
        val coords = visionInference.findElement(action.target, screenshot)
            ?: return "vision_error: element_not_found"
        
        return when (action.action) {
            "click", "tap" -> gestureDispatcher.tap(coords.x, coords.y)
            "type" -> {
                gestureDispatcher.tap(coords.x, coords.y)
                delay(300)
                gestureDispatcher.typeText(action.text)
                "ok"
            }
            else -> "vision_unsupported_action: ${action.action}"
        }
    }
}

object BLACKLIST_APPS {
    val DEFAULT = setOf(
        "com.zhiliaoapp.musically",  // TikTok
        "com.whatsapp",
        "com.tokopedia.tkpd",
        "com.shopee.id",
        "com.instagram.android",
        "com.facebook.katana"
    )
    // User can extend via Settings
}
```

### 2.4 MemoryStore (Rebuild)

#### Why rebuild
v3 hapus `MemoryEmbedding`, `vector/`, `context/` karena tidak dipakai. v4 rebuild dengan use case eksplisit:

1. **Entity resolution**: "buka chat ibu" → resolve "ibu" ke kontak +62 8xx
2. **Semantic command search**: "tadi yang aku suruh tentang Bos" → semantic search di history
3. **Pattern detection**: "user set alarm jam 6 setiap weekday" → suggest automation
4. **Conversation reference**: "buka tiktok" → next turn "cari ikan di sana" → "di sana" = TikTok

#### Schema

```kotlin
@Database(
    entities = [
        CommandHistory::class,      // existing v3
        CronTaskEntity::class,      // existing v3
        EntityRecord::class,        // NEW
        ConversationTurn::class,    // NEW
        SemanticIndex::class        // NEW (vector + text)
    ],
    version = 5,
    exportSchema = true  // ← change from v3 (untuk migration)
)
abstract class ChibiDatabase : RoomDatabase() { ... }

@Entity(tableName = "entity_record")
data class EntityRecord(
    @PrimaryKey val id: String,
    val type: String,           // "person", "app", "location", "topic"
    val canonicalName: String,  // "Budi Setiawan"
    val aliases: List<String>,  // ["budi", "kak budi", "bro"]
    val metadata: Map<String, String>,  // {"phone": "+628...", "email": "..."}
    val confidence: Float,
    val lastUsedTs: Long,
    val createdTs: Long
)

@Entity(tableName = "conversation_turn")
data class ConversationTurn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val role: String,           // "user", "assistant"
    val text: String,
    val toolCallsJson: String?, // json array of tool calls
    val timestamp: Long
)

@Entity(tableName = "semantic_index")
data class SemanticIndex(
    @PrimaryKey val refId: String,        // ref to commandHistory/conversationTurn
    val refType: String,                  // "command", "turn", "entity"
    val text: String,
    val embeddingBlob: ByteArray,         // 384-dim float32 = 1536 bytes
    val createdTs: Long
)
```

#### Embedding model
- **Sentence Transformers MiniLM-L6-v2** quantized (90MB) — dipakai di v3 sebelum dihapus, masih relevan
- Atau **bge-small-en** kalau English-heavy
- Output: 384-dim vector
- Index: brute force cosine similarity (cukup untuk <100k records). Upgrade ke ANN kalau lebih besar.

#### API

```kotlin
@Singleton
class MemoryStore @Inject constructor(
    private val db: ChibiDatabase,
    private val embedder: TextEmbedder,
    private val cipher: SqlCipherKeyManager
) {
    suspend fun saveCommand(command: String, result: String) {
        // existing v3 behavior
        db.commandHistoryDao().insert(CommandHistory(...))
        // NEW: also embed
        val embedding = embedder.embed(command)
        db.semanticIndexDao().insert(SemanticIndex(...))
    }
    
    suspend fun resolveEntity(alias: String): EntityRecord? {
        // exact match dulu
        val exact = db.entityDao().findByAlias(alias)
        if (exact != null) return exact
        // semantic search fallback
        val embedding = embedder.embed(alias)
        return db.semanticIndexDao().findClosest(embedding, type = "entity", topK = 1)?.firstOrNull()
            ?.let { db.entityDao().findById(it.refId) }
    }
    
    suspend fun searchHistory(query: String, topK: Int = 5): List<CommandHistory> {
        val embedding = embedder.embed(query)
        return db.semanticIndexDao().findClosest(embedding, type = "command", topK)
            .mapNotNull { db.commandHistoryDao().findById(it.refId.toLong()) }
    }
}
```

### 2.5 PrivacyManager

```kotlin
@Singleton
class PrivacyManager @Inject constructor(
    private val secureStorage: SecureStorage
) {
    enum class Mode { OFFLINE, CLOUD, HYBRID }
    
    val currentMode: Mode
        get() = secureStorage.getString("privacy_mode")?.let {
            Mode.valueOf(it)
        } ?: Mode.OFFLINE  // default
    
    val preferredCloudAdapter: String
        get() = secureStorage.getString("preferred_cloud") ?: "anthropic-claude"
    
    fun setMode(mode: Mode) {
        secureStorage.putString("privacy_mode", mode.name)
        notifyListeners()
    }
    
    fun shouldAuditDataLeave(): Boolean = currentMode != Mode.OFFLINE
    
    suspend fun logDataLeave(adapter: String, payload: String, sizeBytes: Int) {
        // Audit trail
        db.auditLogDao().insert(AuditLog(
            timestamp = System.currentTimeMillis(),
            event = "data_leave",
            adapter = adapter,
            payloadSummary = payload.take(100),
            sizeBytes = sizeBytes
        ))
    }
}
```

---

## 3. Data Flow Diagrams

### 3.1 Voice command end-to-end

```
[User says "Hey Fuu, buka WhatsApp"]
         │
         ▼
[WakeWordEngine] detects "Hey Fuu"
         │
         ▼
[VoiceLayer.state] → LISTENING; play chime
         │
         ▼
[STT (Whisper.cpp / API)] transcribe "buka WhatsApp"
         │
         ▼
[VoiceLayer.state] → PROCESSING
[CommandGateway.submitDirect("buka WhatsApp", VOICE)]
         │
         ▼
[ChibiAgent.processCommand] picks up
         │
         ▼
[InferenceRouter.selectAdapter(privacyMode=OFFLINE)] → GemmaLocalAdapter
         │
         ▼
[GemmaLocalAdapter.sendMessage(...)] → streaming
         │
         ▼ (Gemma decides)
[InferenceChunk.ToolCall(name="launch_app", args={appName: "WhatsApp"})]
         │
         ▼
[ChibiClawTools.launchApp("WhatsApp")] → onAction(LaunchAppAction)
         │
         ▼
[ActionDispatcher.dispatch] → AutoControlGate.check (allow)
         │
         ▼
[ExecutionStrategy] → "WhatsApp" in VISION_BLACKLIST? YES → VisionFirstStrategy
         │
         ▼ (atau lebih simple kalau LaunchApp pakai Intent)
[AppLauncher.launchByName("WhatsApp")]
         │
         ▼
[Android Intent system] starts WhatsApp activity
         │
         ▼
[InferenceChunk.ToolResult(name="launch_app", result="ok: launched com.whatsapp")]
         │
         ▼
[Gemma generate report] → InferenceChunk.TextDelta("WhatsApp dibuka.")
         │
         ▼
[ChibiAgent collects response] → emit ke voiceResponses flow
         │
         ▼
[VoiceLayer.state] → SPEAKING
[TTS.speak("WhatsApp dibuka.")]
         │
         ▼
[VoiceLayer.state] → IDLE (loop ke wake word listening)
```

### 3.2 Privacy toggle flow

```
[User opens Chat / Voice mode]
         │
         ▼
[Display Mode toggle: Privat (lock icon) | Cloud (cloud icon)]
         │
         ▼ (user taps toggle)
[PrivacyManager.setMode(CLOUD)]
         │
         ▼
[Check preferredCloudAdapter has API key?]
         │
    Yes ─┴─ No
    │       │
    ▼       ▼
  [OK]   [Show API Key prompt]
            │
            ▼
         [User enters key, validate via ping]
            │
            ▼
         [SecureStorage.putString("anthropic_api_key", key)]
            │
            ▼
         [Mode = CLOUD active]
```

---

## 4. Module Structure (Kotlin Package Layout)

```
com/chibiclaw/
├── agent/
│   ├── ChibiAgent.kt
│   ├── PromptBuilder.kt
│   ├── CommandHistoryStore.kt          
│   ├── ToolSafety.kt                    
│   ├── ActionDispatcher.kt              
│   ├── inference/                       ← NEW package
│   │   ├── InferenceAdapter.kt
│   │   ├── InferenceRouter.kt
│   │   ├── InferenceChunk.kt
│   │   ├── ModelCapabilities.kt
│   │   ├── adapters/
│   │   │   ├── GemmaLocalAdapter.kt
│   │   │   ├── OpenAIAdapter.kt
│   │   │   ├── AnthropicAdapter.kt
│   │   │   └── GoogleAIAdapter.kt
│   │   ├── CostMeter.kt
│   │   └── PrivacyManager.kt
│   └── memory/                          ← NEW package
│       ├── MemoryStore.kt
│       ├── EntityResolver.kt
│       ├── SemanticSearcher.kt
│       └── TextEmbedder.kt
├── voice/                               ← NEW top-level package
│   ├── VoiceLayer.kt
│   ├── WakeWordEngine.kt
│   ├── VoiceActivityDetector.kt
│   ├── stt/
│   │   ├── SpeechToTextProvider.kt
│   │   ├── WhisperLocalProvider.kt
│   │   ├── WhisperApiProvider.kt
│   │   └── DeepgramApiProvider.kt
│   ├── tts/
│   │   ├── TextToSpeechProvider.kt
│   │   ├── PiperLocalProvider.kt
│   │   ├── OpenAiTtsProvider.kt
│   │   └── ElevenLabsProvider.kt
│   └── InterruptManager.kt
├── ai/                                  
│   ├── ChibiClawTools.kt                ← consolidated 27 → 10
│   ├── GemmaEngineManager.kt            
│   ├── GemmaInference.kt                
│   └── ModelTier.kt                     
├── executor/                            
│   ├── strategies/                      ← NEW
│   │   ├── ExecutionStrategy.kt
│   │   ├── AccessibilityFirstStrategy.kt
│   │   └── VisionFirstStrategy.kt
│   └── ... (existing tier0-tier4)
├── perception/                          
├── safety/                              
├── memory/                              
│   └── ... (existing entities + DAOs, plus new ones)
├── service/                             
├── gateway/                             
├── ui/                                  
│   ├── voice/                           ← NEW package
│   │   └── VoiceOverlay.kt
│   └── ... (existing)
├── di/                                  
└── core/                                
```

**Total file estimate**: ~180 Kotlin files (dari ~150 di v3, +20% growth).

---

## 5. Cross-cutting Concerns

### 5.1 API Key Management

API keys (Anthropic, OpenAI, Google AI) wajib disimpan secure:
- **Storage**: Android Keystore + EncryptedSharedPreferences
- **Memory**: hanya di-load saat dibutuhkan, clear setelah pakai
- **Display**: di Settings selalu masked (••••••••••), copy disabled
- **Validation**: pas user input, ping API endpoint test (1 small request) untuk verify key valid sebelum save

### 5.2 Cost Meter

```kotlin
@Singleton
class CostMeter @Inject constructor(
    private val db: ChibiDatabase
) {
    private val _todayUsage = MutableStateFlow(Cost.zero())
    val todayUsage: StateFlow<Cost> = _todayUsage
    
    suspend fun recordUsage(cost: Cost) {
        db.costLogDao().insert(...)
        _todayUsage.value += cost
        if (_todayUsage.value.totalUsd > USER_DAILY_LIMIT) {
            // alert user
        }
    }
}
```

UI: Settings → Cloud Cost menampilkan grafik usage per day, monthly total, breakdown per adapter.

### 5.3 Streaming chunk normalization

Setiap adapter punya format streaming berbeda:
- Anthropic: SSE dengan `event: content_block_delta`
- OpenAI: SSE dengan `data: {"choices":[{"delta":{...}}]}`
- Google: gRPC streaming
- Gemma local: callback-based dari LiteRT-LM

Adapter normalize ke `Flow<InferenceChunk>` di output, ChibiAgent tidak peduli.

### 5.4 Cancellation propagation

Saat KillSwitch.activate():
1. ChibiAgent.collect dapat sinyal lewat killSwitch.isActive()
2. Adapter.sendMessage() dapat sinyal cancellation via coroutine cancellation
3. Cloud adapter: HTTP request abort
4. Gemma local: invoke `engine.cancel()` (LiteRT-LM)
5. Tool yang sedang execute: kalau ada cleanup, dipanggil di `finally` block

### 5.5 Context length management

Multi-model = berbeda max context:
- Gemma 4 4B: 32K tokens
- GPT-4o: 128K
- Claude Sonnet 4.6: 200K
- Gemini Flash: 1M

PromptBuilder harus aware dari adapter capability:
```kotlin
suspend fun build(adapter: InferenceAdapter): String {
    val budget = adapter.capabilities.maxContextTokens - 2000  // reserve for response
    val historyTurns = computeHistoryToFit(budget)
    return SYSTEM_PROMPT + assembleHistory(historyTurns)
}
```

---

## 6. Testing Architecture

Lihat [05-testing-strategy.md](05-testing-strategy.md) untuk detail. Summary:
- Unit test setiap adapter (mock HTTP / mock LiteRT)
- Integration test InferenceRouter (verify fallback)
- Integration test VoiceLayer state machine
- Integration test ExecutionStrategy selection
- E2E test: 20 user journey scripts, run di emulator + 1 physical device

---

**Next**: [03-tech-stack.md](03-tech-stack.md) — Tech decisions per layer with alternatives considered.
