# 09 — End-State Vision (Output Jadi)

**Audience**: All stakeholders
**Last updated**: 2026-05-10
**Purpose**: Apa output jadi setelah 16 minggu — tampilan, behavior, feel.

---

## Cover Story

> **"Aku punya asisten yang ngerti aku. Aku panggil 'Hey Fuu' kapan pun, dia siap. Dia bisa apa aja di HP. Dan datanya cuma di HP aku — kalau aku mau cepat dia bisa pakai cloud, tapi aku yang putuskan."**
>
> — testimonial yang diharapkan dari user puas v4

---

## Day-in-the-life: User stories

### Story 1: Pagi rutinitas

**06:30** — Alarm bunyi. User bangun.
**User**: "Hey Fuu, matikan alarm."
**Fuu**: "OK." [TTS] [alarm off]

**06:45** — User di kamar mandi, HP di meja.
**User**: "Hey Fuu, putar musik playlist morning di Spotify."
**Fuu**: "Bentar ya, aku buka Spotify dulu." [opens Spotify, navigate to playlist, play]

**07:00** — Sambil sarapan.
**User**: "Hey Fuu, baca highlights berita pagi."
**Fuu**: "Aku buka Google News dulu." [opens, summarize top 5 articles via screen analysis]

**07:30** — Di motor, mau berangkat.
**User**: "Hey Fuu, set Maps ke kantor, hindari macet, terus minimize."
**Fuu**: "Maps siap." [Google Maps navigation, minimize to dashboard widget]

Total interaction: 4 voice commands, 0 unlock HP, 0 buka aplikasi manual.

### Story 2: Siang kerja

**13:00** — Lunch break.
**User**: "Hey Fuu, ringkas WhatsApp grup kerja jam 9 sampai sekarang."
**Fuu**: "Bentar." [opens WA via vision-first karena WA blacklist, scroll grup, summarize]
**Fuu**: "Ada 32 pesan. Highlight: Bos minta deadline diundur ke Jumat, Tim QA sudah selesai test build, Anita tidak hadir meeting jam 2."

**13:15** —
**User**: "Hey Fuu, balas grup, bilang 'OK noted, deadline jumat'."
**Fuu**: "Konfirmasi: kirim 'OK noted, deadline jumat' ke grup WA Tim Kerja?"
**User**: "Iya."
**Fuu**: [vision-first locate text input, type, send] "Sudah."

### Story 3: Malam relaks

**21:00** — Nonton TV.
**User**: "Hey Fuu, mode privat. Cek email pribadi aku, ada email dari bank?"
**Fuu**: "OK, mode privat aktif. Aku buka Gmail." [switch ke Gemma local, opens Gmail]
**Fuu**: "Ada 1 email dari BCA jam 14:23, subject: 'Konfirmasi transaksi'. Mau aku baca?"
**User**: "Iya."
**Fuu**: "Transaksi belanja online Rp 250,000 jam 13:55 di Tokopedia. Konfirmasi atau report fraud."

### Story 4: Hands-free saat darurat

**Driver di motor** (story persona Hands-Free Need)
**00:30** — Lagi naik motor.
**User**: "Hey Fuu, telepon istri."
**Fuu**: "Aku panggil 'istri' yang tersimpan sebagai Sari. Konfirmasi telepon?"
**User**: "Iya."
**Fuu**: [calls] [sambungkan ke Bluetooth headset]

User tidak pegang HP sama sekali. Drives safely.

---

## Visual Mockups

### Onboarding flow

