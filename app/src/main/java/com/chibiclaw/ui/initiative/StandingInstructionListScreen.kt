package com.chibiclaw.ui.initiative

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.chibiclaw.data.database.StandingInstructionEntity
import com.chibiclaw.data.repository.StandingInstructionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * StandingInstructionListScreen — list semua standing instruction, toggle
 * enable/disable, navigasi ke editor, delete.
 */
@Composable
fun StandingInstructionListScreen(
    repository: StandingInstructionRepository,
    onCreateNew: () -> Unit,
    onEdit: (id: String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf(emptyList<StandingInstructionEntity>()) }

    suspend fun refresh() {
        items = repository.observeAll().first()
    }

    LaunchedEffect(Unit) { refresh() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Standing Instructions",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Button(
                onClick = onCreateNew,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text("+ Baru")
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "Direktif proaktif Fuu — trigger berbasis waktu, event, predicate, atau composite.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))

        if (items.isEmpty()) {
            Text(
                text = "Belum ada instruction. Tap '+ Baru' untuk buat pertama.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items, key = { it.id }) { entity ->
                    InstructionRow(
                        entity = entity,
                        onToggle = { enabled ->
                            scope.launch {
                                repository.setEnabled(entity.id, enabled)
                                refresh()
                            }
                        },
                        onEdit = { onEdit(entity.id) },
                        onDelete = {
                            scope.launch {
                                repository.delete(entity)
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
private fun InstructionRow(
    entity: StandingInstructionEntity,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entity.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (entity.description.isNotBlank()) {
                        Text(
                            text = entity.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Switch(checked = entity.enabled, onCheckedChange = onToggle)
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = "Channel: ${entity.channel} · Cooldown: ${entity.cooldownMs / 1000}s · " +
                    "Fired: ${entity.totalFires}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onEdit, modifier = Modifier.height(36.dp)) { Text("Edit") }
                OutlinedButton(
                    onClick = onDelete,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp),
                ) { Text("Hapus") }
            }
        }
    }
}
