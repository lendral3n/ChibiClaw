# 10 — System Architecture

High-level gambar sistem ChibiClaw v4. File ini meng-overview semua komponen, hubungannya, dan flow data utama. Detail per komponen ada di file 11-19.

---

## Big Picture

```
┌────────────────────────────────────────────────────────────────────┐
│                        ChibiService (FGS)                          │
│           foregroundServiceType="microphone|specialUse|             │
│                              mediaPlayback"                         │
│                                                                    │
│  ┌───────────────────────────────────────────────────────────┐    │
│  │                    AgentRuntime                            │    │
│  │  (orchestrator: tick + event-driven, scheduler, lifecycle) │    │
│  └─────┬──────────────────────────────────────────────────┬──┘    │
│        │                                                  │       │
│        ▼                                                  ▼       │
│  ┌──────────────┐ ┌──────────────┐ ┌─────────────┐ ┌──────────┐ │
│  │ Conversation │ │  Initiative  │ │    Task     │ │  World   │ │
│  │   Manager    │ │    Engine    │ │   Manager   │ │ Observer │ │
│  │              │ │              │ │             │ │          │ │
│  │ Routes input │ │ Eval standing│ │ CRUD task + │ │ Snapshot │ │
│  │ → task chat  │ │ instructions │ │ FSM         │ │ tiap tick│ │
│  │              │ │ tiap tick    │ │ + parallel  │ │ 5-15s    │ │
│  │              │ │              │ │ slot (3-5)  │ │          │ │
│  └──────┬───────┘ └──────┬───────┘ └─────┬───────┘ └─────┬────┘ │
│         │                │                │               │      │
│         └────────────────┴────────────────┴───────────────┘      │
│                                  │                                │
│                                  ▼                                │
│                    ┌──────────────────────┐                       │
│                    │   LLM Inference      │                       │
│                    │   Adapter Pattern    │                       │
│                    │                      │                       │
│                    │   GemmaAdapter →     │ ← default            │
│                    │   GeminiFreeAdapter →│ ← escalate           │
│                    │   ClaudeWebAdapter → │ ← long context       │
│                    │   GPTWebAdapter      │ ← alt fallback       │
│                    └──────────┬───────────┘                       │
│                               │                                   │
│                       Tool calls emitted                          │
│                               ▼                                   │
│                    ┌──────────────────────┐                       │
│                    │   Tool Dispatcher    │                       │
│                    │   (dumb executor)    │                       │
│                    └──────────┬───────────┘                       │
│                               │                                   │
│  ┌────────────────────────────┼─────────────────────────────┐    │
│  ▼                            ▼                              ▼    │
│ ┌──────────┐  ┌──────────────────┐  ┌────────────────┐  ┌──────┐│
│ │  Memory  │  │ Mobile Control   │  │  World Query   │  │ Meta ││
│ │  Tools   │  │  Tools           │  │  Tools         │  │ Tools││
│ │          │  │                  │  │                │  │      ││
│ │ remember │  │ intent_open      │  │ list_apps      │  │ wait ││
│ │ recall   │  │ a11y_click       │  │ get_schedule   │  │ await││
│ │ forget   │  │ a11y_type        │  │ get_location   │  │_user ││
│ │ list_cat │  │ shizuku_exec     │  │ get_battery    │  │ done ││
│ │          │  │ vision_tap       │  │ ...            │  │      ││
│ │          │  │ vision_describe  │  │                │  │      ││
│ │          │  │ messaging        │  │                │  │      ││
│ │          │  │ system_action    │  │                │  │      ││
│ └──────────┘  └──────────────────┘  └────────────────┘  └──────┘│
│                                                                  │
│  Tool result → fed back to LLM → next iteration                  │
│                                                                  │
│  Loop until: done / await_user / max_iteration / fatal_error    │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                  Response Composer                       │    │
│  │  Text + emotion tag + (optional) pre-recorded sample    │    │
│  └────────────────────────────┬────────────────────────────┘    │
│                               ▼                                  │
│                  ┌──────────────────────────┐                    │
│                  │ ElevenLabs Streaming TTS │                    │
│                  │ voice_id Fuu (clone)     │                    │
│                  │ model: eleven_v3         │                    │
│                  └──────────┬───────────────┘                    │
│                             ▼                                    │
│                       AudioTrack out                             │
│                             │                                    │
│                             └──→ (optional) VRM lipsync hook    │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │           Audit Logger + Compliance Log                  │    │
│  │           Room + SQLCipher · 90 hari TTL                 │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘

      ▲                                                    ▲
      │                                                    │
      │ Wake (manual tap)                          User intervene
      │ Voice input via STT                        (touch, dismiss)
      ▼                                                    │
                                                           │
┌───────────────────┐                              ┌──────────────┐
│ Overlay Bubble UI │                              │ Notification │
│ (Compose)         │                              │ Service      │
│                   │                              │ + Cron       │
│ - Idle dot        │                              │ + Geofence   │
│ - Status color    │                              │              │
│ - Tap expand chat │                              │ (event src   │
│   panel           │                              │  untuk       │
└───────────────────┘                              │  initiative) │
                                                   └──────────────┘
```

