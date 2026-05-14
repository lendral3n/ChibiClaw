package com.chibiclaw.ui.setup

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
 *
 * Phase 9 update: tambah Xiaomi HyperOS / MIUI step-by-step + "Allow restricted
 * settings" path untuk sideload (Android 13+).
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

        // Xiaomi HyperOS / Android 13+ sideload step-by-step
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Step di Xiaomi / Android 13+:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text("1. Tap 'Buka App Info' di bawah", style = MaterialTheme.typography.bodySmall)
                Text(
                    "2. Tap menu 3-titik kanan atas → \"Allow restricted settings\" → konfirmasi PIN/fingerprint",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "3. Tap 'Buka Accessibility' di bawah → cari ChibiClaw → toggle ON → Allow",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Catatan HyperOS: kalau menu 'Accessibility' tidak langsung, coba lewat " +
                        "Settings → Additional settings → Accessibility → Downloaded apps.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            text = "Risiko: layanan ini punya akses penuh ke konten layar. " +
                "Semua aksi dicatat di Audit Log dan bisa di-revoke kapan saja " +
                "dari Settings → Accessibility.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(4.dp))

        // Step 1: App Info → Allow restricted settings (sideload unlock)
        OutlinedButton(
            onClick = {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}"),
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            },
            contentPadding = PaddingValues(vertical = 14.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text("1. Buka App Info (untuk Allow restricted settings)")
        }

        // Step 2: Accessibility Settings
        Button(
            onClick = {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            },
            contentPadding = PaddingValues(vertical = 14.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text("2. Buka Settings → Accessibility")
        }

        Spacer(Modifier.height(4.dp))

        Button(
            onClick = onContinue,
            contentPadding = PaddingValues(vertical = 14.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text("Sudah aktifkan, lanjut")
        }

        OutlinedButton(
            onClick = onSkip,
            contentPadding = PaddingValues(vertical = 14.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text("Nanti aja")
        }
    }
}
