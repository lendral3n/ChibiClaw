# Phase 8 — Self-Correction + Concurrency: Progress Audit

> Tanggal: 2026-05-14 · Build: `:app:assembleDebug` ✅ SUCCESSFUL 2m3s

## Ringkasan eksekusi

Phase 8 menambahkan **robustness + scaling layer**:
- **ResourceScheduler** untuk lock shared resources (mic, screen, TTS, Shizuku, cloud)
- **Multi-slot AgentRuntime** (max 3 task paralel)
- **TaskDependency** entity + Room v5 + `task_create_subtask` tool
- **AgentCleanupWorker** daily housekeeping
- **AuditLogger.toolOutcomeCountsLast7d** + **ErrorStatsScreen** observability
- **PromptBuilder system prompt** extended dengan self-correction playbook
  per ErrorClass + subtask hint

Total tool catalog: **25 tools**.

---

## Cakupan per work-package

### W1 — Resource scheduler + self-correction prompt — **100%**

| File | Status |
| --- | --- |
| `agent/scheduler/ResourceScheduler.kt` | ✅ Mutex MIC/SCREEN/TTS + Semaphore SHIZUKU(3)/CLOUD(3), timeout 10s |
| `agent/tools/ToolDispatcher.kt` resource acquire wrap | ✅ withResource block per kind, fallback Error.TIMEOUT |
| `ai/llm/PromptBuilder.kt` self-correction playbook | ✅ per-ErrorClass strategi + anti-same-tool-same-error |
| TaskDao `runnable` priority order | ✅ sudah ada dari Phase 1 (ORDER BY priority DESC, created_at ASC) |

### W2 — Multi-slot + dependency + cleanup + stats — **100%**

| File | Status |
| --- | --- |
| `agent/TaskManager.kt` MAX_PARALLEL_TASKS=3 + activeCount/activeIds/maxParallel exposed | ✅ |
| `agent/AgentRuntime.kt` runOneTick fill all free slots | ✅ while loop until null |
| `data/database/TaskDependencyEntity.kt` | ✅ FK CASCADE + status PENDING/RESOLVED/FAILED |
| `data/database/TaskDependencyDao.kt` | ✅ insert, listDependenciesOf, resolveDependentsOf, countPending |
| AppDatabase v4 → v5 | ✅ destructive migration (dev mode) |
| `di/AppModule.kt` provideTaskDependencyDao | ✅ |
| `agent/tools/impl/TaskCreateSubtaskTool.kt` | ✅ LOW severity, args goal/priority/blocks_parent, __taskId stamped |
| `ToolDispatcher` TOOLS_NEEDING_TASK_ID set | ✅ escalate_to_cloud + task_create_subtask |
| `agent/cleanup/AgentCleanupWorker.kt` | ✅ HiltWorker daily, cleanupExpired |
| `memory/miner/MemoryWorkScheduler.kt` extended | ✅ enqueue AgentCleanupWorker uniqueperiodic KEEP |
| `data/database/AuditDao.kt` countByOutcome + AuditOutcomeCount | ✅ |
| `compliance/AuditLogger.kt` toolOutcomeCountsLast7d | ✅ |
| `ui/debug/ErrorStatsScreen.kt` | ✅ slots + resources + outcome counts |
| MainActivity NavHost route debug/stats | ✅ |

---

## Tool catalog Phase 8 — final (25 tools)

Phase 1 (6) + Phase 3 (10) + Phase 4 (1) + Phase 5 (6) + Phase 7 (2) + **Phase 8 (1): task_create_subtask**.

---

## Build verification

```
> Task :app:assembleDebug
BUILD SUCCESSFUL in 2m 3s
43 actionable tasks: 12 executed, 31 up-to-date
```

Pre-existing warnings unchanged.

---

## Concurrency model

