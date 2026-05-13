package com.chibiclaw.ui.setup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.chibiclaw.ai.llm.webview.SessionExtractor
import com.chibiclaw.data.prefs.SecurePreferences
import com.chibiclaw.permissions.ShizukuManager

/**
 * Setup wizard navigator.
 *
 * Step:
 * 1. PRIVACY_NOTICE
 * 2. CONSENT_OVERLAY
 * 3. VENDOR_WIZARD
 * 4. ACCESSIBILITY_SETUP (Phase 3)
 * 5. SHIZUKU_SETUP (Phase 3)
 * 6. GEMINI_SETUP (Phase 4)
 * 7. CLAUDE_WEB_SETUP (Phase 4)
 * 8. GPT_WEB_SETUP (Phase 4)
 * 9. DONE
 *
 * Phase 2+ microphone consent + ElevenLabs API key: TBD (defer Phase 9 polish).
 */
@Composable
fun SetupNavigator(
    shizukuManager: ShizukuManager,
    securePreferences: SecurePreferences,
    sessionExtractor: SessionExtractor,
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
            onContinue = { step = SetupStep.GEMINI_SETUP },
            onSkip = { step = SetupStep.GEMINI_SETUP },
        )

        SetupStep.GEMINI_SETUP -> GeminiSetupScreen(
            securePreferences = securePreferences,
            onContinue = { step = SetupStep.CLAUDE_WEB_SETUP },
            onSkip = { step = SetupStep.CLAUDE_WEB_SETUP },
        )

        SetupStep.CLAUDE_WEB_SETUP -> ClaudeWebSetupScreen(
            sessionExtractor = sessionExtractor,
            onContinue = { step = SetupStep.GPT_WEB_SETUP },
            onSkip = { step = SetupStep.GPT_WEB_SETUP },
        )

        SetupStep.GPT_WEB_SETUP -> GPTWebSetupScreen(
            sessionExtractor = sessionExtractor,
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
    GEMINI_SETUP,
    CLAUDE_WEB_SETUP,
    GPT_WEB_SETUP,
    DONE,
}
