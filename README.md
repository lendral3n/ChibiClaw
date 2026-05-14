# ChibiClaw v4

> Android AI **agent** — bukan chatbot. Fuu, asisten kawaii dengan full HP access, voice persona dengan emosi, dan visual grounding sebagai fallback saat accessibility gagal.

[![Build](https://img.shields.io/badge/build-passing-brightgreen)](https://github.com/lendral3n/chibiclaw)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![Min SDK](https://img.shields.io/badge/minSdk-28-orange.svg)](https://developer.android.com)
[![License](https://img.shields.io/badge/license-personal-lightgrey.svg)](#license)

---

## Filosofi

> "**Gemma = otak, kode = tangan.**" LLM yang ambil keputusan; kode hanya menyediakan tool, persist state, dan jalanin eksekusi.

ChibiClaw bukan chatbot dengan tool-calling tempel. Dia **agent-native**:
- **Task FSM 8-state** (PENDING → PLANNING → RUNNING → BLOCKED / AWAITING_USER → COMPLETED / FAILED / CANCELLED)
- **AgentRuntime tick loop** dengan multi-slot paralel
- **Tool catalog dinamis** — LLM baca capability dari spec, bukan hardcoded routing
- **3 channel**: CHAT (interactive), STANDING (proactive cron-like), AUTONOMOUS (event-driven)
- **Self-correction** via prompt playbook + ResourceScheduler + ErrorClass taxonomy

---

## Fitur Inti (v4 Alpha)

### Agent Core
- 🧠 **Cascade adapter**: Gemma 4 4B local → Gemini 2.5 Flash free → Claude.ai web (reverse) → ChatGPT web (reverse) → Stub
- 📋 **Task Manager** 3-slot paralel, priority queue, subtask dependency dengan depth limit 3
- 🛠️ **25 Tools**: a11y, Shizuku ADB-level, vision, OCR, messaging, intent, world query, memory, escalate, subtask
- 🔒 **SafetyGate** HIGH severity confirmation overlay 30s auto-deny + pre-authorize via standing instruction

### Voice + Emotion (Fuu)
- 🎙️ **Whisper STT** + **VAD silence detect**
- 🗣️ **ElevenLabs TTS** voice ID `gMIZZcmZCnyySbZdSZrZ` (eleven_v3) dengan emotion-aware tag
- 😊 **Emotion detector** dual-modal (audio Wav2Small + text RoBERTa go-emotions)
- 🎯 Mic button manual (skip wake word MVP)

### Vision Fallback
- 📸 **MediaProjection** capture + 1s cache + IME visibility guard
- 👁️ **MiniCPM-V 4.6** local grounding (atau Gemini multimodal sebagai fallback)
- 📝 **ML Kit OCR** untuk text extraction cepat

### Proactive Layer
- ⏰ **InitiativeEngine** tick 10s + event subscriber
- 🎯 **ComplexTrigger**: Time (cron), Event, Predicate, Composite (AND/OR/NOT), Geofence
- 🔔 Event sources: notification, battery, screen, user-present, **geofence, calendar, app-launch, network**
- 📝 **Guided form UI** untuk standing instruction (3-tab: Trigger / Task / Eksekusi)

### Memory System
- 🗂️ **5 kategori** structured: USER_PROFILE / CONTACT / HABIT / FACT / PREFERENCE
- 🔍 **Hybrid retrieval**: vector (multilingual-e5-small 384-dim) + JSON KB
- ⚒️ **Pattern miner** weekly habit candidate
- 📌 **Pin flag** immune to decay + **approve dialog** boost confidence ke 0.9
- ♻️ **Decay** confidence -0.1 setiap 60 hari stale + auto-forget di bawah 0.2

### Security & Privacy
- 🔐 **SQLCipher** AES-256 at rest
- 🔑 **EncryptedSharedPreferences** untuk API key + cloud session
- 🛡️ **Audit log** lengkap dengan PII redaction (phone/email/card)
- 👤 **Single user** (Lendra), no telemetry, no cloud transit kecuali eksplisit

---

## Arsitektur (high-level)

```
┌─────────────────────────────────────────────────────────┐
│ ChibiService (Foreground: microphone|specialUse|        │
│              mediaProjection)                            │
│                                                          │
│  ┌───────────────┐  ┌──────────────────┐  ┌──────────┐ │
│  │ AgentRuntime  │  │ InitiativeEngine │  │ Overlay  │ │
│  │  tick loop    │  │  tick + event    │  │  Bubble  │ │
│  │  3 slot ||    │  │  loop            │  │ + Panel  │ │
│  └───┬───────────┘  └──────┬───────────┘  └──────────┘ │
│      │                     │                            │
│      └─→ TaskManager ←─────┘                            │
│          │                                              │
│          ↓ next task                                    │
│      ContextBuilder (+ MemoryStore + WorldObserver)     │
│          │                                              │
│          ↓ AgentPrompt                                  │
│      InferenceRouter ─→ [Gemma | Gemini | Claude | GPT] │
│          │                                              │
│          ↓ raw LLM response                             │
│      ResponseParser (JSON → ToolCalls / done / await)   │
│          │                                              │
│          ↓ tool calls                                   │
│      ToolDispatcher ─→ SafetyGate ─→ ResourceScheduler  │
│          │                                              │
│          ↓ ToolResult                                   │
│      [persisted ke AgentStep + AuditLog]                │
└─────────────────────────────────────────────────────────┘

Event sources → EventBus:
  ChibiNotificationListener · SystemEventReceiver · NetworkObserver
  AppLaunchDetector · CalendarEventObserver · GeofenceBroadcastReceiver

Periodic workers (WorkManager):
  PatternMinerWorker (weekly habit candidate)
  MemoryDecayWorker (daily decay + auto-forget)
  AgentCleanupWorker (daily TTL expired tasks)
```

---

## Stack Teknologi

- **Kotlin 2.1+**, **Compose Material 3**, **Hilt** DI
- **Room** v6 + **SQLCipher** AES-256
- **LiteRT-LM 0.11.0** (Gemma 4 4B)
- **ONNX Runtime** (multilingual-e5-small embedding)
- **sherpa-onnx** Whisper (reflection, optional)
- **ML Kit Text Recognition v2** (OCR)
- **OkHttp 4.12** (cloud API + ElevenLabs streaming)
- **Shizuku 13.1.5** (ADB-level ops)
- **kotlinx-serialization** + **kotlinx-datetime**
- **cron-utils 9.2.1** (standing instruction time trigger)
- **Play Services Location** (geofence + FusedLocation)
- **WorkManager** (periodic miner + decay + cleanup)

---

## Progress

| Phase | Scope | Status | Commit |
|-------|-------|--------|--------|
| 0 | Foundation (Hilt + Room + Overlay + Setup wizard) | ✅ DONE | `1cb7a75` |
| 1 | Agent Core (Task FSM + AgentRuntime + 6 base tools) | ✅ DONE | `8f6547c` |
| 2 | Voice + Emotion (STT/TTS/Emotion dual-modal) | ✅ DONE | `c577d33` |
| 3 | Tools Mid (a11y + Shizuku + messaging + SafetyGate) | ✅ DONE | `86557c6` |
| 4 | Cloud Escalation (Gemini + Claude/GPT web reverse + Router cascade) | ✅ DONE | `1d993de` |
| 5 | Vision (MediaProjection + MiniCPM-V + OCR + 6 tools) | ✅ DONE | `dffee13` |
| 6 | Initiative + Standing Instructions + 4 event observers | ✅ DONE | `dbc242e` |
| 7 | Memory Maturity (templates + pattern miner + inspector) | ✅ DONE | `c364541` |
| 8 | Self-Correction + Concurrency (resource scheduler + multi-slot + subtask + cleanup) | ✅ DONE | `6db00c5` |
| 9 | Polish (navigation + migration + observers full + pre-MVP hardening) | ✅ DONE | latest |
| 10 | VRM avatar (Unity UaaL) | 🔜 future | — |

---

## Manual Test Setup

> ChibiClaw v4 sengaja **skip automated test** (sesuai keputusan arsitektur), manual test end-to-end di device fisik (Xiaomi 17 Pro Max China ROM).

### 1. Build & Install

```bash
git clone https://github.com/lendral3n/chibiclaw.git
cd chibiclaw/ChibiClaw
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 2. Push Model Files (optional, untuk runtime functional)

**Model sources (semua open-access, no auth):**

| Model | HF Repo | File | Size |
| --- | --- | --- | --- |
| **Gemma 4 E4B** | `litert-community/gemma-4-E4B-it-litert-lm` | `gemma-4-E4B-it-web.task` | ~2.96 GB |
| **multilingual-e5-small** | `intfloat/multilingual-e5-small` | `onnx/model_qint8_avx512_vnni.onnx` | ~118 MB |
| **Whisper small INT8** | `csukuangfj/sherpa-onnx-whisper-small` | encoder + decoder INT8 | ~357 MB |
| **RoBERTa go-emotions** | `SamLowe/roberta-base-go_emotions-onnx` | `onnx/model_quantized.onnx` | ~125 MB |

**Total ~3.6 GB.** Download via curl atau pakai script:

```bash
# Download semua (Gemma 4 + embedding + Whisper + RoBERTa) — no HF auth
mkdir -p ~/Downloads/chibiclaw-models && cd ~/Downloads/chibiclaw-models

# Gemma 4 E4B (essential)
curl -L -o gemma-4-4b-q4.task \
    "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it-web.task"

# E5-small embedding + tokenizer (essential)
mkdir -p e5-small && curl -L -o e5-small/model.onnx \
    "https://huggingface.co/intfloat/multilingual-e5-small/resolve/main/onnx/model_qint8_avx512_vnni.onnx"
curl -L -o e5-small/tokenizer.json \
    "https://huggingface.co/intfloat/multilingual-e5-small/resolve/main/onnx/tokenizer.json"

# Push ke device
adb shell run-as com.chibiclaw mkdir -p files/models
adb push gemma-4-4b-q4.task /data/local/tmp/
adb shell run-as com.chibiclaw cp /data/local/tmp/gemma-4-4b-q4.task files/models/
adb push e5-small/model.onnx /data/local/tmp/e5_small_q8.onnx
adb shell run-as com.chibiclaw cp /data/local/tmp/e5_small_q8.onnx files/models/
```

Tanpa model file, adapter cascade fallback ke Stub adapter (rule-based echo) — semua tool tetap callable manual via overlay chat panel + cloud adapter (Gemini API key di `local.properties`) tetap jalan.

### 3. Setup Wizard (10 step)

1. Privacy Notice
2. Overlay permission (SYSTEM_ALERT_WINDOW)
3. Vendor wizard (auto-detect 11 OEM)
4. Accessibility (Settings → ChibiClaw)
5. Shizuku (optional — install Shizuku app + ADB start)
6. Gemini API key (paste dari `aistudio.google.com/apikey`)
7. Claude.ai login via WebView (auto-extract session)
8. ChatGPT login via WebView (auto-extract session)
9. Vision MediaProjection grant
10. Done — ChibiService start, bubble muncul

### 4. Navigation (Home Dashboard)

Dari home, akses 6 fitur:
- **Chat** — interactive dengan Fuu
- **Tasks** — riwayat task aktif/historis
- **AI Engine** — adapter status + quota + re-login session
- **Standing Instructions** — direktif proaktif
- **Memory** — browse + edit + pin record
- **Debug Stats** — slots + resources + 7d error counts

---

## Roadmap Out

- **Phase 10**: VRM Avatar overlay (Unity 6.2 UaaL + Kotlin/Compose host)
- **MiniCPM-V JNI .so** custom build untuk vision local
- **bge-m3 migration** kalau e5-small bottleneck terbukti
- **Streaming proper SSE per-token** untuk cloud adapter
- **Composite trigger UI nesting** arbitrary depth

---

## Documentation

Full architecture docs di [`ChibiClaw/docs/architecture/`](ChibiClaw/docs/architecture/):
- [`00-vision-and-philosophy.md`](ChibiClaw/docs/architecture/00-vision-and-philosophy.md) — Why agent, not chatbot
- [`01-decisions-log.md`](ChibiClaw/docs/architecture/01-decisions-log.md) — ADR log
- [`10-system-architecture.md`](ChibiClaw/docs/architecture/10-system-architecture.md) — Component map
- [`11-data-model.md`](ChibiClaw/docs/architecture/11-data-model.md) — Room schema
- [`12-agent-loop.md`](ChibiClaw/docs/architecture/12-agent-loop.md) — Task FSM + AgentRuntime
- [`13-tool-catalog.md`](ChibiClaw/docs/architecture/13-tool-catalog.md) — 25 tools spec
- [`14-llm-routing.md`](ChibiClaw/docs/architecture/14-llm-routing.md) — Cascade adapter
- [`15-voice-pipeline.md`](ChibiClaw/docs/architecture/15-voice-pipeline.md) — STT/TTS/emotion
- [`16-memory-system.md`](ChibiClaw/docs/architecture/16-memory-system.md) — Hybrid retrieval
- [`17-standing-instructions.md`](ChibiClaw/docs/architecture/17-standing-instructions.md) — ComplexTrigger
- [`19-compliance-privacy.md`](ChibiClaw/docs/architecture/19-compliance-privacy.md) — Audit log + PII redaction
- [`progress-audit-phase-*.md`](ChibiClaw/docs/architecture/) — Per-phase audit log

---

## License

Personal project — Lendra ([@lendral3n](https://github.com/lendral3n)). Not licensed for redistribution.

Built with [Claude Code](https://claude.com/claude-code) (Claude Opus 4.7).
