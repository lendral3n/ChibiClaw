# 27 — Phase 6: Initiative + Standing Instructions

**Durasi:** 3 minggu
**Tujuan:** Proactive agent. Standing instruction dengan ComplexTrigger (Time + Event + Predicate + Composite). InitiativeEngine tick + event-driven evaluator. Guided form UI editor.

---

## Outcome

- StandingInstruction entity + Room storage
- InitiativeEngine: tick (per 5-15s) + event-driven evaluation
- ComplexTrigger types lengkap: Time (cron), Event (notif/battery/screen/geofence/calendar/app-launch/network/location), Predicate (expression), Composite (AND/OR/NOT)
- Event sources: NotificationListenerService, BroadcastReceivers (battery/screen/power/network), GeofencingClient, FusedLocation, UsageStatsManager (app launch detect), AlarmManager (precise time)
- CronParser + TriggerEvaluator + SimplePredicateEvaluator
- Guided form UI editor (multi-step Compose)
- Autonomous + Standing channel routing di TaskManager
- Pre-authorize tools per instruction
- Audit log STANDING_INSTRUCTION_FIRED entries

**Test target:** create instruction "Auto-reply Mama WA antara 18-22 di luar kantor" → simulate WA from Mama at 19:00 outside office → Fuu auto-reply ✓ tanpa user intervention.

---

## Deliverable per Minggu

### Minggu 1: Entity + InitiativeEngine + simple triggers

**M1.1: StandingInstruction entity**
- `data/database/StandingInstructionEntity.kt`
- Room migration 4→5
- Repository CRUD

**M1.2: ComplexTrigger sealed class**
- `agent/initiative/trigger/ComplexTrigger.kt` polymorphic serialization
- TimeTrigger, EventTrigger, PredicateTrigger, CompositeTrigger
- TriggerFilter for events

**M1.3: InitiativeEngine**
- `agent/initiative/InitiativeEngine.kt`
- Tick scheduler (coroutine + delay 5-15s adaptive)
- Event subscriber (collect dari EventBus)
- evaluateAll(): loop instructions enabled, evaluate trigger
- fire(instruction, source): create Task channel=STANDING

**M1.4: TimeTrigger + cron parser**
- `agent/initiative/trigger/CronParser.kt` (atau use cron-utils lib)
- Test cron expressions: `"0 7 * * *"`, `"0 18-22 * * MON-FRI"`

**M1.5: Simple EventTrigger**
- NotificationListenerService events → EventBus
- BroadcastReceivers: battery, screen, power → EventBus
- TriggerFilter matching (package, title regex, range)

### Minggu 2: Predicate + Composite + Geofence + Calendar

**M2.1: PredicateEvaluator**
- `agent/initiative/trigger/SimplePredicateEvaluator.kt`
- Grammar: comparison (`==`, `<`, etc), logical (AND, OR, NOT), set (IN, NOT IN, BETWEEN), accessors (`location.distance_from`, `battery.level`, etc)
- Parser (hand-roll ANTLR atau parser combinator)
- Evaluator dengan world snapshot + memory store lookup

**M2.2: CompositeTrigger**
- AND/OR/NOT recursive evaluation
- Short-circuit eval

**M2.3: GeofenceTrigger**
- `world/geofence/GeofenceManager.kt`
- GeofencingClient.addGeofences()
- Saved places dari MemoryStore (CONTACT/FACT category dengan location data)
- Event ENTER/EXIT → EventBus

**M2.4: CalendarEventTrigger**
- ContentObserver di CalendarContract.Events
- AlarmManager schedule precise broadcast 5 min before event start

**M2.5: App launch detector**
- UsageStatsManager periodic poll (per 10s saat instruction butuh)
- BroadcastReceiver `Intent.ACTION_USER_PRESENT` (rough proxy)

### Minggu 3: Guided form UI + integration

**M3.1: Guided form Compose UI**
- `ui/initiative/StandingInstructionEditorScreen.kt`
- Step 1: name + description
- Step 2: trigger type selector (sederhana / kompleks)
- Step 3-N: per-trigger config (time picker, event source, predicate builder, filter)
- Step Add trigger: append + AND/OR composition
- Step Action: task template input dengan `{{var}}` autocomplete
- Step Eksekusi: priority, cooldown, max-fires/day
- Step Tool permissions: pre-authorize HIGH severity tools
- Step Preview: rendered trigger description + sample task render
- Save → serialize to JSON → Room

**M3.2: Instruction list UI**
- `ui/initiative/StandingInstructionListScreen.kt`
- List all instructions, toggle enable, edit, delete
- Fire history per instruction (audit log query)

**M3.3: Task channel routing**
- TaskManager handle channel=STANDING:
  - Pre-authorized tools list passed dari instruction
  - Overlay tidak expand (silent execution)
  - Status notif at start + end
- Channel=AUTONOMOUS similar (event-driven)

**M3.4: Integration test scenarios**
- "Setiap jam 7, bacakan agenda" → Phase 6 fire test
- "Auto-reply Mama WA" composite trigger test
- "Low battery reminder" predicate trigger test

---

## Modul Phase 6

