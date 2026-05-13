# 08 — Success Metrics

**Audience**: PM, Founder, all stakeholders
**Last updated**: 2026-05-10

---

## North Star Metric

**Daily Voice Commands per Active User (DVC/DAU)**

Target end of Year 1: **≥10 voice commands per daily active user per day**

Why this metric:
- ChibiClaw goal = "menggantikan kita" — user pakai sering, bukan sekali setup lalu lupa
- Voice (bukan text) = primary interaction. DVC measures voice-first adoption.
- Per active user (bukan total) — real engagement, bukan vanity metric

---

## Tier 1 Metrics (Track from Day 1)

### Engagement

| Metric | Target Beta (Week 14) | Target Stable (3 month post-release) | Target Year 1 |
|--------|----------------------|---------------------------------------|---------------|
| Daily Active Users (DAU) | 5 (beta testers) | 50 | 500 |
| 7-day retention | 50% | 60% | 70% |
| 30-day retention | 30% | 40% | 50% |
| Sessions per DAU per day | 2 | 5 | 8 |
| Voice commands per session | 3 | 5 | 7 |
| **DVC/DAU** | **6** | **25** | **56** |

Catatan: nilai "stable" dan "year 1" hanya relevant kalau publish/distribute. Personal project: target diri sendiri ≥10 DVC/day konsisten 3 bulan = win.

### Quality

| Metric | Target | Measurement |
|--------|--------|-------------|
| Voice command success rate | ≥85% | "Did Fuu accomplish what user asked?" — manual review beta + automated heuristic |
| Crash-free session rate | ≥99% | Sentry / Crashlytics (kalau enabled) atau Play Console |
| App Not Responding (ANR) rate | <0.5% | Same |
| Wake word false positive | <5%/hour | User-reported "wrong wake" button |
| Wake word miss rate | <10% | User-reported "wake gak respond" |
| First command success (onboarding) | ≥80% | Funnel onboarding → first voice command works |

### Performance

| Metric | Offline Target | Cloud Target |
|--------|----------------|--------------|
| Voice command e2e latency (p50) | ≤5s | ≤3s |
| Voice command e2e latency (p95) | ≤10s | ≤6s |
| Wake word detection latency | <500ms | N/A |
| App memory peak | <4GB (mid-range) | N/A |
| Battery drain idle (wake word) | <5%/hour | N/A |
| Battery drain active session | <15%/hour | <10%/hour |

---

## Tier 2 Metrics (Track from Phase 2)

### Privacy + Trust

| Metric | Target | Measurement |
|--------|--------|-------------|
| Offline mode usage % | ≥50% | Privacy mode toggle telemetry (anonymized) |
| Audit log queries by user | ≥10% open audit log | UI navigation telemetry |
| User-reported privacy concern | <2 per 100 user/month | Support channel |

### Cloud Adapter Adoption

| Metric | Target |
|--------|--------|
| % users with at least 1 cloud adapter configured | 30-50% |
| Most-used cloud adapter | TBD (data-driven) |
| Avg monthly cloud cost per user | <$5 (untuk health) |

### Tool Usage Distribution

Track top-used tools to optimize prompt:
| Tool | Expected % of all calls |
|------|------------------------|
| `intent` (open app) | 30-40% |
| `system` (flashlight, volume) | 15-20% |
| `tap` / `type` (UI) | 15-20% |
| `messaging` | 10-15% |
| `query` | 5-10% |
| `meta` (askUser, report) | 5-10% |
| Others | <5% each |

Anomaly: kalau `vision_analyze` >20%, indicates accessibility-first failing too often → investigate.

---

## Tier 3 Metrics (Track from Stable)

### Net Promoter Score (NPS)

Survey beta + post-release: "Seberapa mungkin kamu rekomendasikan Fuu ke teman/keluarga? 0-10"
- Promoters (9-10): % - Detractors (0-6): % = NPS

| Phase | Target NPS |
|-------|-----------|
| Beta | ≥30 (early adopter bias) |
| Stable Year 1 | ≥40 |
| Stable Year 2 | ≥50 |

### Feature usage breadth

% users using at least N tools:
- 90% use ≥3 different tools
- 50% use ≥10 different tools
- 20% use ≥20 different tools (power users)

### Support burden

| Metric | Target |
|--------|--------|
| Support tickets per 100 DAU | <5/week |
| Time to first response (community channel) | <24 hours |
| Issue resolution time (P1) | <72 hours |

---

## Funnel Metrics

### Onboarding funnel

```
APK download                    100%
  ↓
Open app                         85%   (15% dropout: device incompat, broken APK)
  ↓
Setup completed                  70%   (download model, permissions)
  ↓
First voice command attempt      60%
  ↓
First voice command SUCCESS      55%  ← key conversion
  ↓
Day 2 return                     35%
  ↓
Day 7 return                     20%
  ↓
Day 30 return                    12%
```

Targets: improve each step. Biggest leak typically setup → first success.

### Cloud adapter funnel

