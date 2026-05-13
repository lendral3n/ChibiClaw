# 06 — Risk Register

**Audience**: PM, Engineering Lead, Founder
**Last updated**: 2026-05-10

---

## Reading This Document

Setiap risk punya:
- **ID**: untuk tracking
- **Category**: Technical / Product / Market / Schedule / Operational / Legal
- **Probability**: Low (<30%) / Medium (30-60%) / High (>60%)
- **Impact**: Low / Medium / High / Critical
- **Score**: P × I (1-16, higher = more attention)
- **Mitigation**: action plan jika risk materializes
- **Trigger**: signal yang menunjukkan risk lagi terjadi
- **Owner**: siapa watching

Score interpretation:
- **1-4**: Accept, monitor passively
- **5-8**: Active mitigation plan
- **9-12**: Proactive mitigation, reassess monthly
- **13-16**: Showstopper, block phase progression

---

## Technical Risks

### TR-01: LiteRT-LM v0.10.0 parser bug tidak fix

**Category**: Technical
**Probability**: High (kamu sudah experience)
**Impact**: High (tool calling unreliable = produk broken)
**Score**: 12 → **PROACTIVE**

**Description**: LiteRT-LM v0.10.0 sometimes emits raw `<|tool_call|>` tokens instead of structured JSON, requiring 3-attempt retry workaround. Issue not yet fixed by Google; release cadence ~3 months.

**Mitigation**:
1. Phase 1 Week 1 spike: trial MediaPipe LLM Inference (more stable wrapper). Switch if better.
2. Pin LiteRT-LM version. Don't auto-update.
3. Cloud adapters as escape hatch — kalau Gemma local broken, user pakai Claude/GPT.
4. Track upstream issue tracker, contribute fix kalau bisa.

**Trigger**:
- Parser bug rate >30% di 100 sample commands
- New LiteRT release breaks compatibility

**Owner**: Lendra (engineering)

---

### TR-02: Accessibility Service deprecated atau heavily restricted di Android 16

**Category**: Technical
**Probability**: Medium (Google sudah signal restrict accessibility for non-disabled-user apps)
**Impact**: Critical (50% capability ChibiClaw bergantung accessibility)
**Score**: 9 → **PROACTIVE**

**Description**: Google increasingly restrict Accessibility Service usage for "automation" purposes. Android 14 sudah introduce app store policy review. Future Android version mungkin block API entirely untuk apps yang bukan accessibility tools genuine.

**Mitigation**:
1. **Vision-first execution sebagai primary path** untuk app yang sensitif. Accessibility cuma fast path.
2. Shizuku-based shell command sebagai third option (uses ADB-level permission, not Accessibility).
3. Position ChibiClaw sebagai "accessibility tool" genuinely (target hands-free, lansia, disabled — bukan general automation). Aligns dengan Google's intent.
4. Monitor Android release notes setiap version.

**Trigger**:
- Google rilis Android 16 dengan restricted Accessibility API
- Play Store reject ChibiClaw karena "automation app"

**Owner**: Lendra

---

### TR-03: Cloud LLM API breaking changes

