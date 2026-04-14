package com.chibiclaw.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHubScreen(
    onNavigateToAi: () -> Unit,
    onNavigateToSafety: () -> Unit,
    onNavigateToSkills: () -> Unit,
    onNavigateToPersona: () -> Unit,
    onNavigateToCron: () -> Unit,
    onNavigateToNotifications: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SettingsItem(
                icon = Icons.Default.Memory,
                title = "AI Engine",
                subtitle = "Konfigurasi model Gemma dan backend",
                onClick = onNavigateToAi
            )
            SettingsItem(
                icon = Icons.Default.Security,
                title = "Safety Settings",
                subtitle = "Whitelist, log eksekusi, kebijakan keamanan",
                onClick = onNavigateToSafety
            )
            SettingsItem(
                icon = Icons.Default.Star,
                title = "Skill Editor",
                subtitle = "Kelola dan uji skill Fuu",
                onClick = onNavigateToSkills
            )
            SettingsItem(
                icon = Icons.Default.Person,
                title = "Persona Fuu",
                subtitle = "Bahasa, gaya, dan kepribadian Fuu",
                onClick = onNavigateToPersona
            )
            SettingsItem(
                icon = Icons.Default.Schedule,
                title = "Scheduled Tasks",
                subtitle = "Cron — jadwalkan perintah berulang",
                onClick = onNavigateToCron
            )
            SettingsItem(
                icon = Icons.Default.Notifications,
                title = "Notification Triggers",
                subtitle = "Whitelist app + kata kunci auto-reply",
                onClick = onNavigateToNotifications
            )
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
