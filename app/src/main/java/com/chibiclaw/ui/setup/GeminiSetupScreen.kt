package com.chibiclaw.ui.setup

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.chibiclaw.ai.llm.adapters.GeminiFreeAdapter
import com.chibiclaw.data.prefs.SecurePreferences

/**
 * Phase 4 setup: paste Gemini free API key. Optional — Lendra bisa skip kalau
 * mau Gemma-only atau langsung pakai Claude/GPT web.
 *
 * Key disimpan di SecurePreferences (EncryptedSharedPreferences).
 */
@Composable
fun GeminiSetupScreen(
    securePreferences: SecurePreferences,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
) {
    val context = LocalContext.current
    var apiKey by remember {
        mutableStateOf(securePreferences.getString(GeminiFreeAdapter.KEY_API) ?: "")
    }
    var saved by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Gemini API (Gratis)",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Gemini 2.5 Flash gratis 1500 request/hari dari Google AI Studio. " +
                "Fuu pakai kalau Gemma lokal kewalahan (reasoning panjang, multi-step).",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "Privasi: data task akan transit ke Google. Audit log lokal " +
                "mencatat setiap escalate.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            },
            contentPadding = PaddingValues(vertical = 14.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text("Buka AI Studio → buat API key")
        }

        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it.trim(); saved = false },
            label = { Text("Paste API key di sini") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = {
                securePreferences.putString(GeminiFreeAdapter.KEY_API, apiKey.ifBlank { null })
                saved = apiKey.isNotBlank()
            },
            enabled = apiKey.isNotBlank(),
            contentPadding = PaddingValues(vertical = 14.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text(if (saved) "✅ Tersimpan, lanjut" else "Simpan API key")
        }

        if (saved) {
            Button(
                onClick = onContinue,
                contentPadding = PaddingValues(vertical = 14.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text("Lanjut")
            }
        }

        OutlinedButton(
            onClick = onSkip,
            contentPadding = PaddingValues(vertical = 14.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text("Skip — pakai Gemma lokal saja")
        }
    }
}
