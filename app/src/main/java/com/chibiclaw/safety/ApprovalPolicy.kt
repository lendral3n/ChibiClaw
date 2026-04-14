package com.chibiclaw.safety

enum class ApprovalPolicy { AUTO, ASK, DENY }

enum class Severity { LOW, MEDIUM, HIGH, BLOCKED }

data class ApprovalResult(
    val policy: ApprovalPolicy,
    val severity: Severity,
    val reason: String = ""
)
