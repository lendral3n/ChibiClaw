# 04 — Implementation Roadmap (12 Weeks)

**Audience**: Engineering, project management
**Last updated**: 2026-05-10
**Kickoff target**: 2026-05-17 (assuming v3 manual test PASS)
**Stable target**: 2026-09-06 (16 weeks total: 12 dev + 2 beta + 2 stabilization)

---

## Phase Overview

| Phase | Weeks | Focus | Deliverable | Status |
|-------|-------|-------|-------------|--------|
| **Phase 0: Validate v3** | -1 to 0 | Manual test v3 di device, fix critical bugs | v3 ship-ready snapshot | Pending user |
| **Phase 1: Foundation** | 1-4 | InferenceAdapter, multi-model, settings | APK v4.1-alpha | |
| **Phase 2: Voice Layer** | 5-8 | Wake word, STT, TTS, voice flow | APK v4.2-alpha | |
| **Phase 3: Vision-First & Polish** | 9-12 | Vision execution, memory, polish | APK v4.0-beta | |
| **Beta Test** | 13-14 | 5-10 user real test | Bug list, UX feedback | |
| **Stable** | 15-16 | Address feedback, ship | APK v4.0-stable | |

---

## Phase 0: Validate v3 (Pre-requisite)

**Goal**: Pastikan v3 baseline solid sebelum lanjut. Tidak ada gunanya build v4 di atas v3 yang broken.

**Tasks** (user-driven):
- Install APK v3 di device fisik
- Run 5 critical journey (lihat docs sebelumnya)
- Run 7 regression checklist
- Fix critical bugs jika ada

**Deliverable**: v3 ship-ready, dokumentasikan known issues.

**Estimasi**: 1-2 hari user.

---

## Phase 1: Foundation (Week 1-4)

**Goal**: Multi-model inference adapter pattern. Text chat tetap (voice belum). User bisa toggle Gemma offline / Claude / GPT / Gemini cloud.

### Week 1: Spike + InferenceAdapter Skeleton

**Day 1-2 (Spike): MediaPipe LLM Inference vs LiteRT-LM**
- Branch `spike/mediapipe-llm-inference`
- Port `GemmaInference` ke MediaPipe API
- Benchmark:
  - Parser bug rate (target: <5%)
  - Latency 100 commands (sama prompt, hitung p50/p95/p99)
  - RAM peak
  - API ergonomi
- **Decision criteria** documented in `docs/v4/spikes/01-mediapipe-vs-litert.md`
- Output: keep LiteRT atau switch MediaPipe

**Day 3-4: InferenceAdapter interface**
- File baru: `agent/inference/InferenceAdapter.kt`
- File baru: `agent/inference/InferenceChunk.kt`
- File baru: `agent/inference/ModelCapabilities.kt`
- File baru: `agent/inference/PrivacyTier.kt`
- Refactor `GemmaInference` → wrap dalam `GemmaLocalAdapter`
- File baru: `agent/inference/adapters/GemmaLocalAdapter.kt`
- ChibiAgent inject `InferenceAdapter` (default: GemmaLocalAdapter)
- Existing test passes (regression check)

**Day 5: Settings UI base**
- New screen: `ui/settings/InferenceSettingsScreen.kt`
- Show available adapters, current default
- "Add adapter" button (placeholder, opens stub)
- Privacy mode toggle: OFFLINE / CLOUD / HYBRID

**End of Week 1 deliverable**:
- ✅ MediaPipe vs LiteRT decision made
- ✅ InferenceAdapter interface ready
- ✅ Gemma local working via adapter (zero regression)
- ✅ Settings shows adapter list (only Gemma)

### Week 2: Anthropic + OpenAI Adapters

**Day 6-7: AnthropicAdapter**
- File baru: `agent/inference/adapters/AnthropicAdapter.kt`
- HTTP client: OkHttp + SSE streaming
- Models: claude-sonnet-4-6, claude-opus-4-7, claude-haiku-4-5
- Tool calling normalization: Anthropic format → ChibiAction
- API key storage: EncryptedSharedPreferences
- Unit test: mock HTTP response, verify tool call extraction

**Day 8-9: OpenAIAdapter**
- File baru: `agent/inference/adapters/OpenAIAdapter.kt`
- Models: gpt-4o, gpt-4o-mini, gpt-4-turbo
- SSE streaming format: OpenAI delta-based
- Tool calling: OpenAI tools format → ChibiAction
- Unit test

