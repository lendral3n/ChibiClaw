package com.chibiclaw.ui.setup

import android.app.Application
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chibiclaw.vision.projection.MediaProjectionPermissionActivity
import com.chibiclaw.vision.projection.ProjectionTokenStore
import kotlinx.coroutines.delay

/**
 * Phase 5 setup: arahkan user grant MediaProjection sekali. Token persistent
 * di SecurePreferences (parcel marshalled). Token bisa stale kalau OS
 * revoke — handled saat capture (return null → tool error → LLM retry).
 */
@Composable
fun VisionSetupScreen(
    tokenStore: ProjectionTokenStore,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(tokenStore.hasToken()) }

    LaunchedEffect(Unit) {
        while (true) {
            granted = tokenStore.hasToken()
            delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Vision (Screenshot fallback)",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Fuu butuh akses screen capture buat kasus accessibility gagal " +
                "(TikTok, IG, app non-standard). Screenshot diproses lokal — " +
                "tidak disimpan, tidak dikirim ke server kecuali Lendra " +
                "eksplisit escalate ke cloud.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "Risiko: screenshot bisa berisi konten sensitif (password, OTP). " +
                "Fuu akan refuse capture saat keyboard otomatis muncul (Phase 9).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))
        Text(
            text = "Status: " + if (granted) "✅ Token tersimpan" else "⛔ Belum grant",
            style = MaterialTheme.typography.labelMedium,
        )

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                val app = context.applicationContext as Application
                context.startActivity(MediaProjectionPermissionActivity.launchIntent(app))
            },
            contentPadding = PaddingValues(vertical = 14.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text(if (granted) "Re-grant MediaProjection" else "Grant MediaProjection")
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
            Text("Skip — pakai a11y only")
        }
    }
}
