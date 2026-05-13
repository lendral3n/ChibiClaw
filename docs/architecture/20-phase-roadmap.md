# 20 — Phase Roadmap Overview

Overview 10 phase + 1 bonus untuk ChibiClaw v4 implementation. Detail per phase di file 21-2B.

---

## Total Timeline

**21-24 minggu** (4.5-5.5 bulan). Estimate liberal — termasuk buffer untuk debug. Bisa lebih cepat kalau Lendra full-time, lebih lambat kalau part-time.

Build + install di HP cuma di akhir (Phase 9). Tidak ada test intermediate kecuali kalau Lendra explicit request.

---

## Phase Summary

| # | Nama | Durasi | Outcome | Dependencies |
|---|------|--------|---------|--------------|
| 0 | Foundation | 2 minggu | App skeleton + service + overlay + privacy | — |
| 1 | Agent Core | 5 minggu | TaskManager + AgentRuntime + Memory + Tools dasar | Phase 0 |
| 2 | Voice + Emotion | 2.5 minggu | STT + TTS + emotion detect | Phase 1 |
| 3 | Tools Mid | 3 minggu | A11y + Shizuku + messaging | Phase 1 |
| 4 | Cloud Escalation | 1.5 minggu | Gemini API + Claude/GPT web reverse | Phase 1 + 3 |
| 5 | Vision Tools | 2.5 minggu | Screenshot + MiniCPM-V + vision_tap | Phase 3 |
| 6 | Initiative + Standing | 3 minggu | InitiativeEngine + ComplexTrigger + UI editor | Phase 3 |
| 7 | Memory Maturity | 2 minggu | Knowledge graph + pattern infer + bge-m3 option | Phase 1 + 6 |
| 8 | Self-Correction + Concurrency | 2 minggu | Multi-task parallel + retry policy + resource scheduler | Phase 3 + 6 |
| 9 | Polish + Self-Test | 2 minggu | Battery + vendor + crash hardening + manual test intensif | All |
| 10 (bonus) | VRM Integration | 2-3 minggu | TTS audio → VRM Assistant lipsync IPC | Phase 9 |

Total: 21-24 minggu (mandatory) + 2-3 minggu bonus = 23-27 minggu untuk full vision.

---

## Critical Path

```
Phase 0 (foundation, 2w)
    ↓
Phase 1 (agent core, 5w) ←─── paling heavy, can't be split
    ↓
Phase 2 (voice, 2.5w) ───┐
Phase 3 (tools mid, 3w) ─┤   ← bisa parallel kalau dev dedicated
    ↓
Phase 4 (cloud, 1.5w) ───┘
    ↓
Phase 5 (vision, 2.5w) ──┐
Phase 6 (initiative, 3w) ┤   ← bisa parallel
    ↓
Phase 7 (memory, 2w) ────┘
    ↓
Phase 8 (self-correct, 2w)
    ↓
Phase 9 (polish, 2w)
    ↓
Phase 10 (VRM, 2-3w) — bonus
```

Critical path: 0 → 1 → 2 → 4 → 5 → 7 → 8 → 9 = 17 minggu.
Plus phases parallel-able: 22.5 minggu realistic untuk single dev.

---

## Phase Goals & Deliverables

### Phase 0 — Foundation (2 minggu)

**Goal:** App skeleton dengan service + overlay bubble + privacy infrastructure. No agent logic yet.

**Deliverable utama:**
- APK install jalan, ChibiService FGS active
- Floating bubble overlay (collapsed dot) visible
- Privacy notice + consent flow first launch
- Vendor wizard auto-detect 11 OEM
- Encrypted storage (SQLCipher + EncryptedSharedPreferences) ready
- Audit log infrastructure (table + writer)

**Tidak include:** LLM, TTS/STT, tool execution. App "hidup" tapi tidak agent.

---

### Phase 1 — Agent Core (5 minggu)

**Goal:** Full agent runtime: Task + AgentLoop + Memory + 6 dasar tools. Text input only via overlay chat.

**Deliverable utama:**
- Gemma 4 4B (LiteRT-LM 0.11+) loaded, basic completion works
- TaskManager + Task entity + AgentStep + FSM
- AgentRuntime tick + execute loop iteration
- WorldObserver basic (foreground app, battery, network, time)
- MemoryStore + multilingual-e5-small ONNX INT8 vector
- ToolDispatcher framework
- 6 tools: `intent_open`, `system_action`, `memory_remember`, `memory_recall`, `wait`, `await_user`, `done` (literal)
- Chat panel UI (Compose, expanded overlay state)
- Task list + detail UI (debug)
- AuditLogger lengkap (Phase 0 infra → Phase 1 usage)

