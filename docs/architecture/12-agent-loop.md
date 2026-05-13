# 12 — Agent Loop

Loop iteratif yang mengubah ChibiClaw dari chatbot-with-tools menjadi true agent. Ini bagian paling foundational dari Phase 1.

---

## Konsep Loop

```
Trigger: user input, standing instruction, atau event
   ↓
Task created
   ↓
[AgentLoop start]
While task.iteration < task.maxIteration AND task.status == RUNNING:
   1. Build context (task + history + world + memory + tools + emotion)
   2. LLM.complete(context) → response
   3. Parse response:
      - Has tool_calls? → dispatch, get results, append steps
      - Emit done(summary)? → task.complete, exit loop
      - Emit await_user(question)? → task.block, exit loop
      - Reasoning only (no action)? → append step, continue
   4. task.iteration++
[AgentLoop end]
   ↓
Post-loop: Response composer + TTS (kalau channel CHAT) atau silent notif (channel STANDING/AUTONOMOUS)
```

---

## Pseudocode Lengkap

```kotlin
class AgentRuntime(
    private val taskRepo: TaskRepository,
    private val memoryRepo: MemoryRepository,
    private val worldObserver: WorldObserver,
    private val emotionDetector: EmotionDetector,
    private val inferenceRouter: InferenceRouter,
    private val toolDispatcher: ToolDispatcher,
    private val auditLogger: AuditLogger,
    private val responseComposer: ResponseComposer,
    private val ttsClient: TtsClient,
) {
    
    suspend fun executeTask(taskId: String) {
        var task = taskRepo.get(taskId) ?: return
        
        if (task.status != TaskStatus.PENDING) {
            return  // already handled
        }
        
        taskRepo.updateStatus(taskId, TaskStatus.PLANNING, Instant.now())
        
        var iteration = 0
        val maxIter = task.maxIteration
        
        loop@ while (iteration < maxIter) {
            // 1. Refresh task state
            task = taskRepo.get(taskId)!!
            if (task.status in setOf(TaskStatus.CANCELLED, TaskStatus.FAILED)) {
                break@loop  // user/system cancelled mid-loop
            }
            
            // 2. Build context
            val context = buildContext(task)
            
            // 3. LLM call
            val adapter = inferenceRouter.selectAdapter(task)
            val response = try {
                adapter.complete(context)
            } catch (e: Exception) {
                taskRepo.appendStep(failedStep(taskId, iteration, e))
                taskRepo.setError(taskId, "LLM call failed: ${e.message}")
                taskRepo.updateStatus(taskId, TaskStatus.FAILED, Instant.now())
                break@loop
            }
            
            // 4. Process response
            when (val outcome = parseLlmResponse(response)) {
                is LlmOutcome.Done -> {
                    val emotionTag = outcome.emotionTag
                    val summary = outcome.summary
                    taskRepo.setResult(taskId, summary, emotionTag)
                    taskRepo.updateStatus(taskId, TaskStatus.COMPLETED, Instant.now())
                    break@loop
                }
                
                is LlmOutcome.AwaitUser -> {
                    taskRepo.setError(taskId, "awaiting user: ${outcome.question}")
                    taskRepo.updateStatus(taskId, TaskStatus.AWAITING_USER, Instant.now())
                    notifyUser(task, outcome.question)
                    break@loop
                }
                
                is LlmOutcome.ToolCalls -> {
                    if (task.status == TaskStatus.PLANNING) {
                        taskRepo.updateStatus(taskId, TaskStatus.RUNNING, Instant.now())
                    }
                    
                    for (call in outcome.calls) {
                        // Inline safety gate (tool decides)
                        if (call.requiresConfirmation && !userPreAuthorized(call)) {
                            val approved = requestConfirmation(task, call)
                            if (!approved) {
                                appendStep(taskId, iteration, outcome.reasoning, call, 
                                          ToolResult.UserDenied)
                                continue  // skip this call, next call atau next iteration
                            }
                        }
                        
                        // Execute
                        val result = toolDispatcher.execute(call)
                        appendStep(taskId, iteration, outcome.reasoning, call, result)
                        auditLogger.log(call, result, taskId)
                        
                        if (result is ToolResult.Error && result.fatal) {
                            taskRepo.setError(taskId, "fatal tool error: ${result.message}")
                            taskRepo.updateStatus(taskId, TaskStatus.FAILED, Instant.now())
                            break@loop
                        }
                    }
                    // Continue loop — LLM observe results di next iteration
                }
                
                is LlmOutcome.Reasoning -> {
                    appendStep(taskId, iteration, outcome.reasoning, null, null)
                    // No action, just thinking. Continue.
                }
                
                is LlmOutcome.Escalate -> {
                    // LLM emit escalate_to_cloud tool
                    val nextAdapter = inferenceRouter.escalate(outcome.target, task)
                    if (nextAdapter == null) {
                        // No more adapter, fall back to error
                        taskRepo.setError(taskId, "escalation requested but no adapter available")
                        taskRepo.updateStatus(taskId, TaskStatus.FAILED, Instant.now())
                        break@loop
                    }
                    inferenceRouter.pinAdapter(taskId, nextAdapter)
                    // Continue loop with new adapter
                }
            }
            
            iteration++
        }
        
        // Post-loop
        val finalTask = taskRepo.get(taskId)!!
        if (iteration >= maxIter && finalTask.status == TaskStatus.RUNNING) {
            taskRepo.setError(taskId, "max iteration reached")
            taskRepo.updateStatus(taskId, TaskStatus.FAILED, Instant.now())
        }
        
        respondToUser(finalTask)
    }
    
    private suspend fun buildContext(task: TaskEntity): AgentPrompt {
        val taskHistory = taskRepo.listSteps(task.id).takeLast(N_STEPS_VISIBLE)
        val recentTasks = taskRepo.recentCompleted(limit = 20)
        val worldSnap = worldObserver.current()
        val memorySnippets = memoryRepo.semanticSearch(
            queryEmbedding = embed(task.goal),
            topK = 5
        )
        val emotionCtx = emotionDetector.currentContext()
        val tools = toolDispatcher.availableTools()
        
        return AgentPrompt(
            systemPrompt = SYSTEM_PROMPT_FUU,
            taskGoal = task.goal,
            taskChannel = task.channel,
            taskHistory = taskHistory.map { it.toContextLine() },
            recentTasks = recentTasks.map { it.toContextLine() },
            worldSnapshot = worldSnap.toContextBlock(),
            relevantMemory = memorySnippets.map { it.toContextLine() },
            emotionSignal = emotionCtx,
            toolCatalog = tools.map { it.toolSpec() },
            personaTraits = PERSONA_FUU_TRAITS,
            iteration = task.iterationCount,
            maxIteration = task.maxIteration,
        )
    }
    
    private suspend fun respondToUser(task: TaskEntity) {
        if (task.channel == TaskChannel.CHAT) {
            val response = responseComposer.compose(
                summary = task.resultSummary ?: task.errorMessage,
                emotionTag = task.emotionTag,
                taskStatus = task.status,
            )
            ttsClient.speak(response)
        } else {
            // STANDING / AUTONOMOUS: silent log, optional notif
            if (task.status == TaskStatus.FAILED) {
                notifyUser(task, "Task gagal: ${task.errorMessage}")
            } else if (task.priority >= 4) {
                notifyUser(task, "Task selesai: ${task.resultSummary}")
            }
            // else: silent
        }
    }
}
```

