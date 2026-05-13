package com.chibiclaw.agent.tools.impl

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.SmsManager
import com.chibiclaw.agent.tools.ErrorClass
import com.chibiclaw.agent.tools.Tool
import com.chibiclaw.agent.tools.ToolCall
import com.chibiclaw.agent.tools.ToolCapability
import com.chibiclaw.agent.tools.ToolCost
import com.chibiclaw.agent.tools.ToolResult
import com.chibiclaw.agent.tools.ToolSafety
import com.chibiclaw.agent.tools.ToolSeverity
import com.chibiclaw.agent.tools.ToolSpec
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import javax.inject.Inject

class MessagingTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : Tool {

    override val spec = ToolSpec(
        name = "messaging",
        description = """
            Kirim pesan via SMS / WhatsApp / Telegram.
            SMS: direct via SmsManager (pakai pulsa).
            WhatsApp: via Intent android.intent.action.SENDTO (whatsapp://send).
            Telegram: via Intent (tg://msg_url).
            HIGH severity — selalu konfirmasi user kecuali pre-authorized via
            standing instruction.
        """.trimIndent(),
        parameters = mapOf(
            "kind" to "enum: SMS | WHATSAPP | TELEGRAM",
            "recipient" to "string (phone number atau chat title)",
            "text" to "string (message body)",
        ),
        capability = ToolCapability(
            latencyMsRange = 300..2_000,
            worksOn = listOf("SMS provider", "WhatsApp/Telegram via Intent"),
            knownFail = listOf("recipient tidak ada di kontak (WA/Telegram)"),
            requiresPermission = listOf("SEND_SMS (untuk kind=SMS)"),
            cost = ToolCost.MEDIUM,
        ),
        safety = ToolSafety(
            severity = ToolSeverity.HIGH,
            reason = "Kirim pesan ke pihak ke-3 — pulsa SMS / komunikasi user affected",
            confirmationPromptTemplate = "Kirim %1\$s ke %2\$s: %3\$s ?",
            preAuthorizable = true,
        ),
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        val kindStr = call.args["kind"]?.jsonPrimitive?.content?.uppercase()
        val kind = runCatching { MessagingKind.valueOf(kindStr ?: "") }.getOrNull()
            ?: return ToolResult.Error(
                callId = call.callId,
                errorClass = ErrorClass.INVALID_ARGS,
                message = "kind must be SMS | WHATSAPP | TELEGRAM",
            )
        val recipient = call.args["recipient"]?.jsonPrimitive?.content
            ?: return ToolResult.Error(
                callId = call.callId,
                errorClass = ErrorClass.INVALID_ARGS,
                message = "recipient arg required",
            )
        val text = call.args["text"]?.jsonPrimitive?.content
            ?: return ToolResult.Error(
                callId = call.callId,
                errorClass = ErrorClass.INVALID_ARGS,
                message = "text arg required",
            )

        return when (kind) {
            MessagingKind.SMS -> sendSms(call.callId, recipient, text)
            MessagingKind.WHATSAPP -> sendIntent(
                call.callId,
                "whatsapp://send?phone=${Uri.encode(recipient)}&text=${Uri.encode(text)}",
                channel = "WHATSAPP",
            )
            MessagingKind.TELEGRAM -> sendIntent(
                call.callId,
                "tg://msg_url?url=${Uri.encode(recipient)}&text=${Uri.encode(text)}",
                channel = "TELEGRAM",
            )
        }
    }

    private fun sendSms(callId: String, recipient: String, text: String): ToolResult {
        return runCatching {
            val sms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            val parts = sms.divideMessage(text)
            sms.sendMultipartTextMessage(recipient, null, parts, null, null)
            Timber.i("SMS sent ${parts.size} part(s) ke <redacted>")
            ToolResult.Success(
                callId = callId,
                data = JsonObject(mapOf(
                    "channel" to JsonPrimitive("SMS"),
                    "parts" to JsonPrimitive(parts.size),
                )),
            )
        }.getOrElse { t ->
            ToolResult.Error(
                callId = callId,
                errorClass = ErrorClass.PERMISSION_DENIED,
                message = "SMS send failed: ${t.message}",
                recoveryHint = "Pastikan permission SEND_SMS granted",
            )
        }
    }

    private fun sendIntent(callId: String, uri: String, channel: String): ToolResult {
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolResult.Success(
                callId = callId,
                data = JsonObject(mapOf("channel" to JsonPrimitive(channel))),
            )
        }.getOrElse { t ->
            ToolResult.Error(
                callId = callId,
                errorClass = ErrorClass.NOT_AVAILABLE,
                message = "$channel app tidak terinstall atau intent gagal: ${t.message}",
            )
        }
    }
}

enum class MessagingKind { SMS, WHATSAPP, TELEGRAM }
