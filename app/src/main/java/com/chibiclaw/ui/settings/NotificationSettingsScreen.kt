package com.chibiclaw.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chibiclaw.ui.components.AppPickerDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    viewModel: NotificationSettingsViewModel = hiltViewModel()
) {
    val whitelist by viewModel.whitelist.collectAsState()
    val whitelistLabeled by viewModel.whitelistLabeled.collectAsState()
    val keywords by viewModel.keywords.collectAsState()

    var showAppPicker by remember { mutableStateOf(false) }
    var showKeywordDialog by remember { mutableStateOf(false) }
    var keywordInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notification Triggers") },
                actions = {
                    IconButton(onClick = { viewModel.resetDefaults() }) {
                        Icon(Icons.Default.Restore, contentDescription = "Reset defaults")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
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
                    "Fuu akan membaca notifikasi dari app yang ada di whitelist dan " +
                        "memproses perintah jika notifikasi tersebut mengandung salah " +
                        "satu kata kunci di bawah.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── Whitelist section ────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Whitelist App (${whitelist.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedButton(
                        onClick = { showAppPicker = true }
                    ) {
                        Icon(Icons.Default.Apps, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Pilih App")
                    }
                }
            }

            if (whitelist.isEmpty()) {
                item {
                    EmptyHint("Belum ada app. Tekan 'Pilih App' untuk memilih dari daftar terinstall.")
                }
            }
            items(whitelist.sorted()) { pkg ->
                AppWhitelistRow(
                    packageName = pkg,
                    label = whitelistLabeled[pkg] ?: pkg,
                    onRemove = { viewModel.removePackage(pkg) }
                )
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }

            // ── Keywords section ─────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Kata Kunci Pemicu (${keywords.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = {
                        keywordInput = ""
                        showKeywordDialog = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Add keyword")
                    }
                }
            }
            if (keywords.isEmpty()) {
                item { EmptyHint("Belum ada kata kunci. Tekan + untuk menambah.") }
            }
            items(keywords.sorted()) { kw ->
                ChipRow(label = kw, onRemove = { viewModel.removeKeyword(kw) })
            }
        }
    }

    // App picker — replaces the old "type the package name" dialog
    if (showAppPicker) {
        AppPickerDialog(
            multiSelect = true,
            preselected = whitelist,
            title = "Pilih App untuk Auto-Reply",
            onDismiss = { showAppPicker = false },
            onConfirm = { picked ->
                viewModel.setWhitelist(picked)
                showAppPicker = false
            }
        )
    }

    // Keyword add dialog
    if (showKeywordDialog) {
        AlertDialog(
            onDismissRequest = { showKeywordDialog = false },
            title = { Text("Tambah Kata Kunci") },
            text = {
                OutlinedTextField(
                    value = keywordInput,
                    onValueChange = { keywordInput = it },
                    label = { Text("Kata kunci (mis. balas, @fuu)") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addKeyword(keywordInput)
                        showKeywordDialog = false
                    }
                ) { Text("Tambah") }
            },
            dismissButton = {
                TextButton(onClick = { showKeywordDialog = false }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun AppWhitelistRow(packageName: String, label: String, onRemove: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (label != packageName) {
                    Text(
                        packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun ChipRow(label: String, onRemove: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}
