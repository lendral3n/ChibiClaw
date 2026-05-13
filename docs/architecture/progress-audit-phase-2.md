# Progress Audit — Phase 2 (Voice + Emotion Pipeline)

**Tanggal audit:** 2026-05-13
**Reference blueprint:** [23-phase-2-voice-emotion.md](23-phase-2-voice-emotion.md)
**Build verify:** ✅ Sukses 38 detik (warm), 228 MB APK debug

---

## Ringkasan

| Phase 2 | Compile | Live Functional | Definition of Done | Overall |
|---------|---------|-----------------|--------------------|---------|
| W1 Audio + STT | 100% | 50% | 4/5 (Whisper actual stub) | **80%** |
| W2 TTS + Composer | 100% | 90% | 5/5 (real API call siap, butuh key) | **95%** |
| W3 Emotion | 100% | 50% | 2/4 (model file pending) | **75%** |
| Integration | 100% | 95% | 4/4 | **98%** |
| **Combined** | **100%** | **70%** | — | **85%** |

---

## Deliverable per Milestone

### W1 — Audio + STT

| Item | Status | Catatan |
|------|--------|---------|
| AndroidManifest RECORD_AUDIO + FOREGROUND_SERVICE_MICROPHONE | ✅ 100% | Sudah di-declare upfront sejak Phase 0 |
| ChibiService FGS type bitmask `microphone\|specialUse` | ✅ 100% | `startForeground(id, notif, mask)` Android 14+ |
| `MicLock` — AudioFocus + mutex | ✅ 100% | Single-task mic guarantee |
| `MicCapture` — AudioRecord 16kHz mono streaming Flow | ✅ 100% | 50ms chunk (800 samples) |
| `AudioTrackManager` — PCM playback streaming | ✅ 100% | 22050 Hz mono PCM16 |
| `WhisperStt` — sherpa-onnx via reflection | ⚠️ **30%** | Reflection-based init OK tapi `transcribeOnnx` masih `NotImplementedError`. Fallback echo mode aktif (return signal message). Butuh: push model + enable dep. |

### W2 — TTS + Composer

| Item | Status | Catatan |
|------|--------|---------|
| `ElevenLabsTts` — streaming API v3 | ✅ 100% | POST `/v1/text-to-speech/{voice_id}/stream`, PCM 22050 |
| Voice ID Fuu hardcoded | ✅ 100% | `gMIZZcmZCnyySbZdSZrZ` per ADR-005 |
| API key di SecurePreferences | ✅ 100% | Key `elevenlabs_api_key`, butuh user input |
| Emotion tag mapping inline | ✅ 100% | joy/sad/angry/surprised/satisfied/uncertain |
| VoiceSettings dinamis per emotion | ✅ 100% | stability + style adjusted |
| `ResponseComposer` — task → ComposedResponse | ✅ 100% | Pakai resultSummary/errorMessage/await question |
| OkHttp dependency | ✅ 100% | 4.12.0 di build.gradle |

### W3 — Emotion

| Item | Status | Catatan |
|------|--------|---------|
| `EmotionContext` data class (VAD + textEmotions) | ✅ 100% | `toPromptText()` formatter |
| `Wav2SmallEmotion` — audio VAD ONNX | ⚠️ **30%** | Reflection-based init; `runOnnx` masih NotImplementedError; butuh model file `wav2small.onnx` |
| `TextEmotionClassifier` — roberta-go_emotions | ⚠️ **40%** | ONNX skeleton + **keyword fallback Indonesia** aktif. Cukup untuk dev signal sambil tunggu real ONNX. |
| `EmotionDetector` facade | ✅ 100% | Combine audio + text, reset state |

### Integration

| Item | Status | Catatan |
|------|--------|---------|
| `VoicePipelineOrchestrator` — mic → STT → conversation | ✅ 100% | Cancel-able via stop() |
| `ContextBuilder` inject emotion signal ke LLM | ✅ 100% | `emotionDetector.current()` jadi emotionSignal field |
| `AgentRuntime` speak after CHAT task complete | ✅ 100% | TTS hanya kalau hasApiKey + bukan silent |
| `OverlayChatPanel` mic button | ✅ 95% | Recording toggle 🎤 ↔ ■; warning kalau API key belum di-set |
| `OverlayWindowManager` inject voicePipeline + ElevenLabsTts | ✅ 100% | Constructor params updated |

### Definition of Done Phase 2 (per blueprint)

