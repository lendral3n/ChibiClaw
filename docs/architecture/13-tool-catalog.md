# 13 — Tool Catalog

15+ tools dengan spec lengkap. Tools = "tangan" yang LLM pakai. Kode hanya menyediakan implementasi, tidak punya policy di atas tools.

---

## Prinsip Tool Design

1. **Flat catalog**, bukan tier hierarchy hardcoded
2. **Capability metadata jujur** di description — latency, works-on, known-fail
3. **Inline safety property** di tool spec — HIGH severity tool auto-trigger confirmation
4. **Idempotent kalau memungkinkan** — retry safe
5. **Typed result** dengan error class — LLM bisa decode jenis error
6. **Stateless** — tool tidak punya internal state lintas-call. State di Task atau Memory.

---

## Tool Schema

```kotlin
@Serializable
data class ToolSpec(
    val name: String,                    // unique identifier
    val description: String,             // untuk LLM context
    val parameters: ToolParamSchema,     // JSON schema args
    val capability: ToolCapability,      // metadata
    val safety: ToolSafety,
)

@Serializable
data class ToolCapability(
    val latencyMs: IntRange,             // estimated latency
    val worksOn: List<String>,           // "most_apps", "intent_capable_apps", "any_visible_ui", etc
    val knownFail: List<String>,         // "TikTok, WhatsApp, Instagram"
    val requiresPermission: List<String>, // "ACCESSIBILITY_SERVICE", "SHIZUKU", "SYSTEM_ALERT_WINDOW"
    val cost: ToolCost,                  // low/medium/high
)

@Serializable
data class ToolSafety(
    val severity: ToolSeverity,          // LOW / MEDIUM / HIGH
    val reason: String? = null,          // "user data write / external trigger"
    val confirmationPrompt: String? = null,  // dipakai overlay
    val preAuthorizable: Boolean = true, // user bisa pre-authorize untuk standing
)

enum class ToolSeverity { LOW, MEDIUM, HIGH }
enum class ToolCost { LOW, MEDIUM, HIGH }

@Serializable
sealed class ToolCall {
    abstract val callId: String
}

@Serializable
sealed class ToolResult {
    abstract val callId: String
    
    @Serializable @SerialName("success")
    data class Success(override val callId: String, val data: Map<String, JsonElement>) : ToolResult()
    
    @Serializable @SerialName("error")
    data class Error(
        override val callId: String,
        val errorClass: ErrorClass,
        val message: String,
        val fatal: Boolean = false,
    ) : ToolResult()
    
    @Serializable @SerialName("user_denied")
    object UserDenied : ToolResult() { override val callId: String = "" }
    
    @Serializable @SerialName("timeout")
    data class Timeout(override val callId: String, val elapsedMs: Long) : ToolResult()
}

enum class ErrorClass {
    SELECTOR_NOT_FOUND,
    PERMISSION_DENIED,
    TIMEOUT,
    AMBIGUOUS,
    NETWORK_ERROR,
    RATE_LIMITED,
    INVALID_ARGS,
    NOT_AVAILABLE,
    UNKNOWN,
}
```

---

## Tool Registry

```kotlin
@Singleton
class ToolRegistry @Inject constructor(
    private val tools: Map<String, @JvmSuppressWildcards Tool>
) {
    fun list(): List<ToolSpec> = tools.values.map { it.spec }
    fun get(name: String): Tool? = tools[name]
    fun availableFor(context: ToolContext): List<ToolSpec> {
        return tools.values
            .filter { it.isAvailable(context) }
            .map { it.spec }
    }
}

interface Tool {
    val spec: ToolSpec
    fun isAvailable(context: ToolContext): Boolean
    suspend fun execute(call: ToolCall): ToolResult
}
```

---

## Tools Daftar Lengkap

### Group 1: Mobile Control (Action Tools)

#### `intent_open`
Launch app via Intent.

```yaml
spec:
  name: intent_open
  description: |
    Buka app Android lewat Intent. Cepat (~200ms), works hampir semua app.
    Limitations: hanya buka, tidak interact dengan UI dalam.
    
  parameters:
    package: string (e.g. "com.spotify.music")
    activity: string? (optional explicit activity)
    flags: int[]? (Intent flags optional)
  
  capability:
    latencyMs: 150..300
    worksOn: ["most_apps"]
    knownFail: []
    requiresPermission: []
    cost: LOW
  
  safety:
    severity: LOW
    preAuthorizable: true
```

