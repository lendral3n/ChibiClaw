# 03 — Tech Stack Decisions

**Audience**: Engineering, technical decision makers
**Last updated**: 2026-05-10

---

## How to Read This Doc

Setiap layer ada:
- **Pilihan** yang dipilih
- **Alternatif** yang dipertimbangkan
- **Justifikasi** kenapa pilih yang itu
- **Versi** spesifik yang dipakai
- **Risiko** dengan pilihan ini

---

## 1. Platform & Language

### Pilihan
- **Android native** (no cross-platform framework)
- **Kotlin 2.1.0** sebagai primary language
- **Min SDK 31** (Android 12, October 2021)
- **Target SDK 34** (Android 14)
- **Compile SDK 36**

### Alternatif yang Dipertimbangkan

| Alternative | Pros | Cons | Verdict |
|-------------|------|------|---------|
| Flutter | Cross-platform, hot reload | Tidak ada akses Accessibility/Shizuku, dependency Dart runtime | ❌ |
| Kotlin Multiplatform | Cross-platform native, share business logic | iOS support tidak in-scope v4, premature complexity | ❌ deferred |
| React Native | Large community | Same Accessibility issue as Flutter, JS bridge overhead | ❌ |
| Jetpack Compose Multiplatform | Native UI cross-platform, future-ready | Still maturing untuk Android Accessibility integration | ❌ deferred |

### Justifikasi
Android-deep capability (Accessibility, Shizuku, Intent system) butuh native access. Cross-platform framework menambah abstraction yang melemahkan capability. Kotlin Multiplatform bisa diadopsi v5+ kalau iOS jadi target.

### Versi Pinned
```toml
[versions]
android-gradle-plugin = "8.7.3"
kotlin = "2.1.0"
android-min-sdk = "31"
android-target-sdk = "34"
android-compile-sdk = "36"
java-target = "17"
```

---

## 2. UI Framework

### Pilihan
- **Jetpack Compose 1.7.5** (BOM-managed)
- **Material 3 1.3.1**
- **Compose Navigation 2.8.4**
- **Hilt Navigation Compose 1.2.0**

### Alternatif Dipertimbangkan
- **XML Views**: legacy, banyak boilerplate. ❌
- **Compose Multiplatform**: future option, but premature.

### Justifikasi
v3 sudah pakai Compose. Konsistensi.

### Versi Pinned
```toml
compose-bom = "2024.12.01"
compose-navigation = "2.8.4"
hilt-navigation-compose = "1.2.0"
```

---

## 3. Dependency Injection

### Pilihan
- **Hilt 2.55** (over Dagger raw)
- **KSP 2.1.0-1.0.29** (over KAPT)

### Alternatif Dipertimbangkan
- **Koin**: simpler API tapi runtime, slower. ❌
- **Dagger raw**: more flexible, more boilerplate. ❌
- **Manual DI**: too tedious untuk 100+ singleton. ❌

### Justifikasi
v3 sudah Hilt. Konsisten. KSP > KAPT untuk build speed (~2× faster).

---

## 4. Inference Layer

### 4.1 Local Model

#### Pilihan
- **Gemma 4 E4B (4B parameter)** sebagai default download
- **Gemma 4 E2B (2B parameter)** sebagai opt-in untuk low-end device
- **LiteRT-LM** sebagai inference engine **DENGAN spike eval MediaPipe LLM Inference di Phase 1**

#### Alternatif Dipertimbangkan

| Model | Size | Latency (mid-range) | Tool calling | Indo quality | Verdict |
|-------|------|---------------------|--------------|--------------|---------|
| Gemma 4 E4B | 4GB | 3-5s | Native, buggy parser | Excellent | ✅ default |
| Gemma 4 E2B | 1.6GB | 1-2s | Native, lebih buggy | Good | ✅ opt-in |
| Gemma 3n E4B | 4GB | 3-5s | OK | OK (less Indo) | ❌ less Indo |
| Phi-3.5 Mini (3.8B) | 2.5GB | 2-3s | Excellent | Weak Indo | ❌ |
| Qwen 2.5 1.5B | 1GB | 1s | OK | Mandarin-first | ❌ |
| Llama 3.2 3B | 2GB | 2s | Weak | OK | ❌ |

