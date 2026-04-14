package com.chibiclaw.executor.tier4

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import com.chibiclaw.executor.ShizukuAction
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 5.4 — application manager facade.
 *
 * PackageManager alone gives us enough info to answer "list all apps",
 * "launch X", "open X's Settings page" and "get version of X". For the
 * destructive bits (force-stop, clear-data, silent uninstall) we route
 * through [ShizukuExecutor] and let its whitelist protect us from
 * system-package uninstalls and shell-injection.
 *
 * Everything here is safe to call from an @Inject site; the class
 * never retains Context beyond the Hilt singleton scope.
 */
@Singleton
class AppManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shizuku: ShizukuExecutor
) {

    data class AppEntry(
        val packageName: String,
        val label: String,
        val versionName: String?,
        val isSystem: Boolean,
        val isEnabled: Boolean
    )

    /**
     * List all installed apps. `includeSystem=false` filters out any
     * package with FLAG_SYSTEM so the UI isn't drowned in 300 stock
     * packages.
     */
    fun listInstalled(includeSystem: Boolean = false): List<AppEntry> {
        val pm = context.packageManager
        val all = try {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        } catch (_: Exception) {
            return emptyList()
        }
        return all.asSequence()
            .filter { includeSystem || (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .map { info ->
                val version = try {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(info.packageName, 0).versionName
                } catch (_: Exception) { null }
                AppEntry(
                    packageName = info.packageName,
                    label = pm.getApplicationLabel(info).toString(),
                    versionName = version,
                    isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    isEnabled = info.enabled
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    /** Find an app by label substring (case-insensitive). */
    fun findByLabel(query: String): List<AppEntry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return listInstalled(includeSystem = true).filter { it.label.lowercase().contains(q) }
    }

    /** Launch an app's default activity via PackageManager. */
    fun launch(packageName: String): String {
        val intent = try {
            context.packageManager.getLaunchIntentForPackage(packageName)
        } catch (_: Exception) { null }
        if (intent == null) return "app_error: no_launcher=$packageName"
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "app_launched:$packageName"
        } catch (e: Exception) {
            "app_error: ${e.message}"
        }
    }

    /** Open the Settings → App Info page for a package (always safe). */
    fun openAppInfo(packageName: String): String {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "app_info_opened:$packageName"
        } catch (e: Exception) {
            "app_error: ${e.message}"
        }
    }

    /** Launch the system Play Store entry for a package. */
    fun openInStore(packageName: String): String {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "store_opened:$packageName"
        } catch (_: Exception) {
            try {
                val web = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(web)
                "store_opened_web:$packageName"
            } catch (e: Exception) {
                "app_error: ${e.message}"
            }
        }
    }

    // ---- privileged ops (via Shizuku) ----

    suspend fun forceStop(packageName: String): String =
        shizuku.executeAction(ShizukuAction(kind = "force_stop", payload = packageName))

    suspend fun grantRuntimePermission(packageName: String, permission: String): String =
        shizuku.executeAction(ShizukuAction(kind = "grant_permission", payload = "$packageName $permission"))

    suspend fun revokeRuntimePermission(packageName: String, permission: String): String =
        shizuku.executeAction(ShizukuAction(kind = "revoke_permission", payload = "$packageName $permission"))

    suspend fun uninstallForUser(packageName: String): String =
        shizuku.executeAction(ShizukuAction(kind = "uninstall_user", payload = packageName))

    /**
     * "Clear data" for an app. Uses the hidden `pm clear` command. If
     * Shizuku is unavailable we fall back to opening the Settings →
     * App Info page so the user can tap "Clear storage" themselves.
     */
    suspend fun clearData(packageName: String): String {
        // pm clear is not in the Shizuku whitelist, so we just take
        // the user to the App Info page and let them confirm visually.
        return openAppInfo(packageName)
    }

    /** Check whether a specific package is installed and enabled. */
    fun isInstalled(packageName: String): Boolean {
        return try {
            @Suppress("DEPRECATION")
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            info.enabled
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }
}
