# 00 — Vision & Goals

## Vision Statement

> **"Asisten suara di handphone yang bisa kontrol penuh device-mu, baik offline maupun online, dengan privasi yang kamu kontrol."**

Translasi konkret:
- **Asisten suara**: Voice-first interaction (wake word → speak → action → spoken response). Text masih ada sebagai fallback untuk situasi tidak nyaman bicara.
- **Kontrol penuh device**: Buka app, kirim pesan, set alarm, foto, file management, system settings, accessibility-resistant apps (TikTok/WA/Shopee).
- **Offline maupun online**: Multi-model. Default offline (Gemma 4 lokal) untuk privacy + zero cost. User bisa switch ke cloud (Claude / GPT / Gemini) per session untuk task kompleks.
- **Privasi yang kamu kontrol**: Setiap session user pilih: offline (no data leaves device) atau cloud (audio/text dikirim ke API). Toggle eksplisit, bukan tersembunyi.

## Goals

### Primary Goals (must-have)

1. **Voice-native interaction**
   - Wake word "Hey Fuu" terdengar dari background mode (low-power)
   - End-to-end voice latency < 3 detik untuk simple command
   - Interrupt handling: user bisa potong response Fuu
   - Indonesian-native pronunciation (TTS tidak terdengar robot)

2. **Multi-model inference dengan offline default**
   - Adapter pattern: Gemma (local) | GPT | Claude | Gemini (cloud)
   - User toggle per session: "Mode privat" (offline) vs "Mode pintar" (cloud)
   - Auto-fallback: cloud API gagal → switch ke Gemma local
   - Cost meter visible untuk cloud usage

3. **Vision-first execution untuk app anti-accessibility**
   - Detect app blacklist (TikTok, WhatsApp, Tokopedia, Shopee, Instagram)
   - Untuk app blacklist: screenshot → vision identify → gesture dispatch
   - Untuk app lain: accessibility tree (cepat) dengan vision sebagai fallback

4. **Capability parity dengan v3**
   - Semua 27 tool yang ada di v3 tetap berfungsi (di-konsolidasi ke 10 primitif)
   - Inline safety gate (4 HIGH-risk tool) tetap ada
   - AutoControlGate per-app policy tetap ada

### Secondary Goals (should-have)

5. **Long-term memory dengan use case yang jelas**
   - Vector store untuk semantic search di command history
   - Entity store: "ibu" → kontak, "kantor" → location, dll
   - User-controlled retention: user bisa hapus memory kapan saja
   - Privacy by design: encrypted at rest

6. **Self-correction loop**
   - Setiap action yang ada side-effect: capture before-state, execute, capture after-state, verify
   - Kalau verify failed, retry dengan modified approach
   - Log ke DevConsole untuk debugging

7. **Test infrastructure**
   - Unit test coverage minimum 30% di critical paths
   - Integration test untuk command flow end-to-end (mocked Gemma)
   - Manual test plan untuk 10 critical user journeys di device

### Tertiary Goals (nice-to-have)

8. **Cross-platform readiness**
   - Architecture allow port ke iOS via Kotlin Multiplatform di masa depan
   - Tidak commit ke Android-specific deeper than necessary

9. **Developer extensibility**
   - Plugin SDK: developer pihak ketiga bisa add custom tool via @ChibiTool annotation
   - Tool marketplace (long-term)

## Non-Goals (explicit out-of-scope)

1. **Bukan general-purpose chatbot**
   - Tidak optimize untuk conversation tanpa action
   - Bukan kompetitor ChatGPT untuk Q&A umum
   - Setiap interaksi punya intent action di device

2. **Bukan automation rule builder seperti Tasker**
   - Natural language only, tidak ada visual rule editor
   - Trigger → action chains tetap natural language

3. **Bukan home automation hub**
   - Smart home (lampu, AC, dll) bukan focus utama. Bisa via Intent ke app vendor (Mi Home, Google Home), tapi bukan dukung protokol native (Matter, Zigbee, dll).

4. **Bukan multi-device sync**
   - Setiap device standalone. Sync antar HP user (HP utama + HP cadangan) bukan in-scope v4. Mungkin v5+.

5. **Tidak iOS untuk v4**
   - Architecture allow porting, tapi v4 = Android only.

6. **Tidak desktop**
   - Cowork sudah cover desktop. ChibiClaw fokus mobile.

7. **Tidak hardware device terpisah**
   - Bukan kompetitor Rabbit R1 / Humane Pin. Software only.

## Target Users

