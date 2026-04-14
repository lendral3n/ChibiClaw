package com.chibiclaw.core

import android.util.Log
import com.chibiclaw.ai.ChibiClawTools
import com.chibiclaw.ai.ContextAssembler
import com.chibiclaw.ai.GemmaEngineManager
import com.chibiclaw.ai.GemmaInference
import com.chibiclaw.ai.ModelRouter
import com.chibiclaw.ai.ModelTier
import com.chibiclaw.ai.TextActionParser
import com.chibiclaw.debug.DevLogger
import com.chibiclaw.executor.AskUserAction
import com.chibiclaw.executor.ExecutionRouter
import com.chibiclaw.executor.IntentAction
import com.chibiclaw.executor.KillSwitch
import com.chibiclaw.executor.LaunchAppAction
import com.chibiclaw.executor.ReportAction
import com.chibiclaw.executor.SetAlarmAction
import com.chibiclaw.executor.StepRunner
import com.chibiclaw.executor.SystemControlAction
import com.chibiclaw.executor.UiInteractAction
import com.chibiclaw.gateway.CommandGateway
import com.chibiclaw.memory.MemoryManager
import com.chibiclaw.safety.ApprovalGate
import com.chibiclaw.skills.SkillRegistry
import com.chibiclaw.safety.ApprovalPolicy
import com.chibiclaw.safety.ConfirmationOverlay
import com.chibiclaw.state.ChibiState
import com.chibiclaw.state.ChibiStateMachine
import com.chibiclaw.ai.EngineState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChibiOrchestrator @Inject constructor(
    private val commandGateway: CommandGateway,
    private val approvalGate: ApprovalGate,
    private val contextAssembler: ContextAssembler,
    private val gemmaEngineManager: GemmaEngineManager,
    private val gemmaInference: GemmaInference,
    private val executionRouter: ExecutionRouter,
    private val stateMachine: ChibiStateMachine,
    private val memoryManager: MemoryManager,
    private val killSwitch: KillSwitch,
    private val confirmationOverlay: ConfirmationOverlay,
    private val modelRouter: ModelRouter,
    private val fastPathMatcher: FastPathMatcher,
    private val textActionParser: TextActionParser,
    private val skillRegistry: SkillRegistry,
    private val stepRunner: StepRunner,
    private val devLogger: DevLogger
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _streamingChunk = MutableSharedFlow<String>(extraBufferCapacity = 128)
    val streamingChunk: SharedFlow<String> = _streamingChunk.asSharedFlow()

    /**
     * Re-exposed [StepRunner.stepLog] so Dashboard / FloatingOverlay / DevConsole
     * can subscribe to real-time step updates without reaching into the
     * executor package. Emits a [StepRunner.StepLog] every time a step starts,
     * completes, or errors out during command processing.
     */
    val stepLog: SharedFlow<StepRunner.StepLog> = stepRunner.stepLog

    // Tracks whether a tool already pushed a user-visible message this turn, so
    // we can suppress the "Selesai." fallback. MUST be instance-level (not a
    // local var) because ChibiClawTools is constructed ONCE and then pinned
    // inside a persistent Conversation — the lambda can only see `this.field`
    // fresh state, not per-command locals.
    @Volatile private var toolSpoke = false

    // Tier of the current command's routing decision — snapshotted per-turn
    // so the tools lambda (which runs mid-inference) can stamp app-usage
    // records with the correct tier.
    @Volatile private var currentTier: Int = 4

    // ChibiClawTools is built lazily ONCE and reused for the entire session.
    // LiteRT-LM caches the ToolSet inside the Conversation at create time, so
    // swapping instances per-command would have no effect — the first one wins.
    private val chibiTools: ChibiClawTools by lazy {
        ChibiClawTools(skillRegistry = skillRegistry) { action ->
            devLogger.i("TOOL", "Gemma called: ${action::class.simpleName} → $action")
            when (action) {
                // Gemma is asking the user a question — show it in chat as Fuu's bubble.
                is AskUserAction -> {
                    _messages.update { it + ChatMessage(action.question, isUser = false) }
                    toolSpoke = true
                }
                // Final report from Gemma — show the message verbatim.
                is ReportAction -> {
                    _messages.update { it + ChatMessage(action.message, isUser = false) }
                    toolSpoke = true
                }
                else -> {}
            }
            val result = executionRouter.handle(action)
            // P3.3 — record per-app success/failure so ModelRouter can learn
            // which apps are reliable. We derive a "target package" for each
            // action type where it's meaningful — explicit for LaunchApp
            // (AppLauncher echoes the resolved package in its result) and
            // by convention for the others (system defaults).
            recordPatternFor(action, result)
            result
        }
    }

    /**
     * Derives the effective target package for [action] and records the
     * success/failure in [MemoryManager]. Called from the tools lambda so
     * every turn contributes to app-level learning.
     *
     * - LaunchAppAction → parsed from "launch_success: <pkg>"
     * - IntentAction     → explicit packageName if provided, else unclassified
     * - UiInteractAction → "ui.accessibility" (grouped bucket for UI tasks)
     * - SetAlarmAction   → "com.google.android.deskclock" (AlarmClock default)
     * - SystemControlAction → "system.<target>" bucket (not a real package)
     * - Others           → skipped (no app signal)
     */
    private fun recordPatternFor(action: Any, result: String) {
        val success: Boolean
        val pkg: String? = when (action) {
            is LaunchAppAction -> {
                success = result.startsWith("launch_success:")
                result.substringAfter("launch_success: ", "").trim().takeIf { it.isNotEmpty() }
            }
            is IntentAction -> {
                success = result.startsWith("intent_success")
                action.packageName.takeIf { it.isNotBlank() }
                    ?: intentActionBucket(action.action)
            }
            is UiInteractAction -> {
                success = !result.startsWith("ui_error") && !result.startsWith("error")
                "ui.accessibility"
            }
            is SetAlarmAction -> {
                success = result.startsWith("alarm_set")
                "com.google.android.deskclock"
            }
            is SystemControlAction -> {
                success = result.startsWith("flashlight_on") ||
                    result.startsWith("flashlight_off") ||
                    result.startsWith("volume_set") ||
                    result.startsWith("brightness_set")
                "system.${action.target.lowercase()}"
            }
            else -> {
                success = true
                null
            }
        }
        if (pkg != null) {
            scope.launch {
                runCatching {
                    memoryManager.recordAppUsage(pkg, currentTier, success)
                }.onFailure {
                    devLogger.w("MEMORY", "recordAppUsage($pkg) failed: ${it.message}")
                }
            }
        }
    }

    /** Groups bare Intent actions into coarse buckets for pattern learning. */
    private fun intentActionBucket(intentAction: String): String = when {
        intentAction.contains("CALL") -> "intent.call"
        intentAction.contains("SENDTO") || intentAction.contains("SEND") -> "intent.send"
        intentAction.contains("VIEW") -> "intent.view"
        intentAction.contains("WIFI") -> "system.wifi"
        intentAction.contains("BLUETOOTH") -> "system.bluetooth"
        else -> "intent.other"
    }

    fun start() {
        scope.launch {
            Log.d(TAG, "Orchestrator started, waiting for commands...")
            while (true) {
                val request = commandGateway.nextCommand()
                processCommand(request.rawText)
            }
        }
    }

    private suspend fun processCommand(command: String) {
        Log.d(TAG, "Processing: $command")
        devLogger.i("ORCHESTRATOR", "▶ Command: \"$command\"")

        // Emit user message to UI immediately
        _messages.update { it + ChatMessage(command, isUser = true) }

        // Reset step counter for this turn. Every phase below calls
        // stepRunner.startStep / completeStep so subscribers (Dashboard,
        // FloatingOverlay, DevConsole) can render real-time progress.
        stepRunner.reset()
        stepRunner.startStep("Approval gate: \"${command.take(40)}\"")

        // 1. Approval Gate
        val approval = approvalGate.check(command)

        devLogger.i("APPROVAL", "Policy=${approval.policy} Severity=${approval.severity}")

        if (approval.policy == ApprovalPolicy.DENY) {
            Log.w(TAG, "Command BLOCKED: $command")
            devLogger.w("APPROVAL", "BLOCKED — command rejected by safety gate")
            stepRunner.completeStep("Gate: BLOCKED (${approval.severity})", success = false)
            _messages.update { it + ChatMessage("Perintah diblokir karena terdeteksi berbahaya.", isUser = false) }
            memoryManager.saveCommand(command, "blocked", "BLOCKED", approval.severity.name)
            return
        }
        if (approval.policy == ApprovalPolicy.ASK) {
            stateMachine.transition(ChibiState.WAITING_USER, "HIGH severity — needs confirmation")
            devLogger.w("APPROVAL", "HIGH severity — showing ConfirmationOverlay")
            stepRunner.completeStep("Gate: ASK (${approval.severity}) — waiting for user")
            stepRunner.startStep("ConfirmationOverlay: awaiting user decision")
            val confirmed = confirmationOverlay.requestConfirmation(
                action = command,
                severity = approval.severity
            )
            if (!confirmed) {
                Log.w(TAG, "User denied HIGH severity command: $command")
                devLogger.w("APPROVAL", "User denied — cancelling command")
                stepRunner.completeStep("User denied", success = false)
                _messages.update { it + ChatMessage("Perintah dibatalkan.", isUser = false) }
                stateMachine.transition(ChibiState.IDLE)
                return
            }
            stepRunner.completeStep("User confirmed")
            devLogger.i("APPROVAL", "User confirmed HIGH severity command")
        } else {
            stepRunner.completeStep("Gate: ${approval.policy} (${approval.severity})")
        }

        // 1.5 Fast path — deterministic regex bypass for common single-action
        // commands. Runs BEFORE Gemma so "buka X" / "senter on" / "alarm 7:00"
        // work reliably even when the LLM's tool-calling is flaky (LiteRT-LM
        // 0.10.0 frequently emits raw `<|tool_call|>` text instead of firing
        // a structured call, which silently drops the action on the floor).
        //
        // The matcher returns null for multi-step commands ("buka tiktok cari
        // ikan") so those still go through the full planning pipeline where
        // Gemma can chain tools. Only single-intent commands fast-path here.
        stepRunner.startStep("FastPath match")
        val fast = fastPathMatcher.match(command)
        if (fast != null) {
            devLogger.i("FASTPATH", "Matched → ${fast.action::class.simpleName}")
            stepRunner.completeStep("FastPath hit: ${fast.action::class.simpleName}")
            // The FSM only allows IDLE→PLANNING→EXECUTING→VERIFYING→COMPLETED.
            // We can't skip PLANNING (IDLE→EXECUTING returns false), and we
            // can't skip VERIFYING (EXECUTING→COMPLETED returns false). Walk
            // through the full chain so every transition is legal. If any
            // step fails we surface a visible error instead of silently
            // dropping the command — that's the bug that broke "buka X" /
            // "nyalakan senter" after the previous refactor.
            stepRunner.startStep("FSM: IDLE → PLANNING")
            if (!stateMachine.transition(ChibiState.PLANNING, "fastpath")) {
                devLogger.e("FASTPATH", "Transition IDLE→PLANNING rejected (state=${stateMachine.current})")
                stepRunner.reportError("FSM rejected IDLE→PLANNING")
                _messages.update { it + ChatMessage("Fuu sibuk. Coba lagi sebentar.", isUser = false) }
                stateMachine.reset()
                return
            }
            stepRunner.completeStep("PLANNING entered")
            stepRunner.startStep("FSM: PLANNING → EXECUTING")
            if (!stateMachine.transition(ChibiState.EXECUTING, "fastpath")) {
                devLogger.e("FASTPATH", "Transition PLANNING→EXECUTING rejected")
                stepRunner.reportError("FSM rejected PLANNING→EXECUTING")
                _messages.update { it + ChatMessage("Fuu gagal pindah state. Reset.", isUser = false) }
                stateMachine.reset()
                return
            }
            stepRunner.completeStep("EXECUTING entered")
            try {
                stepRunner.startStep("Execute: ${fast.action::class.simpleName}")
                val result = executionRouter.handle(fast.action)
                devLogger.i("FASTPATH", "Result: $result")
                recordPatternFor(fast.action, result)
                // BUG-C/G: every executor now reports failure with an
                // "<domain>_error" / "<domain>_rejected" prefix. Listing
                // every prefix is annoying but explicit — a blanket
                // contains("error") would also swallow legit bodies like
                // "launched ErrorActivity". If you add a new executor
                // remember to register its failure prefix here.
                val success = !result.startsWith("error") &&
                    !result.startsWith("ui_error") &&
                    !result.startsWith("launch_failed") &&
                    !result.startsWith("launch_not_found") &&
                    !result.startsWith("launch_error") &&
                    !result.startsWith("brightness_error") &&
                    !result.startsWith("volume_error") &&
                    !result.startsWith("flashlight_error") &&
                    !result.startsWith("alarm_error") &&
                    !result.startsWith("intent_error") &&
                    !result.startsWith("intent_rejected") &&
                    !result.startsWith("intent_no_activity")
                stepRunner.completeStep("Result: $result", success = success)
                _messages.update {
                    it + ChatMessage(
                        if (success) fast.friendly else "Tidak bisa: $result",
                        isUser = false
                    )
                }
                memoryManager.saveCommand(
                    command,
                    result,
                    if (success) "COMPLETED" else "ERROR",
                    approval.severity.name
                )
                // EXECUTING → VERIFYING → COMPLETED → IDLE (walk every step).
                stateMachine.transition(ChibiState.VERIFYING, "fastpath")
                stateMachine.transition(ChibiState.COMPLETED, "fastpath")
                stepRunner.startStep("FSM: VERIFYING → COMPLETED")
                stepRunner.completeStep("Turn finished", success = success)
                stateMachine.reset()
            } catch (e: Exception) {
                devLogger.e("FASTPATH", "Execution error: ${e.message}")
                stepRunner.reportError("FastPath exception: ${e.message}")
                _messages.update { it + ChatMessage("Error: ${e.message}", isUser = false) }
                stateMachine.setError(e.message ?: "fastpath error")
                stateMachine.reset()
            }
            return
        }
        stepRunner.completeStep("FastPath miss — falling through to Gemma")

        // 2. Plan
        stepRunner.startStep("FSM: IDLE → PLANNING")
        if (!stateMachine.transition(ChibiState.PLANNING)) {
            stepRunner.reportError("FSM rejected IDLE→PLANNING")
            return
        }
        stepRunner.completeStep("PLANNING entered")
        devLogger.i("STATE", "IDLE → PLANNING")

        // Wait for engine if still loading
        if (gemmaEngineManager.state.value == EngineState.LOADING) {
            stepRunner.startStep("Engine: waiting for model load")
            devLogger.i("ENGINE", "Engine LOADING — waiting up to 90s...")
            _messages.update { it + ChatMessage("Memuat model Gemma, harap tunggu...", isUser = false) }
            val ready = withTimeoutOrNull(90_000L) {
                gemmaEngineManager.state.first { it == EngineState.READY || it == EngineState.ERROR || it == EngineState.UNLOADED }
            }
            if (ready != EngineState.READY) {
                devLogger.e("ENGINE", "Timeout or failed waiting for engine. State=$ready")
                stepRunner.reportError("Engine load timeout (state=$ready)")
                _messages.update { it + ChatMessage("Model gagal dimuat. Coba reload di Settings → AI Engine.", isUser = false) }
                stateMachine.reset()
                return
            }
            stepRunner.completeStep("Engine READY")
            devLogger.i("ENGINE", "Engine became READY after waiting")
        }

        if (!gemmaEngineManager.isReady()) {
            Log.e(TAG, "Gemma engine not loaded — cannot plan")
            devLogger.e("ENGINE", "isReady()=false state=${gemmaEngineManager.state.value}")
            stepRunner.reportError("Engine not ready")
            stateMachine.setError("Gemma engine not loaded")
            _messages.update { it + ChatMessage("Model belum dimuat. Buka Settings → AI Engine → Reload Model.", isUser = false) }
            memoryManager.saveCommand(command, "error: engine not loaded", "ERROR", "LOW")
            stateMachine.reset()
            return
        }

        // P3.5 — Dual model routing
        stepRunner.startStep("Router: choosing tier")
        val modelTier = modelRouter.route(command)
        Log.d(TAG, "Model routing: $command → $modelTier")
        devLogger.i("ROUTING", "Tier=$modelTier for command: \"$command\"")
        stepRunner.completeStep("Tier → $modelTier")
        currentTier = if (modelTier == ModelTier.E2B) 2 else 4

        stepRunner.startStep("Context assembler: building prompt")
        val systemPrompt = contextAssembler.buildSystemPrompt(command)
        devLogger.d("INFERENCE", "System prompt length: ${systemPrompt.length} chars")
        devLogger.d("INFERENCE", "Prompt preview: ${systemPrompt.take(120)}…")
        stepRunner.completeStep("Prompt ready (${systemPrompt.length} chars)")

        val responseBuilder = StringBuilder()

        // 3. Execute (Gemma drives execution via tools)
        stepRunner.startStep("FSM: PLANNING → EXECUTING")
        if (!stateMachine.transition(ChibiState.EXECUTING)) {
            stepRunner.reportError("FSM rejected PLANNING→EXECUTING")
            return
        }
        stepRunner.completeStep("EXECUTING entered")
        devLogger.i("STATE", "PLANNING → EXECUTING")
        if (killSwitch.isActive()) {
            stepRunner.reportError("Kill switch active — aborting")
            stateMachine.reset()
            return
        }

        // Reset per-turn tool-spoke flag. The tools instance itself is reused
        // from `chibiTools` (instance-level lazy), because LiteRT-LM pins the
        // ToolSet inside the Conversation the first time it's handed over.
        toolSpoke = false

        try {
            val fuuBuilder = StringBuilder()

            stepRunner.startStep("Inference: Gemma $modelTier streaming")
            devLogger.i("INFERENCE", "sendMessage() starting on tier=$modelTier…")
            gemmaInference.sendMessage(command, systemPrompt, chibiTools, tier = modelTier).collect { chunk ->
                if (killSwitch.isActive()) return@collect
                responseBuilder.append(chunk)
                fuuBuilder.append(chunk)
                _streamingChunk.tryEmit(chunk)
            }

            val rawText = fuuBuilder.toString().trim()
            devLogger.i("INFERENCE", "Response (${rawText.length} chars): ${rawText.take(200)}")
            stepRunner.completeStep("Inference done (${rawText.length} chars)")

            // Treat [ERROR] strings from inference as real failures
            if (rawText.startsWith("[ERROR]")) {
                devLogger.e("ORCHESTRATOR", "Inference returned error — treating as failure")
                stepRunner.reportError("Inference error: ${rawText.take(60)}")
                stateMachine.setError(rawText)
                _messages.update { it + ChatMessage(rawText, isUser = false) }
                memoryManager.saveCommand(command, rawText, "ERROR", approval.severity.name)
                stateMachine.reset()
                return
            }

            // Phase 10 — no-tools fallback execution.
            //
            // When LiteRT-LM's tool-call parser fails (the 0.10.0 JSON bug),
            // Gemma falls back to no-tools mode and emits raw text like:
            //   Langkah 1: launch_app(appName="TikTok")
            //   Langkah 2: ui_interact(action="click", target="Search")
            //
            // Previously this text was displayed as a chat bubble but NEVER
            // executed — the user saw the plan but nothing happened. Now we
            // attempt to parse the text into real ChibiAction objects and
            // run them through ExecutionRouter, just like tool-call mode does.
            //
            // If the parser finds zero known function calls, we fall through
            // to the old behavior (display as conversational text).
            if (rawText.isNotEmpty() && !toolSpoke) {
                val parsedActions = textActionParser.parse(rawText)
                if (parsedActions.isNotEmpty()) {
                    devLogger.i("FALLBACK", "Parsed ${parsedActions.size} action(s) from no-tools response")
                    stepRunner.startStep("Fallback: executing ${parsedActions.size} parsed action(s)")
                    val results = mutableListOf<String>()
                    var allSuccess = true
                    for ((i, action) in parsedActions.withIndex()) {
                        if (killSwitch.isActive()) break
                        // Skip report actions for execution — they're just
                        // Gemma's commentary. We'll show the last one as
                        // the final chat bubble.
                        if (action is ReportAction) {
                            _messages.update { it + ChatMessage(action.message, isUser = false) }
                            toolSpoke = true
                            continue
                        }
                        if (action is AskUserAction) {
                            _messages.update { it + ChatMessage(action.question, isUser = false) }
                            toolSpoke = true
                            continue
                        }
                        try {
                            devLogger.i("FALLBACK", "[${i + 1}/${parsedActions.size}] ${action::class.simpleName}")
                            val result = executionRouter.handle(action)
                            devLogger.i("FALLBACK", "  → $result")
                            results.add(result)
                            if (result.contains("error") || result.contains("failed") || result.contains("not_found")) {
                                allSuccess = false
                            }
                        } catch (e: Exception) {
                            devLogger.e("FALLBACK", "  → Exception: ${e.message}")
                            results.add("error: ${e.message}")
                            allSuccess = false
                        }
                    }
                    stepRunner.completeStep(
                        "Fallback done: ${results.size} action(s) executed",
                        success = allSuccess
                    )
                    // Show a summary bubble if tools didn't already speak
                    if (!toolSpoke) {
                        val summary = if (allSuccess && results.isNotEmpty()) {
                            results.last()
                        } else if (results.isEmpty()) {
                            "Selesai."
                        } else {
                            results.joinToString("\n")
                        }
                        _messages.update { it + ChatMessage(summary, isUser = false) }
                    }
                } else {
                    // No parseable actions — genuine conversational response
                    _messages.update { it + ChatMessage(rawText, isUser = false) }
                }
            } else if (rawText.isEmpty() && !toolSpoke) {
                _messages.update { it + ChatMessage("Selesai.", isUser = false) }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Execution error: ${e.message}")
            devLogger.e("ORCHESTRATOR", "Exception: ${e::class.simpleName}: ${e.message}")
            stepRunner.reportError("Exception: ${e::class.simpleName}: ${e.message}")
            stateMachine.setError(e.message ?: "Unknown error")
            _messages.update { it + ChatMessage("Error: ${e.message}", isUser = false) }
            memoryManager.saveCommand(command, "error: ${e.message}", "ERROR", approval.severity.name)
            stateMachine.reset()
            return
        }

        // 4. Complete
        stepRunner.startStep("FSM: EXECUTING → COMPLETED")
        stateMachine.transition(ChibiState.COMPLETED)
        devLogger.i("STATE", "EXECUTING → COMPLETED")
        val result = responseBuilder.toString().ifEmpty { "completed" }
        val tierNum = if (modelTier == ModelTier.E2B) 2 else 4
        memoryManager.saveCommand(command, result, "COMPLETED", approval.severity.name, tierNum)
        Log.d(TAG, "Command completed [$modelTier]: $result")
        devLogger.i("ORCHESTRATOR", "✓ Done. Tier=$modelTier result=${result.take(80)}")
        stepRunner.completeStep("Turn finished (tier=$modelTier)")
        stateMachine.reset()
    }

    companion object {
        private const val TAG = "ChibiOrchestrator"
    }
}
