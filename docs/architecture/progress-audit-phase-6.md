# Phase 6 — Initiative + Standing Instructions: Progress Audit

> Tanggal: 2026-05-14 · Build: `:app:assembleDebug` ✅ SUCCESSFUL 1m33s

## Ringkasan eksekusi

Phase 6 menambahkan **proactive agent layer** — InitiativeEngine yang ngeloop +
dengerin event, evaluate standing instructions, fire task channel=STANDING.
ComplexTrigger sealed-class polymorphic (Time/Event/Predicate/Composite/Geofence).
SafetyGate honour pre-authorized tools dari instruction supaya HIGH severity
tool skip overlay saat fire dari standing.

Total **13 file baru + 8 modified** plus Room v3 → v4 migration (destructive).

---

## Cakupan per work-package

### W1 — Entity + Engine + Time/Event triggers — **100%**

| File | Status |
| --- | --- |
| `data/database/StandingInstructionEntity.kt` | ✅ kotlinx.datetime.Instant fields |
| `data/database/StandingInstructionDao.kt` | ✅ CRUD + recordFire + resetDailyCounter |
| `data/database/AppDatabase.kt` v3 → v4 | ✅ destructive migration (dev mode) |
| `di/AppModule.kt` provideStandingInstructionDao | ✅ |
| `data/repository/StandingInstructionRepository.kt` | ✅ JSON serialize ComplexTrigger + canFire + recordFire |
| `agent/initiative/EventBus.kt` | ✅ SharedFlow buffer 32 |
| `agent/initiative/trigger/ComplexTrigger.kt` | ✅ sealed polymorphic dengan @SerialName discriminator |
| `agent/initiative/trigger/CronParser.kt` | ✅ cron-utils 9.2.1 UNIX cron |
| `agent/initiative/trigger/TriggerEvaluator.kt` | ✅ tick + event mode + hasTickLeaf/hasEventLeaf |
| `agent/initiative/trigger/SimplePredicateEvaluator.kt` | ✅ tokenize + recursive descent + Context resolver |
| `agent/initiative/trigger/TemplateRenderer.kt` | ✅ `{{path}}` substitution |
| `agent/initiative/InitiativeEngine.kt` | ✅ tickLoop (10s interval) + eventLoop + fire mutex |
| `build.gradle.kts` `libs.cron.utils` | ✅ aktif |

### W2 — Event sources + SafetyGate pre-auth — **100%**

| File | Status |
| --- | --- |
| `world/observers/SystemEventReceiver.kt` | ✅ battery low/charging/unplug, screen on/off, user present |
| `world/observers/NotificationEventBridge.kt` | ✅ bridge ChibiNotificationListener.events → EventBus |
| `service/ChibiService.kt` lifecycle wiring | ✅ register/unregister + InitiativeEngine.start/stop |
| `agent/tools/safety/SafetyGate.kt` pre-auth path | ✅ STANDING+triggerSource → repo lookup → skip overlay |

**Defer:** Geofence (M2.3), CalendarEvent observer (M2.4), AppLaunch detector
via UsageStatsManager (M2.5). EventType enum sudah include GEOFENCE_ENTER/EXIT,
CALENDAR_EVENT_STARTING, APP_LAUNCHED. Implementasi observer Phase 9 polish —
saat itu cukup tambah class observer + register di ChibiService.

### W3 — Guided form Compose UI — **90%**

| File | Status |
| --- | --- |
| `ui/initiative/StandingInstructionListScreen.kt` | ✅ list + toggle + edit + delete |
| `ui/initiative/StandingInstructionEditorScreen.kt` | ✅ 3-tab (Trigger/Task/Eksekusi) + 4 trigger kind + validation |
| `ui/MainActivity.kt` NavHost routes initiative/list + initiative/edit/{id} | ✅ |

