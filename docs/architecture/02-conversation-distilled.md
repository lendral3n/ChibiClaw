# 02 — Conversation Distilled

Hasil distilled dari percakapan Lendra <-> Claude Code yang menghasilkan arsitektur saat ini. Urut dari paling baru di atas.

Raw chat archive: lihat folder `sessions/`.

---

## Session 2026-05-13 — Phase 0 Implementation

**Durasi:** ~1 jam
**Outcome:** Phase 0 (Foundation) selesai. 24 file Kotlin, APK debug compile sukses (99 MB, 44 detik build).

### Topik Kunci

1. **Eksekusi Phase 0** per [21-phase-0-foundation.md](21-phase-0-foundation.md)
2. **Update gradle config**: `libs.versions.toml` tambah SQLCipher 4.6.1 + Timber 5.0.1; `build.gradle.kts` simplifikasi (Phase 1+ deps di-defer di komentar)
3. **Rewrite AndroidManifest.xml**: Phase 0 scope (subset dari V2 era manifest yang punya banyak service legacy)
4. **24 file Kotlin** di-implement sesuai folder structure di Phase 0 doc

### Module yang Ditulis

```
app/src/main/java/com/chibiclaw/
├── ChibiApplication.kt              (Hilt entry + Timber + WorkManager config)
├── di/
│   ├── AppModule.kt                 (Database, DAO)
│   ├── SecurityModule.kt            (MasterKey, EncryptedPrefs, SQLCipher passphrase)
│   └── ServiceModule.kt             (WindowManager, NotificationManager)
├── service/
│   ├── ChibiService.kt              (FGS specialUse, overlay show/hide)
│   ├── BootReceiver.kt              (auto-restart after reboot)
│   └── overlay/
│       ├── OverlayWindowManager.kt  (SYSTEM_ALERT_WINDOW + drag + snap-to-edge)
│       ├── OverlayLifecycleOwner.kt (Compose host outside Activity)
│       └── BubbleOverlay.kt         (Compose 56dp bubble dengan breathing animation)
├── data/
│   ├── database/
│   │   ├── AppDatabase.kt           (Room v1 + SQLCipher SupportOpenHelperFactory)
│   │   ├── AuditDao.kt
│   │   ├── AuditLogEntity.kt        (Phase 0-9 action types enumerated)
│   │   └── converters/InstantConverter.kt
│   └── prefs/
│       └── SecurePreferences.kt     (EncryptedSharedPreferences wrapper + ConsentKey)
├── compliance/
│   └── AuditLogger.kt               (redact PII + insert async)
├── ui/
│   ├── MainActivity.kt              (setup vs home routing)
│   ├── theme/
│   │   ├── Color.kt                 (rose pastel palette)
│   │   ├── Theme.kt                 (Material 3 light/dark)
│   │   └── Type.kt                  (system font typography)
│   └── setup/
│       ├── SetupNavigator.kt        (privacy → consent → vendor → done)
│       ├── PrivacyNoticeScreen.kt   (full text dari 19-compliance)
│       ├── ConsentOverlayScreen.kt  (SYSTEM_ALERT_WINDOW + POST_NOTIFICATIONS)
│       └── VendorWizardScreen.kt    (per-OEM guidance dari VendorDetector)
└── util/
    └── VendorDetector.kt            (11 OEM detection + KillLevel)
```

24 file total.

### Build Result

- APK: `app/build/outputs/apk/debug/app-debug.apk`
- Size: 99 MB
- SHA-256: `20e19e9ba29e972e34e654a3aa703bab63caad2d74ddf5335d0b85de3d9995aa`
- Build time: 44 detik (warm), 2m36s (cold first attempt yang fail)
- Kotlin compile clean, no warnings beyond Room schema export note

### Issue Encountered & Fixed

1. **Legacy `accessibility_service_config.xml`** reference string resources yang tidak ada → di-hapus (akan dibuat ulang Phase 3 dengan strings yang benar)
2. **`BuildConfig` not resolved** di ChibiApplication.kt → enable `buildConfig = true` di buildFeatures
3. **`Modifier.height` not resolved** di VendorWizardScreen.kt → tambah `import androidx.compose.foundation.layout.height`

### Keputusan di Session Ini

Tidak ada ADR baru (semua arsitektur sudah di-decide session 2026-05-13 sebelumnya). Implementasi straightforward ikut blueprint Phase 0.

### Aksi Dilakukan

