package com.chibiclaw.ui.setup

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Phase 3 setup: arahkan user enable Accessibility Service ChibiClaw.
 * Optional — user bisa "Nanti aja" kalau belum mau ngasih.
 */
@Composable
fun AccessibilitySetupScreen(
    onContinue: () -> Unit,
    onSkip: () -> Unit,
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Akses UI (Accessibility)",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = "Supaya Fuu bisa baca layar & ngeklik tombol di app lain, " +
                "aktifkan Accessibility Service ChibiClaw di Settings.",
            style = MaterialTheme.typography.bodyMedium,
        )

        Text(
            text = "Risiko: layanan ini punya akses penuh ke konten layar. " +
                "Semua aksi dicatat di Audit Log dan bisa di-revoke kapan saja " +
                "dari Settings → Accessibility.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            },
            contentPadding = PaddingValues(vertical = 14.dp),
            modifier = Modifier.fillMaxSize().height(56.dp),
        ) {
            Text("Buka Settings → Accessibility")
        }

        Spacer(Modifier.height(4.dp))

        Button(
            onClick = onContinue,
            contentPadding = PaddingValues(vertical = 14.dp),
            modifier = Modifier.fillMaxSize().height(56.dp),
        ) {
            Text("Sudah aktifkan, lanjut")
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
