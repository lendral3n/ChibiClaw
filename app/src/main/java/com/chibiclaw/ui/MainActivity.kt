package com.chibiclaw.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.chibiclaw.compliance.AuditLogger
import com.chibiclaw.data.database.AuditActionType
import com.chibiclaw.ai.llm.webview.SessionExtractor
import com.chibiclaw.data.prefs.SecurePreferences
import com.chibiclaw.permissions.ShizukuManager
import com.chibiclaw.service.ChibiService
import com.chibiclaw.agent.TaskManager
import com.chibiclaw.agent.initiative.trigger.CronParser
import com.chibiclaw.agent.scheduler.ResourceScheduler
import com.chibiclaw.data.repository.MemoryRepository
import com.chibiclaw.memory.MemoryStore
import com.chibiclaw.ai.llm.AdapterQuotaTracker
import com.chibiclaw.ai.llm.InferenceRouter
import com.chibiclaw.ai.llm.adapters.CloudSessionRotator
import com.chibiclaw.data.repository.StandingInstructionRepository
import com.chibiclaw.ui.chat.ChatScreen
import com.chibiclaw.ui.debug.ErrorStatsScreen
import com.chibiclaw.ui.debug.TaskDetailScreen
import com.chibiclaw.ui.debug.TaskListScreen
import com.chibiclaw.ui.home.HomeDashboardScreen
import com.chibiclaw.ui.initiative.StandingInstructionEditorScreen
import com.chibiclaw.ui.initiative.StandingInstructionListScreen
import com.chibiclaw.ui.memory.MemoryInspectorScreen
import com.chibiclaw.ui.settings.AiEngineSettingsScreen
import com.chibiclaw.ui.setup.SetupNavigator
import com.chibiclaw.vision.projection.ProjectionTokenStore
import com.chibiclaw.ui.theme.ChibiClawTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * MainActivity Phase 0.
 *
 * Flow:
 * 1. Kalau setup belum complete → SetupNavigator (privacy → consent → vendor wizard)
 * 2. Kalau setup complete → MinimalHomePlaceholder (Phase 1 akan ganti dashboard)
 *
 * Phase 1+ akan add navigation graph proper (NavHost) untuk chat, task list, settings.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var securePreferences: SecurePreferences
    @Inject lateinit var auditLogger: AuditLogger
    @Inject lateinit var shizukuManager: ShizukuManager
    @Inject lateinit var sessionExtractor: SessionExtractor
    @Inject lateinit var router: InferenceRouter
    @Inject lateinit var quotaTracker: AdapterQuotaTracker
    @Inject lateinit var sessionRotator: CloudSessionRotator
    @Inject lateinit var projectionTokenStore: ProjectionTokenStore
    @Inject lateinit var standingInstructionRepo: StandingInstructionRepository
    @Inject lateinit var cronParser: CronParser
    @Inject lateinit var memoryStore: MemoryStore
    @Inject lateinit var memoryRepository: MemoryRepository
    @Inject lateinit var taskManager: TaskManager
    @Inject lateinit var resourceScheduler: ResourceScheduler

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* result tidak penting, kita re-check di runtime */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ChibiClawTheme {
                val setupComplete by securePreferences.setupComplete.collectAsState()

                Scaffold { padding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .background(MaterialTheme.colorScheme.background),
                    ) {
                        if (setupComplete) {
                            HomeNavigation(
                                router = router,
                                quotaTracker = quotaTracker,
                                sessionRotator = sessionRotator,
                                sessionExtractor = sessionExtractor,
                                standingInstructionRepo = standingInstructionRepo,
                                cronParser = cronParser,
                                memoryStore = memoryStore,
                                memoryRepository = memoryRepository,
                                taskManager = taskManager,
                                resourceScheduler = resourceScheduler,
                                auditLogger = auditLogger,
                            )
                        } else {
                            SetupNavigator(
                                shizukuManager = shizukuManager,
                                securePreferences = securePreferences,
                                sessionExtractor = sessionExtractor,
                                projectionTokenStore = projectionTokenStore,
                                onRequestOverlayPermission = { requestOverlayPermission() },
                                onSetupComplete = {
                                    securePreferences.setSetupComplete(true)
                                    auditLogger.log(
                                        actionType = AuditActionType.SETUP_COMPLETED,
                                        dataSummary = "User completed setup wizard",
                                    )
                                    ChibiService.start(this@MainActivity)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        if (Settings.canDrawOverlays(this)) return
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName"),
        )
        overlayPermissionLauncher.launch(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeNavigation(
    router: InferenceRouter,
    quotaTracker: AdapterQuotaTracker,
    sessionRotator: CloudSessionRotator,
    sessionExtractor: SessionExtractor,
    standingInstructionRepo: StandingInstructionRepository,
    cronParser: CronParser,
    memoryStore: MemoryStore,
    memoryRepository: MemoryRepository,
    taskManager: TaskManager,
    resourceScheduler: ResourceScheduler,
    auditLogger: com.chibiclaw.compliance.AuditLogger,
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        topBar = {
            if (currentRoute != null && currentRoute != "home") {
                TopAppBar(
                    title = { Text(routeTitle(currentRoute)) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            NavHost(navController = navController, startDestination = "home") {
                composable("home") {
                    HomeDashboardScreen(
                        onOpenChat = { navController.navigate("chat") },
                        onOpenTasks = { navController.navigate("tasks") },
                        onOpenAiEngine = { navController.navigate("settings/ai_engine") },
                        onOpenInitiative = { navController.navigate("initiative/list") },
                        onOpenMemory = { navController.navigate("memory/inspector") },
                        onOpenStats = { navController.navigate("debug/stats") },
                    )
                }
                composable("chat") {
                    ChatScreen(onOpenTask = { taskId -> navController.navigate("task/$taskId") })
                }
                composable("tasks") {
                    TaskListScreen(onOpenTask = { taskId -> navController.navigate("task/$taskId") })
                }
                composable("task/{id}") { backStackEntry ->
                    val id = backStackEntry.arguments?.getString("id") ?: return@composable
                    TaskDetailScreen(taskId = id)
                }
                composable("settings/ai_engine") {
                    AiEngineSettingsScreen(
                        router = router,
                        quotaTracker = quotaTracker,
                        rotator = sessionRotator,
                        sessionExtractor = sessionExtractor,
                    )
                }
                composable("initiative/list") {
                    StandingInstructionListScreen(
                        repository = standingInstructionRepo,
                        onCreateNew = { navController.navigate("initiative/edit/new") },
                        onEdit = { id -> navController.navigate("initiative/edit/$id") },
                    )
                }
                composable("initiative/edit/{id}") { backStackEntry ->
                    val raw = backStackEntry.arguments?.getString("id") ?: "new"
                    val editingId = if (raw == "new") null else raw
                    StandingInstructionEditorScreen(
                        repository = standingInstructionRepo,
                        cronParser = cronParser,
                        editingId = editingId,
                        onDone = { navController.popBackStack("initiative/list", inclusive = false) },
                        onCancel = { navController.popBackStack() },
                    )
                }
                composable("memory/inspector") {
                    MemoryInspectorScreen(
                        memoryStore = memoryStore,
                        memoryRepository = memoryRepository,
                    )
                }
                composable("debug/stats") {
                    ErrorStatsScreen(
                        taskManager = taskManager,
                        resourceScheduler = resourceScheduler,
                        auditLogger = auditLogger,
                    )
                }
            }
        }
    }
}

private fun routeTitle(route: String): String = when {
    route == "chat" -> "Chat"
    route == "tasks" -> "Tasks"
    route.startsWith("task/") -> "Task Detail"
    route == "settings/ai_engine" -> "AI Engine"
    route == "initiative/list" -> "Standing Instructions"
    route.startsWith("initiative/edit") -> "Edit Instruction"
    route == "memory/inspector" -> "Memory"
    route == "debug/stats" -> "Debug Stats"
    else -> "ChibiClaw"
}