**Test manual:** ketik "buka senter" di chat panel → Fuu execute via intent_open → response text di chat.

---

### Phase 2 — Voice + Emotion (2.5 minggu)

**Goal:** Voice input/output dengan emotional layer.

**Deliverable utama:**
- Whisper.cpp small Q5_1 (sherpa-onnx) streaming STT
- ElevenLabs streaming TTS v3 dengan voice ID Fuu
- AudioTrack low-latency playback
- Wav2Small audio emotion + roberta-go_emotions text emotion (sebagai context input ke LLM)
- LLM output emotion tag → ElevenLabs v3 mapping (stability, style dynamic)
- Mic button di overlay bubble → record → STT → agent loop
- Audio focus management (mic + TTS conflict)

**Test manual:** tap bubble, ketuk mic icon, bilang "halo Fuu" → Fuu jawab dengan voice clone.

**Tidak include:** wake word (ADR-006 skip MVP), audio emotion echo, pre-recorded loop.

---

### Phase 3 — Tools Mid (3 minggu)

**Goal:** Accessibility + Shizuku + messaging tools untuk control HP real.

**Deliverable utama:**
- Accessibility Service implementation + manifest registration
- Tools: `a11y_click`, `a11y_type`, `a11y_describe_screen`, `a11y_scroll`
- Shizuku 13.x SDK integration + setup wizard 5-step
- Shizuku AIDL UserService untuk low-latency exec
- Tools: `shizuku_exec`, `shizuku_force_stop`, `shizuku_grant_permission`
- Tool: `messaging` (SMS via Intent + WA via a11y/intent)
- Tools: `world_get_notifications`, `intent_send`
- Inline safety gate (HIGH severity confirm overlay)

**Test manual:** "buka Spotify lalu mainkan playlist favorit", "force-stop YouTube", "kirim SMS ke ...". Confirmation overlay muncul untuk SMS.

---

### Phase 4 — Cloud Escalation (1.5 minggu)

**Goal:** Multi-adapter LLM dengan cascade Gemma → cloud.

**Deliverable utama:**
- GeminiFreeAdapter (Google AI Studio API key, free tier)
- ClaudeWebAdapter (reverse-engineered claude.ai cookie session) — WebView headless login wizard
- GPTWebAdapter (reverse-engineered chatgpt.com)
- InferenceRouter dengan task pinning
- Tool `escalate_to_cloud(reason, target)`
- Quota tracker per adapter (Room)
- Settings UI: AI Engine dengan adapter status + login per cloud

**Test manual:** task complex yang Gemma kewalahan → LLM emit escalate_to_cloud → Gemini call → success.

---

### Phase 5 — Vision Tools (2.5 minggu)

**Goal:** Screenshot + visual grounding sebagai fallback accessibility.

**Deliverable utama:**
- MediaProjection setup wizard + persistent token
- MiniCPM-V 4.6 1.3B Q4 GGUF download + ONNX Runtime / llama.cpp Android integration
- Tools: `vision_tap(query)`, `vision_describe(query)`, `vision_extract_text(region)`
- Tool description sebutkan known fail untuk a11y → LLM yang adapt
- World query tambahan: `world_get_installed_apps`, `world_get_location`, `world_get_schedule`

**Test manual:** "balas WA Budi 'OK'" → LLM coba a11y_click search, fail, switch ke vision_tap, sukses balas.

---

### Phase 6 — Initiative + Standing Instructions (3 minggu)

**Goal:** Proactive agent. Standing instruction dengan ComplexTrigger.

**Deliverable utama:**
- StandingInstruction entity + Room storage
- InitiativeEngine tick + event-driven evaluator
- ComplexTrigger types: Time, Event (notif/battery/screen/geofence), Predicate, Composite
- CronParser + TriggerEvaluator + SimplePredicateEvaluator
- NotificationListenerService
- FusedLocationProviderClient + Geofencing
- BroadcastReceivers untuk battery / screen / network / power
- Guided form UI editor (Compose, multi-step)
- Autonomous + Standing channel routing di TaskManager

**Test manual:** create instruction "Auto-reply Mama WA antara 18-22 di luar kantor" → fire saat condition match.

---

### Phase 7 — Memory Maturity (2 minggu)

**Goal:** Smart memory dengan pattern infer + structured KB.

