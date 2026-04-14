package com.chibiclaw.executor.tier1

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.util.Log
import com.chibiclaw.executor.IntentAction
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IntentExecutor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Whitelist of Android Intent actions we actually support. Anything else
     * gets rejected up front instead of silently calling startActivity on a
     * made-up string. This is the main defense against Gemma inventing fake
     * action names like ACTION_SET_SCREEN_LIGHT_MODE.
     */
    private val allowedActions = setOf(
        Intent.ACTION_VIEW,
        Intent.ACTION_CALL,
        Intent.ACTION_DIAL,
        Intent.ACTION_SEND,
        Intent.ACTION_SENDTO,
        Intent.ACTION_WEB_SEARCH,
        AlarmClock.ACTION_SET_ALARM,
        AlarmClock.ACTION_SET_TIMER,
        AlarmClock.ACTION_SHOW_ALARMS,
        "android.settings.WIFI_SETTINGS",
        "android.settings.BLUETOOTH_SETTINGS",
        "android.settings.SETTINGS",
        "android.settings.AIRPLANE_MODE_SETTINGS",
        "android.settings.LOCATION_SOURCE_SETTINGS",
        "android.settings.SOUND_SETTINGS",
        "android.settings.DISPLAY_SETTINGS",
        "android.intent.action.MAIN"
    )

    fun execute(action: IntentAction): String {
        if (action.action !in allowedActions) {
            Log.w(TAG, "Rejected unknown intent action: ${action.action}")
            return "intent_rejected: action '${action.action}' is not a real Android intent. " +
                "For flashlight/volume/brightness use system_control instead. " +
                "For alarms use set_alarm instead."
        }

        // BUG-I: ACTION_CALL needs CALL_PHONE runtime permission and
        // crashes with SecurityException without it. Gemma consistently
        // chooses ACTION_CALL for "telepon X" commands, so we transparently
        // downgrade to ACTION_DIAL (which requires no permission and just
        // opens the dialer with the number pre-filled). The user still
        // taps the call button themselves, which is actually the safer UX.
        var effective = action
        if (effective.action == Intent.ACTION_CALL) {
            Log.i(TAG, "ACTION_CALL → ACTION_DIAL (no permission required)")
            effective = effective.copy(action = Intent.ACTION_DIAL)
        }

        // BUG-K: ACTION_WEB_SEARCH is not handled by most browsers in 2026
        // — the user sees "no app found" or, worse, a silently-opened
        // browser homepage. Translate to the public Google search URL
        // that every browser opens correctly.
        if (effective.action == Intent.ACTION_WEB_SEARCH) {
            val query = effective.extras[SearchManager.QUERY]
                ?: effective.extras["query"]
                ?: ""
            if (query.isNotEmpty()) {
                val encoded = URLEncoder.encode(query, "UTF-8")
                Log.i(TAG, "ACTION_WEB_SEARCH → ACTION_VIEW google.com?q=$query")
                effective = IntentAction(
                    action = Intent.ACTION_VIEW,
                    uri = "https://www.google.com/search?q=$encoded"
                )
            }
        }

        return try {
            val intent = buildIntent(effective)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            // BUG-G / BUG-I / BUG-J / BUG-K: always resolve first so we
            // return a diagnostic instead of a silent success when nothing
            // on the device handles the intent.
            if (intent.resolveActivity(context.packageManager) == null) {
                Log.w(TAG, "No activity resolves ${effective.action} (uri=${effective.uri})")
                return "intent_no_activity: tidak ada aplikasi yang bisa menangani '${effective.action}'"
            }

            context.startActivity(intent)
            Log.d(TAG, "Intent fired: ${effective.action} → ${effective.uri}")
            "intent_success"
        } catch (e: android.content.ActivityNotFoundException) {
            Log.w(TAG, "No activity for intent: ${effective.action} ${effective.uri}")
            "intent_no_activity: no app on device handles '${effective.action}'"
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException on ${effective.action}: ${e.message}")
            "intent_error: izin ditolak (${e.message})"
        } catch (e: Exception) {
            Log.e(TAG, "Intent failed: ${e.message}")
            "intent_error: ${e.message}"
        }
    }

    /**
     * Dedicated alarm creator. We bypass the generic IntentAction path
     * because AlarmClock requires typed int extras that the
     * Map<String, String> extras field cannot carry.
     */
    fun setAlarm(hour: Int, minute: Int, label: String = ""): String {
        if (hour !in 0..23 || minute !in 0..59) {
            return "alarm_error: invalid time $hour:$minute"
        }
        return try {
            // Try SKIP_UI first — some clock apps on HyperOS refuse this
            // and return null from resolveActivity. If that happens we
            // retry with SKIP_UI=false, which MIUI's built-in clock does
            // honor (it just flashes a "new alarm" dialog).
            val skipUiIntent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                if (label.isNotEmpty()) putExtra(AlarmClock.EXTRA_MESSAGE, label)
            }

            // BUG-G: prior version called startActivity without verifying
            // resolveActivity, so when HyperOS Clock ignored the intent
            // we'd catch nothing and return "alarm_set_..." — the UI then
            // claimed success even though no alarm was created. Now we
            // explicitly fall back to the visible-UI flow before giving up.
            val resolvedSkipUi = skipUiIntent.resolveActivity(context.packageManager)
            val intent = if (resolvedSkipUi != null) {
                skipUiIntent
            } else {
                Log.w(TAG, "No resolver for SET_ALARM with SKIP_UI — retrying without skip")
                Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(AlarmClock.EXTRA_HOUR, hour)
                    putExtra(AlarmClock.EXTRA_MINUTES, minute)
                    if (label.isNotEmpty()) putExtra(AlarmClock.EXTRA_MESSAGE, label)
                }
            }

            if (intent.resolveActivity(context.packageManager) == null) {
                Log.e(TAG, "No clock app on device resolves ACTION_SET_ALARM")
                return "alarm_error: tidak ada aplikasi jam yang mendukung penyetelan alarm"
            }

            context.startActivity(intent)
            Log.d(TAG, "Alarm set: ${"%02d:%02d".format(hour, minute)} ($label)")
            "alarm_set_${"%02d_%02d".format(hour, minute)}"
        } catch (e: android.content.ActivityNotFoundException) {
            "alarm_error: no clock app installed"
        } catch (e: Exception) {
            Log.e(TAG, "Alarm set failed: ${e.message}")
            "alarm_error: ${e.message}"
        }
    }

    private fun buildIntent(action: IntentAction): Intent {
        val intent = Intent(action.action)
        if (action.uri.isNotEmpty()) {
            intent.data = Uri.parse(action.uri)
        }
        if (action.packageName.isNotEmpty()) {
            intent.`package` = action.packageName
        }
        action.extras.forEach { (k, v) -> intent.putExtra(k, v) }
        return intent
    }

    companion object {
        private const val TAG = "IntentExecutor"
    }
}
