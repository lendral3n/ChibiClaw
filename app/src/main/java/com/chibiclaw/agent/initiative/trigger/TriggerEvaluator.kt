package com.chibiclaw.agent.initiative.trigger

import com.chibiclaw.agent.initiative.TriggerEvent
import com.chibiclaw.world.WorldObserver
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TriggerEvaluator — pure-ish evaluator. Returns true kalau trigger fire-able
 * di context yang diberikan.
 *
 * Pemanggil (InitiativeEngine) panggil ini dua bentuk:
 *  1. Tick mode: `evaluate(trigger, EvalContext(event=null, nowMs=..))`
 *     — Time + Predicate + Composite di-evaluate.
 *  2. Event mode: `evaluate(trigger, EvalContext(event=triggerEvent, nowMs=..))`
 *     — Event + Composite (yg child event) di-evaluate.
 */
@Singleton
class TriggerEvaluator @Inject constructor(
    private val cronParser: CronParser,
    private val predicateEvaluator: SimplePredicateEvaluator,
    private val worldObserver: WorldObserver,
) {

    data class EvalContext(
        val nowMs: Long,
        val lastFireMs: Long,
        val event: TriggerEvent? = null,
    )

    fun evaluate(trigger: ComplexTrigger, ctx: EvalContext): Boolean = when (trigger) {
        is ComplexTrigger.Time -> evaluateTime(trigger, ctx)
        is ComplexTrigger.Event -> evaluateEvent(trigger, ctx)
        is ComplexTrigger.Predicate -> evaluatePredicate(trigger, ctx)
        is ComplexTrigger.Composite -> evaluateComposite(trigger, ctx)
        is ComplexTrigger.Geofence -> evaluateGeofence(trigger, ctx)
    }

    private fun evaluateTime(t: ComplexTrigger.Time, ctx: EvalContext): Boolean {
        if (ctx.event != null) return false   // Time only di tick mode
        return cronParser.shouldFire(t.cron, ctx.lastFireMs, ctx.nowMs)
    }

    private fun evaluateEvent(t: ComplexTrigger.Event, ctx: EvalContext): Boolean {
        val e = ctx.event ?: return false
        if (e.type != t.eventType) return false
        return matchesFilter(t.filter, e)
    }

    private fun evaluatePredicate(t: ComplexTrigger.Predicate, ctx: EvalContext): Boolean {
        // Predicate di-evaluate di tick mode dengan world snapshot.
        // Kalau dipanggil di event-mode, predicate juga ikut dievaluasi
        // (kombinasi composite AND/OR yg child event + predicate).
        val world = runCatching { worldObserver.current() }.getOrNull()
        return predicateEvaluator.evaluate(t.expression, world, ctx.event)
    }

    private fun evaluateComposite(t: ComplexTrigger.Composite, ctx: EvalContext): Boolean {
        return when (t.op) {
            CompositeOp.AND -> t.children.all { evaluate(it, ctx) }
            CompositeOp.OR -> t.children.any { evaluate(it, ctx) }
            CompositeOp.NOT -> t.children.isNotEmpty() && !evaluate(t.children.first(), ctx)
        }
    }

    private fun evaluateGeofence(t: ComplexTrigger.Geofence, ctx: EvalContext): Boolean {
        val e = ctx.event ?: return false
        val expected = if (t.enter) EventType.GEOFENCE_ENTER else EventType.GEOFENCE_EXIT
        if (e.type != expected) return false
        val place = e.metadata["place_key"] ?: return false
        return place == t.placeMemoryKey
    }

    private fun matchesFilter(filter: TriggerFilter, event: TriggerEvent): Boolean {
        val md = event.metadata
        if (filter.packageName != null) {
            if (md["package"] != filter.packageName) return false
        }
        if (filter.titleRegex != null) {
            val title = md["title"].orEmpty()
            if (!runCatching { Regex(filter.titleRegex).containsMatchIn(title) }.getOrDefault(false)) {
                return false
            }
        }
        if (filter.textRegex != null) {
            val text = md["text"].orEmpty()
            if (!runCatching { Regex(filter.textRegex).containsMatchIn(text) }.getOrDefault(false)) {
                return false
            }
        }
        if (filter.minValue != null) {
            val v = md["value"]?.toIntOrNull() ?: return false
            if (v < filter.minValue) return false
        }
        if (filter.maxValue != null) {
            val v = md["value"]?.toIntOrNull() ?: return false
            if (v > filter.maxValue) return false
        }
        filter.extras.forEach { (k, v) ->
            if (md[k] != v) return false
        }
        return true
    }

    /** True kalau trigger bertype yang akan diperiksa di event-mode (Event/Composite/Geofence). */
    fun hasEventLeaf(trigger: ComplexTrigger): Boolean = when (trigger) {
        is ComplexTrigger.Event, is ComplexTrigger.Geofence -> true
        is ComplexTrigger.Composite -> trigger.children.any { hasEventLeaf(it) }
        else -> false
    }

    /** True kalau trigger bertype yang fire-able di tick mode (Time/Predicate/Composite). */
    fun hasTickLeaf(trigger: ComplexTrigger): Boolean = when (trigger) {
        is ComplexTrigger.Time, is ComplexTrigger.Predicate -> true
        is ComplexTrigger.Composite -> trigger.children.any { hasTickLeaf(it) }
        else -> false
    }

    init {
        Timber.v("TriggerEvaluator ready")
    }
}
