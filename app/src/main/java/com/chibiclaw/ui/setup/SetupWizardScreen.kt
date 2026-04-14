package com.chibiclaw.ui.setup

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chibiclaw.ai.EngineState
import com.chibiclaw.ai.ModelEntry
import com.chibiclaw.ui.theme.*
import com.chibiclaw.util.AccessibilityGuide
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import java.io.File

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AccessibilityGuideEntryPoint {
    fun accessibilityGuide(): AccessibilityGuide
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupWizardScreen(
    onSetupComplete: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val engineState by viewModel.engineState.collectAsState()
    val importState by viewModel.importState.collectAsState()
    val libraryModels by viewModel.libraryModels.collectAsState()
    val activeModelId by viewModel.activeModelId.collectAsState()
    var step by remember { mutableIntStateOf(0) }

    // SAF file picker — user selects .litertlm, we copy to app-private dir.
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importFromUri(uri)
        }
    }

    // Resolve AccessibilityGuide out of Hilt (Composables can't @Inject directly).
    val accessibilityGuide = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AccessibilityGuideEntryPoint::class.java
        ).accessibilityGuide()
    }

    // Poll accessibility state every 1s while the user is on the accessibility
    // step, so when they come back from Settings the UI auto-updates to
    // "Aktif" without needing to navigate away.
    var accessibilityEnabled by remember { mutableStateOf(false) }
    LaunchedEffect(step) {
        if (step == 1) {
            while (true) {
                accessibilityEnabled = accessibilityGuide.isServiceEnabled()
                delay(1000)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Setup ChibiClaw") },
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
        ) {
            // Step indicator dots
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(5) { index ->
                    Box(
                        modifier = Modifier
                            .size(if (index == step) 10.dp else 7.dp)
                            .clip(CircleShape)
                            .background(
                                if (index <= step) Purple40
                                else MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                    if (index < 4) Spacer(modifier = Modifier.width(8.dp))
                }
            }

            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                    } else {
                        slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
                    }
                },
                label = "setup_step",
                modifier = Modifier.weight(1f)
            ) { currentStep ->
                when (currentStep) {
                    0 -> StepWelcome()
                    1 -> StepAccessibility(
                        isEnabled = accessibilityEnabled,
                        guide = accessibilityGuide
                    )
                    2 -> StepNotification(
                        onOpenSettings = {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }
                    )
                    3 -> StepModel(
                        models = libraryModels,
                        activeModelId = activeModelId,
                        engineState = engineState,
                        importState = importState,
                        onAddModel = {
                            viewModel.resetImportState()
                            // Accept any file type — many file managers don't advertise
                            // application/octet-stream for .litertlm extension.
                            filePickerLauncher.launch(arrayOf("*/*"))
                        },
                        onSelectModel = { id -> viewModel.loadModelFromLibrary(id) },
                        onRemoveModel = { id -> viewModel.removeModel(id) }
                    )
                    4 -> StepDone()
                }
            }

            // Navigation buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (step > 0) {
                    OutlinedButton(
                        onClick = { step-- },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Kembali")
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                val canContinue = when (step) {
                    3 -> engineState == EngineState.READY
                    else -> true
                }

                Button(
                    onClick = {
                        if (step < 4) {
                            step++
                        } else {
                            viewModel.markSetupComplete()
                            onSetupComplete()
                        }
                    },
                    enabled = canContinue,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (step == 4) "Mulai" else "Lanjut")
                }
            }
        }
    }
}

