package com.chibiclaw.ui.debug

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.chibiclaw.data.database.AgentStepEntity
import com.chibiclaw.data.database.TaskEntity
import com.chibiclaw.data.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * TaskDetailScreen — agent step trace, untuk debug Phase 1.
 */
@Composable
fun TaskDetailScreen(
    taskId: String,
    viewModel: TaskDetailViewModel = hiltViewModel(),
) {
    val task by viewModel.observeTask(taskId).collectAsState(initial = null)
    val steps by viewModel.observeSteps(taskId).collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        if (task == null) {
            Text("Task not found", style = MaterialTheme.typography.bodyMedium)
            return
        }

        Text(
            text = "Task Detail",
            style = MaterialTheme.typography.headlineMedium,
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("ID: ${task!!.id.take(8)}", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                Text("Channel: ${task!!.channel.name} · Status: ${task!!.status.name}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Goal: ${task!!.goal}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (task!!.resultSummary != null) {
                    Text("Result: ${task!!.resultSummary}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }
                if (task!!.errorMessage != null) {
                    Text("Error: ${task!!.errorMessage}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        Text(
            text = "Agent Steps (${steps.size})",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
        )

        LazyColumn(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            items(steps, key = { it.id }) { step ->
                StepCard(step)
            }
        }
    }
}

@Composable
private fun StepCard(step: AgentStepEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row {
                Text(
                    text = "#${step.stepIndex}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = " · ${step.nextIntent.name} · ${step.adapterUsed} · ${step.latencyMs ?: 0}ms",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = step.thought,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (step.toolCallJson != null) {
                Text(
                    text = "→ ${step.toolCallJson}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (step.toolResultJson != null) {
                Text(
                    text = "← ${step.toolResultJson}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@HiltViewModel
class TaskDetailViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
) : ViewModel() {

    fun observeTask(taskId: String): Flow<TaskEntity?> = taskRepository.observe(taskId)

    fun observeSteps(taskId: String): Flow<List<AgentStepEntity>> = taskRepository.observeSteps(taskId)
}
