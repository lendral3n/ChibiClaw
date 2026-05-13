package com.chibiclaw.ui.setup

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.chibiclaw.compliance.AuditLogger
import com.chibiclaw.data.database.AuditActionType
import com.chibiclaw.data.database.AuditResultStatus
import com.chibiclaw.data.prefs.ConsentKey
import com.chibiclaw.data.prefs.SecurePreferences
import com.chibiclaw.service.overlay.OverlayWindowManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Consent step: SYSTEM_ALERT_WINDOW + POST_NOTIFICATIONS (Android 13+).
 *
 * Combined screen — keduanya berkaitan dengan overlay + foreground service.
 */
@Composable
fun ConsentOverlayScreen(
    onRequestPermission: () -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    viewModel: ConsentOverlayViewModel = hiltViewModel(),
) {
    val overlayGranted by viewModel.overlayGranted.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.refreshOverlayStatus(context)
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.setNotificationConsent(granted)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Text(
            text = "Akses Overlay & Notifikasi",
            style = MaterialTheme.typography.headlineLarge,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "ChibiClaw butuh dua izin teknis untuk mulai jalan:",
            style = MaterialTheme.typography.bodyLarge,
        )

        Spacer(Modifier.height(20.dp))

        PermissionRow(
            title = "Tampilkan di atas app lain",
            description = "Buat bubble Fuu yang floating di pojok layar. Kamu bisa drag, tap, atau dismiss kapan saja.",
            granted = overlayGranted,
            actionLabel = if (overlayGranted) "Sudah aktif" else "Buka Settings",
            enabled = !overlayGranted,
            onAction = {
                onRequestPermission()
                viewModel.logPermissionRequested("overlay")
            },
        )

        Spacer(Modifier.height(16.dp))

        PermissionRow(
            title = "Notifikasi",
            description = "Buat status service (foreground service wajib di Android modern) + lapor saat task selesai.",
            granted = viewModel.notifGranted.collectAsState().value,
            actionLabel = if (viewModel.notifGranted.collectAsState().value) "Sudah aktif" else "Beri akses",
            enabled = !viewModel.notifGranted.collectAsState().value,
            onAction = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    viewModel.setNotificationConsent(true)
                }
            },
        )

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = onSkip,
            ) {
                Text("Skip")
            }
            Button(
                modifier = Modifier.weight(2f),
                onClick = onContinue,
            ) {
                Text("Lanjut →")
            }
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    enabled: Boolean,
    onAction: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Text(
                text = if (granted) "✓" else "○",
                style = MaterialTheme.typography.titleLarge,
                color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(text = description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onAction,
            enabled = enabled,
        ) {
            Text(actionLabel)
        }
    }
}

@HiltViewModel
class ConsentOverlayViewModel @Inject constructor(
    private val overlayWindowManager: OverlayWindowManager,
    private val securePreferences: SecurePreferences,
    private val auditLogger: AuditLogger,
) : ViewModel() {

    private val _overlayGranted = MutableStateFlow(false)
    val overlayGranted: StateFlow<Boolean> = _overlayGranted

    private val _notifGranted = MutableStateFlow(false)
    val notifGranted: StateFlow<Boolean> = _notifGranted

    init {
        _notifGranted.value = securePreferences.consent(ConsentKey.NOTIFICATION_POST)
        _overlayGranted.value = securePreferences.consent(ConsentKey.OVERLAY)
    }

    fun refreshOverlayStatus(context: android.content.Context) {
        val canDraw = overlayWindowManager.canDrawOverlays()
        _overlayGranted.value = canDraw
        if (canDraw && !securePreferences.consent(ConsentKey.OVERLAY)) {
            securePreferences.setConsent(ConsentKey.OVERLAY, true)
            auditLogger.log(
                actionType = AuditActionType.CONSENT_GRANTED,
                dataSummary = "User granted overlay permission",
            )
        }
    }

    fun setNotificationConsent(granted: Boolean) {
        _notifGranted.value = granted
        securePreferences.setConsent(ConsentKey.NOTIFICATION_POST, granted)
        auditLogger.log(
            actionType = if (granted) AuditActionType.CONSENT_GRANTED else AuditActionType.CONSENT_REVOKED,
            dataSummary = "Notification permission ${if (granted) "granted" else "denied"}",
            resultStatus = if (granted) AuditResultStatus.SUCCESS else AuditResultStatus.USER_DENIED,
        )
    }

    fun logPermissionRequested(name: String) {
        auditLogger.log(
            actionType = AuditActionType.PERMISSION_REQUESTED,
            dataSummary = "Requested permission: $name",
        )
    }
}
