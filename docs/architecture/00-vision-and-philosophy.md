# 00 — Vision & Philosophy

## Vision

**ChibiClaw v4 adalah AI agent personal di Android yang bisa mengontrol HP sepenuhnya, berjalan di background, dengan voice persona emotional bernama Fuu.**

Bukan asisten yang nunggu user ketik perintah dan balas teks. Bukan chatbot yang hanya berbicara. Tapi **agent**: entitas otonom yang punya goal, observasi dunia, ambil aksi, observasi lagi, lalu iterasi sampai goal tercapai — atau eskalasi ke user kalau mentok.

### Skenario Penggunaan Yang Diinginkan

1. **Voice command sederhana**
   "Hey Fuu, kirim WA ke Budi: meeting jam 3" → Fuu buka WA, pilih kontak, ketik pesan, kirim, lapor balik.

2. **Multi-step otonomi**
   "Cariin tiket KAI Jakarta-Bandung paling murah besok pagi" → Fuu buka app KAI, pilih tanggal/route, filter harga, screenshot opsi, balikin summary ke user untuk pilihan.

3. **Proactive trigger**
   Setting: "Kalau Mama WA antara jam 18-22 dan aku tidak di kantor, balas dengan ✓". Fuu menjalankan ini sendiri tanpa user perlu trigger ulang.

4. **Persistent memory**
   Fuu tahu Budi = kontak kerja (chat soal meeting), Mama = keluarga (chat tidak boleh delayed). Pakai info ini untuk shape response.

5. **Background polite**
   Saat user sedang main game, Fuu tidak ganggu screen. Tugas di background eksekusi diam-diam, hasil di-notif kalau perlu.

---

## Filosofi Inti

### 1. "Gemma = otak, kode = tangan"

Filosofi ini diwarisi dari v3 refactor (April 2026). Di v2, ada 5 sistem keputusan paralel yang bersaing dengan Gemma (FastPathMatcher, ModelRouter, ChibiStateMachine, TextActionParser, ApprovalGate). Semua dihapus.

**Prinsip:** LLM yang putus semua keputusan. Kode hanya:
- Menyediakan tools
- Mengeksekusi tool call
- Menyediakan context (history, world state, memory)
- Logging audit

**Kode TIDAK BOLEH:**
- Pre-LLM matching ("kalau user bilang X, langsung lakukan Y")
- Caching policy ("app X selalu pakai gesture Y")
- Confidence threshold routing ("kalau confidence < 0.7, escalate")
- Decision tree (FSM yang membatasi alur agent)

Kalau ada signal yang penting, **kasih sebagai context ke LLM** — LLM yang putus pakai atau tidak.

### 2. Agent, Bukan Chatbot

| Dimensi | Chatbot | Agent |
|---------|---------|-------|
| Unit kerja | Message (per turn) | **Task** (multi-step sampai selesai) |
| Loop | User input → reply → wait | **Observe → reason → act → observe** sampai done atau blocked |
| Trigger | Reactive ke user | Reactive + **proactive** (event, schedule, world state) |
| State | Conversation history | **World snapshot** + persistent memory + task progress |
| Output | Text reply | Aksi yang **mengubah dunia**; teks hanya lapor balik |
| Autonomy | Tunggu instruksi | **Pilih langkah sendiri**, retry sendiri, eskalasi sendiri |
| Concurrency | Satu percakapan | **Multi-task paralel** di background |
| Memory | Chat history (volatile) | **Knowledge persistent** (preferensi, kontak, kebiasaan) |

Implementation implication: Task adalah first-class entity dengan FSM lifecycle, bukan ephemeral command.

### 3. Privacy-First, Cloud-Fallback

Ini HP pribadi, data sensitif (kontak, lokasi, SMS, layar). Default semua di-device. Cloud hanya dipanggil kalau Gemma lokal kurang.

**Hierarki escalation:**
1. **Gemma 4 4B lokal** (default) — semua task dicoba di sini dulu
2. **Gemini 2.5 Flash free tier** (Google AI Studio, 1500 req/day) — kalau LLM emit tool `escalate_to_cloud`
3. **Claude.ai web session** (reverse-engineered cookie/token) — task yang butuh reasoning panjang
4. **ChatGPT web session** (reverse-engineered) — alternatif kalau Claude rate-limited

LLM yang explicit emit `escalate_to_cloud(reason, target)` — bukan code yang putuskan.

User bisa toggle "full local mode" di settings — semua escalation di-disable.