---

## Komponen Inti (8 modul utama)

### 1. AgentRuntime
**Tanggung jawab:** orchestrator utama. Tick scheduler (per 5-15 detik), event router, task slot manager, lifecycle (start/pause/stop). Singleton di ChibiService.

**Tidak punya:** decision logic. Tidak putus "task ini priority tinggi". Hanya scheduling mekanis berdasarkan policy yang user-set atau LLM-set.

### 2. ConversationManager
**Tanggung jawab:** terima input user (voice via STT atau text via overlay) → buat Task baru dengan `channel=CHAT` → submit ke TaskManager.

**Tidak punya:** parsing intent. User input mentah dikirim ke LLM as-is (LLM parser).

### 3. InitiativeEngine
**Tanggung jawab:** evaluasi StandingInstruction setiap tick. Cek trigger condition (time/event/predicate). Kalau match → buat Task channel `STANDING` dengan goal dari template instruction.

**Tidak punya:** task execution logic. Hanya gateway dari trigger → Task creation.

### 4. TaskManager
**Tanggung jawab:** CRUD Task entity di Room. Status FSM transition (PENDING → PLANNING → RUNNING → BLOCKED → COMPLETED/FAILED). Concurrency control (max 3-5 task running paralel). Priority queue. Persistence + recovery setelah crash.

**Tidak punya:** agent loop logic. Loop ada di AgentRuntime + LLM adapter.

### 5. WorldObserver
**Tanggung jawab:** snapshot world state per tick. Sumber data:
- Foreground app (UsageStatsManager / Accessibility)
- Battery level + charging state (BroadcastReceiver)
- Network state (ConnectivityManager)
- Location (FusedLocationProviderClient, opsi user opt-in)
- Time of day + calendar (ContentResolver CalendarContract)
- Recent notifications (NotificationListenerService)
- Screen on/off
- Mic on/off
- Last user active timestamp

**Tidak punya:** interpretasi state. Hanya snapshot. LLM yang interpret.

### 6. MemoryStore
**Tanggung jawab:** persistent memory layer. Room table `memory_record` dengan embedding vector. Index dan retrieval semantic.

**Sub-komponen:**
- `MemoryRepository` — CRUD record
- `EmbeddingProvider` — multilingual-e5-small ONNX INT8 inference
- `SemanticSearch` — cosine similarity top-k
- `KnowledgeGraphHelper` — query by category, traverse simple relations

Detail: lihat [16-memory-system.md](16-memory-system.md).

### 7. LLM Inference Adapter
**Tanggung jawab:** abstraksi backend LLM. Common interface untuk Gemma local, Gemini API, Claude web reverse, GPT web reverse.

**Interface:**
```kotlin
interface InferenceAdapter {
    val capability: AdapterCapability  // context len, multimodal, etc
    suspend fun complete(prompt: AgentPrompt): InferenceResult
    suspend fun stream(prompt: AgentPrompt): Flow<InferenceChunk>
}
```

Detail: lihat [14-llm-routing.md](14-llm-routing.md).

### 8. Tool Dispatcher
**Tanggung jawab:** dumb executor. Receive tool call dari LLM, dispatch ke tool implementation, return result.

