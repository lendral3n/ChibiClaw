# 01 — Decisions Log (ADR)

Format: **ADR (Architecture Decision Records)**. Setiap keputusan signifikan dicatat dengan tanggal, konteks, opsi yang dipertimbangkan, keputusan, dan rationale.

Urut dari paling baru di atas.

---

## ADR-014: Fresh slate code, hapus semua v2/v3/v4 scaffolding
- **Tanggal:** 2026-05-13
- **Status:** Accepted
- **Konteks:** Working tree ChibiClaw masih punya 143 file uncommitted (v3 refactor + v4 scaffolding). Code lama mengikuti pattern chatbot-with-tools yang tidak align dengan filosofi agent-native baru. Lendra eksplisit "code ku dihapus semua tidak masalah".
- **Opsi dipertimbangkan:**
  - A) Refactor existing code progresif
  - B) Branch baru `v4-rewrite`, main intact
  - C) Repo baru ChibiClaw-v4
  - D) Fresh slate di repo existing, archive working tree ke stash
- **Keputusan:** **D** — stash working tree dengan label `v4-rewrite-archive-2026-05-13`, hapus `app/src/main/java/com/chibiclaw/` lengkap. Resources (icon, color, theme XML) di-keep karena masih reusable.
- **Rationale:** Filosofi agent-native fundamentally beda dari chatbot-with-tools. Refactor butuh effort lebih besar dari rewrite, dengan risk pattern lama bocor. Stash mempertahankan history kalau perlu referensi.

## ADR-013: Bahasa dokumentasi Full Indonesia
- **Tanggal:** 2026-05-13
- **Status:** Accepted
- **Konteks:** Lendra user utama, lebih nyaman baca markdown bahasa Indonesia. Code comments tetap English untuk standard developer-friendly.
- **Keputusan:** Markdown documentation Indonesia. Code (Kotlin, JSON, YAML) English.

## ADR-012: VRM Assistant integration sebagai Phase 10 bonus
- **Tanggal:** 2026-05-13
- **Status:** Accepted
- **Konteks:** VRM Assistant adalah project terpisah Lendra (Unity 6.2 UaaL + Kotlin). Lipsync, emotion expression, idle animation sudah riset (dokumen 02 & 08 di docs/research/). Integrasi dengan ChibiClaw bisa via IPC (TTS audio stream → VRM lipsync).
- **Opsi:**
  - A) Di luar 9 phase ChibiClaw
  - B) Phase 10 bonus
  - C) Integrasi Phase 5 (saat vision tools)
- **Keputusan:** **B** — Phase 10 sebagai bonus paling akhir, setelah agent backend matang.
- **Rationale:** Fokus dulu backend agent. Premature integration menambah complexity tanpa value, karena VRM kosmetik bukan kritikal untuk agent functionality.

## ADR-011: Skip semua automated test, manual test di akhir
- **Tanggal:** 2026-05-13
- **Status:** Accepted (dengan risk)
- **Konteks:** Personal project, single developer (Lendra + Claude Code). Lendra eksplisit "test di akhir ketika semua project selesai".
- **Opsi:**
  - A) Unit test core modules (TaskManager, AgentRuntime), skip UI/integration
  - B) Skip semua, manual test di Phase 9
  - C) Unit + integration + UI test (industry standard)
- **Keputusan:** **B** — skip semua, manual test intensif di Phase 9.
- **Rationale:** Lendra prefer speed of development > test rigor. Manual test cukup untuk personal use. Risk: bug regression sulit di-track tanpa test, butuh discipline manual ulang test setelah change.
- **Mitigasi:** Phase 9 dedicated 2 minggu untuk self-test intensif. AuditLogger lengkap supaya bug bisa di-trace dari log.

## ADR-010: Conversation history hybrid (distilled + raw)
- **Tanggal:** 2026-05-13
- **Status:** Accepted
- **Konteks:** Claude Code session tidak persistent — setiap session baru lupa context. Butuh handover doc supaya bisa pick up.
- **Opsi:**
  - A) Distilled summary (500-1000 kata per session)
  - B) Raw chat history (10-20K per session)
  - C) Hybrid
- **Keputusan:** **C** — distilled di [02-conversation-distilled.md](02-conversation-distilled.md) untuk quick read, raw archive di `sessions/YYYY-MM-DD-topic.md` untuk deep dive.

