package com.chibiclaw.agent.initiative.trigger

import com.chibiclaw.agent.initiative.TriggerEvent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Template renderer untuk standing instruction taskTemplate.
 *
 * Syntax: `{{path}}` — replace dengan event metadata atau context value.
 *
 * Supported:
 *   {{event.text}}, {{event.title}}, {{event.package}}, {{event.value}}
 *   {{time.hour}}, {{time.minute}}
 *   {{trigger.name}} — nama standing instruction
 *
 * Tidak match → leave placeholder kosong "".
 */
@Singleton
class TemplateRenderer @Inject constructor() {

    fun render(template: String, event: TriggerEvent?, instructionName: String): String {
        return PATTERN.replace(template) { match ->
            val path = match.groupValues[1].trim()
            resolve(path, event, instructionName) ?: ""
        }
    }

    private fun resolve(path: String, event: TriggerEvent?, instructionName: String): String? {
        val parts = path.split('.')
        return when (parts.firstOrNull()) {
            "event" -> when (parts.getOrNull(1)) {
                "text" -> event?.metadata?.get("text")
                "title" -> event?.metadata?.get("title")
                "package" -> event?.metadata?.get("package")
                "value" -> event?.metadata?.get("value")
                else -> event?.metadata?.get(parts.drop(1).joinToString("."))
            }
            "time" -> {
                val now = java.time.ZonedDateTime.now()
                when (parts.getOrNull(1)) {
                    "hour" -> now.hour.toString().padStart(2, '0')
                    "minute" -> now.minute.toString().padStart(2, '0')
                    "weekday" -> now.dayOfWeek.toString()
                    "iso" -> now.toString()
                    else -> null
                }
            }
            "trigger" -> when (parts.getOrNull(1)) {
                "name" -> instructionName
                else -> null
            }
            else -> null
        }
    }

    companion object {
        private val PATTERN = Regex("\\{\\{([^}]+)}}")
    }
}
