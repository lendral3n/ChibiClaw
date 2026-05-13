package com.chibiclaw.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chibiclaw.permissions.ShizukuManager
import kotlinx.coroutines.delay

/**
 * Phase 3 setup: arahkan user pasang Shizuku app + start service via ADB
 * (atau Sui kalau root). Optional — privileged tools (force-stop, grant
 * permission) tidak available kalau Shizuku tidak aktif, tapi app tetap
 * fungsi normal.
 */
@Composable
fun ShizukuSetupScreen(
    shizukuManager: ShizukuManager,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
) {
    var shizukuOk by remember { mutableStateOf(false) }
    var permOk by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            shizukuOk = shizukuManager.isShizukuAvailable()
            permOk = shizukuManager.hasPermission()
            delay(1500)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Akses Privileged (Shizuku)",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = "Buat aksi level ADB — force-stop app, grant permission system, " +
                "set system settings — Fuu butuh Shizuku. Tanpa Shizuku, " +
                "fitur lain tetap jalan; cuma privileged tools yang non-aktif.",
            style = MaterialTheme.typography.bodyMedium,
        )

        Text(
            text = "Cara setup: install app Shizuku dari Play Store, lalu jalanin " +
                "lewat ADB (wireless atau kabel). Detail di shizuku.rikka.app.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Status Shizuku service: " + if (shizukuOk) "✅ aktif" else "⛔ belum aktif",
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            text = "Permission ChibiClaw: " + if (permOk) "✅ granted" else "⛔ belum granted",
            style = MaterialTheme.typography.labelMedium,
        )

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = onContinue,
            contentPadding = PaddingValues(vertical = 14.dp),
            modifier = Modifier.fillMaxSize().height(56.dp),
        ) {
            Text(if (shizukuOk && permOk) "Lanjut" else "Lanjut (skip privileged tools)")
        }

        OutlinedButton(
            onClick = onSkip,
            contentPadding = PaddingValues(vertical = 14.dp),
            modifier = Modifier.fillMaxSize().height(56.dp),
        ) {
            Text("Nanti aja")
        }
    }
}
