# 30 — Handover Checklist

Dokumen ini untuk **Claude Code di session berikutnya** atau **Lendra di masa depan** supaya bisa pick up project tanpa context loss.

---

## Quick Start untuk Session Baru

Kalau kamu (Claude Code) baru pertama kali lihat repo ini:

1. **Baca dulu**: [README.md](README.md) — index dokumentasi
2. **Pahami vision**: [00-vision-and-philosophy.md](00-vision-and-philosophy.md)
3. **Cek state terakhir**: [02-conversation-distilled.md](02-conversation-distilled.md) — apa yang terjadi di session sebelumnya
4. **Lihat decisions**: [01-decisions-log.md](01-decisions-log.md) — ADR semua keputusan
5. **Cek progress**: file phase yang sedang aktif (lihat status di section "Current State" di bawah)
6. **Tanya ke Lendra**: kalau ada ambiguity di docs, tanyakan dulu sebelum action

Jangan re-design arsitektur. Arsitektur sudah final di file 10-19. Implementasi sesuai phase plan 20-2B.

---

## Current State (Update setiap session)

**Update terakhir:** 2026-05-14 session "Phase 7 Memory Maturity (CategoryTemplates + Workers + Inspector)"

**Phase aktif:** Phase 7 selesai compile (100% W1, 95% W2). Phase 8 ready untuk start.

**Phase Status Summary:**
- **Phase 0** (Foundation): ✅ DONE (98%, post-audit). Commit `1cb7a75` + `fb9355a`.
- **Phase 1** (Agent Core): ✅ Compile DONE (85%). Commit `8f6547c` + `fb9355a`.
- **Phase 2** (Voice + Emotion): ✅ Compile DONE (80%). Commit `c577d33`.
- **Phase 3** (Tools Mid): ✅ Compile DONE (100% W1–W3 + SafetyGate). Commit `86557c6`.
- **Phase 4** (Cloud Escalation): ✅ Compile DONE (100% all WP). Commit `1d993de`.
- **Phase 5** (Vision): ✅ Compile DONE (100% W1–W3). Commit `dffee13`.
- **Phase 6** (Initiative + Standing): ✅ Compile DONE (100% core, 90% UI). Commit `dbc242e`.
- **Phase 7** (Memory Maturity): ✅ Compile DONE (100% W1, 95% W2). Belum committed.

**Total Kotlin files:** ~95+ di `app/src/main/java/com/chibiclaw/`
**Build:** sukses 54 detik (warm post-Phase3), APK debug

**Sub-milestone Pending** (reflection-based, auto-bind saat model + dep enabled):
- Phase 1: GemmaAdapter `runActualInference` (LiteRT-LM real); EmbeddingProvider `encodeOnnx` (ONNX + tokenizer)
- Phase 2: WhisperStt `transcribeOnnx`; Wav2SmallEmotion `runOnnx`; TextEmotionClassifier `runOnnx`; AuditLog calls di voice pipeline
- Phase 3: a11y_describe_screen vision fallback (defer Phase 5), NotificationListener buffer persist (defer Phase 7)

**Working tree:**
- Phase 7 files: 8 baru (CategoryTemplates, PatternMinerWorker, MemoryDecayWorker, MemoryWorkScheduler, MemoryListByCategoryTool, MemoryInferPatternTool, MemoryInspectorScreen, progress-audit-phase-7.md) + 7 modified (TaskDao + MemoryCategoryCount, TaskRepository recentSnapshot, MemoryRepository extensions, PromptBuilder kategori hints, ChibiService scheduler, ToolsModule bindings, MainActivity inject + NavHost)
- Git: HEAD = `dbc242e` (Phase 6 commit). Lendra sudah push manual sampai sini.

**Next action:**
1. Commit Phase 7 ke git lokal
2. Lendra push manual lagi
3. **Phase 8 (Self-correction + parallel tasks + retry-with-different-tool)** ready start
4. Sub-milestone Phase 1+2+5 paralel kapan saja (push model files + enable deps optional)

**CI/CD Behavior Baru:**
- Push ke main → `ci.yml` build debug verify (no APK upload)
- Push tag `v*` (mis. `git tag v4.0.0 && git push --tags`) → `build-release.yml` build release + GitHub Release

