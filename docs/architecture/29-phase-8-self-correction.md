# 29 — Phase 8: Self-Correction + Concurrency

**Durasi:** 2 minggu
**Tujuan:** Robust agent yang handle error class taxonomy + multi-task paralel + resource scheduler.

---

## Outcome

- Error class taxonomy lengkap (SELECTOR_NOT_FOUND, PERMISSION_DENIED, TIMEOUT, AMBIGUOUS, NETWORK_ERROR, RATE_LIMITED, INVALID_ARGS, NOT_AVAILABLE, UNKNOWN)
- Tool result `recoveryHint` field (optional LLM hint)
- AgentRuntime multi-slot paralel (3-5 task simultaneous)
- Resource scheduler: mic / screen capture / TTS / Shizuku binder pool / cloud rate limit
- Priority queue di TaskManager
- Task dependency (parent_id, blocked-on) untuk subtask hierarchy
- Cleanup expired tasks + audit log via WorkManager
- Self-correction observability (per-error stats untuk debug)

**Test target:** 2 task paralel — task A "auto-reply WA Mama" (standing) + task B "user voice command 'buka Spotify'" — kedua jalan tanpa konflik. Failed tool a11y_click di task A → LLM retry strategi vision_tap, sukses.

---

## Deliverable per Minggu

### Minggu 1: Error class + recovery + Resource scheduler

**M1.1: Error class enum mature**
- `agent/tools/ErrorClass.kt`: lengkap dengan dokumentasi per class
- Tools emit specific ErrorClass (refactor Phase 3+ tools yang return generic UNKNOWN)
- ToolResult.Error.recoveryHint field (optional)

**M1.2: System prompt update for self-correction**
- Add ke prompt: "Saat tool error, observasi error_class. Adapt strategi berdasarkan error type. Tidak loop infinite — set realistic max retry per error class."
- Example: "SELECTOR_NOT_FOUND di TikTok → switch ke vision_tap"

**M1.3: Resource scheduler**
- `agent/scheduler/ResourceScheduler.kt`
- Locks: mic (Mutex), screen (Mutex), tts (Mutex), shizuku (Semaphore N=3), cloud rate (per adapter)
- Tool dispatcher acquire lock before execute, release after
- Timeout untuk hindari deadlock

**M1.4: Priority queue**
- TaskRepository.runnable extended: order by priority desc, then created_at asc
- Higher priority tasks preempt lower (kalau slot habis dan task tinggi datang, terdaftar untuk slot berikut)

### Minggu 2: Concurrency + dependencies + cleanup

**M2.1: AgentRuntime multi-slot**
- Constants: MAX_PARALLEL_TASKS = 3 (default), config-able
- activeSlots: ConcurrentHashMap<TaskId, Job>
- tick scheduler: launch new task from runnable list kalau slot kosong

**M2.2: Task dependency**
- `task.parentId` foreign key (existing)
- `task_dependency` table: depends_on_task_id, status (PENDING / RESOLVED / FAILED)
- Task BLOCKED kalau ada unresolved dependency
- Tool `task_create_subtask(goal)` — LLM bisa decompose task

**M2.3: Cleanup WorkManager**
- `agent/cleanup/AgentCleanupWorker.kt` (daily)
- Delete tasks completed/failed > 30 hari
- Delete audit log > TTL
- Delete agent_step orphaned (CASCADE handle)
- Vacuum Room db

**M2.4: Observability**
- Per-error class counter (di Room model_config)
- Dev UI dashboard: total errors per class last 7 days
- Helps tune prompt + tool descriptions

**M2.5: Integration test scenarios**
- 2 task paralel mic + tts conflict resolved
- Self-correction: a11y fail di blacklist app → vision retry
- Subtask: "buatkan summary 3 berita teknologi" → LLM decompose ke 3 subtask paralel

---

## Modul Phase 8

```
app/src/main/java/com/chibiclaw/agent/scheduler/
├── ResourceScheduler.kt
└── MutexLockManager.kt

app/src/main/java/com/chibiclaw/agent/cleanup/
└── AgentCleanupWorker.kt

app/src/main/java/com/chibiclaw/agent/tools/impl/
└── TaskCreateSubtaskTool.kt

app/src/main/java/com/chibiclaw/data/database/
├── TaskDependencyEntity.kt
└── migrations/Migration7to8.kt

app/src/main/java/com/chibiclaw/ui/debug/
└── ErrorStatsScreen.kt
```

---

## Risk

| Risk | Mitigasi |
|------|----------|
| Deadlock di resource scheduler | Timeout per lock acquire; deadlock detection (cycle in waiting graph) |
| Race condition di multi-slot | Concurrent test scenarios; ConcurrentHashMap usage; Mutex thread-safe |
| Task tree explosion (subtask infinite recursion) | max_depth limit per task hierarchy (3 default); LLM constraint di prompt |
| Cleanup worker hapus task yang belum complete (race) | Status filter ketat (COMPLETED/FAILED only); audit log preserve |
| Self-correction loop tanpa progress | maxIteration enforced (Phase 1 udah ada); detect "same tool same error" pattern → fail early |

---

## Definition of Done

- [ ] Error class taxonomy applied di semua tool (test variety errors)
- [ ] System prompt updated, LLM behavior observed self-correct di test scenario
- [ ] Multi-task paralel 3 slot test (3 task running simultaneous, no conflict)
- [ ] Resource scheduler prevent mic double-acquire
- [ ] Priority queue: high-priority task preempt slot allocation
- [ ] Subtask creation works (test: "summarize 3 articles" → 3 subtask paralel)
- [ ] Cleanup worker delete old tasks (test: insert old task, run worker, verify gone)
- [ ] Error stats dashboard show counts
- [ ] No regression Phase 1-7 functionality

---

## Next: [2A-phase-9-polish.md](2A-phase-9-polish.md)
