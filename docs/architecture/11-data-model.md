# 11 — Data Model

Semua entity utama, schema Room, serialisasi JSON, dan relasi. Dipakai sebagai blueprint Phase 1 implementation.

---

## Entity Utama

### Task

First-class entity. Bukan ephemeral command. Hidup di Room sampai expired/dihapus.

```kotlin
@Entity(tableName = "task")
data class TaskEntity(
    @PrimaryKey val id: String,                   // UUID v4
    val parentId: String? = null,                 // subtask hierarchy
    val channel: TaskChannel,                     // CHAT / AUTONOMOUS / STANDING
    val goal: String,                             // natural language goal
    val status: TaskStatus,                       // FSM state
    val priority: Int = 3,                        // 1 (lowest) - 5 (highest)
    val createdAt: Instant,
    val startedAt: Instant? = null,
    val completedAt: Instant? = null,
    val deadline: Instant? = null,
    val triggerSource: String? = null,            // FK ke StandingInstruction.id atau event ref
    val iterationCount: Int = 0,
    val maxIteration: Int = 15,
    val resultSummary: String? = null,            // saat completed
    val errorMessage: String? = null,             // saat failed
    val emotionTag: String? = null,               // emotion saat task complete
    val ttlUntil: Instant? = null,                // auto-cleanup setelah deadline + 30 hari
)

enum class TaskChannel { CHAT, AUTONOMOUS, STANDING }

enum class TaskStatus {
    PENDING,        // baru dibuat, belum di-dispatch
    PLANNING,       // LLM lagi reason awal task
    RUNNING,        // agent loop active
    BLOCKED,        // await_user atau dependency
    AWAITING_USER,  // butuh input user (clarification, confirmation)
    COMPLETED,      // sukses
    FAILED,         // gagal final
    CANCELLED,      // user atau system cancel
}
```

**FSM Transitions:**
```
PENDING → PLANNING (saat dispatch)
PLANNING → RUNNING (saat first tool call)
RUNNING → AWAITING_USER (LLM emit await_user)
RUNNING → BLOCKED (await dependency / resource)
RUNNING → COMPLETED (LLM emit done)
RUNNING → FAILED (max iteration / fatal error)
RUNNING → CANCELLED (user cancel)
AWAITING_USER → RUNNING (user provide input)
BLOCKED → RUNNING (dependency resolved)
* → CANCELLED (user dismiss)
```

### AgentStep

Per-iteration history dalam task. Untuk debug, audit, recall.

```kotlin
@Entity(
    tableName = "agent_step",
    foreignKeys = [ForeignKey(
        entity = TaskEntity::class,
        parentColumns = ["id"],
        childColumns = ["task_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("task_id"), Index("timestamp")]
)
data class AgentStepEntity(
    @PrimaryKey val id: String,                   // UUID
    @ColumnInfo(name = "task_id") val taskId: String,
    val stepIndex: Int,
    val timestamp: Instant,
    val thought: String,                          // LLM reasoning teks
    val toolCallJson: String? = null,             // serialized ToolCall
    val toolResultJson: String? = null,           // serialized ToolResult
    val nextIntent: NextIntent,                   // hint kelanjutan
    val adapterUsed: String,                      // "gemma_local" / "gemini_free" / etc
    val tokensUsed: Int? = null,                  // cost tracking
    val latencyMs: Long? = null,                  // perf tracking
)

enum class NextIntent { CONTINUE, DONE, AWAIT_USER, ERROR, ESCALATE }
```

Per task, biasanya 1-15 step. Total step DB bisa puluhan ribu setelah pemakaian rutin → indexed query mandatory.

### MemoryRecord

Persistent memory untuk knowledge tentang user, kontak, habit, fact, preference.

```kotlin
@Entity(
    tableName = "memory_record",
    indices = [
        Index("category"),
        Index("key", unique = true),  // unique per key (overwrite jika sama)
        Index("last_accessed_at")
    ]
)
data class MemoryRecordEntity(
    @PrimaryKey val id: String,                   // UUID
    val category: MemoryCategory,                 // enum 5 kategori
    val key: String,                              // semantic key, e.g. "contact.budi", "habit.morning_routine"
    val valueJson: String,                        // JSON struktur fact
    val embeddingBlob: ByteArray,                 // FloatArray 384-dim → ByteArray serialize
    val createdAt: Instant,
    val lastAccessedAt: Instant,
    val accessCount: Int = 0,
    val confidence: Float = 1.0f,                 // 0.0-1.0, semakin sering re-confirm semakin tinggi
    val ttlUntil: Instant? = null,                // null = permanent
    val sourceTaskId: String? = null,             // task asal yang menambahkan memory ini
)

enum class MemoryCategory {
    USER_PROFILE,    // nama, umur, role, tempat tinggal, dll
    CONTACT,         // kontak penting (Budi=kerja, Mama=keluarga)
    HABIT,           // kebiasaan (bangun jam X, kopi pagi)
    FACT,            // fakta umum yang user pernah kasih tau (kantor di jalan X)
    PREFERENCE,      // pref UI/voice/emotion/notifikasi
}
```

