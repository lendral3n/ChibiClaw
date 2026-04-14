package com.chibiclaw.executor.tier1

object IntentRegistry {
    // Map of common deep link prefixes per app
    val knownDeepLinks = mapOf(
        "whatsapp" to "https://wa.me/",
        "telegram" to "tg://",
        "gmail" to "googlegmail://",
        "maps" to "geo:",
        "youtube" to "vnd.youtube:",
        "phone" to "tel:",
        "sms" to "sms:",
        "email" to "mailto:",
        "settings" to "android.settings."
    )

    val packageNames = mapOf(
        "whatsapp" to "com.whatsapp",
        "telegram" to "org.telegram.messenger",
        "gmail" to "com.google.android.gm",
        "maps" to "com.google.android.apps.maps",
        "youtube" to "com.google.android.youtube",
        "camera" to "com.android.camera2",
        "calendar" to "com.google.android.calendar",
        "chrome" to "com.android.chrome"
    )
}