**Category**: Technical
**Probability**: Medium (vendor frequently change API, especially Anthropic + OpenAI)
**Impact**: High (cloud adapters break = user can't use them)
**Score**: 6 → **ACTIVE**

**Description**: Anthropic Messages API, OpenAI Chat Completions API, Google Gemini API can change. Tool calling format especially fragile.

**Mitigation**:
1. Adapter pattern isolates breaking changes per adapter.
2. Pin SDK / API version. Test before bumping.
3. Integration tests against staging mock servers (Wiremock or similar).
4. Subscribe to vendor changelog / RSS.

**Trigger**:
- API request returns 4xx/5xx persistently
- Tool call format changes silently

**Owner**: Lendra

---

### TR-04: Gemma 4 model file size + download abandonment

**Category**: Technical / Product
**Probability**: High
**Impact**: Medium (drop-off rate)
**Score**: 9 → **PROACTIVE**

**Description**: Gemma 4 4B = 4GB. Indonesia mobile data expensive (avg Rp 50/MB on prepaid). Download 4GB = ~Rp 200,000 in data. User abandon onboarding.

**Mitigation**:
1. Default offer Gemma 4 E2B (1.6GB) untuk first download. E4B opt-in.
2. Resume-able download (don't restart from scratch on connection loss).
3. WiFi-only download default (don't burn user's mobile data without ask).
4. Show download progress + estimated cost ("Akan menggunakan ~1.6GB dari kuota internet kamu. Lanjut?").
5. Consider hosting model in Indonesian CDN untuk speed (CDN partnership? deferred).

**Trigger**:
- Onboarding completion rate <50%
- Download abandonment >40%

**Owner**: Lendra

---

### TR-05: Wake word false positive rate too high

**Category**: Technical
**Probability**: Medium
**Impact**: Medium (annoying user, battery drain)
**Score**: 6 → **ACTIVE**

**Description**: openWakeWord custom train "Hey Fuu" might fire on similar phrases ("hey food", "okay fuu", etc.) atau random TV/radio sound.

**Mitigation**:
1. Train with diverse negative samples (TV ambient, music, English speech).
2. Confidence threshold tunable. Default 0.7, expose ke Settings.
3. Two-stage detection: wake word + verification (re-listen 200ms to confirm).
4. User feedback loop: button "wrong wake" yang collect false positive samples untuk retrain.

**Trigger**:
- User reports wake word fires when not intended
- Battery drain >8%/hour idle

**Owner**: Lendra

---

### TR-06: Voice latency >5s offline

**Category**: Technical
**Probability**: Medium
**Impact**: High (user perception "lambat")
**Score**: 6 → **ACTIVE**

**Description**: End-to-end voice flow (wake → STT → infer → TTS) butuh <5s offline. Stack:
- Whisper.cpp small INT8: ~2-4s di mid-range
- Gemma 4 4B inference: ~3-5s
- Piper TTS: ~0.8s
Total: 6-10s tipikal. Above target.

**Mitigation**:
1. **Streaming TTS**: synthesize chunk per Gemma response chunk, mulai TTS sebelum response complete. Mengurangi perceived latency 30-50%.
2. **Whisper smaller model**: tiny.en quantized (75MB), ~1s STT, lower accuracy. Trade-off di Settings.
3. **Cloud fallback ke STT API** (Whisper API <1s, Deepgram <500ms). Hybrid: offline default, cloud opt-in.
4. **Gemma E2B (2B parameter)**: 2× faster inference, slightly lower quality. Acceptable for simple commands.

**Trigger**:
- p50 latency >6s offline measured at end of Phase 2

**Owner**: Lendra

---

### TR-07: Coroutine cancellation tidak proper untuk side-effect tools

**Category**: Technical
**Probability**: Medium (sudah identified di v3)
**Impact**: High (e.g., SMS terlanjur kirim setelah user stop)
**Score**: 6 → **ACTIVE**

**Description**: Kill switch hanya boolean flag check di collect. Kalau tool already dispatch ke OS (SmsManager.send, Intent.startActivity), action sudah committed.

**Mitigation**:
1. Pre-flight checks di tool (untuk HIGH-risk): explicit confirm, then atomic execute.
2. Cancellable operations only: long-running tools (vision analyze, file zip) implement coroutine cancellation properly.
3. Post-action rollback: kalau possible (e.g., delete created file).
4. UX honesty: "Action sudah dimulai, tidak bisa di-stop di tengah" untuk tools yang non-cancellable.

**Trigger**:
- User reports "kenapa SMS tetap terkirim setelah saya stop?"

**Owner**: Lendra

---

### TR-08: Memory leak di voice continuous listening

**Category**: Technical
**Probability**: Low-Medium
**Impact**: High (OOM crash)
**Score**: 6 → **ACTIVE**

**Description**: VoiceLayer streaming audio capture continuously. Audio buffer + VAD context easily leak memory kalau Flow tidak properly cancelled.

**Mitigation**:
1. Use cold Flow di STT/VAD; hot reuse buffer pool.
2. Profile dengan Android Profiler weekly.
3. Stress test: 24-hour idle wake word session, monitor RAM.

**Trigger**:
- RSS memory grow >50MB/hour idle
- OOM in beta test

**Owner**: Lendra

---

## Product Risks

### PR-01: Niche tidak validate, general-purpose lose

**Category**: Product / Market
**Probability**: Medium-High
**Impact**: Critical (no users, project dies)
**Score**: 12 → **PROACTIVE**

**Description**: ChibiClaw scope = "general assistant Indonesia". Google Assistant own this market. Without specific niche, no compelling reason to switch.

**Mitigation**:
1. **Validation spike (1 minggu)** sebelum Phase 2 commit: identifikasi 5-10 user dari satu niche specific (saran: hands-free for drivers, atau accessibility for disabled, atau power users), demo, observe.
2. Pivot scope kalau validation negative.
3. Even kalau personal project, validate use case nyata buat diri sendiri ≥2 minggu daily use sebelum invest 3 bulan dev.

**Trigger**:
- Personal use ditch setelah 2 minggu (kamu tidak pakai)
- 5 demo user feedback "nice but won't switch from Google"

**Owner**: User decision

---

### PR-02: Privacy claim challenged (data leak)

**Category**: Product / Operational
**Probability**: Low (kalau implementasi correct)
**Impact**: Critical (trust hancur, project dies)
**Score**: 8 → **ACTIVE**

**Description**: Klaim "offline = zero data leave device". Kalau ada bug yang accidentally send to cloud, atau telemetry undocumented, kepercayaan user rusak.

**Mitigation**:
1. **Audit log mandatory**: setiap data leave device logged + visible to user.
2. **Network monitoring**: testing pre-release dengan packet sniffer (mitmproxy) verify NO traffic in offline mode.
3. **Open source claim** (kalau decide open source): trust through transparency.
4. **Privacy policy** clear, simple Bahasa Indonesia. Don't hide behind legal-ese.

**Trigger**:
- User report network usage in offline mode
- Security researcher flag

**Owner**: Lendra

---

### PR-03: Voice quality (TTS) terdengar robot, user kecewa

**Category**: Product
**Probability**: Medium (offline TTS quality limitation)
**Impact**: Medium-High (asisten tidak feel premium)
**Score**: 6 → **ACTIVE**

**Description**: Piper TTS Indonesian voice OK tapi tidak setara ElevenLabs / OpenAI TTS. User kebiasaan dengar Google Assistant yang fluent.

**Mitigation**:
1. Voice preview di onboarding — set expectation.
2. Premium voice opt-in (cloud) untuk user yang willing.
3. Curate Piper voice — mungkin community contribute better Indonesian voice.
4. Mengurangi TTS untuk simple confirms ("OK") dengan audio cue (chime) instead.

**Trigger**:
- Beta feedback: "suara Fuu tidak natural"

**Owner**: Lendra

---

### PR-04: User keracunan saran assistant (LLM hallucination)

**Category**: Product / Legal
**Probability**: Medium (LLM still hallucinate)
**Impact**: High (kalau user ikuti hallucinated medical/legal advice)
**Score**: 6 → **ACTIVE**

**Description**: User tanya "Hey Fuu, obat sakit kepala saya ibuprofen 1000mg dosis aman ya?" → Gemma jawab confident "iya aman". Sebenarnya 1000mg sekali minum berbahaya.

**Mitigation**:
1. **System prompt safety addendum**: Fuu sebut "Saya bukan dokter / pengacara / penasihat keuangan, konsultasi profesional untuk hal kritis."
2. Off-topic detection: kalau command bukan device control tapi advice question, redirect "Saya asisten kontrol HP, bukan AI advisor general. Coba tanya Google atau Tanya Dokter."
3. Disclaimer kalau dipublish: Terms of Service jelas exclude medical/legal/financial advice.

**Trigger**:
- User report incident
- Beta feedback flag kategori advice

**Owner**: Lendra + (kalau publish) legal review

---

## Market Risks

### MR-01: Anthropic / Google rilis mobile assistant yang competitive

**Category**: Market
**Probability**: High (12-18 bulan timeline)
**Impact**: High (moat dari ChibiClaw shrink)
**Score**: 9 → **PROACTIVE**

**Description**: Cowork is desktop. Mobile version inevitable. Google Assistant terus improve. Once they ship voice-first agent mobile dengan Gemini, ChibiClaw competitive position weak.

**Mitigation**:
1. **Move fast**: ship v4 stable dalam 4 bulan. Capture mobile-first window.
2. **Differentiator unik**: bahasa Indonesia native + offline + privacy. Vendor besar slow on these.
3. **Niche focus**: kalau pivot ke hands-free / accessibility, dominant in niche aman dari general-purpose competition.

**Trigger**:
- Anthropic announce mobile product
- Google rilis Gemini-powered Assistant agentic version

**Owner**: User strategic decision

---

### MR-02: Permission policy tightening di Play Store

**Category**: Market / Legal
**Probability**: High
**Impact**: High (kalau publish ke Play Store)
**Score**: 9 → **PROACTIVE**

**Description**: Google Play increasingly restrict apps with broad permissions (Accessibility, SYSTEM_ALERT_WINDOW, RECORD_AUDIO continuous). ChibiClaw checks all boxes.

**Mitigation**:
1. **Sideload first** (GitHub Releases) untuk avoid Play Store policy. Loyal user accept.
2. **Justification doc** ready untuk Play Console review (kalau eventual publish): explain accessibility for genuine use case (hands-free, disabled, lansia).
3. **Minimum permissions**: review permission list, remove unused.
4. **F-Droid alternative**: open source community store, less restrictive.

**Trigger**:
- Play Store reject submission
- Policy change announcement

**Owner**: Lendra (kalau publish)

---

## Schedule Risks

### SR-01: Solo dev capacity overestimate

**Category**: Schedule
**Probability**: High
**Impact**: Medium (delay stable, no user impact selain delay)
**Score**: 8 → **ACTIVE**

**Description**: 320 hours over 16 weeks = 20h/week. Solo developer with day job rarely sustained 20h/week part-time.

**Mitigation**:
1. **Realistic timeline**: kalau weekend warrior (10h/week), double schedule ke 32 weeks (8 bulan).
2. **Phase gates honest**: jangan rush ke phase berikut kalau current incomplete.
3. **Scope reduction**: drop tertiary goals (cross-platform readiness, plugin SDK) di v4. Defer ke v4.5+.
4. **No sprint heroics**: 4 bulan sustained beat 2 bulan crunch.

**Trigger**:
- End of Phase 1 (Week 4): >7 days behind
- Burnout signs (skipped daily standup 3+ days)

**Owner**: Lendra

---

### SR-02: Spike outcomes negative

**Category**: Schedule / Technical
**Probability**: Medium
**Impact**: Medium (rework needed)
**Score**: 6 → **ACTIVE**

**Description**: Phase 1 Week 1 spike (MediaPipe vs LiteRT) might find both broken. Phase 3 Week 9 spike (multilingual-e5 vs MiniLM) might show neither acceptable.

**Mitigation**:
1. Spike timebox: 5 days max, decision criteria documented up front.
2. Plan B per spike: kalau both options fail, fallback to "stick with current" dengan known limitations.
3. Document spike outcome di `docs/v4/spikes/` for future reference.

**Trigger**:
- Spike day 4 still inconclusive

**Owner**: Lendra

---

### SR-03: Beta tester recruitment fail

**Category**: Schedule
**Probability**: Medium
**Impact**: Low-Medium (delay validation, but not block stable)
**Score**: 4 → **MONITOR**

**Description**: Recruit 5-10 beta tester sulit kalau personal network terbatas.

**Mitigation**:
1. Personal network (keluarga, teman) sufficient untuk minimum signal.
2. Discord communities Android/AI/Indonesia developer relevant — post di sana.
3. Hacker News / Reddit r/Android (kalau publish), but expect non-Indonesia user feedback.

**Trigger**:
- 1 week sebelum beta target (Week 12), recruitment <3 testers

**Owner**: Lendra

---

## Operational Risks

### OR-01: Cloud cost runaway (kalau ChibiClaw publish + free tier abuse)

**Category**: Operational
**Probability**: Low (BYOK model = user pays)
**Impact**: Medium
**Score**: 4 → **MONITOR**

**Description**: Kalau ChibiClaw publish + free tier dengan ChibiClaw-paid cloud, user abuse bisa drain budget.

**Mitigation**:
1. **BYOK only**: user input own API key. ChibiClaw never proxy / never paid by ChibiClaw.
2. Kalau eventual offer hosted tier, hard rate limit per user.

**Trigger**:
- Decide to offer hosted tier (currently no plan)

**Owner**: User strategic

---

### OR-02: Breaking change ke ChibiClaw v3 user (kalau ada)

**Category**: Operational
**Probability**: High (DB destructive migration)
**Impact**: Low (assuming few v3 user)
**Score**: 3 → **ACCEPT**

**Description**: v3 → v4 migration drop semua data. v3 users kehilangan command history, settings.

**Mitigation**:
1. Release notes clear (existing).
2. Export tool di v3 (defer kalau ada user).

**Trigger**: Real user complaint.

**Owner**: Lendra

---

## Legal Risks

### LR-01: Data residency compliance (kalau cloud user)

**Category**: Legal / Privacy
**Probability**: Low (personal use), High (kalau B2B Indonesia)
**Impact**: Medium-High (legal action UU PDP / GDPR)
**Score**: 6 → **ACTIVE**

**Description**: Indonesia UU PDP (Pelindungan Data Pribadi) effective 2024. Cloud LLM (Anthropic, OpenAI di US) berarti data leave Indonesia. Untuk B2B / enterprise user, ini compliance issue.

**Mitigation**:
1. **Default offline**: data stays in Indonesia. UU PDP compliant.
2. **Cloud opt-in dengan explicit notice**: user persetujuan eksplisit data ke US.
3. **Audit log mandatory**: bukti data leave dokumented per UU PDP requirement.
4. **Defer enterprise B2B**: kalau eventual go enterprise, get legal review (deferred to v5+).

**Trigger**:
- B2B inquiry
- UU PDP enforcement update

**Owner**: User strategic + (kalau B2B) legal counsel

---

### LR-02: Trademark / IP issue dengan "ChibiClaw" / "Fuu"

**Category**: Legal
**Probability**: Low
**Impact**: Medium
**Score**: 3 → **ACCEPT**

**Description**: Nama "ChibiClaw" atau "Fuu" mungkin overlap dengan trademark eksisting (anime/game character).

**Mitigation**:
1. Kalau publish, search USPTO + WIPO + DJKI Indonesia trademark database.
2. Backup name siap kalau conflict.

**Trigger**: Cease-and-desist letter.

**Owner**: User strategic (kalau publish)

---

## Risk Summary Matrix

| Score Range | Count | Risks (top by score) |
|-------------|-------|---------------------|
| 13-16 (Critical) | 0 | (none) |
| 9-12 (Proactive) | 5 | TR-01, TR-02, TR-04, PR-01, MR-01, MR-02 |
| 5-8 (Active) | 9 | TR-03, TR-05, TR-06, TR-07, TR-08, PR-02, PR-03, PR-04, SR-01, SR-02, LR-01 |
| 1-4 (Monitor) | 4 | SR-03, OR-01, OR-02, LR-02 |

**Top 3 risks to watch closely:**
1. **TR-01** LiteRT-LM parser bug — addressed by Phase 1 spike
2. **TR-02** Accessibility deprecation — addressed by vision-first design
3. **PR-01** Niche validation — needs user decision pre-Phase 1

---

## Risk Review Cadence

- **Weekly**: triage new risks, update existing scores
- **End of phase**: full register review, decide go/no-go
- **Pre-stable**: comprehensive review before ship

---

## Lessons Learned (post-mortem accumulator)

(Populated as project progresses)

| Date | Lesson | Action taken |
|------|--------|--------------|
| 2026-05-09 | v3 architecture rebuild scope creep — refactor too eager without user explicit approval | Switched to plan-mode workflow with explicit approval gate |

---

**Next**: [07-cost-analysis.md](07-cost-analysis.md) — Money breakdown.