- ✅ Update `gradle/libs.versions.toml` + `app/build.gradle.kts`
- ✅ Rewrite `AndroidManifest.xml` Phase 0 scope
- ✅ Tulis 24 file Kotlin sesuai blueprint
- ✅ Strings.xml minimal
- ✅ Hapus legacy `accessibility_service_config.xml`
- ✅ APK debug compile sukses (gradle clean build)
- ❌ Belum install ke HP (Phase 9 sesuai ADR-011 — test cuma di akhir)

### Open Items / Next

- **Phase 1** ready untuk di-start: Agent Core (LiteRT-LM + TaskManager + AgentRuntime + MemoryStore + 6 tools dasar)
- Phase 1 estimasi 5 minggu, paling kompleks dari 10 phase
- Sebelum Phase 1: konfirmasi Lendra mau lanjut atau ada review Phase 0 doc dulu

### State Akhir Session

- Working tree: 24 file Kotlin baru + modified config files (manifest, build.gradle, libs.versions, strings.xml)
- Stash: `v4-rewrite-archive-2026-05-13` (preserve V3 + V4 scaffolding) + `v4-scaffolding-pre-phase0-2026-05-12`
- Git: belum commit Phase 0 (tunggu Lendra approve sebelum commit ke main)
- APK debug compile sukses, belum install

---

## Session 2026-05-13 — Architecture Design ChibiClaw v4 Full Agent

**Durasi:** ~3-4 jam interaktif
**Outcome:** Arsitektur agent-native ChibiClaw v4 final, 10 phase + 1 bonus roadmap, hapus code lama, mulai docs lengkap.

### Topik Kunci

1. **Hasil deep research (11 dokumen, ~50K kata)** — dipresent ke Lendra. Tiga pattern converge: hybrid cloud+local + planner-executor split + LLM-centric tool calling.

2. **Decision: implementasi pakai apa?** — discussion arsitektur per komponen, dibagi bagian 1 (mobile control) dan bagian 2 (voice emotional). VRM project terpisah (Phase 10 bonus).

3. **Klarifikasi kritis dari Lendra:**
   - "Permission khusus tidak masalah, project pure pribadi"
   - "Full akses HP wajib, bisa gerakan screen + background"
   - "Cloud model pakai OAuth (login akun), BUKAN API key billing"
   - "Default Gemma 4 4B offline, cloud fallback kalau perlu"
   - "Sudah berlangganan ElevenLabs"

4. **Klarifikasi teknis "OAuth"** — Lendra konfirm maksudnya "reverse-engineered web session" (pakai akun subscription Plus/Pro), bukan OAuth resmi (yang tidak ada di Anthropic/OpenAI). Gemini API free tier juga OK karena gratis officially.

5. **Pertanyaan tajam Lendra: "kulihat dari architecture nya, ini seperti code berusaha menjadi lebih pintar daripada llm?"** — momen koreksi besar. Saya akui arsitektur awal re-introduce decision systems yang sudah dihapus v3 refactor (SkillLibrary matcher, InferenceRouter cascade, vision blacklist hardcoded, tier eskalasi). Reformulasi ke LLM-centric murni — kembali ke filosofi "Gemma = otak, kode = tangan".

6. **Pertanyaan tajam Lendra: "Kamu tau bedanya agent & chatbot?"** — momen koreksi kedua. Saya akui arsitektur LLM-centric draft masih chatbot-with-tools (request-response single-shot, no task entity, no persistent world). Reformulasi ke full agent-native: Task first-class entity, AgentRuntime iterative loop, WorldObserver, MemoryStore, InitiativeEngine, ConversationManager.

7. **Konfirmasi Lendra:**
   - LLM-centric (cornerstone decision)
   - Full agent dari awal (cornerstone decision)
   - Standing instruction = mirip cron tapi lebih kaya
   - Memory pakai vector
   - "Jangan simpel diawal, susah kompleks tidak masalah"

8. **10 pertanyaan final** untuk lock arsitektur — Lendra jawab semua:
   - Project: delete code lama, repo sama
   - Reverse session: WebView headless once-off
   - Wake word: skip MVP, manual button
   - Voice ID ElevenLabs Fuu: `gMIZZcmZCnyySbZdSZrZ`
   - Standing instruction UI: Guided form
   - Skill memory: Vector + simple JSON KB
   - Conversation history: Hybrid distilled + raw
   - Test strategy: skip semua, manual di Phase 9
   - VRM integration: Phase 10 bonus paling akhir
   - Language: Full Indonesia

### Keputusan Yang Diambil di Session Ini

Lihat [01-decisions-log.md](01-decisions-log.md) untuk ADR lengkap. Highlight:
- ADR-001: LLM-centric (foundational)
- ADR-002: Full agent dari awal (cornerstone)
- ADR-004: Tool catalog flat dengan capability metadata
- ADR-005-013: 9 jawaban Lendra atas pertanyaan klarifikasi
- ADR-014: Fresh slate code

