# ChibiClaw v4 — Architecture Documentation

**Status:** Design-complete, implementation belum mulai
**Owner:** Lendra (personal project, single-user)
**Tanggal start:** 2026-05-13
**Tanggal terakhir update:** 2026-05-13

---

## Untuk Siapa Dokumen Ini

Dokumen ini adalah **single source of truth** untuk arsitektur ChibiClaw v4 — Android AI agent yang full-akses HP, berjalan di background, dengan voice persona emotional bernama Fuu.

Audiens:
1. **Lendra** — pemilik project, untuk navigasi cepat & decision log
2. **Claude Code di session berikutnya** — supaya bisa lanjut tanpa context loss
3. **Diri-Lendra di masa depan** — reminder kenapa keputusan dibuat

**Bahasa:** Indonesia profesional. Code snippet pakai English.

---

## Cara Pakai Dokumen Ini

Kalau kamu (Claude Code) baru pertama kali baca:

1. **Mulai dari [00-vision-and-philosophy.md](00-vision-and-philosophy.md)** — kenapa project ini ada, prinsip apa yang dipegang
2. Lanjut ke **[10-system-architecture.md](10-system-architecture.md)** — gambar besar sistem
3. Saat butuh detail teknis, buka file area-specific (11-19)
4. Saat mulai coding, ikuti **[20-phase-roadmap.md](20-phase-roadmap.md)** dan phase file yang relevan (21-2A)
5. Sebelum start session baru, cek **[30-handover-checklist.md](30-handover-checklist.md)**

Kalau kamu butuh konteks keputusan: **[01-decisions-log.md](01-decisions-log.md)**.
Kalau kamu butuh rangkuman percakapan sebelumnya: **[02-conversation-distilled.md](02-conversation-distilled.md)** atau folder `sessions/`.

---

## Struktur Folder

```
docs/architecture/
├── README.md                       ← kamu di sini
├── 00-vision-and-philosophy.md     ← prinsip & filosofi
├── 01-decisions-log.md             ← ADR semua keputusan
├── 02-conversation-distilled.md    ← hasil distilled percakapan
├── sessions/                       ← raw transcript per session
│   └── YYYY-MM-DD-topic.md
│
├── 10-system-architecture.md       ← high-level component & flow
├── 11-data-model.md                ← entity & schema
├── 12-agent-loop.md                ← loop iteration detail
├── 13-tool-catalog.md              ← 15+ tools spec
├── 14-llm-routing.md               ← cascade Gemma → cloud
├── 15-voice-pipeline.md            ← wake → STT → TTS → emotion
├── 16-memory-system.md             ← vector RAG + JSON KB
├── 17-standing-instructions.md     ← ComplexTrigger + UI
├── 18-execution-strategy.md        ← tool selection LLM-driven
├── 19-compliance-privacy.md        ← PDP-ID + audit log
│
├── 20-phase-roadmap.md             ← overview 10 phase
├── 21-phase-0-foundation.md        ← detail Phase 0
├── 22-phase-1-agent-core.md        ← detail Phase 1
├── 23-phase-2-voice-emotion.md
├── 24-phase-3-tools-mid.md
├── 25-phase-4-cloud-escalation.md
├── 26-phase-5-vision.md
├── 27-phase-6-initiative.md
├── 28-phase-7-memory.md
├── 29-phase-8-self-correction.md
├── 2A-phase-9-polish.md
├── 2B-phase-10-vrm.md              ← bonus, integrasi VRM Assistant
│
├── 30-handover-checklist.md        ← untuk pick-up session berikutnya
└── 31-self-test-protocol.md        ← test strategy akhir
```

---

## Highlight Cepat untuk Yang Buru-buru

### Apa ChibiClaw v4

Android AI **agent** (bukan chatbot) yang:
- Bisa **full akses HP** (tap, type, screenshot, force-stop, install)
- Berjalan di **background** dengan floating overlay minimal
- Voice persona **Fuu** dengan emotion (ElevenLabs v3 voice clone)
- **Privacy-first**: default Gemma 4 4B lokal di-device, cloud opt-in
- **Proactive**: bisa trigger sendiri dari standing instruction (mirip cron tapi lebih kaya)
- **Persistent memory**: ingat preferensi, kontak, kebiasaan kamu lewat vector RAG

### Filosofi Kunci

> **"Gemma = otak, kode = tangan."**
> Semua keputusan dilakukan LLM. Kode cuma menyediakan tools dan mengeksekusi. Tidak ada hardcoded routing/cache/policy yang bersaing dengan LLM.

### Stack Singkat

| Lapisan | Tech |
|---------|------|
| Brain (default) | Gemma 4 4B via LiteRT-LM 0.11+ |
| Brain (fallback) | Gemini 2.5 Flash free tier → Claude.ai web → ChatGPT web (reverse-engineered) |
| Voice STT | Whisper.cpp small Q5_1 (sherpa-onnx) |
| Voice TTS | ElevenLabs v3 streaming, voice ID `gMIZZcmZCnyySbZdSZrZ` |
| Wake word | **Skip MVP** — pakai manual button overlay |
| Vision (Phase 5+) | MiniCPM-V 4.6 1.3B (ONNX) |
| Memory | multilingual-e5-small ONNX INT8 (vector) + Room JSON KB |
| Execution | Intent / API / Accessibility / Shizuku / Vision (LLM yang pilih) |
| Storage | Room + SQLCipher |
| UI | Compose + Material 3 + custom CC components |
| Service | Foreground service `microphone|specialUse|mediaPlayback` |

### Phase Total

**10 phase + 1 bonus = 21-24 minggu** untuk MVP agent-native sehari-hari pakai pribadi.

| Phase | Topik | Estimasi |
|-------|-------|----------|
| 0 | Foundation (service, overlay, privacy) | 2 minggu |
| 1 | Agent Core (TaskManager + Loop + Memory) | 5 minggu |
| 2 | Voice + Emotion Pipeline | 2.5 minggu |
| 3 | Tool Catalog Mid (a11y + Shizuku) | 3 minggu |
| 4 | Cloud Escalation (Gemini + Claude/GPT reverse) | 1.5 minggu |
| 5 | Vision Tools | 2.5 minggu |
| 6 | Initiative + Standing Instructions | 3 minggu |
| 7 | Memory Maturity | 2 minggu |
| 8 | Self-correction + Concurrency | 2 minggu |
| 9 | Polish + Self-test | 2 minggu |
| 10 (bonus) | VRM Assistant integration | 2-3 minggu |

---

## Referensi Eksternal Penting

- Research deep dive: [docs/research/](../research/) (11 file, ~50K kata)
- Design prototype React: [docs/Design/](../Design/) (UI mockup)
- Spec v4 lama (legacy, jangan jadi sumber utama): [docs/v4/](../v4/)
- Memory user file: `/Users/lendra/.claude/projects/-Users-lendra-Documents-codeV/memory/`

---

## Update Protocol

Setiap kali ada keputusan baru atau perubahan arsitektur signifikan:

1. **Update [01-decisions-log.md](01-decisions-log.md)** dengan ADR baru
2. **Update file architecture yang affected** (10-19)
3. **Update [02-conversation-distilled.md](02-conversation-distilled.md)** dengan summary session
4. **Save raw chat** ke `sessions/YYYY-MM-DD-topic.md`
5. **Update memory file** di `~/.claude/projects/.../memory/project_chibiclaw.md` kalau ada fakta baru tentang project state

Setiap akhir session yang produktif, panggil checklist di [30-handover-checklist.md](30-handover-checklist.md).
