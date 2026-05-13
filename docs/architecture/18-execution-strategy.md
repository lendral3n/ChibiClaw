# 18 — Execution Strategy

LLM-driven tool selection. Tidak ada tier hierarchy hardcoded. Tool catalog flat dengan capability metadata jujur. LLM yang putus urutan.

---

## Filosofi

Pre-ChibiClaw v4 (yang ditolak):
```
fun execute(action: Action) {
    when {
        canIntent(action) -> tier1Intent(action)
        canA11y(action) -> tier3A11y(action)
        else -> tier5Vision(action)
    }
}
```

Itu code yang putus. Bukan LLM. Salah.

ChibiClaw v4 (LLM-centric):
```
LLM context include semua tool dengan capability metadata
LLM emit ToolCall (yang dia pilih)
ToolDispatcher execute apa adanya
Result kembali ke LLM
LLM observe → next action
```

Tidak ada `if-else` di code yang putus tool mana yang dipakai.

---

## Tool Description sebagai "Otak"

Setiap tool punya description yang LLM baca tiap context build. Description = jujur tentang kemampuan + limitasi.

Contoh:

```yaml
intent_open:
  description: |
    Buka app Android lewat Intent. Cepat (~200ms), works hampir semua app.
    Limitations: hanya buka, tidak interact dengan UI dalam app.
    Tidak butuh permission khusus.
  ...

a11y_click:
  description: |
    Click element di app via Accessibility Service.
    Latency: ~300ms.
    Works: app yang expose accessibility tree (kebanyakan app productivity).
    Known fail: TikTok, WhatsApp, Instagram, Tokopedia, Shopee, banking apps —
      mereka block accessibility automation. Fallback ke vision_tap untuk app ini.
    Butuh: ACCESSIBILITY_SERVICE permission.
  ...

vision_tap:
  description: |
    Screenshot + visual grounding + tap koordinat.
    Slow (~3-5 detik) dan high cost (battery, screenshot processing).
    Works: app apapun yang punya UI visible.
    Use case: fallback saat a11y_click fail di app blacklist accessibility.
    Butuh: MEDIA_PROJECTION permission (sekali setup).
  ...

shizuku_exec:
  description: |
    Execute ADB shell command via Shizuku service.
    Latency: ~150ms.
    Powerful: bisa force-stop app, grant permission, install/uninstall, dumpsys.
    Limitations: tidak bisa root commands (perlu Sui mod).
    Butuh: Shizuku service running (user setup via wizard).
    Severity: HIGH — selalu konfirmasi user kecuali pre-authorized.
  ...
```

LLM baca → decide. Tanpa kode policy.

---

## Self-Correction Pattern

LLM iterate. Setiap tool result jadi observation. Kalau error, LLM adapt strategi.

Contoh sequence "kirim WA ke Budi":

```
Iter 1:
  THOUGHT: User mau kirim WA ke Budi. Aku buka WA dulu.
  TOOL: intent_open(package="com.whatsapp")
  RESULT: Success (WhatsApp dibuka)

Iter 2:
  THOUGHT: WhatsApp terbuka. Cari kontak Budi via accessibility.
  TOOL: a11y_click(selector="Search")
  RESULT: Error{class: SELECTOR_NOT_FOUND}
  
Iter 3:
  THOUGHT: Hmm, a11y_click gagal. WA memang di blacklist accessibility. 
           Coba vision_tap.
  TOOL: vision_tap(query="search icon di pojok kanan atas WhatsApp")
  RESULT: Success (tapped coordinates (x, y))

Iter 4:
  THOUGHT: OK search opened. Type "Budi"
  TOOL: a11y_type(text="Budi")  // input field generic biasanya a11y-friendly
  RESULT: Success
  
Iter 5:
  THOUGHT: Lihat hasil pencarian. Screenshot describe.
  TOOL: vision_describe(query="kontak hasil pencarian")
  RESULT: Success{data: "Top result: 'Budi Santoso', second: 'Budi Klien'"}
  
Iter 6:
  THOUGHT: Ada 2 Budi. Cek memory siapa yang dimaksud user.
  TOOL: memory_recall(query="Budi", category="CONTACT")
  RESULT: Success{records: [{key: "contact.budi", value: {name: "Budi Santoso", relation: "kerja"}}]}
  
Iter 7:
  THOUGHT: Memory bilang Budi = Budi Santoso (kerja). Tap yang pertama.
  TOOL: vision_tap(query="kontak Budi Santoso paling atas")
  RESULT: Success
  
Iter 8:
  THOUGHT: Chat Budi terbuka. Type pesan.
  TOOL: a11y_type(text="Bisa meeting jam 3 sore?")
  RESULT: Success
  
Iter 9:
  THOUGHT: Pesan terketik. Sekarang kirim. WA send button biasanya bottom right.
  TOOL: vision_tap(query="tombol kirim panah hijau pojok kanan bawah")
  RESULT: Success
  
Iter 10:
  THOUGHT: Done.
  EMIT: done(summary="Pesan terkirim ke Budi Santoso", emotion="satisfied")
```

