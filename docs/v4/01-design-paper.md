# 01 — Design Paper: ChibiClaw v4

**Title**: ChibiClaw v4 — Multi-Model Voice-First Mobile AI Agent
**Author**: Lendra
**Status**: Design Phase
**Last updated**: 2026-05-10

---

## Abstract

ChibiClaw v4 adalah evolusi besar dari v3 yang sukses memenangkan tantangan arsitektur (post-refactor: 5 sistem keputusan paralel → 1 tool-centric agent), tapi belum cukup untuk klaim "asisten" (still text-chat, single offline model, accessibility-first).

v4 melakukan 3 pivot strategis:

1. **Inference layer modular** — multi-model adapter pattern. Default offline (Gemma), opsional cloud (Claude/GPT/Gemini) per session.
2. **Voice-first interaction** — wake word "Hey Fuu", end-to-end voice latency <3 detik untuk simple commands, dengan text fallback.
3. **Vision-first execution untuk app blacklist** — accessibility-resistant apps (TikTok, WA, Tokped, Shopee, IG) dieksekusi via screenshot + vision + gesture, bukan accessibility tree.

Estimasi effort: **12 minggu (3 bulan)** untuk ship beta. Total LOC change: +25% dari v3.

---

## 1. Problem Statement

### 1.1 Apa yang v3 sudah capai
- Arsitektur tool-centric agent yang bersih (Gemma yang putuskan, kode yang eksekusi)
- 27 tool yang terhubung ke 4-tier executor (Intent → ContentResolver → Accessibility → Shizuku)
- Single source of truth via @Tool annotation
- Inline safety gate di 4 HIGH-risk tool (post Fase 3)
- Build assembleDebug sukses, APK 345MB valid

### 1.2 Apa yang v3 belum bisa

| Pain point | Impact |
|------------|--------|
| Tidak ada voice — masih text chat | "Asisten" tanpa voice = kontradiksi vision |
| Single model (Gemma 4) | User tidak bisa pilih kualitas vs privasi vs biaya |
| Cloud failover tidak ada | Offline-only kadang terlalu lambat untuk task kompleks |
| Accessibility-first execution rapuh | TikTok/WA/Shopee deteksi otomasi → ban risk |
| 27 tools = banyak choice untuk Gemma | Tool selection error rate tinggi |
| Tidak ada long-term memory | Asisten yang "lupa" tiap sesi = bukan asisten |
| Latency 2-4 detik per simple command | Tidak ada FastPath, semua lewat full inference |

### 1.3 Apa yang user butuhkan tapi belum ada

User explicit di percakapan strategis (2026-05-10):
> "Asisten di handphone, jika sudah bisa menyebut asisten maka dia bisa melakukan apa aja agar bisa kita suruh dan menggantikan kita. ... selain itu bukan chat tapi voice, jadi bisa ngobrol dan full akses ke handphone."

Decoded:
- **Voice-native**, bukan augment
- **Full akses HP** (tidak ada batasan kapabilitas yang awkward)
- **"Menggantikan kita"** = trust & reliability tinggi
- Multi-model fleksibel (user mention GPT, Gemini, Claude, plus Gemma)

---

## 2. Solution Overview

### 2.1 Design philosophy

| Prinsip | Penjelasan |
|---------|-----------|
| **Privacy as a choice, not a default** | User toggle per session: offline (privat) atau cloud (pintar). Tidak hidden behavior. |
| **Voice as primary, text as fallback** | Wake word + STT + TTS adalah default. Text input untuk situasi tidak nyaman bicara (di mall ramai, meeting, dll). |
| **Vision as first-line for hostile apps** | Apps yang deteksi accessibility otomasi (anti-bot) → langsung vision path, skip accessibility. |
| **Adapter over monolith** | Inference adapter, voice adapter, vision adapter. Setiap layer pluggable. |
| **Cost transparency** | Kalau pakai cloud, user lihat cost meter realtime. Tidak ada surprise bill. |
| **Fail open ke offline** | Cloud API down / network mati → seamless fallback ke Gemma local. |

### 2.2 Core architectural changes

