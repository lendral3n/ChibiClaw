package com.chibiclaw.executor

import android.util.Log
import com.chibiclaw.executor.tier0.UtilityExecutor
import com.chibiclaw.executor.tier1.AppLauncher
import com.chibiclaw.executor.tier1.IntentExecutor
import com.chibiclaw.executor.tier2.AudioControlExecutor
import com.chibiclaw.executor.tier2.CalendarExecutor
import com.chibiclaw.executor.tier2.CameraExecutor
import com.chibiclaw.executor.tier2.ClipboardExecutor
import com.chibiclaw.executor.tier2.ContactsExecutor
import com.chibiclaw.executor.tier2.ContactWriteExecutor
import com.chibiclaw.executor.tier2.DeviceInfoExecutor
import com.chibiclaw.executor.tier2.DisplayControlExecutor
import com.chibiclaw.executor.tier2.EmailExecutor
import com.chibiclaw.executor.tier2.FileManagerExecutor
import com.chibiclaw.executor.tier2.HardwareControlExecutor
import com.chibiclaw.executor.tier2.LocationExecutor
import com.chibiclaw.executor.tier2.MediaSessionExecutor
import com.chibiclaw.executor.tier2.MessagingExecutor
import com.chibiclaw.executor.tier2.NetworkControlExecutor
import com.chibiclaw.executor.tier2.PowerControlExecutor
import com.chibiclaw.executor.tier2.ScheduleExecutor
import com.chibiclaw.executor.tier2.SmsExecutor
import com.chibiclaw.executor.tier2.SystemApiExecutor
import com.chibiclaw.executor.tier2.TelephonyExecutor
import com.chibiclaw.executor.tier3.AccessibilityExecutor
import com.chibiclaw.executor.tier3.GestureDispatcher
import com.chibiclaw.executor.tier3.VisionActionExecutor
import com.chibiclaw.executor.tier4.AppManager
import com.chibiclaw.executor.tier4.NotificationController
import com.chibiclaw.executor.tier4.ShizukuExecutor
import com.chibiclaw.memory.MemoryManager
import com.chibiclaw.perception.PerceptionRouter
import com.chibiclaw.safety.AutoControlGate
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExecutionRouter @Inject constructor(
    private val utilityExecutor: UtilityExecutor,
    private val intentExecutor: IntentExecutor,
    private val appLauncher: AppLauncher,
    private val contactsExecutor: ContactsExecutor,
    private val calendarExecutor: CalendarExecutor,
    private val smsExecutor: SmsExecutor,
    private val systemApiExecutor: SystemApiExecutor,
    private val messagingExecutor: MessagingExecutor,
    private val emailExecutor: EmailExecutor,
    private val cameraExecutor: CameraExecutor,
    private val clipboardExecutor: ClipboardExecutor,
    private val mediaSessionExecutor: MediaSessionExecutor,
    private val accessibilityExecutor: AccessibilityExecutor,
    private val gestureDispatcher: GestureDispatcher,
    private val visionActionExecutor: VisionActionExecutor,
    private val shizukuExecutor: ShizukuExecutor,
    private val appManager: AppManager,
    private val notificationController: NotificationController,
    private val audioControlExecutor: AudioControlExecutor,
    private val displayControlExecutor: DisplayControlExecutor,
    private val hardwareControlExecutor: HardwareControlExecutor,
    private val networkControlExecutor: NetworkControlExecutor,
    private val telephonyExecutor: TelephonyExecutor,
    private val fileManagerExecutor: FileManagerExecutor,
    private val powerControlExecutor: PowerControlExecutor,
    private val locationExecutor: LocationExecutor,
    private val deviceInfoExecutor: DeviceInfoExecutor,
    private val contactWriteExecutor: ContactWriteExecutor,
    private val scheduleExecutor: ScheduleExecutor,
    private val perceptionRouter: PerceptionRouter,
    private val memoryManager: MemoryManager,
    private val killSwitch: KillSwitch,
    private val timeoutGuard: TimeoutGuard,
    private val autoControlGate: AutoControlGate,
    private val postActionObserver: PostActionObserver,
    private val dryRunMode: DryRunMode
) {
    // Called by ChibiClawTools when Gemma invokes a tool
    suspend fun handle(action: ChibiAction): String {
        if (killSwitch.isActive()) return "killed"

        // Phase 8.4 — Dry-run short-circuit. Only intercepts
        // side-effectful actions; read-only observations (ScanUi,
        // MemoryQuery, ContentQuery, VisionAnalyze, ReportAction,
        // AskUserAction) still run for real so the agent keeps
        // grounded context.
        if (dryRunMode.isEnabled() && isSideEffectful(action)) {
            return "dry_run: ${dryRunMode.simulate(action)}"
        }

        // Per-app policy gate — blocks commands targeted at packages the
        // user has restricted in Settings → Safety → Auto-Control. Runs
        // BEFORE the tier dispatch so we never even construct an Intent
        // for a denied package. System-level actions (flashlight, alarm,
        // memory query) are passed through unchanged.
        val gateDecision = autoControlGate.check(action)
        if (gateDecision is AutoControlGate.Decision.Deny) {
            Log.w(TAG, "AutoControlGate DENY ${gateDecision.reason}: ${gateDecision.message}")
            return "auto_control_denied: ${gateDecision.reason}: ${gateDecision.message}"
        }

        return when (action) {
            is IntentAction -> {
                val primary = timeoutGuard.withActionTimeout {
                    intentExecutor.execute(action)
                }.getOrElse { "intent_error: ${it.message}" }
                // Most VIEW/SEND intents open another activity, so a short
                // observation is useful to the planner.
                if (primary.startsWith("intent_success")) {
                    postActionObserver.capture(primary)
                } else primary
            }

            is ContentQueryAction -> {
                timeoutGuard.withActionTimeout {
                    routeContentQuery(action)
                }.getOrElse { "content_error: ${it.message}" }
            }

            is UiInteractAction -> {
                val primary = timeoutGuard.withUiTimeout {
                    accessibilityExecutor.perform(action)
                }.getOrElse { "ui_error: ${it.message}" }
                // Auto-observe after the action so Gemma sees the new screen
                // in the same tool response. Only observe on actions that
                // actually changed state (click/type/scroll/globals) —
                // nothing to observe if the accessibility call itself failed
                // synchronously (e.g. service not connected).
                if (shouldObserveUi(primary)) postActionObserver.capture(primary) else primary
            }

            is GestureAction -> {
                val primary = timeoutGuard.withUiTimeout {
                    gestureDispatcher.perform(action)
                }.getOrElse { "gesture_error: ${it.message}" }
                if (primary.startsWith("gesture_success")) {
                    postActionObserver.capture(primary)
                } else primary
            }

            is VisionAnalyzeAction -> {
                timeoutGuard.withVisionTimeout {
                    visionActionExecutor.analyze(action)
                }.getOrElse { "vision_error: ${it.message}" }
            }

            is ScanUiAction -> perceptionRouter.scan(action.method)

            is MemoryQueryAction -> memoryManager.queryAll(action.query)

            is WaitAction -> {
                delay(action.seconds * 1000L)
                "waited_${action.seconds}s"
            }

            is AskUserAction -> {
                Log.d(TAG, "User confirmation needed: ${action.question}")
                "waiting_user: ${action.question}"
            }

            is ReportAction -> {
                Log.i(TAG, "[${action.status.uppercase()}] ${action.message}")
                "reported"
            }

            is SystemControlAction -> {
                timeoutGuard.withActionTimeout {
                    systemApiExecutor.control(action.target, action.state)
                }.getOrElse { "system_error: ${it.message}" }
            }

            is SetAlarmAction -> {
                timeoutGuard.withActionTimeout {
                    intentExecutor.setAlarm(action.hour, action.minute, action.label)
                }.getOrElse { "alarm_error: ${it.message}" }
            }

            is LaunchAppAction -> {
                val primary = timeoutGuard.withActionTimeout {
                    appLauncher.launchByName(action.appName)
                }.getOrElse { "launch_error: ${it.message}" }
                // Launch opens a new foreground activity; give it a beat to
                // settle and then observe so Gemma immediately knows what
                // screen it landed on.
                if (primary.startsWith("launch_success")) {
                    postActionObserver.capture(primary, settleMs = 600L)
                } else primary
            }

            is MessagingAction -> {
                timeoutGuard.withActionTimeout {
                    messagingExecutor.send(action)
                }.getOrElse { "messaging_error: ${it.message}" }
            }

            is ShizukuAction -> {
                timeoutGuard.withActionTimeout {
                    shizukuExecutor.executeAction(action)
                }.getOrElse { "shizuku_error: ${it.message}" }
            }

            is ClipboardAction -> {
                timeoutGuard.withActionTimeout {
                    clipboardExecutor.perform(action)
                }.getOrElse { "clipboard_error: ${it.message}" }
            }

            is UtilityAction -> {
                timeoutGuard.withActionTimeout {
                    utilityExecutor.perform(action)
                }.getOrElse { "utility_error: ${it.message}" }
            }

            is CaptureAction -> {
                timeoutGuard.withActionTimeout {
                    cameraExecutor.perform(action)
                }.getOrElse { "capture_error: ${it.message}" }
            }

            is MediaSessionAction -> {
                timeoutGuard.withActionTimeout {
                    mediaSessionExecutor.perform(action)
                }.getOrElse { "media_error: ${it.message}" }
            }

            // ── Phase 11 — Extended device control actions ──

            is DeviceControlAction -> {
                timeoutGuard.withActionTimeout {
                    routeDeviceControl(action)
                }.getOrElse { "device_control_error: ${it.message}" }
            }

            is TelephonyAction -> {
                timeoutGuard.withActionTimeout {
                    telephonyExecutor.perform(action.operation, action.value)
                }.getOrElse { "telephony_error: ${it.message}" }
            }

            is AppManageAction -> {
                timeoutGuard.withActionTimeout {
                    routeAppManage(action)
                }.getOrElse { "app_manage_error: ${it.message}" }
            }

            is FileAction -> {
                timeoutGuard.withActionTimeout {
                    fileManagerExecutor.perform(action.operation, action.path, action.destination)
                }.getOrElse { "file_error: ${it.message}" }
            }

            is NotificationAction -> {
                timeoutGuard.withActionTimeout {
                    routeNotification(action)
                }.getOrElse { "notification_error: ${it.message}" }
            }

            is LocationAction -> {
                timeoutGuard.withActionTimeout {
                    locationExecutor.perform(action.operation, action.value)
                }.getOrElse { "location_error: ${it.message}" }
            }

            is DeviceInfoAction -> {
                deviceInfoExecutor.perform(action.target)
            }

            is ScheduleAction -> {
                scheduleExecutor.perform(
                    action.operation, action.command,
                    action.scheduleTime, action.repeatInterval, action.taskId
                )
            }

            is ContactWriteAction -> {
                timeoutGuard.withActionTimeout {
                    contactWriteExecutor.perform(
                        action.operation, action.name,
                        action.phone, action.email, action.contactId
                    )
                }.getOrElse { "contact_error: ${it.message}" }
            }
        }
    }

    /** Read-only vs write actions for Phase 8.4 dry-run mode. */
    private fun isSideEffectful(action: ChibiAction): Boolean = when (action) {
        is ScanUiAction, is MemoryQueryAction, is ContentQueryAction,
        is VisionAnalyzeAction, is ReportAction, is AskUserAction, is WaitAction,
        is DeviceInfoAction -> false
        is DeviceControlAction -> action.command.lowercase() !in listOf("get", "status")
        is TelephonyAction -> action.operation.lowercase() !in listOf("call_log", "sim_info", "phone_number", "network")
        is LocationAction -> action.operation.lowercase() !in listOf("status", "get", "current", "gps_status")
        is NotificationAction -> action.operation.lowercase() in listOf("list", "list_app", "count")
        else -> true
    }

    private fun shouldObserveUi(result: String): Boolean {
        // Do NOT observe when the UI action itself errored out at the
        // accessibility-service layer — there's nothing new to see and the
        // observation would fire into an empty/unchanged tree.
        return !result.startsWith("ui_error") &&
            !result.startsWith("node_not_found") &&
            !result.startsWith("editable_not_found") &&
            !result.startsWith("scrollable_not_found") &&
            !result.startsWith("unknown_action")
    }

    private suspend fun routeContentQuery(action: ContentQueryAction): String {
        return when (action.provider.lowercase()) {
            "contacts" -> contactsExecutor.search(action.query)
            "calendar" -> calendarExecutor.getUpcomingEvents()
            "sms" -> smsExecutor.getRecentSms(action.query)
            "system" -> systemApiExecutor.getSystemInfo()
            else -> "unknown_provider: ${action.provider}"
        }
    }

    private suspend fun routeDeviceControl(action: DeviceControlAction): String {
        return when (action.category.lowercase()) {
            "hardware" -> hardwareControlExecutor.perform(action.target, action.command, action.value)
            "network" -> networkControlExecutor.perform(action.target, action.command, action.value)
            "display" -> displayControlExecutor.perform(action.target, action.command, action.value)
            "audio" -> audioControlExecutor.perform(action.target, action.command, action.value)
            "power" -> powerControlExecutor.perform(action.target, action.command)
            else -> "device_control_error: unknown category '${action.category}'"
        }
    }

    private suspend fun routeAppManage(action: AppManageAction): String {
        val op = action.operation.lowercase()
        return when (op) {
            "list" -> {
                val apps = appManager.listInstalled(includeSystem = action.value == "all")
                if (apps.isEmpty()) "apps: empty"
                else {
                    val sb = StringBuilder("[apps] ${apps.size} installed\n")
                    apps.take(30).forEach { sb.append("• ${it.label} (${it.packageName}) v${it.versionName ?: "?"}\n") }
                    if (apps.size > 30) sb.append("... dan ${apps.size - 30} lainnya")
                    sb.toString()
                }
            }
            "info" -> {
                val apps = appManager.findByLabel(action.appName)
                if (apps.isEmpty()) "app_not_found: ${action.appName}"
                else {
                    val app = apps.first()
                    "App: ${app.label} | Package: ${app.packageName} | Version: ${app.versionName} | System: ${app.isSystem} | Enabled: ${app.isEnabled}"
                }
            }
            "force_stop", "stop" -> {
                val apps = appManager.findByLabel(action.appName)
                if (apps.isEmpty()) "app_not_found: ${action.appName}"
                else appManager.forceStop(apps.first().packageName)
            }
            "clear_data", "clear_cache", "clear" -> {
                val apps = appManager.findByLabel(action.appName)
                if (apps.isEmpty()) "app_not_found: ${action.appName}"
                else appManager.clearData(apps.first().packageName)
            }
            "uninstall", "hapus" -> {
                val apps = appManager.findByLabel(action.appName)
                if (apps.isEmpty()) "app_not_found: ${action.appName}"
                else appManager.uninstallForUser(apps.first().packageName)
            }
            "open_info", "settings" -> {
                val apps = appManager.findByLabel(action.appName)
                if (apps.isEmpty()) "app_not_found: ${action.appName}"
                else appManager.openAppInfo(apps.first().packageName)
            }
            "store" -> {
                val apps = appManager.findByLabel(action.appName)
                if (apps.isEmpty()) "app_not_found: ${action.appName}"
                else appManager.openInStore(apps.first().packageName)
            }
            "is_installed", "check" -> {
                val apps = appManager.findByLabel(action.appName)
                if (apps.isEmpty()) "app_not_installed: ${action.appName}"
                else "app_installed: ${apps.first().label} (${apps.first().packageName})"
            }
            else -> "app_manage_error: unknown operation '$op'"
        }
    }

    private fun routeNotification(action: NotificationAction): String {
        val op = action.operation.lowercase()
        return when (op) {
            "list", "all" -> notificationController.format(limit = 15)
            "list_app" -> {
                val entries = notificationController.listActiveFor(action.packageName)
                if (entries.isEmpty()) "notifications: none for ${action.packageName}"
                else {
                    val sb = StringBuilder("[notifications] ${action.packageName}\n")
                    entries.forEach { sb.append("• key=${it.key} | ${it.title}: ${it.text.take(80)}\n") }
                    sb.toString()
                }
            }
            "count" -> "notification_count: ${notificationController.count()}"
            "dismiss", "cancel" -> {
                if (action.notificationKey.isNotBlank()) notificationController.cancel(action.notificationKey)
                else if (action.packageName.isNotBlank()) notificationController.cancelAllFor(action.packageName)
                else notificationController.cancelAll()
            }
            "dismiss_all", "clear" -> notificationController.cancelAll()
            "reply", "balas" -> {
                if (action.notificationKey.isBlank()) "notif_error: key required for reply"
                else notificationController.reply(action.notificationKey, action.replyText)
            }
            "action" -> {
                if (action.notificationKey.isBlank()) "notif_error: key required"
                else notificationController.triggerAction(action.notificationKey, action.replyText)
            }
            else -> "notification_error: unknown operation '$op'"
        }
    }

    companion object {
        private const val TAG = "ExecutionRouter"
    }
}