### 4. Privacy-Aware Memory

Memory yang persistent (preferensi user, kontak, kebiasaan) DISIMPAN LOKAL via Room + SQLCipher encryption. Tidak boleh sync ke cloud.

Kalau cloud LLM butuh konteks memory, snippet relevant dikirim **per-call** (transient, tidak persistent di cloud).

### 5. No Hardcoded Tier Eskalasi

Tier executor (Intent → A11y → Shizuku → Vision) **tidak hardcoded** di code. Semua tools eksis dengan capability metadata di tool description. LLM yang baca dan pilih urutan.

Kalau `accessibility_click` gagal di TikTok dengan error `SELECTOR_NOT_FOUND`, LLM observe, lalu coba `vision_tap` di iterasi berikutnya. Tidak ada code yang "kalau di TikTok pakai vision". LLM punya world knowledge dari conversation history + tool description.

### 6. Standing Instructions = Superset Cron

Cron itu time-only. Standing instruction agent lebih ekspresif:

- **TimeTrigger**: cron expression
- **EventTrigger**: notif, geofence, app launch, battery, sensor
- **PredicateTrigger**: expression yang di-evaluate (mungkin LLM-evaluated)
- **CompositeTrigger**: AND/OR/NOT dari trigger lain

Contoh: "Antara 18-22 + bukan di kantor + notif WA dari Mama → auto-reply ✓"

### 7. Self-Correction Loop

Tool result punya error class taxonomy. LLM observe error → adapt strategi → retry. Bukan code yang retry policy hardcoded.

```
Error classes: SELECTOR_NOT_FOUND, PERMISSION_DENIED, TIMEOUT, 
               AMBIGUOUS, NETWORK_ERROR, RATE_LIMITED, UNKNOWN
```

Tool description hint: "Kalau error_class=SELECTOR_NOT_FOUND, coba vision_tap"

### 8. Code Quality Standards

Diwarisi dari Lendra's coding standards (memory file):
- **Audit code sebelum deliver** — jangan ship code dengan TODO/placeholder
- **No placeholders** — kalau belum bisa implement, dokumentasi alasannya, bukan stub
- **Verify lib versions** — Maven Central / GitHub release, bukan asumsi
- **Copy-paste ready commands** — semua snippet bisa langsung dijalankan

### 9. Bahasa Indonesia Default

UI text + dokumentasi default Indonesia. Voice persona Fuu speak Indonesia (mix English kalau user code-switch).

Tool name + code identifier tetap English (standar industry).

### 10. Long-Horizon Goal: VRM Avatar Embodiment

Phase 10 (bonus) — Fuu jadi VRM avatar floating di HP. Lipsync dengan TTS audio. Expression dari emotion vector. Bukan sekadar suara, tapi entitas visible.

Ini target stretch — tidak boleh delay Phase 0-9.

---

## Yang BUKAN Vision

Untuk menghindari scope creep, ini bukan:

- **Bukan productize ke umum** (Phase 0-9). Personal use only. Kalau nanti productize, scope baru.
- **Bukan AI girlfriend / romantic companion**. Fuu adalah asisten yang punya persona kawaii (lembut, profesional, to-the-point). Bukan emotional substitute.
- **Bukan multi-user / family device**. Single-user, single-account.
- **Bukan general-purpose Tasker replacement**. Fokus AI agent dengan voice + vision; bukan IFTTT framework.
- **Bukan deep customizable platform** — Lendra adalah user utama, UI/UX dibuat untuk satu orang yang mengerti tech. Tidak perlu polish marketplace-grade.
- **Bukan privacy-perfect** — cloud opt-in masih ada (Phase 4+). Trade-off jujur: kualitas reasoning > privasi absolut, tapi user bisa toggle offline.

---

## Metrik Sukses

Untuk personal use, metrik kualitatif lebih relevan dari kuantitatif:

1. **Pakai sendiri ≥1x/hari konsisten selama 3 bulan** setelah Phase 9 stable
2. **Task success rate ≥80%** untuk 10 task harian top yang user define
3. **Latency p50 ≤5 detik** voice command → action terjadi (offline mode)
4. **Crash rate ≤1%** per hari pemakaian
5. **Battery drain ≤5%/jam idle** dengan service jalan
6. **Tidak feel sad kalau hilang** — kalau iya, berarti project value-nya nyata

Anti-metrik (jangan dikejar):
- Total commands ever processed
- LOC
- Github stars (private project)
- Time spent in app