#### `intent_send`
Send broadcast or implicit intent untuk action seperti dial, share text, navigate.

```yaml
parameters:
  action: string (e.g. "android.intent.action.DIAL", "android.intent.action.SEND")
  data: string? (URI atau payload)
  extras: map<string, string>?
  
capability:
  latencyMs: 200..400
  worksOn: ["intent_capable_apps"]
  cost: LOW

safety:
  severity: MEDIUM
  reason: "Triggers external app action (call, share)"
```

#### `a11y_click`
Click element via Accessibility Service.

```yaml
parameters:
  selector: string (label, contentDescription, resource_id, atau xpath-like)
  app_package: string? (filter, optional)
  
capability:
  latencyMs: 200..500
  worksOn: ["accessibility_exposed_apps"]
  knownFail: ["TikTok", "WhatsApp", "Instagram", "Tokopedia", "Shopee", "Banking apps"]
  requiresPermission: ["ACCESSIBILITY_SERVICE"]
  cost: LOW

safety:
  severity: LOW
```

#### `a11y_type`
Type text di field yang focused.

```yaml
parameters:
  text: string
  focused_field_id: string? (untuk verifikasi)
  
capability:
  latencyMs: 100..300
  worksOn: ["accessibility_text_input"]
  knownFail: ["secure input fields (password, OTP)"]
  cost: LOW

safety:
  severity: MEDIUM
  reason: "Types into field, may be sensitive content"
```

#### `a11y_describe_screen`
Get accessibility tree dari foreground app sebagai text representation.

```yaml
parameters:
  max_depth: int = 5
  include_invisible: boolean = false
  
capability:
  latencyMs: 100..400
  worksOn: ["accessibility_exposed_apps"]
  knownFail: ["anti-accessibility apps (TikTok, WA, IG, Shopee)"]
  cost: LOW

safety:
  severity: LOW
```

#### `a11y_scroll`
Scroll vertical/horizontal via accessibility.

```yaml
parameters:
  direction: enum [UP, DOWN, LEFT, RIGHT]
  amount: enum [SHORT, MEDIUM, LONG] = MEDIUM
  
capability:
  latencyMs: 200..600
  worksOn: ["accessibility_scrollable"]
  cost: LOW

safety:
  severity: LOW
```

#### `shizuku_exec`
Execute ADB shell command via Shizuku.

```yaml
parameters:
  command: string (e.g. "am force-stop com.spotify.music")
  timeout_ms: int = 5000
  
capability:
  latencyMs: 50..200
  worksOn: ["any_app_via_adb_capability"]
  knownFail: ["restricted commands need root"]
  requiresPermission: ["SHIZUKU_RUNNING"]
  cost: LOW

safety:
  severity: HIGH
  reason: "ADB-level command, can force-stop / uninstall / grant perm"
  confirmationPrompt: "Jalankan: {command}?"
  preAuthorizable: true
```

#### `shizuku_force_stop`
Convenience wrapper untuk `am force-stop <pkg>`.

```yaml
parameters:
  package: string
  
capability:
  latencyMs: 100..300
  cost: LOW

safety:
  severity: HIGH
  reason: "Force-stops app, user task in app interrupted"
```

#### `shizuku_grant_permission`
Grant runtime permission ke app via `pm grant`.

```yaml
parameters:
  package: string
  permission: string (e.g. "android.permission.CAMERA")
  
capability:
  latencyMs: 100..300
  cost: LOW

safety:
  severity: HIGH
  reason: "Granting permission to other app"
```

#### `vision_tap`
Screenshot + visual grounding + tap koordinat.

```yaml
parameters:
  query: string (deskripsi visual target, e.g. "search icon top right")
  app_package: string? (filter)
  
capability:
  latencyMs: 2500..5000
  worksOn: ["any_visible_ui"]
  knownFail: ["off-screen elements", "ambiguous query"]
  requiresPermission: ["MEDIA_PROJECTION"]
  cost: HIGH

safety:
  severity: MEDIUM
  reason: "Screenshot captured, processed locally by vision model"
```

