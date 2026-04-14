package com.chibiclaw.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chibiclaw.perception.PermissionStatus
import com.chibiclaw.ui.theme.*

/**
 * Phase 10 — Permission Wizard.
 *
 * Single-screen checklist of every permission [PermissionStatus]
 * tracks (accessibility, notification listener, overlay, usage access,
 * runtime grants, write settings). Each row renders:
 *
 *   ● granted    → green check, no button
 *   ○ missing    → "Grant" button that launches the matching
 *                  settings intent
 *
 * Required vs optional permissions are grouped so the user sees
 * the blocking ones first. A 1.5s poll means flipping a toggle in
 * Settings updates the row automatically when they return.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionWizardScreen(
    onDone: () -> Unit,
    viewModel: PermissionWizardViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val entries by viewModel.entries.collectAsState()
    val required = entries.filter { it.required }
    val optional = entries.filter { !it.required }
    val allRequiredOk = required.all { it.granted }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Izin Aplikasi",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (allRequiredOk) "Semua izin wajib terpenuhi"
                            else "${required.count { !it.granted }} izin wajib belum diberikan",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (allRequiredOk) StateCompleted else StateError
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Button(
                    onClick = onDone,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (allRequiredOk) Purple40
                        else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (allRequiredOk) Color.White
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        if (allRequiredOk) "Selesai" else "Lewati untuk sekarang",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Wajib",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            items(required) { entry ->
                PermissionRow(
                    entry = entry,
                    onGrant = {
                        entry.grantIntent?.let {
                            runCatching { context.startActivity(it) }
                        }
                    }
                )
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }

            item {
                Text(
                    "Opsional",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            items(optional) { entry ->
                PermissionRow(
                    entry = entry,
                    onGrant = {
                        entry.grantIntent?.let {
                            runCatching { context.startActivity(it) }
                        }
                    }
                )
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun PermissionRow(
    entry: PermissionStatus.PermissionEntry,
    onGrant: () -> Unit
) {
    val bg = when {
        entry.granted -> StateCompleted.copy(alpha = 0.10f)
        entry.required -> StateError.copy(alpha = 0.08f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = bg),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            entry.granted -> StateCompleted.copy(alpha = 0.18f)
                            entry.required -> StateError.copy(alpha = 0.18f)
                            else -> MaterialTheme.colorScheme.surface
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (entry.granted) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = StateCompleted,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Icon(
                        Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (entry.required) StateError else OnSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    if (entry.granted) "Diberikan" else
                        if (entry.required) "Wajib — belum aktif"
                        else "Opsional",
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        entry.granted -> StateCompleted
                        entry.required -> StateError
                        else -> OnSurfaceVariant
                    }
                )
            }
            if (!entry.granted && entry.grantIntent != null) {
                TextButton(
                    onClick = onGrant,
                    colors = ButtonDefaults.textButtonColors(contentColor = Purple40)
                ) { Text("Buka") }
            }
        }
    }
}