**Trade-off:** Trigger kind UI dibatasi 4 preset (TIME / EVENT / PREDICATE /
COMPOSITE_TIME_EVENT). Composite arbitrary AND/OR/NOT lebih dari 2 children
defer Phase 9 (rare case, JSON editor cukup).

---

## Build verification

```
> Task :app:assembleDebug
BUILD SUCCESSFUL in 1m 33s
43 actionable tasks: 13 executed, 30 up-to-date
```

Pre-existing warnings unchanged.

---

## Bug ditemukan + fix

1. **Initial AuditLogger.log() signature mismatch** — InitiativeEngine pakai
   parameter `standingInstructionId` yang tidak ada. Fix: embed ID di
   `dataSummary` string. AuditLogger akan dapat schema column ID di Phase 9.

Tidak ada error compile lain — Phase 6 berjalan smooth setelah lessons-learned
dari Phase 5 (avoid `Mutex.withLock { suspend body }` pattern di kelas
Hilt-injected; pattern ini dipakai di InitiativeEngine.fire dan compile clean
karena return type Unit, bukan nullable).

---

## Standing Instruction lifecycle

```
[User create via UI]
   ↓ Repository.upsert(serialize ComplexTrigger to JSON)
[Room standing_instruction table]
   ↓ Service onStartCommand → InitiativeEngine.start
[Tick loop 10s] ←— [Event loop via EventBus]
   ↓ evaluate(trigger, ctx)
[canFire check: enabled + cooldown + max/day]
   ↓ render template
[TaskManager.enqueue channel=STANDING triggerSource=standing:{id}]
   ↓ AgentRuntime pick
[ToolDispatcher.execute → SafetyGate.requestApproval]
   ↓ pre-authorized? skip overlay : show confirmation
[Tool execute → result → loop]
```

---

## Yang belum dikerjakan (defer eksplisit)

1. **Geofence + Calendar + AppLaunch observers** — class skeleton di EventType
   sudah ada, observer impl defer Phase 9.
2. **Network connectivity observer** — defer Phase 9.
3. **Audit log standing instruction ID column** — saat ini di-embed di
   dataSummary. Phase 9 add proper column.
4. **Composite arbitrary nesting UI** — saat ini cuma preset 2-children AND.
5. **Predicate LLM fallback** — untuk expression yang tidak parseable grammar.
6. **AlarmManager precise time trigger** — cron-utils tick-based saat ini cukup
   untuk standar use case (granularity ≥ 1 menit).
7. **Setup wizard step** — instruction CRUD via UI di Settings, tidak via wizard.

---

## Tool catalog Phase 6 — unchanged (22 tools)

Tidak ada tool baru. Phase 6 fokus di proactive layer di atas tool execution.

---

## Sample standing instruction (yang bisa di-create via UI)

```kotlin
// "Auto-reply Mama WA jam 18-22"
ComplexTrigger.Composite(
    op = CompositeOp.AND,
    children = listOf(
        ComplexTrigger.Time(cron = "0 18-22 * * MON-FRI"),
        ComplexTrigger.Event(
            eventType = EventType.NOTIFICATION_POSTED,
            filter = TriggerFilter(
                packageName = "com.whatsapp",
                titleRegex = ".*Mama.*",
            ),
        ),
    ),
)
// taskTemplate: "Balas WA dari Mama: '{{event.text}}' dengan reply singkat"
// preAuthorizedTools: ["a11y_click", "a11y_type", "messaging"]
```

---

## Sign-off

✅ Build verified.
✅ InitiativeEngine tick + event loop wired ke ChibiService lifecycle.
✅ SafetyGate honour pre-authorized tools per standing instruction.
✅ Compose UI list + 3-tab editor live di NavHost route initiative/list.
✅ AuditLog STANDING_INSTRUCTION_FIRED entry recorded per fire.

Phase 6 ready untuk commit. Selanjutnya Phase 7 (Memory mature: KB query +
pattern detection) sesuai [28-phase-7-memory.md](28-phase-7-memory.md).