#### `vision_describe`
Screenshot + LLM-friendly description.

```yaml
parameters:
  query: string ("what's on screen", "extract text from top region")
  region: rect? (optional limit)
  
capability:
  latencyMs: 2000..4500
  worksOn: ["any_visible_ui"]
  cost: HIGH

safety:
  severity: MEDIUM
```

#### `vision_extract_text`
OCR text dari region screenshot.

```yaml
parameters:
  region: rect (x, y, w, h dalam pixel)
  language: string = "ind+eng"
  
capability:
  latencyMs: 1000..2500
  worksOn: ["any_visible_text"]
  cost: MEDIUM

safety:
  severity: LOW
```

#### `messaging`
Kirim SMS atau WA dengan high-severity gate.

```yaml
parameters:
  kind: enum [SMS, WHATSAPP, TELEGRAM]
  recipient: string (phone, contact name, atau chat title)
  text: string
  
capability:
  latencyMs: 500..2000
  worksOn: ["sms_provider", "intent_to_messaging_apps"]
  cost: MEDIUM

safety:
  severity: HIGH
  reason: "Sending external message, user pulsa & komunikasi affected"
  confirmationPrompt: "Kirim {kind} ke {recipient}: \"{text}\"?"
  preAuthorizable: true  # via standing instruction
```

#### `system_action`
Action sistem (flashlight, volume, brightness, wifi, bluetooth, dll).

```yaml
parameters:
  action: enum [FLASHLIGHT_ON, FLASHLIGHT_OFF, VOLUME_UP, VOLUME_DOWN, VOLUME_SET, 
                BRIGHTNESS_SET, WIFI_TOGGLE, BLUETOOTH_TOGGLE, DND_TOGGLE, 
                ROTATE_LOCK, AIRPLANE_MODE]
  value: int? (for VOLUME_SET, BRIGHTNESS_SET)
  
capability:
  latencyMs: 100..400
  worksOn: ["system_settings_API"]
  cost: LOW

safety:
  severity: LOW
```

### Group 2: World Query (Read-Only Tools)

#### `world_get_foreground_app`
```yaml
parameters: {}
capability:
  latencyMs: 50..150
  cost: LOW
safety: { severity: LOW }
returns:
  package: string
  activity: string?
  app_label: string
```

#### `world_get_battery`
```yaml
parameters: {}
returns:
  level: int (0-100)
  charging: boolean
  temperature_c: float
  health: string
```

#### `world_get_location`
```yaml
parameters:
  accuracy: enum [FINE, COARSE] = COARSE
returns:
  lat: double?
  lng: double?
  accuracy_m: float?
  
safety:
  severity: MEDIUM
  reason: "Location access (sensitive)"
  confirmationPrompt: null  # but require persistent consent
```

#### `world_get_schedule`
Calendar events dalam window.

```yaml
parameters:
  hours_ahead: int = 24
returns:
  events: list<{title, start, end, location?}>
```

#### `world_get_notifications`
Last N notifications.

```yaml
parameters:
  limit: int = 10
  package_filter: string?
returns:
  notifications: list<{package, title, text, posted_at}>
```

#### `world_get_installed_apps`
List installed apps.

```yaml
parameters:
  user_apps_only: boolean = true
returns:
  apps: list<{package, label, version_name}>
```

### Group 3: Memory Tools

#### `memory_remember`
Save fact to MemoryStore.

```yaml
parameters:
  category: enum [USER_PROFILE, CONTACT, HABIT, FACT, PREFERENCE]
  key: string
  value: object (JSON-serializable)
  confidence: float = 1.0
  ttl_days: int? = null

capability:
  latencyMs: 100..300
  cost: LOW

safety:
  severity: LOW
```

#### `memory_recall`
Semantic search MemoryStore.

```yaml
parameters:
  query: string
  category: enum? (filter)
  top_k: int = 5
  min_similarity: float = 0.3
returns:
  records: list<{id, category, key, value, similarity}>

capability:
  latencyMs: 200..500
  cost: LOW

safety:
  severity: LOW
```

#### `memory_forget`
Delete specific record.

```yaml
parameters:
  id: string? | key: string?
  
safety:
  severity: MEDIUM
  reason: "Permanent data deletion"
```