**Day 10: Settings UI - API Key Management**
- Per-adapter "Configure API Key" card
- Validate via ping endpoint
- Mask key di display (••••••)
- Cost estimator preview (placeholder, full meter di Week 4)

**End of Week 2 deliverable**:
- ✅ Claude works via API
- ✅ GPT works via API
- ✅ User bisa input API key, validate, save
- ✅ Test mode: send "test" command, verify each adapter responds

### Week 3: GoogleAI Adapter + Privacy Manager + Cost Meter

**Day 11-12: GoogleAIAdapter**
- File baru: `agent/inference/adapters/GoogleAIAdapter.kt`
- Models: gemini-2.5-flash, gemini-2.5-pro
- gRPC streaming (atau REST SSE — TBD pilih simpler)
- Tool calling: Gemini function calling → ChibiAction
- Vision support: Gemma local sudah punya, ini extend cloud

**Day 13: PrivacyManager + InferenceRouter**
- File baru: `agent/inference/PrivacyManager.kt`
- File baru: `agent/inference/InferenceRouter.kt`
- Router logic: pilih adapter berdasar mode (OFFLINE/CLOUD/HYBRID) + fallback chain
- Audit log: setiap data leave device → log ke `audit_log` table

**Day 14-15: CostMeter + Cost dashboard**
- File baru: `agent/inference/CostMeter.kt`
- Track per request: input tokens, output tokens, cost USD
- Persist ke DB (`cost_log` table baru)
- New screen: `ui/diagnostics/CostDashboardScreen.kt`
- Show: today usage, this month, breakdown per adapter, budget alert

**End of Week 3 deliverable**:
- ✅ All 4 adapters working (Gemma local, Claude, GPT, Gemini)
- ✅ Privacy mode toggle functional
- ✅ Cost meter shows realtime + history

### Week 4: Integration, Polish, Alpha

**Day 16-17: Multi-adapter integration testing**
- Run 20 sample commands through each adapter
- Verify: tool call format normalization works
- Verify: streaming chunks emit correctly
- Verify: cancellation propagates (kill switch mid-inference cancels HTTP request)
- Document edge cases di `docs/v4/spikes/02-adapter-edge-cases.md`

**Day 18: Fallback chain testing**
- Test: cloud adapter API key invalid → fallback to Gemma local
- Test: cloud adapter network down → fallback
- Test: Gemma local not loaded → block & prompt user

**Day 19: Bugfix sprint**
- Address all bugs discovered Day 16-18

**Day 20: Alpha 4.1 release**
- Build APK v4.1-alpha
- Internal test (kamu sendiri)
- Update [docs/v4/CHANGELOG.md](CHANGELOG.md)
- Tag git: `v4.1-alpha`

**End of Phase 1 deliverable**:
- ✅ APK v4.1-alpha works
- ✅ All 4 adapters integrated
- ✅ Privacy + cost UI complete
- ✅ Zero regression dari v3

**Phase 1 Risk Markers**:
- 🔴 If MediaPipe spike fails AND LiteRT v0.10 parser bug rate >30%, consider postpone v4 launch sampai LiteRT lebih stabil
- 🟡 Cloud API key onboarding too friction → simplify Settings UI

---

## Phase 2: Voice Layer (Week 5-8)

**Goal**: Voice-first interaction. Wake word "Hey Fuu" → STT → process → TTS. End-to-end voice latency <5s offline, <3s cloud.

### Week 5: STT Layer

**Day 21-22: Whisper.cpp Android integration**
- Add native dep: `whisper-cpp` via prefab
- File baru: `voice/stt/SpeechToTextProvider.kt` (interface)
- File baru: `voice/stt/WhisperLocalProvider.kt`
- Download Whisper small model (`ggml-small-q5_1.bin`, 250MB) — bundle atau download? **Decision**: download on demand di onboarding (save APK size).
- Test: 20 sample audio (Bahasa Indonesia, English, mixed) → measure WER (Word Error Rate)

**Day 23: Whisper API + Deepgram providers (cloud opt-in)**
- File baru: `voice/stt/WhisperApiProvider.kt`
- File baru: `voice/stt/DeepgramApiProvider.kt`
- BYOK: user input API key di Settings

**Day 24: VAD (Silero)**
- Add dep: `silero-vad` via ONNX Runtime Android
- File baru: `voice/VoiceActivityDetector.kt`
- Streaming detection: yield true/false per 30ms chunk

