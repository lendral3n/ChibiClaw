package com.chibiclaw.ui.home

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
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * HomeDashboardScreen — entry point Phase 9 untuk navigasi ke fitur lain.
 *
 * Lendra bisa pilih: Chat (interactive), Tasks history, AI Engine settings,
 * Standing Instructions, Memory Inspector, Debug Stats. Setup wizard sudah
 * jalan saat install — dashboard ini muncul after setup_complete.
 */
@Composable
fun HomeDashboardScreen(
    onOpenChat: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenAiEngine: () -> Unit,
    onOpenInitiative: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenStats: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "ChibiClaw",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Fuu siap bantu. Pilih menu di bawah, atau langsung chat.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = onOpenChat,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            Text("💬  Chat dengan Fuu", style = MaterialTheme.typography.titleMedium)
        }

        DashboardCard(
            title = "Tasks",
            subtitle = "Riwayat & status task aktif",
            onClick = onOpenTasks,
        )
        DashboardCard(
            title = "AI Engine",
            subtitle = "Adapter status + quota + re-login session",
            onClick = onOpenAiEngine,
        )
        DashboardCard(
            title = "Standing Instructions",
            subtitle = "Direktif proaktif — Fuu kerja sendiri saat trigger",
            onClick = onOpenInitiative,
        )
        DashboardCard(
            title = "Memory",
            subtitle = "Browse memory per kategori; edit / delete",
            onClick = onOpenMemory,
        )
        DashboardCard(
            title = "Debug Stats",
            subtitle = "Slot & resource usage + error counts 7 hari",
            onClick = onOpenStats,
        )
    }
}

@Composable
private fun DashboardCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    ElevatedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(0.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
