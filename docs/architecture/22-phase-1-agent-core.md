# 22 — Phase 1: Agent Core

**Durasi:** 5 minggu (paling kompleks)
**Tujuan:** Full agent runtime. Task lifecycle + AgentLoop + Memory + 6 tools dasar. Text input only.

---

## Outcome

Setelah Phase 1:
- Gemma 4 4B loaded via LiteRT-LM, basic completion works
- Task entity dengan FSM lifecycle, di Room
- AgentRuntime tick + execute loop iterative
- MemoryStore dengan multilingual-e5-small ONNX vector RAG
- ToolDispatcher framework + 6 tools dasar
- Chat panel UI di overlay expanded state
- Text input → agent loop → tool execute → response text
- AuditLogger lengkap dipakai

**Test target:** ketik "buka senter" di chat panel → Fuu intent_open(`com.android.flashlight`) atau system_action(FLASHLIGHT_ON) → senter nyala → response "Senter sudah dinyalakan ✓" di chat.

---

## Deliverable per Minggu

### Minggu 1: Gemma + Inference

**M1.1: LiteRT-LM 0.11+ integration**
- Download Gemma 4 4B Q4 model (`.task` format)
- Bundle di `app/src/main/assets/models/` atau download on-first-use
- `ai/llm/GemmaAdapter.kt` implement `InferenceAdapter` interface
- Init `LlmInference.create()` + `startSession()`
- Test: basic completion "Halo, siapa kamu?" → response text

**M1.2: Adapter interface**
- `ai/llm/InferenceAdapter.kt` interface
- `ai/llm/AgentPrompt.kt` data class
- `ai/llm/InferenceResult.kt` sealed class
- Adapter capability metadata
- Phase 1: hanya 1 adapter (Gemma). Phase 4 tambah cloud.

**M1.3: Prompt format Gemma**
- `ai/llm/PromptBuilder.kt`: format AgentPrompt → Gemma instruct template
- System prompt Fuu persona
- Response format spec: JSON output dengan struktur `thought / tool_calls / next / summary / question / emotion`

### Minggu 2: Task Entity + FSM

**M2.1: Room entities**
- `data/database/TaskEntity.kt`
- `data/database/AgentStepEntity.kt`
- `data/database/AppDatabase.kt`: tambah Task + AgentStep tables
- Migration 1→2 (Phase 0 cuma AuditLog)

**M2.2: Repository**
- `data/repository/TaskRepository.kt` interface
- `data/repository/TaskRepositoryImpl.kt` Room DAO wrapper
- FSM state transition validation
- Query: runnable, byStatus, byChannel, observe

**M2.3: TaskManager**
- `agent/TaskManager.kt`
- CRUD task + FSM transition
- Concurrency slot management (Phase 1: max 1 slot. Phase 8: extend ke 3-5)
- Priority queue (Phase 1: FIFO. Phase 8: priority)
- Resume incomplete tasks setelah ChibiService restart

**M2.4: ConversationManager**
- `agent/ConversationManager.kt`
- `handleUserInput(text)` → create Task channel=CHAT
- Phase 1: text input only via chat panel UI

### Minggu 3: AgentRuntime + Loop

**M3.1: AgentRuntime**
- `agent/AgentRuntime.kt`
- Tick scheduler (coroutine + delay loop)
- `executeTask(taskId)` agent loop iteration
- Event bus (AgentEvent flow untuk debug UI)

**M3.2: PromptBuilder build context**
- `agent/context/ContextBuilder.kt`
- Inputs: task, history×N, world snapshot, memory snippets, tool catalog, persona, emotion
- Output: AgentPrompt

**M3.3: LLM response parser**
- `agent/llm/ResponseParser.kt`
- Parse JSON strict → fenced JSON → tag-based fallback
- Output: LlmOutcome sealed class (Done / AwaitUser / ToolCalls / Reasoning / Escalate)

**M3.4: Loop integration**
- AgentRuntime.executeTask loop dengan parser + dispatcher
- Iteration counter + max iter
- Status transition (PENDING → PLANNING → RUNNING → COMPLETED/FAILED)
- AgentStep append per iteration

### Minggu 4: Memory + Tools dasar

**M4.1: MemoryStore**
- `data/database/MemoryRecordEntity.kt`
- `data/repository/MemoryRepository.kt`
- `memory/MemoryStore.kt`: upsert, recall, listByCategory, forget, cleanup