#### Inference Engine Comparison

| Engine | Pros | Cons | Verdict |
|--------|------|------|---------|
| LiteRT-LM v0.10.0 | Native Gemma support, multimodal | **Parser bug** v0.10.0 (kamu sudah experience), single-thread requirement, less mature | Current pick, **trial alternatif** |
| MediaPipe LLM Inference | Higher-level API on top of LiteRT, dipakai di Pixel Recorder, lebih stabil | Less low-level control | **Spike Phase 1** |
| MLC LLM | Apache TVM-based, paling cepat (~30% faster than LiteRT), open source aktif | Compile step rumit, multimodal newer | **Spike Phase 2** |
| llama.cpp Android | Paling matang, GGUF format, banyak example | Vision support weak, Android performance OK tapi tidak best | ❌ no vision |
| ONNX Runtime Mobile | Microsoft-backed, reliable | Tool calling support unclear untuk Gemma | ❌ |

**Action**: Phase 1 spike port ke MediaPipe LLM Inference (1 minggu). Decision criteria:
- Parser bug rate <5% (vs LiteRT-LM ~30%)
- Latency parity atau lebih baik
- API ergonomi setara atau lebih baik

Kalau win → switch. Kalau loss → stick LiteRT, tapi pin version yang stable.

### 4.2 Cloud Model APIs

#### Pilihan + Adapter Implementation

| Provider | Model Default | Why | Pricing per million tokens |
|----------|---------------|-----|----------------------------|
| **Anthropic** | claude-sonnet-4-6 | Tool calling SOTA, vision excellent, agentic optimized | $3 input / $15 output |
| **OpenAI** | gpt-4o-mini | Cheap, fast, decent | $0.15 input / $0.60 output |
| **Google AI** | gemini-2.5-flash | Massive context, low cost, vision strong | $0.30 input / $2.50 output |

User pilih default + per-session override.

#### Alternatif Dipertimbangkan

| Provider | Pros | Cons | Verdict |
|----------|------|------|---------|
| Mistral | Open weights, hosted in EU | Tool calling weak compared to Claude/GPT | ❌ |
| Cohere | Enterprise focus | Indonesia weak | ❌ |
| DeepSeek | Cheap | China-based, privacy concern user ID | ❌ defer |
| Groq (Llama 3 inference) | Ultra-fast | Tool calling not native, model quality lower | ❌ |
| Azure OpenAI | Enterprise SLA | Lebih ribet setup, sama model | ❌ |

#### HTTP Client

**OkHttp 4.12.0** + **kotlinx.coroutines** for streaming SSE.

Reasoning:
- Sudah ada di transitive dep (Retrofit dll)
- SSE support via custom interceptor
- Mature, well-maintained

### 4.3 Embedding Model (untuk MemoryStore)

#### Pilihan
- **all-MiniLM-L6-v2** quantized INT8 (~25MB, vs 90MB original)
- Output: 384-dimensional vector
- Inference engine: **LiteRT** (yes, sama dengan main inference)

#### Alternatif Dipertimbangkan
- **bge-small-en**: better quality, 380MB, English-only. ❌ bahasa
- **multilingual-e5-small**: bagus untuk multilingual, 470MB. Kompromi size.
- **OpenAI text-embedding-3-small**: cloud, $0.02/M token. Bagus quality tapi cloud.

**Decision**: MiniLM L6 INT8 untuk offline-first. Cloud embedding via OpenAI sebagai opt-in (kalau user OK cloud untuk memory).

---

## 5. Voice Stack

### 5.1 Wake Word

#### Pilihan
- **openWakeWord 0.6.0** (open source, Apache 2.0)
- Custom train "Hey Fuu" model

#### Alternatif Dipertimbangkan

| Tool | License | Quality | Customization |
|------|---------|---------|---------------|
| **openWakeWord** | Apache 2.0 ✅ | Good | Train custom keyword via web UI |
| Picovoice Porcupine | Free personal, $99/year commercial | Excellent | Custom keyword via Picovoice Console |
| Snowboy | Discontinued (Kitt.AI shut down 2020) | N/A | N/A ❌ |
| Mycroft Precise | Active but smaller community | OK | Train custom |

