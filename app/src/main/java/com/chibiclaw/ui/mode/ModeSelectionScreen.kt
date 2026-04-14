package com.chibiclaw.ui.mode

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chibiclaw.ui.theme.*

@Composable
fun ModeSelectionScreen(
    onUserMode: () -> Unit,
    onDevMode: () -> Unit,
    viewModel: ModeSelectionViewModel = hiltViewModel()
) {
    var showPinDialog by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }
    var pinVisible by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF08080F), Color(0xFF0D0D1E), Color(0xFF08080F))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo / Avatar
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(Purple40.copy(alpha = 0.15f))
                    .border(1.dp, Purple40.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("🐱", fontSize = 44.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "ChibiClaw",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = OnBackgroundDark
            )
            Text(
                "Pilih mode untuk melanjutkan",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            // User Mode Card
            ModeCard(
                icon = { Icon(Icons.Default.Person, contentDescription = null, tint = Teal40, modifier = Modifier.size(32.dp)) },
                title = "Mode Pengguna",
                description = "Pengalaman normal sebagai pengguna Fuu.\nSemua fitur tersedia tanpa debug info.",
                accentColor = Teal40,
                onClick = onUserMode
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Dev Mode Card
            ModeCard(
                icon = { Icon(Icons.Default.BugReport, contentDescription = null, tint = Purple40, modifier = Modifier.size(32.dp)) },
                title = "Mode Developer",
                description = "Dev console, live logs, state machine\ndan full debug tools. Memerlukan PIN.",
                accentColor = Purple40,
                badge = "DEV",
                onClick = { showPinDialog = true }
            )
        }

        // Version tag
        Text(
            "v3.0.0-debug",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF333350)
        )
    }

    // PIN Dialog
    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = {
                showPinDialog = false
                pin = ""
                pinError = false
            },
            containerColor = Color(0xFF12121E),
            shape = RoundedCornerShape(20.dp),
            icon = {
                Icon(Icons.Default.Lock, contentDescription = null, tint = Purple40, modifier = Modifier.size(28.dp))
            },
            title = {
                Text("PIN Developer", fontWeight = FontWeight.Bold, color = OnBackgroundDark)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Masukkan PIN untuk membuka Dev Mode",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant
                    )
                    OutlinedTextField(
                        value = pin,
                        onValueChange = {
                            if (it.length <= 8) {
                                pin = it
                                pinError = false
                            }
                        },
                        label = { Text("PIN") },
                        isError = pinError,
                        supportingText = if (pinError) {
                            { Text("PIN salah", color = StateError) }
                        } else null,
                        visualTransformation = if (pinVisible)
                            VisualTransformation.None
                        else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        trailingIcon = {
                            IconButton(onClick = { pinVisible = !pinVisible }) {
                                Icon(
                                    if (pinVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = OnSurfaceVariant
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Purple40,
                            focusedLabelColor = Purple40
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (viewModel.verifyPin(pin)) {
                            showPinDialog = false
                            pin = ""
                            onDevMode()
                        } else {
                            pinError = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Purple40)
                ) {
                    Text("Masuk")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPinDialog = false
                    pin = ""
                    pinError = false
                }) {
                    Text("Batal", color = OnSurfaceVariant)
                }
            }
        )
    }
}

@Composable
private fun ModeCard(
    icon: @Composable () -> Unit,
    title: String,
    description: String,
    accentColor: Color,
    badge: String? = null,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF10101C)),
        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = OnBackgroundDark
                    )
                    if (badge != null) {
                        Text(
                            badge,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(accentColor.copy(alpha = 0.15f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
