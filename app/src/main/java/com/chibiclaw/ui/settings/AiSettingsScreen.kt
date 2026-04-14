package com.chibiclaw.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chibiclaw.ai.EngineState
import com.chibiclaw.ai.ModelEntry
import com.chibiclaw.ui.setup.ImportState
import com.chibiclaw.ui.theme.*
import com.chibiclaw.util.AccessibilityGuide
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SettingsAccessibilityEntryPoint {
    fun accessibilityGuide(): AccessibilityGuide
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    viewModel: AiSettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val libraryModels by viewModel.libraryModels.collectAsState()
    val activeModelId by viewModel.activeModelId.collectAsState()
    val backend by viewModel.backend.collectAsState()
    val engineState by viewModel.engineState.collectAsState()
    val importState by viewModel.importState.collectAsState()
    val dryRunEnabled by viewModel.dryRunEnabled.collectAsState()

    val guide = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            SettingsAccessibilityEntryPoint::class.java
        ).accessibilityGuide()
    }
    // Poll every 2s so state auto-updates when user comes back from Settings.
    var accessibilityEnabled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            accessibilityEnabled = guide.isServiceEnabled()
            delay(2000)
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importFromUri(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Engine status card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when (engineState) {
                        EngineState.READY -> StateCompleted.copy(alpha = 0.1f)
                        EngineState.LOADING -> StatePlanning.copy(alpha = 0.1f)
                        EngineState.ERROR -> StateError.copy(alpha = 0.1f)
                        EngineState.UNLOADED -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (engineState) {
                        EngineState.READY -> Icon(Icons.Default.CheckCircle, null, tint = StateCompleted, modifier = Modifier.size(28.dp))
                        EngineState.LOADING -> CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp, color = StatePlanning)
                        else -> Icon(Icons.Default.Memory, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
                    }
                    Column {
                        Text(
                            "Gemma Engine",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            when (engineState) {
                                EngineState.READY -> "READY — Model aktif"
                                EngineState.LOADING -> "Memuat model..."
                                EngineState.ERROR -> "Gagal memuat"
                                EngineState.UNLOADED -> "Tidak dimuat"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = when (engineState) {
                                EngineState.READY -> StateCompleted
                                EngineState.LOADING -> StatePlanning
                                EngineState.ERROR -> StateError
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Library Model",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${libraryModels.size} model",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                "Upload beberapa file .litertlm ke library, pilih satu yang aktif. " +
                    "Mirip model switcher di ChatGPT/Claude.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Import state feedback
            when (val s = importState) {
                is ImportState.InProgress -> ImportProgressCard(s.percent, s.copiedMb, s.totalMb)
                is ImportState.Failed -> ImportErrorCard(s.message) {
                    viewModel.resetImportState()
                    filePickerLauncher.launch(arrayOf("*/*"))
                }
                else -> {}
            }

            // Library list
            if (libraryModels.isNotEmpty()) {
                libraryModels.forEach { entry ->
                    AiSettingsModelRow(
                        entry = entry,
                        isActive = entry.id == activeModelId,
                        engineState = if (entry.id == activeModelId) engineState else EngineState.UNLOADED,
                        onSelect = { viewModel.selectModel(entry.id) },
                        onRemove = { viewModel.removeModel(entry.id) }
                    )
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "Belum ada model di library. Tekan tombol di bawah untuk upload " +
                            "file .litertlm dari penyimpanan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Add new model
            if (importState !is ImportState.InProgress) {
                OutlinedButton(
                    onClick = {
                        viewModel.resetImportState()
                        filePickerLauncher.launch(arrayOf("*/*"))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (libraryModels.isEmpty()) "Tambah Model Pertama"
                        else "Tambah Model Lain",
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    "File akan disalin ke folder private app (±4 GB, sekali per model).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Backend selector
            Text("Backend", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("GPU", "CPU").forEach { b ->
                    FilterChip(
                        selected = backend == b,
                        onClick = { viewModel.setBackend(b) },
                        label = { Text(b) }
                    )
                }
            }

            HorizontalDivider()

            // P10 — Dry-Run Mode toggle. When ON, every side-effectful
            // action (intent / gesture / UI interact / Shizuku / alarm)
            // is intercepted by DryRunMode.simulate() and returns a
            // "[dry] ..." preview string instead of actually firing.
            // Read-only ops (ScanUi / MemoryQuery / ContentQuery /
            // VisionAnalyze / Report / AskUser / Wait) still run so
            // the planner keeps fresh grounded context.
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (dryRunEnabled)
                        StatePlanning.copy(alpha = 0.14f)
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Science,
                        contentDescription = null,
                        tint = if (dryRunEnabled) StatePlanning
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Dry-Run Mode",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (dryRunEnabled) StatePlanning
                            else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            if (dryRunEnabled)
                                "Aktif — semua aksi preview saja"
                            else
                                "Preview mode — aksi ditulis ke log, tidak dieksekusi",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = dryRunEnabled,
                        onCheckedChange = { viewModel.setDryRun(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = StatePlanning,
                            checkedTrackColor = StatePlanning.copy(alpha = 0.4f)
                        )
                    )
                }
            }

            HorizontalDivider()

            // Permissions — accessibility status + HyperOS workaround
            Text(
                "Izin",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (accessibilityEnabled)
                        StateCompleted.copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Accessibility,
                        contentDescription = null,
                        tint = if (accessibilityEnabled) StateCompleted
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Accessibility Service",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            if (accessibilityEnabled) "Aktif" else "Belum aktif",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (accessibilityEnabled) StateCompleted else StateError
                        )
                    }
                    if (!accessibilityEnabled) {
                        TextButton(onClick = { guide.openAccessibilitySettings() }) {
                            Text("Buka")
                        }
                    }
                }
            }

            if (!accessibilityEnabled) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = StatePlanning.copy(alpha = 0.10f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "Android 13+ Restricted Settings",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = StatePlanning
                        )
                        Text(
                            "ChibiClaw tidak muncul di daftar Accessibility karena " +
                                "fitur keamanan Android. Ikuti 3 langkah ini (1x saja):",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "1. Buka Accessibility → tap ChibiClaw → abaikan dialog \"Restricted setting\"",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "2. Buka App Info → ⋮ pojok kanan atas → \"Allow restricted settings\"",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "3. Kembali ke Accessibility → toggle ON",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { guide.openAccessibilitySettings() }) {
                                Text("1. Accessibility")
                            }
                            OutlinedButton(onClick = { guide.openAppInfoPage() }) {
                                Text("2. App Info")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AiSettingsModelRow(
    entry: ModelEntry,
    isActive: Boolean,
    engineState: EngineState,
    onSelect: () -> Unit,
    onRemove: () -> Unit
) {
    val containerColor = when {
        isActive && engineState == EngineState.READY -> StateCompleted.copy(alpha = 0.12f)
        isActive && engineState == EngineState.LOADING -> StatePlanning.copy(alpha = 0.12f)
        isActive && engineState == EngineState.ERROR -> StateError.copy(alpha = 0.12f)
        isActive -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RadioButton(
                selected = isActive,
                onClick = onSelect
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Text(
                    "${entry.sizeDisplay} • ${entry.path.substringAfterLast('/')}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                if (isActive) {
                    Text(
                        when (engineState) {
                            EngineState.READY -> "Aktif — READY"
                            EngineState.LOADING -> "Aktif — memuat..."
                            EngineState.ERROR -> "Aktif — gagal dimuat"
                            EngineState.UNLOADED -> "Aktif — belum dimuat"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when (engineState) {
                            EngineState.READY -> StateCompleted
                            EngineState.LOADING -> StatePlanning
                            EngineState.ERROR -> StateError
                            EngineState.UNLOADED -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Hapus",
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun ImportProgressCard(percent: Int, copiedMb: Long, totalMb: Long) {
    Card(
        colors = CardDefaults.cardColors(containerColor = StatePlanning.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Menyalin file model...",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = StatePlanning
            )
            LinearProgressIndicator(
                progress = { percent / 100f },
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "$copiedMb / $totalMb MB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "$percent%",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = StatePlanning
                )
            }
            Text(
                "Jangan tutup app — setelah selesai engine akan auto-reload.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ImportErrorCard(message: String, onRetry: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = StateError.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Error, contentDescription = null, tint = StateError)
                Text(
                    "Gagal menyalin file",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = StateError
                )
            }
            Text(message, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                Text("Coba Lagi")
            }
        }
    }
}