#### Justifikasi
- openWakeWord: full open source, no vendor lock-in
- Custom train "Hey Fuu" via openWakeWord training notebook (~1 jam train, ~50 audio samples)
- Inference cost: <1% CPU continuous

### 5.2 Voice Activity Detection (VAD)

#### Pilihan
- **Silero VAD** (MIT license)
- ~1MB model, ONNX format
- Latency: <10ms per frame

#### Alternatif Dipertimbangkan
- WebRTC VAD: faster but less accurate, deprecated for some platforms
- py-webrtcvad: Python wrapper, bukan untuk Android

### 5.3 Speech-to-Text (STT)

#### Pilihan: Hybrid (offline default + cloud opt-in)

| Provider | Mode | Latency (target) | Quality (Indonesian) | Cost |
|----------|------|------------------|----------------------|------|
| **Whisper.cpp Android** (small model, INT8) | Offline | 2-4s | Good | $0 |
| **OpenAI Whisper API** (whisper-1) | Cloud | <1s | Excellent | $0.006/min |
| **Deepgram Nova-2** | Cloud | <500ms | Very good | $0.0043/min |

Default: Whisper.cpp small INT8 (~250MB).
Cloud opt-in: user pilih OpenAI atau Deepgram di Settings, BYOK.

#### Alternatif Dipertimbangkan
- **AssemblyAI**: bagus quality tapi Indo less optimized
- **Google Cloud Speech**: bagus tapi mahal, dan Google ID logged
- **Native Android SpeechRecognizer**: free tapi cloud (Google), no privacy
- **Wav2Vec2 Indonesia fine-tuned**: research-grade, Hugging Face hosted, perlu setup sendiri

### 5.4 Text-to-Speech (TTS)

#### Pilihan: Hybrid

| Provider | Mode | Latency | Quality | Cost |
|----------|------|---------|---------|------|
| **Piper TTS Android** | Offline | <800ms | OK (Indonesian voice tersedia: id-fajri, id-budi) | $0 |
| **OpenAI TTS** (tts-1, voice "alloy") | Cloud | 1-2s | Excellent natural | $15/1M chars |
| **ElevenLabs Multilingual v2** | Cloud | 1-3s | Best (premium feel) | $0.30/1k chars |

Default: Piper id-fajri voice.
Premium opt-in: ElevenLabs (BYOK) untuk user yang willing pay quality.

#### Alternatif Dipertimbangkan
- **Coqui TTS**: open source good but Indonesian model rare
- **Native Android TextToSpeech**: free tapi quality jelek, robotik
- **Microsoft Azure TTS**: bagus tapi mahal

---

## 6. Database

### Pilihan
- **Room 2.7.2** (Android persistence library)
- **SQLCipher 4.6.0** (database encryption)
- Tidak pakai vector database eksternal — DIY cosine similarity untuk embedding

### Alternatif Dipertimbangkan

| DB | Pros | Cons | Verdict |
|----|------|------|---------|
| **Room** | Type-safe, integrated, mature | No native vector | ✅ + DIY vector |
| ObjectBox | Native vector index, fast | Less mature, smaller community | ❌ defer |
| SQLDelight | Multi-platform | Less Android-tooling | ❌ |
| Realm | Object DB, sync | Heavier, less popular now | ❌ |

### Vector Search Implementation

```kotlin
// Brute force cosine similarity, dipakai sampai N <100k
fun findClosest(query: FloatArray, embeddings: List<EmbeddingRecord>, topK: Int): List<EmbeddingRecord> {
    return embeddings
        .map { it to cosineSimilarity(query, it.vector) }
        .sortedByDescending { it.second }
        .take(topK)
        .map { it.first }
}
```

Untuk N >100k records (unlikely di personal use), upgrade ke ANN (annoy / hnswlib bindings).

---

## 7. Networking & API

### Pilihan
- **OkHttp 4.12.0** untuk HTTP
- **kotlinx.serialization 1.8.0** untuk JSON
- **kotlinx.coroutines 1.10.1** untuk async
- **Retrofit 2.11.0** dipakai hanya untuk REST endpoint sederhana (kalau ada)

### SSE Streaming

Cloud LLM API biasanya pakai Server-Sent Events. OkHttp interceptor + Flow:

```kotlin
fun OkHttpClient.streamSSE(request: Request): Flow<SseEvent> = callbackFlow {
    val call = newCall(request)
    val source = call.execute().body!!.source()
    
    while (!source.exhausted() && !isClosedForSend) {
        val line = source.readUtf8Line()
        when {
            line == null -> close()
            line.startsWith("data: ") -> {
                val data = line.substring(6)
                if (data == "[DONE]") close()
                else trySend(SseEvent(data = data))
            }
        }
    }
    awaitClose { call.cancel() }
}
```

---

## 8. Security & Encryption

### Pilihan

| Layer | Tech | Purpose |
|-------|------|---------|
| API key storage | **EncryptedSharedPreferences** (AndroidX Security 1.1.0-alpha07) | Master key di Keystore, encrypted prefs |
| Database | **SQLCipher** | DB-level encryption AES-256 |
| Backup | **Android Keystore-derived encryption** | Custom backup format kalau user export |
| Network | **OkHttp TLS 1.3** | Standard HTTPS |
| API key validation | Ping endpoint per provider | Verify key valid sebelum save |

### Threat Model

| Threat | Mitigation |
|--------|-----------|
| User device compromised (root/malware) | API keys still readable from compromised device. Mitigate: prompt user re-auth periodically, rate limit unusual activity. |
| Cloud provider data breach | Audit log of what data sent, when, to whom. User can export & delete. |
| Network MITM | TLS 1.3 + cert pinning untuk endpoint kritis (api.anthropic.com, api.openai.com, api.google.com) |
| App impersonation (other app pretend to be ChibiClaw via AIDL) | Caller package whitelist (existing v3 feature) |
| Voice data leak | Default offline. If cloud STT, log to audit trail. |

---

## 9. Build & Deployment

### Build System
- **Gradle 9.3.1** (sudah)
- **AGP 8.7.3**
- **R8 minify** untuk release
- **Bundle (AAB)** untuk Play Store, **APK** untuk sideload

### CI/CD (proposed)

GitHub Actions:
```yaml
on: [push, pull_request]
jobs:
  build:
    - ./gradlew assembleDebug
    - ./gradlew testDebug
    - ./gradlew lintDebug
  release:
    - ./gradlew bundleRelease
    - upload to GitHub Releases
```

### Distribution
- **Phase 1-2 (alpha)**: APK via GitHub Releases (sideload only)
- **Phase 3 (beta)**: APK via GitHub Releases + closed beta on Play Console
- **Stable**: Play Store + GitHub Releases

---

## 10. Testing

### Frameworks
- **JUnit 4.13.2** (current, kept)
- **Mockk 1.13.13** (Kotlin-friendly mocking)
- **Robolectric 4.14.1** (Android components in JVM tests)
- **Espresso 3.6.1** (UI tests)
- **Hilt Testing 2.55** (DI for tests)
- **Turbine 1.2.0** (Flow testing)

### Coverage Tooling
- **Jacoco** untuk coverage report
- Target: ≥30% line coverage di critical paths (agent/, executor/, voice/)

Detail di [05-testing-strategy.md](05-testing-strategy.md).

---

## 11. Observability

### Logging
- **DevLogger** (existing v3) — in-memory ring buffer, exportable
- Tag convention: per-component (AGENT, TOOL, VOICE, INFER, EXEC, etc.)

### Metrics
- **In-app metrics dashboard** (Settings → Diagnostics)
  - Inference latency p50/p95/p99 per adapter
  - Voice flow success rate
  - Tool call success rate
  - Privacy mode usage breakdown

### Crash Reporting
- **Defer** ke v4.5: Sentry atau Firebase Crashlytics. Privacy-first: opt-in, anonymized, no PII.
- v4 awal: rely on Play Console crashes (kalau publish) + manual user bug report.

---

## 12. Permissions Matrix

