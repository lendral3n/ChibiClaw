package com.chibiclaw.safety

import org.json.JSONArray
import org.json.JSONObject

/**
 * Per-package configuration that controls HOW Fuu is allowed to drive a
 * third-party app. This is the data model underpinning the
 * "Auto-Control" UI — user picks an app from the App Picker, then edits
 * its config here.
 *
 * [foregroundEnabled]
 *   The default path. When `true`, Fuu can launch the app (app becomes
 *   visible to the user for ~1 second), then drive it via
 *   AccessibilityService tap/swipe/type. Compatible with every device —
 *   no extra permission beyond accessibility.
 *
 * [backgroundEnabled]
 *   The stealth path. When `true`, Fuu may attempt to drive the app
 *   without bringing it to the foreground, using a MediaProjection
 *   snapshot for perception and Shizuku shell commands for input. This
 *   requires Shizuku to be installed and running — otherwise
 *   [AutoControlGate] refuses the command and reports
 *   `shizuku_unavailable` so the UI can prompt the user.
 *
 * [allowedActions]
 *   Fine-grained whitelist of action types permitted against this app:
 *     - "launch"  — LaunchAppAction
 *     - "tap"     — UiInteractAction("click", ...)
 *     - "type"    — UiInteractAction("type", ...)
 *     - "scroll"  — UiInteractAction("scroll", ...)
 *     - "back"    — UiInteractAction("back", "") or hardware back
 *     - "intent"  — IntentAction targeting this package
 *   An empty set means "all allowed" (back-compat default).
 *
 * A package without an entry in [AutoControlConfig.all] inherits the
 * global default: foreground=true, background=false, actions=all. This
 * keeps brand-new installs fully functional so the user isn't greeted
 * with a gate that blocks every app.
 */
data class AutoControlConfig(
    val packageName: String,
    val foregroundEnabled: Boolean = true,
    val backgroundEnabled: Boolean = false,
    val allowedActions: Set<String> = emptySet()  // empty = all
) {
    fun isActionAllowed(action: String): Boolean {
        if (allowedActions.isEmpty()) return true
        return action.lowercase() in allowedActions
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("pkg", packageName)
        put("fg", foregroundEnabled)
        put("bg", backgroundEnabled)
        put("actions", JSONArray(allowedActions.toList()))
    }

    companion object {
        /** The global fallback applied to packages without an explicit entry. */
        fun default(packageName: String) = AutoControlConfig(
            packageName = packageName,
            foregroundEnabled = true,
            backgroundEnabled = false,
            allowedActions = emptySet()
        )

        fun fromJson(obj: JSONObject): AutoControlConfig {
            val actionsArray = obj.optJSONArray("actions")
            val actions = mutableSetOf<String>()
            if (actionsArray != null) {
                for (i in 0 until actionsArray.length()) {
                    actions += actionsArray.getString(i)
                }
            }
            return AutoControlConfig(
                packageName = obj.getString("pkg"),
                foregroundEnabled = obj.optBoolean("fg", true),
                backgroundEnabled = obj.optBoolean("bg", false),
                allowedActions = actions
            )
        }

        fun listToJson(list: List<AutoControlConfig>): String {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }

        fun listFromJson(json: String): List<AutoControlConfig> {
            if (json.isBlank()) return emptyList()
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
