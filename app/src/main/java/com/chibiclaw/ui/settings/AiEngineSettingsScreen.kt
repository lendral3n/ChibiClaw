package com.chibiclaw.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import com.chibiclaw.ai.llm.AdapterQuotaTracker
import com.chibiclaw.ai.llm.InferenceRouter
import com.chibiclaw.ai.llm.adapters.CloudSessionRotator
import com.chibiclaw.ai.llm.webview.SessionExtractor
import com.chibiclaw.data.database.ModelConfigEntity
import kotlinx.coroutines.launch

/**
 * AI Engine Settings — list semua adapter, status, quota usage, action button
 * (re-login, validate, clear session).
 *
 * Live-update: refresh saat masuk screen + button manual refresh.
 */
@Composable
fun AiEngineSettingsScreen(
    router: InferenceRouter,
    quotaTracker: AdapterQuotaTracker,
    rotator: CloudSessionRotator,
    sessionExtractor: SessionExtractor,
) {
    var configs by remember { mutableStateOf(emptyList<ModelConfigEntity>()) }
    var availability by remember { mutableStateOf(emptyMap<String, Boolean>()) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        configs = quotaTracker.listAll()
        availability = router.allAdapters().associate { it.id to it.isAvailable() }
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
            text = "AI Engine",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Adapter LLM yang aktif untuk Fuu. Cascade default: local Gemma → " +
                "Gemini free → Claude web → GPT web → Stub.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        router.allAdapters().forEach { adapter ->
            val cfg = configs.firstOrNull { it.adapterId == adapter.id }
            val available = availability[adapter.id] == true
            AdapterCard(
                title = adapter.capability.displayName,
                adapterId = adapter.id,
                available = available,
                cfg = cfg,
                isLocal = adapter.capability.isLocal,
                onValidate = {
                    scope.launch {
                        when (adapter.id) {
                            "claude_web" -> rotator.validateClaude()
                            "gpt_web" -> rotator.validateGPT()
                        }
                        refresh()
                    }
                },
                onClearSession = {
                    scope.launch {
                        when (adapter.id) {
                            "claude_web" -> sessionExtractor.clearClaudeSession()
                            "gpt_web" -> sessionExtractor.clearGPTSession()
                        }
                        refresh()
                    }
                },
            )
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { scope.launch { refresh() } },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            Text("Refresh status")
        }
    }
}

@Composable
private fun AdapterCard(
    title: String,
    adapterId: String,
    available: Boolean,
    cfg: ModelConfigEntity?,
    isLocal: Boolean,
    onValidate: () -> Unit,
    onClearSession: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = if (available) "✅ Available" else "⛔ Not configured",
                style = MaterialTheme.typography.labelMedium,
            )
            if (cfg != null) {
                if (cfg.dailyQuota > 0) {
                    Text(
                        text = "Quota: ${cfg.usedToday}/${cfg.dailyQuota} per hari",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Text(
                    text = "Total calls: ${cfg.totalCalls} · errors: ${cfg.totalErrors}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (cfg.exhaustedAt != null) {
                    Text(
                        text = "⚠️ Exhausted — tunggu reset besok",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (!isLocal && (adapterId == "claude_web" || adapterId == "gpt_web")) {
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onValidate, modifier = Modifier.height(36.dp)) {
                    Text("Validate session")
                }
                TextButton(onClick = onClearSession, modifier = Modifier.height(36.dp)) {
                    Text("Hapus session (logout)")
                }
            }
        }
    }
}