```
┌─────────────────────────────┐
│  [Fuu mascot illustration]  │
│                              │
│   Halo! Saya Fuu.            │
│   Asisten kamu di HP.        │
│                              │
│   [Mulai Setup ▶]            │
└─────────────────────────────┘
                ↓
┌─────────────────────────────┐
│   Privasi kamu, kontrol      │
│   kamu.                      │
│                              │
│   ┌──────────────────────┐  │
│   │ 🔒 Privat Saja        │  │
│   │ Offline, gratis       │  │
│   │ Latency 3-5 detik     │  │
│   └──────────────────────┘  │
│                              │
│   ┌──────────────────────┐  │
│   │ ☁️ Pintar (Cloud)     │  │
│   │ Pakai API key kamu    │  │
│   │ Latency 1-2 detik     │  │
│   │ Estimasi $5-20/bulan  │  │
│   └──────────────────────┘  │
│                              │
│   ┌──────────────────────┐  │
│   │ 🔄 Hybrid             │  │
│   │ Default privat,       │  │
│   │ cloud kalau perlu     │  │
│   └──────────────────────┘  │
│                              │
│   [Pilih Hybrid (Saran)]     │
└─────────────────────────────┘
                ↓
┌─────────────────────────────┐
│   Latih Wake Word            │
│   "Hey Fuu"                  │
│                              │
│   Bilang "Hey Fuu" 5 kali    │
│   untuk personalize.         │
│                              │
│   [🎙️ Mulai] (1/5)           │
│   ████░░░░░░  40%            │
└─────────────────────────────┘
```

### Main UI (Voice Mode default)

```
┌─────────────────────────────┐
│ ChibiClaw    🔒 Privat   ⚙️ │ ← header: privacy mode + settings
├─────────────────────────────┤
│                              │
│                              │
│         ●                    │ ← Fuu state indicator (color)
│       ╱   ╲                  │
│      │ Fuu │                 │
│       ╲   ╱                  │
│         ●                    │
│                              │
│        Idle                  │
│   Bilang "Hey Fuu"           │
│                              │
│                              │
├─────────────────────────────┤
│  💬 Chat   📊 Riwayat   ⚙️  │ ← bottom nav
└─────────────────────────────┘
```

State variations (Fuu indicator):
- **Idle**: gray pulse, "Bilang 'Hey Fuu'"
- **Listening**: blue pulse, animasi gelombang suara, "Mendengarkan..."
- **Processing**: purple spinner, "Berpikir..."
- **Speaking**: green wave, "Fuu sedang bicara..."
- **Error**: red, "Ada masalah" + tap untuk detail

### Chat fallback (kalau user tidak mau voice)

```
┌─────────────────────────────┐
│ ChibiClaw    🔒 Privat   ⚙️ │
├─────────────────────────────┤
│ User: nyalakan senter         │
│                              │
│  Fuu: OK, senter aktif. 🔦   │
│                              │
│ User: kirim WA ke ibu         │
│       sampai jumpa nanti      │
│                              │
│  Fuu: Konfirmasi:             │
│  Kirim ke "Ibu" (+62 8xxx)?   │
│  isi: "sampai jumpa nanti"    │
│  [Tidak]  [Ya, Lanjut]        │
│                              │
├─────────────────────────────┤
│  [...]              🎙️    ▶  │
└─────────────────────────────┘
```

### Settings overview

```
Settings
├── 🤖 AI Engine
│   ├── Privacy Mode: [Hybrid ▼]
│   ├── Default Adapter: [Gemma Lokal ▼]
│   ├── Cloud Adapter Configurations
│   │   ├── Anthropic Claude  [Connected ✓]  Configure...
│   │   ├── OpenAI            [Not setup]    Configure...
│   │   └── Google AI         [Not setup]    Configure...
│   └── Model Files: 4.2 GB used
├── 🎙️ Voice
│   ├── Wake Word: "Hey Fuu" [Re-train]
│   ├── STT Provider: [Whisper Lokal ▼]
│   ├── TTS Voice: [Fajri ▼] [Preview]
│   └── Sensitivity: ████████░░ (80%)
├── 🛡️ Safety
│   ├── Auto-Control per App: 5 configured
│   ├── Confirmation Severity: [HIGH only ▼]
│   └── Caller Whitelist: 3 apps
├── 💰 Cloud Usage
│   ├── This month: $14.80
│   ├── Budget alert: $20/month
│   └── [View detailed dashboard]
├── 🧠 Memory
│   ├── Stored entities: 47
│   ├── Command history: 2,341 turns
│   ├── [Clear all memory] [Export data]
├── 🔒 Privacy
│   ├── Audit log: 234 entries this month
│   ├── Telemetry: OFF
│   └── [View what's stored]
├── 🔧 Diagnostics
│   ├── Latency stats
│   ├── Crash log
│   └── Export logs
└── ℹ️ About
```