**Day 25: STT integration test**
- Sample audio → VAD → trim silence → STT → verify transcript
- Latency target: <2.5s offline, <0.8s cloud

**End of Week 5 deliverable**:
- ✅ Whisper local works
- ✅ Cloud STT options ready
- ✅ VAD streaming works

### Week 6: TTS Layer

**Day 26-27: Piper TTS Android integration**
- Add native dep: `piper-tts` via prefab
- File baru: `voice/tts/TextToSpeechProvider.kt` (interface)
- File baru: `voice/tts/PiperLocalProvider.kt`
- Download Piper voice (`id-fajri`, 60MB) on demand
- Test: synthesize sample sentences, measure latency, listen quality

**Day 28: OpenAI TTS + ElevenLabs providers**
- File baru: `voice/tts/OpenAiTtsProvider.kt`
- File baru: `voice/tts/ElevenLabsProvider.kt`
- BYOK

**Day 29: TTS UX**
- Voice selection in Settings: Fajri, Budi, Custom (cloud)
- Preview button: synthesize "Halo, saya Fuu" untuk audition

**Day 30: Streaming TTS investigation**
- Cloud TTS bisa stream audio chunks → mulai play sebelum full synthesis selesai
- Local Piper biasanya generate full audio, less benefit
- Implement streaming TTS untuk cloud opt-in (lebih natural feel)

**End of Week 6 deliverable**:
- ✅ Piper local works (Indonesian voice)
- ✅ Cloud TTS options ready
- ✅ Streaming TTS untuk cloud
- ✅ Voice preview UX

### Week 7: Wake Word + VoiceLayer Orchestration

**Day 31-32: Wake word integration**
- Train custom "Hey Fuu" model via openWakeWord notebook (offline)
  - Record 50 audio samples
  - Train ~1 jam GPU
  - Output: `hey_fuu.tflite`
- File baru: `voice/WakeWordEngine.kt`
- Continuous listening foreground service
- Battery optimization: VAD pre-filter (jangan run wake word kalau silent)

**Day 33-34: VoiceLayer state machine**
- File baru: `voice/VoiceLayer.kt`
- State machine: IDLE → LISTENING → PROCESSING → SPEAKING → IDLE
- File baru: `voice/InterruptManager.kt`
- Hooks: emit state ke UI, handle barge-in

**Day 35: VoiceOverlay UI**
- File baru: `ui/voice/VoiceOverlay.kt`
- Floating overlay window dengan pulsing circle
- Color per state: idle (gray), listening (blue), processing (purple), speaking (green)
- Tap overlay = abort current
- Long press overlay = open chat fallback

**End of Week 7 deliverable**:
- ✅ Wake word "Hey Fuu" detect (offline, <5% battery/hour idle)
- ✅ VoiceLayer state machine works
- ✅ Visual feedback via VoiceOverlay

### Week 8: End-to-End Voice + Alpha

**Day 36-37: E2E voice flow**
- Wake word → STT → ChibiAgent → TTS
- Test 10 simple commands voice-only
- Latency measurement per stage
- Target: ≤5s offline, ≤3s cloud

**Day 38: Interrupt handling**
- User say "Hey Fuu" while Fuu speaking → cut TTS, listen new command
- User long-press overlay during Fuu working → abort
- Verify cancellation propagates: TTS stop, inference cancel, tool execution abort if mid-flight

**Day 39: Onboarding flow update**
- Wake word training step: user say "Hey Fuu" 5x untuk personalization
- Voice preview: user pilih voice
- Permission grants: Microphone (essential), SYSTEM_ALERT_WINDOW (overlay)

**Day 40: Alpha 4.2 release**
- Build APK v4.2-alpha
- Update CHANGELOG
- Tag `v4.2-alpha`

**End of Phase 2 deliverable**:
- ✅ APK v4.2-alpha works voice-first
- ✅ Wake word + STT + TTS integrated
- ✅ Onboarding includes voice setup

**Phase 2 Risk Markers**:
- 🔴 Wake word false positive rate >5% → annoying user, revisit
- 🔴 Latency >7s offline → consider streaming Gemma response (synthesize TTS sambil generate)
- 🟡 Battery drain >10%/hour idle → optimize VAD threshold, sleep mode

---

## Phase 3: Vision-First & Polish (Week 9-12)

**Goal**: Vision-first execution untuk app blacklist, memory rebuild, beta polish.

### Week 9: Vision-First Strategy