Detail Phase 3 audit: [progress-audit-phase-3.md](progress-audit-phase-3.md).
Detail Phase 4 audit: [progress-audit-phase-4.md](progress-audit-phase-4.md).
Detail Phase 5 audit: [progress-audit-phase-5.md](progress-audit-phase-5.md).
Detail Phase 6 audit: [progress-audit-phase-6.md](progress-audit-phase-6.md).
Detail Phase 7 audit: [progress-audit-phase-7.md](progress-audit-phase-7.md).
Detail Phase 8: [29-phase-8-self-correction.md](29-phase-8-self-correction.md).

---

## Checklist Sebelum Mulai Phase X

Setiap mulai phase baru:

- [ ] Baca file phase relevan (mis. `22-phase-1-agent-core.md` untuk Phase 1)
- [ ] Update todo list dengan deliverable per minggu
- [ ] Cek dependencies yang perlu ditambah ke `build.gradle.kts`
- [ ] Cek permission yang perlu ditambah ke `AndroidManifest.xml`
- [ ] Cek migration Room kalau ada entity baru
- [ ] Update [02-conversation-distilled.md](02-conversation-distilled.md) section "Phase X started"

---

## Checklist Saat Phase X Selesai

- [ ] All "Definition of Done" items checked
- [ ] Manual sanity test di emulator atau device (opsional, sesuai ADR-011)
- [ ] Update [02-conversation-distilled.md](02-conversation-distilled.md): tambah session entry summary
- [ ] Update memory file: `/Users/lendra/.claude/projects/-Users-lendra-Documents-codeV/memory/project_chibiclaw.md` kalau ada state baru
- [ ] Commit ke git dengan message format: "phase-X: ${summary}"
- [ ] Update [01-decisions-log.md](01-decisions-log.md) kalau ada ADR baru
- [ ] Inform Lendra status + next phase

---

## Resource Pointers

### Documentation
- Architecture overview: [10-system-architecture.md](10-system-architecture.md)
- Data schema: [11-data-model.md](11-data-model.md)
- Agent loop: [12-agent-loop.md](12-agent-loop.md)
- Tool catalog: [13-tool-catalog.md](13-tool-catalog.md)
- LLM routing: [14-llm-routing.md](14-llm-routing.md)
- Voice: [15-voice-pipeline.md](15-voice-pipeline.md)
- Memory: [16-memory-system.md](16-memory-system.md)
- Standing: [17-standing-instructions.md](17-standing-instructions.md)
- Execution: [18-execution-strategy.md](18-execution-strategy.md)
- Compliance: [19-compliance-privacy.md](19-compliance-privacy.md)

### Research Background (50K kata)
- [docs/research/](../research/) — 11 dokumen deep research
  - Mobile AI agents (01)
  - VRM lipsync (02)
  - Emotion TTS (03)
  - Shizuku ADB (04)
  - Skill library (05) — note: ChibiClaw v4 tidak pakai pattern ini, LLM-centric
  - Vision LLM mobile (06)
  - Floating overlay (07)
  - VRM emotion (08)
  - Tomo FSRS Gemma (09) — relevan untuk Tomo Sensei project, bukan ChibiClaw
  - VIONA RAG (10) — relevan untuk VIONA project, bukan ChibiClaw
  - PDP-ID compliance (11)

### Design Prototype
- [docs/Design/](../Design/) — React prototype UI mockup (untuk reference Phase 9 polish)

### Memory File (out-of-repo)
- `/Users/lendra/.claude/projects/-Users-lendra-Documents-codeV/memory/`
  - `MEMORY.md` — index
  - `project_chibiclaw.md` — project state
  - `user_profile.md` — Lendra info
  - `feedback_coding_standards.md` — coding standards

---

## Critical Info untuk Implementasi

### Konstanta Penting

| Nama | Value | Source |
|------|-------|--------|
| ElevenLabs voice ID Fuu | `gMIZZcmZCnyySbZdSZrZ` | ADR-005 |
| Gemma model file | `gemma-4-4b-q4.task` (LiteRT-LM format) | Phase 1 |
| Embedding model | `intfloat/multilingual-e5-small` ONNX INT8 | ADR-009 |
| Vision model | `openbmb/MiniCPM-V-4_6-int4` GGUF | Phase 5 |
| Audio emotion model | `audeering/wav2small` ONNX INT8 | Phase 2 |
| Text emotion model | `roberta-base-go_emotions` ONNX INT8 | Phase 2 |
| STT model | `sherpa-onnx-whisper-small.int8` | Phase 2 |
| Target SDK | 36 (Android 16) | build.gradle |
| Min SDK | 28 (Android 9) | build.gradle |
| Compile SDK | 36 | build.gradle |
| Kotlin | 2.1+ | build.gradle |

