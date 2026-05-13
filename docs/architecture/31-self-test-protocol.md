# 31 — Self-Test Protocol

Test strategy ChibiClaw v4. Per ADR-011: skip automated test selama Phase 0-8, intensive manual test di Phase 9 (2 minggu dedicated).

---

## Filosofi Test

- **Personal project, single developer + Claude Code** — bukan industry-grade QA.
- **Manual test sebagai gating** — Phase 9 minimum 1 minggu daily use sebelum claim "stable".
- **AuditLogger sebagai trace** — semua action terlog, bug investigation via audit log + agent step trace.
- **No regression test automation** — kalau bug fix, manual re-test scenario related.

Trade-off jujur: cepat develop, risk regression saat refactor. Mitigasi: AuditLogger lengkap + agent step trace mendetail.

---

## Test Categories

### 1. Smoke Test (per Phase, opsional)

Quick sanity check setelah phase done:

| Phase | Smoke test |
|-------|------------|
| 0 | App install, bubble visible, no crash 5 menit |
| 1 | Text "buka senter" via chat → flashlight on |
| 2 | Voice "halo Fuu" → Fuu speak response |
| 3 | Text "force-stop YouTube" → confirmation overlay → approve → YouTube killed |
| 4 | Task complex → LLM emit escalate_to_cloud → Gemini response |
| 5 | "Balas WA Budi 'OK'" → vision fallback success |
| 6 | Create standing "Setiap jam 7 baca berita", set jam ke +1min, observe fire |
| 7 | Memory test: "Aku suka kopi" → "apa yang aku suka?" → "kopi" |
| 8 | 2 task paralel (background + foreground) sukses |
| 9 | Full daily use intensive |

Skip kalau Lendra terlalu sibuk, langsung Phase 9 mass test.

### 2. Intensive Self-Test (Phase 9)

**Durasi:** 1-2 minggu daily use minimum.

**Setup:**
- Install APK v4.0 di Xiaomi 17 Pro Max
- Pakai sebagai daily AI assistant
- Log harian di `docs/self-test-log.md`

**Daily routine target:**
- 1+ voice command pagi (mis. "baca agenda hari ini")
- 1+ text command kapan saja (mis. "kirim WA ke ...")
- 1+ standing instruction fire (background)
- Random task: explore feature, find edge case

**Metrics tracked daily:**
- Task success / fail count
- Crashes (ChibiService died unexpected)
- Latency observation per task type
- Battery drain estimate
- UX surprise (unexpected behavior)
- Bug encountered

---

## Test Scenarios per Feature

### Voice + Emotion

1. **Happy path**: tap mic, "halo Fuu" → response speak natural Fuu voice
2. **Emotion contextual**: bilang sad voice "Fuu, aku lagi capek" → Fuu lebih lembut tone
3. **Long utterance**: "Tolong cariin tiket KAI besok pagi Jakarta-Bandung yang paling murah" → STT akurat + agent execute
4. **Interruption**: speak ke Fuu, lalu cancel/dismiss bubble mid-conversation → audio stop
5. **Background noise**: speak di tempat ramai → STT toleransi
6. **Bahasa code-switch**: "Fuu, send email to my boss saying I'm sick" → mix EN/ID handled

### Mobile Control

1. **Intent open**: "buka Spotify" → Spotify launch
2. **Intent + sub-action**: "buka Spotify lalu mainkan Lo-Fi playlist" → multi-step
3. **A11y click app accessibility-friendly**: "klik Settings di Spotify" → success
4. **A11y fail → vision fallback**: "balas WA Budi 'OK'" → a11y fail TikTok-style → vision success
5. **Shizuku exec**: "force-stop YouTube" → confirm → killed
6. **Shizuku grant**: "grant CAMERA permission ke Tasker" → confirm → success
7. **Messaging SMS**: "kirim SMS ke +6281234... isi: test" → confirm → sent
8. **Messaging WA**: same via WhatsApp Intent

### Background + Standing Instructions

1. **Time trigger**: "Setiap jam 7 pagi, bacakan agenda hari ini" → set, wait jam 7, observe fire
2. **Event trigger**: "Kalau notif WA dari Mama, balas ✓" → simulate notif Mama → auto-reply
3. **Predicate trigger**: "Kalau baterai < 20% dan tidak ngecharge, ingatkan" → drain battery, observe fire
4. **Composite trigger**: "Antara 18-22 + tidak di kantor + notif WA Mama" → setup + test
5. **Cooldown enforced**: trigger fire, fire lagi dalam cooldown → skip
6. **Max fires per day**: trigger 11x kalau max=10 → ke-11 skip

### Memory

1. **Remember**: "Aku suka kopi" → memory_remember PREFERENCE
2. **Recall**: "apa yang aku suka minum?" → response "kopi"
3. **Update**: "aku lebih suka teh sekarang" → confidence kopi turun, teh masuk
4. **Forget**: "lupakan minuman favoritku" → memory_forget
5. **Cross-task persistent**: session 1 remember, session 2 (restart app) recall sukses
6. **Pattern miner**: pakai 1 minggu, miner suggest "habit morning kopi" → user approve

### Cloud Escalation