**Day 41-42: ExecutionStrategy abstraction**
- File baru: `executor/strategies/ExecutionStrategy.kt`
- File baru: `executor/strategies/AccessibilityFirstStrategy.kt`
- File baru: `executor/strategies/VisionFirstStrategy.kt`
- File baru: `executor/strategies/StrategySelector.kt` (route based on app)
- Refactor `ActionDispatcher.dispatchInner` → delegate ke strategy

**Day 43-44: Vision element finder**
- Use existing `VisionActionExecutor.analyze` (multimodal Gemma)
- Extend dengan `findElement(target: String, screenshot: Bitmap): Coords?`
- Prompt template: "Given the screenshot, find the bounds of: $target. Return JSON {x, y, width, height} or null."
- Verify with screenshot diff after action

**Day 45: App blacklist + Settings UI**
- Default blacklist: TikTok, WhatsApp, Tokopedia, Shopee, Instagram, Facebook
- Settings: user-extensible blacklist
- File baru: `ui/settings/ExecutionStrategySettingsScreen.kt`

**End of Week 9 deliverable**:
- ✅ Vision-first strategy works untuk blacklist apps
- ✅ Accessibility-first tetap default untuk app lain
- ✅ User-customizable blacklist

### Week 10: Memory Store Rebuild

**Day 46-47: DB schema + entity**
- DB version bump: 4 → 5 (with explicit Migration script untuk preserve command_history & cron_task)
- New entities: `EntityRecord`, `ConversationTurn`, `SemanticIndex`, `AuditLog`, `CostLog`
- New DAOs

**Day 48-49: Embedding model integration**
- Add MiniLM L6 INT8 quantized (~25MB)
- File baru: `agent/memory/TextEmbedder.kt`
- File baru: `agent/memory/MemoryStore.kt`
- Cosine similarity search

**Day 50: Entity resolution + semantic search**
- File baru: `agent/memory/EntityResolver.kt`
- "buka chat ibu" → resolve "ibu" via alias match → fallback semantic
- Inject ke PromptBuilder: kasih top-3 relevant entities + last 5 commands

**End of Week 10 deliverable**:
- ✅ MemoryStore functional
- ✅ Entity resolution working
- ✅ Semantic search di history

### Week 11: Polish + Tool Consolidation

**Day 51-52: Tool consolidation 27 → 10**
- Refactor ChibiClawTools: merge similar tools
- New primitives:
  1. `tap(target_or_coords)` — universal tap
  2. `type(text)` — universal text input
  3. `key(name)` — keyboard keys + system keys (back, home, recents)
  4. `screenshot()` — capture for vision
  5. `intent(action, uri, package)` — Intent system
  6. `query(provider, query)` — content provider query (contacts, sms, calendar, etc.)
  7. `system(target, state)` — flashlight, volume, brightness, wifi, bluetooth, etc.
  8. `messaging(kind, recipient, body)` — SMS, WA, telegram, email
  9. `shizuku(kind, payload)` — privileged shell
  10. `meta(action, args)` — askUser, report, scheduleTask, fileManage, etc.
- Update ActionDispatcher routing
- Migration: existing prompts may need adjustment

**Day 53: UX polish**
- Onboarding flow review: <5 menit completion target
- Settings hierarchy clean
- Error messages friendlier (translate from "ui_error: node_not_found" to "Aku tidak nemu tombol itu")

**Day 54: Performance pass**
- Profile RAM usage during heavy task
- Profile battery drain idle vs active
- Optimize hot paths

**Day 55: Beta candidate build**
- APK v4.0-beta candidate
- Internal smoke test

**End of Week 11 deliverable**:
- ✅ 10 primitive tools (consolidated)
- ✅ UX polished
- ✅ Performance acceptable

### Week 12: Documentation + Beta Release

**Day 56-57: User-facing docs**
- README user guide
- Voice command examples (top 50 use cases)
- FAQ
- Troubleshooting guide

**Day 58: Developer docs (kalau open source)**
- Architecture overview (link ke docs/v4/)
- How to add custom tool
- How to add custom adapter

**Day 59: Beta APK release**
- Build APK v4.0-beta stable candidate
- Update CHANGELOG
- Tag `v4.0-beta`
- Upload GitHub Release

**Day 60: Beta tester recruitment**
- Identifikasi 5-10 user (target: Power User Indonesia + 1-2 Hands-Free Need)
- Send APK link + onboarding instructions
- Setup feedback channel (Discord / Telegram group)

