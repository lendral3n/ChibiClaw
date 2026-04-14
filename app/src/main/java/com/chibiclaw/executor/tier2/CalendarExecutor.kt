package com.chibiclaw.executor.tier2

import android.content.Context
import android.provider.CalendarContract
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarExecutor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun getUpcomingEvents(days: Int = 7): String {
        val now = System.currentTimeMillis()
        val end = now + days * 24 * 60 * 60 * 1000L
        val results = mutableListOf<String>()
        val sdf = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())

        val cursor = context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART),
            "${CalendarContract.Events.DTSTART} BETWEEN ? AND ?",
            arrayOf(now.toString(), end.toString()),
            "${CalendarContract.Events.DTSTART} ASC"
        )
        cursor?.use {
            while (it.moveToNext()) {
                val title = it.getString(0)
                val start = it.getLong(1)
                results.add("${sdf.format(Date(start))}: $title")
            }
        }
        return if (results.isEmpty()) "No upcoming events in $days days"
        else results.take(10).joinToString("\n")
    }
}