**Tidak punya:** retry policy code-level. Tidak punya validation policy. Inline safety gate ada di tool level (tool itself decides), bukan dispatcher.

**Sub-komponen:**
- `ToolRegistry` — registered tools dengan metadata
- `ToolExecutor` — call dengan timeout, exception handling
- `ToolResult` — typed result (Success / Error{class, message})

Detail: lihat [13-tool-catalog.md](13-tool-catalog.md).

---

## Komponen Pendukung

### 9. ChibiService (Foreground Service)
**Tanggung jawab:** Android FGS host. Boot up AgentRuntime + sub-komponen. Manage notification (mandatory FGS). Lifecycle observer (foreground vs background mode).

**Type:** `microphone|specialUse|mediaPlayback` (Android 14+ wajib declare).

### 10. Overlay UI Layer
**Tanggung jawab:** Compose-based floating overlay. Modes:
- **Collapsed bubble** (56dp dot, status color)
- **Expanded chat** (~380x500dp panel)
- **Confirmation overlay** (untuk HIGH severity tool)

**Window:** SYSTEM_ALERT_WINDOW + `FLAG_NOT_TOUCH_MODAL`. Touch tembus ke app di bawah saat collapsed.

### 11. STT Pipeline
**Tanggung jawab:** Audio capture → text. Whisper.cpp small Q5_1 via sherpa-onnx (offline default). Atau Gemini Realtime (online opt-in).

Detail: lihat [15-voice-pipeline.md](15-voice-pipeline.md).

### 12. TTS Pipeline
**Tanggung jawab:** Text + emotion tag → audio stream → AudioTrack playback. ElevenLabs streaming API v3 dengan voice clone Fuu (`gMIZZcmZCnyySbZdSZrZ`).

### 13. Emotion Detector
**Tanggung jawab:** signal generator untuk LLM. Audio (Wav2Small ONNX) + text (roberta-go_emotions INT8) → VAD vector + 28 emotion probs. Kirim sebagai context input ke LLM, BUKAN sebagai decision policy.

### 14. AuditLogger
**Tanggung jawab:** record semua aksi yang affect user data atau eksternal state. Room table `audit_log` + SQLCipher. Auto-rotate 90 hari TTL. Export to user-demand (CSV/JSON).

Detail compliance: lihat [19-compliance-privacy.md](19-compliance-privacy.md).

### 15. Adapter Setup Wizard
**Tanggung jawab:** UI flow untuk:
- Permission setup (mic, accessibility, overlay, notification listener)
- Vendor wizard (per-OEM battery / autostart setup)
- Shizuku setup 5-step (ADB pairing + run)
- Cloud login WebView headless (Claude.ai, ChatGPT, Gemini)
- Voice ID test ElevenLabs

Setiap step bisa skip atau retry. State tersimpan di SecurePreferences.

---

## Data Flow Utama

### Flow A — User Voice Command (Chat Channel)

```
User tap bubble → expand panel → tap mic
   ↓
ChibiService start mic AudioRecord (16kHz mono)
   ↓
Audio stream → Whisper.cpp streaming → text "kirim WA ke Budi"
   ↓ (parallel)
Wav2Small → audio VAD vector
   ↓
ConversationManager.handleUserInput(text, audioEmotion)
   ↓
Create Task { channel=CHAT, goal="kirim WA ke Budi", trigger=null }
   ↓
TaskManager.enqueue(task) → slot available → start
   ↓
AgentRuntime invoke agent loop on task
   ↓
[loop start]
   PromptBuilder.build(task, world, memory, history, tools, emotion)
   ↓
   InferenceAdapter (Gemma local) .complete(prompt)
   ↓
   Parse response → ToolCalls?
       ↓ yes
       For each call:
         ToolDispatcher.execute(call) → result
         task.appendStep(thought, call, result)
         AuditLogger.log(call, result)
       ↓
   continue loop
[loop end when LLM emit `done(summary)`]
   ↓
Task.complete(summary) + emotion tag from response
   ↓
ResponseComposer build (text + emotion + optional pre-recorded sample)
   ↓
ElevenLabs streaming TTS (voice Fuu)
   ↓
AudioTrack playback
   ↓
Overlay status update (idle gray)
```