### Device Target
- Xiaomi 17 Pro Max (HyperOS China)
- Snapdragon 8 Elite Gen 5 (NPU Hexagon)
- 16GB RAM physical + 16GB virtual
- 600GB free storage
- Detail: memory file `project_chibiclaw.md` section "User Device"

### Critical ADRs untuk Diingat
- **ADR-001 LLM-centric** — code jangan jadi pintar dari LLM
- **ADR-002 Full agent dari awal** — Task entity, AgentLoop, persistent state
- **ADR-004 Flat tool catalog** — no tier hierarchy hardcoded
- **ADR-006 Skip wake word MVP** — manual button
- **ADR-009 Memory: Vector + JSON KB** — multilingual-e5-small
- **ADR-011 Skip test, manual at end** — Phase 9 dedicated test

---

## Format Session Distilled Entry

Saat akhir session, append ke [02-conversation-distilled.md](02-conversation-distilled.md):

```markdown
## Session YYYY-MM-DD — [Judul Topik]

**Durasi:** ~X jam
**Outcome:** [satu kalimat]

### Topik Kunci
- ...

### Keputusan Diambil
- ADR-XXX (link ke decisions-log)

### Aksi Dilakukan
- ✅ ...
- ❌ Belum: ...

### Open Items
- ...
```

Raw chat (kalau perlu): save di `sessions/YYYY-MM-DD-topic.md`.

---

## Memory File Update Protocol

`/Users/lendra/.claude/projects/-Users-lendra-Documents-codeV/memory/project_chibiclaw.md`:

Update setiap:
- Phase mulai / selesai
- Ada decision besar (ADR)
- State HEAD/branch berubah
- Voice ID / API key di-rotate (jangan simpan value, cuma "updated")

Update tidak terlalu sering — file ini di-load ke setiap conversation context.

---

## Pertanyaan ke Lendra Sebelum Action Besar

Action yang **perlu konfirmasi** sebelum eksekusi:

- Delete file / folder beyond docs/architecture (mis. hapus stash)
- Push commit ke remote
- Modify file di luar `app/`, `docs/`
- Add dependency baru yang tidak listed di phase plan
- Skip atau merge phase
- Change ADR (ada decision baru yang conflict dengan ADR sebelumnya)

Action yang **bisa langsung** tanpa confirm:

- Tulis kode sesuai phase plan
- Add file di `app/src/`
- Update [02-conversation-distilled.md](02-conversation-distilled.md)
- Update memory file
- Build APK (tanpa install)
- Tulis docs lanjutan

---

## Hand-off Examples

### Contoh Hand-off ke Session Berikutnya (di akhir session Phase X)

```
Lendra,

Phase X status:
- ✅ Done: M1.1, M1.2, M1.3, M2.1, M2.2
- 🔄 In progress: M2.3 (StandingInstruction editor UI, blocked di trigger picker)
- ❌ Not started: M3.x

Bugs found:
- B1: NotificationListener crash di Pixel emulator
- B2: Geofence trigger fire 2x setelah re-enter

Next session:
1. Resolve B1, B2
2. Finish M2.3 trigger picker
3. Lanjut M3.x

Docs updated:
- [01-decisions-log.md] ADR-015 about ...
- [02-conversation-distilled.md] session entry added
- memory/project_chibiclaw.md updated phase status
```

### Contoh Pick-up Session Baru

```
Aku baru pertama lihat repo ini.

Cek [README.md] → vision → distilled → current state.

OK, status: Phase 6 in progress, M2.3 stuck di trigger picker.
2 bug pending. ADR-015 baru about ...

Aku akan lanjut M2.3 dulu, lalu fix B1 + B2.

Tanya Lendra: trigger picker yang stuck — kamu prefer dropdown 
single-select atau multi-select untuk filter compose?
```

---

## Final Notes

- **Konsisten dengan filosofi**: kalau ragu, balik ke [00-vision-and-philosophy.md](00-vision-and-philosophy.md). "Gemma = otak, kode = tangan."
- **Jangan over-engineer**: phase plan sudah cukup detail. Kalau ada gap, tanya Lendra dulu sebelum invent.
- **Jangan skip phase**: dependencies di critical path. Skip = bug accumulation.
- **Audit log selalu**: setiap action affect data atau cloud transit, log ke audit_log.
- **Privacy-first**: kalau bingung, default ke local + opt-in cloud.

---

## Next: [31-self-test-protocol.md](31-self-test-protocol.md)
