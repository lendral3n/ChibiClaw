# 2A — Phase 9: Polish + Self-Test

**Durasi:** 2 minggu
**Tujuan:** Stable untuk pemakaian pribadi sehari-hari. Final integration UI v4 design + bug hardening + 1-2 minggu intensive daily use.

---

## Outcome

- Battery profiling + duty-cycle optimization
- Vendor death test Xiaomi 17 Pro Max + buddy device (kalau ada)
- Crash hardening + ANR mitigation
- Performance benchmark (latency p50/p95, memory peak, crash rate)
- Compliance audit (audit log review, retention test, export/erase)
- UI polish: integrate v4 design dari [docs/Design](../../Design) ke real ViewModel
- 1-2 minggu intensive daily use → bug fix iterative

**Test target:** Lendra pakai sehari-hari minimal 1x/hari, ≥80% task success rate, ≤5% battery drain/hour idle, no crash 24 jam.

---

## Deliverable per Minggu

### Minggu 1: Stability + performance

**M1.1: Battery profiling**
- Profile ChibiService idle baseline (no task, just FGS + tick)
- Profile per-feature: wake word (Phase 10+), mic listening, vision capture, LLM call
- Target: <5%/jam idle, <15%/jam active continuous
- Optimize: duty-cycle WorldObserver tick (lebih lambat saat idle), pause emotion detector saat tidak record

**M1.2: Vendor death test**
- Xiaomi 17 Pro Max: standard test pattern (12 jam idle, autostart locked, battery saver off, etc)
- Track ChibiService kill events
- Add restart strategy (JobScheduler retry + BroadcastReceiver)
- Document per-vendor quirks

**M1.3: Crash hardening**
- Run app 24-48 jam continuous
- Logcat collection
- Fix exception path: tool dispatcher, adapter call, parser fallback
- ANR monitor (StrictMode, ANRWatchdog lib optional)

**M1.4: Performance benchmark**
- Manual timer per critical path
- Voice cycle (utterance → action → response speak): target p50 <5s, p95 <10s
- Memory peak Snapdragon 8 Elite Gen 5: target <4GB peak (Gemma + Whisper + MiniCPM-V + Compose UI)
- Cold start ChibiService: <30s acceptable (Gemma load slow)

### Minggu 2: UI polish + compliance + self-test

**M2.1: UI integration v4 design**
- Reference: [docs/Design/](../../Design/) (React prototype)
- Restore atau re-implement components dari stash `v4-rewrite-archive-2026-05-13`:
  - CCBlob, CCStateOrb, CCPill, CCStateChip, CCSparkle, CCCard, CCAnimations
  - ChibiClawThemeV2, OKLCH tokens
  - Dashboard variants V1-V6 (pilih 1 default, sisanya selector di Settings)
- Wire ke ViewModel real (TaskManager, MemoryStore, StandingInstruction list)
- Polish overlay bubble + chat panel + status colors

**M2.2: Settings v4 polish**
- AI Engine settings (adapter status + cloud login)
- Voice settings (toggle Whisper offline/online, ElevenLabs voice ID test button)
- Memory inspector
- Standing instructions list + editor
- Privacy: consent management, audit log viewer, export/erase

**M2.3: Compliance audit**
- Review audit log: redaction works? PII protected?
- Retention test: 90 hari old log auto-cleaned
- Export test: full data export ke JSON
- Erase test: erase all → reset state ke first-launch

**M2.4: Self-test intensive 1-2 minggu**
- Lendra pakai sehari-hari minimal:
  - 1x voice command via overlay mic
  - 1x text command via chat panel
  - 1x trigger standing instruction (mis. "auto-reply WA" actually fire)
- Track in `docs/self-test-log.md`:
  - Task success/fail per day
  - Crashes encountered
  - UX issues / surprises
  - Feature requests / scope creep ideas

**M2.5: Final bug fix sprint**
- Top 10 bug dari self-test → fix
- Top 5 UX issue → polish
- README + handover docs update

---

## Modul Phase 9

Existing modules + UI integration. New files:

```
app/src/main/java/com/chibiclaw/ui/
├── cc/  (restored dari stash, atau re-implement)
│   ├── CCBlob.kt
│   ├── CCStateOrb.kt
│   ├── CCPill.kt
│   ├── CCStateChip.kt
│   ├── CCSparkle.kt
│   ├── CCCard.kt
│   └── CCAnimations.kt
├── theme/
│   ├── ChibiClawThemeV2.kt
│   ├── ChibiTokens.kt
│   └── (lainnya dari stash)
├── dashboard/v4/
│   ├── DashboardHost.kt
│   ├── DashboardV1.kt  # default Soft Orb
│   └── ... (V2-V6 selectable)
└── settings/v4/
    └── (re-integrate dari stash)
```

---

## Self-Test Log Template

```markdown
# ChibiClaw v4 Self-Test Log

## Day 1 (YYYY-MM-DD)

### Tasks executed
- 09:15 — "buka senter" (voice) → success, 3s latency
- 14:30 — "kirim WA ke Budi 'meeting jam 3'" (voice) → success, 8s latency (via vision_tap)
- 19:00 — Standing "morning agenda" — fired but Fuu error (memory_recall returned nothing). Bug:  ___
- ...

### Bugs
- B1: Standing instruction "morning agenda" fail saat task_template render — `{{schedule}}` not resolved
- B2: Overlay bubble disappear setelah Xiaomi battery saver kick (perlu Lock in Recents lagi)
- ...

### UX issues
- U1: Voice latency >5s di first command after idle 1 hour (Gemma re-warmup needed)
- ...

### Feature requests
- F1: Quick action: "set timer 5 menit" tanpa konfirmasi
- ...

## Day 2 (YYYY-MM-DD)
...
```

---

## Risk

| Risk | Mitigasi |
|------|----------|
| Self-test reveal major bug | Time-box 1 minggu fix; defer non-critical |
| UI v4 polish under-scope | Default 1 dashboard variant; sisanya marked "experimental" |
| Vendor wizard tidak cukup | Add custom HyperOS specific guidance kalau Lendra di Xiaomi |
| Performance miss (latency, battery) | Profile specific bottleneck; defer optimization Phase 10+ kalau critical |
| Compliance gap | Document gap + plan productize remediation |

---

## Definition of Done

- [ ] Battery drain <5%/jam idle, <15%/jam active continuous
- [ ] ChibiService survive 24 jam continuous run no crash
- [ ] Voice cycle p50 <5s
- [ ] UI v4 design integrated, overlay + chat + settings polish
- [ ] Compliance audit pass (audit log, retention, export, erase)
- [ ] Self-test log 1 minggu minimum daily use
- [ ] Top 10 bug fixed
- [ ] APK v4.0 stable ready untuk pemakaian pribadi
- [ ] Handover docs final (file 30 updated)

---

## Next: [2B-phase-10-vrm.md](2B-phase-10-vrm.md) (bonus)