```
app/src/main/java/com/chibiclaw/agent/initiative/
├── InitiativeEngine.kt
├── EventBus.kt
└── trigger/
    ├── ComplexTrigger.kt
    ├── TriggerEvaluator.kt
    ├── CronParser.kt
    ├── SimplePredicateEvaluator.kt
    ├── PredicateGrammar.kt
    └── TemplateRenderer.kt

app/src/main/java/com/chibiclaw/world/
├── geofence/GeofenceManager.kt
├── calendar/CalendarObserver.kt
├── usage/AppLaunchDetector.kt
└── network/NetworkObserver.kt

app/src/main/java/com/chibiclaw/data/database/
└── StandingInstructionEntity.kt

app/src/main/java/com/chibiclaw/ui/initiative/
├── StandingInstructionEditorScreen.kt
├── StandingInstructionListScreen.kt
├── TriggerBuilderComposables.kt
└── TaskTemplatePicker.kt
```

---

## Dependencies tambahan

```kotlin
dependencies {
    // Cron parsing
    implementation("com.cronutils:cron-utils:9.2.1")
    
    // Geofencing (Play Services Location sudah ada Phase 5)
    // CalendarContract: native API
    
    // UsageStatsManager: native API (butuh PACKAGE_USAGE_STATS permission)
}
```

---

## Permission tambahan Phase 6

- `PACKAGE_USAGE_STATS` (special permission, butuh user grant via Settings → Apps → Special access)
- `READ_CALENDAR`
- `ACCESS_BACKGROUND_LOCATION` (kalau pakai geofence + location)
- `RECEIVE_BOOT_COMPLETED` (sudah Phase 0)
- `BIND_NOTIFICATION_LISTENER_SERVICE` (sudah Phase 3)

Setup wizard step tambahan untuk PACKAGE_USAGE_STATS + READ_CALENDAR + background location.

---

## Sample Standing Instruction Render

User input via guided form:

```
Name: "Auto-reply Mama WA"
Description: "Balas WA Mama jam aktif kalau tidak di kantor"

Trigger:
  Type: Kompleks
  Composition: AND
  
  Trigger 1: Time
    Hari: Senin, Selasa, Rabu, Kamis, Jumat
    Jam: 18:00 - 22:59
    
  Trigger 2: Predicate
    Expression: location.distance_from('kantor') > 1km
    
  Trigger 3: Event
    Source: NOTIFICATION_POSTED
    Package: com.whatsapp
    Title regex: .*Mama.*

Action:
  Task template: "Balas WA dari Mama: '{{event.text}}' dengan reply singkat"

Eksekusi:
  Priority: Normal
  Cooldown: 30 menit
  Max fires per hari: 20
  
  Tools pre-authorized:
    [x] a11y_click
    [x] a11y_type
    [x] messaging (HIGH) — Auto-confirm: Yes
```

Compiled ke JSON:

```json
{
  "id": "uuid-xxx",
  "name": "Auto-reply Mama WA",
  "description": "Balas WA Mama jam aktif kalau tidak di kantor",
  "trigger": {
    "type": "composite",
    "op": "AND",
    "children": [
      {"type": "time", "cron": "0 18-22 * * MON-FRI"},
      {"type": "predicate", "expression": "location.distance_from('kantor') > 1km"},
      {"type": "event", "eventType": "NOTIFICATION_POSTED",
       "filter": {"key": "package", "value": "com.whatsapp", "regex": ".*Mama.*"}}
    ]
  },
  "taskTemplate": "Balas WA dari Mama: '{{event.text}}' dengan reply singkat",
  "enabled": true,
  "priority": 3,
  "cooldownMs": 1800000,
  "maxFiresPerDay": 20,
  "preAuthorizedTools": ["a11y_click", "a11y_type", "messaging"]
}
```

---

## Risk

| Risk | Mitigasi |
|------|----------|
| PACKAGE_USAGE_STATS revocation by user | Detect lose access → notif re-grant; instruction with app_launch trigger fail gracefully |
| Geofence battery drain | Use lower-power geofence API; coarse location default; user toggle |
| Notification listener crashes | Defensive coding, restart service on exception, audit log unusual events |
| Cron expression user error | Form-based input (drop-down), don't expose raw cron unless advanced mode |
| Predicate evaluator complexity creep | Limit grammar; fallback ke LLM-evaluated predicate (PredicateEvaluator.LLM) untuk edge case |
| Standing instruction runaway (fire too often) | maxFiresPerDay + cooldownMs hard limit; global rate limit 30/hour |
| Privacy concern: agent active 24/7 | Audit log lengkap; user dapat lihat semua fire history; toggle disable all standing |

---

## Performance Target

| Metric | Target |
|--------|--------|
| Tick interval | 5-15s adaptive (lebih cepat saat foreground active, lebih lambat saat user idle) |
| Tick evaluation overhead | <100ms untuk 10 instructions enabled |
| Event-driven trigger latency (notif → task created) | <200ms |
| Geofence trigger latency | <30s (Android geofence inherent) |
| Battery drain InitiativeEngine alone | <1%/jam |

---

## Definition of Done

- [ ] StandingInstruction CRUD via UI
- [ ] TimeTrigger fire test (set cron 1 menit ke depan, verify Task created)
- [ ] EventTrigger notif test (post test notif via adb shell cmd notif → trigger fired)
- [ ] PredicateTrigger battery test (set instruction "battery < 50%" → drain → fire)
- [ ] CompositeTrigger 3-element AND test (Auto-reply Mama composite)
- [ ] Geofence enter/exit test (move physical device + verify ENTER event)
- [ ] Guided form UI walkthrough sukses (create, edit, delete instruction)
- [ ] Task channel=STANDING execute silent (no overlay expand)
- [ ] Pre-authorize tools skip confirmation (test: standing with messaging pre-auth)
- [ ] Cooldown + max fires per day enforced
- [ ] Audit log STANDING_INSTRUCTION_FIRED entries

---

## Next: [28-phase-7-memory.md](28-phase-7-memory.md)