```
v3:                                      v4:
                                         
    ChatScreen (text only)                  VoiceLayer (wake → STT)
        ↓                                       ↓                    ↓
   CommandGateway                            CommandGateway         ChatScreen (text fallback)
        ↓                                       ↓
   ChibiAgent                               ChibiAgent
        ↓                                       ↓
   GemmaInference (local only)              InferenceAdapter ←─┐
        ↓                                       ├──────────────│
   ChibiClawTools (27 tools)                    Gemma | Claude | GPT | Gemini
        ↓                                                       │
   ActionDispatcher (30 deps)                ChibiClawTools (10 primitif)
        ↓                                       ↓
   Executors (4-tier)                        ActionDispatcher (consolidated)
                                                ↓
                                             ┌──┴──────┐
                                          Vision    Accessibility    Intent/System
                                          (first    (fallback        (fast path)
                                          line for  for non-
                                          black-   blacklisted)
                                          listed)
                                                ↓
                                             TTS (kalau voice mode)
```

### 2.3 Key components

#### A. **InferenceAdapter** (new)
Abstraction untuk model inference. Implementasi:
- `GemmaLocalAdapter` — wrap existing GemmaInference (current v3 code)
- `OpenAIAdapter` — GPT-4o, GPT-4o-mini via OpenAI API
- `AnthropicAdapter` — Claude Sonnet 4.6, Opus 4.7 via Anthropic API
- `GoogleAIAdapter` — Gemini Pro, Gemini Flash via Google AI API

User pilih default + per-session override. Tool calling protocol normalized — adapter convert dari format vendor ke unified ChibiAction.

#### B. **VoiceLayer** (new)
Pipeline:
```
Microphone → VAD → Wake word detector → STT → ChibiAgent
                                          ↓
                            (process command, get response)
                                          ↓
                                       TTS → Speaker
```

Components:
- VAD: Silero VAD (offline, low CPU)
- Wake word: openWakeWord (custom train "Hey Fuu" model)
- STT: Whisper.cpp Android (small/medium model) | OpenAI Whisper API (cloud)
- TTS: Piper TTS Android (offline) | OpenAI TTS / ElevenLabs (cloud)

Interrupt handling: Fuu sedang bicara, user mulai bicara → VAD detect → TTS pause → STT capture.

#### C. **VisionFirstExecutor** (new)
Override accessibility-first untuk app blacklist. Flow:
```
For UiInteractAction(action="tap", target="Login button"):
  if currentForegroundApp in BLACKLIST {
    screenshot()
    coords = visionModel.findElement("Login button")
    gestureDispatcher.tap(coords)
    verifyScreenshot()  // verify state changed
  } else {
    accessibilityExecutor.perform(action)  // fast path
  }
```

App blacklist (initial): TikTok, WhatsApp, Tokopedia, Shopee, Instagram, Facebook.
User-extensible via Settings.

#### D. **MemoryStore** (rebuild dari v3 yang dihapus)
Vector store untuk semantic search di:
- Command history (semua command + result)
- Entity store (nama → kontak, lokasi, app preference)
- Conversation context (last N turns dengan reference resolution)

Tech: SQLite + ObjectBox vector index (compact, on-device).
Encrypted at rest dengan SQLCipher.

#### E. **PrivacyManager** (new)
Top-level coordinator untuk privacy mode:
- Default: offline mode (no data leaves device)
- User toggle: cloud mode per session
- Visible indicator di UI (lock icon = offline, cloud icon = cloud)
- Audit trail: log setiap data leave device dengan timestamp + recipient

---

## 3. User Experience Flows

### 3.1 First-time onboarding (target: <5 menit)

```
1. Install APK → Open
2. Welcome screen — explain Fuu
3. Microphone permission grant
4. Choose voice: "Suara Fuu" preview (3 voice options)
5. Choose model strategy:
   [A] Privat saja (offline only, gratis, ~3-4s latency)
   [B] Pintar (cloud, butuh API key, ~1-2s latency, $X/bulan estimate)
   [C] Hybrid (default offline, cloud opt-in per session)
6. Kalau pilih B/C: input API key (BYOK) atau skip
7. Accessibility permission grant (with explainer "kenapa butuh ini")
8. Optional: Shizuku setup tutorial
9. Wake word training (5x say "Hey Fuu" untuk personalization)
10. First command demo: "Hey Fuu, jam berapa sekarang?"
```

### 3.2 Daily flow (target: hands-free)

```
User: "Hey Fuu" [wake word triggered, soft chime]
Fuu (TTS): "Iya?"
User: "Buka WhatsApp, kirim ke Budi 'sampai jumpa nanti'"
Fuu (TTS): "Aku buka WhatsApp dulu ya..."
[App opens, Fuu identifies Budi chat via vision, types message]
Fuu (TTS): "Pesan siap dikirim ke Budi: 'sampai jumpa nanti'. Kirim sekarang?"
User: "Iya"
[Fuu taps Send]
Fuu (TTS): "Sudah terkirim."
```