**End of Phase 3 deliverable**:
- ✅ APK v4.0-beta released
- ✅ Documentation complete
- ✅ Beta testers onboarded

---

## Beta Test (Week 13-14)

**Day 61-65: Active monitoring**
- Daily check Discord/Telegram feedback
- Collect crash reports
- Triage bugs: P0 (crash), P1 (functional broken), P2 (UX), P3 (cosmetic)

**Day 66-70: Bug fix sprint**
- Address P0+P1 bugs
- UX improvements based on feedback
- Performance optimization round 2

---

## Stabilization (Week 15-16)

**Day 71-75: Final QA**
- Full regression test (all manual test plan)
- Performance benchmark final
- Lint + security audit

**Day 76-80: Stable release**
- Build APK v4.0-stable
- Final docs update
- Tag `v4.0`
- Announce (kalau publish)

---

## Milestone Calendar (Konkret)

Assuming kickoff 2026-05-17:

| Week | Date | Milestone |
|------|------|-----------|
| W1 | 2026-05-17 | Phase 1 kickoff: spike + InferenceAdapter skeleton |
| W4 | 2026-06-07 | APK v4.1-alpha — multi-model |
| W5 | 2026-06-14 | Phase 2 kickoff: STT |
| W8 | 2026-07-05 | APK v4.2-alpha — voice-first |
| W9 | 2026-07-12 | Phase 3 kickoff: vision + memory |
| W12 | 2026-08-02 | APK v4.0-beta released |
| W14 | 2026-08-16 | Beta test ends |
| W16 | 2026-08-30 | APK v4.0-stable shipped |

---

## Daily Workflow Recommendation

**Morning standup (10 min, solo):**
- What I did yesterday
- What I'll do today
- Blockers

**Daily build sanity:**
- `./gradlew assembleDebug` (must succeed before commit)
- `./gradlew testDebug` (must pass)

**Weekly retro (30 min, Friday):**
- Review week deliverable
- Update [04-implementation-roadmap.md](04-implementation-roadmap.md) (this doc) progress
- Adjust next week if behind schedule

**Branch strategy:**
- `main`: stable
- `develop`: integration of features
- `phase-1-foundation`, `phase-2-voice`, `phase-3-vision`: phase branches
- `feature/inference-anthropic-adapter` etc: per-task branches

---

## Code Review Checklist

Before merge to develop:
- [ ] Build pass (`./gradlew assembleDebug`)
- [ ] Tests pass (`./gradlew testDebug`)
- [ ] Lint clean (`./gradlew lintDebug`)
- [ ] No new unused dependencies
- [ ] Touched files have tests (kalau possible)
- [ ] CHANGELOG.md updated (kalau user-facing change)
- [ ] No secrets in code (API keys, etc.)
- [ ] Commit message follows convention: `feat(scope): ...` / `fix: ...` / `refactor: ...`

---

## Effort Estimation Summary

| Phase | Effort (working hours) | Calendar weeks |
|-------|------------------------|----------------|
| Phase 1: Foundation | 80h (4 weeks × 20h/week) | 4 |
| Phase 2: Voice Layer | 80h | 4 |
| Phase 3: Vision-First & Polish | 80h | 4 |
| Beta Test | 40h | 2 |
| Stable | 40h | 2 |
| **Total** | **320h** | **16 weeks (4 bulan)** |

Catatan: 20h/week = ~3h/day, 7 days/week. Realistic untuk solo dev. Kalau hanya weekend warrior (10h/week), durasi double = 32 minggu = 8 bulan.

---

## Decision Gates

Setelah setiap phase:
- ✅ Pass: lanjut ke phase berikut
- 🟡 Yellow: ada concern, evaluate scope reduction
- 🔴 Red: kritis broken, pause + reassess

**Phase 1 gate criteria:**
- Multi-adapter works for at least 2 cloud + Gemma local
- Cost meter accurate (verify with provider's billing dashboard sample)
- Zero regression v3 critical paths

**Phase 2 gate criteria:**
- E2E voice latency ≤5s offline, ≤3s cloud (p50)
- Wake word false positive <5%/hour
- Battery drain idle <8%/hour

**Phase 3 gate criteria:**
- Vision-first works untuk minimum 3 of 6 blacklist apps
- Memory recall accuracy ≥80% di entity resolution test set
- Tool consolidation tidak break existing functionality

---

**Next**: [05-testing-strategy.md](05-testing-strategy.md) — QA plan.
