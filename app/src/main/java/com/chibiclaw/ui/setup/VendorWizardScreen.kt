package com.chibiclaw.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.chibiclaw.compliance.AuditLogger
import com.chibiclaw.data.database.AuditActionType
import com.chibiclaw.data.prefs.SecurePreferences
import com.chibiclaw.util.KillLevel
import com.chibiclaw.util.VendorDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Vendor wizard Phase 0.
 *
 * Auto-detect manufacturer (Xiaomi, Oppo, Vivo, etc), tampilkan instruksi
 * spesifik supaya user setup battery exception + autostart manual.
 */
@Composable
fun VendorWizardScreen(
    onContinue: () -> Unit,
    viewModel: VendorWizardViewModel = hiltViewModel(),
) {
    val vendor = remember { VendorDetector.current() }
    val guidance = remember(vendor) { VendorDetector.guidanceFor(vendor) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "Setup HP kamu",
            style = MaterialTheme.typography.headlineLarge,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = guidance.displayName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Vendor HP kamu butuh setup khusus supaya ChibiClaw tetap hidup di background. Tanpa ini, service bisa dimatikan dalam 5-15 menit.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        KillLevelBadge(guidance.killAggressiveness)

        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Langkah yang harus dilakukan:",
                    style = MaterialTheme.typography.titleMedium,
                )
                guidance.steps.forEachIndexed { idx, step ->
                    Text(
                        text = "${idx + 1}. $step",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "ChibiClaw tidak bisa setup ini otomatis (sistem operasi proteksi). Setelah selesai manual, tap Selesai.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                viewModel.markDone(vendor.name)
                onContinue()
            },
        ) {
            Text("Selesai →")
        }
    }
}

@Composable
private fun KillLevelBadge(level: KillLevel) {
    val (label, container) = when (level) {
        KillLevel.LOW -> "Risiko mati: rendah" to MaterialTheme.colorScheme.primaryContainer
        KillLevel.MEDIUM -> "Risiko mati: sedang" to MaterialTheme.colorScheme.tertiaryContainer
        KillLevel.HIGH -> "Risiko mati: tinggi" to MaterialTheme.colorScheme.errorContainer
        KillLevel.VERY_HIGH -> "Risiko mati: sangat tinggi" to MaterialTheme.colorScheme.errorContainer
    }
    Card(
        shape = RoundedCornerShape(999.dp),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

@HiltViewModel
class VendorWizardViewModel @Inject constructor(
    private val securePreferences: SecurePreferences,
    private val auditLogger: AuditLogger,
) : ViewModel() {

    fun markDone(vendorName: String) {
        securePreferences.setVendorWizardDone(true)
        auditLogger.log(
            actionType = AuditActionType.SETUP_COMPLETED,
            dataSummary = "Vendor wizard completed: $vendorName",
        )
    }
}
