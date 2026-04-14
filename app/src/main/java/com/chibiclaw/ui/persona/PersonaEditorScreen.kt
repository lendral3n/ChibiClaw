package com.chibiclaw.ui.persona

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chibiclaw.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaEditorScreen(
    viewModel: PersonaEditorViewModel = hiltViewModel()
) {
    val systemPrompt by viewModel.systemPrompt.collectAsState()
    val language by viewModel.language.collectAsState()
    val tone by viewModel.tone.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Persona Fuu") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("System Prompt", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = systemPrompt,
                onValueChange = viewModel::setSystemPrompt,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                label = { Text("Fuu's personality & instructions") }
            )

            HorizontalDivider()

            Text("Bahasa Respons", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Indonesia", "English", "Mixed").forEach { lang ->
                    FilterChip(
                        selected = language == lang,
                        onClick = { viewModel.setLanguage(lang) },
                        label = { Text(lang) }
                    )
                }
            }

            Text("Gaya Bicara", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Formal", "Casual", "Kawaii").forEach { t ->
                    FilterChip(
                        selected = tone == t,
                        onClick = { viewModel.setTone(t) },
                        label = { Text(t) }
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = viewModel::resetToDefault,
                    modifier = Modifier.weight(1f)
                ) { Text("Reset Default") }
                Button(
                    onClick = viewModel::savePersona,
                    modifier = Modifier.weight(1f)
                ) { Text("Simpan") }
            }
        }
    }
}
