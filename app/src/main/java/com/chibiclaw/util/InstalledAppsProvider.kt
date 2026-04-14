package com.chibiclaw.util

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight installed-apps inventory used by any UI that needs to let the
 * user pick a real package instead of typing one blindly. Scans via
 * [PackageManager] on first access, then caches the result in-process so
 * repeated opens of the picker are instant.
 *
 * The scan uses launcher-intent filtering (so pure-background system
 * services don't pollute the list). System apps that DO have a launcher
 * icon are still included because the user may legitimately want Fuu to
 * control them (Settings, Clock, Camera, etc.).
 *
 * NOTE: [AppInfo.icon] is a [Drawable] handle into PackageManager's icon
 * cache. It's safe to pass to Compose via rememberDrawablePainter, but
 * don't persist it — it can outlive the package on uninstall.
 */
@Singleton
class InstalledAppsProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class AppInfo(
        val packageName: String,
        val label: String,
        val icon: Drawable?,
        val isSystem: Boolean
    )

    private val mutex = Mutex()
    @Volatile private var cache: List<AppInfo>? = null

    /**
     * Returns the cached inventory, scanning on first call. [forceRefresh]
     * wipes the cache and re-scans — use it after the user installs or
     * uninstalls something and reopens the picker.
     */
    suspend fun getInstalledApps(forceRefresh: Boolean = false): List<AppInfo> {
        val current = cache
        if (!forceRefresh && current != null) return current
        return mutex.withLock {
            // Double-check inside the lock so two coroutines racing on cold
            // start only pay for one PackageManager scan.
            val again = cache
            if (!forceRefresh && again != null) return@withLock again
            val scanned = withContext(Dispatchers.IO) { scan() }
            cache = scanned
            scanned
        }
    }

    /** Drops the cache so the next call re-scans PackageManager. */
    fun invalidate() {
        cache = null
    }

    private fun scan(): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolved = try {
            pm.queryIntentActivities(intent, 0)
        } catch (e: Exception) {
            Log.e(TAG, "queryIntentActivities failed: ${e.message}")
            emptyList()
        }

        // De-dupe by package — some apps register multiple launcher activities
        // and we only want one row per app.
        val seen = HashSet<String>()
        val out = ArrayList<AppInfo>(resolved.size)
        for (ri in resolved) {
            val pkg = ri.activityInfo?.packageName ?: continue
            if (!seen.add(pkg)) continue
            val label = try {
                val raw = ri.loadLabel(pm).toString()
                if (raw.isBlank()) pkg else raw
            } catch (_: Exception) {
                pkg
            }
            val icon: Drawable? = try {
                ri.loadIcon(pm)
            } catch (_: Exception) {
                null
            }
            val isSystem = try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
            out += AppInfo(
                packageName = pkg,
                label = label,
                icon = icon,
                isSystem = isSystem
            )
        }
        // Sort alphabetically by label (case-insensitive) so the picker looks
        // the same on every cold start.
        out.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        Log.d(TAG, "Scanned ${out.size} launcher apps")
        return out
    }

    /**
     * Resolves a package name to its human label without scanning the full
     * inventory. Used by [NotificationSource] and similar code that has a
     * package and just wants a pretty name in a chat bubble.
     */
    fun labelForPackage(packageName: String): String {
        val pm = context.packageManager
        return try {
            val ai = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(ai).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        } catch (e: Exception) {
            Log.w(TAG, "labelForPackage($packageName) failed: ${e.message}")
            packageName
        }
    }

    companion object {
        private const val TAG = "InstalledAppsProvider"
    }
}