## ADR-009: Memory system pakai Vector + simple JSON KB
- **Tanggal:** 2026-05-13
- **Status:** Accepted
- **Konteks:** Agent perlu persistent memory (kontak, habit, preferensi). Pilihan: vector RAG only, vector + graph KB, vector + simple JSON KB.
- **Keputusan:** **Vector + simple JSON KB**.
- **Detail:**
  - Vector: **multilingual-e5-small ONNX INT8** (~50MB, 384-dim, support Indonesia + 93 bahasa lain)
  - Storage: Room table `memory_record` (id, category, key, value JSON, embedding FloatArray)
  - Kategori: `USER_PROFILE`, `CONTACT`, `HABIT`, `FACT`, `PREFERENCE`
  - LLM tools: `memory_remember`, `memory_recall(query)`, `memory_forget`, `memory_list_by_category`
- **Rationale:** Vector untuk semantic search, JSON untuk struktur fact. Graph (Neo4j-style) overkill untuk MVP. Bisa upgrade ke bge-m3 (~600MB Q4, 1024-dim) di Phase 7 kalau e5-small jadi bottleneck akurasi.

## ADR-008: Standing Instruction UI = Guided form
- **Tanggal:** 2026-05-13
- **Status:** Accepted
- **Konteks:** Standing instruction adalah superset cron (time + event + predicate + composite). Pilihan editor UI: teknis (kasih raw expression) atau guided form step-by-step.
- **Keputusan:** **Guided form** — UI step-by-step (pilih trigger type → fill fields → preview).
- **Rationale:** Walaupun Lendra ngerti tech, guided form lebih cepat untuk create rule kompleks. Composite trigger dengan AND/OR susah readable kalau raw text. Form mengurangi error.
- **Mitigasi:** "Advanced mode" toggle di form untuk paste raw expression kalau perlu.

## ADR-007: Reverse-engineered web session pakai WebView headless
- **Tanggal:** 2026-05-13
- **Status:** Accepted (dengan known risk)
- **Konteks:** Lendra punya akun ChatGPT Plus + Claude Pro + Gemini Advanced. Mau pakai akun ini untuk cloud LLM via reverse-engineered (bukan API key billing). Anthropic + OpenAI tidak provide OAuth resmi untuk API access.
- **Opsi:**
  - A) WebView headless once-off di setup wizard
  - B) User input cookie manual
  - C) Skip reverse-engineered, hanya Gemini API free tier
- **Keputusan:** **A** — WebView headless once-off di setup wizard. Login interactive sekali per service, extract cookie/session token, simpan di `EncryptedSharedPreferences` (Android Keystore-backed).
- **Risk:**
  - ToS violation Anthropic/OpenAI/Google (account bisa ban kalau detect bot-like usage)
  - Endpoint signature rotate sering, perlu maintenance
  - Rate limit lebih ketat dari API resmi
- **Mitigasi:**
  - User explicit consent + risk disclosure di setup wizard
  - Fallback ke Gemini API free tier (resmi, gratis) sebagai primary cloud
  - Rate limit + retry policy graceful

## ADR-006: Skip wake word di MVP, pakai manual button overlay
- **Tanggal:** 2026-05-13
- **Status:** Accepted
- **Konteks:** Wake word "Hey Fuu" custom butuh training 50-100 sample voice + Colab + TFLite integration. Significant effort untuk MVP.
- **Opsi:**
  - A) Custom train wake word dari awal
  - B) Placeholder pre-trained ("Hey Jarvis"), custom belakangan
  - C) Skip wake word MVP, manual button di overlay
- **Keputusan:** **C** — skip wake word di MVP. Voice trigger lewat tap bubble overlay.
- **Rationale:** Reduce scope Phase 2. Wake word always-on mic ada concern privacy + battery. Manual button cukup untuk personal use.
- **Future:** Wake word bisa ditambah Phase 10+ kalau Lendra request.

## ADR-005: Voice clone Fuu pakai ElevenLabs subscription existing
- **Tanggal:** 2026-05-13
- **Status:** Accepted
- **Konteks:** Lendra sudah subscribe ElevenLabs dan sudah buat voice clone Fuu. Voice ID: `gMIZZcmZCnyySbZdSZrZ`.
- **Keputusan:** Pakai ElevenLabs streaming API v3 dengan voice ID `gMIZZcmZCnyySbZdSZrZ`. Tidak perlu hire voice actor atau train custom TTS.
- **Emotion control:** "Lebih advance" — LLM-driven prosody (stability, style dinamis), audio emotion echo, pre-recorded loop injection (giggle, sigh).

## ADR-004: Tool catalog flat dengan capability metadata, bukan tier eskalasi hardcoded
- **Tanggal:** 2026-05-13
- **Status:** Accepted
- **Konteks:** Saya (Claude) awalnya propose eskalasi T1 (Intent) → T2 (API) → T3 (A11y) → T4 (Shizuku) → T5 (Vision) di code. Lendra koreksi: itu code berusaha lebih pintar daripada LLM.
- **Keputusan:** Tool catalog flat. Setiap tool punya capability metadata di description:
  - Latency expected
  - Works on: app jenis apa
  - Known fail: app yang tidak support
  - Cost (waktu, baterai, izin)
