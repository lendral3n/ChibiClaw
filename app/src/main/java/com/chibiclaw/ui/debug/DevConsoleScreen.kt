package com.chibiclaw.ui.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chibiclaw.ai.EngineState
import com.chibiclaw.debug.DevLog
import com.chibiclaw.debug.DevLogLevel
import com.chibiclaw.state.ChibiState
import com.chibiclaw.ui.theme.*
import kotlinx.coroutines.launch

private val TAG_COLORS = mapOf(
    "ORCHESTRATOR" to Color(0xFFCE93D8),
    "APPROVAL"     to Color(0xFFFFCC02),
    "ROUTING"      to Color(0xFF80DEEA),
    "ENGINE"       to Color(0xFF69F0AE),
    "INFERENCE"    to Color(0xFF82B1FF),
    "TOOL"         to Color(0xFFFFAB40),
    "MEMORY"       to Color(0xFF80CBC4),
    "SETUP"        to Color(0xFFEF9A9A),
    "STATE"        to Color(0xFFB39DDB),
)

private fun tagColor(tag: String): Color =
    TAG_COLORS[tag] ?: Color(0xFFB0B0C0)

private fun levelColor(level: DevLogLevel): Color = when (level) {
    DevLogLevel.DEBUG -> Color(0xFF757575)
    DevLogLevel.INFO  -> Color(0xFFE0E0E0)
    DevLogLevel.WARN  -> Color(0xFFFFD740)
    DevLogLevel.ERROR -> Color(0xFFFF5252)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevConsoleScreen(
    onBack: () -> Unit,
    viewModel: DevConsoleViewModel = hiltViewModel()
) {
    val logs by viewModel.logs.collectAsState()
    val engineState by viewModel.engineState.collectAsState()
    val agentState by viewModel.agentState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var selectedTag by remember { mutableStateOf("ALL") }
    var autoScroll by remember { mutableStateOf(true) }

    val allTags = remember(logs) {
        listOf("ALL") + logs.map { it.tag }.distinct().sorted()
    }
    val filteredLogs = remember(logs, selectedTag) {
        if (selectedTag == "ALL") logs else logs.filter { it.tag == selectedTag }
    }

    // Auto-scroll to bottom when new logs arrive
    LaunchedEffect(filteredLogs.size) {
        if (autoScroll && filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Dev Console", fontWeight = FontWeight.Bold)
                        Text(
                            "${filteredLogs.size} entries",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0D0D1A)
                ),
                actions = {
                    IconButton(onClick = { autoScroll = !autoScroll }) {
                        Icon(
                            Icons.Default.ArrowDownward,
                            contentDescription = "Auto-scroll",
                            tint = if (autoScroll) Purple40 else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        val clip = ClipData.newPlainText("DevLog", viewModel.exportLogs())
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(clip)
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy all")
                    }
                    IconButton(onClick = { viewModel.clearLogs() }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = StateError)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF080810))
                .padding(padding)
        ) {
            // Stats row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0D0D1A))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatChip(
                    label = "ENGINE",
                    value = engineState.name,
                    color = when (engineState) {
                        EngineState.READY    -> StateExecuting
                        EngineState.LOADING  -> StatePlanning
                        EngineState.ERROR    -> StateError
                        EngineState.UNLOADED -> StateIdle
                    }
                )
                StatChip(
                    label = "STATE",
                    value = agentState.name,
                    color = when (agentState) {
                        ChibiState.IDLE          -> StateIdle
                        ChibiState.PLANNING      -> StatePlanning
                        ChibiState.EXECUTING     -> StateExecuting
                        ChibiState.ERROR_RECOVERY -> StateError
                        ChibiState.WAITING_USER  -> StateWaiting
                        else                     -> StatePaused
                    }
                )
                StatChip(label = "LOGS", value = logs.size.toString(), color = Purple40)
            }

            // Tag filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                allTags.forEach { tag ->
                    FilterChip(
                        selected = selectedTag == tag,
                        onClick = { selectedTag = tag },
                        label = {
                            Text(
                                tag,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = tagColor(tag).copy(alpha = 0.25f),
                            selectedLabelColor = tagColor(tag)
                        )
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF1E1E30))

            // Log list
            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Belum ada log. Kirim perintah ke Fuu.",
                        color = Color(0xFF444460),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(filteredLogs, key = { it.id }) { log ->
                        LogRow(log)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogRow(log: DevLog) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF0D0D1A))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Timestamp
        Text(
            log.formattedTime(),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF444460),
            modifier = Modifier.width(80.dp)
        )
        // Tag badge
        Text(
            log.tag.take(12),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = tagColor(log.tag),
            modifier = Modifier.width(92.dp)
        )
        // Level indicator
        Text(
            log.level.name.take(1),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = levelColor(log.level),
            modifier = Modifier.width(10.dp)
        )
        // Message
        Text(
            log.message,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = levelColor(log.level),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatChip(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            color = color.copy(alpha = 0.7f),
            fontWeight = FontWeight.Bold
        )
        Text(
            value,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}