### Primary Persona: "Power User Indonesia"
- Usia 25-45
- Pengguna Android (Pixel, Samsung, Xiaomi, OPPO)
- Familiar dengan Tasker / Macrodroid (sudah pakai automation tapi merasa rule-based terlalu kaku)
- Concern privacy (resist Google Assistant cloud)
- Bilingual: Bahasa Indonesia + English
- Willing pay $5-10/bulan untuk premium feature

### Secondary Persona: "Hands-Free Need"
- Driver Gojek/Grab (butuh hands-free phone control while driving)
- Field worker (kurir, sales lapangan)
- User dengan disabilitas tangan/penglihatan
- Lansia yang struggle dengan touch interface

### Anti-Persona (NOT target)
- General consumer yang pakai HP cuma untuk WhatsApp + sosmed
- User yang sudah puas dengan Google Assistant
- User dengan HP entry-level di bawah Rp 2 juta (RAM <4GB tidak cukup untuk Gemma local)

## Success Definition

ChibiClaw v4 dianggap sukses kalau **dalam 3 bulan post-release**:
- ≥100 daily active users (DAU) yang return at least 3× per minggu
- Voice command success rate ≥85% di Top 20 task scenarios
- Crash-free session ≥99%
- User rating Play Store ≥4.0 (kalau dipublish)

Lebih detail di [08-success-metrics.md](08-success-metrics.md).

## Open Questions — ANSWERED (2026-05-10)

### Q1. Personal project atau eventual product? ✅ ANSWERED
**Jawaban user**: **Personal dulu, evaluate 6 bulan.**
- Fokus kebutuhan kamu sendiri sebagai user pertama
- No business model required
- Setelah 6 bulan, kalau retention kuat (kamu pakai daily) baru pertimbangkan productize

### Q2. Cloud LLM access strategy? ✅ ANSWERED (after 3 rounds)
**Jawaban user**: **BYOK API key, stay personal.**

Konteks decision:
- User awalnya minta OAuth subscription experience ("login langsung oauth bukan api key")
- Saya jelaskan: OAuth subscription untuk LLM consumer **tidak ada di pasar** (Anthropic Pro, ChatGPT Plus, Gemini Advanced semua hanya web/own-app, tidak open API ke third-party)
- Pilihan untuk OAuth subscription experience = pivot ke product company (ChibiClaw charge subscription, ChibiClaw bayar API)
- User: "tidak masalah, karena ini awalan untuk penggunaan pribadi, jangan pikir product dulu" → stay personal

**Final implementasi**:
- Default offline: Gemma 4 lokal
- Cloud opt-in: Claude / GPT / Gemini via **BYOK API key**
- User input API key di Settings, ChibiClaw simpan encrypted
- Onboarding tutorial: "Klik tombol, browser buka Anthropic Console, login Google → Create API Key → copy ke ChibiClaw"
- Time setup user: ~3 menit one-time per provider
- Billing: user dapat invoice langsung dari provider, ChibiClaw zero infrastructure cost

**Catatan untuk masa depan**: Kalau eventually pivot ke product company (di luar v4 scope), bisa add subscription model dengan ChibiClaw's master API key. Defer ke v5+.

### Q3. Privacy stance untuk audio (STT)? ✅ ANSWERED
**Jawaban user**: **Pragmatic — default offline, toggle cloud.**
- Default Whisper.cpp lokal
- User bisa toggle ke OpenAI Whisper API / Deepgram per session di Settings (pakai BYOK)
- Visible indicator di UI (lock icon = offline STT, cloud icon = cloud STT)

### Q4. Voice latency target acceptable? ✅ ANSWERED
**Jawaban user**: **Tidak peduli latency, prioritaskan kualitas.**

Implication:
- Pakai model paling kuat (Claude Opus untuk cloud, Gemma 4 4B full untuk offline)
- Pakai best STT (Whisper medium/large untuk offline, Deepgram/OpenAI cloud)
- Pakai best TTS (ElevenLabs cloud, Piper id-fajri offline)
- Acceptable latency: 5-10 detik simple command, 30s+ untuk task kompleks
- **Skip Phase 2 latency optimization** (streaming TTS, Whisper tiny, etc.)

### Q5. OpenClaw protocol — relevan?
**Status**: Defer ke v5. Tidak in-scope v4.

### Q6. Distribusi: Play Store, sideload, atau both?
**Default**: Sideload (GitHub Release) untuk v4 personal use. Play Store deferred (tidak relevan untuk personal).

---

## Decision Status

✅ **All critical questions answered.** Plan siap eksekusi setelah Phase 0 (validate v3) selesai.

**Next action**: User test ChibiClaw v3 di device fisik, fix critical bugs, lalu kickoff Phase 1.

---

**Next**: [01-design-paper.md](01-design-paper.md) — Comprehensive design paper.
