package com.chibiclaw.safety

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SensitiveDetector @Inject constructor() {

    private val sensitiveKeywords = listOf(
        "password", "kata sandi", "pin", "cvv", "nomor kartu",
        "credit card", "kartu kredit", "rekening", "transfer",
        "otp", "kode verifikasi", "secret", "token"
    )

    fun hasSensitiveContent(text: String): Boolean {
        val lower = text.lowercase()
        return sensitiveKeywords.any { lower.contains(it) }
    }
}
