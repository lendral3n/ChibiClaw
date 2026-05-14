package com.chibiclaw.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chibiclaw.agent.TaskManager
import com.chibiclaw.agent.scheduler.ResourceKind
import com.chibiclaw.agent.scheduler.ResourceScheduler
import com.chibiclaw.compliance.AuditLogger
import com.chibiclaw.data.database.AuditResultStatus
import kotlinx.coroutines.launch

/**
 * ErrorStatsScreen — minimal observability dashboard untuk dev iterasi:
 *  - Tool execution outcome count last 7 days (success/failed/timeout/denied)
 *  - Active slot snapshot dari TaskManager
 *  - ResourceScheduler state per kind (busy/free, count)
 *
 * Phase 8 cukup baca AuditLog via AuditLogger.statsLastWeek (placeholder kalau
 * method belum ada — Phase 9 polish bisa add aggregate method ke DAO).
 */
@Composable
fun ErrorStatsScreen(
    taskManager: TaskManager,
    resourceScheduler: ResourceScheduler,
    auditLogger: AuditLogger,
) {
    val scope = rememberCoroutineScope()
    var activeCount by remember { mutableStateOf(0) }
    var maxParallel by remember { mutableStateOf(0) }
    var resourceState by remember { mutableStateOf(emptyMap<ResourceKind, com.chibiclaw.agent.scheduler.ResourceState>()) }
    var outcomeCounts by remember { mutableStateOf<Map<AuditResultStatus, Int>>(emptyMap()) }

    suspend fun refresh() {
        activeCount = taskManager.activeCount()
        maxParallel = taskManager.maxParallel()
        resourceState = resourceScheduler.snapshot()
        outcomeCounts = auditLogger.toolOutcomeCountsLast7d()
    }

    LaunchedEffect(Unit) { refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Error Stats & Concurrency",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Slots", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("$activeCount / $maxParallel task running")
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Resources", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                resourceState.forEach { (kind, state) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(kind.name)
                        Text(state.usageText)
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Tool outcomes (last 7 days)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (outcomeCounts.isEmpty()) {
                    Text("No audit data yet", style = MaterialTheme.typography.bodySmall)
                } else {
                    outcomeCounts.forEach { (status, count) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(status.name)
                            Text(count.toString())
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = { scope.launch { refresh() } },
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Text("Refresh")
        }
    }
}
