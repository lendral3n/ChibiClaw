package com.chibiclaw.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chibiclaw.safety.AutoControlConfig
import com.chibiclaw.ui.components.AppPickerDialog
import com.chibiclaw.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SafetySettingsScreen(
    viewModel: SafetySettingsViewModel = hiltViewModel()
) {
    val whitelist by viewModel.whitelist.collectAsState()
    val executionLog by viewModel.executionLog.collectAsState()
    val logFilter by viewModel.logFilter.collectAsState()
    val autoControlConfigs by viewModel.autoControlConfigs.collectAsState()
    val autoControlLabels by viewModel.autoControlLabels.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var newPackage by remember { mutableStateOf("") }
    var showAutoCtrlPicker by remember { mutableStateOf(false) }
    var expandedPkg by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Safety Settings") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ─── Auto-Control per-app ────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Auto-Control App (${autoControlConfigs.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Atur per-app: boleh buka (foreground), stealth (background + Shizuku), " +
                                "dan aksi yang diizinkan. App tanpa entry = default: foreground only.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(onClick = { showAutoCtrlPicker = true }) {
                        Icon(Icons.Default.Apps, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Tambah")
                    }
                }
            }

            if (autoControlConfigs.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "Belum ada app yang dikonfigurasi. Semua app jalan dengan default " +
                                "(foreground-only, semua aksi diizinkan).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            items(autoControlConfigs, key = { "ac_${it.packageName}" }) { cfg ->
                AutoControlCard(
                    config = cfg,
                    label = autoControlLabels[cfg.packageName] ?: cfg.packageName,
                    expanded = expandedPkg == cfg.packageName,
                    onExpandToggle = {
                        expandedPkg = if (expandedPkg == cfg.packageName) null else cfg.packageName
                    },
                    onToggleForeground = { viewModel.toggleForeground(cfg.packageName, it) },
                    onToggleBackground = { viewModel.toggleBackground(cfg.packageName, it) },
                    onToggleAction = { action, enabled ->
                        viewModel.toggleAction(cfg.packageName, action, enabled)
                    },
                    onRemove = { viewModel.removeAutoControl(cfg.packageName) }
                )
            }

            item { HorizontalDivider() }

            // ─── AIDL Caller Whitelist ───────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "AIDL Caller Whitelist",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add")
                    }
                }
            }

            if (whitelist.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "Kosong — semua caller diizinkan",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            items(whitelist.toList()) { pkg ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            pkg,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.removeFromWhitelist(pkg) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = StateError)
                        }
                    }
                }
            }

            item { HorizontalDivider() }

            // ─── Execution Log ───────────────────────────────────────────
            item {
                Text(
                    "Execution Log",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("ALL", "SUCCESS", "FAILED", "BLOCKED").forEach { filter ->
                        FilterChip(
                            selected = logFilter == filter,
                            onClick = { viewModel.setLogFilter(filter) },
                            label = { Text(filter, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            items(executionLog) { entry ->
                val (color, label) = when (entry.state) {
                    "COMPLETED" -> StateCompleted to "OK"
                    "ERROR" -> StateError to "ERR"
                    "BLOCKED" -> StateWaiting to "BLK"
                    else -> StateIdle to entry.state
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = color.copy(alpha = 0.15f)
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                color = color,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(entry.command, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            Text(
                                "Severity: ${entry.severity}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Tambah Package") },
            text = {
                OutlinedTextField(
                    value = newPackage,
                    onValueChange = { newPackage = it },
                    label = { Text("Package name (e.g. com.example.app)") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPackage.isNotBlank()) {
                            viewModel.addToWhitelist(newPackage.trim())
                            newPackage = ""
                            showAddDialog = false
                        }
                    }
                ) { Text("Tambah") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Batal") }
            }
        )
    }

    if (showAutoCtrlPicker) {
        AppPickerDialog(
            multiSelect = false,
            title = "Pilih App untuk Auto-Control",
            onDismiss = { showAutoCtrlPicker = false },
            onConfirm = { picked ->
                picked.firstOrNull()?.let { pkg ->
                    viewModel.addAutoControl(pkg)
                    expandedPkg = pkg
                }
                showAutoCtrlPicker = false
            }
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AutoControlCard(
    config: AutoControlConfig,
    label: String,
    expanded: Boolean,
    onExpandToggle: () -> Unit,
    onToggleForeground: (Boolean) -> Unit,
    onToggleBackground: (Boolean) -> Unit,
    onToggleAction: (String, Boolean) -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (label != config.packageName) {
                        Text(
                            config.packageName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Compact summary when collapsed
                    val summary = buildString {
                        if (config.foregroundEnabled) append("FG ")
                        if (config.backgroundEnabled) append("BG ")
                        if (!config.foregroundEnabled && !config.backgroundEnabled) append("DISABLED ")
                        if (config.allowedActions.isNotEmpty()) {
                            append("• ${config.allowedActions.size} actions")
                        } else {
                            append("• all actions")
                        }
                    }
                    Text(
                        summary,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onExpandToggle) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand"
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = StateError)
                }
            }

            if (expanded) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                // Foreground toggle
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Foreground mode",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "App dibuka sebentar, Fuu tap/type via Accessibility. Kompatibel semua device.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = config.foregroundEnabled,
                        onCheckedChange = onToggleForeground
                    )
                }
                Spacer(Modifier.height(8.dp))

                // Background toggle
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Background mode (Shizuku)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Stealth — tanpa buka app di layar. Butuh Shizuku aktif.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = config.backgroundEnabled,
                        onCheckedChange = onToggleBackground
                    )
                }
                Spacer(Modifier.height(12.dp))

                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text(
                    "Aksi yang diizinkan",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(6.dp))

                val allActions = listOf("launch", "tap", "type", "scroll", "back", "intent")
                val effective = if (config.allowedActions.isEmpty()) allActions.toSet() else config.allowedActions
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    allActions.forEach { action ->
                        FilterChip(
                            selected = action in effective,
                            onClick = {
                                onToggleAction(action, action !in effective)
                            },
                            label = { Text(action, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }
    }
}