---

## LLM Response Parsing

LLM Gemma + cloud adapters emit response dengan format yang ChibiClaw expected (constrained decoding kalau memungkinkan, else parsing best-effort).

**Format target:**

```
THOUGHT: [LLM reasoning, free text]

TOOL_CALLS:
[
  {"tool": "intent_open", "args": {"package": "com.spotify.music"}},
  {"tool": "wait", "args": {"seconds": 2}}
]

NEXT: continue | done | await_user | escalate
SUMMARY: [only if NEXT=done]
QUESTION: [only if NEXT=await_user]
EMOTION: [optional tag like "satisfied", "uncertain"]
```

Atau pakai JSON struktural penuh:

```json
{
  "thought": "...",
  "tool_calls": [
    {"tool": "intent_open", "args": {...}}
  ],
  "next": "continue",
  "summary": null,
  "question": null,
  "emotion": "neutral"
}
```

**Parser fallback hierarchy:**
1. Coba parse JSON strict
2. Coba parse JSON yang dibungkus markdown code fence
3. Regex extract dari format teks dengan tag THOUGHT/TOOL_CALLS/NEXT
4. Kalau semua gagal → treat sebagai Reasoning only, kasih hint ke LLM di next iter ("response format wajib JSON")

```kotlin
sealed class LlmOutcome {
    data class Done(val summary: String, val emotionTag: String?) : LlmOutcome()
    data class AwaitUser(val question: String) : LlmOutcome()
    data class ToolCalls(val calls: List<ToolCall>, val reasoning: String) : LlmOutcome()
    data class Reasoning(val reasoning: String) : LlmOutcome()
    data class Escalate(val reason: String, val target: AdapterTarget) : LlmOutcome()
}

fun parseLlmResponse(raw: String): LlmOutcome {
    // 1. Try JSON
    runCatching {
        val json = Json.parseToJsonElement(raw).jsonObject
        return parseJsonOutcome(json)
    }
    
    // 2. Try fenced JSON
    val fenced = Regex("```(?:json)?\\s*(\\{[\\s\\S]+\\})\\s*```").find(raw)?.groupValues?.get(1)
    if (fenced != null) {
        runCatching {
            val json = Json.parseToJsonElement(fenced).jsonObject
            return parseJsonOutcome(json)
        }
    }
    
    // 3. Try tag-based regex extraction
    runCatching {
        return parseTagOutcome(raw)
    }
    
    // 4. Fallback
    return LlmOutcome.Reasoning(raw.take(2000))
}
```

