package com.chibiclaw.ui.skills

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chibiclaw.ui.theme.*

/**
 * UI for browsing built-in skills and managing user-authored custom
 * skills. Implements the P3.6 plan entry: list with play/test button,
 * JSON editor dialog ("Add skill"), import-from-file (ACTION_GET_CONTENT),
 * and a delete button on every custom entry.
 *
 * Built-in and custom skills both show up in the same LazyColumn — the
 * only visual difference is a small `CUSTOM` badge and a trash icon on
 * the cards whose name lives in [SkillEditorViewModel.customNames].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillEditorScreen(
    viewModel: SkillEditorViewModel = hiltViewModel()
) {
    val skills by viewModel.skills.collectAsState()
    val customNames by viewModel.customNames.collectAsState()
    val testResult by viewModel.testResult.collectAsState()
    val editorResult by viewModel.editorResult.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }

    // ACTION_GET_CONTENT launcher — the user picks any "*/*" file and we
    // try to parse it as a skill JSON. Anything invalid surfaces via
    // editorResult so the user sees the parser error inline.
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) viewModel.importFromUri(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Skill Editor") },
                actions = {
                    IconButton(onClick = { importLauncher.launch("application/json") }) {
                        Icon(Icons.Default.Add, contentDescription = "Import file")
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New skill") },
                containerColor = MaterialTheme.colorScheme.primary
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                val customCount = customNames.size
                val builtInCount = skills.size - customCount
                Text(
                    "$builtInCount built-in · $customCount custom · ${skills.size} total",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            editorResult?.let { msg ->
                item {
                    ResultBanner(
                        text = msg,
                        tint = MaterialTheme.colorScheme.primary,
                        onDismiss = { viewModel.clearEditorResult() }
                    )
                }
            }

            testResult?.let { result ->
                item {
                    ResultBanner(
                        text = result,
                        tint = StatePlanning,
                        onDismiss = { viewModel.clearTestResult() }
                    )
                }
            }

            items(skills, key = { it.name }) { skill ->
                SkillCard(
                    skill = skill,
                    isCustom = skill.name in customNames,
                    onTest = { viewModel.testSkill(skill) },
                    onDelete = { viewModel.removeCustom(skill.name) }
                )
            }
        }
    }

    if (showAddDialog) {
        AddSkillDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { raw ->
                viewModel.addCustomSkillFromJson(raw)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun ResultBanner(
    text: String,
    tint: androidx.compose.ui.graphics.Color,
    onDismiss: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    }
}

@Composable
private fun SkillCard(
    skill: com.chibiclaw.skills.SkillDefinition,
    isCustom: Boolean,
    onTest: () -> Unit,
    onDelete: () -> Unit
) {
    val tierColor = when (skill.defaultTier) {
        1 -> StateCompleted
        2 -> StatePlanning
        3 -> StateWaiting
        else -> StateError
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(skill.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = tierColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            "T${skill.defaultTier}",
                            style = MaterialTheme.typography.labelSmall,
                            color = tierColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                    if (isCustom) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                "CUSTOM",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    skill.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Triggers: ${skill.triggers.take(3).joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onTest) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Test", tint = StateCompleted)
            }
            if (isCustom) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = StateError)
                }
            }
        }
    }
}

/**
 * Modal dialog that lets the user paste a full [SkillDefinition] as
 * JSON. We intentionally keep this as a raw text editor rather than a
 * structured form because:
 *   - Power users who care enough to author skills tend to prefer
 *     editing JSON directly.
 *   - A structured form would need to mirror every field of
 *     [SkillDefinition] and keep up with future additions.
 *   - A single [OutlinedTextField] plays nicely with soft-wrap and
 *     copy/paste across devices.
 */
@Composable
private fun AddSkillDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(TEMPLATE_SKILL_JSON) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New custom skill") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Paste a SkillDefinition as JSON. The 'name' field is the primary key — reusing a name overwrites the existing custom skill.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 240.dp),
                    textStyle = MaterialTheme.typography.bodySmall,
                    singleLine = false,
                    label = { Text("JSON") }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private val TEMPLATE_SKILL_JSON = """
{
  "name": "my_custom_skill",
  "description": "Describe what this skill does",
  "triggers": ["buka contoh", "open example"],
  "defaultTier": 1,
  "intentAction": "",
  "intentUriTemplate": "",
  "targetPackage": "",
  "examples": ["buka contoh"]
}
""".trimIndent()
