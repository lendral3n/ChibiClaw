package com.chibiclaw.ui.setup

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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chibiclaw.ai.llm.webview.CloudLoginScripts
import com.chibiclaw.ai.llm.webview.CloudLoginWebView
import com.chibiclaw.ai.llm.webview.SessionExtractor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun ClaudeWebSetupScreen(
    sessionExtractor: SessionExtractor,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
) {
    var loginActive by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val json = remember { Json { ignoreUnknownKeys = true } }

    if (loginActive) {
        CloudLoginWebView(
            loginUrl = "https://claude.ai/login",
            successUrlPrefix = "https://claude.ai/chat",
            extractScript = CloudLoginScripts.CLAUDE_EXTRACT,
            onExtracted = { payload ->
                runCatching {
                    val obj = json.parseToJsonElement(payload).jsonObject
                    val ok = sessionExtractor.extractAndPersistClaude(
                        orgId = obj.stringOrEmpty("orgId"),
                        userId = obj.stringOrEmpty("userId"),
                        activeConvId = obj["activeConvId"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
                        clientSha = obj.stringOrEmpty("clientSha"),
                        clientVersion = obj.stringOrEmpty("clientVersion"),
                        deviceId = obj.stringOrEmpty("deviceId"),
                        userAgent = obj.stringOrEmpty("userAgent"),
                    )
                    status = if (ok) "✅ Claude.ai session tersimpan" else "⚠️ Extract incomplete — coba ulang"
                }.onFailure {
                    status = "⚠️ Parse payload gagal: ${it.message?.take(60)}"
                }
                loginActive = false
            },
            onCancel = { loginActive = false },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Claude.ai (Web Session)",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Login claude.ai via WebView. Session cookie + clientSha akan " +
                "diekstrak dan disimpan terenkripsi. Berlaku ±14 hari.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "Risiko: Anthropic bisa rotate signature kapan saja → kamu " +
                "perlu re-login. Rate-limit 1 call/30s di-enforce.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        status?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.labelMedium)
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { loginActive = true },
            contentPadding = PaddingValues(vertical = 14.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text("Login Claude.ai via WebView")
        }

        Button(
            onClick = onContinue,
            contentPadding = PaddingValues(vertical = 14.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text("Lanjut")
        }

        OutlinedButton(
            onClick = onSkip,
            contentPadding = PaddingValues(vertical = 14.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text("Skip")
        }
    }
}

@Composable
fun GPTWebSetupScreen(
    sessionExtractor: SessionExtractor,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
) {
    var loginActive by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val json = remember { Json { ignoreUnknownKeys = true } }

    if (loginActive) {
        CloudLoginWebView(
            loginUrl = "https://chatgpt.com/auth/login",
            successUrlPrefix = "https://chatgpt.com/",
            extractScript = CloudLoginScripts.GPT_EXTRACT,
            onExtracted = { payload ->
                runCatching {
                    val obj = json.parseToJsonElement(payload).jsonObject
                    val ok = sessionExtractor.extractAndPersistGPT(
                        userId = obj.stringOrEmpty("userId"),
                        accessToken = obj.stringOrEmpty("accessToken"),
                        conversationId = obj["conversationId"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
                        userAgent = obj.stringOrEmpty("userAgent"),
                    )
                    status = if (ok) "✅ ChatGPT session tersimpan" else "⚠️ Extract incomplete — coba ulang"
                }.onFailure {
                    status = "⚠️ Parse payload gagal: ${it.message?.take(60)}"
                }
                loginActive = false
            },
            onCancel = { loginActive = false },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "ChatGPT (Web Session)",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Login chatgpt.com via WebView. Session token + cookies akan " +
                "diekstrak dan disimpan terenkripsi.",
            style = MaterialTheme.typography.bodyMedium,
        )

        status?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.labelMedium)
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { loginActive = true },
            contentPadding = PaddingValues(vertical = 14.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text("Login ChatGPT via WebView")
        }

        Button(
            onClick = onContinue,
            contentPadding = PaddingValues(vertical = 14.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text("Lanjut")
        }

        OutlinedButton(
            onClick = onSkip,
            contentPadding = PaddingValues(vertical = 14.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text("Skip")
        }
    }
}

private fun JsonObject.stringOrEmpty(key: String): String =
    this[key]?.jsonPrimitive?.content.orEmpty()