```
ChibiService.onStartCommand
   ↓ AgentRuntime.start (tick loop)
[Every TICK_INTERVAL_MS]
   ↓ runOneTick: loop nextRunnable() until null
[For each free slot]
   ↓ scope.launch { executeTask(id) }
[executeTask iteratif]
   ↓ ToolDispatcher.execute
[Per tool]
   ↓ resourceKindFor(toolName) → null OR ResourceKind
   ↓ if not null, withResource(kind) acquire mutex/semaphore
[Tool body runs serialized per resource]
   ↓ release on completion
[ResultStatus → AuditLog → AgentStep persist → next iteration]
```

Three slots running in parallel; shared resources (mic/screen/Shizuku/cloud) serialized via scheduler. No deadlock risk (single-resource per tool call, 10s timeout fallback).

---

## Self-correction playbook (system prompt)

LLM diberi strategi per ErrorClass:
- **SELECTOR_NOT_FOUND** → vision_tap atau a11y_describe_screen + retry
- **PERMISSION_DENIED** → system_action ke Settings atau await_user
- **TIMEOUT** → switch alternatif tool atau escalate_to_cloud
- **AMBIGUOUS** → await_user clarification
- **NETWORK_ERROR** → wait 5s + retry, then skip kalau offline-OK
- **RATE_LIMITED** → wait cooldown atau escalate ke adapter lain
- **INVALID_ARGS** → re-emit dengan args koreksi
- **NOT_AVAILABLE** → check recoveryHint, await_user atau fail dengan summary
- **UNKNOWN** → max 1 retry, fail

Anti-loop: same-tool-same-error 2x → switch atau fail.

---

## Yang belum dikerjakan (defer eksplisit)

1. **Dependency resolver di TaskRepository.runnable** — saat ini DAO query
   ORDER BY priority + created_at, tidak filter task BLOCKED-on-pending-deps.
   Solusi sementara: LLM yang aware (task_create_subtask emit sebelum lanjut),
   AgentRuntime hanya pick PENDING (BLOCKED tidak picked). Auto-mark-BLOCKED
   saat subtask created defer Phase 9.
2. **Resolver auto-mark RESOLVED** saat subtask complete → parent unblocked.
   Defer Phase 9 — perlu listener di TaskRepository.markCompleted.
3. **Per-error-class counter di model_config** — saat ini count via audit
   log aggregat. Phase 9 dedicated table.
4. **Subtask depth limit** — saat ini tanpa guard, LLM dianjurkan via prompt.
   Phase 9 add max_depth=3 enforce.
5. **Dependency cycle detection** — saat ini DAG-only by convention.
6. **Cleanup vacuum Room db** — defer Phase 9 (SQLCipher tidak support VACUUM).
7. **Audit log dedicated counter table** untuk faster dashboard query.

---

## Risiko residual

- **Multi-slot starvation**: LOW priority task bisa kelamaan menunggu kalau HIGH terus datang. Mitigasi: priority cap di guided form 1-5; tidak ada starvation di skala personal (single user, paling 10 task/jam).
- **Resource scheduler timeout 10s terlalu agresif untuk vision_tap**: MiniCPM-V inference bisa 3-4s tapi capture cepat. Tool sendiri pakai withTimeoutOrNull. Acceptable — 10s lock cukup untuk tool typical.
- **TaskDependency tanpa resolver auto-mark**: parent task tetap BLOCKED sampai user manual `markCompleted` via UI. Phase 9 add observer.
- **Subtask infinite recursion**: tidak ada hard limit; LLM constraint di prompt. Manual test Phase 9 catch kalau jadi issue.

---

## Sign-off

✅ Build verified.
✅ ResourceScheduler wired ke ToolDispatcher untuk tool-yang-konflik.
✅ TaskManager 3 paralel, AgentRuntime fill-all-slots per tick.
✅ task_create_subtask tool registered (25 total).
✅ AgentCleanupWorker scheduled via MemoryWorkScheduler.
✅ ErrorStatsScreen live di NavHost route debug/stats.
✅ System prompt extended self-correction playbook + subtask hint.

Phase 8 ready untuk commit. Selanjutnya Phase 9 (Polish: manual test,
in-app model downloader, telemetry, missing edge cases) sesuai
[2A-phase-9-polish.md](2A-phase-9-polish.md).