**Deliverable utama:**
- KnowledgeGraph categories lengkap (5 kategori dengan template value)
- Tools: `memory_infer_pattern`, `memory_list_by_category`
- Pattern miner (WorkManager periodic): scan command_history → infer habit candidates → user approve
- Migration option ke bge-m3 (kalau e5-small bottleneck)
- Memory inspection UI (Compose, browse + edit per kategori)
- Memory pruning + access count + TTL logic

**Test manual:** Fuu remember "kantor di Sudirman" otomatis setelah user mention beberapa kali.

---

### Phase 8 — Self-Correction + Concurrency (2 minggu)

**Goal:** Robust agent yang handle error + paralel task.

**Deliverable utama:**
- Error class taxonomy lengkap (SELECTOR_NOT_FOUND, PERMISSION_DENIED, etc)
- Tool result recoveryHint
- AgentRuntime multi-slot parallel (3-5 task)
- Resource scheduler (mic / screen / TTS lock)
- Priority queue di TaskManager
- Cleanup expired tasks + audit log via WorkManager

**Test manual:** 2 task paralel (background email check + foreground chat) tanpa conflict. Failed tool → LLM retry strategi beda.

---

### Phase 9 — Polish + Self-Test (2 minggu)

**Goal:** Stable untuk pemakaian pribadi sehari-hari.

**Deliverable utama:**
- Battery profiling + duty-cycle optimization
- Vendor death test Xiaomi 17 Pro Max
- Crash hardening + ANR mitigation
- Performance benchmark (latency p50/p95, memory peak)
- Compliance audit (audit log review, retention test, export/erase)
- UI polish (integrate v4 design dari [docs/Design](../Design/) ke real ViewModel)
- 1-2 minggu intensive daily use → bug fix

**Test manual:** pakai sehari-hari, minimal 1x/hari konsisten 1 minggu, ≥80% task success rate.

---

### Phase 10 — VRM Integration (Bonus, 2-3 minggu)

**Goal:** Fuu jadi VRM avatar floating di HP. Lipsync + emotion.

**Deliverable utama:**
- IPC channel ChibiClaw ↔ VRM Assistant (TTS audio stream pipe)
- VRM Assistant adopt uLipSync v3.1.4 (MIT, sample VRM 1.0 ready)
- Emotion vector → VRM expression preset (joy/sad/angry/surprised/relaxed)
- Idle animation (blink Poisson, saccade, breathing chest)
- Gaze direction (60% camera, 40% break)

Detail: [2B-phase-10-vrm.md](2B-phase-10-vrm.md).

---

## Risk Register

| Risk | Phase | Mitigasi |
|------|-------|----------|
| LiteRT-LM 0.11 Kotlin API not stable | 1 | Spike Phase 1 W1; fallback MediaPipe LLM Inference Java |
| Gemma 4 4B too slow di Snapdragon 8 Elite Gen 5 | 1 | Benchmark spike; fallback E2B 2B model |
| Reverse-engineered web session signature rotate | 4 | Maintenance contract dengan endpoint detection; fallback Gemini free |
| MiniCPM-V 4.6 grounding accuracy kurang | 5 | Try alternative (Phi-4-Multimodal, Qwen 2.5 VL) atau fallback cloud vision (Gemini Pro vision via free tier) |
| Vendor kill aggressive | 0/9 | Vendor wizard onboarding + autostart enforcement docs |
| Reverse-engineered web account ban | 4 | Rate limit ketat + explicit user consent + Gemini fallback |
| Compliance ramp-up kompleks | 9 | Tetap personal use, productize layer terpisah |
| Test cuma di akhir → bug accumulation | 9 | AuditLogger lengkap untuk easy debug, manual test 2 minggu dedicated |

---

## Definition of Done per Phase

Setiap phase punya:
1. **Code deliverable** (modul Kotlin yang compile)
2. **Documentation update** (kalau ada decision baru → ADR; kalau ada API baru → reference)
3. **Audit log entry** (apa yang di-build, apa yang di-defer)
4. **No automated test, but manual sanity check** untuk satu happy path

Tidak require test full coverage. Tidak require build APK + install per phase.

---

## After Phase 9

- v4.0 stable APK ready untuk install
- Lendra pakai sehari-hari minimal 1 bulan
- Iterasi based on real usage feedback
- Bonus Phase 10 kalau commitment 2-3 minggu lagi
- Future productize: scope ulang dengan compliance + UX polish

---

## Next Files

- Detail per phase: [21-phase-0-foundation.md](21-phase-0-foundation.md), [22-phase-1-agent-core.md](22-phase-1-agent-core.md), dst.