**Contoh value JSON per kategori:**

USER_PROFILE:
```json
{
  "name": "Lendra",
  "preferred_pronoun": "aku",
  "timezone": "Asia/Jakarta",
  "role": "developer",
  "language_primary": "id",
  "language_mix": "en"
}
```

CONTACT:
```json
{
  "name": "Budi Santoso",
  "phone": "+6281234567890",
  "relation": "kerja",
  "preferred_channel": "whatsapp",
  "tags": ["meeting_reguler", "klien"]
}
```

HABIT:
```json
{
  "title": "Morning routine",
  "frequency": "daily",
  "time_window": "06:00-07:30",
  "steps": ["check_email", "olahraga_15min", "kopi"],
  "last_observed": "2026-05-13T06:15:00Z"
}
```

FACT:
```json
{
  "subject": "kantor_alamat",
  "value": "Gedung X, Jalan Sudirman No. 123, Jakarta",
  "verified": true
}
```

PREFERENCE:
```json
{
  "scope": "voice",
  "settings": {
    "speed": "normal",
    "emotion_intensity": "medium",
    "interrupt_allowed": true
  }
}
```

### StandingInstruction

Aturan persistent yang trigger task otomatis.

```kotlin
@Entity(tableName = "standing_instruction", indices = [Index("enabled"), Index("name")])
data class StandingInstructionEntity(
    @PrimaryKey val id: String,                   // UUID
    val name: String,                             // human-readable label
    val description: String,                      // detail dari user
    val triggerJson: String,                      // serialized ComplexTrigger tree
    val taskTemplate: String,                     // goal template dengan {{var}} placeholder
    val enabled: Boolean = true,
    val createdAt: Instant,
    val lastFiredAt: Instant? = null,
    val fireCount: Int = 0,
    val cooldownMs: Long? = null,                 // jangan fire ulang dalam window
    val priority: Int = 3,                        // task created akan inherit priority ini
    val maxFiresPerDay: Int? = null,              // anti-runaway
)
```

**ComplexTrigger** (di-serialize sebagai JSON tree, polymorphic):

```kotlin
@Serializable
sealed class ComplexTrigger {
    @Serializable @SerialName("time")
    data class Time(val cron: String, val timezone: String = "Asia/Jakarta") : ComplexTrigger()
    
    @Serializable @SerialName("event")
    data class Event(
        val eventType: EventType,
        val filter: TriggerFilter,
    ) : ComplexTrigger()
    
    @Serializable @SerialName("predicate")
    data class Predicate(val expression: String) : ComplexTrigger()  // simple expression atau LLM-evaluated
    
    @Serializable @SerialName("composite")
    data class Composite(val op: CompositeOp, val children: List<ComplexTrigger>) : ComplexTrigger()
}

enum class EventType {
    NOTIFICATION_POSTED,
    GEOFENCE_ENTER, GEOFENCE_EXIT,
    APP_LAUNCH, APP_CLOSE,
    BATTERY_LEVEL_CHANGE,
    SCREEN_ON, SCREEN_OFF,
    CALENDAR_EVENT_START,
    USER_IDLE_FOR,
    NETWORK_CONNECTED, NETWORK_DISCONNECTED,
}

@Serializable
data class TriggerFilter(
    val key: String? = null,           // mis. "package" untuk app_launch
    val value: String? = null,         // mis. "com.whatsapp"
    val regex: String? = null,
    val rangeMin: Double? = null,      // mis. battery min 20%
    val rangeMax: Double? = null,
)

enum class CompositeOp { AND, OR, NOT }
```

**Contoh trigger ber-cascade:**

"Antara 18-22 + bukan di kantor + notif WA dari Mama":
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

`taskTemplate`: "Balas WA dari Mama dengan ✓"

