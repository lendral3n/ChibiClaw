package com.chibiclaw.executor.tier2

import android.annotation.SuppressLint
import android.content.Context
import android.database.Cursor
import android.os.Build
import android.provider.CallLog
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 11 — Telephony executor: call log, answer/reject, USSD, SIM info.
 */
@Singleton
class TelephonyExecutor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager

    @SuppressLint("MissingPermission")
    fun perform(operation: String, value: String): String {
        val op = operation.trim().lowercase()
        return try {
            when (op) {
                "call_log", "calllog", "riwayat" -> getCallLog(value.toIntOrNull() ?: 10)
                "sim_info", "siminfo", "sim" -> getSimInfo()
                "answer", "angkat" -> answerCall()
                "reject", "tolak", "decline" -> rejectCall()
                "end", "hangup", "tutup" -> endCall()
                "ussd" -> sendUssd(value)
                "phone_number", "nomor" -> getPhoneNumber()
                "network", "operator" -> getNetworkInfo()
                else -> "telephony_error: unknown operation '$op'"
            }
        } catch (e: SecurityException) {
            "telephony_error: permission denied — ${e.message}"
        } catch (e: Exception) {
            "telephony_error: ${e.message}"
        }
    }

    @SuppressLint("MissingPermission")
    private fun getCallLog(limit: Int): String {
        val cursor: Cursor? = try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION
                ),
                null, null,
                "${CallLog.Calls.DATE} DESC"
            )
        } catch (e: SecurityException) {
            return "calllog_error: READ_CALL_LOG permission not granted"
        }

        cursor ?: return "calllog_error: cursor null"
        val sb = StringBuilder("[call_log]\n")
        var count = 0
        val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
        cursor.use {
            while (it.moveToNext() && count < limit) {
                val number = it.getString(0) ?: "unknown"
                val name = it.getString(1) ?: ""
                val type = when (it.getInt(2)) {
                    CallLog.Calls.INCOMING_TYPE -> "IN"
                    CallLog.Calls.OUTGOING_TYPE -> "OUT"
                    CallLog.Calls.MISSED_TYPE -> "MISS"
                    CallLog.Calls.REJECTED_TYPE -> "REJ"
                    else -> "?"
                }
                val date = dateFormat.format(Date(it.getLong(3)))
                val duration = it.getLong(4)
                val displayName = if (name.isNotEmpty()) "$name ($number)" else number
                sb.append("$type $displayName — $date — ${duration}s\n")
                count++
            }
        }
        if (count == 0) sb.append("(empty)")
        return sb.toString()
    }

    @SuppressLint("MissingPermission")
    private fun getSimInfo(): String {
        val parts = mutableListOf<String>()
        try {
            parts += "Operator: ${telephonyManager.networkOperatorName}"
            parts += "Network: ${getNetworkTypeName()}"
            parts += "SIM State: ${getSimStateName()}"
            parts += "Country: ${telephonyManager.networkCountryIso?.uppercase()}"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                    val subs = subManager?.activeSubscriptionInfoList
                    if (subs != null) {
                        parts += "Active SIMs: ${subs.size}"
                        subs.forEachIndexed { i, info ->
                            parts += "SIM${i + 1}: ${info.carrierName} (slot ${info.simSlotIndex})"
                        }
                    }
                } catch (_: SecurityException) {
                    parts += "SIM details: permission denied"
                }
            }
        } catch (e: Exception) {
            parts += "sim_error: ${e.message}"
        }
        return parts.joinToString(" | ")
    }

    @SuppressLint("MissingPermission")
    private fun answerCall(): String {
        val tm = telecomManager ?: return "call_error: TelecomManager unavailable"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                tm.acceptRingingCall()
            }
            "call_answered"
        } catch (e: SecurityException) {
            "call_error: ANSWER_PHONE_CALLS permission not granted"
        } catch (e: Exception) {
            "call_error: ${e.message}"
        }
    }

    @SuppressLint("MissingPermission")
    private fun rejectCall(): String {
        val tm = telecomManager ?: return "call_error: TelecomManager unavailable"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                @Suppress("DEPRECATION")
                tm.endCall()
                "call_rejected"
            } else {
                "call_error: requires Android 9+"
            }
        } catch (e: Exception) {
            "call_error: ${e.message}"
        }
    }

    @SuppressLint("MissingPermission")
    private fun endCall(): String = rejectCall().replace("rejected", "ended")

    @SuppressLint("MissingPermission")
    private fun sendUssd(code: String): String {
        if (code.isBlank()) return "ussd_error: empty code"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                telephonyManager.sendUssdRequest(
                    code,
                    object : TelephonyManager.UssdResponseCallback() {
                        override fun onReceiveUssdResponse(
                            telephonyManager: TelephonyManager, request: String, response: CharSequence
                        ) {
                            Log.d(TAG, "USSD response: $response")
                        }
                        override fun onReceiveUssdResponseFailed(
                            telephonyManager: TelephonyManager, request: String, failureCode: Int
                        ) {
                            Log.w(TAG, "USSD failed: code=$failureCode")
                        }
                    },
                    null
                )
                "ussd_sent: $code"
            } else {
                "ussd_error: requires Android 8+"
            }
        } catch (e: SecurityException) {
            "ussd_error: CALL_PHONE permission not granted"
        } catch (e: Exception) {
            "ussd_error: ${e.message}"
        }
    }

    @SuppressLint("MissingPermission")
    private fun getPhoneNumber(): String {
        return try {
            val number = telephonyManager.line1Number
            if (number.isNullOrBlank()) "phone_number: unavailable (SIM tidak menyimpan nomor)"
            else "phone_number: $number"
        } catch (e: SecurityException) {
            "phone_error: READ_PHONE_STATE permission not granted"
        }
    }

    private fun getNetworkInfo(): String {
        return "Operator: ${telephonyManager.networkOperatorName} | " +
            "Type: ${getNetworkTypeName()} | " +
            "Roaming: ${telephonyManager.isNetworkRoaming} | " +
            "Country: ${telephonyManager.networkCountryIso?.uppercase()}"
    }

    @SuppressLint("MissingPermission")
    private fun getNetworkTypeName(): String {
        return try {
            when (telephonyManager.dataNetworkType) {
                TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
                TelephonyManager.NETWORK_TYPE_NR -> "5G NR"
                TelephonyManager.NETWORK_TYPE_HSPAP -> "3G HSPA+"
                TelephonyManager.NETWORK_TYPE_UMTS -> "3G UMTS"
                TelephonyManager.NETWORK_TYPE_EDGE -> "2G EDGE"
                TelephonyManager.NETWORK_TYPE_GPRS -> "2G GPRS"
                else -> "Unknown"
            }
        } catch (_: SecurityException) { "Unknown (no permission)" }
    }

    private fun getSimStateName(): String = when (telephonyManager.simState) {
        TelephonyManager.SIM_STATE_READY -> "Ready"
        TelephonyManager.SIM_STATE_ABSENT -> "Absent"
        TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN Required"
        TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK Required"
        TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "Network Locked"
        else -> "Unknown"
    }

    companion object {
        private const val TAG = "TelephonyExecutor"
    }
}