@Composable
private fun StepWelcome() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Fuu mascot placeholder
        Surface(
            modifier = Modifier.size(120.dp),
            shape = CircleShape,
            color = Purple40.copy(alpha = 0.15f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    "\uD83D\uDC31",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = androidx.compose.ui.unit.TextUnit(
                            56f, androidx.compose.ui.unit.TextUnitType.Sp
                        )
                    )
                )
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            "Halo! Aku Fuu",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Asisten AI lokal yang berjalan di devicemu.\nTidak ada cloud, tidak ada biaya.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StepAccessibility(
    isEnabled: Boolean,
    guide: AccessibilityGuide
) {
    val context = LocalContext.current
    // Which sub-step of the guided flow the user is on. Starts at 0 (intro).
    var guidedStep by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Aksesibilitas",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        // Live status card (top)
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isEnabled)
                    StateCompleted.copy(alpha = 0.15f)
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
                    if (isEnabled) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (isEnabled) StateCompleted
                    else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(28.dp)
                )
                Column {
                    Text(
                        if (isEnabled) "Accessibility Aktif" else "Accessibility Belum Aktif",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isEnabled) StateCompleted
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        if (isEnabled)
                            "Fuu bisa otomasi UI di app lain."
                        else "Opsional — hanya untuk UI automation multi-step.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (isEnabled) {
            // Green banner with what's now unlocked
            Text(
                "✓ Sekarang kamu bisa kasih perintah multi-step seperti " +
                    "\"buka TikTok cari eskrim\" — Fuu akan otomatis navigasi UI-nya.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        // Optional banner — let user skip without guilt
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Purple40.copy(alpha = 0.10f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "Boleh dilewati kalau kamu cuma butuh task sederhana:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                listOf(
                    "Senter, volume, kecerahan, alarm",
                    "Telepon, SMS, WhatsApp, buka app",
                    "Kontak, kalender, Wi-Fi/Bluetooth settings"
                ).forEach {
                    Text(
                        "• $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "Tekan Lanjut untuk skip. Bisa diaktifkan kapan saja dari Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Guided setup card — the important flow
        RestrictedSettingsGuideCard(
            guidedStep = guidedStep,
            onStepChange = { guidedStep = it },
            onOpenAccessibilitySettings = { guide.openAccessibilitySettings() },
            onOpenAppInfo = { guide.openAppInfoPage() },
            onCopyAdb = { text ->
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE)
                    as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("adb", text))
                Toast.makeText(
                    context,
                    "Command disalin ke clipboard",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }
}

/**
 * Guided card that walks the user through Android 13+ "Restricted Settings"
 * flow, which is what actually blocks ChibiClaw from appearing on HyperOS 3.0.
 *
 * The flow is specifically ordered:
 *  1. Trigger the Restricted Setting warning by trying to enable the service
 *     in Settings → Accessibility (this is what exposes the hidden ⋮ menu).
 *  2. Open App Info and tap ⋮ → "Allow restricted settings".
 *  3. Go back to Accessibility and flip the toggle.
 *
 * We also surface an ADB one-liner as an escape hatch for power users.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RestrictedSettingsGuideCard(
    guidedStep: Int,
    onStepChange: (Int) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onCopyAdb: (String) -> Unit
) {
    val adbCommand = "adb shell settings put secure enabled_accessibility_services " +
        "com.chibiclaw/com.chibiclaw.service.ChibiAccessibility"

    Card(
        colors = CardDefaults.cardColors(
            containerColor = StatePlanning.copy(alpha = 0.10f)
        ),
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
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = StatePlanning,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    "Aktifkan Accessibility — 3 langkah",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = StatePlanning
                )
            }
            Text(
                "Android 13+ memblokir sideloaded app dari menu Accessibility " +
                    "demi keamanan. Kamu perlu \"unlock\" ChibiClaw dulu lewat " +
                    "App Info. Tenang, cuma sekali seumur hidup app ini.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Step 1 — Trigger the restricted-setting warning
            GuidedStepRow(
                number = 1,
                title = "Buka Settings → Accessibility → ChibiClaw",
                description = "Coba toggle ON. Kamu akan lihat dialog \"Restricted setting — " +
                    "For your security, this setting is currently unavailable.\" Ini NORMAL " +
                    "dan justru dibutuhkan — dialog ini yang akan memunculkan menu ⋮ " +
                    "tersembunyi di langkah 2.",
                active = guidedStep == 0,
                done = guidedStep > 0,
                actionLabel = "Buka Accessibility Settings",
                onAction = {
                    onOpenAccessibilitySettings()
                    onStepChange(1)
                }
            )

            // Step 2 — Allow restricted settings from App Info
            GuidedStepRow(
                number = 2,
                title = "App Info → ⋮ (pojok kanan atas) → \"Allow restricted settings\"",
                description = "Setelah langkah 1, kembali ke sini dan tekan tombol " +
                    "di bawah. Di halaman App Info, tap menu tiga titik (⋮) di pojok " +
                    "kanan atas — sekarang menu \"Allow restricted settings\" muncul. " +
                    "Ketuk menu itu, lalu konfirmasi.",
                active = guidedStep == 1,
                done = guidedStep > 1,
                actionLabel = "Buka App Info ChibiClaw",
                onAction = {
                    onOpenAppInfo()
                    onStepChange(2)
                }
            )

            // Step 3 — Actually enable the service
            GuidedStepRow(
                number = 3,
                title = "Kembali ke Accessibility → ChibiClaw → toggle ON",
                description = "Sekarang toggle tidak abu-abu lagi. Geser ON → " +
                    "Izinkan. Halaman ini akan otomatis ganti ke ✓ Aktif dalam 1-2 detik.",
                active = guidedStep == 2,
                done = false,
                actionLabel = "Buka Accessibility Settings",
                onAction = {
                    onOpenAccessibilitySettings()
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Escape hatch — ADB one-liner for power users
            Text(
                "Atau cara cepat (butuh PC + USB debugging aktif):",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onCopyAdb(adbCommand) },
                        onLongClick = { onCopyAdb(adbCommand) }
                    )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        adbCommand,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = StatePlanning,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                "Tap perintah untuk menyalin, jalankan di terminal saat device terhubung.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GuidedStepRow(
    number: Int,
    title: String,
    description: String,
    active: Boolean,
    done: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Circular step indicator
            Surface(
                modifier = Modifier.size(28.dp),
                shape = CircleShape,
                color = when {
                    done -> StateCompleted
                    active -> StatePlanning
                    else -> MaterialTheme.colorScheme.outlineVariant
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (done) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Text(
                            "$number",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (active) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        OutlinedButton(
            onClick = onAction,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 38.dp)
        ) {
            Text(actionLabel)
        }
    }
}

@Composable
private fun StepNotification(onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Akses Notifikasi",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Opsional — memungkinkan Fuu merespons notifikasi dari WhatsApp, Telegram, dll.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Fitur yang diaktifkan:", style = MaterialTheme.typography.labelMedium)
                listOf("Auto-reply WhatsApp", "Forward notifikasi Gmail", "Trigger dari Telegram").forEach {
                    Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        OutlinedButton(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Buka Pengaturan Notifikasi")
        }
        Text(
            "Bisa dilewati — bisa diaktifkan nanti di Settings",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StepModel(
    models: List<ModelEntry>,
    activeModelId: String?,
    engineState: EngineState,
    importState: ImportState,
    onAddModel: () -> Unit,
    onSelectModel: (String) -> Unit,
    onRemoveModel: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Library Model",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Tambahkan satu atau lebih file .litertlm ke library. Kamu bisa " +
                "switch antar model kapan saja — tapi hanya satu yang aktif " +
                "pada satu waktu, mirip pilihan model di ChatGPT/Claude.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        when (val s = importState) {
            is ImportState.InProgress -> ImportProgressCard(s.percent, s.copiedMb, s.totalMb)
            is ImportState.Failed -> ImportErrorCard(s.message, onRetry = onAddModel)
            is ImportState.Done, ImportState.Idle -> {}
        }

        // Always-visible "+ Tambah Model" button so the user can add more
        // even after the first one is uploaded.
        OutlinedButton(
            onClick = onAddModel,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = importState !is ImportState.InProgress
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (models.isEmpty()) "Tambah Model Pertama" else "Tambah Model Lain",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }

        if (models.isEmpty() && importState !is ImportState.InProgress) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Cara kerja library",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "• Upload file .litertlm (±4GB) dari Download atau SAF",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "• File disalin ke folder private app, aslinya boleh dihapus",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "• Upload model lain kapan pun (E4B, E2B, Gemma 3, …)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "• Pilih satu dengan radio button → otomatis jadi aktif",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (models.isNotEmpty()) {
            Text(
                "Pilih model aktif:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                items(models, key = { it.id }) { entry ->
                    ModelLibraryRow(
                        entry = entry,
                        isActive = entry.id == activeModelId,
                        engineState = if (entry.id == activeModelId) engineState else EngineState.UNLOADED,
                        onSelect = { onSelectModel(entry.id) },
                        onRemove = { onRemoveModel(entry.id) }
                    )
                }
            }

            // Engine status footer for the currently-active entry.
            if (activeModelId != null) {
                when (engineState) {
                    EngineState.LOADING -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            "Memuat model aktif ke engine...",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatePlanning
                        )
                    }
                    EngineState.READY -> Text(
                        "✓ Model aktif siap — tekan Lanjut",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = StateCompleted
                    )
                    EngineState.ERROR -> Text(
                        "Gagal memuat model aktif. Coba pilih model lain atau cek Dev Console.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StateError
                    )
                    EngineState.UNLOADED -> Text(
                        "Tekan radio button pada model untuk memuatnya.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelLibraryRow(
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
        isActive -> Purple40.copy(alpha = 0.10f)
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
                            EngineState.READY -> "Aktif — engine READY"
                            EngineState.LOADING -> "Aktif — sedang dimuat..."
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
                "Jangan tutup app — proses ini sekali saja.",
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
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                Text("Coba Lagi")
            }
        }
    }
}

@Composable
private fun ImportSuccessCard(fileName: String, size: String, onReplace: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = StateCompleted.copy(alpha = 0.12f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = StateCompleted,
                    modifier = Modifier.size(28.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "File Tersimpan",
                        style = MaterialTheme.typography.labelSmall,
                        color = StateCompleted
                    )
                    Text(
                        fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                    Text(
                        "Ukuran: $size",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            TextButton(onClick = onReplace) {
                Text("Ganti File")
            }
        }
    }
}

@Composable
private fun StepDone() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = StateCompleted,
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Siap!",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Fuu sudah siap membantumu.\nKetuk Mulai untuk membuka dashboard.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
