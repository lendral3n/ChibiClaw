# 2B — Phase 10: VRM Integration (Bonus)

**Durasi:** 2-3 minggu
**Status:** Bonus paling akhir, setelah Phase 9 stable. Optional, tidak block ChibiClaw production-ready.

**Tujuan:** Fuu jadi VRM avatar floating di HP. Lipsync TTS + emotion expression. Integrasi dengan VRM Assistant Android project (Unity 6.2 UaaL + Kotlin).

---

## Outcome

- IPC channel ChibiClaw ↔ VRM Assistant: TTS audio stream pipe + emotion vector
- VRM Assistant adopt uLipSync v3.1.4 (MIT, sample VRM 1.0 ready dari [docs/research/02-vrm-lipsync.md](../research/02-vrm-lipsync.md))
- Emotion vector dari ChibiClaw → VRM expression preset (joy/sad/angry/surprised/relaxed)
- Idle animation: blink Poisson λ=1/3.5s, saccade ±5°, breathing 14 bpm chest sine
- Gaze direction (60% camera, 40% break) — referensi [docs/research/08-vrm-emotion.md](../research/08-vrm-emotion.md)
- Floating VRM avatar overlay di luar app boundaries (atau dalam overlay window ChibiClaw)

**Test target:** Fuu speak via TTS → VRM avatar lipsync synchronized + smile saat happy emotion + sad face saat empathetic.

---

## Deliverable per Minggu

### Minggu 1: IPC bridge + VRM Assistant integration

**M1.1: IPC channel design**
- ChibiClaw expose AudioTrack stream + emotion vector via BroadcastReceiver atau ContentProvider atau direct binder (kalau VRM Assistant juga running di service)
- Format: PCM stream + JSON metadata (emotion VAD + tags)

**M1.2: VRM Assistant adopt uLipSync**
- Import uLipSync v3.1.4 ke Unity project
- Sample VRM 1.0 ada di `Samples/04. VRM/Runtime/uLipSyncExpressionVRM.cs`
- Wire AudioSource yang dapat PCM dari IPC bridge → uLipSync `OnAudioFilterRead`
- Map ke VRM expression aa/ih/ou/ee/oh

**M1.3: Pipeline TTS → VRM**
- ChibiClaw TTS playback (AudioTrack) tap audio stream
- Pipe ke Unity AudioSource via AudioClip.Create dynamic feed
- uLipSync analyze + set expression weight
- Latency target <50ms (perceived sync)

### Minggu 2: Emotion + idle animation

**M2.1: Emotion vector → VRM expression**
- ChibiClaw kirim emotion (joy/sad/angry/surprised/neutral) saat TTS start
- Unity script map ke VRM 1.0 preset (happy / sad / angry / surprised / relaxed)
- `vrm10.Runtime.Expression.SetWeight(ExpressionKey.CreateFromPreset(ExpressionPreset.Happy), weight)`
- Smooth transition (fade 300ms in, hold, fade 500ms out)

**M2.2: Idle animation**
- Blink: Poisson process λ=1/3.5s (random per Disney research)
- Saccade: random ±5° eye angle, change every 0.5-2s
- Breathing: chest bone sine wave 14 bpm
- Gaze: lock to camera 60% time, break 40% time (look up/down/side)
- Coroutines di Unity Update + LateUpdate

**M2.3: Emotion priority + override semantics**
- VRM 1.0 spec: `overrideMouth/Blink/LookAt` → koordinasi lipsync + emotion + blink tanpa konflik
- Saat emotion intensity tinggi (e.g. JOY 0.9), override blink + mouth temporarily

### Minggu 3 (kalau ada): Overlay placement + polish

**M3.1: Floating VRM overlay**
- Unity render ke Texture2D → display di ChibiClaw overlay window
- Atau standalone VRM Assistant app dengan SYSTEM_ALERT_WINDOW
- Coordinate position dengan ChibiClaw bubble (mis. avatar above bubble)

**M3.2: Audio emotion echo polish**
- Pre-recorded sample loop (Phase 2 stub Phase 10 implementasi)
- Inject sample sebelum/sesudah TTS speak untuk hyper-realistic
- Cross-fade audio

**M3.3: Test scenarios**
- "Fuu, lagi sedih nih" → user sad voice (audio emotion detect) → Fuu sad face + empathetic voice
- Fuu speak panjang → lipsync accurate per syllable
- 1 jam continuous use, VRM avatar tidak janky / OOM

---

## Modul Phase 10

VRM Assistant (separate project, Unity + Kotlin):
- uLipSync integration
- Emotion expression script
- Idle animation script
- IPC receiver

ChibiClaw side:
- `vrm/VrmIpcBridge.kt`: send audio + emotion to VRM Assistant
- `vrm/AudioTap.kt`: tap AudioTrack output
- Settings: enable/disable VRM avatar

---

## Risk

| Risk | Mitigasi |
|------|----------|
| Unity UaaL Service integration kompleks | Use Activity intermediate kalau perlu; fallback render via SurfaceView dalam ChibiClaw overlay |
| Audio sync latency >100ms | Buffer tuning; pre-allocate AudioClip; profile per stage |
| VRM avatar performance (FPS drop) | Reduce poly count VRM model; LOD switching |
| Battery drain Unity continuous render | Pause render saat avatar invisible (collapsed bubble state) |

---

## Definition of Done

- [ ] VRM avatar visible di HP overlay
- [ ] Lipsync sync dengan TTS speech (manual review: audio play, mouth move sesuai)
- [ ] Emotion expression apply (test 5 emotion: joy/sad/angry/surprised/neutral)
- [ ] Idle animation alive (blink, saccade, breathing visible saat avatar idle)
- [ ] Performance acceptable (FPS >30, no janky 1 jam test)
- [ ] Toggle disable di Settings (kalau user mau matikan Phase 10 entirely)

---

## After Phase 10

ChibiClaw v4 final:
- AI agent backend lengkap (Phase 0-9)
- Voice persona Fuu dengan emotion
- VRM avatar embodiment (Phase 10)
- Pakai sehari-hari personal

Future iterations possibilities (out of current scope):
- Wake word "Hey Fuu" (skip ADR-006 Phase 0-9)
- Multi-language voice (Fuu speak Jepang / English natively)
- Productize: scope ulang dengan publish, compliance, multi-user
- Cross-platform (iOS via Flutter / Compose Multiplatform)
- Local voice cloning fine-tune (replace ElevenLabs)

Tracked via roadmap baru kalau Lendra commit lanjut.

---

## Total Roadmap

Phase 0-9: ChibiClaw backend agent native + UI integration (21-22 minggu)
Phase 10 bonus: VRM embodiment (2-3 minggu)
**Total grand: 23-25 minggu ChibiClaw v4 full vision.**
