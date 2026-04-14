package com.chibiclaw.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chibiclaw.debug.DevModeManager
import com.chibiclaw.memory.pref.SecurePreferences
import com.chibiclaw.ui.bootstrap.BootstrapScreen
import com.chibiclaw.ui.chat.ChatScreen
import com.chibiclaw.ui.dashboard.DashboardScreen
import com.chibiclaw.ui.debug.DevConsoleScreen
import com.chibiclaw.ui.mode.ModeSelectionScreen
import com.chibiclaw.ui.onboarding.PermissionWizardScreen
import com.chibiclaw.ui.persona.PersonaEditorScreen
import com.chibiclaw.ui.settings.AiSettingsScreen
import com.chibiclaw.ui.settings.CronSettingsScreen
import com.chibiclaw.ui.settings.NotificationSettingsScreen
import com.chibiclaw.ui.settings.SafetySettingsScreen
import com.chibiclaw.ui.settings.SettingsHubScreen
import com.chibiclaw.ui.setup.SetupWizardScreen
import com.chibiclaw.ui.skills.SkillEditorScreen
import com.chibiclaw.ui.theme.ChibiClawTheme
import com.chibiclaw.ui.theme.Purple40
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Phase 10 — entry point.
 *
 * Navigation graph:
 *
 *   setup        (first boot, one-time)
 *     → mode_select
 *       → bootstrap/{mode}   ← NEW: heavy init runs here, behind a
 *                              pulsing-mascot loading screen
 *         → home / chat / settings (bottom nav shell)
 *
 * **Nothing heavy runs before bootstrap**. ChibiService is no longer
 * started in onCreate() — it starts from [com.chibiclaw.core.ChibiBootstrapper]
 * which is triggered by [BootstrapScreen] once the user picks a mode.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var securePreferences: SecurePreferences
    @Inject lateinit var devModeManager: DevModeManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Decide start destination up front so we don't flicker.
        // Fresh install? → setup wizard
        // Setup done?   → mode selection (lazy, zero heavy init)
        val startDest = if (securePreferences.isSetupComplete()) "mode_select" else "setup"

        setContent {
            ChibiClawTheme {
                val navController = rememberNavController()
                val backStack by navController.currentBackStackEntryAsState()
                val currentRoute = backStack?.destination?.route
                val isDevMode by devModeManager.isDevMode.collectAsState()

                val mainRoutes = setOf("home", "chat", "settings")
                val showBottomBar = currentRoute in mainRoutes

                Scaffold(
                    bottomBar = {
                        if (showBottomBar) {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = currentRoute == "home",
                                    onClick = { navController.navigate("home") { launchSingleTop = true } },
                                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                                    label = { Text("Home") }
                                )
                                NavigationBarItem(
                                    selected = currentRoute == "chat",
                                    onClick = { navController.navigate("chat") { launchSingleTop = true } },
                                    icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null) },
                                    label = { Text("Chat") }
                                )
                                NavigationBarItem(
                                    selected = currentRoute == "settings",
                                    onClick = { navController.navigate("settings") { launchSingleTop = true } },
                                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                    label = { Text("Settings") }
                                )
                                if (isDevMode) {
                                    NavigationBarItem(
                                        selected = currentRoute == "dev_console",
                                        onClick = { navController.navigate("dev_console") { launchSingleTop = true } },
                                        icon = {
                                            Box {
                                                Icon(Icons.Default.BugReport, contentDescription = null)
                                                Text(
                                                    "DEV",
                                                    fontSize = 6.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.Black,
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(top = 1.dp)
                                                )
                                            }
                                        },
                                        label = { Text("Dev", fontSize = 10.sp) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = Purple40,
                                            selectedTextColor = Purple40,
                                            indicatorColor = Purple40.copy(alpha = 0.15f)
                                        )
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = startDest,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("mode_select") {
                            ModeSelectionScreen(
                                onUserMode = {
                                    navController.navigate("bootstrap/user") {
                                        popUpTo("mode_select") { inclusive = true }
                                    }
                                },
                                onDevMode = {
                                    navController.navigate("bootstrap/dev") {
                                        popUpTo("mode_select") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable(
                            route = "bootstrap/{mode}",
                            arguments = listOf(navArgument("mode") { type = NavType.StringType })
                        ) { entry ->
                            val mode = entry.arguments?.getString("mode") ?: "user"
                            BootstrapScreen(
                                modeLabel = if (mode == "dev") "DEVELOPER" else "USER",
                                onReady = {
                                    navController.navigate("home") {
                                        popUpTo("bootstrap/{mode}") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("setup") {
                            SetupWizardScreen(
                                onSetupComplete = {
                                    navController.navigate("mode_select") {
                                        popUpTo("setup") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("home") {
                            DashboardScreen(
                                onNavigateToChat = {
                                    navController.navigate("chat") { launchSingleTop = true }
                                },
                                onNavigateToPermissions = {
                                    navController.navigate("permissions")
                                }
                            )
                        }
                        composable("chat") { ChatScreen() }
                        composable("settings") {
                            SettingsHubScreen(
                                onNavigateToAi = { navController.navigate("settings_ai") },
                                onNavigateToSafety = { navController.navigate("settings_safety") },
                                onNavigateToSkills = { navController.navigate("settings_skills") },
                                onNavigateToPersona = { navController.navigate("settings_persona") },
                                onNavigateToCron = { navController.navigate("settings_cron") },
                                onNavigateToNotifications = { navController.navigate("settings_notifications") }
                            )
                        }
                        composable("settings_ai") { AiSettingsScreen() }
                        composable("settings_safety") { SafetySettingsScreen() }
                        composable("settings_skills") { SkillEditorScreen() }
                        composable("settings_persona") { PersonaEditorScreen() }
                        composable("settings_cron") { CronSettingsScreen() }
                        composable("settings_notifications") { NotificationSettingsScreen() }
                        composable("permissions") {
                            PermissionWizardScreen(
                                onDone = { navController.popBackStack() }
                            )
                        }
                        composable("dev_console") {
                            DevConsoleScreen(onBack = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
    }
}
