package com.chibiclaw.agent.tools

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registry tool yang ter-bind via Hilt @IntoMap.
 *
 * Phase 1 list: intent_open, system_action, memory_remember, memory_recall,
 * wait, await_user. `done` bukan tool literal — di-emit oleh parser dari
 * response "next": "done".
 */
@Singleton
class ToolRegistry @Inject constructor(
    private val tools: Map<String, @JvmSuppressWildcards Tool>,
) {
    fun all(): List<Tool> = tools.values.toList()

    fun availableSpecs(): List<ToolSpec> = tools.values
        .filter { it.isAvailable() }
        .map { it.spec }

    fun get(name: String): Tool? = tools[name]
}