---

## Iteration Limits & Resource

| Parameter | Default | Override per |
|-----------|---------|--------------|
| `task.maxIteration` | 15 | Channel: CHAT=15, STANDING=10, AUTONOMOUS=8 |
| Per-iteration LLM timeout | 60s | Adapter: Gemma=120s (load slow), cloud=30s |
| Per-tool timeout | 30s | Tool spec |
| Total task timeout (wall clock) | 5 menit | task.deadline override |

Saat hit limit:
- `maxIteration`: task.fail("max iteration reached")
- LLM timeout: retry sekali, kalau masih gagal failed
- Tool timeout: tool result Error(TIMEOUT), LLM observe & decide
- Task deadline: task.fail("deadline exceeded"), cancel remaining steps

---

## Self-Correction Without Hardcoded Retry

LLM observe error di tool result. Kalau perlu retry, LLM emit tool call lagi (mungkin dengan variasi parameter atau tool berbeda).

Contoh:
```
Iteration 3:
  THOUGHT: WhatsApp dibuka, sekarang cari kontak Budi
  TOOL_CALLS: [a11y_click(label="Search")]
  
Iteration 4:
  TOOL_RESULT: Error{class: SELECTOR_NOT_FOUND, message: "label 'Search' not visible"}
  THOUGHT: Search button tidak ketemu via a11y. WA blacklist a11y, coba vision_tap
  TOOL_CALLS: [vision_tap(query="search icon top right of WhatsApp chat list")]
  
Iteration 5:
  TOOL_RESULT: Success{coordinates: [x, y], tapped: true}
  THOUGHT: OK tapped. Sekarang type "Budi" untuk filter kontak
  TOOL_CALLS: [a11y_type(text="Budi") OR vision_describe then derive next action]
```

Code tidak punya retry policy hardcoded. LLM yang adapt.

**Tool description hint** sebutkan known limitation: tool `a11y_click` punya field `known_limitations: "fails di TikTok, WhatsApp, Instagram, Tokopedia, Shopee — fallback ke vision_tap"`. LLM baca + decide.

---

## Concurrency: Multi-Task Parallel

AgentRuntime menjalankan multiple task di slot paralel (3-5 default). Coroutine scope tied ke service lifecycle.

```kotlin
class AgentRuntime(/*...*/) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val activeSlots = ConcurrentHashMap<String, Job>()
    private val maxParallel = 5
    
    fun tick() {
        scope.launch {
            cleanupCompletedSlots()
            val freeSlots = maxParallel - activeSlots.size
            if (freeSlots > 0) {
                val runnableTasks = taskRepo.runnable(maxParallel = freeSlots)
                for (task in runnableTasks) {
                    val job = scope.launch { executeTask(task.id) }
                    activeSlots[task.id] = job
                }
            }
        }
    }
}
```

Resource scheduler enforce:
- Mic: kalau ada task ambil mic, task lain yang butuh mic queue
- Screen capture: similar
- TTS output: priority queue, higher priority interrupt

