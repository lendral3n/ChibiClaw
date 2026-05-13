# 19 — Compliance & Privacy

PDP-ID (UU PDP 27/2022) + audit log + consent + data minimization. Detail riset di [docs/research/11-pdp-id-ai-compliance.md](../research/11-pdp-id-ai-compliance.md).

---

## Konteks Personal Project

ChibiClaw v4 untuk pemakaian pribadi Lendra. Tidak distribusi publik. Compliance burden lebih rendah dari produk publik, tapi:

1. **Practice good habit** — kalau nanti productize, foundation sudah bersih.
2. **Self-protection** — kalau data bocor (lost HP, dll), encrypted at rest mengurangi risk.
3. **Cloud opt-in** — saat user pakai Gemini/Claude/GPT, data transit ke pihak ke-3. Wajib aware.

Disclaimer: dokumen ini **bukan legal advice**. Untuk action eksekusi (publish, productize), konsultasi advokat data privacy.

---

## Prinsip yang Dipakai

1. **Privacy-first by default**: cloud opt-in, local mode default.
2. **Data minimization**: simpan minimum yang perlu. Tidak simpan raw voice; simpan transcribed text.
3. **Encryption at rest**: SQLCipher untuk Room. EncryptedSharedPreferences untuk secret (API key, cloud session, voice ID).
4. **Audit log**: setiap action sensitif logged. 90 hari TTL default.
5. **Right to forget**: user bisa erase semua data.
6. **Right to export**: user bisa export semua memory + log.
7. **Granular consent**: per-permission (mic / accessibility / vision / cloud), bukan all-or-nothing.

---

## Data Categories di ChibiClaw

| Data | Sensitivity | Storage | Cloud Transit? |
|------|-------------|---------|----------------|
| User profile (nama, dll) | Medium | Memory_record (SQLCipher) | Opt-in (saat cloud LLM call dengan memory context) |
| Kontak | Medium-High | Memory_record + on-the-fly query Android ContactsContract | Opt-in |
| Lokasi | High | Memory_record (saved places) | Opt-in |
| SMS content | High | Tidak disimpan — direct send via Intent | Tidak |
| Audio voice (mic input) | High | Buffer transient (4s window), tidak persistent | Tidak untuk offline Whisper. Opt-in untuk cloud STT. |
| Voice biometric (Fuu output) | Medium (kalau dianggap biometric) | ElevenLabs voice_id reference (not raw audio) | Yes (semua TTS call lewat ElevenLabs cloud) |
| Screen capture (vision) | High | Buffer transient per frame, tidak persistent | Opt-in (vision multimodal cloud LLM) |
| Notifications content | High | World snapshot transient, possible audit log dengan ringkasan | Opt-in |
| Command history | Low-Medium | Room (SQLCipher) | Opt-in (auto-include di context) |
| Task history (agent steps) | Medium | Room (SQLCipher) | Opt-in |
| Audit log | Low | Room (SQLCipher) | Tidak |
| API keys + cloud session | Critical | EncryptedSharedPreferences (Android Keystore) | Tidak |

---

## Privacy Notice (Setup Wizard)

Wajib display di first launch. User harus aktif consent.

Konten template:

```
ChibiClaw — Privacy Notice
═══════════════════════════════

Aku Fuu, AI asisten di HP kamu. Sebelum kita mulai, ada yang harus
kamu tahu.

══ Yang aku akses ══
• Mikrofon — buat dengar perintah suara kamu
• Layar (Accessibility + opsional vision) — buat lakukan task di app
• Notifikasi — buat reagir ke event (kalau kamu pasang standing instruction)
• Lokasi (opsional) — buat trigger berdasarkan tempat
• Kontak (opsional) — buat "kirim WA ke Budi" jalan

══ Di mana data kamu disimpan ══
• Default: di HP kamu sendiri, encrypted (SQLCipher)
• Cloud (opt-in): saat kamu pakai Gemini/Claude/GPT, snippet task
  ringkasan akan transit ke server mereka. Kontrol penuh ada di kamu.

══ Yang TIDAK aku simpan ══
• Audio mentah (cuma transcribed text)
• Konten SMS (langsung kirim tanpa simpan)
• Screenshot mentah (cuma processed result)
• Password / OTP (di-redact dari log otomatis)

══ Kontrol kamu ══
• Toggle off cloud kapan saja — back to full local mode
• Export semua data ke JSON
• Erase semua data permanen
• Lihat audit log: siapa akses apa kapan

══ Risiko yang harus kamu sadari ══
• Cloud LLM (Gemini/Claude/GPT) ada Terms of Service mereka sendiri
• Reverse-engineered web session (Claude.ai / ChatGPT) BERPOTENSI 
  melanggar ToS — akun bisa di-ban. Pakai dengan sadar.
• Voice clone Fuu di ElevenLabs subject to ElevenLabs ToS.

══ Aku jamin (best effort) ══
• Encryption at rest
• 90 hari rotating audit log
• Right to erase / export
• No telemetry phone-home

═══════════════════════════════
                                         [Tidak setuju]   [Setuju, mulai]
```

