# 28 — Phase 7: Memory Maturity

**Durasi:** 2 minggu
**Tujuan:** Smart memory dengan pattern infer + structured KB. Fuu "kenal" user setelah pemakaian.

---

## Outcome

- KnowledgeGraph categories template lengkap (USER_PROFILE / CONTACT / HABIT / FACT / PREFERENCE)
- Tools: `memory_infer_pattern`, `memory_list_by_category` (Phase 1 sudah ada basic, Phase 7 mature)
- Pattern miner: WorkManager periodic scan command_history + agent_step → infer habit candidates
- Migration option ke bge-m3 (kalau e5-small bottleneck)
- Memory inspection UI (Compose, browse + edit per kategori)
- Memory pruning + access count + TTL refinement
- LLM-driven memory ingestion: per task complete, LLM bisa emit memory_remember

**Test target:** Fuu remember "kantor di Sudirman" setelah user mention 3 kali; Fuu suggest "berangkat sekarang biar tidak telat" saat schedule meeting + lokasi rumah + Sudirman traffic.

---

## Deliverable per Minggu

### Minggu 1: KnowledgeGraph categories + tools

**M1.1: Category templates**
- `memory/categories/UserProfileTemplate.kt`: schema validation untuk USER_PROFILE
- Similarly CONTACT, HABIT, FACT, PREFERENCE templates
- LLM prompt: "Saat memory_remember USER_PROFILE, value harus include: name, pronoun, timezone, language_primary"

**M1.2: memory_infer_pattern tool**
- `agent/tools/impl/MemoryInferPatternTool.kt`
- Args: scope (string, e.g. "morning_routine", "contact_frequency")
- Internal: query command_history + agent_step (limit 200), LLM-driven analysis
- Output: pattern candidate untuk user approve

**M1.3: memory_list_by_category tool**
- `agent/tools/impl/MemoryListByCategoryTool.kt`
- Sudah ada di Phase 1, refine: sort by access_count, filter by confidence

**M1.4: Memory inspection UI**
- `ui/memory/MemoryInspectorScreen.kt`
- Tabs per kategori
- List records: key, value summary, last accessed, confidence
- Edit / delete per record
- Search box (full-text via FTS)

### Minggu 2: Pattern miner + bge-m3 option + polish

**M2.1: Pattern miner WorkManager**
- `memory/miner/PatternMinerWorker.kt`
- Periodic (weekly), scan command_history + audit log + agent_step
- LLM-driven inference (use Gemma local): "User sering melakukan X di jam Y → habit candidate"
- Insert candidate ke memory_record dengan confidence 0.5 + source="auto_miner"
- Notif user: "Fuu menemukan pattern baru: ___. Confirm?" → approve → confidence boost ke 0.9

**M2.2: bge-m3 migration option**
- Compare bge-m3 (~600MB Q4, 1024-dim) vs current e5-small
- Migration utility (re-encode all records, update embedding column)
- Settings toggle: "Use bge-m3 for better accuracy (uses +500MB)"
- Default tetap e5-small kecuali user opt-in

**M2.3: LLM-driven memory ingestion enhancement**
- System prompt update Phase 7: "Setelah task complete, observasi apakah ada fakta penting tentang user yang perlu di-remember. Kalau ya, emit memory_remember sebelum done."
- Track: kalau LLM emit memory_remember, log + show notif "Memory baru: ___"
- User bisa undo via memory_forget di inspector

**M2.4: Memory pruning + TTL refinement**
- LRU evict kalau >5000 records (Phase 1 stub, Phase 7 mature)
- Confidence decay: kalau record tidak diakses 60 hari, confidence -0.1
- Confidence < 0.2 selama 30 hari → auto-forget
- WorkManager daily cleanup

**M2.5: Polishing**
- Memory analytics dashboard (count per kategori, recent access)
- Export memory subset (kategori filter)
- Re-confirm pattern: kalau user trigger task yang match cached habit, confidence boost

---

## Modul Phase 7

```
app/src/main/java/com/chibiclaw/memory/
├── categories/
│   ├── UserProfileTemplate.kt
│   ├── ContactTemplate.kt
│   ├── HabitTemplate.kt
│   ├── FactTemplate.kt
│   └── PreferenceTemplate.kt
├── miner/
│   ├── PatternMinerWorker.kt
│   └── PatternCandidateScorer.kt
└── migration/
    └── BgeM3MigrationUtility.kt

app/src/main/java/com/chibiclaw/agent/tools/impl/
├── MemoryInferPatternTool.kt
└── MemoryListByCategoryTool.kt  # Phase 1 refactored

app/src/main/java/com/chibiclaw/ui/memory/
├── MemoryInspectorScreen.kt
├── MemoryCategoryDetailScreen.kt
└── PatternApprovalDialog.kt
```

---

## Risk

| Risk | Mitigasi |
|------|----------|
| Pattern miner false positive (suggest wrong habit) | User approval workflow; low initial confidence; auto-prune kalau user ignore |
| bge-m3 too slow di on-device | Benchmark first; Phase 7 OPTIONAL migration, default tetap e5-small |
| LLM-driven ingestion bloat (terlalu banyak memory write) | Confidence threshold; auto-prune low-confidence; UI batch undo |
| Memory inspector UI complex | Phase 7 keep simple; advanced features Phase 9+ |
| Confidence decay too aggressive (forget useful fact) | Decay rate conservative; user can pin memory (immune to decay) |

---

## Definition of Done

- [ ] Kategori templates documented + LLM follows schema
- [ ] memory_infer_pattern returns reasonable pattern candidates
- [ ] Memory inspector UI: browse all categories, edit value, delete record
- [ ] Pattern miner runs weekly, suggests at least 1 reasonable habit dari sample data
- [ ] bge-m3 migration option works (manual test, optional)
- [ ] LLM-driven ingestion: test scenario user mention "aku bangun jam 6" → Fuu auto memory_remember HABIT
- [ ] Confidence decay applied (test: record tidak diakses 60 hari → confidence drop)
- [ ] Memory analytics dashboard show counts

---

## Next: [29-phase-8-self-correction.md](29-phase-8-self-correction.md)