### Flow B — Standing Instruction Fires (Standing Channel)

```
WorldObserver tick (every 5-15s)
   ↓
InitiativeEngine.evaluateAll()
   ↓
For each StandingInstruction.enabled:
   trigger.evaluate(worldSnapshot, lastFired) → bool
   ↓
   if match + cooldown OK:
      Create Task { channel=STANDING, goal=instruction.task_template_render(), trigger=instructionRef }
      TaskManager.enqueue(task) with priority based on instruction
   ↓
[Task lifecycle sama seperti Flow A, tapi:]
- Overlay NOT expanded (silent execution)
- Result: kalau success silent, kalau failed → notif
```

### Flow C — Notification Listener Trigger (Autonomous Channel)

```
NotificationListenerService receives StatusBarNotification
   ↓
Event dispatch → InitiativeEngine via event bus
   ↓
[evaluate semua EventTrigger yang listen NotificationListener]
   ↓
Match → Task creation channel=AUTONOMOUS
   ↓
[similar to Flow B]
```

### Flow D — Cloud Escalation (Tool-Driven)

```
[di tengah agent loop, Flow A]
LLM emit ToolCall: escalate_to_cloud(reason="task too complex for Gemma", target="claude")
   ↓
ToolDispatcher route ke EscalationTool
   ↓
EscalationTool:
   1. Cek adapter target available (ClaudeWebAdapter session valid?)
   2. Snapshot current task state (history, world, memory)
   3. Re-invoke agent loop dengan adapter=ClaudeWebAdapter
   4. Inject result balik ke task continuation
   ↓
Task continues dengan adapter cloud
```

### Flow E — Self-Correction After Tool Error

```
[di tengah agent loop]
ToolDispatcher.execute(call) → ToolResult.Error { error_class: SELECTOR_NOT_FOUND }
   ↓
task.appendStep(thought, call, errorResult)
   ↓ (no special code handling)
LLM next iteration:
   PromptBuilder include errorResult in history
   LLM reason: "selector not found → coba vision_tap"
   LLM emit ToolCall: vision_tap(query="message input field")
   ↓
ToolDispatcher.execute → success
   ↓
Task continues
```

LLM yang adapt strategi. Code tidak punya retry policy hardcoded.

---

## Memory & State Lifecycle

```
App startup
   ↓
ChibiService.onCreate()
   ↓
AgentRuntime.boot():
   - Load Gemma 4 4B (LiteRT-LM) → 30-60s
   - Init MemoryStore (Room migrate, embedding model load)
   - Init WorldObserver (subscribe broadcast receivers)
   - Init InitiativeEngine (load StandingInstructions)
   - Init ConversationManager (mic init)
   - TaskManager.resumeIncomplete() → recovery dari crash
   ↓
Service running, tick loop alive
   ↓
[idle state: no task running, bubble collapsed gray]
   ↓
[user trigger atau initiative trigger] → Flow A/B/C
   ↓
[task lifecycle, parallel slots]
   ↓
App killed (vendor / user / OOM)
   ↓
ChibiService.onDestroy():
   - TaskManager checkpoint incomplete tasks
   - MemoryStore flush
   - AudioRecord release
   ↓
BroadcastReceiver BOOT_COMPLETED → re-launch ChibiService
   ↓
[bootstrap via specialUse type → upgrade to microphone after user tap notif]
```

---

## Concurrency Model

Multi-task parallel slot (3-5 max). Resource conflict resolution:

| Resource | Sharing | Conflict Resolution |
|----------|---------|---------------------|
| Mic | Single-task | Task incoming mic: pause task that holds mic, switch, resume |
| Screen capture (MediaProjection) | Single-task | Queue task that need screen, await release |
| TTS audio out | Single-task | Interrupt current TTS if higher-priority task speaks |
| Accessibility action | Multi-task safe | OK paralel (different app each) |
| Shizuku exec | Multi-task safe | Throttle to avoid binder overload |
| LLM call | Multi-task | Per adapter quota (Gemma local: 1 sequential; cloud: parallel up to rate limit) |

Conflict logic ada di AgentRuntime scheduler — bukan keputusan LLM. Ini implementation detail.

---

## Module Dependency Graph