| Permission | Required | Why | Granted at |
|------------|----------|-----|------------|
| `RECORD_AUDIO` | YES | Wake word + STT | Onboarding step 3 |
| `BIND_ACCESSIBILITY_SERVICE` | YES | UI automation tier 3 | Onboarding step 7 |
| `INTERNET` | Conditional | Only if cloud mode | Auto |
| `FOREGROUND_SERVICE` | YES | ChibiService persistent | Auto |
| `FOREGROUND_SERVICE_MEDIA_PROJECTION` | YES | Vision via screenshot | Per session |
| `SYSTEM_ALERT_WINDOW` | YES | Floating overlay + ConfirmationOverlay | Onboarding step 8 |
| `POST_NOTIFICATIONS` | YES | Notification listener + status | Onboarding step 4 |
| `READ_EXTERNAL_STORAGE` | YES | File access tier 2 | On-demand |
| `WRITE_EXTERNAL_STORAGE` | YES | File ops | On-demand |
| `READ_CONTACTS` | Optional | contact_query | On-demand (kalau user pernah pakai contacts) |
| `SEND_SMS` | Optional | messaging tool kind=sms | On-demand |
| `SCHEDULE_EXACT_ALARM` | YES | Cron tasks | Auto |
| `QUERY_ALL_PACKAGES` | YES | App listing untuk launch_app | Auto |
| `BLUETOOTH_*` | Optional | Bluetooth control | On-demand |
| ... (50+ permission) | | | |

**Permission strategy**: deferred + just-in-time. Bukan dump 70 permission upfront.

---

## 13. Critical External Dependencies (Maven coordinates)

```toml
[versions]
# AndroidX
core-ktx = "1.15.0"
lifecycle = "2.8.7"
compose-bom = "2024.12.01"
hilt = "2.55"
hilt-compose = "1.2.0"
room = "2.7.2"
work = "2.10.0"
security-crypto = "1.1.0-alpha07"

# Kotlin
kotlin = "2.1.0"
ksp = "2.1.0-1.0.29"
coroutines = "1.10.1"
serialization = "1.8.0"

# Network
okhttp = "4.12.0"
retrofit = "2.11.0"

# Inference (NEW)
litert-lm = "0.10.0"     # current, may switch to MediaPipe LLM Inference
mediapipe-tasks-genai = "0.10.x"  # spike candidate
sqlcipher = "4.6.0"

# Voice (NEW)
openwakeword = "0.6.0"   # via gradle native lib
silero-vad = "5.0.0"     # ONNX runtime android
whisper-cpp = "1.7.1"    # via prefab native lib
piper-tts = "1.2.0"      # via prefab native lib

# Image processing
mlkit-text-recognition = "16.0.1"
coil = "2.7.0"
camera-x = "1.4.1"

# Utilities
shizuku-api = "13.1.5"
timber = "5.0.1"
```

---

## 14. Build Output Targets

### APK Size Budget
- v3 baseline: 345 MB
- v4 target: ≤400 MB
- Breakdown:
  - Gemma 4 E4B model: 4GB → tidak bundled (download on first run)
  - APK code + native libs: ~120 MB
  - Whisper.cpp small model: 250 MB (bundled atau download? TBD)
  - Wake word model: 5 MB
  - VAD model: 1 MB
  - Embedding model (MiniLM INT8): 25 MB
  - TTS Piper voice (id-fajri): 60 MB
  - Other native libs (LiteRT, OpenCV, etc): 100 MB

**Total bundled (decision needed)**: ~550 MB if all bundled. Probably 250 MB APK + ~300 MB download on first run = better.

### Memory Budget
- Idle: <300 MB RAM
- Active inference (Gemma 4 4B): <4 GB peak
- Active vision (Gemma 4 multimodal): <5 GB peak
- Voice listening (idle wake word): +50 MB

Mid-range device (6 GB RAM): margin tight. Low-end (4 GB): not supported.

---

## 15. Open Tech Decisions (need spike)

These need 1-week spike each before commit:

1. **MediaPipe LLM Inference vs LiteRT-LM**: parser bug rate, latency. (Phase 1 Week 1)
2. **Whisper.cpp small INT8 vs Deepgram Nova-2 cloud**: latency vs cost vs privacy. (Phase 2 Week 5)
3. **Piper TTS vs ElevenLabs**: quality threshold, user reception. (Phase 2 Week 6)
4. **MiniLM L6 vs multilingual-e5-small**: Indonesian semantic search quality. (Phase 3 Week 9)

Decision criteria documented in each spike doc (created when spike starts).

---

**Next**: [04-implementation-roadmap.md](04-implementation-roadmap.md) — 12-week breakdown.