### AuditLog

Compliance + observability. Setiap action yang affect data user atau eksternal state.

```kotlin
@Entity(tableName = "audit_log", indices = [Index("timestamp"), Index("action_type"), Index("ttl_until")])
data class AuditLogEntity(
    @PrimaryKey val id: String,
    val timestamp: Instant,
    val actionType: AuditActionType,
    val taskId: String? = null,                   // kalau dari task
    val toolName: String? = null,                 // tool yang dipanggil
    val dataSummary: String,                      // ringkasan data, BUKAN raw (PII protection)
    val cloudDestination: String? = null,         // null kalau local, else "gemini_free" / "claude_web" / etc
    val userConsentState: String,                 // snapshot consent saat aksi
    val resultStatus: AuditResultStatus,
    val ttlUntil: Instant,                        // 90 hari default
)

enum class AuditActionType {
    LLM_CALL_LOCAL, LLM_CALL_CLOUD,
    TOOL_EXECUTED,
    DATA_READ_CONTACT, DATA_READ_LOCATION, DATA_READ_SMS, DATA_READ_NOTIFICATION,
    DATA_WRITE_MESSAGING, DATA_WRITE_FILE,
    MIC_ACTIVATED, MIC_DEACTIVATED,
    SCREEN_CAPTURED,
    SHIZUKU_EXEC,
    MEMORY_WRITE, MEMORY_READ,
    CLOUD_AUTH_SETUP, CLOUD_AUTH_REVOKED,
    EXPORT_DATA, ERASE_DATA,
}

enum class AuditResultStatus { SUCCESS, FAILED, BLOCKED_BY_GATE, USER_DENIED }
```

WorkManager periodic worker (daily) untuk auto-cleanup `ttl_until < now`.

### CommandHistory (legacy, possibly merge ke Task)

```kotlin
@Entity(tableName = "command_history")
data class CommandHistoryEntity(
    @PrimaryKey val id: String,
    val command: String,                          // raw text input
    val taskId: String,                           // FK ke task
    val createdAt: Instant,
    val source: CommandSource,                    // VOICE / TEXT / TRIGGER
)

enum class CommandSource { VOICE, TEXT, TRIGGER }
```

**Note:** dapat di-merge ke Task entity dengan field tambahan, tapi historikal ada di v3 schema. Phase 1 putuskan: keep separate atau merge.

### ModelConfig (key-value store ringan)

Untuk runtime config yang mutable (adapter quota tracker, last-used adapter, dll).

```kotlin
@Entity(tableName = "model_config", primaryKeys = ["key"])
data class ModelConfigEntity(
    val key: String,                              // e.g. "gemini_free.quota_used_today"
    val value: String,                            // JSON string, di-deserialize per use case
    val updatedAt: Instant,
    val ttlUntil: Instant? = null,
)
```

Contoh entries:
- `gemini_free.quota_used_today` → `{"count": 234, "reset_at": "2026-05-14T00:00:00Z"}`
- `claude_web.session_status` → `{"valid_until": "...", "rate_limit_remaining": ...}`
- `preferred_adapter` → `"gemma_local"` (user pref)
- `wake_word_enabled` → `"false"` (skip MVP per ADR-006)
- `cloud_mode` → `"opt_in"` atau `"full_local"` atau `"hybrid"`

---

## Repository Pattern

```kotlin
interface TaskRepository {
    suspend fun create(task: TaskEntity): String
    suspend fun get(id: String): TaskEntity?
    fun observe(id: String): Flow<TaskEntity?>
    fun observeByStatus(status: TaskStatus): Flow<List<TaskEntity>>
    fun observeByChannel(channel: TaskChannel): Flow<List<TaskEntity>>
    suspend fun updateStatus(id: String, status: TaskStatus, timestamp: Instant)
    suspend fun appendStep(step: AgentStepEntity)
    suspend fun listSteps(taskId: String): List<AgentStepEntity>
    suspend fun setResult(id: String, summary: String, emotionTag: String?)
    suspend fun setError(id: String, error: String)
    suspend fun runnable(maxParallel: Int): List<TaskEntity>  // priority queue
    suspend fun cleanupExpired()
}

interface MemoryRepository {
    suspend fun upsert(record: MemoryRecordEntity)
    suspend fun get(id: String): MemoryRecordEntity?
    suspend fun getByKey(key: String): MemoryRecordEntity?
    suspend fun listByCategory(category: MemoryCategory): List<MemoryRecordEntity>
    suspend fun semanticSearch(queryEmbedding: FloatArray, topK: Int, category: MemoryCategory? = null): List<MemoryRecordEntity>
    suspend fun delete(id: String)
    suspend fun cleanup()  // TTL-based + LRU
}

interface StandingInstructionRepository { ... }
interface AuditRepository { ... }
interface ModelConfigRepository { ... }
```

