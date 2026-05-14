package com.chibiclaw.ui.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chibiclaw.data.database.MemoryCategory
import com.chibiclaw.data.database.MemoryRecordEntity
import com.chibiclaw.data.repository.MemoryRepository
import com.chibiclaw.memory.MemoryStore
import com.chibiclaw.memory.categories.CategoryTemplates
import kotlinx.coroutines.launch

/**
 * MemoryInspectorScreen — tabs per kategori, browse + search + delete.
 *
 * Phase 7 minimum: edit-in-place via expanded card; advanced edit (multi-field
 * form per template) defer Phase 9.
 */
@Composable
fun MemoryInspectorScreen(
    memoryStore: MemoryStore,
    memoryRepository: MemoryRepository? = null,
) {
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    var records by remember { mutableStateOf(emptyList<MemoryRecordEntity>()) }
    var counts by remember { mutableStateOf(emptyMap<MemoryCategory, Int>()) }
    var query by remember { mutableStateOf("") }
    val categories = MemoryCategory.values()

    suspend fun refresh() {
        records = memoryStore.listByCategory(categories[selectedTab])
        if (memoryRepository != null) {
            counts = memoryRepository.countByCategory().associate { it.category to it.cnt }
        }
    }

    LaunchedEffect(selectedTab) { refresh() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Memory Inspector",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = CategoryTemplates.describe(categories[selectedTab]).summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (counts.isNotEmpty()) {
            Text(
                text = "Total per kategori: " +
                    categories.joinToString(" · ") { "${it.name.first()}=${counts[it] ?: 0}" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(8.dp))

        TabRow(selectedTabIndex = selectedTab) {
            categories.forEachIndexed { i, cat ->
                Tab(
                    selected = selectedTab == i,
                    onClick = { selectedTab = i },
                    text = { Text(cat.name) },
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search key / value (substring)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))

        val filtered = if (query.isBlank()) records else records.filter {
            it.key.contains(query, ignoreCase = true) ||
                it.valueJson.contains(query, ignoreCase = true)
        }

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(top = 16.dp)) {
                Text(
                    text = "Belum ada record di kategori ini.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(filtered, key = { it.id }) { record ->
                    MemoryRow(
                        record = record,
                        onDelete = {
                            scope.launch {
                                memoryStore.forget(record.id)
                                refresh()
                            }
                        },
                        onTogglePin = { pinned ->
                            scope.launch {
                                memoryStore.setPinned(record.id, pinned)
                                refresh()
                            }
                        },
                        onApprove = {
                            scope.launch {
                                memoryStore.approvePatternCandidate(record.id)
                                refresh()
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MemoryRow(
    record: MemoryRecordEntity,
    onDelete: () -> Unit,
    onTogglePin: (Boolean) -> Unit,
    onApprove: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val isCandidate = record.key.startsWith("auto:") && record.confidence < 0.85f
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = (if (record.pinned) "📌 " else "") + record.key,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "conf ${"%.2f".format(record.confidence)}",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Text(
                text = if (expanded) record.valueJson else record.valueJson.take(140),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "access ${record.accessCount} · last ${record.lastAccessedAt}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                if (isCandidate) {
                    TextButton(
                        onClick = onApprove,
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) { Text("✅ Approve") }
                }
                TextButton(
                    onClick = { onTogglePin(!record.pinned) },
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) { Text(if (record.pinned) "Unpin" else "📌 Pin") }
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) { Text(if (expanded) "Tutup" else "Detail") }
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
