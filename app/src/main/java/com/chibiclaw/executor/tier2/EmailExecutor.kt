package com.chibiclaw.executor.tier2

import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 4.2 — builds a MAILTO intent that opens the user's email app with
 * the recipient, subject and body pre-populated. We intentionally stop at
 * "opened" — actually tapping Send is the user's job so we never send
 * mail on their behalf without explicit confirmation.
 */
@Singleton
class EmailExecutor @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun compose(recipient: String, subject: String, body: String): String {
        if (recipient.isBlank()) return "email_error: empty_recipient"
        val uri = Uri.parse(
            "mailto:$recipient" +
                "?subject=" + Uri.encode(subject) +
                "&body=" + Uri.encode(body)
        )
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            context.startActivity(intent)
            "email_opened: $recipient"
        } catch (e: Exception) {
            "email_error: ${e.message}"
        }
    }
}