### Voice overlay (saat Fuu listening, di atas app lain)

```
[user di TikTok, say "Hey Fuu"]

       ┌─────────────────┐
       │  ●●●  (listening)│  ← floating overlay above app
       │  "Mendengarkan..."│
       └─────────────────┘

[TikTok continues di belakang, overlay non-blocking, tap untuk cancel]
```

---

## Behavior Demo Scenarios (Acceptance Tests)

### Scenario A: Simple system control

```
User (voice): "Hey Fuu, naikkan volume jadi 50%"
Fuu state: IDLE → LISTENING → PROCESSING → SPEAKING → IDLE
Backend trace:
  WakeWord("Hey Fuu") → 0.4s
  STT("naikkan volume jadi 50%") → 1.2s
  Gemma local infer → 1.8s
    → ToolCall: system(target="volume", state="50")
  AudioControlExecutor.perform("volume", "set", "50") → 0.1s
  Gemma generate response → 0.6s
    → "Volume sudah 50%."
  TTS speak → 0.5s

Total: 4.6s end-to-end
```

### Scenario B: Multi-step di app blacklist

```
User (voice): "Hey Fuu, buka WhatsApp, balas ke ibu 'sampai nanti malam'"

Fuu chains:
  1. launchApp(WhatsApp) → Intent.startActivity → 1s
  2. screenshot() → analyze, identify chat list
  3. visionFindElement("Ibu chat") → coords (450, 320)
  4. tap(450, 320) → 0.3s
  5. screenshot() → identify text input area
  6. visionFindElement("text input") → coords (540, 1400)
  7. tap(540, 1400) → focus input
  8. type("sampai nanti malam") → 0.5s
  9. visionFindElement("send button") → coords (1020, 1400)
  10. ToolCall: meta(action="confirm", "Send 'sampai nanti malam' to Ibu?")
  11. User confirms via voice "iya"
  12. tap(1020, 1400) → send
  13. screenshot() verify message sent
  14. TTS: "Pesan terkirim ke Ibu."

Total: ~25s (multimodal heavy)
```

### Scenario C: Privacy mode switch mid-session

```
User: "Hey Fuu, ringkas semua chat aku hari ini"
Fuu: "Task ini akan baca chat WA + Telegram + Gmail kamu hari ini. 
       Mau pakai mode privat (offline, agak lambat ~20 detik)
       atau cloud (cepat 5 detik tapi data dikirim ke Anthropic)?"
User: "Privat"
Fuu: [switch to GemmaLocalAdapter for this session]
     [reads via accessibility/vision]
     [summarizes]
     "Total 47 pesan dari 5 percakapan. Highlights: ..."
```

---

## Final Capability Matrix

| Kategori | v3 status | v4 target |
|----------|-----------|-----------|
| **Input modalities** | Text only | Voice (primary) + text |
| **Output modalities** | Text streaming | Voice (TTS) + text |
| **Wake word** | None | "Hey Fuu" custom trained |
| **Inference engines** | LiteRT-LM Gemma 4 only | LiteRT-LM/MediaPipe + 3 cloud adapters |
| **Vision execution** | Fallback only | Primary for blacklist apps |
| **Memory** | Last 5 commands inline | Vector store + entity resolution |
| **Privacy mode** | Always offline | User-controlled toggle (offline/cloud/hybrid) |
| **Audit log** | None | Comprehensive |
| **Cost tracking** | N/A | Realtime meter + budget alerts |
| **Tool count** | 27 | 10 primitives (consolidated) |
| **Languages** | Indonesian + English (text) | Indonesian voice + English voice + Indonesian-English mix |
| **Test coverage** | 0% | 35% target |
| **Build size** | 345 MB | <400 MB |
| **First-install download** | 4 GB | 1.6 GB (E2B default) |

