package com.chibiclaw.executor.tier2

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.SmsManager
import com.chibiclaw.executor.MessagingAction
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 4.1 — unified messaging executor.
 *
 * Gemma calls `messaging(kind=..., recipient=..., body=...)`; this class
 * picks the right backend per kind:
 *
 *  - sms:      direct SmsManager.sendTextMessage (requires SEND_SMS grant,
 *              automatically chunked for >160 char bodies)
 *  - whatsapp: deep-link intent https://wa.me/<phone>?text=<body> (no send
 *              button press — user taps Send inside WA, matching WhatsApp's
 *              ToS which forbids automated sending)
 *  - telegram: tg://msg?text=… + open chat by @username
 *  - email:    delegates to [EmailExecutor] which builds the proper
 *              MAILTO intent with subject/body extras
 *
 * For kinds that rely on deep links we ONLY open the activity — we do
 * NOT click Send. The user still has to confirm in the destination app,
 * matching platform safety guarantees.
 */
@Singleton
class MessagingExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val emailExecutor: EmailExecutor
) {

    suspend fun send(action: MessagingAction): String {
        val recipient = action.recipient.trim()
        val body = action.body
        return when (action.kind.lowercase()) {
            "sms" -> sendSms(recipient, body)
            "whatsapp", "wa" -> openWhatsApp(recipient, body)
            "telegram", "tg" -> openTelegram(recipient, body)
            "email", "mail" -> emailExecutor.compose(recipient, action.subject, body)
            else -> "messaging_error: unknown kind=${action.kind}"
        }
    }

    private fun sendSms(recipient: String, body: String): String {
        if (recipient.isEmpty() || body.isEmpty()) return "sms_error: empty recipient or body"
        return try {
            val smsManager = SmsManager.getDefault()
            val parts = smsManager.divideMessage(body)
            smsManager.sendMultipartTextMessage(recipient, null, parts, null, null)
            "sms_sent: ${parts.size}_parts"
        } catch (e: SecurityException) {
            "sms_error: SEND_SMS permission not granted"
        } catch (e: Exception) {
            "sms_error: ${e.message}"
        }
    }

    private fun openWhatsApp(recipient: String, body: String): String {
        val phoneDigits = recipient.filter { it.isDigit() || it == '+' }.removePrefix("+")
        if (phoneDigits.isEmpty()) return "wa_error: invalid phone"
        val encoded = URLEncoder.encode(body, "UTF-8")
        val uri = Uri.parse("https://wa.me/$phoneDigits?text=$encoded")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.whatsapp")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            context.startActivity(intent)
            "wa_opened: $phoneDigits"
        } catch (e: Exception) {
            // fall back to the generic web link so the OS picks whatever
            // messaging app handles wa.me
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
                "wa_opened_fallback: $phoneDigits"
            } catch (e2: Exception) {
                "wa_error: ${e2.message}"
            }
        }
    }

    private fun openTelegram(recipient: String, body: String): String {
        val username = recipient.trimStart('@')
        val uri = if (username.isEmpty()) {
            Uri.parse("tg://msg?text=${URLEncoder.encode(body, "UTF-8")}")
        } else {
            Uri.parse("https://t.me/$username?text=${URLEncoder.encode(body, "UTF-8")}")
        }
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            context.startActivity(intent)
            "tg_opened: $username"
        } catch (e: Exception) {
            "tg_error: ${e.message}"
        }
    }
}