Latency budget per turn:
- Wake word detection: <500ms
- STT: <1500ms
- Inference (offline): <2000ms; (cloud): <800ms
- Action execution: 500-3000ms (depend on app)
- TTS: <1000ms

Total simple command (offline): ~5-7 detik
Total simple command (cloud): ~3-4 detik

### 3.3 Privacy-sensitive flow

```
User: "Hey Fuu, ringkas chat WhatsApp aku dengan Bos hari ini"
[Fuu detect: chat content sensitive]
Fuu: "Task ini perlu baca chat WhatsApp kamu. Mau pakai mode privat (offline,
agak lambat ~10 detik) atau cloud (cepat tapi data dikirim ke Anthropic)?"
User: "Privat saja"
[Fuu switches to Gemma local for this session]
[Process via accessibility tree, summarize with Gemma local]
Fuu: "Bos kamu hari ini bahas tentang deadline project A, minta meeting jam 3,
dan share file budget. Total 23 pesan. Mau aku detail kah?"
```

### 3.4 Multi-step task

```
User: "Hey Fuu, set alarm besok jam 6, terus buka aplikasi banking aku, terus kasih lihat saldo"
Fuu: "OK, 3 task. Pertama: alarm besok 06:00 — done. Kedua: aku buka mobile banking dulu..."
[opens BCA Mobile app]
Fuu: "Aplikasi sudah terbuka. Untuk lihat saldo aku perlu kamu PIN dulu. Kamu input ya."
[user inputs PIN]
Fuu: "Saldo kamu Rp 12,500,000. Selesai."
```

Note: untuk action sensitif (PIN, password), Fuu **never** auto-input. Selalu pause minta user.

---

## 4. Capability Coverage

ChibiClaw v4 capability di kategorikan dalam **5 layer**, dari paling simple ke paling kompleks:

### Layer 1: System control (no app launch)
- Senter on/off, volume, brightness, WiFi/Bluetooth toggle, dll
- Latency target: <2 detik (offline)

### Layer 2: Intent-based app control (single tap)
- Buka app, search di YouTube, dial nomor telepon, navigate Maps, share text
- Latency target: <3 detik

### Layer 3: Multi-step app interaction (accessibility-friendly)
- Buka Gmail → cari email "rapat" → baca → reply
- Latency target: <10 detik (offline) / <5 detik (cloud)

### Layer 4: Multi-step di app blacklist (vision-first)
- Buka TikTok → cari "ikan masak" → like 5 video pertama
- Latency target: <20 detik (multimodal vision lebih lambat)

### Layer 5: Cross-app workflow
- Lihat email pertama dari Bos → kalau ada tanggal → set calendar event
- Latency target: <30 detik

Untuk Layer 4-5, cloud model recommended. Layer 1-3 cukup offline.

---

## 5. Why Not Just Use [Existing Solution]?

| Solution | Mengapa tidak cukup |
|----------|---------------------|
| Google Assistant | Cloud-only, no privacy mode, granular device control terbatas |
| Bixby | Samsung-only, capability gap signifikan |
| Siri | iOS-only, irrelevant |
| Tasker | Rule-based, not natural language, learning curve curam |
| Mobile-Agent (research) | Bahasa Indonesia weak, Python-on-Android friction, research-grade |
| AppAgent | Same as above, less active |
| Cowork (Anthropic) | Desktop-only, cloud-only, $100/bulan, English-first |
| OpenInterpreter | Desktop-first, Android port belum stabil |

**Differentiator ChibiClaw v4**:
1. Indonesian-native (Gemma 4 + custom prompt + ID TTS)
2. Offline default (privacy stance)
3. Native Kotlin (performance > Python-on-Android)
4. Vision + accessibility hybrid
5. BYOK cost model (no recurring subscription dari ChibiClaw side)

---

## 6. Design Trade-offs (eksplisit)

### 6.1 Latency vs Privacy
**Trade-off**: Offline = lebih privat tapi lebih lambat. Cloud = lebih cepat tapi data leave device.
**Decision**: User pilih per session. Default offline (privacy-first), opt-in cloud.

