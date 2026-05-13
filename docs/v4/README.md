# ChibiClaw v4 — Design Documentation

**Status**: Design Phase (Planning)
**Author**: Lendra
**Version**: v4.0-draft
**Last updated**: 2026-05-10

---

## Pivot Statement

ChibiClaw v3 adalah **text-chat agent dengan single offline model**. ChibiClaw v4 adalah **voice-first multi-model agent** yang bisa pakai cloud (Claude / GPT / Gemini) atau offline (Gemma) per kebutuhan, dengan execution path vision-first yang lebih robust terhadap accessibility-resistant apps.

## Reading Order

| # | Document | Untuk Pembaca | Time to Read |
|---|----------|---------------|--------------|
| 00 | [Vision & Goals](00-vision-and-goals.md) | Semua stakeholder | 5 min |
| 01 | [Design Paper](01-design-paper.md) | Semua stakeholder | 20 min |
| 02 | [Architecture](02-architecture.md) | Engineering | 30 min |
| 03 | [Tech Stack](03-tech-stack.md) | Engineering | 20 min |
| 04 | [Implementation Roadmap](04-implementation-roadmap.md) | Engineering, PM | 25 min |
| 05 | [Testing Strategy](05-testing-strategy.md) | Engineering, QA | 15 min |
| 06 | [Risk Register](06-risk-register.md) | PM, Eng Lead | 10 min |
| 07 | [Cost Analysis](07-cost-analysis.md) | PM, Founder | 10 min |
| 08 | [Success Metrics](08-success-metrics.md) | PM, Founder | 10 min |
| 09 | [End-State Vision](09-end-state-vision.md) | Semua stakeholder | 15 min |

**Total reading time**: ~2.5 hours untuk full deep dive. **Quick scan**: baca 00 + 09 (~20 min).

## Decision Log

| Date | Decision | Rationale |
|------|----------|-----------|
| 2026-05-10 | Pivot ChibiClaw v3 → v4 (multi-model + voice + vision-first) | v3 architecture sudah bersih tapi tidak voice-native; competitor (Cowork, Mobile-Agent) confirm market direction |
| 2026-05-10 | Skip fork Mobile-Agent | Bahasa Indonesia weak, Python-on-Android friction, kontrol arsitektur penting |
| 2026-05-10 | Multi-model adapter pattern over single-model | Future-proof: tidak lock ke vendor; user privacy choice; cost flexibility |
| 2026-05-10 | Vision-first untuk app blacklist (TikTok/WA/Tokped/Shopee/IG) | Anti-accessibility detection bypass; accessibility tree breakage saat app update |
| 2026-05-10 | **Stay personal project** (Q1) | User clear: focus personal use awal, jangan pivot ke product company dulu |
| 2026-05-10 | **BYOK API key** untuk cloud adapter (Q2) | OAuth subscription untuk LLM consumer tidak ada di pasar; pivot ke product company tidak diinginkan saat ini; BYOK = user control + zero infrastructure ChibiClaw |
| 2026-05-10 | **Pragmatic privacy** untuk audio (Q3) | Default Whisper local STT, opt-in cloud STT; balance privacy + quality |
| 2026-05-10 | **Prioritas kualitas, latency tidak prioritas** (Q4) | Pakai model paling kuat (Claude Opus, Gemma 4 4B full); skip latency optimizations di Phase 2 |

## Open Questions

✅ **All critical questions answered.** Lihat [00-vision-and-goals.md § Open Questions](00-vision-and-goals.md) untuk detail jawaban dan implication.

## Current Phase

```
[✓] Design Documentation (this folder)
[✓] Open Questions Answered (Q1-Q4 user decided 2026-05-10)
[ ] Phase 0: Validate v3 di device fisik (manual test 5 critical journey)
[ ] Phase 1 Implementation (Week 1-4) — Multi-adapter foundation
[ ] Phase 2 Implementation (Week 5-8) — Voice layer
[ ] Phase 3 Implementation (Week 9-12) — Vision-first + memory
[ ] Beta Test (Week 13-14)
[ ] Stable Release (Week 15-16)
```

## Quick Status: ChibiClaw v3 → v4 Diff

| Capability | v3 (Current) | v4 (Target) |
|------------|--------------|-------------|
| Inference engine | LiteRT-LM (Gemma 4 only) | Multi-adapter: Gemma local, GPT, Claude, Gemini |
| Input modality | Text chat | Voice-first + text fallback |
| Output modality | Text streaming | Voice (TTS) + text |
| UI automation | Accessibility-first | Vision-first + accessibility fallback |
| Wake word | Tidak ada | "Hey Fuu" custom wake word |
| Long-term memory | Hanya 5 last commands | Semantic vector store (rebuild) |
| Privacy mode | Always offline | Per-session toggle (offline/cloud) |
| Tool count | 27 tools | 10 primitives (consolidated) |
| LOC | ~17,500 | ~22,000 (estimated +25%) |

## Authoritative Sources Referenced

- ChibiClaw v3 codebase: `/Users/lendra/Documents/codeV/ChibiClaw`
- ChibiClaw v3 design docs (this folder, files 01-11)
- Anthropic Claude Cowork docs: https://www.anthropic.com/product/claude-cowork
- Mobile-Agent paper: https://arxiv.org/abs/2401.16158
- AppAgent paper: https://arxiv.org/abs/2312.13771
- Computer Use API: https://docs.anthropic.com/en/docs/build-with-claude/computer-use

---

**Ada pertanyaan?** Tulis di [docs/v4/QUESTIONS.md](QUESTIONS.md) (file dibuat pas ada pertanyaan).
