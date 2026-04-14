package com.chibiclaw.ai

import android.util.Log
import com.chibiclaw.executor.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 10 — fallback parser for no-tools mode responses.
 *
 * When LiteRT-LM's tool-call parser bugs out (the 0.10.0 JSON parse crash),
 * Gemma falls back to no-tools mode and emits plain text that looks like:
 *
 *     Langkah 1: launch_app(appName="TikTok")
 *     Langkah 2: ui_interact(action="click", target="Search")
 *     Langkah 3: ui_interact(action="type", target="Search", text="ikan")
 *     Langkah 4: report(message="Selesai mencari ikan di TikTok")
 *
 * This parser extracts those pseudo-function-calls and converts them into
 * real [ChibiAction] objects that [ChibiOrchestrator] can feed to
 * [com.chibiclaw.executor.ExecutionRouter].
 *
 * The regex is intentionally lenient — Gemma sometimes omits "Langkah N:",
 * sometimes uses camelCase, sometimes puts extra whitespace. We handle all
 * variants.
 */
@Singleton
class TextActionParser @Inject constructor() {

    /**
     * Matches function calls in the form: `function_name(param1="value1", param2="value2")`
     * Captures:
     *   group 1 → function name (e.g. "launch_app")
     *   group 2 → full param string (e.g. `appName="TikTok", target="Search"`)
     */
    private val callPattern = Regex(
        """(\w+)\s*\(\s*((?:[^()]*|"[^"]*")*)\s*\)"""
    )

    /**
     * Matches individual key="value" or key=number parameters.
     * Handles quoted strings and bare integers/floats.
     */
    private val paramPattern = Regex(
        """(\w+)\s*=\s*(?:"([^"]*)"|([\d.]+))"""
    )

    /**
     * Attempts to parse [rawText] into a list of executable actions.
     * Returns an empty list if no valid function calls are found, meaning
     * the orchestrator should treat the response as plain conversational text.
     */
    fun parse(rawText: String): List<ChibiAction> {
        if (rawText.isBlank()) return emptyList()

        val actions = mutableListOf<ChibiAction>()

        for (match in callPattern.findAll(rawText)) {
            val funcName = match.groupValues[1].lowercase()
            val paramStr = match.groupValues[2]
            val params = extractParams(paramStr)

            val action = buildAction(funcName, params)
            if (action != null) {
                actions.add(action)
                Log.d(TAG, "Parsed: $funcName(${params.keys.joinToString()}) → ${action::class.simpleName}")
            } else {
                Log.w(TAG, "Unknown function: $funcName — skipping")
            }
        }

        return actions
    }

    /**
     * Returns true if [rawText] contains at least one parseable function call.
     * Used by the orchestrator to decide whether to attempt execution or
     * just display the text as a chat bubble.
     */
    fun containsActions(rawText: String): Boolean {
        if (rawText.isBlank()) return false
        return callPattern.findAll(rawText).any { match ->
            val funcName = match.groupValues[1].lowercase()
            funcName in KNOWN_FUNCTIONS
        }
    }

    private fun extractParams(paramStr: String): Map<String, String> {
        val params = mutableMapOf<String, String>()
        for (m in paramPattern.findAll(paramStr)) {
            val key = m.groupValues[1]
            val value = m.groupValues[2].ifEmpty { m.groupValues[3] }
            params[key] = value
        }
        return params
    }