1. **Gemini free**: task butuh long reasoning → escalate Gemini → success
2. **Quota exhaust**: fire 1500 task ke Gemini → quota habis → fallback Claude
3. **Claude session valid**: WebView login fresh → call → response
4. **Session expired**: simulate cookie expired → notify user re-login
5. **Network down**: cloud unavailable → fallback Gemma local

### Vision

1. **Vision tap di TikTok**: "cari ikan di TikTok" → vision_tap search icon → success
2. **Vision describe**: "apa yang ada di layar?" → describe correct
3. **Vision extract text**: notification shade text extract
4. **MediaProjection persist**: ChibiService restart → projection token recovered

### Concurrency

1. **2 task paralel**: standing "auto-reply WA" + user voice "buka maps" → both run
2. **Mic conflict**: task A pakai mic, task B incoming voice → resolve dengan pause/resume
3. **Subtask decomp**: "summarize 3 articles" → 3 subtask paralel → consolidate

### Compliance

1. **Audit log redaction**: phone number, email di summary text — verify redacted
2. **Retention**: insert old audit entry (90+ days), run cleanup, verify deleted
3. **Export**: full data export → JSON valid + readable
4. **Erase**: full erase → reset to first-launch state
5. **Cloud disclosure**: badge/indicator muncul saat cloud LLM call

### Vendor Death (Xiaomi-specific)

1. **Battery saver kick**: 5 jam idle, ChibiService masih alive
2. **Autostart locked**: reboot HP, ChibiService restart
3. **App recent unlock**: clear recent → service masih survive (locked in recents)
4. **MIUI security setting**: USB debug + ADB pairing flow Shizuku setup works

---

## Performance Benchmark

Measure di Snapdragon 8 Elite Gen 5 (Xiaomi 17 Pro Max):

| Metric | Target | Critical Threshold |
|--------|--------|--------------------|
| Voice cycle p50 (utter → action) | <5s | <8s |
| Voice cycle p95 | <10s | <15s |
| Memory peak | <4GB | <6GB |
| Battery drain idle | <5%/hour | <8%/hour |
| Battery drain active (1 task/min) | <15%/hour | <25%/hour |
| ChibiService cold start | <30s | <60s |
| Crash rate | <1%/day | <5%/day |
| Standing fire latency | <30s | <2min |

Below critical threshold = fail, must fix before declare stable.

---

## Bug Severity & Priority

| Severity | Definition | Action |
|----------|------------|--------|
| **P0** | Crash / data loss / security | Fix segera, block phase ship |
| **P1** | Major feature broken | Fix before next phase |
| **P2** | Minor feature broken / edge case | Backlog, fix saat ada waktu |
| **P3** | UX issue / polish | Backlog Phase 9 atau nanti |
| **P4** | Feature request | Out of scope, log untuk future |

---

## Self-Test Log Template

```markdown
# ChibiClaw v4 Self-Test Log

## Day 1 (YYYY-MM-DD)

### Daily routine
- Morning: voice "baca agenda" (success, 4s)
- Afternoon: text "kirim WA ke Budi" (partial, vision needed, 8s)
- Evening: standing "auto-reply Mama" fired (success, silent)

### Tasks (total: 12)
- Success: 10
- Partial: 1 (vision_tap koordinat off by 20px, user retap manual)
- Fail: 1 (Claude session expired, user belum re-login)

### Bugs
- B1 [P2]: Vision_tap di Tokopedia search field off by ~20px (mungkin device scale issue)
- B2 [P3]: Bubble icon kadang tidak update warna setelah task complete

### UX issues
- U1: Setup wizard step 5 (Shizuku) instruksi kurang jelas, perlu screenshot

### Performance
- Voice cycle avg: 5.2s (target <5s, borderline)
- Battery drain 8 jam: 18% (within target)

### Feature requests
- F1: Quick action "set timer 5 menit" tanpa konfirmasi (HIGH severity unnecessary)

## Day 2 ...
```

---

## Sign-off Criteria for v4.0 Stable

Phase 9 final, declare v4.0 stable kalau:

- [ ] All P0 bugs fixed
- [ ] No P1 bugs in critical path (voice cycle, standing, basic tools)
- [ ] Performance metrics within target (atau within critical threshold)
- [ ] 7 hari consecutive daily use tanpa crash blocker
- [ ] Compliance audit pass (export + erase + redaction)
- [ ] User (Lendra) feels confident pakai sehari-hari

Kalau ada P1 / borderline:
- Defer to Phase 9.1 patch release
- Document di "Known Issues" di final README

---

## Post-Stable: Monitoring

Setelah v4.0 stable, ongoing monitoring (lightweight):

- AuditLogger query weekly: cek error rate per tool
- Memory accuracy: spot-check kalau Fuu remember benar
- Latency drift: cek voice cycle p50 tetap <5s

Kalau muncul regression, hotfix targeted (jangan rewrite besar).

---

## Next iterations beyond v4.0

(Out of current scope, log untuk future)

- Wake word "Hey Fuu" custom training
- Voice cloning local (replace ElevenLabs)
- Multi-language native (Jepang, English)
- Productize (PSE registration, DPO, multi-user)
- iOS port
- VRM Assistant integration (Phase 10 bonus)

Tracked di handover doc kalau Lendra commit lanjut.

---

## End

ChibiClaw v4 — agent native, Fuu emotional, full akses HP.
Pakai sendiri, iterate berdasarkan real usage.