```
service (ChibiService)
   ↓ depends
runtime (AgentRuntime, scheduler)
   ↓ depends
managers (TaskManager, ConversationManager, InitiativeEngine, WorldObserver)
   ↓ depends
core (PromptBuilder, ToolDispatcher, InferenceAdapter abstract)
   ↓ depends
adapters (GemmaAdapter, GeminiFreeAdapter, ClaudeWebAdapter, GPTWebAdapter)
tools/* (intent, a11y, shizuku, vision, memory, world_query, meta)
memory (MemoryStore, embedding)
voice (STT, TTS, EmotionDetector)

ui (Overlay, ChatPanel, ConfirmOverlay, SetupWizard, TaskListUI)
   ↓ depends
service + viewmodels (lifecycle-aware)

data (Room entities, DAOs, migrations)
   ↓ depends only on Room runtime + kotlinx-serialization

util (audit logger, encrypted prefs, vendor wizard helpers)
```

---

## Persistence Layer (Room)

Tabel utama:
- `task` (id, parent_id, channel, goal, status, priority, created_at, ...)
- `agent_step` (id, task_id, step_index, thought, tool_call_json, tool_result_json, timestamp)
- `memory_record` (id, category, key, value_json, embedding_blob, ...)
- `standing_instruction` (id, name, trigger_json, task_template, enabled, ...)
- `command_history` (legacy compat, mungkin merge ke task)
- `audit_log` (id, action_type, data_summary, cloud_destination, timestamp, ttl_until)
- `model_config` (key, value, ttl?)  // adapter quota tracker, preferred adapter, etc

Detail: lihat [11-data-model.md](11-data-model.md).

---

## Threading Model

- **Main thread (UI)**: Compose recomposition, overlay drag, click handler. NO heavy work.
- **Service thread (ChibiService dispatcher)**: ChibiService lifecycle callbacks, broadcast receive.
- **Agent thread (Coroutine Default dispatcher)**: AgentRuntime tick loop, task execution.
- **LLM thread (single)**: Gemma inference (CPU/NPU intensive). Single thread untuk mencegah OOM.
- **Audio thread**: AudioRecord callback, Whisper streaming.
- **Tool dispatch thread (IO dispatcher)**: tool execute (network, Shizuku binder, screenshot).
- **Embedding thread (Default)**: e5-small ONNX inference (sporadic per memory write/recall).

Coroutine structured concurrency, lifecycle-tied ke ChibiService.

---

## Failure Modes & Resilience

| Failure | Detection | Response |
|---------|-----------|----------|
| Gemma load fail (OOM) | LiteRT-LM exception | Fallback ke cloud Gemini, retry Gemma on next boot |
| Vendor kill ChibiService | onDestroy called | BOOT_COMPLETED receiver re-launch, TaskManager resume |
| Mic acquire fail | AudioRecord init error | Disable voice input, fallback text-only |
| Cloud auth expired | HTTP 401 dari adapter | Notify user re-login WebView, queue task pending |
| Tool timeout | Coroutine timeout | Return ToolResult.Error(TIMEOUT) → LLM retry |
| LLM infinite loop | iteration count > max | Task.fail("max iteration reached"), report |
| OOM | OnTrimMemory callback | Pause non-critical task, evict task history cache |
| Screen lock during task | KeyguardManager state | Pause UI-interactive task, resume on unlock |
| Network down | Connectivity broadcast | Disable cloud adapter, force-local-only mode |

---

## Next Files

- Data schema detail: [11-data-model.md](11-data-model.md)
- Agent loop implementation: [12-agent-loop.md](12-agent-loop.md)
- Tool catalog dengan spec: [13-tool-catalog.md](13-tool-catalog.md)
- LLM adapter detail: [14-llm-routing.md](14-llm-routing.md)
- Voice pipeline: [15-voice-pipeline.md](15-voice-pipeline.md)
- Memory system: [16-memory-system.md](16-memory-system.md)
- Standing instruction: [17-standing-instructions.md](17-standing-instructions.md)
- Execution strategy LLM-driven: [18-execution-strategy.md](18-execution-strategy.md)
- Compliance & audit: [19-compliance-privacy.md](19-compliance-privacy.md)