### Aksi yang Sudah Dilakukan Session Ini

1. ✅ Working tree di-stash dengan label `v4-rewrite-archive-2026-05-13`
2. ✅ Hapus `app/src/main/java/com/chibiclaw/` lengkap
3. ✅ Buat folder `docs/architecture/`
4. ✅ Tulis dokumen foundation (README, 00, 01, 02 — ini lagi ditulis)

### Aksi yang Belum Dilakukan

- Tulis dokumen architecture core (10-19, 10 file)
- Tulis dokumen phase detail (20-2B, 11 file)
- Tulis handover + test (30-31)
- Update memory file dengan pointer ke architecture docs
- Belum mulai implementation Phase 0

### Open Items dari Session Ini

- Stash `stash@{0}` (v4-rewrite-archive) — preserve sampai dokumen done + Lendra konfirm tidak butuh
- Voice ID `gMIZZcmZCnyySbZdSZrZ` tercatat di config plan Phase 2
- Reverse-engineered web session library Kotlin — belum dipilih, perlu riset Phase 4

---

## Session 2026-05-12 / 2026-05-13 — Deep Research Round 1 + 2

**Outcome:** 11 dokumen research total ~50K kata di `docs/research/`.

### Topik

Round 1 (3 dokumen, sesi awal):
- 01: Mobile AI agents full akses HP
- 02: VRM lipsync real-time Android
- 03: Emotion TTS + voice clone

Round 2 (8 dokumen, dilakukan sesi lanjutan):
- 04: Shizuku + ADB tool catalog
- 05: Skill Library / macro per-app
- 06: Vision LLM mobile benchmark
- 07: Floating overlay Android best practice
- 08: VRM emotion + facial expression
- 09: Tomo Sensei FSRS + Gemma prompt
- 10: VIONA RAG telco optimization
- 11: PDP-ID + AI compliance Indonesia

### Highlight Cross-Document

- Pattern dominan: hybrid cloud+local + planner-executor split
- Wake word: ganti rencana OpenWakeWord → microWakeWord (lebih hemat)
- Vision LLM mobile sweet spot: MiniCPM-V 4.6 (1.3B)
- TTS: ElevenLabs v3 untuk validasi cepat, GPT-SoVITS v2Pro untuk fine-tune Indonesia
- Lipsync: uLipSync v3.1.4 (MIT) sudah ada sample VRM 1.0
- Compliance: UU PDP sanksi aktif Oktober 2024, voice biometric kemungkinan Data Spesifik Pasal 4(2)
- Android 16/17: a11y service auto-revoke untuk app non-`isAccessibilityTool`

### Aksi

- ✅ Tulis 11 dokumen + 1 README index di `docs/research/`
- ❌ Belum integrate finding ke implementation plan (dilakukan di session 2026-05-13 berikutnya)

---

## Session 2026-05-12 — Implementasi v4 Design + Build APK + Install

**Outcome:** v4 UI design (dashboards + setup wizard + settings + critical states + chat) ter-implementasi ke Compose dan ter-install di HP Lendra. Tapi semua ini SEKARANG di-stash (ADR-014 fresh slate).

### Topik

- Implementasi 6 dashboard variants V1-V6 (Soft Orb, Equalizer, Progress Ring, Pixel Heart, Constellation, Voice First) ke Kotlin Compose
- Setup wizard 4-step redesign
- Settings (AI, Safety, Persona, Skills) + SettingsHubV4 gateway
- Critical states (Empty, Approval, Error, Notification, Overlay)
- Chat V4 dengan bubbles + execution log + voice input
- Build + install ke Xiaomi 17 Pro Max berhasil

### Aksi

- ✅ ~20 file Compose di `ui/dashboard/v4/`, `ui/setup/v4/`, `ui/settings/v4/`, `ui/states/v4/`, `ui/chat/v4/`, `ui/history/v4/`
- ✅ Wire MainActivity routes ke V4 screens
- ✅ Build APK debug, install via ADB
- ❌ Hardcoded sample data — belum hook ke ViewModel
- ⛔ Di-stash sebagai archive di session 2026-05-13 (rewrite ke agent-native)

### Catatan

Code Compose UI ini berguna sebagai **reference visual** untuk Phase 2-3 nanti. Tidak akan di-restore as-is, tapi snippet komponen (CCBlob, CCStateOrb, theme V2 OKLCH) bisa di-copy ulang dari stash.

---

## Format Session Distilled Baru

Setiap akhir session yang produktif, append section baru di atas dengan format:

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

Raw chat: save di `sessions/YYYY-MM-DD-topic.md`.
