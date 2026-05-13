package com.chibiclaw.ui.setup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.chibiclaw.permissions.ShizukuManager

/**
 * Setup wizard navigator.
 *
 * Step:
 * 1. PRIVACY_NOTICE — display full privacy notice, user agree/disagree
 * 2. CONSENT_OVERLAY — request SYSTEM_ALERT_WINDOW
 * 3. VENDOR_WIZARD — auto-detect + per-OEM guidance
 * 4. ACCESSIBILITY_SETUP — arahkan ke Settings → Accessibility (Phase 3)
 * 5. SHIZUKU_SETUP — info Shizuku, optional skip (Phase 3)
 * 6. DONE — call onSetupComplete
 *
 * Phase 2+ akan tambah step microphone consent + ElevenLabs API key.
 * Phase 4+ akan tambah cloud login WebView (Gemini/Claude/GPT).
 */
@Composable
fun SetupNavigator(
    shizukuManager: ShizukuManager,
    onRequestOverlayPermission: () -> Unit,
    onSetupComplete: () -> Unit,
) {
    var step by rememberSaveable { mutableStateOf(SetupStep.PRIVACY_NOTICE) }

    when (step) {
        SetupStep.PRIVACY_NOTICE -> PrivacyNoticeScreen(
            onAgree = { step = SetupStep.CONSENT_OVERLAY },
            onDisagree = { /* exit app — handled by host activity */ },
        )

        SetupStep.CONSENT_OVERLAY -> ConsentOverlayScreen(
            onRequestPermission = onRequestOverlayPermission,
            onContinue = { step = SetupStep.VENDOR_WIZARD },
            onSkip = { step = SetupStep.VENDOR_WIZARD },
        )

        SetupStep.VENDOR_WIZARD -> VendorWizardScreen(
            onContinue = { step = SetupStep.ACCESSIBILITY_SETUP },
        )

        SetupStep.ACCESSIBILITY_SETUP -> AccessibilitySetupScreen(
            onContinue = { step = SetupStep.SHIZUKU_SETUP },
            onSkip = { step = SetupStep.SHIZUKU_SETUP },
        )

        SetupStep.SHIZUKU_SETUP -> ShizukuSetupScreen(
            shizukuManager = shizukuManager,
            onContinue = { step = SetupStep.DONE },
            onSkip = { step = SetupStep.DONE },
        )

        SetupStep.DONE -> {
            // Trigger callback once
            onSetupComplete()
        }
    }
}

enum class SetupStep {
    PRIVACY_NOTICE,
    CONSENT_OVERLAY,
    VENDOR_WIZARD,
    ACCESSIBILITY_SETUP,
    SHIZUKU_SETUP,
    DONE,
}
