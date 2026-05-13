# 23 — Phase 2: Voice + Emotion Pipeline

**Durasi:** 2.5 minggu
**Tujuan:** Voice input/output + emotion sebagai context signal ke LLM. ElevenLabs streaming TTS dengan voice clone Fuu.

---

## Outcome

- Manual mic button di overlay (tap to record) → Whisper STT → agent loop → ElevenLabs TTS → AudioTrack playback
- Wav2Small + roberta-go_emotions emotion detection sebagai context input ke LLM
- LLM output `<emotion>` tag → ElevenLabs v3 mapping (stability + style dinamis)
- Audio focus management (mic + TTS conflict resolution dengan Google Assistant + other apps)
- ChibiService upgrade `foregroundServiceType="microphone|specialUse|mediaPlayback"`

**Test target:** tap bubble mic, bilang "halo Fuu apa kabar", Fuu jawab "Halo Lendra! Aku baik-baik aja, makasih nanya~" dengan voice clone + tone ramah.

---

## Deliverable per Minggu

### Minggu 1: STT + audio capture

**M1.1: AndroidManifest update**
- `<uses-permission android:name="android.permission.RECORD_AUDIO" />`
- ChibiService `foregroundServiceType="microphone|specialUse|mediaPlayback"`

**M1.2: AudioRecord wrapper**
- `voice/audio/MicCapture.kt`: AudioRecord 16kHz mono PCM, streaming buffer
- `voice/audio/MicLock.kt`: AudioFocus request + mutex untuk hindari concurrent acquire

**M1.3: Whisper.cpp via sherpa-onnx**
- Download `sherpa-onnx-whisper-small.int8` model
- `voice/stt/WhisperStt.kt`: streaming transcribe, language=id
- Performance: target <1s untuk utterance 3-5 detik

**M1.4: Mic UI di overlay**
- Mic button di chat panel (sudah ada placeholder Phase 1)
- Tap to start record, tap again to stop / VAD silence detect (Whisper streaming endpoint)
- Visual feedback: pulse animation saat record

### Minggu 2: TTS + AudioTrack

**M2.1: ElevenLabs streaming API**
- `voice/tts/ElevenLabsTts.kt`: streaming POST `/v1/text-to-speech/{voice_id}/stream`
- voice_id = `gMIZZcmZCnyySbZdSZrZ` (Fuu clone)
- model_id = `eleven_v3`
- output_format = `pcm_22050`
- API key di EncryptedSharedPreferences (user input di setup wizard sub-step)

**M2.2: AudioTrack manager**
- `voice/audio/AudioTrackManager.kt`: streaming PCM playback
- AudioAttributes USAGE_VOICE_COMMUNICATION (tidak duck musik)
- Low-latency buffer config

**M2.3: ResponseComposer**
- `voice/ResponseComposer.kt`: text + emotion tag → ElevenLabs payload
- Apply emotion tag inline format ElevenLabs v3: `[excited]`, `[whispers]`, `[sternly]`
- Compute VoiceSettings (stability, style) dynamic based on emotion

**M2.4: Integration ke AgentRuntime**
- Setelah task complete (channel=CHAT, status=COMPLETED), trigger ResponseComposer → ElevenLabsTts → AudioTrack
- Status indicator bubble: gray idle → blue listening → purple thinking → mint executing → lilac complete

### Minggu 0.5 (3): Emotion detection

**M3.1: Wav2Small ONNX**
- Download `audeering/wav2small` ONNX INT8 (~120KB)
- `voice/emotion/Wav2SmallEmotion.kt`: ONNX Runtime, 3s audio window → VAD vector
- Parallel di-call dengan Whisper STT (multiplex audio stream)

**M3.2: Text emotion classifier**
- Download `roberta-base-go_emotions` ONNX INT8 (~125MB)
- `voice/emotion/TextEmotionClassifier.kt`: tokenize + inference → 28-emotion multi-label probs
- Called setelah STT result text available

**M3.3: EmotionDetector composite**
- `voice/emotion/EmotionDetector.kt`: combine audio VAD + text emotions → EmotionContext
- Pass ke `ContextBuilder` sebagai signal input ke LLM
- LLM yang interpret (no policy code)

**M3.4: LLM emotion output mapping**
- System prompt update: "Kamu bisa emit emotion tag di response untuk TTS. Valid tags: joy, sad, angry, surprised, neutral, anxious, satisfied"
- ResponseComposer translate ke ElevenLabs format

---

## Modul Phase 2

```
app/src/main/java/com/chibiclaw/voice/
├── audio/
│   ├── MicCapture.kt
│   ├── MicLock.kt
│   └── AudioTrackManager.kt
├── stt/
│   └── WhisperStt.kt
├── tts/
│   └── ElevenLabsTts.kt
├── emotion/
│   ├── Wav2SmallEmotion.kt
│   ├── TextEmotionClassifier.kt
│   ├── EmotionDetector.kt
│   └── EmotionContext.kt
├── ResponseComposer.kt
└── VoicePipelineOrchestrator.kt
```

ContextBuilder dari Phase 1 di-extend untuk include `emotion_signal` field.

---

## Dependencies tambahan