DAO via Room generated. Repository wrap DAO + business logic (TTL cleanup, embedding compute, dll).

---

## Database Schema Versioning

```
v1 (Phase 1): task + agent_step + memory_record + model_config + audit_log
v2 (Phase 6): + standing_instruction
v3 (Phase 8): + task_dependency (foreign key for blocked-on)
```

Migrations explicit (no auto). Room migrate dengan `@Migration` rules.

---

## Serialisasi JSON

Pakai `kotlinx.serialization` semua. Polymorphic untuk:
- `ComplexTrigger` (sealed class)
- `ToolCall` + `ToolResult` (different per tool, common interface)

```kotlin
val agentJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    serializersModule = SerializersModule {
        polymorphic(ComplexTrigger::class) {
            subclass(ComplexTrigger.Time::class)
            subclass(ComplexTrigger.Event::class)
            subclass(ComplexTrigger.Predicate::class)
            subclass(ComplexTrigger.Composite::class)
        }
    }
}
```

---

## Encryption

Room database file di `/data/data/com.chibiclaw/databases/chibiclaw.db`.

SQLCipher wrap:
```kotlin
val passphrase = SecureRandom.getInstanceStrong().nextBytes(32)
val passphraseBytes = SQLiteDatabase.getBytes(passphrase)
val supportFactory = SupportFactory(passphraseBytes)

Room.databaseBuilder(context, AppDatabase::class.java, "chibiclaw.db")
    .openHelperFactory(supportFactory)
    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
    .build()
```

Passphrase store di EncryptedSharedPreferences (Android Keystore-backed).

---

## Embedding Blob Format

```kotlin
fun FloatArray.toEmbeddingBlob(): ByteArray {
    val buffer = ByteBuffer.allocate(this.size * Float.SIZE_BYTES)
        .order(ByteOrder.LITTLE_ENDIAN)
    this.forEach { buffer.putFloat(it) }
    return buffer.array()
}

fun ByteArray.toEmbeddingArray(): FloatArray {
    val buffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
    return FloatArray(this.size / Float.SIZE_BYTES) { buffer.float }
}
```

384-dim float32 = 1536 bytes per record. 10K record = 15MB. Acceptable.

---

## TTL & Cleanup Strategy

Daily WorkManager job:

| Table | Default TTL | Cleanup Policy |
|-------|-------------|----------------|
| `audit_log` | 90 hari | `delete from audit_log where ttl_until < now()` |
| `task` (completed/failed) | 30 hari setelah complete | `delete from task where status in (COMPLETED, FAILED) and ttl_until < now()` |
| `agent_step` | CASCADE dari task | auto via foreign key |
| `memory_record` | null (permanent) tapi LRU evict kalau >5000 | `delete top 100 order by last_accessed_at asc when count > 5000` |
| `model_config` | null kecuali ttl_until set | per-row |
| `standing_instruction` | null (manual delete user) | tidak auto |
| `command_history` | 90 hari | similar to audit_log |

User bisa override via Settings (longer retention, shorter, atau export-before-delete).

---

## Export / Erase Data

User-demand actions:

**Export ALL** (`Settings → Privacy → Export`):
- Generate JSON / CSV semua tabel
- Anonymize cloud_destination jadi "cloud" kalau user mau
- Compressed ZIP, save ke Download/

**Erase ALL** (`Settings → Privacy → Erase All Data`):
- `room.clearAllTables()` semua tabel
- ElevenLabs voice ID retain (user property, not data)
- Cloud session token via WebView → revoke + clear cookie
- EncryptedSharedPreferences clear
- Restart Service fresh state

Both action di-log ke `audit_log` (sebelum erase, append entry final).

---

## Next Files

- Agent loop iteration logic: [12-agent-loop.md](12-agent-loop.md)
- Tool spec lengkap dengan capability metadata: [13-tool-catalog.md](13-tool-catalog.md)
- Memory system detail: [16-memory-system.md](16-memory-system.md)
- Compliance + audit detail: [19-compliance-privacy.md](19-compliance-privacy.md)