### 6.2 Reliability vs Capability
**Trade-off**: Vision-first lebih reliable di hostile apps tapi 3-5× lebih lambat dari accessibility tree.
**Decision**: Hybrid — accessibility default, vision untuk blacklist apps.

### 6.3 Voice quality vs Latency
**Trade-off**: Offline TTS (Piper) lebih cepat (~500ms) tapi suara agak robot. Cloud TTS (ElevenLabs) sangat natural tapi 1500-2500ms.
**Decision**: Offline default, premium voice opt-in.

### 6.4 Multi-model adapter complexity
**Trade-off**: Adapter pattern butuh more code (4 implementation), but future-proof.
**Decision**: Worth it. Lock-in ke single vendor (Google for Gemma) terlalu berisiko.

### 6.5 Memory rebuild
**Trade-off**: Memory layer (vector store) yang baru saja dihapus rebuild = effort signifikan.
**Decision**: Rebuild — tapi dengan use case yang JELAS (entity resolution, semantic search di history). Bukan vector embedding speculative.

### 6.6 Tool consolidation (27 → 10)
**Trade-off**: Tool granularity lebih rendah = Gemma butuh reasoning lebih untuk decompose. Tapi tool count lebih kecil = selection error rate lebih rendah.
**Decision**: Konsolidasi ke 10 primitif. Refer Cowork (4 primitif) — too few untuk Android. 10 sweet spot.

---

## 7. Technical Constraints & Assumptions

### 7.1 Constraints
- Min Android: API 31 (Android 12) — jangan support older karena foreground service rules berubah signifikan
- Min RAM: 6GB — Gemma 4 4B butuh ~3.5GB working memory
- Min storage: 6GB free — APK 350MB + model 4GB + cache
- Network: optional — offline mode harus 100% functional

### 7.2 Assumptions
- LiteRT-LM masih primary inference engine v4 awal (TBD: trial MediaPipe LLM Inference di Phase 1 spike)
- Gemma 4 E4B (~4GB) jadi default. E2B (1.6GB) opt-in untuk low-end device.
- User punya Google account untuk download model (atau bring own GGUF)
- User willing grant Accessibility + Microphone permission

---

## 8. Out of Scope (defer ke v5+)

- iOS support
- Cross-device sync
- Smart home protocol native (Matter, Zigbee)
- Multi-language voice (English support deferred)
- Voice cloning untuk personalized Fuu voice
- Enterprise SSO
- Plugin marketplace
- Wear OS / smartwatch support

---

## 9. Implementation Phases (high-level)

| Phase | Duration | Goals | Deliverable |
|-------|----------|-------|-------------|
| Phase 1: Foundation | 4 weeks | InferenceAdapter, multi-model, settings UI | APK 4.1 alpha — text chat tetap, multi-model toggle |
| Phase 2: Voice Layer | 4 weeks | Wake word, STT, TTS, voice flow | APK 4.2 alpha — voice end-to-end |
| Phase 3: Vision-First & Polish | 4 weeks | Vision execution, memory rebuild, beta polish | APK 4.0 beta — feature complete |
| Beta Test | 2 weeks | 5-10 user real test | Bug list + UX feedback |
| Stable | 2 weeks | Address beta feedback, ship | APK 4.0 stable |

Total: 16 minggu = 4 bulan dari kickoff ke stable. Detail di [04-implementation-roadmap.md](04-implementation-roadmap.md).

---

## 10. Success Criteria

ChibiClaw v4 SHIP-READY kalau:

1. ✅ Voice command success rate ≥ 85% di Top 20 task scenarios
2. ✅ Crash-free session ≥ 99% di beta test (5+ users, 1+ minggu)
3. ✅ Offline mode 100% functional tanpa network
4. ✅ Cloud mode di semua 4 adapter (Gemma local, GPT, Claude, Gemini) jalan
5. ✅ Privacy toggle bekerja: offline mode = ZERO data leave device (verify dengan packet sniffer)
6. ✅ Onboarding completion rate ≥ 70% (dari first-open ke first-command)
7. ✅ Latency: simple command ≤ 5s offline, ≤ 3s cloud
8. ✅ Build size ≤ 400MB APK
9. ✅ RAM peak usage ≤ 4.5GB di mid-range device (Snapdragon 7 Gen 2 baseline)
10. ✅ Manual test plan (lihat 05-testing-strategy.md) PASS 100%

---

**Next**: [02-architecture.md](02-architecture.md) — Detailed architecture diagrams + component breakdown.