```kotlin
dependencies {
    // sherpa-onnx for Whisper STT
    implementation("com.k2-fsa.sherpa-onnx:sherpa-onnx-android:1.10.0")
    // (atau JNI binding via .so files)
    
    // OkHttp for ElevenLabs API
    implementation(libs.okhttp)
    
    // ONNX Runtime (sudah ada di Phase 1)
}
```

---

## ElevenLabs Setup di Wizard

Add ke setup wizard Phase 2 step:

```
Step: ElevenLabs API Key
┌─────────────────────────────┐
│ Fuu pakai ElevenLabs untuk  │
│ voice. Kamu punya akun?     │
│                             │
│ Voice ID Fuu sudah pre-set: │
│ gMIZZcmZCnyySbZdSZrZ        │
│                             │
│ Paste API key di sini:      │
│ [____________________]      │
│                             │
│ [Test voice] [Skip] [Lanjut]│
└─────────────────────────────┘
```

Test voice: panggil ElevenLabs dengan text "Halo, aku Fuu" → user dengar → konfirm.

API key di-store di EncryptedSharedPreferences.

---

## System Prompt Update

Tambah di Phase 2:

```
... (Phase 1 prompt)

## Voice & Emotion

Kamu speak via ElevenLabs voice clone. User akan dengar respons kamu.
Untuk variation emotion, emit field "emotion" di response JSON:
- "joy" / "excited" untuk hal menyenangkan
- "sad" / "empathetic" saat user cerita masalah
- "neutral" default
- "surprised" untuk hal mengejutkan
- "satisfied" saat task selesai sukses
- "uncertain" saat butuh konfirmasi

Konteks emotion user akan dikirim di "emotion_signal" — sesuaikan tone kamu.
Contoh: kalau user sad (audio_emotion: V=0.2, A=0.3) — kamu lebih lembut, 
empathetic.

## Response Length

Voice response: keep concise. Max 2-3 kalimat. Long detail simpan untuk
chat fallback (kalau user butuh dive deep).
```

---

## UI Update Phase 2

**Overlay bubble status colors:**
- Idle: gray (`#7B7B85`)
- Listening (mic active): blue accent (`#5BA3D9`)
- Thinking (LLM call): purple accent (`#A07BC9`)
- Executing (tool dispatch): mint green (`#7BC9A0`)
- Complete: lilac (`#C97BA0`)
- Error: rose (`#D97B7B`)
- Speaking (TTS playback): warm yellow (`#E0C266`)

Status color animation: fade transition 200ms.

**Chat panel:**
- Tap mic icon → bubble status berubah listening blue
- Voice activity visualizer (mini waveform)
- Transcribed text appear real-time (partial result)
- Send button hidden saat mic mode

---

## Audio Focus Coordination

```kotlin
class MicLock @Inject constructor(
    private val audioManager: AudioManager,
) {
    suspend fun withMic(block: suspend () -> Unit) = mutex.withLock {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .build())
            .setOnAudioFocusChangeListener { focusChange ->
                if (focusChange == AUDIOFOCUS_LOSS_TRANSIENT) {
                    // pause mic, resume later
                }
            }
            .build()
        
        val granted = audioManager.requestAudioFocus(request) == AUDIOFOCUS_REQUEST_GRANTED
        if (!granted) throw MicAcquireFailed()
        
        try { block() } finally { audioManager.abandonAudioFocusRequest(request) }
    }
}
```

Saat TTS playback, mic listener temporary pause (avoid Fuu hear herself echo).

---

## Risk

| Risk | Mitigasi |
|------|----------|
| Whisper.cpp Indonesia accuracy kurang | Test extensive; fallback ke Whisper medium kalau perlu (~750MB, slower) |
| ElevenLabs quota habis tengah-tengah | Quota monitor + UI warning. User upgrade tier kalau perlu. |
| ElevenLabs API down | Local TTS fallback (Piper, Phase 7 implementasi) |
| Audio focus rebut dengan Google Assistant | Implement OnAudioFocusChangeListener properly, graceful pause |
| Latency end-to-end >5s | Profile per stage (mic→STT, STT→LLM, LLM→TTS, TTS→play); optimize bottleneck |
| Mic indicator privacy concern | OK, Android 12+ auto-tampilkan icon — bagus untuk transparency |

---

## Performance Target

| Metric | Target |
|--------|--------|
| Mic start latency | <100ms |
| Whisper STT 3s utterance | <1s |
| Emotion detection paralel | tidak block STT |
| ElevenLabs first byte | <800ms |
| End-to-end voice cycle (3s utterance → response speak) | <5s p50 |
| TTS audio quality | natural Fuu voice (manual review) |

---

## Definition of Done

- [ ] Tap mic → record → STT works (test 5 utterance variety bahasa Indonesia)
- [ ] LLM response with emotion tag → ElevenLabs streaming → AudioTrack playback
- [ ] Voice Fuu sounds like target persona (kawaii, lembut)
- [ ] Emotion context passed to LLM (cek di prompt log)
- [ ] LLM output emotion tag → terealisasi di TTS (stability/style berbeda per emotion)
- [ ] Mic + TTS audio focus tidak konflik dengan Google Assistant
- [ ] Bubble color animation responsive per state
- [ ] No crash voice loop 30 menit kontinu
- [ ] AuditLog populated dengan MIC_ACTIVATED/DEACTIVATED, LLM_CALL_LOCAL, TTS calls

---

## Next: [24-phase-3-tools-mid.md](24-phase-3-tools-mid.md)