User tap "Setuju" → masuk setup wizard. Tap "Tidak" → exit app.

---

## Granular Consent (per Permission)

Setelah privacy notice, setup wizard per-permission step. User bisa skip per-step.

```
Step 2/8: Mikrofon
┌─────────────────────────────┐
│ Fuu perlu mikrofon buat     │
│ dengar perintah suara kamu. │
│                             │
│ Audio tidak disimpan        │
│ permanen, cuma di-          │
│ transcribe ke teks lokal.   │
│                             │
│ [Skip] [Beri akses]         │
└─────────────────────────────┘
```

Per-step consent → stored di SecurePreferences. Audit log entry per consent action.

---

## Audit Log Schema

Lihat [11-data-model.md](11-data-model.md#auditlog).

Sample entries:

```
2026-05-13 14:32:01 | LLM_CALL_LOCAL    | adapter=gemma_local, tokens=345, latency=2100ms | local | SUCCESS
2026-05-13 14:32:03 | TOOL_EXECUTED     | tool=intent_open, target=com.whatsapp | local | SUCCESS
2026-05-13 14:32:05 | DATA_READ_CONTACT | summary="Resolve 'Budi' from contacts" | local | SUCCESS
2026-05-13 14:32:08 | LLM_CALL_CLOUD    | adapter=gemini_free, tokens=890, latency=1200ms | gemini_free | SUCCESS
2026-05-13 14:32:10 | TOOL_EXECUTED     | tool=messaging, kind=WHATSAPP, recipient=hashed | local | SUCCESS
2026-05-13 14:32:11 | DATA_WRITE_MESSAGING | summary="Sent WA to contact" | local | SUCCESS
```

Note:
- `data_summary` ringkasan, **bukan raw content**
- Phone number / contact name di-hash atau di-redact sebelum tulis ke log
- Cloud destination dicatat untuk transparency

Implementasi:

```kotlin
@Singleton
class AuditLogger @Inject constructor(
    private val dao: AuditDao,
    private val securePrefs: SecurePreferences,
) {
    
    suspend fun log(
        actionType: AuditActionType,
        dataSummary: String,
        taskId: String? = null,
        toolName: String? = null,
        cloudDestination: String? = null,
        resultStatus: AuditResultStatus = AuditResultStatus.SUCCESS,
    ) {
        // Redact sensitive content
        val redacted = redactSensitive(dataSummary)
        
        val entry = AuditLogEntity(
            id = UUID.randomUUID().toString(),
            timestamp = Instant.now(),
            actionType = actionType,
            taskId = taskId,
            toolName = toolName,
            dataSummary = redacted,
            cloudDestination = cloudDestination,
            userConsentState = securePrefs.currentConsentSnapshot(),
            resultStatus = resultStatus,
            ttlUntil = Instant.now().plus(Duration.ofDays(securePrefs.auditRetentionDays())),
        )
        
        dao.insert(entry)
    }
    
    private fun redactSensitive(text: String): String {
        return text
            // Phone numbers
            .replace(Regex("\\+?\\d{10,}"), "[PHONE]")
            // Email
            .replace(Regex("[\\w.-]+@[\\w.-]+\\.\\w+"), "[EMAIL]")
            // Credit card patterns
            .replace(Regex("\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b"), "[CARD]")
            // Take only first 200 char untuk hindari leak
            .take(200)
    }
}
```

---

## Data Lifecycle

### Retention

| Data | Default TTL | Configurable | Auto-cleanup |
|------|-------------|---------------|--------------|
| audit_log | 90 hari | Yes (30-365) | WorkManager daily |
| task (completed/failed) | 30 hari setelah complete | Yes | WorkManager daily |
| agent_step | CASCADE dari task | (inherited) | Auto via FK |
| memory_record | Permanent (LRU evict >5000) | Per-record TTL | WorkManager weekly |
| command_history | 90 hari | Yes | WorkManager daily |
| model_config | Per-row TTL atau permanent | No | Per-row |

### Erase

User-demand action di Settings → Privacy → Erase All:

```kotlin
suspend fun eraseAllData() {
    // 1. Log the erase action (final audit before wipe)
    auditLogger.log(
        actionType = AuditActionType.ERASE_DATA,
        dataSummary = "User requested full data erase",
    )
    
    // 2. Clear Room
    db.clearAllTables()
    
    // 3. Clear EncryptedSharedPreferences
    securePrefs.clearAll()
    
    // 4. Revoke cloud sessions
    claudeWebAdapter.revokeSession()
    gptWebAdapter.revokeSession()
    geminiFreeAdapter.clearApiKey()
    
    // 5. ElevenLabs voice ID: retain (user property)
    
    // 6. Reset consent state
    consentRepo.reset()
    
    // 7. Restart ChibiService
    serviceManager.restart()
}
```

Confirmation modal dengan double-tap untuk hindari accidental.

### Export

User-demand action di Settings → Privacy → Export:

```kotlin
suspend fun exportAllData(): File {
    val export = DataExport(
        version = "1.0",
        exportedAt = Instant.now(),
        userProfile = memoryRepo.listByCategory(MemoryCategory.USER_PROFILE),
        contacts = memoryRepo.listByCategory(MemoryCategory.CONTACT),
        habits = memoryRepo.listByCategory(MemoryCategory.HABIT),
        facts = memoryRepo.listByCategory(MemoryCategory.FACT),
        preferences = memoryRepo.listByCategory(MemoryCategory.PREFERENCE),
        tasks = taskRepo.listAll(limit = null),
        agentSteps = stepRepo.listAll(limit = null),
        auditLog = auditRepo.listAll(limit = null),
        standingInstructions = instructionRepo.listAll(),
    )
    
    val json = Json.encodeToString(export)
    val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), 
                    "chibiclaw_export_${Instant.now()}.json")
    file.writeText(json)
    
    auditLogger.log(
        actionType = AuditActionType.EXPORT_DATA,
        dataSummary = "Full data export to ${file.name}",
    )
    
    return file
}
```

Output file structured JSON, optional zip kalau besar.

---

## Cloud Transit Disclosure

Saat LLM call cloud, tampilkan indicator real-time:

- Overlay bubble: badge "↑ Gemini" muncul 2 detik saat call
- Notification kalau dalam STANDING mode (background)
- Audit log entry per call

User toggle "show cloud activity" di settings. Default ON.

---

## Voice Biometric Handling

Voice clone Fuu di ElevenLabs:
- `voice_id` reference (string, bukan audio mentah) → stored encrypted
- Audio output stream dari ElevenLabs ke device → tidak persistent (cuma play, lalu discard)
- User voice input (mic) → transcribed langsung, audio buffer 4s window, lalu free

User voice clone (kalau user bikin clone dari suaranya sendiri di ElevenLabs):
- Lendra punya control di ElevenLabs side
- ChibiClaw cuma pakai voice_id, tidak access voice clone training data

UU PDP Pasal 4(2): biometric data spesifik. Karena ChibiClaw tidak simpan biometric raw, exposure rendah.

---

## Disclosure untuk Reverse-Engineered Cloud

Setup wizard step Claude/ChatGPT login:

```
⚠️ Sebelum lanjut

Kamu akan login Claude.ai / ChatGPT pakai akun subscription kamu via 
WebView. Setelah login, ChibiClaw akan extract session token + cookies 
dan simpan terenkripsi di HP.

RISIKO:
• Cara akses ini BERPOTENSI melanggar Terms of Service Anthropic / 
  OpenAI / Google
• Akun kamu bisa di-suspend kalau detect bot-like usage
• Session bisa expire mendadak — perlu re-login periodik

ALTERNATIF AMAN: Pakai Gemini API free tier saja (resmi gratis, 1500 
req/day). Skip step ini untuk fitur cloud limited.

Kamu sadar dan setuju lanjut?
                                       [Skip & pakai Gemini]  [Lanjut]
```

User explicit consent, audit log entry.

---

## EU AI Act (Aug 2026 efektif) — Antisipasi

Walaupun ChibiClaw personal use, kalau nanti productize:

1. **Article 50 transparency**: User harus tahu mereka interact dengan AI. Voice persona Fuu mencantumkan "AI Assistant" di intro.
2. **Watermarking AI-generated content**: TTS output Fuu otomatis di-tag "AI-generated voice" di metadata (kalau export audio).
3. **High-risk AI list**: ChibiClaw general productivity assistant — likely NOT high-risk. Tapi cek lagi saat publish.
4. **Data residency**: kalau target EU user, harus regulasi GDPR + EU AI Act. ChibiClaw saat ini target Indonesia, tidak masalah.

---

## Compliance Roadmap by Phase

| Phase | Compliance Item |
|-------|----------------|
| 0 | Privacy notice + first consent + encryption setup (SQLCipher + EncryptedSharedPreferences) |
| 1 | Audit log infrastructure |
| 3 | Per-permission consent flow di setup wizard (a11y, shizuku) |
| 4 | Cloud disclosure + opt-in toggle |
| 5 | Vision/screen capture consent + audit |
| 6 | Standing instruction transparency (notif "running automation rule X") |
| 7 | Memory export / erase UI |
| 9 | Final compliance review + UX polish |

---

## Open Questions (kalau nanti productize)

1. PSE Privat registration (Kominfo Permenkominfo 5/2020) — wajib kalau publish.
2. DPO designation (UU PDP Pasal 53, post-MK ruling) — threshold based on data spesifik handling.
3. Cross-border SCC (UU PDP Pasal 56) — untuk cloud transit, butuh contract dengan vendor cloud.
4. ISO/IEC 42001 AI management system certification — relevant kalau scale up.
5. Anthropic / OpenAI commercial license — kalau abandon reverse-engineered, switch ke API key billing.

Detail kompleks di [docs/research/11-pdp-id-ai-compliance.md](../research/11-pdp-id-ai-compliance.md).

---

## Next Files

- Phase roadmap: [20-phase-roadmap.md](20-phase-roadmap.md)