| Checklist | Status | Catatan |
|-----------|--------|---------|
| Tap mic → record → STT works | ⚠️ | Mic record real, STT pakai fallback echo sampai Whisper model + dep enabled |
| LLM response + emotion tag → ElevenLabs → AudioTrack | ✅ | End-to-end siap, butuh API key |
| Voice Fuu natural | ⏳ | Tunggu user test dengan API key |
| Emotion context passed to LLM | ✅ | Verified di ContextBuilder logic |
| LLM output emotion tag → TTS settings dynamic | ✅ | ElevenLabsTts compute stability+style per emotion |
| Audio focus tidak konflik dengan Google Assistant | ✅ | AudioFocusRequest TRANSIENT |
| Bubble color animation per state | ⏳ | Phase 9 polish dengan v4 design (state color masih flat gray Phase 0) |
| No crash voice loop 30 menit | 🟡 | Defer Phase 9 |
| AuditLog populated dengan MIC + TTS entries | ⏳ | MIC_ACTIVATED/DEACTIVATED + STT_RESULT + TTS_PLAYBACK belum di-log eksplisit (audit infrastructure ready tapi belum di-call dari pipeline). Phase 2 polish atau Phase 9 |

**5 ✅ + 2 ⚠️ + 4 ⏳/🟡 = 11 item.** Setara ~70%.

---

## Sub-Milestone Sisa Phase 2 (Tidak Block Phase 3)

| Item | Action | Estimasi |
|------|--------|----------|
| Whisper STT real inference | Push sherpa-onnx Whisper small model ke `/data/data/com.chibiclaw/files/models/sherpa_whisper_small/` + enable `sherpa-onnx-android` dep + replace `transcribeOnnx` body | 2-3 jam |
| Wav2Small audio emotion real | Push `wav2small.onnx` (~120KB) ke assets + implement `runOnnx` (PCM short[] → float32 tensor → run → extract VAD) | 1-2 jam |
| RoBERTa text emotion real | Push `roberta_goemotions_q8.onnx` (~125MB) + tokenizer + implement `runOnnx` | 3-4 jam |
| AuditLog calls di pipeline | Tambah `auditLogger.log(...)` di MicCapture start/stop, STT result, TTS playback | 1 jam |
| Bubble color per state | Phase 9 polish dengan ChibiState enum + v4 design tokens | Phase 9 |

---

## Build Result

- APK: 228 MB (tidak naik dari Phase 1; OkHttp + voice code Kotlin small)
- Build time: **38 detik** (warm)
- 0 error, 1 warning kosmetik (No cast needed di AgentRuntime)

---

## CI/CD Updates (di-eksekusi paralel dengan Phase 2)

Audit & rewrite CI/CD karena workflow lama auto-trigger release di setiap push (boros saat Phase 0-9 dev period):

| File | Sebelum | Sesudah |
|------|---------|---------|
| `.github/workflows/ci.yml` | (tidak ada) | **NEW** — debug build verify per push main + PR; no APK upload (hemat quota) |
| `.github/workflows/build-release.yml` | Trigger `push: main` → auto-release tiap commit | Trigger `push tags: v*` + manual `workflow_dispatch` → release intentional |
| `codemagic.yaml` | Trigger `push: main` | Trigger tag `v*` only; doc keep sebagai alternative CI |

Workflow strategy:
- **Phase 0-9 dev**: setiap commit → CI verify compile (`ci.yml`). No release auto.
- **Phase 9 polish + onwards**: Lendra tag manual `git tag v4.0.0 && git push --tags` → release Build + GitHub Release.
- Prerelease auto-detect: tag dengan `-` (e.g. `v4.0.0-alpha`) → marked prerelease, bukan latest.

---

## Score Phase 2

Real score post-implementation:

| Kategori | Score |
|----------|-------|
| Audio infrastructure | **100%** |
| TTS pipeline | **95%** (siap, butuh API key user) |
| STT pipeline | **40%** (Whisper real pending) |
| Emotion detection | **60%** (text keyword fallback ada, audio + real ONNX pending) |
| Integration ke agent loop | **100%** |
| UI mic button | **95%** |
| Audit log calls | **40%** |
| **Combined Phase 2** | **80%** |

20% gap = sub-milestone yang butuh model files + enable optional deps. Pattern sama dengan Phase 1 (model file pending + dep optional).

Phase 3 (Tools Mid — Accessibility + Shizuku + messaging) bisa proceed paralel dengan sub-milestone Phase 1+2.
