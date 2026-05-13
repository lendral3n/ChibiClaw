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
import com.chibiclaw.compliance.AuditLogger
import com.chibiclaw.data.database.AuditActionType
import com.chibiclaw.data.prefs.SecurePreferences
import com.chibiclaw.service.ChibiService
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
                            MinimalHomePlaceholder()
                        } else {
                            SetupNavigator(
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
private fun MinimalHomePlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "ChibiClaw Phase 0\n" +
                "Service jalan, bubble overlay aktif.\n" +
                "Phase 1+ akan tambah agent runtime di sini.",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