    private fun buildAction(funcName: String, p: Map<String, String>): ChibiAction? {
        return when (funcName) {
            // App launcher
            "launch_app", "launchapp" ->
                LaunchAppAction(appName = p["appName"] ?: p["app_name"] ?: p["name"] ?: return null)

            // Intent API
            "intent_send", "intentsend" ->
                IntentAction(
                    action = p["action"] ?: return null,
                    uri = p["uri"] ?: "",
                    packageName = p["packageName"] ?: p["package_name"] ?: p["package"] ?: ""
                )

            // System control
            "system_control", "systemcontrol" ->
                SystemControlAction(
                    target = p["target"] ?: return null,
                    state = p["state"] ?: "toggle"
                )

            // Alarm
            "set_alarm", "setalarm" ->
                SetAlarmAction(
                    hour = (p["hour"] ?: return null).toIntOrNull() ?: return null,
                    minute = (p["minute"] ?: "0").toIntOrNull() ?: 0,
                    label = p["label"] ?: ""
                )

            // UI interaction
            "ui_interact", "uiinteract" ->
                UiInteractAction(
                    action = p["action"] ?: return null,
                    target = p["target"] ?: "",
                    text = p["text"] ?: ""
                )

            // Gesture
            "gesture" ->
                GestureAction(
                    kind = p["kind"] ?: return null,
                    x1 = (p["x1"] ?: "0").toIntOrNull() ?: 0,
                    y1 = (p["y1"] ?: "0").toIntOrNull() ?: 0,
                    x2 = (p["x2"] ?: "0").toIntOrNull() ?: 0,
                    y2 = (p["y2"] ?: "0").toIntOrNull() ?: 0,
                    durationMs = (p["durationMs"] ?: p["duration_ms"] ?: "0").toIntOrNull() ?: 0
                )

            // Vision
            "vision_analyze", "visionanalyze" ->
                VisionAnalyzeAction(
                    mode = p["mode"] ?: "describe",
                    query = p["query"] ?: ""
                )

            // Content query
            "content_query", "contentquery" ->
                ContentQueryAction(
                    provider = p["provider"] ?: return null,
                    query = p["query"] ?: ""
                )

            // Messaging
            "messaging" ->
                MessagingAction(
                    kind = p["kind"] ?: return null,
                    recipient = p["recipient"] ?: "",
                    body = p["body"] ?: p["message"] ?: "",
                    subject = p["subject"] ?: ""
                )

            // Clipboard
            "clipboard" ->
                ClipboardAction(
                    op = p["op"] ?: "get",
                    text = p["text"] ?: ""
                )

            // Scan UI
            "scan_ui", "scanui" ->
                ScanUiAction(method = p["method"] ?: "accessibility")

            // Memory
            "memory_query", "memoryquery" ->
                MemoryQueryAction(
                    query = p["query"] ?: return null,
                    scope = p["scope"] ?: "all"
                )

            // Wait
            "wait" ->
                WaitAction(seconds = (p["seconds"] ?: "2").toIntOrNull()?.coerceIn(1, 10) ?: 2)

            // Ask user
            "ask_user", "askuser" ->
                AskUserAction(question = p["question"] ?: return null)

            // Report
            "report" ->
                ReportAction(
                    message = p["message"] ?: return null,
                    status = p["status"] ?: "success"
                )

            // Capture
            "capture" ->
                CaptureAction(kind = p["kind"] ?: return null)

            // Media session
            "media_session", "mediasession" ->
                MediaSessionAction(
                    command = p["command"] ?: return null,
                    target = p["target"] ?: ""
                )

            // Utility
            "utility", "calc" ->
                UtilityAction(
                    kind = if (funcName == "calc") "calc" else (p["kind"] ?: "calc"),
                    input = p["input"] ?: p["expr"] ?: "",
                    param = p["param"] ?: ""
                )

            // Shizuku privileged command
            "shizuku", "shizuku_command", "shizukucommand" ->
                ShizukuAction(
                    kind = p["kind"] ?: return null,
                    payload = p["payload"] ?: p["command"] ?: ""
                )

            // Phase 11 — Extended actions
            "device_control", "devicecontrol" ->
                DeviceControlAction(
                    category = p["category"] ?: return null,
                    target = p["target"] ?: return null,
                    command = p["command"] ?: "get",
                    value = p["value"] ?: ""
                )

            "telephony", "telephony_query", "telephonyquery" ->
                TelephonyAction(
                    operation = p["operation"] ?: p["op"] ?: return null,
                    value = p["value"] ?: ""
                )

            "app_manage", "appmanage", "app_management" ->
                AppManageAction(
                    operation = p["operation"] ?: p["op"] ?: return null,
                    appName = p["appName"] ?: p["app_name"] ?: p["app"] ?: "",
                    value = p["value"] ?: ""
                )

            "file_manage", "filemanage", "file" ->
                FileAction(
                    operation = p["operation"] ?: p["op"] ?: return null,
                    path = p["path"] ?: "",
                    destination = p["destination"] ?: p["dest"] ?: ""
                )

            "notification_control", "notificationcontrol", "notif" ->
                NotificationAction(
                    operation = p["operation"] ?: p["op"] ?: return null,
                    packageName = p["packageName"] ?: p["package"] ?: "",
                    notificationKey = p["key"] ?: p["notificationKey"] ?: "",
                    replyText = p["text"] ?: p["replyText"] ?: p["reply"] ?: ""
                )

            "location_query", "locationquery", "location" ->
                LocationAction(
                    operation = p["operation"] ?: p["op"] ?: return null,
                    value = p["value"] ?: ""
                )

            "device_info", "deviceinfo" ->
                DeviceInfoAction(
                    target = p["target"] ?: "all"
                )

            "schedule_task", "scheduletask", "schedule" ->
                ScheduleAction(
                    operation = p["operation"] ?: p["op"] ?: return null,
                    command = p["command"] ?: "",
                    scheduleTime = p["scheduleTime"] ?: p["time"] ?: "",
                    repeatInterval = p["repeatInterval"] ?: p["interval"] ?: "",
                    taskId = p["taskId"] ?: p["id"] ?: ""
                )

            "contact_manage", "contactmanage", "contact" ->
                ContactWriteAction(
                    operation = p["operation"] ?: p["op"] ?: return null,
                    name = p["name"] ?: "",
                    phone = p["phone"] ?: "",
                    email = p["email"] ?: "",
                    contactId = p["contactId"] ?: p["id"] ?: ""
                )

            else -> null
        }
    }

    companion object {
        private const val TAG = "TextActionParser"

        private val KNOWN_FUNCTIONS = setOf(
            "launch_app", "launchapp",
            "intent_send", "intentsend",
            "system_control", "systemcontrol",
            "set_alarm", "setalarm",
            "ui_interact", "uiinteract",
            "gesture",
            "vision_analyze", "visionanalyze",
            "content_query", "contentquery",
            "messaging",
            "clipboard",
            "scan_ui", "scanui",
            "memory_query", "memoryquery",
            "wait",
            "ask_user", "askuser",
            "report",
            "capture",
            "media_session", "mediasession",
            "utility", "calc",
            "shizuku", "shizuku_command", "shizukucommand",
            "device_control", "devicecontrol",
            "telephony", "telephony_query", "telephonyquery",
            "app_manage", "appmanage", "app_management",
            "file_manage", "filemanage", "file",
            "notification_control", "notificationcontrol", "notif",
            "location_query", "locationquery", "location",
            "device_info", "deviceinfo",
            "schedule_task", "scheduletask", "schedule",
            "contact_manage", "contactmanage", "contact"
        )
    }
}