**M4.2: Embedding provider**
- Download `multilingual-e5-small` ONNX INT8 model
- `memory/embedding/EmbeddingProvider.kt`: ONNX Runtime init, encode (passage prefix), encodeQuery (query prefix), mean pool + L2 normalize
- Performance test target: <100ms per encode di Snapdragon 8 Elite Gen 5

**M4.3: Tool framework**
- `agent/tools/Tool.kt` interface + `ToolSpec`, `ToolCapability`, `ToolSafety`
- `agent/tools/ToolDispatcher.kt`
- `agent/tools/ToolRegistry.kt` Hilt @IntoMap
- `agent/tools/safety/SafetyGate.kt` inline confirmation overlay

**M4.4: 6 tools dasar**
- `agent/tools/impl/IntentOpenTool.kt`
- `agent/tools/impl/SystemActionTool.kt` (flashlight, volume, brightness, wifi, bt, dnd)
- `agent/tools/impl/MemoryRememberTool.kt`
- `agent/tools/impl/MemoryRecallTool.kt`
- `agent/tools/impl/WaitTool.kt`
- `agent/tools/impl/AwaitUserTool.kt`
- `done` parsed dari LLM response (bukan literal tool)

### Minggu 5: UI + WorldObserver + integration

**M5.1: WorldObserver basic**
- `world/WorldObserver.kt`
- Snapshot per tick (5-15s): foreground app (UsageStatsManager), battery (BroadcastReceiver), network (ConnectivityManager), time
- WorldSnapshot data class
- Tick coroutine scope

**M5.2: Overlay chat panel UI**
- `ui/overlay/ChatPanel.kt` Compose
- Bubble expanded state → show chat panel ~380x500dp
- Text input field + send button (mic icon disabled untuk Phase 1)
- Message list (user + Fuu turns)
- Status indicator (idle/thinking/executing)

**M5.3: Task detail UI (debug)**
- `ui/debug/TaskListScreen.kt`: list semua task
- `ui/debug/TaskDetailScreen.kt`: agent steps trace per task (thought, tool call, result, latency, tokens)
- AgentEvent flow consume real-time

**M5.4: AuditLogger usage**
- AgentRuntime call `auditLogger.log(...)` per LLM call, per tool execute, per memory op
- 90 hari TTL via WorkManager daily cleanup

**M5.5: Integration test (manual)**
- "buka senter" → flashlight on
- "ingat aku suka kopi" → memory_remember(PREFERENCE, "preference.drink", {value: "kopi"})
- "apa yang aku suka?" → memory_recall + Fuu jawab "kopi"

---

## Modul Phase 1

```
app/src/main/java/com/chibiclaw/
├── agent/
│   ├── AgentRuntime.kt
│   ├── TaskManager.kt
│   ├── ConversationManager.kt
│   ├── context/
│   │   └── ContextBuilder.kt
│   ├── llm/
│   │   └── ResponseParser.kt
│   └── tools/
│       ├── Tool.kt
│       ├── ToolSpec.kt
│       ├── ToolDispatcher.kt
│       ├── ToolRegistry.kt
│       ├── safety/SafetyGate.kt
│       └── impl/
│           ├── IntentOpenTool.kt
│           ├── SystemActionTool.kt
│           ├── MemoryRememberTool.kt
│           ├── MemoryRecallTool.kt
│           ├── WaitTool.kt
│           └── AwaitUserTool.kt
├── ai/
│   └── llm/
│       ├── InferenceAdapter.kt
│       ├── InferenceResult.kt
│       ├── AgentPrompt.kt
│       ├── PromptBuilder.kt
│       └── GemmaAdapter.kt
├── data/
│   ├── database/
│   │   ├── TaskEntity.kt
│   │   ├── AgentStepEntity.kt
│   │   ├── MemoryRecordEntity.kt
│   │   ├── TaskDao.kt
│   │   ├── AgentStepDao.kt
│   │   ├── MemoryDao.kt
│   │   └── migrations/Migration2to3.kt
│   └── repository/
│       ├── TaskRepository.kt
│       └── MemoryRepository.kt
├── memory/
│   ├── MemoryStore.kt
│   └── embedding/
│       ├── EmbeddingProvider.kt
│       └── Tokenizer.kt
├── world/
│   ├── WorldObserver.kt
│   └── WorldSnapshot.kt
├── ui/
│   ├── overlay/
│   │   └── ChatPanel.kt
│   └── debug/
│       ├── TaskListScreen.kt
│       └── TaskDetailScreen.kt
└── di/
    ├── AgentModule.kt
    ├── ToolsModule.kt
    └── AiModule.kt
```