- LLM baca metadata + history + world state → putus tool mana yang dipakai dan urutannya.
- **Rationale:** Konsisten dengan filosofi "Gemma = otak, kode = tangan". Tier hardcoded adalah re-introduksi decision system yang sudah dihapus di v3 refactor.

## ADR-003: Skill memory hybrid (auto-include + recall tool)
- **Tanggal:** 2026-05-13
- **Status:** Accepted
- **Konteks:** Setelah keputusan LLM-centric, pertanyaan: bagaimana history task lama tersedia ke LLM?
- **Opsi:**
  - A) Tool `recall(query)` only — LLM panggil eksplisit
  - B) Auto-include N command terakhir di context
  - C) Hybrid
- **Keputusan:** **C** — auto-include N=20 command terakhir sebagai context default; tool `recall(query, limit)` untuk semantic search history yang lebih jauh.
- **Rationale:** Auto-include cukup untuk 80% kasus (pattern recognition recent). Recall tool untuk 20% kasus referensi temporal/topik lama. Mencegah context bloat saat history >100.

## ADR-002: Full agent dari awal, bukan chatbot-with-tools yang dipoles
- **Tanggal:** 2026-05-13
- **Status:** Accepted (cornerstone decision)
- **Konteks:** Lendra tanya "Kamu tau bedanya agent & chatbot?" Mengarahkan saya menyadari arsitektur draft awal masih chatbot pattern (request-response single-shot, no task entity, no persistent world model).
- **Opsi:**
  - A) Chatbot-with-tools dulu (Phase 1-3), agent layer di Phase 4-5 refactor
  - B) Full agent dari awal
- **Keputusan:** **B** — full agent. Lendra eksplisit "ChibiClaw harus full agent dari awal" dan "jangan simpel diawal, susah kompleks tidak masalah".
- **Rationale:** Refactor besar di tengah jalan lebih costly daripada upfront design effort. Konsisten dengan filosofi.
- **Implication:**
  - Phase 1 jadi 5 minggu (build TaskManager + AgentRuntime + WorldObserver + MemoryStore + ToolDispatcher + ConversationManager bersamaan)
  - Total roadmap 21-24 minggu (vs 12-15 kalau chatbot-with-tools)

## ADR-001: LLM-centric architecture, hindari "code lebih pintar dari LLM"
- **Tanggal:** 2026-05-13
- **Status:** Accepted (foundational)
- **Konteks:** Lendra kritik draft awal saya yang re-introduce SkillLibrary matcher, InferenceRouter cascade, EmotionDirector fusion, vision blacklist hardcoded, tier eskalasi T1-T5. "kulihat dari architecture nya, ini seperti code berusaha menjadi lebih pintar daripada llm?"
- **Keputusan:** **LLM-centric murni**.
- **Yang code DOES (tangan):**
  - Tool implementations
  - Adapter pattern (Gemma + cloud)
  - Audio pipeline
  - Storage
  - ChibiService + overlay
  - Audit log
- **Yang code DOES NOT (otak):**
  - Pre-LLM matching / caching
  - Confidence threshold routing
  - Decision tree / FSM yang membatasi alur
  - Capability inference dari pattern history
- **Rationale:** Konsisten dengan v3 philosophy "Gemma = otak, kode = tangan". Latency lebih tinggi tapi konsistensi + simpler debug.

## ADR-000: Bahasa Indonesia profesional sebagai bahasa primary
- **Tanggal:** 2026-05-13
- **Status:** Accepted
- **Konteks:** Lendra Indonesian developer, work tooling sehari-hari Indonesia. Memory file menunjukkan "bahasa-default ID".
- **Keputusan:** Markdown documentation Indonesia. Voice persona Fuu Indonesia (mix EN kalau code-switch). Code English (industry standard).

---

## Format ADR untuk Decision Berikutnya

```markdown
## ADR-XXX: [Judul singkat]
- **Tanggal:** YYYY-MM-DD
- **Status:** Proposed / Accepted / Superseded by ADR-YYY
- **Konteks:** [kenapa decision ini perlu]
- **Opsi dipertimbangkan:**
  - A) ...
  - B) ...
  - C) ...
- **Keputusan:** [pilih opsi mana + detail]
- **Rationale:** [kenapa pilih itu]
- **Risk / Mitigasi:** [kalau ada]
```

Setiap decision baru, append di atas (paling baru di atas).