Detail concurrency control: [10-system-architecture.md](10-system-architecture.md#concurrency-model).

---

## Adapter Pinning Per Task

Saat LLM emit `escalate_to_cloud`, adapter target di-pin untuk task yang berjalan. Iterasi berikutnya pakai adapter baru. Kalau cloud adapter gagal (auth expired, rate limited), fallback ke next tier.

```kotlin
class InferenceRouter(
    private val gemma: GemmaAdapter,
    private val gemini: GeminiFreeAdapter,
    private val claude: ClaudeWebAdapter,
    private val gpt: GPTWebAdapter,
) {
    private val taskPinning = ConcurrentHashMap<String, AdapterTarget>()
    
    fun selectAdapter(task: TaskEntity): InferenceAdapter {
        val pinned = taskPinning[task.id]
        return when (pinned) {
            AdapterTarget.GEMINI -> gemini
            AdapterTarget.CLAUDE -> claude
            AdapterTarget.GPT -> gpt
            null, AdapterTarget.GEMMA -> gemma
        }
    }
    
    fun escalate(target: AdapterTarget, task: TaskEntity): InferenceAdapter? {
        val adapter = when (target) {
            AdapterTarget.GEMINI -> gemini.takeIf { it.isAvailable() }
            AdapterTarget.CLAUDE -> claude.takeIf { it.isAvailable() }
            AdapterTarget.GPT -> gpt.takeIf { it.isAvailable() }
            AdapterTarget.GEMMA -> gemma
        }
        if (adapter != null) {
            taskPinning[task.id] = target
        }
        return adapter
    }
}
```

Detail adapter implementation: [14-llm-routing.md](14-llm-routing.md).

---

## Channel-Specific Behavior

| Aspek | CHAT | AUTONOMOUS | STANDING |
|-------|------|------------|----------|
| Source | User direct (voice/text) | Event (notif, app launch) | Time/predicate trigger |
| User notification | Always (overlay expanded) | Silent unless priority high | Silent unless priority high |
| TTS response | Always speak | Skip TTS, log only | Skip TTS, log only |
| Max iteration | 15 | 8 | 10 |
| Approval gate (HIGH severity) | Required, user confirm UI | Pre-authorized list dari standing | Pre-authorized list dari standing |
| Audit verbosity | Standard | Detailed | Detailed |
| Cancellable | Yes (user dismiss bubble) | Yes (user dismiss notif) | Yes (user disable instruction) |
| Resume after crash | Yes, if task incomplete | Yes | Yes |

---

## Recovery After Crash

ChibiService restart (vendor kill / OOM / reboot):

```
1. onCreate() → AgentRuntime.boot()
2. taskRepo.findIncomplete() → list of tasks in PLANNING/RUNNING/BLOCKED status
3. For each incomplete task:
   - Cek age (created_at vs now). > 1 jam = mark failed("stale, restart aborted")
   - Cek last step. Kalau ada tool call yang belum result → mark failed("recovery: tool call dropped")
   - Else: status reset ke PENDING, re-enqueue
4. TaskManager mulai tick loop normal
```

User notified kalau ada task auto-failed via notif "X task gagal setelah service restart, lihat history".

---

## Observability Hooks (untuk debug)

Setiap step di Agent loop emit event via Flow ke debug UI:

```kotlin
sealed class AgentEvent {
    data class LoopStart(val taskId: String) : AgentEvent()
    data class IterationStart(val taskId: String, val iter: Int) : AgentEvent()
    data class ContextBuilt(val taskId: String, val tokensEstimate: Int) : AgentEvent()
    data class LlmCallStart(val taskId: String, val adapter: String) : AgentEvent()
    data class LlmCallEnd(val taskId: String, val latencyMs: Long, val tokensUsed: Int) : AgentEvent()
    data class ToolCallStart(val taskId: String, val tool: String) : AgentEvent()
    data class ToolCallEnd(val taskId: String, val tool: String, val resultStatus: String) : AgentEvent()
    data class IterationEnd(val taskId: String, val iter: Int) : AgentEvent()
    data class LoopEnd(val taskId: String, val finalStatus: TaskStatus) : AgentEvent()
}

class AgentEventBus {
    private val _events = MutableSharedFlow<AgentEvent>(replay = 100)
    val events: SharedFlow<AgentEvent> = _events.asSharedFlow()
    
    suspend fun emit(event: AgentEvent) = _events.emit(event)
}
```

Phase 1 UI consume ini di TaskDetailScreen — tampilkan step-by-step + latency + tokens (debug mode).

---

## Open Questions (Phase 1 perlu putus)

1. **Constrained decoding**: LiteRT-LM 0.11 support llguidance? Kalau iya, force JSON output. Kalau tidak, parsing fallback hierarchy.
2. **Max history depth in context**: 20 steps cukup? Atau dynamic based on token budget?
3. **Concurrent same-tool conflict**: 2 task pakai `vision_tap` sekaligus — MediaProjection multiplex? Single session?
4. **Auto-eval emotion before LLM**: emotion signal di-include semua, atau hanya kalau intensity > threshold (token saving)?
5. **Trace persistence**: AgentEvent ke Room atau ephemeral in-memory only?

Ditangani di Phase 1 spike.

---

## Next Files

- Tool spec lengkap: [13-tool-catalog.md](13-tool-catalog.md)
- LLM routing & adapter detail: [14-llm-routing.md](14-llm-routing.md)
- Phase 1 execution detail: [22-phase-1-agent-core.md](22-phase-1-agent-core.md)
