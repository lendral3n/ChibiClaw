package com.chibiclaw.safety

import com.chibiclaw.memory.MemoryManager
import com.chibiclaw.memory.local.entity.AppWhitelist
import com.chibiclaw.memory.local.dao.WhitelistDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhitelistManager @Inject constructor(
    private val whitelistDao: WhitelistDao
) {
    // Default whitelist populated on first run
    private val defaults = listOf(
        AppWhitelist("com.whatsapp", allowedTier = 3, policy = "ask"),
        AppWhitelist("com.google.android.gm", allowedTier = 2, policy = "ask"),
        AppWhitelist("com.google.android.apps.maps", allowedTier = 1, policy = "auto"),
        AppWhitelist("org.telegram.messenger", allowedTier = 3, policy = "ask"),
        AppWhitelist("com.android.dialer", allowedTier = 1, policy = "auto"),
        AppWhitelist("com.google.android.calendar", allowedTier = 2, policy = "auto"),
        AppWhitelist("com.google.android.youtube", allowedTier = 1, policy = "auto"),
        AppWhitelist("com.spotify.music", allowedTier = 1, policy = "auto"),
        AppWhitelist("com.android.chrome", allowedTier = 1, policy = "auto"),
        AppWhitelist("com.android.settings", allowedTier = 2, policy = "ask")
    )

    suspend fun initDefaults() {
        defaults.forEach { entry ->
            if (whitelistDao.getByPackage(entry.packageName) == null) {
                whitelistDao.insert(entry)
            }
        }
    }

    suspend fun isAllowed(packageName: String): Boolean =
        whitelistDao.getByPackage(packageName) != null

    suspend fun getPolicy(packageName: String): String =
        whitelistDao.getByPackage(packageName)?.policy ?: "ask"
}