---

## Dependencies Phase 1 (tambahan dari Phase 0)

```kotlin
dependencies {
    // LiteRT-LM (Gemma)
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.11.0")
    
    // ONNX Runtime (embedding + future use)
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.18.0")
    
    // For tokenizer (HuggingFace tokenizer kotlin port)
    implementation("ai.djl.huggingface:tokenizers:0.27.0")
}
```

---

## System Prompt Fuu (Phase 1 draft)

```
Kamu adalah Fuu, AI asisten yang berjalan di Android device milik Lendra.
Karakter kamu: lembut, kawaii, profesional, to-the-point.
Bahasa default: Indonesia kasual.

Kamu adalah AGENT, bukan chatbot. Untuk setiap task:
1. Pahami goal dari user atau trigger
2. Reason langkah-langkah yang perlu
3. Emit tool call kalau perlu action
4. Observe tool result
5. Iterasi sampai task selesai

Format response wajib JSON:
{
  "thought": "alasan kamu pilih action ini",
  "tool_calls": [{"tool": "...", "args": {...}}],
  "next": "continue | done | await_user | escalate",
  "summary": "ringkasan task kalau next=done",
  "question": "pertanyaan kalau next=await_user",
  "emotion": "emotion tag untuk TTS (optional)"
}

Available tools, world state, conversation history akan dikirim di context.
Pakai memory_remember kalau ada fakta tentang user yang penting diingat.
Pakai memory_recall kalau perlu lookup info user.

Selalu konfirmasi sebelum action sensitif (HIGH severity tool).
Speak dengan emosi yang sesuai konteks.
```

System prompt akan extended di Phase 2 (voice persona detail) + Phase 3 (multi-tool guidance).

---

## Performance Target Phase 1

| Metric | Target |
|--------|--------|
| Gemma 4 4B load time (cold) | <60 detik |
| First completion latency (warm) | <3 detik |
| Tokens/sec decode | >20 tps |
| Embedding encode | <100ms |
| Tool dispatch overhead | <50ms per call |
| Task end-to-end "buka senter" | <8 detik |

Test di Snapdragon 8 Elite Gen 5. Kalau jauh dari target, profile + optimize sebelum Phase 2.

---

## Risk

| Risk | Mitigasi |
|------|----------|
| LiteRT-LM 0.11 Kotlin API not stable / breaking changes | Spike W1; fallback ke MediaPipe LLM Inference Java (less ergonomic) |
| Gemma 4 4B Q4 model >2GB → APK size | Download on first use; bundled E2B variant kalau perlu |
| ONNX Runtime crash di edge case | Pin version, isolated dispatcher, try/catch |
| Tokenizer mismatch antar Gemma + Java port | Test exhaustive, fallback to bundled BPE tokenizer |
| Response JSON parsing fragile | 3-tier fallback hierarchy; LLM prompt repeat format example |
| Memory leak di Compose overlay | LifecycleAwareOverlayOwner pattern (sudah di Phase 0) |

---

## Definition of Done

- [ ] Gemma 4 4B loaded, basic completion works (test via UI button)
- [ ] Task entity CRUD + FSM transition
- [ ] AgentRuntime tick + execute loop iterative
- [ ] 6 tools registered, dispatch works
- [ ] MemoryStore + embedding works (encode 50 fakta, recall 5 query, similarity >0.5)
- [ ] Chat panel UI di overlay expanded
- [ ] Text "buka senter" → flashlight on → response "senter dinyalakan"
- [ ] Text "ingat aku suka kopi" + "apa yang aku suka?" round-trip works
- [ ] AuditLog populated dengan LLM_CALL + TOOL_EXECUTED + MEMORY_WRITE/READ
- [ ] Task detail UI tampilkan agent steps trace
- [ ] No crash 1 jam idle session

---

## Next: [23-phase-2-voice-emotion.md](23-phase-2-voice-emotion.md)