#### `memory_list_by_category`
```yaml
parameters:
  category: enum
returns:
  records: list<{id, key, value, last_accessed_at}>
```

### Group 4: Cloud Escalation

#### `escalate_to_cloud`
LLM signal: "task ini butuh adapter lebih kuat".

```yaml
parameters:
  reason: string (mengapa escalate)
  target: enum [GEMINI, CLAUDE, GPT] = GEMINI
  context_hint: string? (apa yang perlu dipertahankan)

capability:
  latencyMs: 100  # ini cuma signal, bukan call cloud beneran (router yang handle)
  cost: LOW (signal); cloud call HIGH

safety:
  severity: MEDIUM
  reason: "Task data sebagian akan transit ke cloud"
  confirmationPrompt: "Kirim task ke {target}? (privasi: data task ringkasan ditransit)"
  preAuthorizable: true  # user bisa toggle "selalu allow cloud"
```

### Group 5: Meta Tools

#### `wait`
Pause N detik. Sleep tidak block thread (coroutine delay).

```yaml
parameters:
  seconds: float (0.1 - 60.0)
  
capability:
  latencyMs: variable
  cost: LOW

safety:
  severity: LOW
```

#### `await_user`
Stop loop, request user response.

```yaml
parameters:
  question: string
  options: list<string>? (multiple choice optional)
  
capability:
  latencyMs: variable (until user respond)
  cost: LOW

safety:
  severity: LOW
```

#### `done`
LLM emit task selesai.

```yaml
parameters:
  summary: string
  emotion: string? (tag for TTS)
  attachments: list<string>? (URLs / references)
```

Catatan: `done` bukan literal tool, tapi parse-time signal di LLM response. Tapi dimasukin di tool catalog supaya LLM tahu pattern.

#### `recall` (alias `memory_recall`)
Semantic search command history dan agent steps lama.

```yaml
parameters:
  query: string
  scope: enum [COMMAND_HISTORY, AGENT_STEPS, BOTH] = BOTH
  limit: int = 5
```

---

## Tool Description ke LLM (Format)

PromptBuilder render tools jadi natural-readable spec untuk LLM context:

```
Available Tools:

[intent_open]
Description: Buka app Android lewat Intent. Cepat (~200ms), works hampir semua app.
Limitations: hanya buka, tidak interact dengan UI dalam.
Args: package (string, e.g. "com.spotify.music")
Latency: ~200ms · Severity: LOW

[a11y_click]
Description: Click element via Accessibility Service.
Args: selector (string), app_package (string, optional)
Known fail: TikTok, WhatsApp, Instagram, Tokopedia, Shopee — fallback to vision_tap
Latency: ~300ms · Severity: LOW

[vision_tap]
Description: Screenshot + visual grounding + tap. Slow but works on any visible UI.
Args: query (string, deskripsi visual target)
Latency: ~3s · Severity: MEDIUM · Cost: HIGH (battery + slow)

[shizuku_exec]
Description: Execute ADB shell command. Requires Shizuku running.
Args: command (string)
Latency: ~150ms · Severity: HIGH · Confirm: required unless pre-authorized

...

[memory_remember]
Description: Save fact to persistent memory.
Args: category (USER_PROFILE | CONTACT | HABIT | FACT | PREFERENCE), key, value
Severity: LOW

[escalate_to_cloud]
Description: Signal that current task needs stronger LLM (Gemini/Claude/GPT).
Use when: Gemma struggling with complex reasoning, long context, or ambiguous task.
Args: reason (string), target (GEMINI | CLAUDE | GPT, default GEMINI)

[done]
Description: Mark task complete. Emit summary + optional emotion.
Args: summary (string), emotion (string, optional)

[await_user]
Description: Stop, ask user for clarification.
Args: question (string), options (list, optional)
```

---

## Inline Safety Gate Logic

Saat LLM emit ToolCall, ToolDispatcher cek `tool.spec.safety.severity`:

```kotlin
suspend fun execute(call: ToolCall): ToolResult {
    val tool = registry.get(call.tool) ?: return ToolResult.Error(...)
    
    if (tool.spec.safety.severity == ToolSeverity.HIGH) {
        val preAuthed = isPreAuthorized(call, task)
        if (!preAuthed) {
            val confirmed = showConfirmationOverlay(call, tool.spec.safety)
            if (!confirmed) {
                return ToolResult.UserDenied
            }
        }
    }
    
    // Timeout wrap
    return withTimeoutOrNull(tool.spec.capability.latencyMs.last * 3L) {
        tool.execute(call)
    } ?: ToolResult.Timeout(call.callId, elapsedMs = tool.spec.capability.latencyMs.last * 3L)
}
```

PreAuthorize: kalau task channel = STANDING, instruction.preAuthorizedTools list mengizinkan tool ini → skip confirmation.

Confirmation overlay (Compose, modal di atas tool dispatch):

```
┌──────────────────────────────────┐
│  🛑 HIGH SEVERITY                │
│                                  │
│  Fuu mau lakukan ini:            │
│                                  │
│  Kirim SMS ke +6281234567890     │
│  "Halo, ini test pesan"          │
│                                  │
│         [Tidak]   [Ya, Lanjut]   │
│                                  │
│  Auto-deny dalam: 28s            │
└──────────────────────────────────┘
```

30 detik timeout default → auto-deny.

---

## Pre-Authorize Workflow

Saat user setup standing instruction, di guided form ada step "Tool permissions":

```
Standing instruction: "Auto-reply Mama WA"
Tools yang akan dipakai:
  ☑ a11y_click (LOW)
  ☑ a11y_type (MEDIUM)
  ☐ messaging (HIGH) — Auto-confirm setiap fire?

(Re-confirm: tool HIGH severity tetap minta confirmasi setiap fire kecuali checkbox dicentang)
```

Stored di `standing_instruction.preAuthorizedTools: List<String>`.

---

## Tool Registration di DI

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object ToolsModule {
    
    @Provides
    @IntoMap
    @StringKey("intent_open")
    fun provideIntentOpen(impl: IntentOpenTool): Tool = impl
    
    @Provides
    @IntoMap
    @StringKey("a11y_click")
    fun provideA11yClick(impl: A11yClickTool): Tool = impl
    
    // ... etc
}

class IntentOpenTool @Inject constructor(
    private val context: Context,
) : Tool {
    override val spec = ToolSpec(
        name = "intent_open",
        description = "...",
        // ...
    )
    
    override fun isAvailable(context: ToolContext) = true
    
    override suspend fun execute(call: ToolCall): ToolResult {
        // implementation
    }
}
```

---

## Per-Phase Tool Availability

Tidak semua tool tersedia di Phase 1. Tool registry filter by phase:

| Tool | Phase introduced |
|------|------------------|
| intent_open, intent_send | 1 |
| system_action | 1 |
| memory_remember, memory_recall, memory_forget, memory_list_by_category | 1 |
| wait, await_user, done, recall | 1 |
| world_get_foreground_app, world_get_battery | 1 |
| a11y_click, a11y_type, a11y_describe_screen, a11y_scroll | 3 |
| shizuku_exec, shizuku_force_stop, shizuku_grant_permission | 3 |
| escalate_to_cloud | 4 |
| vision_tap, vision_describe, vision_extract_text | 5 |
| world_get_schedule, world_get_notifications, world_get_installed_apps, world_get_location | 5 atau 6 |
| messaging | 3 |

ToolRegistry hanya expose tool yang phase-nya sudah selesai. Hindari LLM emit tool yang belum ada (akan ToolResult.Error(NOT_AVAILABLE)).

---

## Future Tools (Phase 7+)

Untuk Phase 7+ (memory maturity, self-correction), tambahan:
- `memory_infer_pattern(scope)` — LLM-side analytic
- `task_create_subtask(goal)` — decompose task
- `task_query_history(filter)` — history query advanced
- `model_query_capability(adapter)` — LLM check adapter capability
- `screen_record_start/stop` — kalau need video evidence

Bisa dipertimbangkan saat Phase relevan.

---

## Next Files

- LLM adapter routing: [14-llm-routing.md](14-llm-routing.md)
- Execution strategy detail: [18-execution-strategy.md](18-execution-strategy.md)
- Memory tools deep: [16-memory-system.md](16-memory-system.md)