```
Open Settings → Cloud           100%  (of those who explore)
  ↓
Choose provider                  50%
  ↓
Input API key                    30%
  ↓
Validate (ping endpoint)         28%   (2% leak: invalid key)
  ↓
Use cloud at least once          20%   (8% leak: never tried after setup)
  ↓
Use cloud regularly (>5/week)    10%
```

### Retention curve

Month 0 → Month 1 → Month 3 → Month 6 → Year 1
100%   → 50%      → 30%      → 20%      → 15%

Target: bend the curve. Industry-typical assistant app retention <10% Month 6 → ChibiClaw goal beat with privacy + native ID.

---

## Anti-Metrics (DON'T optimize for these)

| Metric | Why avoid |
|--------|-----------|
| Total commands ever | Vanity. Same user spam = inflated. |
| Total downloads | Doesn't measure use. Could be installer bot. |
| Lines of code | Negative signal. Smaller = better usually. |
| Stars on GitHub | Vanity unless community-driven dev. |
| Time spent in app | Bad: less time = better (Fuu efficient). |

---

## Measurement Infrastructure

### What instrumented (Phase 1+)

#### Local-only (no telemetry)
Setiap user device punya:
- Daily active counter (saved local)
- Command count per day
- Tool usage histogram
- Latency log (last 100 commands)
- Crash count (last 30 days)

User bisa:
- View own stats (Settings → Diagnostics)
- Export own data (CSV, anonymized)
- Delete own data anytime

#### Opt-in telemetry (Phase 3+)
**Strict opt-in, default OFF.**

Kalau user enable:
- Aggregated usage stats anonymized (hashed device ID)
- Crash reports
- Command success/fail (no command content)
- Latency p50/p95
- Privacy mode breakdown

**Never sent**:
- Command text content
- Tool result content
- API keys
- Personal identifiers (real name, contacts, location)

Stack: Sentry SDK self-hosted, atau PostHog self-hosted, atau Plausible Analytics (privacy-first).

### Manual measurement (beta + early stable)

- Weekly check-in survey (5 question Google Form)
- Discord/Telegram engagement metrics
- 1-on-1 user interview (60 min, monthly with 3-5 users)

---

## Decision Triggers

### Phase Gate criteria (cross-ref [04-implementation-roadmap.md](04-implementation-roadmap.md))

#### Phase 1 → Phase 2 (Week 4)
- ✅ Multi-adapter works (4 adapters)
- ✅ Build + integration tests PASS
- ✅ Cost meter accurate (cross-validate vs provider's billing)
- 🟡 If 1-2 adapters broken: lanjut tapi note sebagai known issue
- 🔴 If no cloud adapter works: pause, fix before Phase 2

#### Phase 2 → Phase 3 (Week 8)
- ✅ E2E voice latency p50 ≤5s offline
- ✅ Wake word false positive <8%/hour
- ✅ At least 5 voice commands work end-to-end
- 🔴 If latency >8s consistently: pause, optimize before Phase 3

#### Phase 3 → Beta (Week 12)
- ✅ Vision-first works untuk ≥3 of 6 blacklist apps
- ✅ Memory recall accuracy ≥80% di entity test set
- ✅ Manual test plan ≥85% PASS
- 🔴 If vision-first <50% success: defer feature, beta tanpa vision-first

#### Beta → Stable (Week 14)
- ✅ Crash-free ≥99%
- ✅ Average NPS ≥6
- ✅ ≤3 P1 bugs open
- 🔴 If P0 bug found: hold release until fix

### Year 1 Go/No-Go (Month 12)

**GO continue investing**:
- DAU ≥100
- 30-day retention ≥40%
- NPS ≥40
- User feedback: "saya tidak bisa kembali ke Google Assistant after using Fuu"

**KEEP MAINTAINING (no major investment)**:
- DAU 30-100
- Retention 20-40%
- Stable user base, not growing

**SUNSET (deprecate gracefully)**:
- DAU <30
- Retention <20%
- Heavy support burden

---

## Personal Project Metrics (kalau bukan untuk publish)

Kalau ChibiClaw v4 = personal use only, simplify drastically:

### Honest self-evaluation (3 month after stable)

1. **Apakah saya pakai ≥1× sehari?** Yes/No
2. **Apakah saya akan sedih kalau tidak ada?** Yes/No
3. **Apakah saya bisa kerja tanpa nya sekarang?** Yes/No
4. **Berapa kali per minggu saya say "wow Fuu pintar" vs "ah Fuu bodoh"?**
5. **Apakah saya pernah recommend ke teman tanpa diminta?**

Kalau jawab "Yes" pada Q1+Q2 dan rasio 3:1 wow:bodoh → personal sukses. Lanjut maintain.

Kalau Q1=No konsisten 1 bulan → product/market mismatch. Pertimbangkan pivot atau abandon.

---

## Reporting Cadence

- **Daily** (during dev): build status, test pass rate
- **Weekly** (Friday): retro, week deliverable, blockers
- **Monthly** (post-stable): metrics dashboard review, user feedback sintesis
- **Quarterly** (post-stable): strategic review, decide invest more atau maintain

---

**Next**: [09-end-state-vision.md](09-end-state-vision.md) — Output jadi (final state).