LLM yang strategi. Code cuma execute + return result. Tidak ada "if a11y fail → otomatis vision" di code. LLM yang decide via observation.

---

## Inline Safety Gate

Tools dengan severity HIGH wajib confirm sebelum execute, kecuali pre-authorized.

```kotlin
suspend fun execute(call: ToolCall, task: TaskEntity): ToolResult {
    val tool = registry.get(call.tool) ?: return ToolResult.Error(...)
    
    // Check inline safety
    if (tool.spec.safety.severity == ToolSeverity.HIGH) {
        val preAuthed = isPreAuthorizedForTask(call, task)
        if (!preAuthed) {
            val confirmation = showConfirmationOverlay(call, tool)
            if (!confirmation.approved) {
                return ToolResult.UserDenied(callId = call.callId)
            }
        }
    }
    
    // Execute
    return withTimeout(tool.spec.capability.latencyMs.last * 3L) {
        tool.execute(call)
    }
}

fun isPreAuthorizedForTask(call: ToolCall, task: TaskEntity): Boolean {
    // CHAT channel: never pre-authorize HIGH (always ask user di moment)
    if (task.channel == TaskChannel.CHAT) return false
    
    // STANDING channel: check instruction.preAuthorizedTools
    if (task.channel == TaskChannel.STANDING && task.triggerSource != null) {
        val instruction = instructionRepo.get(task.triggerSource)
        return call.tool in (instruction?.preAuthorizedTools ?: emptyList())
    }
    
    return false
}
```

Pre-auth logic:
- CHAT (user inisiate via voice/text) → selalu konfirmasi HIGH severity (user expect responsiveness, jadi confirm modal cepat)
- STANDING (instruction fire otomatis) → instruction.preAuthorizedTools menentukan tool mana yang sudah disetujui upfront
- AUTONOMOUS (event-driven trigger) → similar STANDING, instruction.preAuthorizedTools

User di guided form bisa pilih: "Auto-confirm setiap fire" untuk per-tool — itu yang masuk preAuthorizedTools.

---

## Tool Availability Check

Tidak semua tool selalu available. Check before listing ke LLM:

```kotlin
class ToolContext(
    val a11yEnabled: Boolean,
    val shizukuRunning: Boolean,
    val mediaProjectionGranted: Boolean,
    val notificationListenerEnabled: Boolean,
    val networkOnline: Boolean,
    val cloudAdapters: Map<String, Boolean>,
)

class ToolRegistry @Inject constructor(
    private val context: ToolContextProvider,
) {
    fun availableNow(): List<ToolSpec> {
        val ctx = context.current()
        return allTools.filter { tool ->
            tool.spec.capability.requiresPermission.all { perm ->
                when (perm) {
                    "ACCESSIBILITY_SERVICE" -> ctx.a11yEnabled
                    "SHIZUKU" -> ctx.shizukuRunning
                    "MEDIA_PROJECTION" -> ctx.mediaProjectionGranted
                    "NOTIFICATION_LISTENER" -> ctx.notificationListenerEnabled
                    "NETWORK" -> ctx.networkOnline
                    else -> true
                }
            }
        }.map { it.spec }
    }
}
```

LLM cuma lihat tool yang available. Kalau user belum setup Shizuku, `shizuku_exec` tidak ada di catalog → LLM tidak emit panggil itu.

User onboarding wizard tampilkan status setup per permission, kasih opsi enable.

---

## Tool Result Schema

