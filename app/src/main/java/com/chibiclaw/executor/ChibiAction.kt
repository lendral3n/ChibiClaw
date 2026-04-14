package com.chibiclaw.executor

sealed class ChibiAction

data class IntentAction(
    val action: String,
    val uri: String,
    val packageName: String = "",
    val extras: Map<String, String> = emptyMap()
) : ChibiAction()

data class ContentQueryAction(
    val provider: String,
    val query: String
) : ChibiAction()

data class UiInteractAction(
    val action: String,
    val target: String,
    val text: String = ""
) : ChibiAction()

data class ScanUiAction(
    val method: String = "accessibility"
) : ChibiAction()

data class MemoryQueryAction(
    val query: String,
    val scope: String = "all"
) : ChibiAction()

data class WaitAction(
    val seconds: Int
) : ChibiAction()

data class AskUserAction(
    val question: String
) : ChibiAction()

data class ReportAction(
    val message: String,
    val status: String = "success"
) : ChibiAction()

/**
 * Direct system toggle that cannot be done via Intent API (flashlight,
 * volume, brightness). Target examples: "flashlight", "volume", "brightness".
 * State is "on" / "off" / "toggle" or a 0–100 integer for volume/brightness.
 */
data class SystemControlAction(
    val target: String,
    val state: String
) : ChibiAction()

/**
 * Create an alarm on the device clock app. Uses ACTION_SET_ALARM with the
 * proper typed extras (hour + minute are int, not string), which plain
 * IntentAction cannot express.
 */
data class SetAlarmAction(
    val hour: Int,
    val minute: Int,
    val label: String = ""
) : ChibiAction()

/**
 * Launch an installed app by human-readable name. PackageManager resolves
 * the label to a real package instead of letting Gemma guess a fake string.
 */
data class LaunchAppAction(
    val appName: String
) : ChibiAction()

/**
 * Low-level gesture dispatched via AccessibilityService.dispatchGesture.
 * Used when ui_interact fails because the target element is custom-drawn
 * (Canvas, game, WebView) and Gemma has visual coordinates from vision_analyze.
 *
 * kinds: tap_coord, long_press, swipe, drag, pinch_in, pinch_out, double_tap
 */
data class GestureAction(
    val kind: String,
    val x1: Int = 0,
    val y1: Int = 0,
    val x2: Int = 0,
    val y2: Int = 0,
    val durationMs: Int = 0
) : ChibiAction()

/**
 * Visual screen analysis via Gemma multimodal + ML Kit OCR. Runs on a
 * fresh screenshot captured by MediaProjection.
 *
 * modes:
 *  - describe: natural-language description of the visible screen
 *  - find_element: locate a described element and return its bounds
 *  - ocr: extract all visible text via ML Kit text recognition
 *  - count: count how many instances of [query] appear on screen
 */
data class VisionAnalyzeAction(
    val mode: String,
    val query: String = ""
) : ChibiAction()

/**
 * Send a chat / SMS / email through the appropriate messaging executor.
 * kind: "sms", "whatsapp", "telegram", "email"
 */
data class MessagingAction(
    val kind: String,
    val recipient: String,
    val body: String,
    val subject: String = ""
) : ChibiAction()

/**
 * Shizuku-mediated privileged command. Only a small whitelist of prefixes
 * is accepted (see [com.chibiclaw.executor.tier4.ShizukuExecutor]).
 */
data class ShizukuAction(
    val kind: String,
    val payload: String = ""
) : ChibiAction()

/**
 * Clipboard get/set/history via ClipboardManager + recent history store.
 */
data class ClipboardAction(
    val op: String,
    val text: String = ""
) : ChibiAction()

/**
 * Pure-logic utility action handled by Tier-0 [com.chibiclaw.executor.tier0.UtilityExecutor].
 * kinds: calc, unit_convert, timezone, translate
 */
data class UtilityAction(
    val kind: String,
    val input: String,
    val param: String = ""
) : ChibiAction()

/**
 * Camera / media capture operations. kinds: photo, video, qr_scan, ocr_gallery.
 */
data class CaptureAction(
    val kind: String,
    val args: Map<String, String> = emptyMap()
) : ChibiAction()

/**
 * MediaSession transport control (play/pause/skip/prev/volume).
 */
data class MediaSessionAction(
    val command: String,
    val target: String = ""
) : ChibiAction()

// ═══════════════════════════════════════════════════════════════
// Phase 11 — Extended device control actions (C1–C14)
// ═══════════════════════════════════════════════════════════════

/**
 * Consolidated device control: hardware, network, display, audio, power, security.
 * category: "hardware" | "network" | "display" | "audio" | "power"
 * target:   specific sub-target within category
 * command:  "on" | "off" | "toggle" | "set" | "get"
 * value:    parameter value (e.g. "50" for volume, "120" for Hz)
 */
data class DeviceControlAction(
    val category: String,
    val target: String,
    val command: String,
    val value: String = ""
) : ChibiAction()

/**
 * Telephony operations: call log, SIM info, answer/reject, USSD.
 */
data class TelephonyAction(
    val operation: String,
    val value: String = ""
) : ChibiAction()

/**
 * App management: list, info, force stop, clear cache, uninstall, disable, usage stats.
 */
data class AppManageAction(
    val operation: String,
    val appName: String = "",
    val value: String = ""
) : ChibiAction()

/**
 * File operations: list, info, copy, move, delete, share, zip, unzip, search, storage_info.
 */
data class FileAction(
    val operation: String,
    val path: String = "",
    val destination: String = ""
) : ChibiAction()

/**
 * Advanced notification control: list, dismiss, reply, count, dnd.
 */
data class NotificationAction(
    val operation: String,
    val packageName: String = "",
    val notificationKey: String = "",
    val replyText: String = ""
) : ChibiAction()

/**
 * Location operations: GPS toggle, current location, status.
 */
data class LocationAction(
    val operation: String,
    val value: String = ""
) : ChibiAction()

/**
 * Device info queries: identity, cpu, network, sensors, all.
 */
data class DeviceInfoAction(
    val target: String
) : ChibiAction()

/**
 * Scheduled task automation via WorkManager.
 */
data class ScheduleAction(
    val operation: String,
    val command: String = "",
    val scheduleTime: String = "",
    val repeatInterval: String = "",
    val taskId: String = ""
) : ChibiAction()

/**
 * Contact CRUD: add, edit, delete.
 */
data class ContactWriteAction(
    val operation: String,
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val contactId: String = ""
) : ChibiAction()
