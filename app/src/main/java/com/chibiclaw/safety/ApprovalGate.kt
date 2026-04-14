package com.chibiclaw.safety

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApprovalGate @Inject constructor(
    private val severityClassifier: SeverityClassifier,
    private val sensitiveDetector: SensitiveDetector,
    private val whitelistManager: WhitelistManager
) {
    suspend fun check(command: String): ApprovalResult {
        // 1. Classify severity of the command
        var severity = severityClassifier.classify(command)

        // 2. Escalate if sensitive content detected
        if (sensitiveDetector.hasSensitiveContent(command)) {
            severity = Severity.HIGH
            Log.w(TAG, "Sensitive content detected in command — escalating to HIGH")
        }

        // 3. Map severity to policy
        val policy = when (severity) {
            Severity.BLOCKED -> ApprovalPolicy.DENY
            Severity.HIGH -> ApprovalPolicy.ASK
            Severity.MEDIUM -> ApprovalPolicy.AUTO
            Severity.LOW -> ApprovalPolicy.AUTO
        }

        Log.d(TAG, "Approval: severity=$severity policy=$policy for: $command")
        return ApprovalResult(policy = policy, severity = severity)
    }

    companion object {
        private const val TAG = "ApprovalGate"
    }
}
