# 17 — Standing Instructions

Aturan persistent yang trigger task otomatis. Superset cron — time + event + predicate + composite. ADR-008.

---

## Konsep

Standing instruction = "kalau {trigger}, lakukan {task}". User definisi sekali, sistem run forever sampai user disable.

Contoh:
- "Setiap jam 7 pagi, baca agenda hari ini"
- "Kalau notif WA dari Mama, balas dengan ✓"
- "Kalau baterai <20% dan tidak ada charger 30 menit, ingatkan aku"
- "Antara 18-22 + bukan di kantor + notif WA Mama → auto-reply ✓"

---

## Entity Recap

Lihat [11-data-model.md](11-data-model.md#standinginstruction):

```kotlin
@Entity(tableName = "standing_instruction")
data class StandingInstructionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val triggerJson: String,           // ComplexTrigger serialized
    val taskTemplate: String,          // goal template dengan {{var}}
    val enabled: Boolean = true,
    val createdAt: Instant,
    val lastFiredAt: Instant? = null,
    val fireCount: Int = 0,
    val cooldownMs: Long? = null,
    val priority: Int = 3,
    val maxFiresPerDay: Int? = null,
    val preAuthorizedTools: List<String> = emptyList(),  // HIGH severity tools yang pre-confirm
)
```

---

## ComplexTrigger Types

### TimeTrigger

Cron-based scheduling. Pakai cron-utils library.

```kotlin
@Serializable @SerialName("time")
data class TimeTrigger(
    val cron: String,                  // "0 7 * * *"
    val timezone: String = "Asia/Jakarta",
) : ComplexTrigger()
```

Common cron expressions:
- `0 7 * * *` — setiap hari jam 7
- `0 */2 * * *` — setiap 2 jam
- `0 18-22 * * MON-FRI` — Senin-Jumat 18:00-22:59
- `0 6 * * SUN` — Minggu jam 6

Editor UI: guided field hari + jam, generate cron string.

### EventTrigger

Reaktif terhadap Android event.

```kotlin
@Serializable @SerialName("event")
data class EventTrigger(
    val eventType: EventType,
    val filter: TriggerFilter,
) : ComplexTrigger()

enum class EventType {
    NOTIFICATION_POSTED,         // notif app baru
    NOTIFICATION_REMOVED,
    GEOFENCE_ENTER,              // masuk area
    GEOFENCE_EXIT,
    APP_LAUNCH,                  // foreground app berubah
    APP_CLOSE,
    BATTERY_LEVEL_CHANGE,        // dengan filter range
    BATTERY_CHARGING_CHANGE,     // plug / unplug
    SCREEN_ON,
    SCREEN_OFF,
    CALENDAR_EVENT_START,        // event kalender mulai
    CALENDAR_EVENT_END,
    USER_IDLE_FOR,               // idle X detik
    NETWORK_CONNECTED,
    NETWORK_DISCONNECTED,
    LOCATION_UPDATE,             // location berubah (interval-based)
}

@Serializable
data class TriggerFilter(
    val key: String? = null,         // "package", "title", "geofence_id"
    val value: String? = null,
    val regex: String? = null,
    val rangeMin: Double? = null,    // battery min %
    val rangeMax: Double? = null,    // battery max %
    val durationMs: Long? = null,    // untuk USER_IDLE_FOR
)
```

Event sources implementation:

| Event | Implementation |
|-------|----------------|
| NOTIFICATION_POSTED/REMOVED | NotificationListenerService |
| GEOFENCE_ENTER/EXIT | GeofencingClient + GeofencingRequest |
| APP_LAUNCH/CLOSE | UsageStatsManager polling + ActivityLifecycle |
| BATTERY_LEVEL_CHANGE | BroadcastReceiver `Intent.ACTION_BATTERY_CHANGED` |
| BATTERY_CHARGING_CHANGE | `Intent.ACTION_POWER_CONNECTED / DISCONNECTED` |
| SCREEN_ON/OFF | `Intent.ACTION_SCREEN_ON/OFF` |
| CALENDAR_EVENT_START | CalendarContract observer + AlarmManager schedule |
| USER_IDLE_FOR | UserUnlock check + Timer |
| NETWORK_CONNECTED/DISCONNECTED | ConnectivityManager.NetworkCallback |
| LOCATION_UPDATE | FusedLocationProviderClient periodic |

### PredicateTrigger

Boolean expression yang di-evaluate per tick.

```kotlin
@Serializable @SerialName("predicate")
data class PredicateTrigger(
    val expression: String,        // simple syntax atau LLM-evaluated
    val evaluator: PredicateEvaluator = PredicateEvaluator.SIMPLE,
) : ComplexTrigger()

enum class PredicateEvaluator {
    SIMPLE,    // grammar-based, fast
    LLM,       // ambil snapshot + send ke LLM (more expressive, slow)
}
```

Simple expression syntax (Phase 6 implementation):

```
location.distance_from('kantor') > 1km
battery.level < 20 AND NOT battery.charging
schedule.next_event.starts_in < 15min
foreground_app NOT IN ['com.spotify.music', 'com.netflix.mediaclient']
hour BETWEEN 18 AND 22
day_of_week IN [MON, TUE, WED, THU, FRI]
```

Parser sederhana grammar (ANTLR atau hand-roll). Evaluator dapat WorldSnapshot, return bool.

### CompositeTrigger

AND/OR/NOT dari trigger lain.

```kotlin
@Serializable @SerialName("composite")
data class CompositeTrigger(
    val op: CompositeOp,
    val children: List<ComplexTrigger>,
) : ComplexTrigger()

enum class CompositeOp { AND, OR, NOT }
```

Contoh "Antara 18-22 + bukan di kantor + notif WA Mama":

```json
{
  "type": "composite",
  "op": "AND",
  "children": [
    {
      "type": "time",
      "cron": "0 18-22 * * *"
    },
    {
      "type": "predicate",
      "expression": "location.distance_from('kantor') > 1km"
    },
    {
      "type": "event",
      "eventType": "NOTIFICATION_POSTED",
      "filter": {
        "key": "package",
        "value": "com.whatsapp",
        "regex": ".*Mama.*"
      }
    }
  ]
}
```

---

## Initiative Engine

```kotlin
@Singleton
class InitiativeEngine @Inject constructor(
    private val instructionRepo: StandingInstructionRepository,
    private val triggerEvaluator: TriggerEvaluator,
    private val worldObserver: WorldObserver,
    private val taskManager: TaskManager,
    private val eventBus: EventBus,
) {
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    fun start() {
        // Tick-based evaluation (untuk Time + Predicate trigger)
        scope.launch {
            while (isActive) {
                evaluateAll()
                delay(WORLD_TICK_INTERVAL)  // 5-15 detik adaptive
            }
        }
        
        // Event-based evaluation
        scope.launch {
            eventBus.events.collect { event ->
                evaluateEventDriven(event)
            }
        }
    }
    
    private suspend fun evaluateAll() {
        val instructions = instructionRepo.listEnabled()
        val snapshot = worldObserver.current()
        
        for (instruction in instructions) {
            val trigger = Json.decodeFromString<ComplexTrigger>(instruction.triggerJson)
            if (shouldEvaluateOnTick(trigger)) {
                val match = triggerEvaluator.evaluate(trigger, snapshot, instruction.lastFiredAt)
                if (match) {
                    fire(instruction, "tick")
                }
            }
        }
    }
    
    private suspend fun evaluateEventDriven(event: WorldEvent) {
        val instructions = instructionRepo.listEnabledWithEventTrigger(event.type)
        val snapshot = worldObserver.current()
        
        for (instruction in instructions) {
            val trigger = Json.decodeFromString<ComplexTrigger>(instruction.triggerJson)
            val match = triggerEvaluator.evaluate(trigger, snapshot, instruction.lastFiredAt, event)
            if (match) {
                fire(instruction, "event:${event.type}")
            }
        }
    }
    
    private suspend fun fire(instruction: StandingInstructionEntity, source: String) {
        // Cooldown check
        if (instruction.cooldownMs != null && instruction.lastFiredAt != null) {
            val elapsed = Duration.between(instruction.lastFiredAt, Instant.now()).toMillis()
            if (elapsed < instruction.cooldownMs) return
        }
        
        // Max fires per day check
        if (instruction.maxFiresPerDay != null) {
            val firedToday = instructionRepo.countFiresToday(instruction.id)
            if (firedToday >= instruction.maxFiresPerDay) return
        }
        
        // Render task goal from template
        val goal = renderTemplate(instruction.taskTemplate, worldObserver.current())
        
        // Create task
        val task = TaskEntity(
            id = UUID.randomUUID().toString(),
            channel = TaskChannel.STANDING,
            goal = goal,
            status = TaskStatus.PENDING,
            priority = instruction.priority,
            createdAt = Instant.now(),
            triggerSource = instruction.id,
            iterationCount = 0,
            maxIteration = 10,
        )
        
        taskManager.enqueue(task)
        instructionRepo.recordFire(instruction.id)
    }
}
```

---

## TriggerEvaluator

```kotlin
@Singleton
class TriggerEvaluator @Inject constructor(
    private val cronParser: CronParser,
    private val predicateEvaluator: PredicateEvaluator,
) {
    
    fun evaluate(
        trigger: ComplexTrigger,
        snapshot: WorldSnapshot,
        lastFired: Instant?,
        event: WorldEvent? = null,
    ): Boolean {
        return when (trigger) {
            is TimeTrigger -> evaluateTime(trigger, snapshot, lastFired)
            is EventTrigger -> evaluateEvent(trigger, snapshot, event)
            is PredicateTrigger -> evaluatePredicate(trigger, snapshot)
            is CompositeTrigger -> evaluateComposite(trigger, snapshot, lastFired, event)
        }
    }
    
    private fun evaluateTime(trigger: TimeTrigger, snapshot: WorldSnapshot, lastFired: Instant?): Boolean {
        val cron = cronParser.parse(trigger.cron)
        val nextRun = cron.nextExecution(lastFired ?: Instant.EPOCH) ?: return false
        return snapshot.timestamp >= nextRun
    }
    
    private fun evaluateEvent(trigger: EventTrigger, snapshot: WorldSnapshot, event: WorldEvent?): Boolean {
        if (event == null) return false
        if (event.type != trigger.eventType) return false
        
        // Apply filter
        return matchesFilter(event, trigger.filter)
    }
    
    private fun evaluatePredicate(trigger: PredicateTrigger, snapshot: WorldSnapshot): Boolean {
        return predicateEvaluator.evaluate(trigger.expression, snapshot)
    }
    
    private fun evaluateComposite(
        trigger: CompositeTrigger,
        snapshot: WorldSnapshot,
        lastFired: Instant?,
        event: WorldEvent?,
    ): Boolean {
        return when (trigger.op) {
            CompositeOp.AND -> trigger.children.all { evaluate(it, snapshot, lastFired, event) }
            CompositeOp.OR -> trigger.children.any { evaluate(it, snapshot, lastFired, event) }
            CompositeOp.NOT -> !evaluate(trigger.children.first(), snapshot, lastFired, event)
        }
    }
}
```

---

## PredicateEvaluator (Simple)

Mini-grammar dengan accessor world snapshot:

```kotlin
class SimplePredicateEvaluator @Inject constructor() {
    
    fun evaluate(expression: String, snapshot: WorldSnapshot): Boolean {
        // Tokenize + parse + evaluate
        val tokens = tokenize(expression)
        val ast = parse(tokens)
        return ast.eval(snapshot)
    }
    
    // Sample accessors:
    private val accessors = mapOf(
        "location.distance_from" to { args: List<Any>, snap: WorldSnapshot ->
            val targetName = args[0] as String
            // resolve target dari memory_store
            val target = memoryStore.getByKey("location.$targetName")
            haversine(snap.location, target) 
        },
        "battery.level" to { _: List<Any>, snap: WorldSnapshot -> snap.battery.level },
        "battery.charging" to { _: List<Any>, snap: WorldSnapshot -> snap.battery.charging },
        "schedule.next_event.starts_in" to { _: List<Any>, snap: WorldSnapshot -> 
            snap.scheduledEvents.firstOrNull()?.let { 
                Duration.between(snap.timestamp, it.start).toMinutes() 
            } ?: Long.MAX_VALUE
        },
        "foreground_app" to { _: List<Any>, snap: WorldSnapshot -> snap.foregroundApp },
        "hour" to { _: List<Any>, snap: WorldSnapshot -> 
            ZonedDateTime.ofInstant(snap.timestamp, ZoneId.of("Asia/Jakarta")).hour 
        },
        "day_of_week" to { _: List<Any>, snap: WorldSnapshot -> 
            ZonedDateTime.ofInstant(snap.timestamp, ZoneId.of("Asia/Jakarta")).dayOfWeek 
        },
    )
}
```

Operators: `==`, `!=`, `<`, `<=`, `>`, `>=`, `AND`, `OR`, `NOT`, `IN`, `NOT IN`, `BETWEEN ... AND`.

Constants: number, string `'kantor'`, list `[MON, TUE, ...]`, unit `1km`, `15min`, `20%`.

---

## Task Template Rendering

`taskTemplate` field di StandingInstruction punya `{{var}}` placeholder yang diganti dengan world snapshot value.

Available vars:
- `{{now}}` — current timestamp formatted
- `{{foreground_app}}` — package
- `{{event.title}}`, `{{event.text}}`, `{{event.package}}` — kalau event-driven
- `{{location}}` — current location name (resolved dari memory)
- `{{battery_level}}`, `{{network_state}}`, etc

Contoh:

```
template: "Balas WA dari {{event.title}}: '{{event.text}}' dengan reply singkat"
event: {title: "Mama", text: "udah pulang belum?"}
rendered: "Balas WA dari Mama: 'udah pulang belum?' dengan reply singkat"
```

```kotlin
fun renderTemplate(template: String, snapshot: WorldSnapshot, event: WorldEvent? = null): String {
    return Regex("\\{\\{(\\w+(?:\\.\\w+)*)\\}\\}").replace(template) { match ->
        val key = match.groupValues[1]
        resolveVar(key, snapshot, event) ?: "[${key}]"
    }
}
```

---

## Guided Form UI (Phase 6)

Editor standing instruction step-by-step:

```
[Step 1: Nama & deskripsi]
┌─────────────────────────────┐
│ Nama:                       │
│ [ Auto-reply Mama         ] │
│                             │
│ Deskripsi:                  │
│ [ Balas WA Mama setiap   ] │
│ [ jam aktif kalau aku    ] │
│ [ tidak di kantor.       ] │
└─────────────────────────────┘

[Step 2: Trigger]
○ Sederhana (1 trigger)
● Kompleks (kombinasi AND/OR)

[Step 3: Trigger 1]
Jenis: ● Time   ○ Event   ○ Predicate
Setiap: 
  [X] Senin [X] Selasa [X] Rabu [X] Kamis [X] Jumat
  [ ] Sabtu [ ] Minggu
Jam: [18:00] - [22:00]

[+ Tambah trigger]

[Step 4: Trigger 2]
Jenis: ○ Time   ○ Event   ● Predicate
Expression: [location.distance_from('kantor') > 1km]

[+ Tambah trigger]

[Step 5: Trigger 3]
Jenis: ○ Time   ● Event   ○ Predicate
Event: [NOTIFICATION_POSTED]
Filter:
  package: [com.whatsapp]
  title regex: [.*Mama.*]

[Step 6: Action]
Task template:
[Balas WA dari Mama dengan reply singkat]

[Step 7: Eksekusi]
Priority: ●Normal ○Urgent
Cooldown: [30] menit (jangan fire lagi dalam window ini)
Max fires per hari: [10]

Tools yang akan dipakai (pre-authorize):
[X] a11y_click (LOW)
[X] a11y_type (MEDIUM)
[X] messaging — HIGH ⚠️ Auto-confirm?
       ○ Tanya tiap fire
       ● Auto-confirm (pre-authorized)

[Step 8: Preview]
Compiled trigger:
  Time + Predicate + Event (AND)
Render task contoh:
  "Balas WA dari Mama dengan reply singkat"

[Simpan]  [Batal]
```

Backend convert form → ComplexTrigger JSON tree.

---

## Standing Instruction Examples

### Example 1: "Setiap jam 7 pagi, bacakan agenda"

```json
{
  "name": "Morning agenda",
  "description": "Baca agenda hari ini saat bangun",
  "trigger": {
    "type": "time",
    "cron": "0 7 * * *"
  },
  "taskTemplate": "Bacakan agenda hari ini dengan singkat",
  "enabled": true,
  "priority": 3,
  "preAuthorizedTools": ["world_get_schedule"]
}
```

### Example 2: "Auto-reply Mama WA"

```json
{
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
  "taskTemplate": "Balas WA dari {{event.title}}: '{{event.text}}' dengan reply singkat",
  "enabled": true,
  "priority": 4,
  "cooldownMs": 1800000,
  "maxFiresPerDay": 20,
  "preAuthorizedTools": ["a11y_click", "a11y_type", "messaging"]
}
```

### Example 3: "Low battery reminder"

```json
{
  "name": "Low battery warning",
  "description": "Ingatkan kalau baterai rendah dan tidak ngecharge",
  "trigger": {
    "type": "composite",
    "op": "AND",
    "children": [
      {"type": "predicate", "expression": "battery.level < 20"},
      {"type": "predicate", "expression": "NOT battery.charging"}
    ]
  },
  "taskTemplate": "Ingatkan user untuk colok charger (baterai {{battery_level}}%)",
  "enabled": true,
  "priority": 5,
  "cooldownMs": 600000,
  "maxFiresPerDay": 5
}
```

---

## Concurrency

Multiple instruction bisa fire simultaneous. TaskManager handle priority + slot allocation.

Anti-runaway:
- Cooldown per instruction
- Max fires per day per instruction
- Global rate limit: max 30 standing-triggered tasks per jam (kalau exceeded, skip + log warning)

---

## Audit Trail

Setiap fire di-log ke `audit_log`:

```kotlin
auditLogger.log(
    actionType = AuditActionType.STANDING_INSTRUCTION_FIRED,
    dataSummary = "Standing '${instruction.name}' fired due to $source. Task created: ${task.id}",
    taskId = task.id,
)
```

Dev UI tampilkan fire history per instruction.

---

## Phase Implementation

**Phase 6** — InitiativeEngine + StandingInstruction entity + TimeTrigger + simple EventTrigger (notif, battery, screen) + simple PredicateEvaluator.

**Phase 6 mid** — GeofenceTrigger + CalendarEventStart + CompositeTrigger.

**Phase 6 polish** — Guided form UI lengkap + preview render.

Detail: [27-phase-6-initiative.md](27-phase-6-initiative.md).

---

## Next Files

- Execution strategy: [18-execution-strategy.md](18-execution-strategy.md)
- Phase 6 detail: [27-phase-6-initiative.md](27-phase-6-initiative.md)