---

## "Saya bisa kerja apa pakai Fuu sekarang?"

End user akan jawab:

✅ **Saya bisa**:
- Buka app apa pun (TikTok, WA, Gmail, Maps, dll), bahkan saat tangan kotor
- Kirim pesan voice-only
- Set alarm, timer, reminder, calendar event
- Kontrol media (musik, video, podcast)
- Cek info: cuaca, jadwal, kontak, riwayat panggilan
- Otomasi sederhana ("setiap jam 7 pagi turn off airplane mode")
- Vision-based action di app yang biasanya block automation
- Switch privasi per task
- Lihat berapa banyak data saya yang leave device (audit log)

❌ **Saya tidak bisa** (intentionally):
- Login ke app dengan kredensial saya (Fuu never auto-input password)
- Transaksi finansial otomatis (always butuh user confirm)
- Posting publik tanpa konfirmasi
- Cross-device control (HP A → HP B)

⏳ **Saya bisa di v5+** (defer):
- iOS support
- Smart home protocol native
- Voice cloning untuk Fuu personal voice
- Plugin marketplace
- Multi-user / family share

---

## What "Sukses" Looks Like (3-month post-release)

### Hard signals
- ≥50 DAU
- 30-day retention ≥40%
- Voice command success rate ≥85%
- Crash-free ≥99%
- Average rating ≥4.0 (kalau publish)

### Soft signals
- User unprompted recommend ke teman
- Power user contribute custom tools (kalau plugin SDK eventual)
- Beta tester upgrade ke stable hari pertama release
- Komunitas Discord/Telegram aktif (≥10 message/day)
- Saya sendiri pakai Fuu daily, tidak balik ke Google Assistant

### Anti-signals (warning)
- DAU plateau <30 setelah 2 bulan
- Beta tester pasif setelah 1 minggu
- Saya sendiri lupa pakai Fuu
- Crash rate >3%
- Negative feedback "lebih ribet dari Google Assistant"

---

## Final Pitch (kalau publish)

**One-liner**: "Fuu adalah asisten suara di HP yang ngerti Bahasa Indonesia, kerja offline, dan kamu yang kontrol privasinya."

**Tagline options**:
- "Asisten yang ngerti kamu, bahasa kamu, privasi kamu."
- "HP kamu, kontrolnya makin gampang. Tinggal bilang."
- "Lebih dari assistant. Fuu yang menggantikan kamu."

**Differentiator (3 bullet untuk landing page)**:
1. 🇮🇩 **Bahasa Indonesia native** — Fuu ngerti slang, dialek, mix code dengan Inggris
2. 🔒 **Privasi by design** — default offline, kamu yang putuskan mau ke cloud atau enggak
3. 🛠️ **Full akses HP** — buka app apa aja, otomasi apa aja, hands-free

---

## Definition of "Done" untuk v4

ChibiClaw v4 ship-ready kalau:

- [ ] Semua 20 manual test scenario PASS
- [ ] Voice command success rate ≥85% di 100-command test
- [ ] Latency p50 ≤5s offline, ≤3s cloud
- [ ] Build size ≤400MB
- [ ] First-install download (model + APK) ≤2.5GB total
- [ ] Memory peak ≤4.5GB di mid-range device
- [ ] Privacy mode offline = packet sniffer verify NO traffic
- [ ] Audit log functional
- [ ] All 4 inference adapters working
- [ ] Wake word false positive <5%/hour
- [ ] Crash-free ≥99% in 2-week beta
- [ ] Documentation user-facing complete
- [ ] Documentation engineering complete (this folder)
- [ ] CHANGELOG accurate

Kalau semua check ✅ → release stable.
Kalau >2 belum check → defer release, address dulu.

---

**End of v4 design documentation.** Total: 9 documents, ~25,000 kata, full coverage planning + architecture + tech + ops.

**Next action**: User decision on Open Questions di [00-vision-and-goals.md § Open Questions](00-vision-and-goals.md#open-questions). After decision → kickoff Phase 0 (validate v3) → Phase 1.
