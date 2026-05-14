# Phase 7 — Memory Maturity: Progress Audit

> Tanggal: 2026-05-14 · Build: `:app:assembleDebug` ✅ SUCCESSFUL 1m45s

## Ringkasan eksekusi

Phase 7 mendewasakan memory layer:
- **CategoryTemplates** — schema validator + suggested fields per kategori
  (USER_PROFILE / CONTACT / HABIT / FACT / PREFERENCE) + LLM prompt hint
  diinjeksi via PromptBuilder.
- **2 tools baru**: `memory_list_by_category` + `memory_infer_pattern`.
- **MemoryInspectorScreen** — tabs per kategori, search substring,
  expand/collapse, delete.
- **PatternMinerWorker** — periodic weekly aggregate task history → habit
  candidate dengan confidence 0.4-0.7 untuk user approve.
- **MemoryDecayWorker** — daily: decay confidence kalau stale 60 hari,
  auto-forget kalau confidence < 0.2 selama 30 hari + LRU evict.
- **MemoryWorkScheduler** — enqueue periodic workers via WorkManager
  (battery-not-low constraint, KEEP policy idempotent).
- **NavHost route** `memory/inspector`.

Total tool catalog: **24 tools** advertised ke LLM.

---

## Cakupan per work-package

### W1 — Templates + Tools + Inspector — **100%**

| File | Status |
| --- | --- |
| `memory/categories/CategoryTemplates.kt` | ✅ 5 template + validate() + llmPromptHint() |
| `agent/tools/impl/MemoryListByCategoryTool.kt` | ✅ filter by minConfidence, sort accessCount DESC |
| `agent/tools/impl/MemoryInferPatternTool.kt` | ✅ aggregate hour-bucket + keyword extract |
| `ui/memory/MemoryInspectorScreen.kt` | ✅ tabs per kategori + search + expand + delete |
| `ai/llm/PromptBuilder.kt` system prompt extension | ✅ kategori hints inline |
| TaskRepository.recentSnapshot() helper | ✅ suspend query (untuk pattern miner & infer tool) |
| `di/ToolsModule.kt` 2 new bindings | ✅ |
| `ui/MainActivity.kt` NavHost route memory/inspector | ✅ |

### W2 — Pattern Miner + Decay + Analytics — **95%**

| File | Status |
| --- | --- |
| `memory/miner/PatternMinerWorker.kt` | ✅ HiltWorker, weekly, aggregate hour-bucket + 7-day span |
| `memory/miner/MemoryDecayWorker.kt` | ✅ HiltWorker, daily, confidence decay + auto-forget + cleanup |
| `memory/miner/MemoryWorkScheduler.kt` | ✅ enqueueUniquePeriodicWork KEEP policy |
| MemoryRepository extensions (listStaleSince, deleteLowConfidenceStale, countByCategory) | ✅ |
| TaskDao + DAO (MemoryCategoryCount data class) | ✅ |
| ChibiService schedule on onStartCommand | ✅ |

**Defer (5%):**
- **Memory analytics dashboard UI** — repo punya `countByCategory()`, tapi
  belum ada dashboard layout. Defer Phase 9 polish.
- **bge-m3 migration utility** — defer Phase 9 (e5-small kerjanya cukup;
  Lendra opt-in optional).
- **LLM-driven pattern naming** — saat ini key auto `auto:hour_NN`. Phase 9
  bisa call Gemma untuk natural-language label.
- **Pattern approval dialog** — saat ini user approve via inspector +
  manually edit confidence (atau biarkan TTL 90 hari expire). Phase 9 add
  dedicated approval dialog dengan boost confidence ke 0.9.

---

## Tool catalog Phase 7 — final (24 tools)

Phase 1 (6): wait, await_user, intent_open, system_action, memory_remember, memory_recall.
Phase 3 (10): a11y_click, a11y_type, a11y_describe_screen, a11y_scroll, shizuku_exec, shizuku_force_stop, shizuku_grant_permission, messaging, intent_send, world_get_notifications.
Phase 4 (1): escalate_to_cloud.
Phase 5 (6): vision_tap, vision_describe, vision_extract_text, world_get_installed_apps, world_get_location, world_get_schedule.
**Phase 7 (2): memory_list_by_category, memory_infer_pattern.**

---

## Memory layer flow

```
[User input / LLM observation]
   ↓ memory_remember (kategori sesuai CategoryTemplates hint)
[MemoryStore.remember → embedding via e5-small → Room memory_record]
   ↓ recall flow: memory_recall semantic OR memory_list_by_category enumerate
[ContextBuilder auto-include top-3 high-confidence di prompt]

[Periodic background, MemoryWorkScheduler]
   ↓ weekly PatternMinerWorker
[TaskRepository.recentSnapshot(200) → hour-bucket aggregate → HABIT candidate]
   ↓ confidence 0.5, TTL 90 hari
[User review via MemoryInspectorScreen → boost confidence atau biarkan expire]

   ↓ daily MemoryDecayWorker
[Stale > 60d → confidence -0.1; <0.2 + stale 30d → delete; LRU > 5000 → evict]
```

---

## Build verification

```
> Task :app:assembleDebug
BUILD SUCCESSFUL in 1m 45s
43 actionable tasks: 12 executed, 31 up-to-date
```

Pre-existing warnings unchanged. Room schemaLocation tetap warning (defer to
schema-aware migration Phase 9).

---

## Yang belum dikerjakan (defer eksplisit)

1. **Memory analytics dashboard Compose** — repo helper sudah ada, layout
   defer Phase 9.
2. **bge-m3 migration utility** — re-encode all records + Settings toggle.
3. **LLM-driven habit naming** — PatternMinerWorker pakai Gemma untuk label
   natural-language.
4. **Pattern approval dialog dedicated UI** (boost confidence ke 0.9).
5. **Memory export per kategori** — JSON dump untuk backup.
6. **Pin memory immune to decay** — flag `pinned: Boolean` di entity.
7. **FTS untuk inspector search** — saat ini substring filter di Kotlin side.

---

## Risiko residual

- **Pattern miner false positive**: confidence awal 0.4-0.7 (rendah) +
  TTL 90 hari + user perlu observasi via inspector. Auto-forget kalau tidak
  diakses + low confidence.
- **Decay terlalu agresif**: STALE_DAYS=60 + DECAY_STEP=0.1 → record akan
  jatuh dari 1.0 ke <0.2 minimal dalam 480 hari kalau tidak pernah diakses.
  Phase 9 add `pinned` flag untuk immune.
- **bge-m3 not migrated**: e5-small (384-dim) sudah cukup untuk personal
  use-case. Migration optional.

---

## Sign-off

✅ Build verified.
✅ CategoryTemplates wired ke LLM prompt + inspector UI.
✅ 2 tools registered via DI (memory_list_by_category + memory_infer_pattern).
✅ MemoryInspectorScreen live di NavHost route memory/inspector.
✅ PatternMinerWorker + MemoryDecayWorker scheduled periodic via WorkManager.

Phase 7 ready untuk commit. Selanjutnya Phase 8 (Self-correction + parallel
tasks) sesuai [29-phase-8-self-correction.md](29-phase-8-self-correction.md).
