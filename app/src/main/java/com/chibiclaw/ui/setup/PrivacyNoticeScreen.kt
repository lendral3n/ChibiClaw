package com.chibiclaw.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.chibiclaw.compliance.AuditLogger
import com.chibiclaw.data.database.AuditActionType
import com.chibiclaw.data.prefs.SecurePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@Composable
fun PrivacyNoticeScreen(
    onAgree: () -> Unit,
    onDisagree: () -> Unit,
    viewModel: PrivacyNoticeViewModel = hiltViewModel(),
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "ChibiClaw — Privacy Notice",
            style = MaterialTheme.typography.headlineLarge,
        )

        Text(
            text = "Aku Fuu, AI asisten di HP kamu. Sebelum kita mulai, ada yang harus kamu tahu.",
            style = MaterialTheme.typography.bodyLarge,
        )

        SectionHeader("Yang aku akses")
        Text(
            text = "• Mikrofon — buat dengar perintah suara kamu\n" +
                "• Layar (Accessibility + opsional vision) — buat lakukan task di app\n" +
                "• Notifikasi — buat reagir ke event (kalau pasang standing instruction)\n" +
                "• Lokasi (opsional) — buat trigger berdasarkan tempat\n" +
                "• Kontak (opsional) — buat \"kirim WA ke Budi\" jalan",
            style = MaterialTheme.typography.bodyMedium,
        )

        SectionHeader("Di mana data kamu disimpan")
        Text(
            text = "• Default: di HP kamu sendiri, encrypted (SQLCipher)\n" +
                "• Cloud (opt-in): saat kamu pakai Gemini/Claude/GPT, snippet task ringkasan transit ke server mereka. Kontrol penuh ada di kamu.",
            style = MaterialTheme.typography.bodyMedium,
        )

        SectionHeader("Yang TIDAK aku simpan")
        Text(
            text = "• Audio mentah (cuma transcribed text)\n" +
                "• Konten SMS (langsung kirim tanpa simpan)\n" +
                "• Screenshot mentah (cuma processed result)\n" +
                "• Password / OTP (di-redact dari log otomatis)",
            style = MaterialTheme.typography.bodyMedium,
        )

        SectionHeader("Kontrol kamu")
        Text(
            text = "• Toggle off cloud kapan saja — back to full local mode\n" +
                "• Export semua data ke JSON\n" +
                "• Erase semua data permanen\n" +
                "• Lihat audit log: siapa akses apa kapan",
            style = MaterialTheme.typography.bodyMedium,
        )

        SectionHeader("Risiko yang harus kamu sadari")
        Text(
            text = "• Cloud LLM (Gemini/Claude/GPT) ada Terms of Service mereka sendiri\n" +
                "• Reverse-engineered web session (Claude.ai / ChatGPT) BERPOTENSI melanggar ToS — akun bisa di-ban\n" +
                "• Voice clone Fuu di ElevenLabs subject to ElevenLabs ToS",
            style = MaterialTheme.typography.bodyMedium,
        )

        SectionHeader("Aku jamin (best effort)")
        Text(
            text = "• Encryption at rest (SQLCipher + EncryptedSharedPreferences)\n" +
                "• 90 hari rotating audit log\n" +
                "• Right to erase / export\n" +
                "• No telemetry phone-home",
            style = MaterialTheme.typography.bodyMedium,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = {
                    viewModel.logDisagree()
                    onDisagree()
                },
            ) {
                Text("Tidak setuju")
            }
            Button(
                modifier = Modifier.weight(1f),
                onClick = {
                    viewModel.logAgree()
                    onAgree()
                },
            ) {
                Text("Setuju, mulai")
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@HiltViewModel
class PrivacyNoticeViewModel @Inject constructor(
    private val securePreferences: SecurePreferences,
    private val auditLogger: AuditLogger,
) : ViewModel() {

    fun logAgree() {
        securePreferences.setPrivacyAccepted(true)
        auditLogger.log(
            actionType = AuditActionType.PRIVACY_ACCEPTED,
            dataSummary = "User accepted privacy notice",
        )
    }

    fun logDisagree() {
        auditLogger.log(
            actionType = AuditActionType.PRIVACY_ACCEPTED,
            dataSummary = "User declined privacy notice",
            resultStatus = com.chibiclaw.data.database.AuditResultStatus.USER_DENIED,
        )
    }
}
