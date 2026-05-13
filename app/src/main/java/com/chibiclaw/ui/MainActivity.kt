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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.chibiclaw.compliance.AuditLogger
import com.chibiclaw.data.database.AuditActionType
import com.chibiclaw.data.prefs.SecurePreferences
import com.chibiclaw.permissions.ShizukuManager
import com.chibiclaw.service.ChibiService
import com.chibiclaw.ui.chat.ChatScreen
import com.chibiclaw.ui.debug.TaskDetailScreen
import com.chibiclaw.ui.debug.TaskListScreen
import com.chibiclaw.ui.setup.SetupNavigator
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
                            HomeNavigation()
                        } else {
                            SetupNavigator(
                                shizukuManager = shizukuManager,
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

@Composable
private fun HomeNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "chat") {
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
    }
}