```kotlin
@Serializable
sealed class ToolResult {
    abstract val callId: String
    
    @Serializable @SerialName("success")
    data class Success(
        override val callId: String,
        val data: Map<String, JsonElement> = emptyMap(),
    ) : ToolResult()
    
    @Serializable @SerialName("error")
    data class Error(
        override val callId: String,
        val errorClass: ErrorClass,
        val message: String,
        val fatal: Boolean = false,
        val recoveryHint: String? = null,  // optional, LLM hint
    ) : ToolResult()
    
    @Serializable @SerialName("timeout")
    data class Timeout(
        override val callId: String,
        val elapsedMs: Long,
    ) : ToolResult()
    
    @Serializable @SerialName("user_denied")
    data class UserDenied(override val callId: String) : ToolResult()
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

`recoveryHint` optional. Tool implementation bisa kasih hint ke LLM untuk next strategi:

```kotlin
ToolResult.Error(
    callId = call.callId,
    errorClass = ErrorClass.SELECTOR_NOT_FOUND,
    message = "Label 'Search' not found in accessibility tree",
    recoveryHint = "Try vision_tap with visual description, or describe_screen to get current layout",
)
```

LLM observe → adapt. Bukan code yang auto-retry pakai vision.

---

## Confidence vs Action

LLM tidak punya field "confidence" explicit di response. Tapi LLM bisa reason: "aku tidak yakin tool mana yang akan jalan, coba a11y dulu, kalau gagal vision".

Itu strategy LLM bisa expose di THOUGHT field. ChibiClaw tidak parse "confidence" untuk routing — semua keputusan dari LLM.

Kalau LLM mau ask user, dia emit `await_user(question="Maksud Budi yang mana? Budi Santoso atau Budi Klien?")`.

---

## Parallel vs Sequential Tool Calls

Single response LLM bisa emit multiple tool calls:

```json
{
  "thought": "Aku perlu cek baterai + lokasi + schedule paralel",
  "tool_calls": [
    {"tool": "world_get_battery"},
    {"tool": "world_get_location"},
    {"tool": "world_get_schedule", "args": {"hours_ahead": 6}}
  ],
  "next": "continue"
}
```

ToolDispatcher execute paralel (kalau tidak ada konflik resource), aggregate results, return ke LLM next iteration.

```kotlin
suspend fun executeAll(calls: List<ToolCall>): List<ToolResult> = coroutineScope {
    calls.map { call ->
        async { execute(call) }
    }.awaitAll()
}
```

Kalau ada conflict (mis. 2 calls butuh mic), serialize:

```kotlin
fun groupByConflict(calls: List<ToolCall>): List<List<ToolCall>> {
    // Group calls yang share resource lock
    // Mic-using calls: 1 grup serial. Mic-free calls: 1 grup parallel.
}
```

---

## Adapter-Driven Tool Constraint

Beberapa adapter punya constraint tool support:

| Adapter | Constraints |
|---------|-------------|
| Gemma local | Support tool calling via JSON output, no native tool API |
| Gemini free | Native tool calling, support vision tool natively |
| Claude web | No native tool API (reverse-engineered web), mock via prompt format |
| GPT web | Same as Claude web |

Saat adapter berbeda dipakai (escalation), prompt format berubah, tapi tool catalog visible sama. LLM yang interpret.

---

## Performance Considerations

| Tool | Avg Latency | Strategy |
|------|-------------|----------|
| intent_open | 200ms | Fast, no special concern |
| a11y_click | 300ms | Cache last screen state untuk faster decision |
| vision_tap | 3-5s | Cache last screenshot, reuse kalau scene tidak berubah |
| shizuku_exec | 150ms | Pre-warm AIDL connection |
| memory_recall | 200ms | Embedding cache untuk query yang sama |

Caching layer di tool implementation, bukan di dispatcher.

---

## Concurrency Constraints

| Resource | Lock |
|----------|------|
| Mic (AudioRecord) | Single global mutex |
| MediaProjection (screenshot) | Single global mutex |
| TTS audio out (AudioTrack) | Single global mutex |
| Shizuku binder | Pool, 3 concurrent max |
| Cloud LLM call per adapter | Rate-limited, sequential by default |

Coordinator di AgentRuntime, bukan di tool individual.

---

## Tools Roadmap by Phase

| Phase | Tools added |
|-------|-------------|
| 1 | intent_open, intent_send, system_action, world_get_battery, world_get_foreground_app, memory_*, wait, await_user, done |
| 2 | (no new tools, voice pipeline only) |
| 3 | a11y_*, shizuku_*, messaging, world_get_notifications |
| 4 | escalate_to_cloud |
| 5 | vision_tap, vision_describe, vision_extract_text, world_get_installed_apps, world_get_location, world_get_schedule |
| 6 | (no new tools, initiative engine only) |
| 7 | memory_infer_pattern, task_create_subtask |
| 8 | (no new tools, self-correction infrastructure) |

---

## Next Files

- Compliance & privacy: [19-compliance-privacy.md](19-compliance-privacy.md)
- Phase 3 detail: [24-phase-3-tools-mid.md](24-phase-3-tools-mid.md)
- Phase 5 detail: [26-phase-5-vision.md](26-phase-5-vision.md)
