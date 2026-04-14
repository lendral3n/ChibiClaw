package com.chibiclaw.executor.tier2

import android.content.Context
import android.provider.Telephony
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmsExecutor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun getRecentSms(contact: String, limit: Int = 5): String {
        val results = mutableListOf<String>()
        val cursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.TYPE),
            "${Telephony.Sms.ADDRESS} LIKE ?",
            arrayOf("%$contact%"),
            "${Telephony.Sms.DATE} DESC"
        )
        cursor?.use {
            while (it.moveToNext() && results.size < limit) {
                val address = it.getString(0)
                val body = it.getString(1)
                val type = it.getInt(2)
                val direction = if (type == Telephony.Sms.MESSAGE_TYPE_SENT) "→" else "←"
                results.add("$direction [$address] $body")
            }
        }
        return if (results.isEmpty()) "No SMS found for: $contact"
        else results.joinToString("\n")
    }
}
