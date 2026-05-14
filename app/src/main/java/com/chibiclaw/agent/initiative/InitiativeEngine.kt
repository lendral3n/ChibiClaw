package com.chibiclaw.agent.initiative

import com.chibiclaw.agent.TaskManager
import com.chibiclaw.agent.initiative.trigger.ComplexTrigger
import com.chibiclaw.agent.initiative.trigger.TemplateRenderer
import com.chibiclaw.agent.initiative.trigger.TriggerEvaluator
import com.chibiclaw.compliance.AuditLogger
import com.chibiclaw.data.database.AuditActionType
import com.chibiclaw.data.database.StandingInstructionEntity
import com.chibiclaw.data.database.TaskChannel
import com.chibiclaw.data.repository.StandingInstructionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * InitiativeEngine — proactive evaluator standing instructions.
 *
 * Dua jalur evaluasi:
 *  1. **Tick** — coroutine looper interval 5-15s, evaluate semua instruction
 *     yang punya Time/Predicate leaf.
 *  2. **Event** — subscribe EventBus, evaluate yang punya Event/Geofence leaf
 *     (≤200ms latency target).
 *
 * Fire flow:
 *  - canFire() (enabled, cooldown, max-fires/day) → evaluate → render template
 *    → TaskManager.enqueue channel=STANDING + triggerSource=instructionId
 *  - AuditLog STANDING_INSTRUCTION_FIRED entry append.
 *
 * Adaptive tick: standar 10s, slow down ke 30s saat layar off (battery save).
 */
@Singleton
class InitiativeEngine @Inject constructor(
    private val repo: StandingInstructionRepository,
    private val evaluator: TriggerEvaluator,
    private val templateRenderer: TemplateRenderer,
    private val taskManager: TaskManager,
    private val eventBus: EventBus,
    private val auditLogger: AuditLogger,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val fireMutex = Mutex()
    @Volatile private var tickJob: Job? = null
    @Volatile private var eventJob: Job? = null
    @Volatile private var running = false

    fun start() {
        if (running) return
        running = true
        Timber.i("InitiativeEngine.start()")
        tickJob = scope.launch { tickLoop() }
        eventJob = scope.launch { eventLoop() }
    }

    fun stop() {
        Timber.i("InitiativeEngine.stop()")
        running = false
        tickJob?.cancel()
        eventJob?.cancel()
        tickJob = null
        eventJob = null
    }

    fun shutdown() {
        stop()
        scope.cancel()
    }

    private suspend fun tickLoop() {
        while (running) {
            runCatching { tickOnce() }.onFailure { Timber.e(it, "Tick failed") }
            delay(TICK_INTERVAL_MS)
        }
    }

    private suspend fun eventLoop() {
        eventBus.events.collect { event ->
            runCatching { onEvent(event) }.onFailure { Timber.e(it, "Event handle failed") }
        }
    }

    private suspend fun tickOnce() {
        val nowMs = System.currentTimeMillis()
        val instructions = repo.listEnabled()
        if (instructions.isEmpty()) return

        instructions.forEach { entity ->
            val trigger = repo.parseTrigger(entity) ?: return@forEach
            if (!evaluator.hasTickLeaf(trigger)) return@forEach
            if (!repo.canFire(entity, nowMs)) return@forEach
            val ctx = TriggerEvaluator.EvalContext(
                nowMs = nowMs,
                lastFireMs = repo.lastFireMs(entity),
                event = null,
            )
            if (evaluator.evaluate(trigger, ctx)) {
                fire(entity, event = null, source = "tick")
            }
        }
    }

    private suspend fun onEvent(event: TriggerEvent) {
        val nowMs = System.currentTimeMillis()
        val instructions = repo.listEnabled()
        instructions.forEach { entity ->
            val trigger = repo.parseTrigger(entity) ?: return@forEach
            if (!evaluator.hasEventLeaf(trigger)) return@forEach
            if (!repo.canFire(entity, nowMs)) return@forEach
            val ctx = TriggerEvaluator.EvalContext(
                nowMs = nowMs,
                lastFireMs = repo.lastFireMs(entity),
                event = event,
            )
            if (evaluator.evaluate(trigger, ctx)) {
                fire(entity, event = event, source = "event:${event.type}")
            }
        }
    }

    private suspend fun fire(
        entity: StandingInstructionEntity,
        event: TriggerEvent?,
        source: String,
    ) = fireMutex.withLock {
        // Double-check canFire setelah lock (race condition guard)
        val now = System.currentTimeMillis()
        val fresh = repo.get(entity.id) ?: return@withLock
        if (!repo.canFire(fresh, now)) return@withLock

        val rendered = templateRenderer.render(
            template = fresh.taskTemplate,
            event = event,
            instructionName = fresh.name,
        )
        if (rendered.isBlank()) {
            Timber.w("Empty rendered template for instruction=${fresh.id}; skip fire")
            return@withLock
        }

        Timber.i("FIRE standing='${fresh.name}' source=$source → task channel=${fresh.channel}")

        taskManager.enqueue(
            goal = rendered,
            channel = fresh.channel,
            priority = fresh.priority,
            triggerSource = "standing:${fresh.id}",
            maxIteration = 15,
        )
        repo.recordFire(fresh)
        auditLogger.log(
            actionType = AuditActionType.STANDING_INSTRUCTION_FIRED,
            dataSummary = "Standing '${fresh.name}' (id=${fresh.id}) fired via $source",
        )
    }

    /** Inspect helper untuk debug UI. */
    fun isRunning(): Boolean = running

    companion object {
        private const val TICK_INTERVAL_MS = 10_000L
    }
}
